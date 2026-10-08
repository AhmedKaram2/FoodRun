package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class WheelProtectionServiceTest {
    private fun request(f: RoomFixture, member: RoomReply, plan: WheelProtectionPlan) = f.legacyWheelRequest(member,plan)
    private fun approve(f: RoomFixture, request: WheelProtection) = f.send(f.owner, CommandKind.REVIEW_WHEEL_PROTECTION) { it.copy(transferId = request.id, flag = true) }
    private fun declare(f: RoomFixture, member: RoomReply, request: WheelProtection) = f.send(member, CommandKind.DECLARE_WHEEL_PAYMENT) { it.copy(transferId = request.id, amount = request.amount, text = "Cash paid to owner") }
    private fun confirm(f: RoomFixture, request: WheelProtection) = f.send(f.owner, CommandKind.CONFIRM_WHEEL_PAYMENT) { it.copy(transferId = request.id, amount = request.amount, flag = true) }
    private fun activate(f: RoomFixture, member: RoomReply, plan: WheelProtectionPlan): WheelProtection {
        val request = request(f, member, plan); approve(f, request); declare(f, member, request); confirm(f, request); return request
    }

    @Test fun approvalAndConfirmedPaymentAreBothRequiredBeforeExclusion() = RoomFixture().use { f ->
        val member = f.join(); val request = request(f, member, WheelProtectionPlan.EXCLUDE)
        assertEquals(1000L, request.amount); assertEquals("AED", request.currency)
        assertTrue(member.memberId in WheelProtectionRules.candidates(f.state().room!!))
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_WHEEL_PAYMENT).copy(transferId = request.id, amount = 1000, text = "Cash")).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.REVIEW_WHEEL_PROTECTION).copy(transferId = request.id, flag = true)).ok)
        approve(f, request)
        assertTrue(member.memberId in WheelProtectionRules.candidates(f.state().room!!))
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PREPARE_SPIN)).ok)
        declare(f, member, request)
        assertTrue(member.memberId in WheelProtectionRules.candidates(f.state().room!!))
        assertFalse(f.service.execute(f.command(member, CommandKind.CONFIRM_WHEEL_PAYMENT).copy(transferId = request.id, amount = 1000, flag = true)).ok)
        confirm(f, request)
        val room = f.state().room!!
        assertTrue(room.orderingMembers.any { it.id == member.memberId })
        assertFalse(member.memberId in WheelProtectionRules.candidates(room))
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.SELECT_PAYER).copy(memberId = member.memberId)).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.REQUEST_WHEEL_PROTECTION).copy(text = WheelProtectionPlan.HALF_CHANCE.name)).ok)
        val spin = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        assertEquals(listOf(f.owner.memberId), spin.memberIds)
    }
    @Test fun reducedChanceIsExactAndCanNeverBeOverriddenByTheLegacyForcedWinner() = RoomFixture().use { f ->
        val member = f.join(); activate(f, member, WheelProtectionPlan.HALF_CHANCE)
        val room = f.state().room!!
        val candidates = WheelProtectionRules.candidates(room)
        val weights = WheelProtectionRules.weights(room)
        assertEquals(listOf(3, 1), weights)
        val reducer = RoomReducer({ "spin" }) { 0 }
        assertEquals(f.owner.memberId, reducer.spin(room, f.now, member.memberId).winnerId)
        val spin = f.send(f.owner, CommandKind.PREPARE_SPIN).room!!.spin!!
        assertEquals(candidates, spin.memberIds); assertEquals(weights, spin.weights)
    }
    @Test fun wrongAmountsOtherMembersAndStaleOrdersCannotPayOrApprove() = RoomFixture().use { f ->
        val member = f.join(); val other = f.join("Other"); val request = request(f, member, WheelProtectionPlan.HALF_CHANCE)
        approve(f, request)
        assertFalse(f.service.execute(f.command(other, CommandKind.DECLARE_WHEEL_PAYMENT).copy(transferId = request.id, amount = 500, text = "Cash")).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_WHEEL_PAYMENT).copy(transferId = request.id, amount = 1, text = "Cash")).ok)
        assertFalse(f.service.execute(f.command(member, CommandKind.DECLARE_WHEEL_PAYMENT).copy(transferId = request.id, amount = 500, text = "Cash", expectedOrderNumber = 99)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.HANDOVER).copy(memberId = other.memberId, text = "Handover")).ok)
        declare(f, member, request)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.CONFIRM_WHEEL_PAYMENT).copy(transferId = request.id, amount = 1, flag = true)).ok)
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.REVIEW_WHEEL_PROTECTION).copy(transferId = request.id, flag = false)).ok)
        assertTrue(f.state(other).room!!.wheelProtections.isEmpty())
        confirm(f, request)
        assertEquals("", f.state(other).room!!.wheelProtections.single().reference)
    }
    @Test fun legacyUnreceivedPaymentsDoNotActivate() = RoomFixture().use { f ->
        val member = f.join(); val first = request(f, member, WheelProtectionPlan.EXCLUDE)
        f.send(f.owner, CommandKind.REVIEW_WHEEL_PROTECTION) { it.copy(transferId = first.id, flag = false) }
        val second = request(f, member, WheelProtectionPlan.HALF_CHANCE); approve(f, second); declare(f, member, second)
        f.send(f.owner, CommandKind.CONFIRM_WHEEL_PAYMENT) { it.copy(transferId = second.id, amount = second.amount, flag = false) }
        assertNull(WheelProtectionRules.active(f.state().room!!, member.memberId))
        declare(f, member, second); confirm(f, second)
        assertEquals(WheelProtectionPlan.HALF_CHANCE, WheelProtectionRules.active(f.state().room!!, member.memberId))
    }
    @Test fun newPaidWheelRequestsAreUnavailableForBothPlans() = RoomFixture().use { f ->
        val member = f.join();val before = f.state().room
        WheelProtectionPlan.entries.forEach { plan ->
            val result = f.service.execute(f.command(member,CommandKind.REQUEST_WHEEL_PROTECTION).copy(text = plan.name))
            assertFalse(result.ok);assertEquals("Paid wheel options are no longer available.",result.error)
            assertEquals(before,f.state().room)
        }
    }
    @Test fun paymentReplayIsIdempotentAndProtectionSurvivesRestartButResetsNextOrder() = RoomFixture().use { f ->
        val member = f.join(); val request = request(f, member, WheelProtectionPlan.EXCLUDE)
        approve(f, request); declare(f, member, request)
        val command = f.command(f.owner, CommandKind.CONFIRM_WHEEL_PAYMENT).copy(transferId = request.id, amount = request.amount, flag = true)
        f.execute(command); f.execute(command); f.execute(command.copy(wheelProtectionDetails = true))
        assertEquals(1, f.state().room!!.wheelProtections.size)
        f.restart(); assertEquals(WheelProtectionPlan.EXCLUDE, WheelProtectionRules.active(f.state().room!!, member.memberId))
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Cancelled meal") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        assertTrue(f.state().room!!.wheelProtections.isEmpty())
        assertEquals(WheelProtectionStatus.ACTIVE, f.db.history(f.owner.room!!.id).single().wheelProtections.single().status)
        assertFalse(f.service.execute(command.copy(commandId = f.id())).ok)
    }
    @Test fun ownerCannotSelfApproveAndEligibilityChangesCannotBypassPaidExclusion() = RoomFixture().use { f ->
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.REQUEST_WHEEL_PROTECTION).copy(text = "EXCLUDE")).ok)
        val member = f.join(); activate(f, member, WheelProtectionPlan.EXCLUDE)
        f.send(member, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        assertFalse(member.memberId in WheelProtectionRules.candidates(f.state().room!!))
        f.send(f.owner, CommandKind.PARTICIPATE) { it.copy(flag = false) }
        assertFalse(f.service.execute(f.command(f.owner, CommandKind.PREPARE_SPIN)).ok)
    }
    @Test fun strictOlderClientsDoNotReceiveNewFieldsOrUnsupportedWeights() = RoomFixture().use { f ->
        val member = f.join(); activate(f, member, WheelProtectionPlan.EXCLUDE)
        val reply = f.state()
        assertFalse(orderJson.encodeToString(reply.forClient(true, true, true, true)).contains("wheelProtection"))
        assertTrue(orderJson.encodeToString(reply.forClient(true, true, true, true, true)).contains("wheelProtections"))
        val large = reply.copy(room = reply.room!!.copy(spin = SpinRound("s", listOf("a", "b"), "a", 0, weights = listOf(120, 1))))
        assertTrue(large.forClient(true).room!!.spin!!.weights.isEmpty())
        assertEquals(listOf(120, 1), large.forClient(true, wheelProtectionDetails = true).room!!.spin!!.weights)
    }
}
