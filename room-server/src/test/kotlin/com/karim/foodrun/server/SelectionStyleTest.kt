package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class SelectionStyleTest {
    @Test fun namesStyleSurvivesRestartAndNextOrderAndIsHiddenFromOldClients() = RoomFixture().use { f ->
        val created = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Owner", text = "Mohre", restaurant = f.restaurant, selectionStyle = "names"))
        val original = created.room!!
        f.restart()
        var reply = f.service.snapshot(original.id, created.token)
        assertEquals("names", reply.room!!.selectionStyle)
        assertFalse(orderJson.encodeToString(reply.forClient(true)).contains("selectionStyle"))
        assertTrue(orderJson.encodeToString(reply.forClient(true, true)).contains("\"selectionStyle\":\"names\""))
        for(kind in listOf(CommandKind.CANCEL, CommandKind.NEXT_ORDER)) {
            reply = f.execute(RoomCommand(commandId = f.id(), kind = kind, roomId = original.id, token = created.token,
                expectedRevision = reply.room!!.revision, expectedOrderNumber = reply.room!!.orderNumber, text = "New meal"))
        }
        assertEquals("names", reply.room!!.selectionStyle)
        val rejected = f.service.execute(RoomCommand(commandId = f.id(), kind = CommandKind.CREATE, name = "Owner", text = "Bad style", restaurant = f.restaurant, selectionStyle = "unknown"))
        assertFalse(rejected.ok)
    }
    @Test fun runningNamesAlwaysFinishOnTheServerWinner() {
        val members = listOf("a", "b", "c")
        members.forEach { winner ->
            val spin = SpinRound("spin", members, winner, 1000, 6000, 7)
            assertEquals(0, spin.runningNameIndex(0))
            for(time in 1000L..6999L step 53) assertTrue(spin.runningNameIndex(time) in members.indices)
            assertEquals(members.indexOf(winner), spin.runningNameIndex(7000))
            assertEquals(members.indexOf(winner), spin.runningNameIndex(99999))
        }
    }
}
