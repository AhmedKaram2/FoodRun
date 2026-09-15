package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class OrderIsolationTest {
    @Test fun anOldClientWithoutAnOrderBindingCannotMutateTheRoom(): Unit = RoomFixture().use { f ->
        f.send(f.owner, CommandKind.READY) { it.copy(flag = false, eligible = false) }
        val legacy = f.command(f.owner, CommandKind.READY).copy(expectedOrderNumber = 0, flag = true, eligible = true)
        val rejected = f.service.execute(legacy)
        assertFalse(rejected.ok)
        assertTrue(rejected.error.contains("Update Food Run"))
        assertFalse(f.state().room!!.members.single().eligible)
    }

    @Test fun anUndeliveredReadyCommandCannotConsentToTheNextOrder(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val delayed = f.command(member, CommandKind.READY).copy(flag = true, eligible = true)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Order cancelled before this request arrived") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.restart()

        val response = f.service.execute(delayed)

        assertFalse(response.ok, "Yesterday's readiness must not opt a member into today's order")
        val today = f.state(member).room!!.members.single { it.id == member.memberId }
        assertFalse(today.participating)
        assertFalse(today.ready)
        assertFalse(today.eligible)
    }

    @Test fun anUndeliveredCartCannotOverwriteTheNextOrdersEmptyCart(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        val delayed = f.command(member, CommandKind.CART).copy(
            expectedRevision = 0,
            cart = MemberCart(member.memberId, lines = listOf(CartLine("old-line", "meal", 2))),
        )
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Restaurant cancelled the first order") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.start(member)

        assertFalse(f.service.execute(delayed).ok, "A matching cart revision must not authorize edits to a different order")
        assertTrue(f.state(member).room!!.carts.isEmpty())
    }

    @Test fun anAlreadySavedCommandCanStillBeAcknowledgedAfterTheNextOrderStarts(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val command = f.command(member, CommandKind.READY).copy(flag = true, eligible = true)
        val acknowledgement = f.execute(command)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Start again tomorrow") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.restart()

        assertEquals(acknowledgement, f.execute(command))
        val today = f.state(member).room!!.members.single { it.id == member.memberId }
        assertFalse(today.participating)
        assertFalse(today.eligible)
    }

    @Test fun aCommandRecordedBeforeOrderBindingWasAddedRetainsItsDurableAcknowledgement(): Unit = RoomFixture().use { f ->
        val member = f.join(); f.approve(member)
        val command = f.command(member, CommandKind.READY).copy(flag = true, eligible = true)
        val acknowledgement = f.execute(command)
        // Reconstruct the previous wire schema and its stored digest. The old server had no
        // expectedOrderNumber property; adding a default during decoding must not alter its hash.
        val legacy = command.copy(commandId = f.id(), expectedOrderNumber = 0)
        val fields = orderJson.parseToJsonElement(orderJson.encodeToString(legacy)).jsonObject
        val previousPayload = JsonObject(fields.filterKeys { it != "expectedOrderNumber" }).toString()
        f.db.record(legacy.commandId, RoomService.hash(previousPayload), acknowledgement)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished for today") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.restart()

        val restored = orderJson.decodeFromString<RoomCommand>(previousPayload)
        assertEquals(acknowledgement, f.execute(restored))
        assertFalse(f.state(member).room!!.members.single { it.id == member.memberId }.participating)
    }
}
