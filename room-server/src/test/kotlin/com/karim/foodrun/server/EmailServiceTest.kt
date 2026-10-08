package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.Base64
import kotlin.test.*

class EmailServiceTest {
    @Test fun expiredSenderAuthorizationPreservesQueuedMailAndDoesNotConsumeSendQuota() = RoomFixture(Identity(),emailEnabled = true).use { f ->
        reminder(f)
        val first = assertNotNull(f.service.nextEmail(1))
        repeat(8) { index ->
            val attempt = if(index == 0) first else assertNotNull(f.service.nextEmail(1))
            f.service.finishEmail(attempt,EmailResult.AUTH_REQUIRED)
            assertTrue(f.db.record("email-job:${first.job.id}") != null)
            assertNull(f.db.record("email-result:${first.job.id}"))
            f.now += 300_001
        }
        val recovered = assertNotNull(f.service.nextEmail(1))
        f.service.finishEmail(recovered,EmailResult.SENT)
        assertTrue(f.db.record("email-result:${first.job.id}")!!.endsWith(":sent"))
    }
    @Test fun invalidGrantIsClassifiedWithoutAttemptingToSendOrLoggingProviderCredentials() {
        val response = com.google.api.client.http.HttpResponseException.Builder(400,"Bad Request",com.google.api.client.http.HttpHeaders())
            .setContent("{\"error\":\"invalid_grant\"}").build()
        val sender = GmailEmailSender({ throw java.io.IOException("Refresh failed",response) },"https://intrvioo.com","https://api.example.test") { error("Must not send without authorization") }
        val job = EmailJob("a","user","room",1,"Invitation","Body",0)
        assertEquals(EmailResult.AUTH_REQUIRED,sender.send(EmailDelivery(job,"member@example.test")))
    }
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
    private fun reminder(f: RoomFixture): RoomReply {
        val member = f.placed(); f.pay()
        assertTrue(f.db.records("email-job:").isEmpty())
        val command = f.command(f.owner, CommandKind.REMIND_PAYMENT).copy(memberId = member.memberId)
        f.execute(command); f.execute(command)
        return member
    }

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
    @Test fun explicitPaymentReminderIsQueuedOnceAndRetriedDurablyWithQuota() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        reminder(f)
        assertEquals(1, f.db.records("email-job:").size)
        val first = assertNotNull(f.service.nextEmail(1))
        assertEquals("Member@example.test", first.address)
        assertNull(f.service.nextEmail(2)) // lease prevents a second worker from taking the same job
        f.service.finishEmail(first, EmailResult.RETRY)
        f.restart(); assertNull(f.service.nextEmail(2))
        f.now += 120_000
        assertNull(f.service.nextEmail(1)) // rolling limit includes failed attempts
        val retry = assertNotNull(f.service.nextEmail(2))
        assertEquals(first.job.id, retry.job.id)
        f.service.finishEmail(retry, EmailResult.SENT)
        assertTrue(f.db.records("email-job:").isEmpty())
        assertTrue(f.db.record("email-result:${first.job.id}")!!.endsWith(":sent"))
        assertNull(f.service.nextEmail(2))
    }
    @Test fun disabledEmailDoesNotBuildAnOutbox() = RoomFixture(Identity()).use { f ->
        f.join(); f.execute(select(f))
        assertTrue(f.db.records("email-job:").isEmpty())
        assertNull(f.service.nextEmail(100))
    }
    @Test fun blockedRecipientsAreSkippedBeforeDelivery() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        reminder(f)
        f.db.putRecord("admin:disabled:Member", "true")
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun expiredEmailIsNotSentAfterTheServerWakes() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        reminder(f)
        f.now += 86_400_001
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun changedVerifiedAddressIsResolvedAtDeliveryTime() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        reminder(f)
        EmailContacts.capture(f.db, CloudIdentity("Member", "fixture", email = "new@example.test", emailVerified = true), f.now)
        assertEquals("new@example.test", assertNotNull(f.service.nextEmail(100)).address)
    }
    @Test fun invitationStaysInAppWithoutSendingEmail() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        val owner = login(f, "fixture-owner"); val friend = login(f, "friend")
        val invite = RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = owner.identityToken,
            roomId = f.owner.room!!.id, token = f.owner.token, identity = IdentityRequest(IdentityAction.INVITE, userId = "friend"))
        f.execute(invite); f.execute(invite.copy(commandId = f.id()))
        assertTrue(f.db.records("email-job:").isEmpty())
        val id = f.db.records("invitation:friend:").single().first.removePrefix("invitation:friend:")
        f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = friend.identityToken,
            identity = IdentityRequest(IdentityAction.ACCEPT_INVITE, invitationId = id)))
        assertNull(f.service.nextEmail(100))
    }
    @Test fun removedRoomMemberCannotReceiveQueuedEmail() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        val member = reminder(f)
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(members = room.members.map { if (it.id == member.memberId) it.copy(removed = true) else it }))
        assertNull(f.service.nextEmail(100))
    }
    @Test fun roomAndPaymentStatusChangesOnlyCreateInAppNotifications() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        val member = f.placed(); f.pay()
        val declared = f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = f.state(member).receipts.single { r -> r.memberId == member.memberId }.balance, text = "Cash payment") }
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = declared.room!!.transfers.single().id) }
        f.send(f.owner, CommandKind.FULFILL)
        assertTrue(f.db.records("notification:").isNotEmpty())
        assertTrue(f.db.records("email-job:").isEmpty())
        assertNull(f.service.nextEmail(100))
    }
    @Test fun queuedLegacyStatusAndInvitationEmailsAreDiscardedAfterRestart() = RoomFixture(Identity(), emailEnabled = true).use { f ->
        val member = reminder(f)
        val room = f.state().room!!
        val jobs = listOf(
            EmailJob("legacy-status", "fixture-owner", room.id, room.orderNumber, "Order update", "Order selected", f.now),
            EmailJob("legacy-payment", "fixture-owner", room.id, room.orderNumber, "Payment update", "Payment received", f.now),
            EmailJob("legacy-invite", "fixture-owner", room.id, room.orderNumber, "Invitation", "Join the room", f.now, invitationId = "old-invitation"),
        )
        jobs.forEach { f.db.putRecord("email-job:${it.id}", orderJson.encodeToString(it)) }
        f.restart()
        val delivery = assertNotNull(f.service.nextEmail(100))
        assertEquals(member.memberId, delivery.job.reminderMemberId)
        f.service.finishEmail(delivery, EmailResult.SENT)
        assertNull(f.service.nextEmail(100))
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun gmailUsesFixedSenderUtf8AndNoSessionCredentials() {
        val delivery = EmailDelivery(EmailJob("a".repeat(40), "user", "private-room-id", 1, "تم استلام دفعتك", "Your payment is confirmed.", 1000), "recipient@example.test")
        val sender = GmailEmailSender({ "fixture-access-token" }, "https://intrvioo.com", "https://api.example.test") { request ->
            assertEquals("https://gmail.googleapis.com/gmail/v1/users/foodruncollection@gmail.com/messages/send", request.uri().toString())
            assertEquals("Bearer fixture-access-token", request.headers().firstValue("Authorization").get())
            GmailApiResponse(200)
        }
        val mime = sender.mime(delivery)
        assertContains(mime, "From: Intrvioo <foodruncollection@gmail.com>")
        assertContains(mime, "To: recipient@example.test\r\n")
        assertContains(mime, "Subject: =?UTF-8?B?")
        val body = String(Base64.getMimeDecoder().decode(mime.substringAfter("\r\n\r\n")), Charsets.UTF_8)
        assertContains(body, "https://intrvioo.com?hub=https%3A%2F%2Fapi.example.test")
        assertFalse(body.contains("private-room-id"))
        assertFalse(mime.contains("fixture-access-token"))
        assertEquals(EmailResult.SENT, sender.send(delivery))
        assertFailsWith<IllegalArgumentException> { sender.mime(delivery.copy(address = "a@example.test\r\nBcc: victim@example.test")) }
        assertEquals(EmailResult.RETRY, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { GmailApiResponse(429) }.send(delivery))
        assertEquals(EmailResult.FAILED, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { GmailApiResponse(400) }.send(delivery))
        assertEquals(EmailResult.FAILED, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { GmailApiResponse(403, listOf("accessNotConfigured")) }.send(delivery))
        assertEquals(EmailResult.RETRY, GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") { GmailApiResponse(403, listOf("userRateLimitExceeded")) }.send(delivery))
        assertNull(GmailEmailSender.configured { null })
        assertFailsWith<IllegalArgumentException> { GmailEmailSender.configured { if (it == "FOODRUN_EMAIL_ENABLED") "true" else null } }
    }

    @Test fun optedOutRecipientGetsAppReminderWithoutEmailAndRepeatIsIdempotent() = RoomFixture(Identity(),emailEnabled = true).use { f ->
        val member = f.placed(); f.pay()
        val user = login(f,"Member")
        f.execute(RoomCommand(commandId = f.id(),kind = CommandKind.SET_NOTIFICATION_PREFERENCES,identityToken = user.identityToken,notificationPreferences = NotificationPreferences(false,false)))
        val command = f.command(f.owner,CommandKind.REMIND_PAYMENT).copy(memberId = member.memberId)
        assertEquals("REMINDER_NOTIFIED",f.execute(command).code); assertEquals("REMINDER_NOTIFIED",f.execute(command).code)
        assertTrue(f.db.records("email-job:").isEmpty()); assertNull(f.service.nextPush())
        val inbox = f.service.notificationRequest(NotificationRequest(user.identityToken),true).notifications
        assertEquals(1,inbox.count { it.kind == "payment_reminder" })
        assertFalse(f.service.execute(command.copy(commandId = f.id())).ok)
    }
}
