package com.karim.foodrun.orders

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

@Serializable enum class CommandKind {
    CREATE, JOIN, SNAPSHOT, NEXT_ORDER, APPROVE, APPROVE_LATE_JOIN, REMOVE, PARTICIPATE, READY, PREPARE_SPIN, ACK_SPIN, ABORT_PREPARE,
    ACCEPT_DUTY, DECLINE_DUTY, SHARE_ACCOUNT, CART, SUBMIT_CART, REVIEW, CONFIRM_QUOTE,
    REOPEN, SET_FEES, UPDATE_RESTAURANT, PLACE, PAY_RESTAURANT, FULFILL, DECLARE_TRANSFER, CONFIRM_TRANSFER,
    REJECT_TRANSFER, DECLARE_REFUND, CONFIRM_REFUND, ADJUST_BILL, APPROVE_ADJUSTMENT, HANDOVER, ARCHIVE, CANCEL,
}
@Serializable data class RoomCommand(
    val protocolVersion: Int = 1, val commandId: String, val kind: CommandKind,
    val roomId: String = "", val token: String = "", val expectedRevision: Long = -1,
    val name: String = "", val code: String = "", val memberId: String = "", val text: String = "",
    val flag: Boolean = false, val guest: Boolean = false, val eligible: Boolean = false,
    val restaurant: Restaurant? = null, val expectedNames: List<String> = emptyList(), val destination: String = "", val deadline: Long = 0,
    val fees: FeePolicy? = null, val cart: MemberCart? = null, val account: ReceivingAccount? = null,
    val amount: Long = 0, val transferId: String = "", val historyOffset: Int = 0,
    // Omit the legacy default so commands saved before this field retain their replay digest.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expectedOrderNumber: Long = 0,
)
@Serializable data class RoomReply(
    val protocolVersion: Int = 1, val ok: Boolean = true, val error: String = "", val code: String = "",
    val room: Room? = null, val token: String = "", val memberId: String = "", val serverTime: Long = 0,
    val receipts: List<Receipt> = emptyList(), val history: List<PastOrder> = emptyList(), val historyNextOffset: Int = -1,
)
@Serializable data class HubPairing(val url: String, val fingerprint: String)
@Serializable data class StoredSession(val hub: HubPairing, val roomId: String, val token: String, val memberId: String, val roomName: String)
