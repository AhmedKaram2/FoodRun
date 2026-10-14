package com.karim.foodrun.orders

/** Weighted random draws keep a positive chance for every eligible person. */
object PayerSelection {
    const val HISTORY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    fun weights(candidates: List<String>, lastChosenMemberId: String?): List<Int> =
        candidates.map { if (it == lastChosenMemberId && candidates.size > 1) 20 else 80 }

    fun historyWeights(candidates: List<String>, selectionTimes: Map<String, List<Long>>, now: Long,
        lastChosenMemberId: String?): List<Int> = candidates.map { memberId ->
        // A choice in the last week counts three times as much as an older choice.
        val score = selectionTimes[memberId].orEmpty().filter { it >= now - HISTORY_WINDOW_MS }
            .sumOf { if (it >= now - RECENT_WINDOW_MS) 3L else 1L }.coerceAtMost(11)
        // Small units also keep legacy paid-protection weights within the protocol's 10,000 limit.
        val historyWeight = (12L / (1 + score)).toInt().coerceAtLeast(1)
        if (memberId == lastChosenMemberId && candidates.size > 1) minOf(3, historyWeight) else historyWeight
    }

    fun choose(candidates: List<String>, weights: List<Int>, randomIndex: (Int) -> Int): String {
        require(candidates.isNotEmpty() && candidates.size == weights.size && weights.all { it > 0 })
        var ticket = randomIndex(weights.sum())
        require(ticket in 0 until weights.sum())
        for (index in candidates.indices) {
            if (ticket < weights[index]) return candidates[index]
            ticket -= weights[index]
        }
        error("Invalid selection ticket.")
    }
}
