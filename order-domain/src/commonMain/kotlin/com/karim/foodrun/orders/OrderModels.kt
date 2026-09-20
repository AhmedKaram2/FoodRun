package com.karim.foodrun.orders

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlin.math.pow

@Serializable enum class RoomPhase { LOBBY, PREPARING_SPIN, SPINNING, ACCEPTING, COLLECTING, REVIEW, PLACED, FULFILLED, ARCHIVED, CANCELLED }
@Serializable data class Member(
    val id: String, val name: String, val approved: Boolean = false, val guest: Boolean = false,
    val eligible: Boolean = false, val ready: Boolean = false, val participating: Boolean = true, val lastSeen: Long = 0, val removed: Boolean = false, val latePayerApproved: Boolean = false,
)
@Serializable data class CartLine(val id: String, val itemId: String, val quantity: Int, val variantId: String? = null, val optionIds: List<String> = emptyList(), val notes: String = "", val description: String = "", val unitPrice: Long? = null)
@Serializable data class MemberCart(val memberId: String, val revision: Long = 0, val lines: List<CartLine> = emptyList(), val submitted: Boolean = false, val confirmedQuote: Long = -1)
@Serializable data class FeePolicy(val delivery: Long = 0, val service: Long = 0, val discount: Long = 0, val proportionalDelivery: Boolean = false)
@Serializable data class ReceivingAccount(val id: String, val holder: String, val bank: String, val identifier: String, val currency: String = "AED", val version: Long = 1, val method: PaymentMethod = PaymentMethod.BANK) {
    fun validate() {
        MenuValidation.label(id); MenuValidation.label(holder); MenuValidation.label(bank)
        require(currency == "AED") { "Food Run uses AED (Dirham) for payments." }
        require(version > 0) { "Invalid account version." }
        if (method == PaymentMethod.AANI) {
            UaePhone.normalize(identifier, mobileOnly = true)
            return
        }
        val v = identifier.replace(" ", "").uppercase()
        require(currency in Money.currencies && v.length in 5..50 && v.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' }) { "Enter a valid receiving account." }
        if (v.take(2).all { it in 'A'..'Z' } && v.drop(2).take(2).all { it.isDigit() }) {
            require(v.length in 15..34) { "Invalid IBAN length." }
            var remainder = 0
            (v.drop(4) + v.take(4)).forEach { c ->
                val n = if (c in 'A'..'Z') (c.code - 55).toString() else c.toString()
                n.forEach { require(it.isDigit()) { "Invalid IBAN." }; remainder = (remainder * 10 + it.digitToInt()) % 97 }
            }
            require(remainder == 1) { "IBAN checksum is invalid." }
        }
    }
    fun normalized(): ReceivingAccount = if (method == PaymentMethod.AANI) copy(identifier = UaePhone.normalize(identifier, mobileOnly = true), currency = "AED") else copy(currency = "AED")
}
@Serializable data class SpinRound(val id: String, val memberIds: List<String>, val winnerId: String, val startAt: Long, val duration: Long = 6500, val turns: Int = 7) {
    init {
        require(memberIds.isNotEmpty() && memberIds.distinct().size == memberIds.size && winnerId in memberIds) { "Invalid spin candidates." }
        require(duration in 1..60_000 && turns in 1..100 && startAt in 0..Long.MAX_VALUE - duration) { "Invalid spin timing." }
    }
    val endAt: Long get() = startAt + duration
    fun rotation(now: Long): Double {
        val target = ((-memberIds.indexOf(winnerId) * 360.0 / memberIds.size) % 360 + 360) % 360
        val progress = ((now - startAt).toDouble() / duration).coerceIn(0.0, 1.0)
        return (turns * 360 + target) * (1 - (1 - progress).pow(4))
    }
}
@Serializable enum class TransferStatus {
    @SerialName("declared") DECLARED, @SerialName("confirmed") CONFIRMED, @SerialName("rejected") REJECTED,
}
@Serializable data class Transfer(val id: String, val memberId: String, val amount: Long, val reference: String, val recipient: ReceivingAccount, val refund: Boolean = false, val status: TransferStatus = TransferStatus.DECLARED, val createdAt: Long = 0)
@Serializable data class ReceiptLine(
    val description: String, val quantity: Int, val amount: Long, val notes: String = "",
    val itemId: String = "", val variantId: String? = null, val optionIds: List<String> = emptyList(),
)
@Serializable data class Receipt(val memberId: String, val name: String, val lines: List<ReceiptLine>, val food: Long, val delivery: Long, val service: Long, val discount: Long, val tax: Long, val total: Long, val paid: Long, val balance: Long, val revision: Long, val currency: String) {
    val totalText: String get() = Money.format(total, currency)
    val balanceText: String get() = Money.format(balance, currency)
}
@Serializable data class AuditEntry(val id: String, val actorId: String, val action: String, val reason: String, val at: Long)
@Serializable data class RestaurantVote(val memberId: String, val restaurantId: String)
@Serializable data class Room(
    val id: String, val code: String, val ownerId: String, val name: String, val restaurant: Restaurant,
    val expectedNames: List<String> = emptyList(), val deliveryMode: Boolean = false, val destination: String = "",
    val deadline: Long = 0, val fees: FeePolicy = FeePolicy(), val phase: RoomPhase = RoomPhase.LOBBY,
    val members: List<Member> = emptyList(), val carts: List<MemberCart> = emptyList(),
    val revision: Long = 1, val quoteRevision: Long = 1, val preparationId: String = "", val preparedIds: List<String> = emptyList(),
    val spin: SpinRound? = null, val pastSpins: List<SpinRound> = emptyList(), val payerId: String? = null,
    val account: ReceivingAccount? = null, val transfers: List<Transfer> = emptyList(), val audit: List<AuditEntry> = emptyList(),
    val restaurantReference: String = "", val restaurantPaid: Boolean = false, val createdAt: Long = 0, val updatedAt: Long = 0,
    val billRevision: Long = 1, val adjustment: Long = 0, val adjustmentApprovals: List<String> = emptyList(), val orderNumber: Long = 1,
    val restaurantOptions: List<Restaurant> = emptyList(), val restaurantVotes: List<RestaurantVote> = emptyList(),
    val restaurantPollOpen: Boolean = false,
) {
    val activeMembers: List<Member> get() = members.filter { it.approved && !it.removed }
    val orderingMembers: List<Member> get() = activeMembers.filter { !it.guest && it.participating }
}
@Serializable data class PastOrder(val number: Long, val restaurantName: String, val completedAt: Long, val receipts: List<Receipt>, val account: ReceivingAccount? = null)
