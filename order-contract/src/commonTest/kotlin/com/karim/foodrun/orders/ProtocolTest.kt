package com.karim.foodrun.orders

import kotlin.test.*

class ProtocolTest {
    @Test fun commandsRoundTripWithoutDroppingFinancialOrMembershipFields() {
        val command = RoomCommand(commandId = "1234567890123456", kind = CommandKind.PARTICIPATE, roomId = "room", token = "token", expectedRevision = 42, flag = false)
        assertEquals(command, orderJson.decodeFromString<RoomCommand>(orderJson.encodeToString(command)))
    }
    @Test fun enumWireValuesRemainCompatibleWithMenuVersionOne() {
        assertEquals("\"added\"", orderJson.encodeToString(TaxTreatment.ADDED))
        assertEquals("\"declared\"", orderJson.encodeToString(TransferStatus.DECLARED))
        assertFailsWith<IllegalArgumentException> { orderJson.decodeFromString<TransferStatus>("\"unknown\"") }
    }
    @Test fun absentNewMembershipFlagPreservesExistingParticipation() {
        val member = orderJson.decodeFromString<Member>("""{"id":"member","name":"Karim"}""")
        assertTrue(member.participating)
    }
    @Test fun unknownCommandsAndUnexpectedFieldsFailClosed() {
        assertFailsWith<IllegalArgumentException> { orderJson.decodeFromString<RoomCommand>("""{"commandId":"1234567890123456","kind":"BECOME_OWNER"}""") }
        assertFailsWith<IllegalArgumentException> { orderJson.decodeFromString<RoomCommand>("""{"commandId":"1234567890123456","kind":"READY","isAdmin":true}""") }
    }
}
