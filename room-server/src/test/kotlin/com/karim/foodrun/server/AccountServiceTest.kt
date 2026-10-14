package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountServiceTest {
    @Test fun homeSpendingTracksRestaurantPaymentAndConfirmedMemberPaymentFromRoomCommands() = RoomFixture(FakeIdentityProvider()).use { f ->
        val member=f.placed();f.pay()
        f.send(f.owner,CommandKind.RECORD_PAYMENT) { it.copy(memberId=member.memberId,amount=3000,text="Received") }
        fun home(user: String)=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.IDENTITY,spendingTimeZone="Asia/Dubai",
            identity=IdentityRequest(IdentityAction.FIREBASE_SIGN_IN,firebaseToken="firebase-$user"))).home!!
        assertEquals(4500,home("fixture-owner").spending!!.periods.first().total)
        assertEquals(3000,home("Member").spending!!.periods.first().total)
    }
    @Test fun newGoogleMemberCanJoinAndSaveAaniWithoutASeparateContactPhone() = RoomFixture(FakeIdentityProvider()).use { f ->
        val signed = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY,
            identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "firebase-new-google")))
        assertEquals("", signed.home!!.profile.phone)
        val joined = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN, code = f.owner.room!!.code,
            name = signed.home!!.profile.name, identityToken = signed.identityToken))
        assertNotNull(joined.room)
        val account = ReceivingAccount("mobile", "new-google", "Aani", "٠٥٠١٢٣٤٥٦٧", method = PaymentMethod.AANI)
        val saved = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identityToken = signed.identityToken,
            identity = IdentityRequest(IdentityAction.SAVE_PROFILE, profile = signed.home!!.profile.copy(payment = account))))
        assertEquals("", saved.home!!.profile.phone)
        assertEquals("+971501234567", saved.home!!.profile.payment!!.identifier)
        assertEquals(1, saved.home!!.rooms.size)
    }
    private class FakeIdentityProvider : IdentityProvider {
        private val profiles = mutableMapOf<String, FoodProfile>()
        override fun signIn(email: String, password: String, register: Boolean) = identity(email.substringBefore('@'))
        override fun exchange(idToken: String) = identity(idToken.removePrefix("firebase-"))
        override fun refresh(refreshToken: String) = identity(refreshToken.removePrefix("refresh-"))
        override fun profile(identity: CloudIdentity) = profiles[identity.userId]
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) { profiles[identity.userId] = profile }
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
        private fun identity(uid: String) = CloudIdentity(uid, "id-$uid", "refresh-$uid", uid)
    }

    @Test fun sudaneseProfileLanguageSurvivesSavingAndServerRestart() = RoomFixture(FakeIdentityProvider()).use { f ->
        val signed=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.IDENTITY,
            identity=IdentityRequest(IdentityAction.FIREBASE_SIGN_IN,firebaseToken="firebase-sudanese-user")))
        val saved=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.IDENTITY,identityToken=signed.identityToken,
            identity=IdentityRequest(IdentityAction.SAVE_PROFILE,profile=signed.home!!.profile.copy(language="ar-SD"))))
        assertEquals("ar-SD",saved.home!!.profile.language)
        f.restart()
        val home=f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.HOME,identityToken=signed.identityToken))
        assertEquals("ar-SD",home.home!!.profile.language)
    }

    @Test fun walletSearchOnlyOffersCanonicalHoldersWhoCanReceiveTheSelectedCurrency() = RoomFixture(FakeIdentityProvider()).use { f ->
        fun register(email: String, profile: FoodProfile) = f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.IDENTITY,
            identity=IdentityRequest(IdentityAction.REGISTER,email=email,password="fixture-password",profile=profile)))
        val searcher=register("searcher@example.test",FoodProfile(name="Searcher"))
        val correct=register("correct-holder@example.test",FoodProfile(name="Ahmed Karam",payment=f.account))
        register("old-holder@example.test",FoodProfile(name="ahmed karam"))
        fun search(currency: String) = f.execute(RoomCommand(commandId=f.id(),kind=CommandKind.WALLET_PEOPLE,
            identityToken=searcher.identityToken,text="Ahmed Karam",currency=currency)).walletPeople!!
        assertEquals(listOf(FoodPerson(correct.home!!.profile.userId,"Ahmed Karam")),search("AED"))
        assertTrue(search("USD").isEmpty())
        f.restart()
        assertEquals(1,search("AED").size)
    }

    @Test fun configuredServerRequiresAccountForRoomCreationAndJoining() = RoomFixture(FakeIdentityProvider()).use { fixture ->
        val create = fixture.service.execute(RoomCommand(commandId = fixture.id(), kind = CommandKind.CREATE,
            name = "Anonymous", text = "Room", restaurant = fixture.restaurant))
        assertTrue(!create.ok)
        val join = fixture.service.execute(RoomCommand(commandId = fixture.id(), kind = CommandKind.JOIN,
            name = "Anonymous", code = fixture.owner.room!!.code))
        assertTrue(!join.ok)
        assertEquals(1, fixture.db.allRooms().size)
        assertEquals(1, fixture.state().room!!.members.size)
    }

    @Test fun registeredPeopleCanBeInvitedAndResumeTheirRoomFromHome() = RoomFixture(FakeIdentityProvider()).use { fixture ->
        fun register(email: String, name: String): RoomReply = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.IDENTITY,
            identity = IdentityRequest(
                action = IdentityAction.REGISTER, email = email, password = "secret12",
                profile = FoodProfile(name = name, phone = "+971501234567"),
            ),
        ))

        val organizer = register("organizer@example.com", "Organizer")
        val invited = register("friend@example.com", "Friend")
        val room = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.CREATE, identityToken = organizer.identityToken,
            name = "Organizer", text = "Friday lunch", restaurant = fixture.restaurant,
        ))

        val invitationReply = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.IDENTITY,
            roomId = room.room!!.id, token = room.token, identityToken = organizer.identityToken,
            identity = IdentityRequest(action = IdentityAction.INVITE, userId = invited.home!!.profile.userId),
        ))
        assertTrue(invitationReply.home!!.people.any { it.userId == invited.home!!.profile.userId })

        val invitedHome = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.HOME, identityToken = invited.identityToken,
        ))
        val invitation = assertNotNull(invitedHome.home?.invitations?.singleOrNull())
        val joined = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.IDENTITY, identityToken = invited.identityToken,
            identity = IdentityRequest(action = IdentityAction.ACCEPT_INVITE, invitationId = invitation.id),
        ))
        assertTrue(joined.token.isNotBlank())
        assertEquals("Friend", joined.room?.members?.single { it.id == joined.memberId }?.name)

        fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.APPROVE,
            roomId = room.room!!.id, token = room.token, memberId = joined.memberId,
            expectedRevision = joined.room!!.revision, expectedOrderNumber = joined.room!!.orderNumber,
        ))
        val resumed = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.HOME, identityToken = invited.identityToken,
        ))
        assertEquals(room.room!!.id, resumed.home?.rooms?.singleOrNull()?.roomId)
        assertEquals(joined.token, resumed.home?.rooms?.singleOrNull()?.token)
    }

    @Test fun favoriteOrdersPersistThroughTheProfileApi() = RoomFixture(FakeIdentityProvider()).use { fixture ->
        val registered = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.IDENTITY,
            identity = IdentityRequest(
                action = IdentityAction.REGISTER, email = "favorite@example.com", password = "secret12",
                profile = FoodProfile(name = "Favorite User", phone = "+971501234567"),
            ),
        ))
        val favorite = FavoriteOrder(
            id = "favorite-1", restaurantId = "restaurant-1", restaurantName = "Kitchen", title = "My usual",
            lines = listOf(FavoriteOrderLine(itemId = "burger", quantity = 2, label = "Burger")), savedAt = 100,
        )
        val saved = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.IDENTITY, identityToken = registered.identityToken,
            identity = IdentityRequest(
                action = IdentityAction.SAVE_PROFILE,
                profile = FoodProfile(name = "Favorite User", phone = "+971501234567", favoriteOrders = listOf(favorite)),
            ),
        ))
        assertEquals(favorite, saved.home?.profile?.favoriteOrders?.single())

        val restored = fixture.execute(RoomCommand(
            commandId = fixture.id(), kind = CommandKind.HOME, identityToken = registered.identityToken,
        ))
        assertEquals(favorite, restored.home?.profile?.favoriteOrders?.single())
    }
}
