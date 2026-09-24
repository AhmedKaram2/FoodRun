package com.karim.foodrun.orders

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

@Serializable enum class CommandKind {
    CREATE_PAYMENT_ROOM, UPDATE_PAYMENT_RECEIPT, UPDATE_PAYMENT_SHARE, RECORD_PAYMENT, IDENTITY, HOME, REQUEST_BLOCK, PRICE_ITEM, CREATE, JOIN, SNAPSHOT, NEXT_ORDER, APPROVE, APPROVE_LATE_JOIN, REMOVE, PARTICIPATE, READY,
    VOTE_RESTAURANT, FINALIZE_RESTAURANT, PREPARE_SPIN, SELECT_PAYER, ACK_SPIN, ABORT_PREPARE,
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
    val restaurants: List<Restaurant> = emptyList(),
    // Omit the legacy default so commands saved before this field retain their replay digest.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expectedOrderNumber: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val identity: IdentityRequest? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val identityToken: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val paymentRoom: PaymentRoomRequest? = null,
    // Older mobile clients decode replies strictly and cannot read the new selection fields.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val selectionDetails: Boolean = false,

)
@Serializable data class OrderProgress(
    val accountShared: Boolean = false,
    val canReview: Boolean = false, val reviewBlocker: String = "",
    val canArchive: Boolean = false, val archiveBlocker: String = "",
)
@Serializable data class RoomReply(
    val protocolVersion: Int = 1, val ok: Boolean = true, val error: String = "", val code: String = "",
    val room: Room? = null, val token: String = "", val memberId: String = "", val serverTime: Long = 0,
    val receipts: List<Receipt> = emptyList(), val history: List<PastOrder> = emptyList(), val historyNextOffset: Int = -1,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val progress: OrderProgress? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val home: HomePayload? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val identityToken: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val accessBlock: AccessBlock? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val deletedHistoryNumbers: Set<Long> = emptySet(),

)
/** A private LAN hub uses a pinned fingerprint; a public API uses normal CA-validated HTTPS. */
@Serializable data class HubPairing(val url: String, val fingerprint: String = "")
@Serializable data class StoredSession(val hub: HubPairing, val roomId: String, val token: String, val memberId: String, val roomName: String)

@Serializable enum class IdentityAction { REGISTER, SIGN_IN, SAVE_PROFILE, SIGN_OUT, INVITE, ACCEPT_INVITE, RESET_PASSWORD, FIREBASE_SIGN_IN, ENABLE_CLOUD }
@Serializable data class IdentityRequest(
    val action: IdentityAction, val email: String = "", val password: String = "",
    val profile: FoodProfile? = null, val userId: String = "", val invitationId: String = "", val firebaseToken: String = "",
)
