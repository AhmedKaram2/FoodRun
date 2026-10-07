package com.karim.foodrun.server

import com.google.auth.oauth2.UserCredentials
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

internal data class GmailApiResponse(val status: Int, val reasons: List<String> = emptyList())

internal class GmailEmailSender(
    private val accessToken: () -> String,
    private val appUrl: String,
    private val apiUrl: String,
    private val transport: (HttpRequest) -> GmailApiResponse = { request ->
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val reasons = if (response.statusCode() == 403) runCatching {
            Json.parseToJsonElement(response.body()).jsonObject["error"]?.jsonObject?.get("errors")?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["reason"]?.jsonPrimitive?.content }
        }.getOrDefault(emptyList()) else emptyList()
        GmailApiResponse(response.statusCode(), reasons)
    },
) : EmailSender {
    init {
        for (url in listOf(appUrl, apiUrl)) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/")) {
                "Email links require public HTTPS origins."
            }
        }
    }
    override fun send(delivery: EmailDelivery): EmailResult {
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime(delivery).toByteArray(Charsets.UTF_8))
        // Using the exact mailbox instead of 'me' also rejects credentials for another account.
        val request = HttpRequest.newBuilder(URI("https://gmail.googleapis.com/gmail/v1/users/$SENDER/messages/send"))
            .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer ${accessToken()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject { put("raw", raw) }.toString())).build()
        val response = transport(request)
        return when (response.status) {
            in 200..299 -> EmailResult.SENT
            403 -> if (response.reasons.any { it in listOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "quotaExceeded") }) EmailResult.RETRY else EmailResult.FAILED
            408, 429, in 500..599 -> EmailResult.RETRY
            else -> EmailResult.FAILED
        }
    }
    internal fun mime(delivery: EmailDelivery): String {
        require(EmailContacts.valid(delivery.address)) { "Invalid email recipient." }
        val job = delivery.job
        require(job.id.matches(Regex("[a-f0-9]{1,64}")))
        val title = job.subject.replace(Regex("[\\r\\n\\p{Cc}]"), " ")
        val encodedTitle = title.codePoints().toArray().take(160).chunked(10).joinToString("\r\n ") { points ->
            val text = String(points.toIntArray(), 0, points.size)
            "=?UTF-8?B?${Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))}?="
        }
        // Payment actions require sign-in; room IDs and credentials never appear in mail links.
        val link = "${appUrl.trimEnd('/')}?hub=${URLEncoder.encode(apiUrl.trimEnd('/'), Charsets.UTF_8)}"
        val reason = if(job.friendGroupId.isNotEmpty()) "Intrvioo sends this invitation at the friend group creator's request." else "Intrvioo sends this payment reminder at the chosen payer's request."
        val body = "${job.body}\n\nIntrvioo: $link\n\n$reason\nReply to this email if you need help."
        return listOf("From: Intrvioo <$SENDER>", "To: ${delivery.address}", "Subject: $encodedTitle",
            "Date: ${DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(job.createdAt).atOffset(ZoneOffset.UTC))}",
            "Message-ID: <foodrun-${job.id}@gmail.com>", "Auto-Submitted: auto-generated", "MIME-Version: 1.0",
            "Content-Type: text/plain; charset=UTF-8", "Content-Transfer-Encoding: base64", "",
            Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.toByteArray(Charsets.UTF_8))).joinToString("\r\n")
    }
    companion object {
        const val SENDER = "foodruncollection@gmail.com"
        private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
        fun configured(env: (String) -> String? = System::getenv): GmailEmailSender? {
            if (env("FOODRUN_EMAIL_ENABLED") != "true") return null
            fun required(key: String) = requireNotNull(env(key)?.takeIf { it.isNotBlank() }) { "$key is required when email is enabled." }
            val credentials = UserCredentials.newBuilder().setClientId(required("FOODRUN_GMAIL_CLIENT_ID"))
                .setClientSecret(required("FOODRUN_GMAIL_CLIENT_SECRET")).setRefreshToken(required("FOODRUN_GMAIL_REFRESH_TOKEN")).build()
            return GmailEmailSender({ credentials.refreshIfExpired(); requireNotNull(credentials.accessToken).tokenValue },
                required("FOODRUN_EMAIL_APP_URL"), required("FOODRUN_EMAIL_API_URL"))
        }
    }
}
