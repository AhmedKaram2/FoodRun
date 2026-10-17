package com.karim.foodrun.server

import com.google.cloud.firestore.FirestoreOptions
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class FirestoreStreamingRestoreTest {
    @Test fun largeStoreRestoresAndBuildsItsCompactCacheWithinTheProductionHeapLimit() {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue(host != null, "Run with the Firestore emulator for bounded-memory coverage")
        require(host.startsWith("127.0.0.1:") || host.startsWith("localhost:"))
        val namespace = "stream-${UUID.randomUUID()}"
        val client = FirestoreOptions.newBuilder().setProjectId("demo-foodrun").setEmulatorHost(host).build().service
        FirestoreStore(client, namespace, true).use { store ->
            store.load()
            // Decoded data exceeds the entire production heap. Keep only a seed
            // batch here; the child process must restore without a full row list.
            for (batch in (0 until 4_400).chunked(200)) {
                store.commit(batch.map { index -> StoredRow("account_records", listOf("fixture:$index", "$index:" + "x".repeat(64 * 1024))) }.associateBy { it.id })
            }
        }
        repeat(2) { // First canonical restore, then the published compact cache.
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath,
                "-Xmx192m", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-XX:MaxDirectMemorySize=32m",
                "-cp", System.getProperty("foodrun.test.runtimeClasspath"), FirestoreRestoreProbe::class.java.name, namespace)
                .redirectErrorStream(true).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Bounded restore exceeded 60 seconds") }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            assertTrue(output.contains("restored=4400"), output)
        }
    }
}

/** Forked by the test above; production Firebase credentials are never used. */
object FirestoreRestoreProbe {
    @JvmStatic fun main(args: Array<String>) {
        val host = requireNotNull(System.getenv("FIRESTORE_EMULATOR_HOST"))
        require(host.startsWith("127.0.0.1:") || host.startsWith("localhost:"))
        val client = FirestoreOptions.newBuilder().setProjectId("demo-foodrun").setEmulatorHost(host).build().service
        val directory = Files.createTempDirectory("foodrun-stream-restore").toFile()
        try {
            RoomDatabase(directory, FirestoreStore(client, args.single())).use { db ->
                check(db.available)
                check(db.record("fixture:4399")!!.startsWith("4399:"))
                DriverManager.getConnection("jdbc:sqlite:${File(directory, "rooms.sqlite").absolutePath}").use { connection ->
                    connection.createStatement().use { statement -> statement.executeQuery("SELECT COUNT(*) FROM account_records").use { result ->
                        result.next(); check(result.getInt(1) == 4_400); println("restored=${result.getInt(1)}")
                    } }
                }
            }
        } finally { directory.deleteRecursively() }
    }
}
