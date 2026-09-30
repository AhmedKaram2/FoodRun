package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import kotlinx.serialization.json.*
import java.util.Base64

internal class SignInRequired : IllegalArgumentException("Please sign out and sign in again to reconnect your FoodRun account.")

/** Only applies to FoodRun. The shared Firebase project's other apps are unaffected. */
internal object SessionReset {
    const val MARKER = "auth:last-session-reset"
    fun cutoff(): Long = System.getenv("FOODRUN_AUTH_VALID_AFTER")?.toLong()?.also { require(it > 0) } ?: 0L

    // Called only AFTER Firebase's account lookup has accepted this exact ID token.
    // auth_time stays unchanged on token refresh; iat does not prove a new login.
    fun requireFreshLogin(verifiedIdToken: String, validAfter: Long) {
        if (validAfter == 0L) return
        val authenticatedAt = runCatching {
            val payload = Base64.getUrlDecoder().decode(verifiedIdToken.split('.')[1]).toString(Charsets.UTF_8)
            orderJson.parseToJsonElement(payload).jsonObject["auth_time"]?.jsonPrimitive?.longOrNull
        }.getOrNull() ?: throw SignInRequired()
        if (authenticatedAt < validAfter) throw SignInRequired()
    }

    /** Runs before opening the listening port. Batches keep Firestore writes within its atomic limit. */
    fun apply(db: RoomDatabase, validAfter: Long): Pair<Int, Int>? {
        if (validAfter == 0L || (db.record(MARKER)?.toLong() ?: 0L) >= validAfter) return null
        var accounts = 0
        db.records("identity:").chunked(50).forEach { batch ->
            db.transaction { batch.forEach { db.deleteRecord(it.first) } }
            accounts += batch.size
        }
        var rooms = 0
        while (true) {
            val removed = db.deleteSessionBatch()
            rooms += removed
            if (removed == 0) break
        }
        // Retain memberships; fresh sign-in issues new room tokens through AccountService.home.
        db.putRecord(MARKER, validAfter.toString())
        return accounts to rooms
    }
}
