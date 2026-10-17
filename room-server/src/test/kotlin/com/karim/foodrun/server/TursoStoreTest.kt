package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import com.karim.foodrun.orders.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.concurrent.Executors
import kotlinx.serialization.json.*
import kotlin.test.*

class TursoStoreTest {
    @Test fun reusedSqlConnectionsDoNotAccumulateTemporaryWriterFenceTables() {
        TursoFixture().use { remote ->
            remote.store(create = true).use { store ->
                store.load()
                repeat(10) { index ->
                    val value = row("probe", index.toString())
                    store.commit(mapOf(value.id to value))
                }
                assertFalse(remote.hasTemporaryFence())
            }
        }
    }

    @Test fun dailyWriteGuardPreservesConfirmedRowsAndResetsOnTheNextDay() {
        TursoFixture().use { remote ->
            remote.store(create = true, dailyLimit = 10, day = { "2026-10-17" }).use { store ->
                store.load()
                val first = row("first", "confirmed")
                val second = row("second", "confirmed")
                store.commit(mapOf(first.id to first))
                store.commit(mapOf(second.id to second))
                assertFailsWith<StorageUnavailable> { store.commit(mapOf(first.id to null)) }
            }
            remote.store(dailyLimit = 10, day = { "2026-10-18" }).use { store ->
                assertEquals(setOf(row("first", "confirmed"), row("second", "confirmed")), store.load()!!.toSet())
                val newer = row("first", "updated")
                store.commit(mapOf(newer.id to newer))
                assertEquals(setOf(newer, row("second", "confirmed")), store.load()!!.toSet())
            }
        }
    }

