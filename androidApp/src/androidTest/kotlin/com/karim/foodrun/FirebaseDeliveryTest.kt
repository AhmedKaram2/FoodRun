package com.karim.foodrun

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in device check: a CLI helper sends only to this emulator's private token file. */
@RunWith(AndroidJUnit4::class)
class FirebaseDeliveryTest {
    @Test fun receiveActionablePushOnTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("foodrunPushSmoke") == "true")
        val context = instrumentation.targetContext
        assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val settings = context.getSharedPreferences("push-settings", Context.MODE_PRIVATE)
        val wasEnabled = settings.getBoolean("enabled", false)
        val file = File(context.cacheDir, "push-smoke-token")
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "f".repeat(40).hashCode()
        try {
            if(android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            settings.edit().putBoolean("enabled", true).commit()
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 40, TimeUnit.SECONDS)
            assertTrue(token.isNotBlank())
            file.writeText(token)
            val deadline = System.currentTimeMillis() + 90_000
            while(System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.id == id }) Thread.sleep(500)
            val notification = manager.activeNotifications.singleOrNull { it.id == id }?.notification
            assertNotNull("The test device did not receive its Firebase message", notification)
            assertEquals("Food Run delivery check", notification!!.extras.getString("android.title"))
            assertEquals(listOf("Copy order", "Share order"), notification.actions.map { it.title.toString() })
            assertTrue(notification.actions.all { it.actionIntent.isActivity })
        } finally {
            file.delete(); manager.cancel(id)
            settings.edit().putBoolean("enabled", wasEnabled).commit()
            FirebaseMessaging.getInstance().isAutoInitEnabled = wasEnabled
        }
    }
}
