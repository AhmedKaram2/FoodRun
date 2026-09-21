package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class DirectSelectionTest {
    @Test fun chosenOfflineMemberCanContinueWithoutWheelOrAcceptanceAfterRestart(): Unit = RoomFixture().use { f ->
        val member = f.join()
        f.now += 60_000
        val chosen = f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = member.memberId) }.room!!
        assertEquals(RoomPhase.COLLECTING, chosen.phase)
        assertEquals(member.memberId, chosen.payerId)
        assertNull(chosen.spin)
        assertTrue(chosen.pastSpins.isEmpty())
        f.restart()
        assertEquals(member.memberId, f.state(member).room!!.payerId)
        f.send(member, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 1); f.cart(member, 2)
        val placed = f.send(member, CommandKind.PLACE) { it.copy(text = "Delivery in 30 minutes") }
        assertEquals(RoomPhase.PLACED, placed.room!!.phase)
        assertEquals("Delivery in 30 minutes", placed.room!!.restaurantReference)
    }

    @Test fun onlyOrganizerCanSelectAnActiveOrdererWithFreshState(): Unit = RoomFixture().use { f ->
        val member = f.join()
        assertFalse(f.service.execute(f.command(member, CommandKind.SELECT_PAYER).copy(memberId = member.memberId)).ok)
        val stale = f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = member.memberId)
        f.send(member, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        assertFalse(f.service.execute(stale).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = member.memberId)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = "missing")).ok)
        val guest = f.join("Historical viewer", guest = true)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = guest.memberId)).ok)
        f.send(f.owner, CommandKind.SELECT_PAYER) { it.copy(memberId = f.owner.memberId) }
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = f.owner.memberId)).ok)
    }

    @Test fun selectionWaitsForRestaurantPoll(): Unit = RoomFixture().use { f ->
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(restaurantPollOpen = true, restaurantOptions = listOf(f.restaurant, f.restaurant.copy(id = "second"))))
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = f.owner.memberId)).ok)
        assertEquals(RoomPhase.LOBBY, f.state().room!!.phase)
    }

    @Test fun guestModeCannotCreateOrJoinRooms(): Unit = RoomFixture().use { f ->
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE,
            name = "Guest", text = "Guest room", restaurant = f.restaurant, guest = true)).ok)
        assertFalse(f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.JOIN,
            name = "Guest", code = f.owner.room!!.code, guest = true)).ok)
    }
}
