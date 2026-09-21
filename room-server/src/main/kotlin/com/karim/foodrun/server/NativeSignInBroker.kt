package com.karim.foodrun.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** One-use browser-to-app handoff. Only a code travels in the callback URL. */
class NativeSignInBroker(private val validate: (String) -> Unit, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Ticket(val challenge: String, val token: String, val expires: Long)
    private val tickets = mutableMapOf<String, Ticket>()
    @Synchronized fun complete(challenge: String, firebaseToken: String): String {
        require(challenge.matches(Regex("[a-f0-9]{64}"))) { "Invalid sign-in request. Start again in the app." }
        require(firebaseToken.length in 100..16000) { "Sign in with Google first." }
        tickets.entries.removeAll { it.value.expires <= clock() }
        require(tickets.size < 100) { "Sign-in is busy. Try again shortly." }
        validate(firebaseToken)
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        tickets[code] = Ticket(challenge, firebaseToken, clock() + 5 * 60_000)
        return code
    }
    @Synchronized fun exchange(code: String, verifier: String): String {
        require(code.length == 43 && verifier.matches(Regex("[A-Za-z0-9_-]{43,128}"))) { "Invalid sign-in callback." }
        val ticket = requireNotNull(tickets[code]) { "Sign-in expired. Start again in the app." }
        require(ticket.expires > clock()) { "Sign-in expired. Start again in the app." }
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(challenge.toByteArray(), ticket.challenge.toByteArray())) { "Sign-in does not match this device." }
        tickets.remove(code)
        return ticket.token
    }
}
