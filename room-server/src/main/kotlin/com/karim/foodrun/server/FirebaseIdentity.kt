package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.io.File
import java.util.Properties

/** No admin key is needed: every cloud operation uses this user's Firebase credentials and rules. */
interface IdentityProvider {
    fun signIn(email: String, password: String, register: Boolean): CloudIdentity
    fun exchange(idToken: String): CloudIdentity
    fun refresh(refreshToken: String): CloudIdentity
    fun profile(identity: CloudIdentity): FoodProfile?
    fun saveProfile(identity: CloudIdentity, profile: FoodProfile)
    fun resetPassword(email: String)
    fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String)
}
data class CloudIdentity(val userId: String, val idToken: String, val refreshToken: String = "", val name: String = "")

class FirebaseIdentity(private val apiKey: String, private val project: String) : IdentityProvider {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val json = Json { ignoreUnknownKeys = true }
    init { require(project.matches(Regex("[a-z0-9-]+")) && apiKey.isNotBlank()) }
    private fun send(url: String, method: String, body: JsonObject? = null, token: String = "", missing: Boolean = false): JsonObject {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
        if (token.isNotEmpty()) request.header("Authorization", "Bearer $token")
        request.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) } ?: HttpRequest.BodyPublishers.noBody())
        val response = try { http.send(request.build(), HttpResponse.BodyHandlers.ofString()) }
            catch (_: Exception) { throw IllegalStateException("Firebase is unreachable. Check the hub's internet connection and retry.") }
        if (missing && response.statusCode() == 404) return JsonObject(emptyMap())
        if (response.statusCode() !in 200..299) {
            val message = runCatching { json.parseToJsonElement(response.body()).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull().orEmpty()
            throw IllegalArgumentException(when {
                "EMAIL_EXISTS" in message -> "This email already has an account. Sign in with your Intrvioo password."
                "INVALID_LOGIN_CREDENTIALS" in message || "INVALID_PASSWORD" in message || "EMAIL_NOT_FOUND" in message -> "Email or password is incorrect."
                "WEAK_PASSWORD" in message -> "Use a password with at least 6 characters."
                "TOO_MANY_ATTEMPTS" in message -> "Too many attempts. Wait a moment and try again."
                response.statusCode() == 403 -> "Firebase denied this operation. Check the FoodRun Firestore rules and account permissions."
                response.statusCode() == 401 -> "Your Firebase session expired. Sign in again."
                else -> "Firebase could not complete this operation (${response.statusCode()}). Check authentication configuration."
            })
        }
        return json.parseToJsonElement(response.body()).jsonObject
    }
    private fun auth(method: String, body: JsonObject) = send("https://identitytoolkit.googleapis.com/v1/accounts:$method?key=$apiKey", "POST", body)
    override fun signIn(email: String, password: String, register: Boolean): CloudIdentity {
        val r = auth(if (register) "signUp" else "signInWithPassword", buildJsonObject {
            put("email", email); put("password", password); put("returnSecureToken", true)
        })
        return CloudIdentity(r.string("localId"), r.string("idToken"), r.string("refreshToken"), r.string("displayName"))
    }
    override fun exchange(idToken: String): CloudIdentity {
        require(idToken.length in 100..16000) { "Sign in with Firebase first." }
        val user = auth("lookup", buildJsonObject { put("idToken", idToken) })["users"]?.jsonArray?.singleOrNull()?.jsonObject
            ?: error("Firebase account not found.")
        return CloudIdentity(user.string("localId"), idToken, name = user.string("displayName"))
    }
    override fun refresh(refreshToken: String): CloudIdentity {
        val request = HttpRequest.newBuilder(URI("https://securetoken.googleapis.com/v1/token?key=$apiKey"))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, Charsets.UTF_8))).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            catch (_: Exception) { error("Firebase is unreachable. Retry when the hub is online.") }
        require(response.statusCode() == 200) { "Your Firebase session expired. Sign in again." }
        val r = json.parseToJsonElement(response.body()).jsonObject
        return CloudIdentity(r.string("user_id"), r.string("id_token"), r.string("refresh_token"))
    }
    private fun userPath(uid: String): String {
        require(uid.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/users/$uid"
    }
    override fun profile(identity: CloudIdentity): FoodProfile? {
        val fields = send(userPath(identity.userId), "GET", token = identity.idToken, missing = true)["fields"]?.jsonObject ?: return null
        val saved = fields["foodRunProfile"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return null
        return orderJson.decodeFromString<FoodProfile>(saved).copy(userId = identity.userId)
    }
    override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) {
        profile.validate()
        // A field mask preserves all existing Intrvioo fields, credits, interviews, and profile data.
        send(userPath(identity.userId) + "?updateMask.fieldPaths=foodRunProfile", "PATCH", buildJsonObject {
            putJsonObject("fields") { putJsonObject("foodRunProfile") { put("stringValue", orderJson.encodeToString(profile)) } }
        }, identity.idToken)
    }
    override fun resetPassword(email: String) {
        auth("sendOobCode", buildJsonObject { put("requestType", "PASSWORD_RESET"); put("email", email) })
    }
    override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) {
        val path = userPath(identity.userId) + "/foodrunHubs/$hubId/records/$key"
        send(path, "PATCH", buildJsonObject { putJsonObject("fields") { putJsonObject("payload") { put("stringValue", value) } } }, identity.idToken)
    }
    companion object {
        fun configured(): FirebaseIdentity? {
            val props = Properties().apply {
                val file = File(System.getenv("FOODRUN_FIREBASE_CONFIG") ?: ".local/firebase.properties")
                if (file.isFile) file.inputStream().use(::load)
            }
            val key = System.getenv("FOODRUN_FIREBASE_API_KEY") ?: props.getProperty("apiKey", "")
            val project = System.getenv("FOODRUN_FIREBASE_PROJECT_ID") ?: props.getProperty("projectId", "")
            return if (key.isBlank() || project.isBlank()) null else FirebaseIdentity(key, project)
        }
    }
}
private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content.orEmpty()
