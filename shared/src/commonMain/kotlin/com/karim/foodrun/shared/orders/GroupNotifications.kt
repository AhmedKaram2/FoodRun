package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.*

internal class GroupNotifications(private val c: GroupController) {
    var items = emptyList<FoodNotification>(); private set
    var selected: FoodNotification? = null; private set
    private var intendedAction = "open"
    private var pending: Pair<String, String>? = null
    private var fetching = false
    private var lastFetch = 0L
    private var registered = ""
    private var registering = false
    private var registrationGeneration = 0
    var deviceEnabled = false; private set
    private var token = ""
    private fun tr(en: String, ar: String) = if(c.library.language.startsWith("ar")) GroupUiText.translate(en, c.library.language).takeIf { it != en } ?: ar else en
    private fun request(fields: NotificationRequest, result: (NotificationReply) -> Unit = {}) {
        val hub = c.library.identityHub ?: return
        val identity = c.library.identityToken
        c.platform.notificationRequest(hub, orderJson.encodeToString(fields), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                if(c.library.identityToken != identity || c.library.identityHub != hub) return
                fetching = false
                try {
                    require(error.isEmpty()) { error }
                    val reply = orderJson.decodeFromString<NotificationReply>(body)
                    require(reply.ok) { reply.error }
                    items = reply.notifications
                    result(reply)
                    pending?.let { (id, action) ->
                        val item = items.firstOrNull { it.id == id }
                        if(item != null && (item.kind.startsWith("wallet_") || item.kind.startsWith("friend_") || c.library.sessions.any { it.roomId == item.roomId && c.sameHub(it.hub, c.library.identityHub) })) { pending = null; open(id, action) }
                    }
                } catch (failure: Exception) {
                    if(c.page == GroupPage.NOTIFICATIONS) c.error = failure.message.orEmpty()
                }
                c.publish()
            }
        })
    }
    fun refresh() {
        if(c.library.identityToken.isEmpty() || fetching) return
        if(c.platform.now() - lastFetch < 3000 && c.page != GroupPage.NOTIFICATIONS && pending == null) return
        fetching = true; lastFetch = c.platform.now()
        request(NotificationRequest(c.library.identityToken))
    }
    fun register(prompt: Boolean) {
        if(c.supportActive || registering || c.library.home?.notificationPreferences?.pushEnabled == false) return
        if(!prompt && registered.startsWith(c.library.identityToken) && registered.endsWith(c.library.language) && registered.isNotEmpty()) return
        if(c.library.identityToken.isEmpty()) { if(prompt) c.error = tr("Sign in to enable notifications.", "سجل الدخول لتفعيل الإشعارات."); return }
        val identity = c.library.identityToken
        val generation = ++registrationGeneration
        registering = true
        c.platform.pushToken(prompt, object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                if(generation != registrationGeneration) return
                registering = false
                if(identity != c.library.identityToken) return
                if(body.isEmpty() && error.isEmpty()) return
                if(error.isNotEmpty()) { if(prompt) { c.error = error; c.publish() }; return }
                try {
                    val data = orderJson.parseToJsonElement(body).jsonObject
                    token = data.getValue("token").jsonPrimitive.content
                    val key = identity + token + c.library.language
                    if(registered == key && !prompt) return
                    request(NotificationRequest(identity, "register", token = token, platform = data.getValue("platform").jsonPrimitive.content,
                        installationId = data.getValue("installationId").jsonPrimitive.content, language = if(c.library.language.startsWith("ar")) "ar" else "en")) {
                        if(generation == registrationGeneration && c.library.home?.notificationPreferences?.pushEnabled != false) { registered = key; deviceEnabled = true }
                    }
                } catch (_: Exception) { if(prompt) c.error = tr("Notifications could not be enabled. Try again.", "تعذر تفعيل الإشعارات. حاول مجدداً.") }
                c.publish()
            }
        })
    }
    fun disable() {
        if(c.supportActive) return
        registrationGeneration++; registering = false
        if(token.isNotEmpty()) request(NotificationRequest(c.library.identityToken, "unregister", token = token))
        c.platform.disablePush(); registered = ""; token = ""; deviceEnabled = false
    }
    fun clear() { registrationGeneration++; deviceEnabled = false; registering = false; items = emptyList(); selected = null; pending = null; registered = ""; token = ""; fetching = false; lastFetch = 0 }
    fun openFromPush(id: String, action: String) {
        if(!id.matches(Regex("[a-f0-9]{40}"))) return
        pending = id to action; c.page = GroupPage.NOTIFICATIONS; refresh(); c.publish()
    }
    fun open(id: String, action: String) {
        val item = items.firstOrNull { it.id == id } ?: error(tr("Refresh your notifications.", "حدث الإشعارات."))
        if(item.kind == "friend_group_added") { request(NotificationRequest(c.library.identityToken,"read",notificationId=id)); c.friends.open(); return }
        if(item.kind == "friend_room_invitation") { request(NotificationRequest(c.library.identityToken,"read",notificationId=id)); c.dispatch(GroupAction.ACCEPT_INVITE,item.transferId); return }
        if(item.kind.startsWith("wallet_")) {
            request(NotificationRequest(c.library.identityToken, "read", notificationId = id)); selected = null; c.page = GroupPage.WALLET; return
        }
        val session = c.library.sessions.firstOrNull { it.roomId == item.roomId && c.sameHub(it.hub, c.library.identityHub) }
            ?: error(tr("This room is no longer available.", "الغرفة لم تعد متاحة."))
        selected = item
        intendedAction = action.takeIf { value -> value == "open" || item.actions.any { it.id == value } } ?: "open"
        request(NotificationRequest(c.library.identityToken, "read", notificationId = id))
        c.resume(session)
    }
    private fun valid(item: FoodNotification): Boolean = c.reply?.room?.let { it.id == item.roomId && it.orderNumber == item.orderNumber } == true
    private fun target(item: FoodNotification): String {
        if(!valid(item)) return "expired"
        val room = c.room()
        return when(intendedAction) {
            "copy", "share", "order" -> if(room.payerId == c.me() && room.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW, RoomPhase.PLACED, RoomPhase.FULFILLED)) "order" else "expired"
            "confirm" -> if(room.payerId == c.me() && room.transfers.any { it.id == item.transferId && !it.refund && it.status == TransferStatus.DECLARED }) "confirm" else "expired"
            "accept" -> if(room.phase == RoomPhase.ACCEPTING && room.spin?.winnerId == c.me()) "accept" else "expired"
            "pay" -> if(room.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED) && room.restaurantPaid && room.payerId != c.me()) "pay" else "expired"
            else -> "open"
        }
    }
    fun perform(action: String) {
        if(action == "close") { selected = null; return }
        val item = requireNotNull(selected)
        require(c.online && valid(item)) { tr("Reconnect and check the current order first.", "أعد الاتصال وراجع الطلب الحالي أولاً.") }
        val target = target(item)
        when(action) {
            "copy", "share", "place" -> {
                require(target == "order")
                when(action) {
                    "copy" -> c.platform.copyToClipboard(GroupPresentation(c).restaurantOrderText())
                    "share" -> c.platform.share(GroupPresentation(c).restaurantOrderText(), "")
                    else -> { require(c.reply?.progress?.canReview == true); c.command(CommandKind.PLACE) }
                }
            }
            "confirm" -> { require(target == "confirm"); c.command(CommandKind.CONFIRM_TRANSFER, transferId = item.transferId) }
            "accept" -> { require(target == "accept"); c.command(CommandKind.ACCEPT_DUTY) }
            "pay" -> { require(target == "pay"); c.page = GroupPage.PAYMENT; selected = null }
        }
    }
    fun inbox(): GroupFlowContent {
        val walletCards = c.walletFunds.cards("")
        val cards = items.map { item ->
            val direct = if(item.kind.startsWith("wallet_")) {
                val matching = walletCards.filter { it.id in listOf("wallet-top-up:${item.transferId}","wallet-batch:${item.transferId}","wallet-payment:${item.transferId}") }
                val payment = c.library.home?.wallet?.payments?.firstOrNull { it.id == item.transferId }
                val grouped = payment?.takeIf { it.holderId == c.library.home?.profile?.userId && it.status == WalletPaymentStatus.OWING }?.let { target ->
                    val first = c.library.home?.wallet?.payments?.firstOrNull { it.holderId == target.holderId && it.recipientId == target.recipientId && it.currency == target.currency && it.status == WalletPaymentStatus.OWING }
                    walletCards.filter { it.id == "wallet-group:${first?.id}" }
                }.orEmpty()
                val room = payment?.let { c.library.snapshots[it.roomId]?.room }
                val saved = payment?.let { p -> c.library.sessions.firstOrNull { it.roomId == p.roomId } }
                val claim = payment?.let { p -> room?.transfers?.firstOrNull { it.id == "wallet-${p.id}" && it.status == TransferStatus.DECLARED } }
                val approval = if(room?.orderNumber == payment?.orderNumber && room?.payerId == saved?.memberId && claim != null)
                    listOf(GroupButton(tr("Approve wallet payment","الموافقة على دفعة المحفظة"),GroupAction.WALLET_CONFIRM,"${room!!.id}|${claim.memberId}",enabled=!c.busy),
                        GroupButton(tr("Decline","رفض"),GroupAction.WALLET_REJECT,"${room.id}|${claim.memberId}",enabled=!c.busy)) else emptyList()
                (matching + grouped).flatMap { it.buttons }.filter { it.action == GroupAction.WALLET_FUNDS_ACTION } + approval
            } else emptyList()
            val room = c.library.snapshots[item.roomId]?.room
            val saved = c.library.sessions.firstOrNull { it.roomId == item.roomId }
            val transfer = room?.transfers?.firstOrNull { it.id == item.transferId && it.status == TransferStatus.DECLARED }
            val confirm = if(room?.orderNumber == item.orderNumber && room.payerId == saved?.memberId && transfer?.refund == false)
                listOf(GroupButton(tr("Confirm payment received", "تأكيد استلام الدفعة"),GroupAction.WALLET_CONFIRM,"${room.id}|${transfer.memberId}",enabled=!c.busy),
                    GroupButton(tr("Not received", "لم أستلم"),GroupAction.WALLET_REJECT,"${room.id}|${transfer.memberId}",enabled=!c.busy)) else emptyList()
            val receipt = c.library.snapshots[item.roomId]?.receipts?.firstOrNull { it.memberId == saved?.memberId }
            val canPay = room?.orderNumber == item.orderNumber && room.restaurantPaid && room.payerId != saved?.memberId && receipt != null && receipt.balance > 0 &&
                room.transfers.none { it.memberId == saved?.memberId && it.status == TransferStatus.DECLARED }
            val pay = if(canPay && item.actions.any { it.id == "pay" }) listOf(GroupButton(
                tr("I sent the full amount", "أرسلت كامل المبلغ") + " · " + Money.format(requireNotNull(receipt).balance,receipt.currency),GroupAction.WALLET_PAY,"${room!!.id}|${saved!!.memberId}",enabled=!c.busy)) else emptyList()
            GroupCard("notification:${item.id}",item.title,item.body,if(item.read) "" else tr("New","جديد"),
                direct + confirm + pay + item.actions.map { GroupButton(it.title,GroupAction.OPEN_NOTIFICATION,"${item.id}:${it.id}") })
        }
            .ifEmpty { listOf(GroupCard("notifications-empty", tr("No notifications yet", "لا توجد إشعارات بعد"), tr("Order and payment updates will appear here.", "ستظهر تحديثات الطلبات والمدفوعات هنا."))) }
        return GroupFlowContent(cards = cards, buttons = if(deviceEnabled) listOf(
            GroupButton(tr("Turn off device notifications", "إيقاف إشعارات الجهاز"), GroupAction.DISABLE_ALERTS))
            else listOf(GroupButton(tr("Enable notifications", "تفعيل الإشعارات"), GroupAction.ENABLE_ALERTS)))
    }
    fun actionCard(): GroupCard? {
        val item = selected ?: return null
        if(c.session?.roomId != item.roomId) return null
        val target = target(item)
        val actions = mutableListOf<GroupButton>()
        fun button(en: String, ar: String, value: String, allowed: Boolean = true) { actions += GroupButton(tr(en, ar), GroupAction.NOTIFICATION_ACTION, value, enabled = allowed && c.online && !c.busy) }
        var detail = item.body
        when(target) {
            "order" -> { button("Copy order", "نسخ الطلب", "copy"); button("Share order", "مشاركة الطلب", "share"); button("I sent the full order", "أرسلت الطلب الكامل", "place", c.reply?.progress?.canReview == true) }
            "confirm" -> {
                val transfer = c.room().transfers.single { it.id == item.transferId }
                detail = "${c.room().members.firstOrNull { it.id == transfer.memberId }?.name} · ${Money.format(transfer.amount, c.room().restaurant.currency)}\n${transfer.reference}\n" + tr("Check that the money reached your account before confirming.", "تحقق من وصول المبلغ لحسابك قبل التأكيد.")
                button("Confirm payment received", "تأكيد استلام الدفعة", "confirm")
            }
            "accept" -> button("Accept selection", "قبول الاختيار", "accept")
            "pay" -> button("Open my payment", "فتح دفعتي", "pay")
            "expired" -> detail = tr("This action is no longer available. Check the current room below.", "هذا الإجراء لم يعد متاحاً. راجع الغرفة الحالية بالأسفل.")
        }
        actions += GroupButton(tr("Close", "إغلاق"), GroupAction.NOTIFICATION_ACTION, "close")
        return GroupCard("notification-action", item.title, detail, buttons = actions)
    }
}
