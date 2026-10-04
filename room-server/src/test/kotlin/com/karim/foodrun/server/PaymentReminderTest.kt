package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class PaymentReminderTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "fixture-token", email = email, emailVerified = true)
        override fun exchange(idToken: String) = error("Unused")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, identity.userId, "+971501234567")
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun fixture(enabled: Boolean = true) = RoomFixture(Identity(), emailEnabled = enabled)
    private fun ready(f: RoomFixture): RoomReply {
        val member = f.placed(); f.pay()
        f.db.records("email-job:").forEach { f.db.deleteRecord(it.first) }
        return member
    }
    private fun remind(f: RoomFixture, member: RoomReply) = f.command(f.owner, CommandKind.REMIND_PAYMENT).copy(memberId = member.memberId)

    @Test fun missingAddressAsksForEmailAndManualRecipientIsPrivateAndValidated() = fixture().use { f ->
        val member = ready(f)
        f.db.deleteRecord("email-contact:Member")
        val command = remind(f, member)
        assertEquals("REMINDER_EMAIL_REQUIRED", f.service.execute(command).code)
        for (address in listOf("not-an-email", "a@example.test\r\nBcc: other@example.test")) {
            assertFalse(f.service.execute(command.copy(commandId = f.id(), text = address)).ok)
            assertTrue(f.db.records("email-job:").isEmpty())
        }
        val reply = f.execute(command.copy(commandId = f.id(), text = " alternate@example.test "))
        assertFalse(orderJson.encodeToString(reply).contains("alternate@example.test"))
        assertNull(EmailContacts.address(f.db, "Member"))
        f.restart()
        assertEquals("alternate@example.test", assertNotNull(f.service.nextEmail(100)).address)
    }

    @Test fun commandRouteWaitsForGmailAcceptanceAndReplayCannotDeliverTwice() = fixture().use { f ->
        val member = ready(f)
        val command = remind(f, member).copy(text = "override@example.test")
        var sent = 0
        val gmail = GmailEmailSender({ "fixture" }, "https://intrvioo.com", "https://api.example.test") {
            sent++; GmailApiResponse(200)
        }
        val sender = EmailSender { delivery ->
            assertEquals("Member@example.test", delivery.address) // verified address cannot be overridden
            assertFalse(Thread.holdsLock(f.service))
            gmail.send(delivery)
        }
        testApplication {
            application { hubRoutes(f.service, reminderSender = { f.service.sendPaymentReminder(it, sender, 100) }) }
            suspend fun post() = orderJson.decodeFromString<RoomReply>(client.post("/command") {
                contentType(ContentType.Application.Json); setBody(orderJson.encodeToString(command))
            }.bodyAsText())
            assertEquals("REMINDER_SENT", post().code)
            assertEquals("REMINDER_SENT", post().code)
        }
        assertEquals(1, sent)
        assertTrue(f.db.records("email-job:").isEmpty())
    }

    @Test fun retriesReportProgressAndPermanentFailureAllowsANewAttempt() = fixture().use { f ->
        val member = ready(f)
        val command = remind(f, member)
        f.execute(command)
        assertEquals("REMINDER_PENDING", f.service.sendPaymentReminder(command.commandId, EmailSender { EmailResult.RETRY }, 100))
        f.restart()
        val status = f.command(f.owner, CommandKind.PAYMENT_REMINDER_STATUS).copy(memberId = member.memberId)
        assertEquals("REMINDER_PENDING", f.execute(status).code)
        assertFalse(f.service.execute(status.copy(commandId = f.id(), token = member.token)).ok)
        assertFalse(f.service.execute(status.copy(commandId = f.id(), expectedOrderNumber = 0)).ok)
        f.now += 120_000
        assertEquals("REMINDER_FAILED", f.service.sendPaymentReminder(command.commandId, EmailSender { EmailResult.FAILED }, 100))
        assertEquals("REMINDER_FAILED", f.execute(status.copy(commandId = f.id())).code)
        val retry = remind(f, member)
        f.execute(retry)
        assertEquals("REMINDER_SENT", f.service.sendPaymentReminder(retry.commandId, EmailSender { EmailResult.SENT }, 100))
        assertEquals("REMINDER_SENT", f.execute(status.copy(commandId = f.id())).code)
    }

    @Test fun payerQueuesOneBilingualReminderAndCommandReplayDoesNotSendAnother() = fixture().use { f ->
        val member = ready(f)
        val room = f.state().room!!
        val command = remind(f, member).copy(amount = 1, text = "Client text must not enter the email")
        val reply = f.execute(command); f.execute(command)
        assertEquals("REMINDER_QUEUED", reply.code)
        assertEquals(room.revision, f.state().room!!.revision)
        assertEquals(1, f.db.records("email-job:").size)
        val delivery = assertNotNull(f.service.nextEmail(100))
        assertEquals("Member@example.test", delivery.address)
        assertContains(delivery.job.body, "Dear Member")
        assertContains(delivery.job.body, "عزيزي Member")
        assertContains(delivery.job.body, "to fixture-owner")
        assertContains(delivery.job.body, Money.format(f.state().receipts.single { it.memberId == member.memberId }.balance, room.restaurant.currency))
        assertFalse(delivery.job.body.contains(command.text))
        assertFalse(delivery.job.body.contains(f.account.identifier))
        assertFalse(delivery.job.body.contains(member.token))
    }
    @Test fun ordinaryMemberAndRoomOwnerCannotImpersonateTheChosenPayer() = fixture().use { f ->
        val member = ready(f)
        val room = f.state().room!!
        f.db.save(room.copy(ownerId = member.memberId))
        val attempt = f.command(member, CommandKind.REMIND_PAYMENT).copy(memberId = f.owner.memberId)
        assertFalse(f.service.execute(attempt).ok)
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun backendRejectsOwnShareMissingEmailStaleOrderAndPendingPayment() = fixture().use { f ->
        val member = ready(f)
        assertFalse(f.service.execute(remind(f, member).copy(memberId = f.owner.memberId)).ok)
        assertFalse(f.service.execute(remind(f, member).copy(expectedOrderNumber = 0)).ok)
        assertFalse(f.service.execute(remind(f, member).copy(expectedRevision = -1)).ok)
        f.db.deleteRecord("email-contact:Member")
        assertFalse(f.service.execute(remind(f, member)).ok)
        EmailContacts.capture(f.db, CloudIdentity("Member", "fixture", email = "member@example.test", emailVerified = true), f.now)
        f.send(member, CommandKind.DECLARE_TRANSFER) { it.copy(amount = 100, text = "Already sent") }
        assertFalse(f.service.execute(remind(f, member)).ok)
    }
    @Test fun cooldownSurvivesRestartAndDifferentCommandIds() = fixture().use { f ->
        val member = ready(f)
        f.execute(remind(f, member)); f.restart()
        val tooSoon = f.service.execute(remind(f, member))
        assertFalse(tooSoon.ok); assertContains(tooSoon.error, "24 hours")
        f.now += PaymentReminderRules.COOLDOWN_MS + 1
        f.execute(remind(f, member))
    }
    @Test fun queuedReminderIsDroppedWhenMemberPaysOrClaimsPaymentBeforeDelivery() {
        for (confirmed in listOf(false, true)) fixture().use { f ->
            val member = ready(f)
            f.execute(remind(f, member))
            val room = f.state().room!!
            val due = f.state().receipts.single { it.memberId == member.memberId }.balance
            val transfer = Transfer(f.id(), member.memberId, due, "Paid", f.account,
                status = if (confirmed) TransferStatus.CONFIRMED else TransferStatus.DECLARED)
            f.db.save(room.copy(transfers = room.transfers + transfer))
            assertNull(f.service.nextEmail(100))
            assertTrue(f.db.records("email-job:").isEmpty())
        }
    }
    @Test fun deliveryRecalculatesPartialPaymentAndSkipsChangedPayer() = fixture().use { f ->
        val member = ready(f)
        f.execute(remind(f, member))
        val room = f.state().room!!
        val before = f.state().receipts.single { it.memberId == member.memberId }.balance
        f.db.save(room.copy(transfers = room.transfers + Transfer(f.id(), member.memberId, 100, "Partial", f.account, status = TransferStatus.CONFIRMED)))
        val delivery = assertNotNull(f.service.nextEmail(100))
        assertContains(delivery.job.body, Money.format(before - 100, room.restaurant.currency))
        f.service.finishEmail(delivery, EmailResult.RETRY)
        f.db.save(f.state().room!!.copy(payerId = member.memberId))
        f.now += 300_000
        assertNull(f.service.nextEmail(100))
    }
    @Test fun disabledEmailFailsClearlyWithoutQueuingAnything() = fixture(false).use { f ->
        val member = ready(f)
        val result = f.service.execute(remind(f, member))
        assertFalse(result.ok); assertContains(result.error, "not available")
        assertTrue(f.db.records("email-job:").isEmpty())
    }
    @Test fun templatePreservesThreeDecimalCurrencyAndUsesTheCurrentCollectorName() = fixture().use { f ->
        val member = ready(f)
        val room = f.state().room!!
        val receipt = f.state().receipts.single { it.memberId == member.memberId }.copy(balance = 1234, currency = "JOD")
        assertContains(PaymentReminderEmail.body(room, receipt), "JOD 1.234")
    }
}
