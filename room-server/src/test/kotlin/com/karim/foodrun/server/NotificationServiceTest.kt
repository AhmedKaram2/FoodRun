package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class NotificationServiceTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email.substringBefore('@'), "token")
        override fun exchange(idToken: String) = CloudIdentity(idToken, "token")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, identity.userId, "+971501234567")
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    private fun signIn(f: RoomFixture, user: String) = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN, email = "$user@example.test", password = "fixture-password"))).identityToken
    private fun inbox(f: RoomFixture, user: String) = f.service.notificationRequest(NotificationRequest(signIn(f,user)), true).notifications
    private fun device(f: RoomFixture, user: String, token: String = "fixture-push-token-123456789", installation: String = "fixture-installation-123456") =
        f.service.notificationRequest(NotificationRequest(signIn(f,user), "register", token = token, platform = "android", installationId = installation), true)

    @Test fun submissionsNotifyOnlyThePayerAndTheLastOneProvidesCopyShareAndSendActions() = RoomFixture(Identity()).use { f ->
        val member = f.join()
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        f.cart(f.owner,1); f.cart(member,2)
        val chosen = inbox(f,"fixture-owner")
        assertEquals(1, chosen.count { it.kind == "order_submitted" })
        assertEquals(listOf("copy","share","order"), chosen.single { it.kind == "all_submitted" }.actions.map { it.id })
        assertTrue(inbox(f,"Member").isEmpty())
        f.service.snapshot(f.owner.room!!.id, f.owner.token)
        f.restart()
        assertEquals(chosen.map { it.id }.toSet(), inbox(f,"fixture-owner").map { it.id }.toSet())
    }
    @Test fun paymentSubmissionAndConfirmationDoNotDuplicateOnCommandRetry() = RoomFixture(Identity()).use { f ->
        val member = f.placed(); f.pay()
        val command = f.command(member, CommandKind.DECLARE_TRANSFER).copy(amount = 100, text = "Receipt reference")
        val sent = f.execute(command)
        f.execute(command)
        val notice = inbox(f,"fixture-owner").single { it.kind == "payment_sent" }
        assertEquals(sent.room!!.transfers.single().id, notice.transferId)
        assertEquals("confirm", notice.actions.first().id)
        assertFalse(notice.body.contains("AE07033"))
        f.send(f.owner, CommandKind.CONFIRM_TRANSFER) { it.copy(transferId = notice.transferId) }
        assertEquals(1, inbox(f,"Member").count { it.kind == "payment_received" })
        assertFalse(inbox(f,"Member").any { it.kind == "payment_sent" })
    }
    @Test fun notificationsAndReadReceiptsAreScopedToTheSignedInUser() = RoomFixture(Identity()).use { f ->
        f.join(); f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val item = inbox(f,"fixture-owner").single()
        val member = signIn(f,"Member")
        assertTrue(f.service.notificationRequest(NotificationRequest(member,"read",notificationId=item.id),true).notifications.isEmpty())
        assertFalse(inbox(f,"fixture-owner").single().read)
        assertFails { f.service.notificationRequest(NotificationRequest("invalid"),true) }
        f.service.notificationRequest(NotificationRequest(signIn(f,"fixture-owner"),"read",notificationId=item.id),true)
        assertTrue(inbox(f,"fixture-owner").single().read)
    }
    @Test fun outboxPersistsRetriesAndTokenReassignmentCannotSendAnotherUsersNotification() = RoomFixture(Identity()).use { f ->
        device(f,"fixture-owner")
        f.join(); f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val first = assertNotNull(f.service.nextPush())
        f.service.finishPush(first, PushResult.RETRY)
        assertNull(f.service.nextPush())
        f.now += 20_000; f.restart()
        assertEquals(first.job.notification.id, assertNotNull(f.service.nextPush()).job.notification.id)
        device(f,"Member")
        assertNull(f.service.nextPush())
    }
    @Test fun readNotificationsAndRemovedMembersDoNotReceiveQueuedPush() = RoomFixture(Identity()).use { f ->
        device(f,"fixture-owner")
        f.join(); f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val first = assertNotNull(f.service.nextPush())
        f.service.notificationRequest(NotificationRequest(signIn(f,"fixture-owner"),"read",notificationId=first.job.notification.id),true)
        assertNull(f.service.nextPush())
        assertTrue(f.db.records("push-job:").isEmpty())
    }
    @Test fun invalidTokensAreRemovedButTransientFailuresRemainRetryable() = RoomFixture(Identity()).use { f ->
        device(f,"fixture-owner"); f.join(); f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        val first = assertNotNull(f.service.nextPush())
        f.service.finishPush(first, PushResult.INVALID_TOKEN)
        assertTrue(f.db.records("push-device:").isEmpty()); assertNull(f.service.nextPush())
    }
    @Test fun registrationRejectsMalformedAndUnauthenticatedRequests() = RoomFixture(Identity()).use { f ->
        val token = signIn(f,"fixture-owner")
        assertFails { f.service.notificationRequest(NotificationRequest(token,"register",token="bad",platform="android",installationId="fixture-installation-123456"),true) }
        assertFails { f.service.notificationRequest(NotificationRequest(token,"register",token="fixture-push-token-123456789",platform="other",installationId="fixture-installation-123456"),true) }
        assertFails { f.service.notificationRequest(NotificationRequest(token,"register",token="fixture-push-token-123456789",platform="android",installationId="fixture-installation-123456"),false) }
    }
}
