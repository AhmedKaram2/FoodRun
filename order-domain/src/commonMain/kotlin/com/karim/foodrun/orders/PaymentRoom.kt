package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

/** An already-ordered bill, settled with the same receipts and transfer ledger as a food room. */
@Serializable data class PaymentRoomDetails(val orderDetails: String, val receiptPhoto: String = "") {
    fun validate() {
        require(orderDetails.isNotBlank() && orderDetails.length <= 4000) { "Enter order details (up to 4000 characters)." }
        require(receiptPhoto.isEmpty() || receiptPhoto.length <= 600_000 &&
            receiptPhoto.matches(Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+"))) {
            "Attach a JPEG, PNG or WebP receipt under 450 KB after resizing."
        }
    }
}
@Serializable data class PaymentShare(val userId: String, val description: String, val amount: Long, val received: Long = 0)
@Serializable data class PaymentRoomRequest(val details: PaymentRoomDetails, val shares: List<PaymentShare>)
