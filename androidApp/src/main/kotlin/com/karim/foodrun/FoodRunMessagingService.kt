package com.karim.foodrun

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.karim.foodrun.orders.*
import com.karim.foodrun.shared.orders.GroupLibrary
import com.karim.foodrun.shared.orders.GroupReplyCallback

class FoodRunMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if(message.data["foodrun"] != "1" || !getSharedPreferences("push-settings", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        val item = runCatching { orderJson.decodeFromString<FoodNotification>(message.data["payload"].orEmpty()) }.getOrNull() ?: return
        if(!item.id.matches(Regex("[a-f0-9]{40}"))) return
        if(android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("foodrun-updates", "Orders and payments", NotificationManager.IMPORTANCE_HIGH))
        fun intent(action: String): PendingIntent {
            val value = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction("foodrun.notification.${item.id}.$action").putExtra("notificationId", item.id).putExtra("notificationAction", action)
            return PendingIntent.getActivity(this, (item.id + action).hashCode(), value, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this, "foodrun-updates").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(item.title).setContentText(item.body).setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setContentIntent(intent("open")).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        item.actions.take(3).forEach { notification.addAction(0, it.title, intent(it.id)) }
        manager.notify(item.id.hashCode(), notification.build())
    }
    override fun onNewToken(token: String) {
        if(!getSharedPreferences("push-settings", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        val library = runCatching { orderJson.decodeFromString<GroupLibrary>(GroupSecureStore(this).read("group-library-v1")) }.getOrNull() ?: return
        val hub = library.identityHub ?: return
        if(library.identityToken.isEmpty()) return
        val installation = getSharedPreferences("push-settings", Context.MODE_PRIVATE).getString("installation", null) ?: return
        val transport = GroupTransport(this)
        transport.request(hub, orderJson.encodeToString(NotificationRequest(library.identityToken, "register", token = token, platform = "android", installationId = installation, language = library.language)),
            object : GroupReplyCallback { override fun complete(body: String, error: String) { transport.close() } }, "notifications")
    }
}
