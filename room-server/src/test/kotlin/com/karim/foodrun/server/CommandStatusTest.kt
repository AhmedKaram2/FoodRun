package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class CommandStatusTest {
    private class Identity : IdentityProvider {
        override fun signIn(email: String,password: String,register: Boolean) = CloudIdentity(email.substringBefore('@'),"fixture",email = email,emailVerified = true)
        override fun exchange(idToken: String) = error("Unused")
        override fun refresh(refreshToken: String) = error("Unused")
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity,profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity,hubId: String,key: String,value: String) = Unit
    }
    private fun login(f: RoomFixture,name: String) = f.execute(RoomCommand(commandId = f.id(),kind = CommandKind.IDENTITY,
        identity = IdentityRequest(IdentityAction.SIGN_IN,email = "$name@example.test",password = "fixture-password")))
    @Test fun lostPaymentAcknowledgementCanBeReadAfterTokenRenewalWithoutAnotherTransfer() = RoomFixture(Identity()).use { f ->
        val member=f.placed();f.pay()
        val receipt=f.state(member).receipts.single { it.memberId==member.memberId }
        val payment=f.command(member,CommandKind.DECLARE_TRANSFER).copy(amount=receipt.balance,text="fixture cash payment")
        f.execute(payment);val before=f.state(member).room!!
        val signed=login(f,"Member")
        val query=RoomCommand(commandId=f.id(),kind=CommandKind.COMMAND_STATUS,identityToken=signed.identityToken,text=payment.commandId)
        val result=f.execute(query)
        assertEquals("COMMAND_FOUND",result.code)
        assertEquals(1,result.room!!.transfers.size)
        assertEquals(before,f.state(member).room)
        assertNull(f.db.recordedReply(query.commandId))
        val outsider=login(f,"Other")
        assertFalse(f.service.execute(query.copy(commandId=f.id(),identityToken=outsider.identityToken)).ok)
    }
    @Test fun unknownRequestDoesNotApplyAnyMutationAndAccountChangesRecoverPrivately() = RoomFixture(Identity()).use { f ->
        val user=login(f,"Alice")
        val unknown=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.COMMAND_STATUS,identityToken=user.identityToken,text=f.id()))
        assertEquals("COMMAND_UNKNOWN",unknown.code);assertNull(unknown.home)
        val save=RoomCommand(commandId=f.id(),kind=CommandKind.SET_NOTIFICATION_PREFERENCES,identityToken=user.identityToken,notificationPreferences=NotificationPreferences(emailEnabled=false))
        f.execute(save)
        val recovered=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.COMMAND_STATUS,identityToken=user.identityToken,text=save.commandId))
        assertFalse(recovered.home!!.notificationPreferences!!.emailEnabled)
        assertNull(recovered.room)
    }
}
