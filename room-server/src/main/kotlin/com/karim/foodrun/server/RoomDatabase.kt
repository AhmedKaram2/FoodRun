package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.io.File
import kotlinx.serialization.encodeToString
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RoomDatabase(directory: File, private val durable: DurableStore? = null) : AutoCloseable {
    val cloudDurable: Boolean get() = durable != null
    @Volatile private var failed = false
    val available: Boolean get() = !failed
    private var transactionDepth = 0
    private val random = SecureRandom()
    private val key: SecretKeySpec
    private val sqlConnection: Connection
    private val connection: Connection get() {
        if (failed) throw StorageUnavailable("Storage must reconnect before serving requests.")
        return sqlConnection
    }
    init {
        directory.mkdirs(); restrict(directory, true)
        val keyFile = File(directory, "storage.key")
        require(keyFile.exists() || !File(directory, "rooms.sqlite").exists()) { "The database encryption key is missing. Restore storage.key from the same hub backup." }
        if (!keyFile.exists()) { keyFile.writeBytes(ByteArray(32).also(random::nextBytes)); restrict(keyFile) }
        val keyBytes = keyFile.readBytes()
        require(keyBytes.size == 32) { "The database encryption key is invalid. Restore the hub backup." }
        key = SecretKeySpec(keyBytes, "AES")
        sqlConnection = DriverManager.getConnection("jdbc:sqlite:${File(directory, "rooms.sqlite").absolutePath}")
        connection.createStatement().use { s ->
            val version = s.executeQuery("PRAGMA user_version").use { if (it.next()) it.getInt(1) else 0 }
            require(version <= 3) { "This database requires a newer Food Run hub." }
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL"); s.execute("PRAGMA busy_timeout=5000")
            s.execute("CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, code TEXT UNIQUE, body TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS sessions (hash TEXT PRIMARY KEY, room_id TEXT NOT NULL, member_id TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS commands (id TEXT PRIMARY KEY, digest TEXT NOT NULL, body TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS orders (room_id TEXT NOT NULL, number INTEGER NOT NULL, body TEXT NOT NULL, PRIMARY KEY(room_id,number))")
            val columns = s.executeQuery("PRAGMA table_info(rooms)").use { rs -> buildSet { while (rs.next()) add(rs.getString("name")) } }
            if ("phase" !in columns) {
                s.execute("ALTER TABLE rooms ADD COLUMN phase TEXT NOT NULL DEFAULT 'LOBBY'")
                // Migrate existing encrypted records once; later ticks query only active spins.
                allRooms().forEach { room -> connection.prepareStatement("UPDATE rooms SET phase=? WHERE id=?").use { it.setString(1, room.phase.name); it.setString(2, room.id); it.executeUpdate() } }
            }
            s.execute("CREATE INDEX IF NOT EXISTS room_phase ON rooms(phase)")
            s.execute("CREATE TABLE IF NOT EXISTS account_records (key TEXT PRIMARY KEY, body TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS cloud_outbox (key TEXT PRIMARY KEY, body TEXT NOT NULL)")
            s.execute("PRAGMA user_version=3")
        }
        restrict(File(directory, "rooms.sqlite"))
        if (durable != null) {
            val restored = durable.load()
            if (restored != null) {
                connection.autoCommit = false
                try {
                    storedTables.forEach { table -> connection.createStatement().use { it.executeUpdate("DELETE FROM ${table.name}") } }
                    connection.createStatement().use { it.executeUpdate("DELETE FROM cloud_outbox") }
                    restored.forEach(::restoreRow)
                    connection.commit()
                } catch (error: Throwable) { connection.rollback(); throw error }
                finally { connection.autoCommit = true }
            } else {
                connection.prepareStatement("INSERT OR IGNORE INTO account_records VALUES(?,?)").use { it.setString(1, "storage:format"); it.setString(2, encrypt("1")); it.executeUpdate() }
                val rows = storedTables.flatMap { table -> connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT ${table.columns.joinToString(",")} FROM ${table.name}").use { result ->
                        buildList { while (result.next()) add(readRow(table, result)) }
                    }
                } }
                durable.commit(rows.associateBy { it.id })
            }
            installTracking()
        }
    }
    fun room(id: String): Room? = query("SELECT body FROM rooms WHERE id=?", id)?.let { orderJson.decodeFromString<Room>(decrypt(it)) }
    fun roomByCode(code: String): Room? = query("SELECT body FROM rooms WHERE code=?", code)?.let { orderJson.decodeFromString<Room>(decrypt(it)) }
    fun allRooms(): List<Room> = connection.createStatement().use { s -> s.executeQuery("SELECT body FROM rooms").use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun activeRoomCount(): Int = connection.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM rooms WHERE phase NOT IN ('ARCHIVED','CANCELLED')").use { it.next(); it.getInt(1) } }
    fun spinningRooms(): List<Room> = connection.createStatement().use { s -> s.executeQuery("SELECT body FROM rooms WHERE phase IN ('SPINNING','PREPARING_SPIN')").use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun save(room: Room) { transaction { enqueueCloud("room-${room.id}", orderJson.encodeToString(room)); connection.prepareStatement("INSERT INTO rooms(id,code,body,phase) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,phase=excluded.phase").use { it.setString(1, room.id); it.setString(2, room.code); it.setString(3, encrypt(orderJson.encodeToString(room))); it.setString(4, room.phase.name); it.executeUpdate() }  } }
    fun session(hash: String): Pair<String, String>? = connection.prepareStatement("SELECT room_id,member_id FROM sessions WHERE hash=?").use { it.setString(1, hash); it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null } }
    fun addSession(hash: String, room: String, member: String) { transaction { connection.prepareStatement("INSERT INTO sessions VALUES(?,?,?)").use { it.setString(1, hash); it.setString(2, room); it.setString(3, member); it.executeUpdate() }  } }
    fun previous(id: String, digest: String): RoomReply? = connection.prepareStatement("SELECT digest,body FROM commands WHERE id=?").use { it.setString(1, id); it.executeQuery().use { rs -> if (!rs.next()) null else { require(rs.getString(1) == digest) { "Command ID reused with different content." }; orderJson.decodeFromString<RoomReply>(decrypt(rs.getString(2))) } } }
    internal fun recordedReply(id: String): RoomReply? = query("SELECT body FROM commands WHERE id=?", id)
        ?.let { orderJson.decodeFromString<RoomReply>(decrypt(it)) }
    fun record(id: String, digest: String, reply: RoomReply) { transaction { connection.prepareStatement("INSERT INTO commands VALUES(?,?,?)").use { it.setString(1, id); it.setString(2, digest); it.setString(3, encrypt(orderJson.encodeToString(reply))); it.executeUpdate() }  } }
    fun <T> transaction(block: () -> T): T {
        if (transactionDepth > 0) return block()
        connection.autoCommit = false
        transactionDepth++
        var cloudCommitted = false
        return try {
            val result = block()
            if (durable != null) {
                val changes = trackedChanges()
                if (changes.isNotEmpty()) { durable.commit(changes); cloudCommitted = true }
                connection.createStatement().use { it.executeUpdate("DELETE FROM durable_changes") }
            }
            connection.commit()
            result
        } catch (error: Throwable) {
            if (error is StorageUnavailable || cloudCommitted) failed = true
            runCatching { sqlConnection.rollback() }
            throw if (cloudCommitted) StorageUnavailable("Reload the durable store before retrying.", error) else error
        } finally { transactionDepth--; sqlConnection.autoCommit = true }
    }
    private fun query(sql: String, value: String): String? = connection.prepareStatement(sql).use { it.setString(1, value); it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
    fun archive(room: Room) { transaction { enqueueCloud("order-${room.id}-${room.orderNumber}", orderJson.encodeToString(room)); connection.prepareStatement("INSERT OR REPLACE INTO orders VALUES(?,?,?)").use { it.setString(1, room.id); it.setLong(2, room.orderNumber); it.setString(3, encrypt(orderJson.encodeToString(room))); it.executeUpdate() }  } }
    fun history(roomId: String, offset: Int = 0, limit: Int = 6): List<Room> = connection.prepareStatement("SELECT body FROM orders WHERE room_id=? ORDER BY number DESC LIMIT ? OFFSET ?").use { it.setString(1, roomId); it.setInt(2, limit); it.setInt(3, offset); it.executeQuery().use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun allHistory(): List<Room> = connection.createStatement().use { statement -> statement.executeQuery("SELECT body FROM orders ORDER BY room_id,number DESC").use { rows -> buildList { while(rows.next()) add(orderJson.decodeFromString<Room>(decrypt(rows.getString(1)))) } } }
    fun record(key: String): String? = query("SELECT body FROM account_records WHERE key=?", key)?.let(::decrypt)
    fun records(prefix: String): List<Pair<String, String>> = connection.prepareStatement("SELECT key,body FROM account_records WHERE substr(key,1,?)=?").use {
        it.setInt(1, prefix.length); it.setString(2, prefix); it.executeQuery().use { rs -> buildList { while(rs.next()) add(rs.getString(1) to decrypt(rs.getString(2))) } }
    }
    fun putRecord(key: String, value: String) { transaction { connection.prepareStatement("INSERT INTO account_records VALUES(?,?) ON CONFLICT(key) DO UPDATE SET body=excluded.body").use {
        it.setString(1, key); it.setString(2, encrypt(value)); it.executeUpdate()
    }  } }
    fun deleteRecord(key: String) { transaction { connection.prepareStatement("DELETE FROM account_records WHERE key=?").use { it.setString(1, key); it.executeUpdate() }  } }
    fun enqueueCloud(key: String, value: String) { if (cloudDurable) return; transaction { connection.prepareStatement("INSERT INTO cloud_outbox VALUES(?,?) ON CONFLICT(key) DO UPDATE SET body=excluded.body").use {
        it.setString(1, key); it.setString(2, encrypt(value)); it.executeUpdate()
    }  } }
    fun pendingCloud(): Pair<String, String>? = connection.createStatement().use { s -> s.executeQuery("SELECT key,body FROM cloud_outbox LIMIT 1").use { rs -> if(rs.next()) rs.getString(1) to decrypt(rs.getString(2)) else null } }
    fun finishCloud(key: String, value: String) { transaction {
        // Do not drop a newer queued revision while a cloud request was in flight.
        if (query("SELECT body FROM cloud_outbox WHERE key=?", key)?.let(::decrypt) == value)
            connection.prepareStatement("DELETE FROM cloud_outbox WHERE key=?").use { it.setString(1, key); it.executeUpdate() }
     } }
    /** Called under the room service lock and a transaction after admin validation. */
    fun deletedHistory(roomId: String): Set<Long> = records("admin:deleted-history:$roomId:").mapNotNull { it.first.substringAfterLast(':').toLongOrNull() }.toSet()
    fun deleteHistory(roomId: String, number: Long) { transaction {
        putRecord("admin:deleted-history:$roomId:$number", "true")
        connection.prepareStatement("DELETE FROM orders WHERE room_id=? AND number=?").use { it.setString(1, roomId); it.setLong(2, number); it.executeUpdate() }
        enqueueCloud("order-$roomId-$number", "{\"deleted\":true}")
     } }
    fun deleteRoom(roomId: String) { transaction {
        putRecord("admin:deleted-room:$roomId", "true")
        allHistory().filter { it.id == roomId }.forEach { deleteHistory(it.id, it.orderNumber) }
        for (table in listOf("sessions", "rooms")) {
            val column = if (table == "rooms") "id" else "room_id"
            connection.prepareStatement("DELETE FROM $table WHERE $column=?").use { it.setString(1, roomId); it.executeUpdate() }
        }
        records("membership:").filter { orderJson.decodeFromString<AccountRoom>(it.second).roomId == roomId }.forEach { deleteRecord(it.first) }
        records("invitation:").filter { orderJson.decodeFromString<FoodInvitation>(it.second).roomId == roomId }.forEach { deleteRecord(it.first) }
        records("member-user:$roomId:").forEach { deleteRecord(it.first) }
        // Erase idempotent responses too: a retry must not resurrect the deleted room.
        val commandIds = connection.createStatement().use { statement -> statement.executeQuery("SELECT id,body FROM commands").use { rows ->
            buildList { while(rows.next()) if (orderJson.decodeFromString<RoomReply>(decrypt(rows.getString(2))).room?.id == roomId) add(rows.getString(1)) }
        } }
        commandIds.forEach { id -> connection.prepareStatement("UPDATE commands SET body=? WHERE id=?").use { it.setString(1, encrypt(orderJson.encodeToString(RoomReply(ok = false, code = "REMOVED", error = "This room was deleted by the administrator.")))); it.setString(2, id); it.executeUpdate() } }
        enqueueCloud("room-$roomId", "{\"deleted\":true}")
     } }
    fun revokeUserSessions(userId: String) { transaction {
        val memberships = records("membership:$userId:")
        memberships.forEach { (_, body) ->
            val member = orderJson.decodeFromString<AccountRoom>(body)
            connection.prepareStatement("DELETE FROM sessions WHERE room_id=? AND member_id=?").use { it.setString(1, member.roomId); it.setString(2, member.memberId); it.executeUpdate() }
        }
        records("identity:").filter { kotlinx.serialization.json.Json.parseToJsonElement(it.second).let { value ->
            (value as kotlinx.serialization.json.JsonObject)["userId"] == kotlinx.serialization.json.JsonPrimitive(userId)
        } }.forEach { deleteRecord(it.first) }
     } }
    private fun encrypt(text: String): String { val iv = ByteArray(12).also(random::nextBytes); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)); return Base64.getEncoder().encodeToString(iv + cipher.doFinal(text.toByteArray())) }
    private fun decrypt(text: String): String { val data = Base64.getDecoder().decode(text); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12))); return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8) }
    private fun readRow(table: StoredTable, result: java.sql.ResultSet): StoredRow = StoredRow(table.name,
        table.columns.indices.map { index -> result.getString(index + 1).let { if (index == table.body) decrypt(it) else it } })
    private fun restoreRow(row: StoredRow) {
        val table = row.schema
        require(row.cells.size == table.columns.size)
        connection.prepareStatement("INSERT INTO ${table.name} (${table.columns.joinToString(",")}) VALUES (${table.columns.joinToString(",") { "?" }})").use { statement ->
            row.cells.forEachIndexed { index, value -> statement.setString(index + 1, if (index == table.body) encrypt(value) else value) }
            statement.executeUpdate()
        }
    }
    private fun installTracking() = connection.createStatement().use { statement ->
        statement.execute("CREATE TEMP TABLE durable_changes (table_name TEXT, row_key TEXT)")
        storedTables.forEach { table ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                val source = if (operation == "DELETE") "OLD" else "NEW"
                val keys = table.columns.take(table.primaryKeys).joinToString(",") { "$source.$it" }
                statement.execute("CREATE TEMP TRIGGER durable_${table.name}_$operation AFTER $operation ON ${table.name} BEGIN INSERT INTO durable_changes VALUES ('${table.name}', json_array($keys)); END")
            }
        }
    }
    private fun trackedChanges(): Map<String, StoredRow?> {
        val keys = connection.createStatement().use { statement -> statement.executeQuery("SELECT DISTINCT table_name,row_key FROM durable_changes").use { rows ->
            buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) }
        } }
        return keys.associate { (tableName, key) ->
            val table = storedTables.single { it.name == tableName }
            val values = kotlinx.serialization.json.Json.parseToJsonElement(key) as kotlinx.serialization.json.JsonArray
            val primary = values.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            val id = RoomService.hash("$tableName:${orderJson.encodeToString(primary)}")
            val row = connection.prepareStatement("SELECT ${table.columns.joinToString(",")} FROM $tableName WHERE ${table.columns.take(table.primaryKeys).joinToString(" AND ") { "$it=?" }}").use { statement ->
                primary.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.executeQuery().use { if (it.next()) readRow(table, it) else null }
            }
            id to row
        }
    }
    override fun close() { sqlConnection.close(); durable?.close() }
    companion object {
        fun restrict(file: File, directory: Boolean = false) { file.setReadable(false, false); file.setWritable(false, false); file.setReadable(true, true); file.setWritable(true, true); if (directory) { file.setExecutable(false, false); file.setExecutable(true, true) } }
    }
}
