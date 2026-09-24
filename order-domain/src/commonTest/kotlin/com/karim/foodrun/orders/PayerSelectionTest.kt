package com.karim.foodrun.orders

import kotlin.test.*

class PayerSelectionTest {
    @Test fun previousWinnerHasTwentyTicketsAndEveryOtherPersonHasEighty() {
        for (count in 2..30) {
            val candidates = (1..count).map { "member-$it" }
            val weights = PayerSelection.weights(candidates, candidates.last())
            val outcomes = (0 until weights.sum()).map { ticket -> PayerSelection.choose(candidates, weights) { ticket } }.groupingBy { it }.eachCount()
            candidates.forEach { assertEquals(if (it == candidates.last()) 20 else 80, outcomes[it]) }
        }
    }

    @Test fun missingOrIneligiblePreviousWinnerDoesNotPenalizeOtherPeople() {
        assertEquals(listOf(80, 80), PayerSelection.weights(listOf("a", "b"), "absent"))
        assertEquals(listOf(80, 80), PayerSelection.weights(listOf("a", "b"), null))
        assertEquals("a", PayerSelection.choose(listOf("a"), PayerSelection.weights(listOf("a"), "a")) { it - 1 })
    }

    @Test fun weightedWheelAlwaysStopsWithTheChosenSliceAtThePointer() {
        val candidates = listOf("a", "b", "c")
        for (last in candidates) for (winner in candidates) {
            val spin = SpinRound("spin", candidates, winner, 1000, weights = PayerSelection.weights(candidates, last))
            assertEquals(0.0, (spin.sliceCenter(candidates.indexOf(winner)) + spin.rotation(spin.endAt)) % 360, 0.00001)
        }
    }
}
