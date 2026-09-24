package com.karim.foodrun.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.karim.foodrun.orders.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal class FirebasePush(private val project: String, private val credentials: com.google.auth.oauth2.GoogleCredentials) : PushSender {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    override fun send(device: PushDevice, notification: FoodNotification): PushResult {
        credentials.refreshIfExpired()
        val body = buildJsonObject { put("message", message(device, notification)) }
        val request = HttpRequest.newBuilder(URI("https://fcm.googleapis.com/v1/projects/$project/messages:send"))
            .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer ${requireNotNull(credentials.accessToken).tokenValue}")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) return PushResult.SENT
        // Do not delete a token for project, APNs, IAM, quota, or payload errors.
        val error = runCatching { orderJson.parseToJsonElement(response.body()).jsonObject["error"]?.jsonObject }.getOrNull()
        val unregistered = error?.get("details")?.jsonArray?.any { it.jsonObject["errorCode"]?.jsonPrimitive?.content == "UNREGISTERED" } == true
        return if (unregistered) PushResult.INVALID_TOKEN else PushResult.RETRY
    }
    companion object {
        internal fun message(device: PushDevice, item: FoodNotification): JsonObject = buildJsonObject {
            put("token", device.token)
            putJsonObject("data") { put("foodrun", "1"); put("notificationId", item.id); put("payload", orderJson.encodeToString(item)) }
            when(device.platform) {
                "android" -> putJsonObject("android") { put("priority", "high"); put("ttl", "86400s"); put("collapse_key", item.id) }
                "web" -> putJsonObject("webpush") { putJsonObject("headers") { put("TTL", "86400"); put("Urgency", "high") } }
                "ios" -> putJsonObject("apns") {
                    putJsonObject("headers") { put("apns-priority", "10"); put("apns-push-type", "alert"); put("apns-collapse-id", item.id) }
                    putJsonObject("payload") { putJsonObject("aps") {
                        putJsonObject("alert") { put("title", item.title); put("body", item.body) }; put("sound", "default")
                        put("category", when(item.kind) { "all_submitted" -> "FOODRUN_ORDER"; "payment_sent" -> "FOODRUN_CONFIRM"; "payment_due", "restaurant_paid", "order_placed" -> "FOODRUN_PAY"; else -> "FOODRUN_OPEN" })
                    } }
                }
            }
        }
        fun configured(): FirebasePush? {
            val credential = System.getenv("FOODRUN_PUSH_CREDENTIALS") ?: System.getenv("FOODRUN_FIRESTORE_CREDENTIALS") ?: return null
            val project = System.getenv("FOODRUN_FIREBASE_PROJECT_ID") ?: return null
            val account = ServiceAccountCredentials.fromStream(credential.byteInputStream())
            require(account.projectId == project) { "Push credentials belong to a different project." }
            return FirebasePush(project, account.createScoped("https://www.googleapis.com/auth/firebase.messaging"))
        }
    }
}
