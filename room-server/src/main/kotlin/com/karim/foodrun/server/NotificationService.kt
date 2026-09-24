package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

@Serializable internal data class PushDevice(val userId: String, val token: String, val platform: String, val installationId: String, val language: String, val updatedAt: Long)
@Serializable internal data class PushJob(val userId: String, val notification: FoodNotification, val devices: List<String>, val attempts: Int = 0, val nextAt: Long = 0)
internal data class PushDelivery(val key: String, val job: PushJob, val deviceKey: String, val device: PushDevice)
internal enum class PushResult { SENT, INVALID_TOKEN, RETRY }
internal fun interface PushSender { fun send(device: PushDevice, notification: FoodNotification): PushResult }

/** Inbox and outbox are committed with the room mutation; network delivery happens after commit. */
internal class NotificationService(private val db: RoomDatabase, private val clock: () -> Long) {
    fun request(uid: String, request: NotificationRequest, pushAvailable: Boolean): NotificationReply {
        require(request.notificationId.length <= 100 && request.token.length <= 4096 && request.installationId.length <= 100)
        when (request.action) {
            "list" -> Unit
            "read" -> {
                val key = "notification:$uid:${request.notificationId}"
                db.record(key)?.let { body ->
                    val value = orderJson.decodeFromString<FoodNotification>(body)
                    if (!value.read) db.putRecord(key, orderJson.encodeToString(value.copy(read = true)))
                }
            }
            "register" -> {
                require(pushAvailable) { "Push delivery is not configured on this hub. Your notification inbox is still available." }
                require(request.platform in listOf("web", "android", "ios") && request.language in listOf("en", "ar"))
                require(request.token.length in 20..4096 && request.token.none(Char::isWhitespace)) { "Invalid notification token." }
                require(request.installationId.matches(Regex("[A-Za-z0-9-]{16,100}")))
                val key = "push-device:${RoomService.hash(request.token)}"
                val device = PushDevice(uid, request.token, request.platform, request.installationId, request.language, clock())
                val existing = devices()
                existing.filter { (id, value) -> id != key && value.platform == device.platform && value.installationId == device.installationId }
                    .forEach { db.deleteRecord(it.first) }
                val previous = existing.firstOrNull { it.first == key }?.second
                if (previous == null || previous.copy(updatedAt = device.updatedAt) != device || device.updatedAt - previous.updatedAt > 86_400_000)
                    db.putRecord(key, orderJson.encodeToString(device))
                devices().filter { it.second.userId == uid }.sortedByDescending { it.second.updatedAt }.drop(6).forEach { db.deleteRecord(it.first) }
            }
            "unregister" -> {
                val key = "push-device:${RoomService.hash(request.token)}"
                db.record(key)?.let { if (orderJson.decodeFromString<PushDevice>(it).userId == uid) db.deleteRecord(key) }
            }
            else -> error("Unknown notification action.")
        }
        val items = db.records("notification:$uid:").map { orderJson.decodeFromString<FoodNotification>(it.second) }
            .filter { accessible(uid, it) }.sortedByDescending { it.createdAt }.take(100)
        return NotificationReply(notifications = items, pushAvailable = pushAvailable)
    }

