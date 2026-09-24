package com.karim.foodrun.orders

/** The most recent winner has weight 20; each other eligible person has weight 80. */
object PayerSelection {
    fun weights(candidates: List<String>, lastChosenMemberId: String?): List<Int> =
        candidates.map { if (it == lastChosenMemberId && candidates.size > 1) 20 else 80 }

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
