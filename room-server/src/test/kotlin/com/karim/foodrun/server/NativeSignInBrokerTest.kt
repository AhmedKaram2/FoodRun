package com.karim.foodrun.server

import java.security.MessageDigest
import kotlin.test.*

class NativeSignInBrokerTest {
    private val verifier = "a".repeat(64)
    private val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).joinToString("") { "%02x".format(it) }
    @Test fun onlyTheInitiatingAppCanExchangeTheCodeOnce() {
        val token = "firebase-token".repeat(20)
        var validated = ""
        val broker = NativeSignInBroker({ validated = it })
        val code = broker.complete(challenge, token)
        assertEquals(token, validated)
        assertFalse(code.contains(token))
        assertFailsWith<IllegalArgumentException> { broker.exchange(code, "b".repeat(64)) }
        assertEquals(token, broker.exchange(code, verifier))
        assertFailsWith<IllegalArgumentException> { broker.exchange(code, verifier) }
    }
    @Test fun expiredAndInvalidTokensCannotSignIn() {
        var now = 0L
        val broker = NativeSignInBroker({ require(it.startsWith("valid")) }, { now })
        assertFailsWith<IllegalArgumentException> { broker.complete(challenge, "x".repeat(200)) }
        val code = broker.complete(challenge, "valid".repeat(40))
        now = 300001
        assertFailsWith<IllegalArgumentException> { broker.exchange(code, verifier) }
    }
}
