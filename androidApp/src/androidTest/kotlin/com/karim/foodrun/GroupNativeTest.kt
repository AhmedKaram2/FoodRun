package com.karim.foodrun

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karim.foodrun.orders.CommandKind
import com.karim.foodrun.orders.HubPairing
import com.karim.foodrun.orders.RoomCommand
import com.karim.foodrun.orders.RoomReply
import com.karim.foodrun.orders.Menu
import com.karim.foodrun.orders.MenuCategory
import com.karim.foodrun.orders.MenuItem
import com.karim.foodrun.orders.Restaurant
import com.karim.foodrun.orders.orderJson
import com.karim.foodrun.shared.orders.GroupAction
import com.karim.foodrun.shared.orders.GroupController
import com.karim.foodrun.shared.orders.GroupFieldKey
import com.karim.foodrun.shared.orders.GroupReplyCallback
import com.karim.foodrun.shared.orders.GroupSubscription
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun encryptedDataSurvivesNewStoreAndNeverWritesPlaintext() {
        val key = "test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "$key.enc")
        try {
            val value = "Saved receipt: AED 42.00; account TEST-PRIVATE"
            assertTrue(GroupSecureStore(context).write(key, value))
            val first = file.readBytes()
            assertFalse(first.toString(Charsets.UTF_8).contains(value))
            assertEquals(value, GroupSecureStore(context).read(key))
            assertTrue(GroupSecureStore(context).write(key, value))
            assertFalse(first.contentEquals(file.readBytes())) // A fresh random GCM nonce for each save.
            assertEquals(value, GroupSecureStore(context).read(key))
        } finally { file.delete() }
    }

    @Test
    fun tamperedOrTruncatedEncryptedDataCannotBeAccepted() {
        val key = "test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "$key.enc")
        try {
            val store = GroupSecureStore(context)
            assertTrue(store.write(key, "Private receipt"))
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertTrue(runCatching { store.read(key) }.isFailure)
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(runCatching { store.read(key) }.isFailure)
            assertFalse(store.write("../outside", "Data"))
        } finally { file.delete() }
    }

    @Test
    fun boundedMenuReadAcceptsLimitAndRejectsOneExtraByte() {
        val data = ByteArray(16_384) { (it % 256).toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readBounded(data.size))
        assertTrue(runCatching { ByteArrayInputStream(data).readBounded(data.size - 1) }.isFailure)
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readBounded(0))
    }

    @Test
    fun activityRecreationKeepsTheSameControllerAndDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var original: GroupController
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[GroupViewModel::class.java].controller
                original.dispatch(GroupAction.CREATE)
                original.update(GroupFieldKey.HUB_URL, "https://10.0.2.2:8443")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = ViewModelProvider(activity)[GroupViewModel::class.java].controller
                assertSame(original, restored)
                assertEquals("https://10.0.2.2:8443", restored.state.fields.single { it.key == GroupFieldKey.HUB_URL }.value)
            }
        }
    }

    @Test
    fun realHubHttpsAndWebSocketResumeTheSameJoinedMembership() {
        val args = InstrumentationRegistry.getArguments()
        val hub = hubOrSkip()
        val platform = GroupAndroidPlatform(context)
        try {
            val code = args.getString("roomCode")?.takeIf { it.isNotBlank() } ?: createTestRoom(platform, hub)
            val join = RoomCommand(
                commandId = UUID.randomUUID().toString(),
                kind = CommandKind.JOIN,
                name = "Android check ${UUID.randomUUID().toString().take(6)}",
                code = code,
                guest = true,
            )
            val joined = request(platform, hub, join)
            assertTrue(joined.error, joined.ok)
            assertTrue(joined.token.isNotBlank())
            val snapshot = RoomCommand(
                commandId = UUID.randomUUID().toString(),
                kind = CommandKind.SNAPSHOT,
                roomId = requireNotNull(joined.room).id,
                token = joined.token,
            )
            val reconnect = request(platform, hub, snapshot)
            assertTrue(reconnect.error, reconnect.ok)
            assertEquals(joined.memberId, reconnect.memberId)
            assertEquals(joined.room?.id, reconnect.room?.id)
            val observed = AtomicReference<RoomReply>()
            val count = AtomicInteger()
            val ready = CountDownLatch(1)
            lateinit var subscription: GroupSubscription
            instrumentation.runOnMainSync {
                subscription = platform.watch(hub, orderJson.encodeToString(snapshot), object : GroupReplyCallback {
                    override fun complete(body: String, error: String) {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        if (error.isEmpty()) {
                            observed.set(orderJson.decodeFromString<RoomReply>(body))
                            count.incrementAndGet()
                            ready.countDown()
                        }
                    }
                })
            }
            assertTrue("Expected a real WebSocket room snapshot", ready.await(15, TimeUnit.SECONDS))
            assertEquals(joined.memberId, observed.get().memberId)
            assertEquals(joined.room?.id, observed.get().room?.id)
            instrumentation.runOnMainSync { subscription.cancel() }
            val cancelledCount = count.get()
            Thread.sleep(500)
            assertEquals("No events after cancel", cancelledCount, count.get())
        } finally { instrumentation.runOnMainSync { platform.close() } }
    }

    @Test
    fun wrongCertificateFingerprintRejectsTheRealHub() {
        val validHub = hubOrSkip()
        val wrongHub = validHub.copy(fingerprint = if (validHub.fingerprint == "0".repeat(64)) "1".repeat(64) else "0".repeat(64))
        val platform = GroupAndroidPlatform(context)
        val completed = CountDownLatch(1)
        val bodyResult = AtomicReference<String>()
        val errorResult = AtomicReference<String>()
        try {
            instrumentation.runOnMainSync {
                platform.request(wrongHub, "{}", object : GroupReplyCallback {
                    override fun complete(body: String, error: String) {
                        bodyResult.set(body)
                        errorResult.set(error)
                        completed.countDown()
                    }
                })
            }
            assertTrue(completed.await(25, TimeUnit.SECONDS))
            assertEquals("", bodyResult.get())
            assertTrue(errorResult.get().isNotBlank())
        } finally { instrumentation.runOnMainSync { platform.close() } }
    }

    private fun hubOrSkip(): HubPairing {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("hubUrl").orEmpty()
        val fingerprint = args.getString("fingerprint").orEmpty()
        assumeTrue("Pass hubUrl and fingerprint for real-hub checks", url.isNotEmpty() && fingerprint.isNotEmpty())
        return HubPairing(url, fingerprint)
    }

    private fun createTestRoom(platform: GroupAndroidPlatform, hub: HubPairing): String {
        val suffix = UUID.randomUUID().toString().take(6)
        val restaurant = Restaurant(
            id = UUID.randomUUID().toString(),
            name = "Android test kitchen",
            menu = Menu(
                categories = listOf(MenuCategory("mains", "Mains")),
                items = listOf(MenuItem("meal", "mains", "Test meal", basePriceMinor = 2_500)),
            ),
        )
        val result = request(
            platform,
            hub,
            RoomCommand(
                commandId = UUID.randomUUID().toString(),
                kind = CommandKind.CREATE,
                name = "Native owner $suffix",
                text = "Android native verification $suffix",
                restaurant = restaurant,
            ),
        )
        assertTrue(result.error, result.ok)
        return requireNotNull(result.room).code
    }

    private fun request(platform: GroupAndroidPlatform, hub: HubPairing, command: RoomCommand): RoomReply {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<String>()
        instrumentation.runOnMainSync {
            platform.request(hub, orderJson.encodeToString(command), object : GroupReplyCallback {
                override fun complete(body: String, error: String) {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    result.set(body)
                    failure.set(error)
                    latch.countDown()
                }
            })
        }
        assertTrue("Request timed out", latch.await(25, TimeUnit.SECONDS))
        assertEquals("", failure.get())
        return orderJson.decodeFromString(result.get())
    }
}
