package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable enum class WalletStatus { PENDING, CONFIRMED, REJECTED }
@Serializable enum class WalletPaymentStatus { OWING, SENT, SETTLED }
/** Confirmed top-ups less committed payments, separated by currency and cash holder. */
@Serializable data class WalletBalance(val customerId: String, val customerName: String, val holderId: String, val holderName: String, val currency: String, val available: Long)
@Serializable data class WalletTopUp(val id: String, val customerId: String, val customerName: String, val holderId: String, val holderName: String,
    val amount: Long, val currency: String, val account: ReceivingAccount, val note: String, val status: WalletStatus = WalletStatus.PENDING, val createdAt: Long, val resolvedAt: Long = 0)
@Serializable data class WalletPayment(val id: String, val customerId: String, val customerName: String, val holderId: String, val holderName: String,
    val recipientId: String, val recipientName: String, val roomId: String, val roomName: String, val orderNumber: Long, val memberId: String,
    val amount: Long, val currency: String, val status: WalletPaymentStatus = WalletPaymentStatus.OWING, val batchId: String = "", val createdAt: Long)
@Serializable data class WalletBatch(val id: String, val holderId: String, val holderName: String, val recipientId: String, val recipientName: String,
    val amount: Long, val currency: String, val paymentIds: List<String>, val account: ReceivingAccount, val note: String,
    val status: WalletStatus = WalletStatus.PENDING, val createdAt: Long, val resolvedAt: Long = 0)
@Serializable data class WalletSnapshot(val balances: List<WalletBalance> = emptyList(), val topUps: List<WalletTopUp> = emptyList(),
    val payments: List<WalletPayment> = emptyList(), val batches: List<WalletBatch> = emptyList())
@Serializable data class WalletRecipient(val person: FoodPerson, val accounts: List<ReceivingAccount>)
