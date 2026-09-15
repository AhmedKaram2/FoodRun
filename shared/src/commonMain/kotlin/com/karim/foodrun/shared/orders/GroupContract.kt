package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

enum class GroupPage { HOME, QUICK_SPIN, CONNECT, SETUP, LIBRARY, RESTAURANT, ROOM, ITEM, ACCOUNT, RECEIPTS, HISTORY }
enum class GroupAction {
    BACK, QUICK_SPIN, CREATE, JOIN, CONNECT, DISCOVER, SCAN, OPEN_LIBRARY, NEW_RESTAURANT, EDIT_RESTAURANT,
    IMPORT_MENU, PREVIEW_IMPORT, CONFIRM_IMPORT, EXPORT_MENU, SAVE_RESTAURANT, DELETE_RESTAURANT, SELECT_RESTAURANT,
    ADD_MENU_ITEM, REMOVE_MENU_ITEM, SAVE_ROOM_RESTAURANT, UPDATE_ROOM_MENU, RESUME, CREATE_ROOM, JOIN_ROOM, RETRY, REFRESH,
    PARTICIPATE, READY, APPROVE, APPROVE_LATE_JOIN, REMOVE, PREPARE_SPIN, ABORT_SPIN, ACCEPT_DUTY, DECLINE_DUTY, OPEN_ITEM,
    SELECT_VARIANT, TOGGLE_OPTION, ADD_CART_ITEM, REMOVE_CART_ITEM, SUBMIT_CART, REVIEW, CONFIRM_QUOTE, REOPEN,
    OPEN_ACCOUNT, SELECT_ACCOUNT, SAVE_ACCOUNT, SHARE_ACCOUNT, DELETE_ACCOUNT, SET_FEES, PLACE, PAY_RESTAURANT,
    FULFILL, CALL_RESTAURANT, SHARE_RESTAURANT_ORDER, OPEN_RECEIPTS, OPEN_HISTORY, LOAD_OLDER_HISTORY, DECLARE_TRANSFER, CONFIRM_TRANSFER, REJECT_TRANSFER,
    DECLARE_REFUND, CONFIRM_REFUND, ADJUST_BILL, APPROVE_ADJUSTMENT, HANDOVER, ARCHIVE, CANCEL, NEXT_ORDER, SHARE_ROOM, SHARE_RECEIPT,
}
enum class GroupFieldKey {
    HUB_URL, FINGERPRINT, PAIRING_LINK, NAME, ROOM_NAME, ROOM_CODE, EXPECTED_NAMES, DELIVERY, DESTINATION,
    RESTAURANT_NAME, BRANCH, CURRENCY, PHONE, ADDRESS, MENU_ITEM_NAME, MENU_ITEM_PRICE, DELIVERY_FEE, SERVICE_FEE, DISCOUNT,
    PROPORTIONAL, JSON_MENU, ELIGIBLE, QUANTITY, NOTE, ACCOUNT_HOLDER, ACCOUNT_BANK, ACCOUNT_IDENTIFIER, AMOUNT, REFERENCE, REASON, GUEST,
}
data class GroupField(val key: GroupFieldKey, val label: String, val value: String, val multiline: Boolean = false, val toggle: Boolean = false, val secret: Boolean = false)
data class GroupButton(val title: String, val action: GroupAction, val value: String = "", val primary: Boolean = false, val destructive: Boolean = false, val enabled: Boolean = true)
data class GroupCard(val id: String, val title: String, val detail: String = "", val badge: String = "", val buttons: List<GroupButton> = emptyList())
data class GroupWheel(val names: List<String>, val round: SpinRound, val serverOffset: Long, val winner: String)
data class GroupState(
    val page: GroupPage = GroupPage.HOME, val title: String = "Food Run", val subtitle: String = "Good food. Great company.",
    val fields: List<GroupField> = emptyList(), val cards: List<GroupCard> = emptyList(), val buttons: List<GroupButton> = emptyList(),
    val busy: Boolean = false, val online: Boolean = false, val status: String = "", val error: String = "", val wheel: GroupWheel? = null,
    val roomCode: String = "", val canGoBack: Boolean = false,
    val progressStep: Int = -1,
) {
    val primaryAction: GroupButton? get() = buttons.firstOrNull { it.primary }
    val utilityButtons: List<GroupButton> get() = if (page == GroupPage.ROOM) buttons.filter {
        it.action in GroupLayout.roomUtilities && it != primaryAction
    } else emptyList()
    val inlineButtons: List<GroupButton> get() = buttons.filter { it != primaryAction && it !in utilityButtons }
    val extraFields: List<GroupField> get() = fields.filter {
        when (page) {
            GroupPage.CONNECT -> it.key in listOf(GroupFieldKey.HUB_URL, GroupFieldKey.FINGERPRINT)
            GroupPage.LIBRARY -> it.key == GroupFieldKey.JSON_MENU
            GroupPage.ROOM -> it.key in GroupLayout.roomExtraFields
            else -> false
        }
    }
    val mainFields: List<GroupField> get() = fields.filterNot { it in extraFields }
    val sections: List<GroupSection> get() = GroupLayout.sections(page, cards)
}
interface GroupObserver { fun changed(state: GroupState) }
object GroupText {
    val brand = "FOOD RUN / TOGETHER"
    val back = "Back"
    val backToRooms = "Back to rooms"
    val homeTitle = "Good food.\nBetter together."
    val homeSubtitle = "Make the next meal a group effort."
    val groupEyebrow = "A TABLE FOR EVERYONE"
    val groupTitle = "One room. The whole crew."
    val groupDescription = "Choose food, pick a payer and keep every order together."
    val quickDescription = "Pick who's getting the food. No setup needed."
    val libraryDescription = "Keep your favorite menus close."
    val savedRooms = "Your tables"
    val explore = "Make it a Food Run"
    val roomCode = "ROOM CODE"
    val roomOptions = "Room options & adjustments"
    val manualConnection = "Enter connection details manually"
    val pasteMenu = "Paste menu JSON"
    val details = "Your details"
    val working = "Updating your table…"
    val progressSteps = listOf("Gather", "Pick payer", "Order", "Settle")
}
interface GroupReplyCallback { fun complete(body: String, error: String) }
interface GroupSubscription { fun cancel() }
/** Native services only. Implementations deliver callbacks on their UI thread. */
interface GroupPlatform {
    fun read(key: String): String
    fun write(key: String, value: String): Boolean
    fun now(): Long
    fun uuid(): String
    fun request(hub: HubPairing, body: String, callback: GroupReplyCallback)
    fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription
    fun share(text: String, fileName: String)
    fun openLink(url: String)
    fun importMenu(callback: GroupReplyCallback)
    fun scanPairing(callback: GroupReplyCallback)
    fun discover(callback: GroupReplyCallback)
}
@Serializable data class GroupLibrary(
    val restaurants: List<RestaurantExport> = emptyList(), val accounts: List<ReceivingAccount> = emptyList(),
    val sessions: List<StoredSession> = emptyList(), val snapshots: Map<String, RoomReply> = emptyMap(),
    val selectedHub: HubPairing? = null, val displayName: String = "",
    val pending: RoomCommand? = null, val pendingHub: HubPairing? = null,
)
