package com.karim.foodrun.orders

/** Wallet allocation settles the customer's share; cash remains with its holder until confirmed. */
object WalletSettlement {
    fun pending(room: Room): Long = room.walletPayments.filter { it.status != WalletPaymentStatus.SETTLED }.sumOf { it.amount }
    fun pending(room: Room, memberId: String): Long = room.walletPayments.filter { it.memberId == memberId && it.status != WalletPaymentStatus.SETTLED }.sumOf { it.amount }
    fun received(room: Room, receipts: List<Receipt>): Long = receipts.filterNot { it.memberId == room.payerId }.sumOf { it.paid } -
        room.walletPayments.filter { payment -> payment.status != WalletPaymentStatus.SETTLED &&
            room.transfers.any { it.id == "wallet-${payment.id}" && it.status == TransferStatus.CONFIRMED } }.sumOf { it.amount }
}
