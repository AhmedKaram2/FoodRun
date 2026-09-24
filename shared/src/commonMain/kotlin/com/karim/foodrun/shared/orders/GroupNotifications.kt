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
    var deviceEnabled = false; private set
    private var token = ""
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
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
                        if(item != null && c.library.sessions.any { it.roomId == item.roomId && c.sameHub(it.hub, c.library.identityHub) }) { pending = null; open(id, action) }
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
        if(registering) return
        if(!prompt && registered.startsWith(c.library.identityToken) && registered.endsWith(c.library.language) && registered.isNotEmpty()) return
        if(c.library.identityToken.isEmpty()) { if(prompt) c.error = tr("Sign in to enable notifications.", "سجل الدخول لتفعيل الإشعارات."); return }
        val identity = c.library.identityToken
        registering = true
        c.platform.pushToken(prompt, object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
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
                        installationId = data.getValue("installationId").jsonPrimitive.content, language = c.library.language)) { registered = key; deviceEnabled = true }
                } catch (_: Exception) { if(prompt) c.error = tr("Notifications could not be enabled. Try again.", "تعذر تفعيل الإشعارات. حاول مجدداً.") }
                c.publish()
            }
        })
    }
    fun disable() {
        if(token.isNotEmpty()) request(NotificationRequest(c.library.identityToken, "unregister", token = token))
        c.platform.disablePush(); registered = ""; token = ""; deviceEnabled = false
    }
    fun clear() { deviceEnabled = false; registering = false; items = emptyList(); selected = null; pending = null; registered = ""; token = ""; fetching = false; lastFetch = 0 }
    fun openFromPush(id: String, action: String) {
        if(!id.matches(Regex("[a-f0-9]{40}"))) return
        pending = id to action; c.page = GroupPage.NOTIFICATIONS; refresh(); c.publish()
    }
    fun open(id: String, action: String) {
        val item = items.firstOrNull { it.id == id } ?: error(tr("Refresh your notifications.", "حدث الإشعارات."))
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
        val cards = items.map { item -> GroupCard("notification:${item.id}", item.title, item.body, if(item.read) "" else tr("New", "جديد"),
            item.actions.map { GroupButton(it.title, GroupAction.OPEN_NOTIFICATION, "${item.id}:${it.id}") }) }
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
