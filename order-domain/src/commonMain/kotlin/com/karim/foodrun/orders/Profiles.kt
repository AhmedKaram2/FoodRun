package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

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
@Serializable data class FoodProfile(
    val userId: String = "", val name: String = "", val phone: String = "", val photo: String = "",
    val payment: ReceivingAccount? = null, val discoverable: Boolean = true, val language: String = "en",
) {
    fun validate() {
        MenuValidation.label(name)
        UaePhone.normalize(phone, mobileOnly = true)
        require(photo.isEmpty() || photo.length <= 180_000 && (
            photo.matches(Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")) ||
            photo.length <= 2000 && photo.startsWith("https://") && photo.none { it.isWhitespace() }
        )) { "Use an HTTPS photo URL or a small JPEG, PNG or WebP photo." }
        payment?.validate()
        require(language in listOf("en", "ar")) { "Choose Arabic or English." }
    }
    fun normalized(): FoodProfile = copy(phone = UaePhone.normalize(phone, mobileOnly = true), payment = payment?.normalized())
}
@Serializable data class FoodPerson(val userId: String, val name: String, val photo: String = "")
@Serializable data class FoodInvitation(val id: String, val userId: String, val roomId: String, val roomName: String, val invitedBy: String, val orderNumber: Long)
@Serializable data class AccountRoom(val roomId: String, val roomName: String, val memberId: String, val token: String)
@Serializable data class HomePayload(
    val profile: FoodProfile, val people: List<FoodPerson> = emptyList(),
    val invitations: List<FoodInvitation> = emptyList(), val rooms: List<AccountRoom> = emptyList(), val cloudStatus: String = "Cloud storage has not been connected for this hub.",
    val restaurants: List<Restaurant> = emptyList(),
)
