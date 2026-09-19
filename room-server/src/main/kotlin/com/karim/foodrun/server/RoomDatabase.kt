package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.io.File
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RoomDatabase(directory: File) : AutoCloseable {
    private val random = SecureRandom()
    private val key: SecretKeySpec
    private val connection: Connection
    init {
        directory.mkdirs(); restrict(directory, true)
        val keyFile = File(directory, "storage.key")
        require(keyFile.exists() || !File(directory, "rooms.sqlite").exists()) { "The database encryption key is missing. Restore storage.key from the same hub backup." }
        if (!keyFile.exists()) { keyFile.writeBytes(ByteArray(32).also(random::nextBytes)); restrict(keyFile) }
        val keyBytes = keyFile.readBytes()
        require(keyBytes.size == 32) { "The database encryption key is invalid. Restore the hub backup." }
        key = SecretKeySpec(keyBytes, "AES")
        connection = DriverManager.getConnection("jdbc:sqlite:${File(directory, "rooms.sqlite").absolutePath}")
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
    }
    fun room(id: String): Room? = query("SELECT body FROM rooms WHERE id=?", id)?.let { orderJson.decodeFromString<Room>(decrypt(it)) }
    fun roomByCode(code: String): Room? = query("SELECT body FROM rooms WHERE code=?", code)?.let { orderJson.decodeFromString<Room>(decrypt(it)) }
    fun allRooms(): List<Room> = connection.createStatement().use { s -> s.executeQuery("SELECT body FROM rooms").use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun activeRoomCount(): Int = connection.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM rooms WHERE phase NOT IN ('ARCHIVED','CANCELLED')").use { it.next(); it.getInt(1) } }
    fun spinningRooms(): List<Room> = connection.createStatement().use { s -> s.executeQuery("SELECT body FROM rooms WHERE phase='SPINNING'").use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun save(room: Room) { enqueueCloud("room-${room.id}", orderJson.encodeToString(room)); connection.prepareStatement("INSERT INTO rooms(id,code,body,phase) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,phase=excluded.phase").use { it.setString(1, room.id); it.setString(2, room.code); it.setString(3, encrypt(orderJson.encodeToString(room))); it.setString(4, room.phase.name); it.executeUpdate() } }
    fun session(hash: String): Pair<String, String>? = connection.prepareStatement("SELECT room_id,member_id FROM sessions WHERE hash=?").use { it.setString(1, hash); it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null } }
    fun addSession(hash: String, room: String, member: String) { connection.prepareStatement("INSERT INTO sessions VALUES(?,?,?)").use { it.setString(1, hash); it.setString(2, room); it.setString(3, member); it.executeUpdate() } }
    fun previous(id: String, digest: String): RoomReply? = connection.prepareStatement("SELECT digest,body FROM commands WHERE id=?").use { it.setString(1, id); it.executeQuery().use { rs -> if (!rs.next()) null else { require(rs.getString(1) == digest) { "Command ID reused with different content." }; orderJson.decodeFromString<RoomReply>(decrypt(rs.getString(2))) } } }
    fun record(id: String, digest: String, reply: RoomReply) { connection.prepareStatement("INSERT INTO commands VALUES(?,?,?)").use { it.setString(1, id); it.setString(2, digest); it.setString(3, encrypt(orderJson.encodeToString(reply))); it.executeUpdate() } }
    fun <T> transaction(block: () -> T): T { connection.autoCommit = false; return try { block().also { connection.commit() } } catch (t: Throwable) { connection.rollback(); throw t } finally { connection.autoCommit = true } }
    private fun query(sql: String, value: String): String? = connection.prepareStatement(sql).use { it.setString(1, value); it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
    fun archive(room: Room) { enqueueCloud("order-${room.id}-${room.orderNumber}", orderJson.encodeToString(room)); connection.prepareStatement("INSERT OR REPLACE INTO orders VALUES(?,?,?)").use { it.setString(1, room.id); it.setLong(2, room.orderNumber); it.setString(3, encrypt(orderJson.encodeToString(room))); it.executeUpdate() } }
    fun history(roomId: String, offset: Int = 0, limit: Int = 6): List<Room> = connection.prepareStatement("SELECT body FROM orders WHERE room_id=? ORDER BY number DESC LIMIT ? OFFSET ?").use { it.setString(1, roomId); it.setInt(2, limit); it.setInt(3, offset); it.executeQuery().use { rs -> buildList { while (rs.next()) add(orderJson.decodeFromString<Room>(decrypt(rs.getString(1)))) } } }
    fun record(key: String): String? = query("SELECT body FROM account_records WHERE key=?", key)?.let(::decrypt)
    fun records(prefix: String): List<Pair<String, String>> = connection.prepareStatement("SELECT key,body FROM account_records WHERE key LIKE ?").use {
        it.setString(1, "$prefix%"); it.executeQuery().use { rs -> buildList { while(rs.next()) add(rs.getString(1) to decrypt(rs.getString(2))) } }
    }
    fun putRecord(key: String, value: String) { connection.prepareStatement("INSERT INTO account_records VALUES(?,?) ON CONFLICT(key) DO UPDATE SET body=excluded.body").use {
        it.setString(1, key); it.setString(2, encrypt(value)); it.executeUpdate()
    } }
    fun deleteRecord(key: String) { connection.prepareStatement("DELETE FROM account_records WHERE key=?").use { it.setString(1, key); it.executeUpdate() } }
    fun enqueueCloud(key: String, value: String) { connection.prepareStatement("INSERT INTO cloud_outbox VALUES(?,?) ON CONFLICT(key) DO UPDATE SET body=excluded.body").use {
        it.setString(1, key); it.setString(2, encrypt(value)); it.executeUpdate()
    } }
    fun pendingCloud(): Pair<String, String>? = connection.createStatement().use { s -> s.executeQuery("SELECT key,body FROM cloud_outbox LIMIT 1").use { rs -> if(rs.next()) rs.getString(1) to decrypt(rs.getString(2)) else null } }
    fun finishCloud(key: String, value: String) {
        // Do not drop a newer queued revision while a cloud request was in flight.
        if (query("SELECT body FROM cloud_outbox WHERE key=?", key)?.let(::decrypt) == value)
            connection.prepareStatement("DELETE FROM cloud_outbox WHERE key=?").use { it.setString(1, key); it.executeUpdate() }
    }
    private fun encrypt(text: String): String { val iv = ByteArray(12).also(random::nextBytes); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)); return Base64.getEncoder().encodeToString(iv + cipher.doFinal(text.toByteArray())) }
    private fun decrypt(text: String): String { val data = Base64.getDecoder().decode(text); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12))); return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8) }
    override fun close() = connection.close()
    companion object {
        fun restrict(file: File, directory: Boolean = false) { file.setReadable(false, false); file.setWritable(false, false); file.setReadable(true, true); file.setWritable(true, true); if (directory) { file.setExecutable(false, false); file.setExecutable(true, true) } }
    }
}
