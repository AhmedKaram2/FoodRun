package com.karim.foodrun.server

import com.karim.foodrun.orders.*
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