    private fun devices() = db.records("push-device:").map { it.first to orderJson.decodeFromString<PushDevice>(it.second) }
    private fun accessible(uid: String, item: FoodNotification): Boolean {
        if (AccountRestrictions.current(db, uid, clock()) != null || AccountRestrictions.forRoom(db, uid, item.roomId, clock()) != null) return false
        val room = db.room(item.roomId) ?: return false
        val membership = db.record("membership:$uid:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it) }
        return membership != null && room.activeMembers.any { it.id == membership.memberId }
    }
    private fun add(room: Room, memberId: String, eventId: String, kind: String, title: Pair<String, String>, body: Pair<String, String>, actions: List<String>, transferId: String = "") {
        val uid = db.record("member-user:${room.id}:$memberId") ?: return
        addUser(uid, room, eventId, kind, title, body, actions, transferId)
    }
    private fun addUser(uid: String, room: Room, eventId: String, kind: String, title: Pair<String, String>, body: Pair<String, String>, actions: List<String>, transferId: String = "") {
        val id = RoomService.hash("${room.id}:${room.orderNumber}:$eventId:$uid").take(40)
        val key = "notification:$uid:$id"
        if (db.record(key) != null) return
        val ar = db.record("profile:$uid")?.let { orderJson.decodeFromString<FoodProfile>(it).language == "ar" } == true
        val labels = mapOf("open" to ("Open room" to "فتح الغرفة"), "order" to ("Review & send order" to "مراجعة وإرسال الطلب"),
            "copy" to ("Copy order" to "نسخ الطلب"), "share" to ("Share order" to "مشاركة الطلب"), "pay" to ("Payment sent" to "أرسلت الدفع"),
            "confirm" to ("Payment received" to "استلمت الدفع"), "accept" to ("Accept selection" to "قبول الاختيار"))
        val item = FoodNotification(id, room.id, room.orderNumber, kind, if(ar) title.second else title.first,
            if(ar) body.second else body.first, actions.map { action -> NotificationAction(action, labels.getValue(action).let { if(ar) it.second else it.first }) }, transferId, clock())
        db.putRecord(key, orderJson.encodeToString(item))
        val targets = devices().filter { it.second.userId == uid && it.second.updatedAt > clock() - 90L * 86_400_000 }.map { it.first }
        if (targets.isNotEmpty()) db.putRecord("push-job:$id", orderJson.encodeToString(PushJob(uid, item, targets)))
        db.records("notification:$uid:").map { it.first to orderJson.decodeFromString<FoodNotification>(it.second) }
            .sortedByDescending { it.second.createdAt }.drop(100).forEach { db.deleteRecord(it.first) }
    }

    fun changed(before: Room, room: Room, actor: String, command: RoomCommand) {
        val name = room.members.firstOrNull { it.id == actor }?.name.orEmpty()
        fun payer(kind: String, title: Pair<String,String>, actions: List<String> = listOf("open"), transfer: String = "", body: Pair<String,String> = ("$name · ${room.name}" to "$name · ${room.name}")) {
            room.payerId?.let { add(room, it, "${command.commandId}:$kind", kind, title, body, actions, transfer) }
        }
        fun all(kind: String, title: Pair<String,String>, actions: List<String> = listOf("open")) {
            room.orderingMembers.forEach { add(room, it.id, "${command.commandId}:$kind", kind, title, room.name to room.name, actions) }
        }
        when(command.kind) {
            CommandKind.SELECT_PAYER, CommandKind.ACCEPT_DUTY, CommandKind.HANDOVER -> payer("selected", "You are the chosen person" to "أنت المسؤول عن الطلب", listOf("order"))
            CommandKind.SUBMIT_CART -> {
                if (actor != room.payerId) payer("order_submitted", "An order was submitted" to "تم إرسال طلب شخص")
            }
            CommandKind.PLACE -> all("order_placed", "The full order was submitted" to "تم إرسال الطلب الكامل", listOf("pay", "open"))
            CommandKind.PAY_RESTAURANT -> all("restaurant_paid", "The restaurant has been paid" to "تم دفع حساب المطعم", listOf("pay", "open"))
            CommandKind.FULFILL -> all("order_arrived", "Your order has arrived" to "وصل طلبكم")
            CommandKind.CANCEL -> all("order_cancelled", "This order was cancelled" to "تم إلغاء هذا الطلب")
            CommandKind.REOPEN -> all("order_reopened", "The order is open for changes" to "الطلب متاح للتعديل")
            CommandKind.DECLARE_TRANSFER -> room.transfers.firstOrNull { item -> before.transfers.none { it.id == item.id } }?.let {
                payer("payment_sent", "Payment sent — confirm receipt" to "تم إرسال دفعة — أكد الاستلام", listOf("confirm", "open"), it.id)
            }
            CommandKind.CONFIRM_TRANSFER, CommandKind.REJECT_TRANSFER, CommandKind.RECORD_PAYMENT -> {
                val transfer = room.transfers.firstOrNull { it.id == command.transferId } ?: room.transfers.firstOrNull { item -> before.transfers.none { it.id == item.id } }
                transfer?.let {
                    val confirmed = it.status == TransferStatus.CONFIRMED
                    add(room, it.memberId, command.commandId, if(confirmed) "payment_received" else "payment_rejected",
                        if(confirmed) "Payment received" to "تم استلام دفعتك" else "Payment needs attention" to "دفعتك تحتاج مراجعة", room.name to room.name,
                        if(confirmed) listOf("open") else listOf("pay", "open"), it.id)
                }
            }
            CommandKind.DECLARE_REFUND -> room.transfers.firstOrNull { item -> item.refund && before.transfers.none { it.id == item.id } }?.let {
                add(room, it.memberId, command.commandId, "refund_sent", "A refund was sent to you" to "تم إرسال مبلغ مسترد لك", room.name to room.name, listOf("open"))
            }
            CommandKind.ADJUST_BILL, CommandKind.UPDATE_PAYMENT_SHARE -> all("bill_updated", "Your bill was updated" to "تم تحديث الحساب")
            else -> Unit
        }
        fun ready(value: Room) = value.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW) && value.orderingMembers.isNotEmpty() && value.orderingMembers.all { member -> value.carts.any { it.memberId == member.id && it.submitted } }
        if (ready(room) && (!ready(before) || before.payerId != room.payerId)) payer("all_submitted", "Everyone submitted their order" to "الجميع أرسل طلبه", listOf("copy", "share", "order"),
            body = "${room.name} · Review, copy or share the full order, then mark it sent." to "${room.name} · راجع الطلب الكامل وانسخه أو شاركه ثم أكد إرساله.")
    }
    fun selected(room: Room) { room.spin?.let { spin -> add(room, spin.winnerId, "spin:${spin.id}", "selection_pending", "You were chosen!" to "وقع الاختيار عليك!", room.name to room.name, listOf("accept", "open")) } }
    fun paymentRoom(room: Room) { room.orderingMembers.filter { it.id != room.payerId }.forEach { add(room, it.id, "created", "payment_due", "Your payment share is ready" to "حصتك جاهزة للدفع", room.name to room.name, listOf("pay", "open")) } }

