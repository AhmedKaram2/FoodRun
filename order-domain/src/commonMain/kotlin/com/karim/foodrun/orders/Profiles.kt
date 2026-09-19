package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable enum class PaymentMethod { BANK, AANI }
@Serializable data class FoodProfile(
    val userId: String = "", val name: String = "", val phone: String = "", val photo: String = "",
    val payment: ReceivingAccount? = null, val discoverable: Boolean = true,
) {
    fun validate() {
        MenuValidation.label(name)
        require(phone.matches(Regex("\\+?[0-9 ()-]{7,24}")) && phone.count { it.isDigit() } in 7..15) { "Enter your phone number with country code." }
        require(photo.isEmpty() || photo.length <= 180_000 && (
            photo.matches(Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")) ||
            photo.length <= 2000 && photo.startsWith("https://") && photo.none { it.isWhitespace() }
        )) { "Use an HTTPS photo URL or a small JPEG, PNG or WebP photo." }
        payment?.validate()
    }
}
@Serializable data class FoodPerson(val userId: String, val name: String, val photo: String = "")
@Serializable data class FoodInvitation(val id: String, val userId: String, val roomId: String, val roomName: String, val invitedBy: String, val orderNumber: Long)
@Serializable data class AccountRoom(val roomId: String, val roomName: String, val memberId: String, val token: String)
@Serializable data class HomePayload(
    val profile: FoodProfile, val people: List<FoodPerson> = emptyList(),
    val invitations: List<FoodInvitation> = emptyList(), val rooms: List<AccountRoom> = emptyList(), val cloudStatus: String = "Cloud storage has not been connected for this hub.",
)
