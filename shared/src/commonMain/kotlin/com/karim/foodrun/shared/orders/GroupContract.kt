package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

enum class GroupPage { BLOCK_REQUEST, HOME, PROFILE, PEOPLE, CUSTOM_ITEM, PRICE_ITEM, PRICES, QUICK_SPIN, CONNECT, SETUP, LIBRARY, RESTAURANT, ROOM, ITEM, ACCOUNT, RECEIPTS, HISTORY }
enum class GroupAction {
    OPEN_BLOCK_REQUEST, REQUEST_BLOCK, OPEN_POLL_RESTAURANTS, TOGGLE_POLL_RESTAURANT, CONFIRM_POLL_RESTAURANTS, OPEN_ORDER_PRICES,
    OPEN_PROFILE, SET_LANGUAGE, SIGN_IN, REGISTER, SAVE_PROFILE, SIGN_OUT, RESET_PASSWORD, ENABLE_CLOUD, ENABLE_ALERTS, OPEN_PEOPLE, INVITE_PERSON, ACCEPT_INVITE, OPEN_CUSTOM_ITEM, ADD_CUSTOM_ITEM, OPEN_PRICE_ITEM, SAVE_ITEM_PRICE, USE_OPEN_ORDER, BACK, QUICK_SPIN, CREATE, JOIN, USE_INTERNET, CONNECT, DISCOVER, SCAN, OPEN_LIBRARY, NEW_RESTAURANT, EDIT_RESTAURANT,
    IMPORT_MENU, PREVIEW_IMPORT, CONFIRM_IMPORT, EXPORT_MENU, SAVE_RESTAURANT, DELETE_RESTAURANT, SELECT_RESTAURANT, EDIT_ROOM_RESTAURANT, SELECT_TAX_TREATMENT,
    ADD_MENU_ITEM, REMOVE_MENU_ITEM, SAVE_ROOM_RESTAURANT, UPDATE_ROOM_MENU, RESUME, CREATE_ROOM, JOIN_ROOM, RETRY, REFRESH,
    PARTICIPATE, READY, APPROVE, APPROVE_LATE_JOIN, REMOVE, VOTE_RESTAURANT, FINALIZE_RESTAURANT,
    PREPARE_SPIN, ABORT_SPIN, ACCEPT_DUTY, DECLINE_DUTY, OPEN_ITEM, EDIT_CART_ITEM,
    SELECT_VARIANT, TOGGLE_OPTION, ADD_CART_ITEM, REMOVE_CART_ITEM, SUBMIT_CART, REVIEW, CONFIRM_QUOTE, REOPEN,
    OPEN_ACCOUNT, NEW_ACCOUNT, SELECT_ACCOUNT, SAVE_ACCOUNT, SHARE_ACCOUNT, DELETE_ACCOUNT, SET_FEES, PLACE, PAY_RESTAURANT,
    FULFILL, CALL_RESTAURANT, SHARE_RESTAURANT_ORDER, SHARE_ORDER_WHATSAPP, OPEN_RECEIPTS, OPEN_HISTORY, LOAD_OLDER_HISTORY, DECLARE_TRANSFER, CONFIRM_TRANSFER, REJECT_TRANSFER,
    DECLARE_REFUND, CONFIRM_REFUND, ADJUST_BILL, APPROVE_ADJUSTMENT, HANDOVER, ARCHIVE, CANCEL, NEXT_ORDER, SHARE_ROOM, SHARE_RECEIPT,
    WALLET_PAY, WALLET_REFUND, WALLET_CONFIRM, WALLET_REJECT, WALLET_COPY, COPY_RESTAURANT_PHONE, GOOGLE_SIGN_IN, SELECT_PAYER,
    REUSE_ORDER, FAVORITE_ORDER, REMOVE_FAVORITE_ORDER, COPY_PAYMENT_DETAILS, USE_REMAINING_AMOUNT, MORE_PREVIOUS_ORDERS,
    QUICK_ADD_ITEM, INCREASE_CART_QUANTITY, DECREASE_CART_QUANTITY,
}
internal const val FOOD_RUN_INTERNET_API = "https://foodrun-api-q6b9.onrender.com"
enum class GroupFieldKey {
    BLOCK_MEMBER, BLOCK_HOURS, BLOCK_REASON, PAYER_MODE, PAYER_CHOICE, EMAIL, PASSWORD, PROFILE_PHONE, PHOTO, ACCOUNT_IBAN_DRAFT, ACCOUNT_AANI_DRAFT, AANI, DISCOVERABLE, CUSTOM_NAME, HUB_URL, FINGERPRINT, PAIRING_LINK, NAME, ROOM_NAME, ROOM_CODE, EXPECTED_NAMES, RESTAURANT_POLL, DELIVERY, DESTINATION,
    RESTAURANT_NAME, BRANCH, CURRENCY, PHONE, ADDRESS, MENU_ITEM_NAME, MENU_ITEM_PRICE, DELIVERY_FEE, SERVICE_FEE, DISCOUNT, TAX_RATE, MINIMUM_ORDER,
    RESTAURANT_SEARCH, RESTAURANT_EMIRATE, RESTAURANT_AREA, RESTAURANT_MEAL, MENU_SEARCH, MENU_CATEGORY, PROPORTIONAL, AUTOMATIC_DELIVERY, JSON_MENU, ELIGIBLE, QUANTITY, NOTE, ACCOUNT_HOLDER, ACCOUNT_BANK, ACCOUNT_IDENTIFIER, AMOUNT, BILL_ADJUSTMENT, REFERENCE, REASON, GUEST,
}
data class GroupChoice(val value: String, val label: String)
data class GroupField(val key: GroupFieldKey, val label: String, val value: String, val multiline: Boolean = false, val toggle: Boolean = false, val secret: Boolean = false, val choices: List<GroupChoice> = emptyList())
data class GroupButton(val title: String, val action: GroupAction, val value: String = "", val primary: Boolean = false, val destructive: Boolean = false, val enabled: Boolean = true)
data class GroupCard(val id: String, val title: String, val detail: String = "", val badge: String = "", val buttons: List<GroupButton> = emptyList())
data class GroupWheel(val names: List<String>, val round: SpinRound, val serverOffset: Long, val winner: String)
data class GroupState(
    val page: GroupPage = GroupPage.HOME, val title: String = "Food Run", val subtitle: String = "Good food. Great company.",
    val fields: List<GroupField> = emptyList(), val cards: List<GroupCard> = emptyList(), val buttons: List<GroupButton> = emptyList(),
    val busy: Boolean = false, val online: Boolean = false, val status: String = "", val error: String = "", val wheel: GroupWheel? = null,
    val roomCode: String = "", val canGoBack: Boolean = false,
    val progressStep: Int = -1,
    val inviteLink: String = "",
    val rtl: Boolean = false,
    val inviteSummary: String = "",
    val accessBlocked: Boolean = false,
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
            GroupPage.SETUP -> it.key in listOf(GroupFieldKey.EXPECTED_NAMES, GroupFieldKey.DELIVERY_FEE, GroupFieldKey.SERVICE_FEE, GroupFieldKey.DISCOUNT, GroupFieldKey.PROPORTIONAL)
            GroupPage.ROOM -> it.key in GroupLayout.roomExtraFields
            else -> false
        }
    }
    val mainFields: List<GroupField> get() = fields.filterNot { it in extraFields }
    val topCards: List<GroupCard> get() = emptyList()
    val sections: List<GroupSection> get() = GroupLayout.sections(page, cards.filterNot { it in topCards }).map { it.copy(title = GroupUiText.translate(it.title, rtl)) }
}
interface GroupObserver { fun changed(state: GroupState) }
object GroupText {
    fun localized(value: String, rtl: Boolean): String = if(!rtl) value else GroupUiText.translate(value, true).takeIf { it != value } ?: when(value) {
        homeTitle -> "أكل طيب.\nأحلى مع بعض."
        homeSubtitle -> "شارك أصحابك الوجبة القادمة."
        groupEyebrow -> "مكان للجميع"
        groupTitle -> "غرفة واحدة لكل المجموعة."
        groupDescription -> "اختاروا الطعام ومسؤول الطلب واجمعوا طلباتكم معاً."
        quickDescription -> "اختاروا من يجلب الطعام دون إعداد مسبق."
        libraryDescription -> "احتفظ بقوائم مطاعمك المفضلة."
        savedRooms -> "مجموعاتك"
        explore -> "خيارات فود رن"
        back, backToRooms -> "رجوع"
        working -> "جارٍ تحديث مجموعتك…"
        roomCode -> "رمز الغرفة"
        roomOptions -> "خيارات الغرفة والتعديلات"
        manualConnection -> "إدخال بيانات الاتصال يدوياً"
        pasteMenu -> "لصق قائمة المطعم"
        details -> "بياناتك"
        "Join" -> "انضمام"
        "Restaurant" -> "المطعم"
        "Sandwiches" -> "السندويشات"
        "Pick payer" -> "مسؤول الطلب"
        "Confirm" -> "تأكيد"
        "Settle" -> "تسوية"
        else -> value
    }
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
    val progressSteps = listOf("Join", "Restaurant", "Sandwiches", "Pick payer", "Send order", "Settle")
}
interface GroupReplyCallback { fun complete(body: String, error: String) }
interface GroupSubscription { fun cancel() }
/** Native services only. Implementations deliver callbacks on their UI thread. */
interface GroupPlatform {
    fun notify(title: String, body: String) {}
    fun enableNotifications() {}
    fun read(key: String): String
    fun write(key: String, value: String): Boolean
    fun now(): Long
    fun uuid(): String
    fun request(hub: HubPairing, body: String, callback: GroupReplyCallback)
    fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription
    fun share(text: String, fileName: String)
    fun copyToClipboard(text: String) { share(text, "") }
    fun googleSignIn(callback: GroupReplyCallback) { callback.complete("", "Google sign-in is unavailable on this device.") }
    fun openLink(url: String)
    fun importMenu(callback: GroupReplyCallback)
    fun scanPairing(callback: GroupReplyCallback)
    fun discover(callback: GroupReplyCallback)
}
@Serializable data class GroupLibrary(
    val restaurants: List<RestaurantExport> = emptyList(), val accounts: List<ReceivingAccount> = emptyList(),
    val sessions: List<StoredSession> = emptyList(), val snapshots: Map<String, RoomReply> = emptyMap(),
    val selectedHub: HubPairing? = null, val displayName: String = "",
    val identityToken: String = "", val identityHub: HubPairing? = null, val home: HomePayload? = null, val seenAlerts: List<String> = emptyList(),
    val pending: RoomCommand? = null, val pendingHub: HubPairing? = null,
    val language: String = "en", val managedRestaurantIds: Set<String> = emptySet(),
)
