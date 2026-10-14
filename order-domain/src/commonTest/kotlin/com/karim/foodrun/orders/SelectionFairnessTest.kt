package com.karim.foodrun.orders

import kotlin.test.*

class SelectionFairnessTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test fun frequencyAndRecencyReduceWeightsWithoutRemovingAnyEligiblePerson() {
        val candidates = listOf("new", "older", "recent", "repeated", "very-frequent")
        val weights = PayerSelection.historyWeights(candidates, mapOf(
            "older" to listOf(now - 15 * day), "recent" to listOf(now - day),
            "repeated" to listOf(now - day, now - 2 * day), "very-frequent" to List(100) { now - day }
        ), now, null)
        assertEquals(listOf(12, 6, 3, 1, 1), weights)
        val outcomes = (0 until weights.sum()).map { ticket -> PayerSelection.choose(candidates, weights) { ticket } }
            .groupingBy { it }.eachCount()
        assertEquals(weights, candidates.map { outcomes[it] })
    }

    @Test fun oldHistoryExpiresAndEqualHistoriesHaveEqualChances() {
        assertEquals(listOf(12, 12), PayerSelection.historyWeights(listOf("a", "b"),
            mapOf("a" to listOf(now - 31 * day)), now, null))
        assertEquals(listOf(1, 1), PayerSelection.historyWeights(listOf("a", "b"),
            mapOf("a" to listOf(now, now), "b" to listOf(now, now)), now, null))
        assertEquals(listOf(3, 12), PayerSelection.historyWeights(listOf("a", "b"), emptyMap(), now, "a"))
    }

    @Test fun paidHalfChanceStillHalvesTheActualProbabilityAfterHistoryBalancing() {
        val room = Room("r", "123456", "a", "Lunch", Restaurant("food", "Kitchen"),
            members = listOf("a", "b", "c").map { Member(it, it, approved = true, eligible = true) },
            wheelProtections = listOf(WheelProtection("half", "b", "a", 1, WheelProtectionPlan.HALF_CHANCE,
                status = WheelProtectionStatus.ACTIVE)))
        val base = listOf(1, 3, 12)
        val adjusted = WheelProtectionRules.weights(room, base = base)
        assertEquals(base[1].toLong() * adjusted.sum(), 2L * adjusted[1] * base.sum())
        assertTrue(adjusted.all { it > 0 })
        SpinRound("balanced", room.members.map { it.id }, "a", now, weights = adjusted)
    }

    @Test fun balancedProtectionWeightsFitEverySupportedRoomSizeAndOlderAppValidation() {
        for (count in 2..30) for (protected in 1 until count) {
            val members = (0 until count).map { Member("m$it", "Member", approved = true, eligible = true) }
            val room = Room("r", "123456", "m0", "Lunch", Restaurant("food", "Kitchen"), members = members,
                wheelProtections = (1..protected).map { WheelProtection("half$it", "m$it", "m0", 1,
                    WheelProtectionPlan.HALF_CHANCE, status = WheelProtectionStatus.ACTIVE) })
            val base = members.indices.map { listOf(12, 6, 3, 1)[it % 4] }
            val weights = WheelProtectionRules.weights(room, base = base)
            SpinRound("balanced", members.map { it.id }, "m0", now, weights = weights)
        }
    }
}