    fun pending(): PushDelivery? {
        for ((key, body) in db.records("push-job:")) {
            val job = orderJson.decodeFromString<PushJob>(body)
            if (job.nextAt > clock()) continue
            val item = db.record("notification:${job.userId}:${job.notification.id}")?.let { orderJson.decodeFromString<FoodNotification>(it) }
            if (item == null || item.read || job.notification.createdAt < clock() - 86_400_000 || !accessible(job.userId, job.notification)) { db.deleteRecord(key); continue }
            val target = job.devices.firstOrNull { id -> db.record(id)?.let { orderJson.decodeFromString<PushDevice>(it).userId == job.userId } == true }
            if (target == null) { db.deleteRecord(key); continue }
            return PushDelivery(key, job, target, orderJson.decodeFromString(db.record(target)!!))
        }
        return null
    }
    fun delivered(delivery: PushDelivery, result: PushResult) {
        if (db.record(delivery.key) == null) return
        if (result == PushResult.INVALID_TOKEN && db.record(delivery.deviceKey)?.let { orderJson.decodeFromString<PushDevice>(it) } == delivery.device) db.deleteRecord(delivery.deviceKey)
        val left = if(result == PushResult.RETRY) delivery.job.devices else delivery.job.devices - delivery.deviceKey
        if (left.isEmpty() || delivery.job.attempts >= 10) db.deleteRecord(delivery.key)
        else db.putRecord(delivery.key, orderJson.encodeToString(delivery.job.copy(devices = left, attempts = if(result == PushResult.RETRY) delivery.job.attempts + 1 else 0,
            nextAt = if(result == PushResult.RETRY) clock() + minOf(3_600_000L, 10_000L * (1L shl delivery.job.attempts)) else 0)))
    }
}
