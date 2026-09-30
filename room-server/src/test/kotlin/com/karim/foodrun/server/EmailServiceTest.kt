package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.Base64
import kotlin.test.*

class EmailServiceTest {
    private class Identity : IdentityProvider {
        var verified = true
        override fun signIn(email: String, password: String, register: Boolean) = account(email)
        override fun exchange(idToken: String) = account("$idToken@example.test")
        private fun account(email: String) = CloudIdentity(email.substringBefore('@'), "fixture-token", name = email.substringBefore('@'), email = email, emailVerified = verified)
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, identity.name, "+971501234567")
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun login(f: RoomFixture, user: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = user, email = "forged@example.test")))
    private fun select(f: RoomFixture): RoomCommand = f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = f.owner.memberId)

    @Test fun verifiedGoogleEmailIsPrivateAndSurvivesSignOutAndRestart() = RoomFixture(Identity()).use { f ->
        val signedIn = login(f, "friend")
        assertEquals("friend@example.test", EmailContacts.address(f.db, "friend"))
        assertFalse(orderJson.encodeToString(signedIn).contains("friend@example.test"))
        assertFalse(orderJson.encodeToString(signedIn).contains("forged@example.test"))
        f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = signedIn.identityToken,
            identity = IdentityRequest(IdentityAction.SIGN_OUT)))
        f.restart()
        assertEquals("friend@example.test", EmailContacts.address(f.db, "friend"))
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = signedIn.identityToken)).ok)
    }
    @Test fun unverifiedOrChangedUnverifiedIdentityRemovesOldDeliveryAddress() {
        val identity = Identity()
        RoomFixture(identity).use { f ->
            login(f, "friend"); identity.verified = false; login(f, "friend")
            assertNull(EmailContacts.address(f.db, "friend"))
        }
    }
    @Test fun notificationIsQueuedOnceAndRetriedDurablyWithQuota() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        f.join()
        val command = select(f); f.execute(command); f.execute(command)
        assertEquals(1, f.db.records("email-job:").size)
        val first = assertNotNull(f.service.nextEmail(2))
        assertEquals("fixture-owner@example.test", first.address)
        assertNull(f.service.nextEmail(2)) // lease prevents a second worker from taking the same job
        f.service.finishEmail(first, EmailResult.RETRY)
        f.restart(); assertNull(f.service.nextEmail(2))
        f.now += 120_000
        val retry = assertNotNull(f.service.nextEmail(2))
        assertEquals(first.job.id, retry.job.id)
        f.service.finishEmail(retry, EmailResult.SENT)
        assertTrue(f.db.records("email-job:").isEmpty())
        assertTrue(f.db.record("email-result:${first.job.id}")!!.endsWith(":sent"))
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Test order cancelled") }
        assertTrue(f.db.records("email-job:").isNotEmpty())
        assertNull(f.service.nextEmail(2)) // rolling limit includes failed attempts
    }
    @Test fun disabledEmailDoesNotBuildAnOutbox() = RoomFixture(Identity()).use { f ->
        f.join(); f.execute(select(f))
        assertTrue(f.db.records("email-job:").isEmpty())
        assertNull(f.service.nextEmail(100))
    }
    @Test fun blockedRecipientsAreSkippedBeforeDelivery() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        f.join(); f.execute(select(f))
        f.db.putRecord("admin:disabled:fixture-owner", "true")
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun expiredEmailIsNotSentAfterTheServerWakes() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        f.join(); f.execute(select(f))
        f.now += 86_400_001
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun changedVerifiedAddressIsResolvedAtDeliveryTime() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        f.join(); f.execute(select(f))
        EmailContacts.capture(f.db, CloudIdentity("fixture-owner", "fixture", email = "new@example.test", emailVerified = true), f.now)
        assertEquals("new@example.test", assertNotNull(f.service.nextEmail(100)).address)
    }
    @Test fun invitationIsDeduplicatedAndCancelledWhenAccepted() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        val owner = login(f, "fixture-owner"); val friend = login(f, "friend")
        val invite = RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = owner.identityToken,
            roomId = f.owner.room!!.id, token = f.owner.token, identity = IdentityRequest(IdentityAction.INVITE, userId = "friend"))
        f.execute(invite); f.execute(invite.copy(commandId = f.id()))
        assertEquals(1, f.db.records("email-job:").size)
        val id = f.db.records("invitation:friend:").single().first.removePrefix("invitation:friend:")
        f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = friend.identityToken,
            identity = IdentityRequest(IdentityAction.ACCEPT_INVITE, invitationId = id)))
        assertNull(f.service.nextEmail(100))
    }
    @Test fun removedRoomMemberCannotReceiveQueuedEmail() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        f.join(); f.execute(select(f))
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(members = room.members.map { if (it.id == f.owner.memberId) it.copy(removed = true) else it }))
        assertNull(f.service.nextEmail(100))
    }
    @Test fun gmailUsesFixedSenderUtf8AndNoSessionCredentials() {
        val delivery = EmailDelivery(EmailJob("a".repeat(40), "user", "private-room-id", 1, "تم استلام دفعتك", "Your payment is confirmed.", 1000), "recipient@example.test")
        val sender = GmailEmailSender({ "fixture-access-token" }, "https://intrvioo.com", "https://api.example.test") { request ->
            assertEquals("https://gmail.googleapis.com/gmail/v1/users/foodruncollection@gmail.com/messages/send", request.uri().toString())
            assertEquals("Bearer fixture-access-token", request.headers().firstValue("Authorization").get())
            200
        }
        val mime = sender.mime(delivery)
        assertContains(mime, "From: Food Run <foodruncollection@gmail.com>")
        assertContains(mime, "To: recipient@example.test\r\n")
        assertContains(mime, "Subject: =?UTF-8?B?")
        val body = String(Base64.getMimeDecoder().decode(mime.substringAfter("\r\n\r\n")), Charsets.UTF_8)
        assertContains(body, "https://intrvioo.com?hub=https%3A%2F%2Fapi.example.test")
        assertFalse(body.contains("private-room-id"))
        assertFalse(mime.contains("fixture-access-token"))
        assertEquals(EmailResult.SENT, sender.send(delivery))
        assertFailsWith<IllegalArgumentException> { sender.mime(delivery.copy(address = "a@example.test\r\nBcc: victim@example.test")) }
        assertEquals(EmailResult.RETRY, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { 429 }.send(delivery))
        assertEquals(EmailResult.FAILED, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { 400 }.send(delivery))
        assertNull(GmailEmailSender.configured { null })
        assertFailsWith<IllegalArgumentException> { GmailEmailSender.configured { if (it == "FOODRUN_EMAIL_ENABLED") "true" else null } }
    }
}
