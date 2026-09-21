package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountServiceTest {
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
