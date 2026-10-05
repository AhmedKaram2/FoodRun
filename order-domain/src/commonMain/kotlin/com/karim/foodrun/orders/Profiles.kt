package com.karim.foodrun.orders

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class PaymentMethod { BANK, AANI }
object UaePhone {
    fun normalize(value: String, mobileOnly: Boolean = false): String {
        val digits = value.filter(Char::isDigit)
        val local = when {
            digits.startsWith("00971") -> digits.drop(5)
            digits.startsWith("971") -> digits.drop(3)
            digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        val validLandline = local.length == 8 && local.firstOrNull() in '2'..'9'
        val validMobile = local.length == 9 && local.startsWith('5')
        require(if (mobileOnly) validMobile else validLandline || validMobile) {
            if (mobileOnly) "Enter a UAE mobile number, for example +971 50 123 4567."
            else "Enter a UAE phone number, for example +971 4 123 4567."
        }
        return "+971$local"
    }
}
object InternationalPhone {
    fun normalize(value: String): String {
        val trimmed = value.trim().map { c -> when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            else -> c
        } }.joinToString("")
        if (trimmed.startsWith("0") && !trimmed.startsWith("00")) return UaePhone.normalize(trimmed)
        val digits = trimmed.filter(Char::isDigit).let { if (it.startsWith("00")) it.drop(2) else it }
        // Legacy UAE profiles may contain just the national number, without 0 or +971.
        // An explicit international prefix always takes precedence.
        if (!trimmed.startsWith("+") && !trimmed.startsWith("00") &&
            (digits.length == 9 && digits.startsWith('5') || digits.length == 8 && digits.firstOrNull() in '2'..'9')) {
            return UaePhone.normalize(digits)
        }
        require(digits.length in 7..15 && digits.firstOrNull() in '1'..'9') {
            "Enter a phone number with country code, for example +20 10 1234 5678."
        }
        return "+$digits"
    }
}
@Serializable data class FoodProfile(
    val userId: String = "", val name: String = "", val phone: String = "", val photo: String = "",
    val payment: ReceivingAccount? = null, val discoverable: Boolean = true, val language: String = "en",
    val favoriteOrders: List<FavoriteOrder> = emptyList(),
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val paymentAccounts: List<ReceivingAccount> = emptyList(),
) {
    val receivingAccounts: List<ReceivingAccount> get() = (listOfNotNull(payment) + paymentAccounts).distinctBy { it.id }
    fun validate() {
        MenuValidation.label(name)
        InternationalPhone.normalize(phone)
        require(photo.isEmpty() || photo.length <= 180_000 && (
            photo.matches(Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")) ||
            photo.length <= 2000 && photo.startsWith("https://") && photo.none { it.isWhitespace() }
        )) { "Use an HTTPS photo URL or a small JPEG, PNG or WebP photo." }
        payment?.validate()
        require(paymentAccounts.size <= 10 && paymentAccounts.map { it.id }.distinct().size == paymentAccounts.size && receivingAccounts.size <= 10) { "Save up to 10 distinct payment methods." }
        paymentAccounts.forEach(ReceivingAccount::validate)
        require(language in listOf("en", "ar")) { "Choose Arabic or English." }
        require(favoriteOrders.size <= 30 && favoriteOrders.map { it.id }.distinct().size == favoriteOrders.size) { "Save up to 30 distinct favorite orders." }
        favoriteOrders.forEach(FavoriteOrder::validate)
    }
    fun normalized(): FoodProfile = copy(phone = InternationalPhone.normalize(phone), payment = payment?.normalized(), paymentAccounts = paymentAccounts.filterNot { it.id == payment?.id }.map { it.normalized() })
}
@Serializable data class FavoriteOrderLine(
    val itemId: String = "", val quantity: Int, val variantId: String? = null,
    val optionIds: List<String> = emptyList(), val notes: String = "",
    val description: String = "", val label: String = "",
)
@Serializable data class FavoriteOrder(
    val id: String, val restaurantId: String, val restaurantName: String, val title: String,
    val lines: List<FavoriteOrderLine>, val savedAt: Long,
) {
    fun validate() {
        MenuValidation.label(id); MenuValidation.label(restaurantId); MenuValidation.label(restaurantName); MenuValidation.label(title)
        require(savedAt >= 0 && lines.size in 1..100) { "A favorite order needs 1–100 items." }
        lines.forEach { line ->
            require(line.quantity in 1..99 && line.notes.length <= 500 && line.description.length <= 160 && line.label.length in 1..1000) { "A favorite order item is invalid." }
            require(line.optionIds.size <= 30 && line.optionIds.distinct().size == line.optionIds.size) { "A favorite order has invalid extras." }
            if (line.itemId.isBlank()) require(line.description.isNotBlank() && line.variantId == null && line.optionIds.isEmpty()) { "A custom favorite item is invalid." }
            else require(line.description.isBlank()) { "A menu favorite item is invalid." }
        }
    }
}

private fun selectionKey(restaurantId: String, lines: List<FavoriteOrderLine>): String = Json.encodeToString(
    listOf(restaurantId.trim().lowercase()) + lines.map { line ->
        Json.encodeToString(listOf(
            line.itemId, line.variantId.orEmpty(), Json.encodeToString(line.optionIds.sorted()),
            if (line.itemId.isBlank()) line.description.trim().lowercase() else "",
            line.quantity.toString(), line.notes.trim().lowercase(),
        ))
    }.sorted()
)
fun Receipt.orderSelectionKey(restaurantKey: String): String = selectionKey(restaurantKey, lines.map {
    FavoriteOrderLine(it.itemId, it.quantity, it.variantId, it.optionIds, it.notes, it.description)
})
fun FavoriteOrder.selectionKey(): String = selectionKey(restaurantId, lines)
@Serializable data class FoodPerson(val userId: String, val name: String, val photo: String = "")
@Serializable data class FoodInvitation(val id: String, val userId: String, val roomId: String, val roomName: String, val invitedBy: String, val orderNumber: Long)
@Serializable data class AccountRoom(val roomId: String, val roomName: String, val memberId: String, val token: String,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val phase: RoomPhase? = null,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val orderNumber: Long = 0,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val paymentsPending: Boolean = false,
)
@Serializable data class AccessBlock(val until: Long = 0, val reason: String = "", val durationHours: Int = 0, val removed: Boolean = false, val roomId: String = "")
@Serializable data class HomePayload(
    val profile: FoodProfile, val people: List<FoodPerson> = emptyList(),
    val invitations: List<FoodInvitation> = emptyList(), val rooms: List<AccountRoom> = emptyList(), val cloudStatus: String = "Cloud storage has not been connected for this hub.",
    val restaurants: List<Restaurant> = emptyList(),
    val deletedRestaurantIds: Set<String> = emptySet(),
    val deletedRoomIds: Set<String> = emptySet(),
    val roomAccessBlocks: Map<String, AccessBlock> = emptyMap(),
)