    @Test fun tursoSignInAndRegistrationDoNotDependOnLegacyFirestoreProfiles() {
        val identity = object : IdentityProvider {
            override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "id", "refresh", name = email.substringBefore('@'))
            override fun exchange(idToken: String) = CloudIdentity(idToken.removePrefix("firebase-"), "id", "refresh", name = idToken.removePrefix("firebase-"))
            override fun refresh(refreshToken: String) = error("Not needed")
            override fun profile(identity: CloudIdentity): FoodProfile? = throw IllegalStateException("Firestore quota exhausted")
            override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = error("Legacy profile writes must be deferred")
            override fun resetPassword(email: String) = Unit
            override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = error("Turso must not back up room rows to Firestore")
        }
        TursoFixture().use { remote ->
            RoomFixture(identity, durableFactory = { remote.store(create = true) }).use { f ->
                val registered = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
                    identity = IdentityRequest(IdentityAction.REGISTER, email = "new@example.test", password = "fixture-password", profile = FoodProfile(name = "New member"))))
                assertEquals("New member", registered.home!!.profile.name)
                assertTrue(registered.home!!.cloudStatus.contains("saved online"))
                f.restart(clearCache = true)
                val signed = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
                    identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "firebase-new")))
                assertEquals("New member", signed.home!!.profile.name)
                assertNotNull(f.db.record("profile-sync:new"))
            }
        }
    }

    @Test fun walletHistorySessionsAndPaymentIdempotencySurviveAnEmptyLocalDisk() {
        TursoFixture().use { remote ->
            RoomFixture(durableFactory = { remote.store(create = true) }).use { f ->
                assertEquals("turso", f.service.storageMode)
                val member = f.placed(); f.pay()
                val command = f.command(member, com.karim.foodrun.orders.CommandKind.DECLARE_TRANSFER).copy(amount = 3000, text = "Paid")
                val transfer = f.execute(command).room!!.transfers.single()
                f.send(f.owner, com.karim.foodrun.orders.CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = transfer.id) }
                f.send(f.owner, com.karim.foodrun.orders.CommandKind.FULFILL)
                f.send(f.owner, com.karim.foodrun.orders.CommandKind.ARCHIVE)
                f.send(f.owner, com.karim.foodrun.orders.CommandKind.NEXT_ORDER)
                f.restart(clearCache = true)
                assertEquals(2, f.state(member).room!!.orderNumber)
                assertEquals(1, f.state(member).history.size)
                assertEquals(0, f.state(member).history.single().receipts.single().balance)
                assertEquals(transfer.id, f.execute(command).room!!.transfers.single().id)
            }
        }
    }

    @Test fun failedMiddleStatementRollsBackEveryRowAndTheCommitMarker() {
        TursoFixture().use { remote ->
            remote.store(create = true).use { store ->
                assertNull(store.load())
                val previous = row("previous", "confirmed")
                store.commit(mapOf(previous.id to previous))
                remote.failAt = "INSERT INTO foodrun_rows"
                assertFailsWith<StorageUnavailable> { store.commit(mapOf(previous.id to null, row("new", "pending").let { it.id to it })) }
                assertFalse(store.available)
            }
            remote.failAt = null
            remote.store().use { assertEquals(listOf(row("previous", "confirmed")), it.load()) }
        }
    }

    @Test fun lostSuccessResponseIsConfirmedWithoutReplayingThePayment() {
        TursoFixture().use { remote ->
            remote.store(create = true).use { store ->
                store.load()
                val payment = row("payment:once", "confirmed")
                remote.dropNextCommitResponse = true
                store.commit(mapOf(payment.id to payment))
                assertTrue(store.available)
                assertEquals(1, remote.rowWrites)
            }
            remote.store().use { assertEquals(listOf(row("payment:once", "confirmed")), it.load()) }
        }
    }

    @Test fun takeoverFencesWritesAndStopsCachedReadsOnTheNextOwnershipCheck() {
        TursoFixture().use { remote ->
            var now = 10_000L
            remote.store(create = true, clock = { now }).use { first ->
                first.load(); first.commit(mapOf(row("balance", "42").let { it.id to it }))
                remote.store().use { second ->
                    assertEquals("42", second.load()!!.single().cells.last())
                    now += 1_001
                    assertFalse(first.available)
                    assertFailsWith<StorageUnavailable> { first.commit(mapOf(row("balance", "0").let { it.id to it })) }
                    assertEquals("42", second.load()!!.single().cells.last())
                }
            }
        }
    }

    @Test fun staleWriterCannotMutateRowsEvenBeforeItsCachedOwnershipCheckExpires() {
        TursoFixture().use { remote ->
            remote.store(create = true, clock = { 1_000 }).use { old ->
                old.load(); old.commit(mapOf(row("balance", "42").let { it.id to it }))
                remote.store(clock = { 1_000 }).use { current ->
                    current.load()
                    assertTrue(old.available) // Cached ownership has not been polled yet.
                    assertFailsWith<StorageUnavailable> { old.commit(mapOf(row("balance", "0").let { it.id to it })) }
                    assertFalse(old.available)
                    assertEquals("42", current.load()!!.single().cells.last())
                }
            }
        }
    }

    @Test fun namespacesAreIsolatedAndBlankProductionStorageCannotBootstrapItself() {
        TursoFixture().use { remote ->
            assertFailsWith<StorageUnavailable> { remote.store().load() }
            remote.store(create = true).use { it.load(); it.commit(mapOf(row("a", "one").let { it.id to it })) }
            assertFailsWith<StorageUnavailable> { remote.store(namespace = "other").load() }
            remote.store().use { assertEquals(listOf(row("a", "one")), it.load()) }
        }
    }

    @Test fun migrationReadsBackEveryRowBeforePublishingAndNeverOverwritesATarget() {
        TursoFixture().use { remote ->
            val rows = (0 until 300).map { row("fixture:$it", "$it:" + "x".repeat(16_384)) }
            val source = DurableStorageTest.MemoryStore().apply { this.rows = rows.associateBy { it.id }.toMutableMap() }
            remote.store(create = true).use { assertEquals(300, it.importFrom(source)) }
            assertEquals(0, source.commits)
            remote.store().use { assertEquals(rows.toSet(), it.load()!!.toSet()) }
            remote.store(create = true).use { assertFailsWith<StorageUnavailable> { it.importFrom(source) } }
            remote.store().use { assertEquals(rows.toSet(), it.load()!!.toSet()) }
        }
    }

    @Test fun interruptedMigrationRemainsUnavailableAndPreservesTheSource() {
        TursoFixture().use { remote ->
            val source = object : DurableStore {
                override fun load(): List<StoredRow>? = error("Use streaming restore")
                override fun restore(accept: (StoredRow) -> Unit): Boolean {
                    repeat(80) { accept(row("source:$it", "preserved")) }
                    throw StorageUnavailable("Source quota exhausted")
                }
                override fun commit(changes: Map<String, StoredRow?>) = error("Migration cannot mutate source rows")
            }
            remote.store(create = true).use { assertFailsWith<StorageUnavailable> { it.importFrom(source) } }
            assertTrue(remote.rowWrites > 0)
            assertFailsWith<StorageUnavailable> { remote.store().load() }
        }
    }

    @Test fun configurationRequiresARealDatabaseUrlAndKeepsCredentialsOutOfErrors() {
        assertFailsWith<IllegalArgumentException> { TursoSql.endpoint("https://app.turso.tech/ahmedkaram") }
        assertFailsWith<IllegalArgumentException> { TursoSql.endpoint("https://example.org") }
        assertFailsWith<IllegalArgumentException> { TursoSql.endpoint("libsql://database.turso.io?authToken=private") }
        assertEquals("https://database.turso.io/v2/pipeline", TursoSql.endpoint("libsql://database.turso.io").toString())
        assertNull(configuredDurableStore(emptyMap()))
        assertFailsWith<IllegalArgumentException> { configuredDurableStore(mapOf("FOODRUN_STORAGE" to "turso")) }
        TursoFixture().use { remote ->
            remote.httpStatus = 401
            val error = assertFailsWith<StorageUnavailable> { remote.store(create = true).load() }
            assertFalse(error.toString().contains("fixture-secret"))
            assertNull(error.cause)
        }
    }

    private fun row(key: String, body: String) = StoredRow("account_records", listOf(key, body))

    /** The real HTTP client talks to a protocol fixture backed by real SQLite transactions. */
    private class TursoFixture : AutoCloseable {
        private val directory = Files.createTempDirectory("foodrun-turso-test").toFile()
        private val file = directory.resolve("remote.sqlite")
        private val connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        private val executor = Executors.newSingleThreadExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var failAt: String? = null
        var dropNextCommitResponse = false
        var httpStatus = 200
        var rowWrites = 0
        init {
            server.executor = executor
            server.createContext("/v2/pipeline") { exchange ->
                try {
                    check(exchange.requestHeaders.getFirst("Authorization") == "Bearer fixture-secret")
                    val body = orderJson.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                    val operation = body["requests"]!!.jsonArray.first().jsonObject
                    val response = when (operation["type"]!!.jsonPrimitive.content) {
                        "execute" -> buildJsonObject { put("type", "execute"); put("result", execute(connection, operation["stmt"]!!.jsonObject)) }
                        "batch" -> batch(connection, operation["batch"]!!.jsonObject)
                        else -> error("Unsupported fixture request")
                    }
                    val drop = dropNextCommitResponse && operation["type"]!!.jsonPrimitive.content == "batch"
                    if (drop) dropNextCommitResponse = false
                    val result = buildJsonObject {
                        put("results", buildJsonArray {
                            add(buildJsonObject { put("type", "ok"); put("response", response) })
                            add(buildJsonObject { put("type", "ok"); put("response", buildJsonObject { put("type", "close") }) })
                        })
                    }.toString().toByteArray()
                    exchange.sendResponseHeaders(if (drop) 503 else httpStatus, result.size.toLong())
                    exchange.responseBody.use { it.write(result) }
                } catch (_: Exception) {
                    exchange.sendResponseHeaders(500, -1)
                } finally { exchange.close() }
            }
            server.start()
        }
        fun store(create: Boolean = false, namespace: String = "test", clock: () -> Long = System::currentTimeMillis,
            dailyLimit: Int = 12_000, day: () -> String = { "2026-10-17" }) =
            TursoStore(TursoSql("http://127.0.0.1:${server.address.port}", "fixture-secret", allowLocalhost = true), namespace, create, clock, dailyLimit, day)
        fun hasTemporaryFence(): Boolean = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT 1 FROM sqlite_temp_master WHERE name='foodrun_writer_fence'").use { it.next() }
        }

        private fun batch(connection: Connection, batch: JsonObject): JsonObject {
            val results = mutableListOf<JsonElement>()
            val errors = mutableListOf<JsonElement>()
            batch["steps"]!!.jsonArray.forEach { step ->
                val value = step.jsonObject
                val condition = value["condition"]?.jsonObject
                fun allowed(cond: JsonObject): Boolean = when (cond["type"]!!.jsonPrimitive.content) {
                    "ok" -> results[cond["step"]!!.jsonPrimitive.int] != JsonNull
                    "not" -> !allowed(cond["cond"]!!.jsonObject)
                    else -> error("Unsupported condition")
                }
                if (condition != null && !allowed(condition)) { results.add(JsonNull); errors.add(JsonNull) }
                else try {
                    val result = execute(connection, value["stmt"]!!.jsonObject)
                    results.add(result); errors.add(JsonNull)
                } catch (_: Exception) {
                    results.add(JsonNull); errors.add(buildJsonObject { put("code", "SQLITE_CONSTRAINT"); put("message", "Fixture statement failed") })
                }
            }
            return buildJsonObject { put("type", "batch"); put("result", buildJsonObject {
                put("step_results", JsonArray(results)); put("step_errors", JsonArray(errors))
            }) }
        }
        private fun execute(connection: Connection, statement: JsonObject): JsonObject {
            val sql = statement["sql"]!!.jsonPrimitive.content
            if (failAt?.let(sql::contains) == true) error("Injected statement failure")
            return connection.prepareStatement(sql).use { query ->
                statement["args"]!!.jsonArray.forEachIndexed { index, item ->
                    val arg = item.jsonObject
                    when (arg["type"]!!.jsonPrimitive.content) {
                        "text" -> query.setString(index + 1, arg["value"]!!.jsonPrimitive.content)
                        "integer" -> query.setLong(index + 1, arg["value"]!!.jsonPrimitive.long)
                        "blob" -> query.setBytes(index + 1, Base64.getDecoder().decode(arg["base64"]!!.jsonPrimitive.content))
                    }
                }
                val rows = mutableListOf<JsonElement>()
                val columns = mutableListOf<JsonElement>()
                val hasRows = query.execute()
                if (hasRows) query.resultSet.use { result ->
                    val count = result.metaData.columnCount
                    repeat(count) { columns.add(buildJsonObject { put("name", result.metaData.getColumnName(it + 1)) }) }
                    while (result.next()) rows.add(buildJsonArray {
                        for (i in 1..count) add(buildJsonObject {
                            when (val value = result.getObject(i)) {
                                is ByteArray -> { put("type", "blob"); put("base64", Base64.getEncoder().encodeToString(value)) }
                                is Number -> { put("type", "integer"); put("value", value.toString()) }
                                null -> put("type", "null")
                                else -> { put("type", "text"); put("value", value.toString()) }
                            }
                        })
                    })
                }
                val affected = if (hasRows) 0 else query.updateCount.coerceAtLeast(0)
                if (sql.startsWith("INSERT INTO foodrun_rows")) rowWrites += affected
                buildJsonObject { put("cols", JsonArray(columns)); put("rows", JsonArray(rows)); put("affected_row_count", affected); put("last_insert_rowid", JsonNull) }
            }
        }
        override fun close() { server.stop(0); executor.shutdownNow(); connection.close(); directory.deleteRecursively() }
    }
}
