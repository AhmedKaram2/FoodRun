package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable enum class WheelProtectionPlan(val amount: Long) { EXCLUDE(1000), HALF_CHANCE(500) }
@Serializable enum class WheelProtectionStatus { REQUESTED, AWAITING_PAYMENT, PAYMENT_DECLARED, ACTIVE, REJECTED }
@Serializable data class WheelProtection(
    val id: String, val memberId: String, val recipientId: String, val orderNumber: Long,
    val plan: WheelProtectionPlan, val amount: Long = plan.amount, val currency: String = "AED",
    val status: WheelProtectionStatus = WheelProtectionStatus.REQUESTED,
    val reference: String = "", val createdAt: Long = 0, val approvedAt: Long = 0, val paidAt: Long = 0,
)

object WheelProtectionRules {
    fun active(room: Room, memberId: String): WheelProtectionPlan? = room.wheelProtections
        .singleOrNull { it.memberId == memberId && it.orderNumber == room.orderNumber && it.status == WheelProtectionStatus.ACTIVE }?.plan
    fun candidates(room: Room): List<String> = room.orderingMembers
        .filter { it.eligible && active(room, it.id) != WheelProtectionPlan.EXCLUDE }.map { it.id }
    fun weights(room: Room, candidates: List<String> = candidates(room)): List<Int> {
        val base = PayerSelection.weights(candidates, room.lastChosenMemberId)
        val reduced = candidates.map { active(room, it) == WheelProtectionPlan.HALF_CHANCE }
        if (reduced.none { it }) return base
        // Halving a weight alone does not halve the normalized probability.
        // Halve each protected probability, then redistribute the freed mass
        // proportionally among candidates who have no reduction.
        val units = base.map { it / 20 }
        val total = units.sum()
        val reducedTotal = units.indices.filter { reduced[it] }.sumOf { units[it] }
        val normalTotal = total - reducedTotal
        require(normalTotal > 0) { "At least one eligible member must keep their normal wheel chance." }
        val raw = units.indices.map { if (reduced[it]) units[it] * normalTotal else units[it] * (2 * total - reducedTotal) }
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val divisor = raw.reduce(::gcd)
        return raw.map { it / divisor }
    }
    fun requireSelectable(room: Room) {
        val candidates = candidates(room)
        require(candidates.isNotEmpty()) { "At least one eligible member must remain in the wheel." }
        weights(room, candidates)
    }
}
