package com.karim.foodrun.orders

import kotlin.test.*

class WheelProtectionTest {
    private fun room(count: Int, half: Set<String> = emptySet(), excluded: Set<String> = emptySet(), last: String? = null): Room {
        val members = (0 until count).map { Member("m$it", "Member $it", approved = true, eligible = true) }
        return Room("room", "123456", "m0", "Lunch", Restaurant("r", "Kitchen"), members = members, lastChosenMemberId = last,
            wheelProtections = (half + excluded).map { WheelProtection("request-$it", it, "m0", 1,
                if (it in excluded) WheelProtectionPlan.EXCLUDE else WheelProtectionPlan.HALF_CHANCE, status = WheelProtectionStatus.ACTIVE) })
    }
    @Test fun halfChanceMeansExactlyHalfTheNormalizedProbabilityForEverySupportedGroupSize() {
        for (count in 2..30) for (reducedCount in 1 until count) for (last in listOf<String?>(null, "m1", "m${count - 1}")) {
            val half = (1..reducedCount).map { "m$it" }.toSet()
            val room = room(count, half, last = last)
            val candidates = WheelProtectionRules.candidates(room)
            val base = PayerSelection.weights(candidates, last)
            val adjusted = WheelProtectionRules.weights(room)
            candidates.indices.filter { candidates[it] in half }.forEach { index ->
                assertEquals(base[index].toLong() * adjusted.sum(), 2L * adjusted[index] * base.sum(), "count=$count half=$reducedCount last=$last member=${candidates[index]}")
            }
            assertTrue(adjusted.all { it in 1..10000 })
            val outcomes = (0 until adjusted.sum()).map { ticket -> PayerSelection.choose(candidates, adjusted) { ticket } }.groupingBy { it }.eachCount()
            assertEquals(adjusted, candidates.map { outcomes.getValue(it) })
        }
    }
    @Test fun exclusionDoesNotRemoveFoodParticipationAndPendingPaymentsNeverChangeChances() {
        val room = room(3, excluded = setOf("m1"))
        assertEquals(listOf("m0", "m2"), WheelProtectionRules.candidates(room))
        assertEquals(3, room.orderingMembers.size)
        for (status in listOf(WheelProtectionStatus.REQUESTED, WheelProtectionStatus.AWAITING_PAYMENT, WheelProtectionStatus.PAYMENT_DECLARED, WheelProtectionStatus.REJECTED)) {
            val pending = room.copy(wheelProtections = room.wheelProtections.map { it.copy(status = status) })
            assertEquals(3, WheelProtectionRules.candidates(pending).size)
            assertEquals(listOf(80, 80, 80), WheelProtectionRules.weights(pending))
        }
    }
    @Test fun lastEligiblePersonCannotBeExcludedOrReceiveAnImpossibleHalfChance() {
        assertFailsWith<IllegalArgumentException> { WheelProtectionRules.requireSelectable(room(1, excluded = setOf("m0"))) }
        assertFailsWith<IllegalArgumentException> { WheelProtectionRules.requireSelectable(room(2, half = setOf("m0", "m1"))) }
        val mixed = room(4, half = setOf("m1"), excluded = setOf("m2"), last = "m1")
        assertEquals(listOf("m0", "m1", "m3"), WheelProtectionRules.candidates(mixed))
        assertEquals(listOf(17, 2, 17), WheelProtectionRules.weights(mixed))
    }
    @Test fun feePricesAreAlwaysFixedAedMinorUnits() {
        assertEquals(1000L, WheelProtectionPlan.EXCLUDE.amount)
        assertEquals(500L, WheelProtectionPlan.HALF_CHANCE.amount)
    }
}
