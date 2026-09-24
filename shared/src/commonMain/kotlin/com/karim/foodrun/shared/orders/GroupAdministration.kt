package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupAdministration(private val c: GroupController) {
    var allowed = false; private set
    var editingRestaurant = false; private set
    private var checkedIdentity = ""
    private var checking = false
    private var dashboard: AdminDashboard? = null
    private var preview: AdminCleanupPreview? = null
    private var tab = "overview"
    private var userId = ""
    private var pendingAction = ""
    private var pendingTarget = ""
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun field(key: GroupFieldKey, en: String, ar: String, toggle: Boolean = false, secret: Boolean = false, choices: List<GroupChoice> = emptyList()) = GroupField(key, tr(en, ar), c.text(key), toggle = toggle, secret = secret, choices = choices)
    private fun button(en: String, ar: String, action: GroupAction, value: String = "", primary: Boolean = false, destructive: Boolean = false) = GroupButton(tr(en, ar), action, value, primary, destructive)
    fun clear() { allowed = false; checking = false; checkedIdentity = ""; dashboard = null; preview = null; editingRestaurant = false }
    fun checkAccess() {
        if(c.library.identityToken.isBlank() || checkedIdentity == c.library.identityToken || checking) return
        checking = true
        request("access", silent = true)
    }
    private fun request(action: String, payload: String = "", silent: Boolean = false, done: () -> Unit = {}) {
        val hub = c.library.identityHub ?: return
        val identity = c.library.identityToken
        if(!silent) { c.busy = true; c.publish() }
        c.platform.adminRequest(hub, orderJson.encodeToString(NativeAdminRequest(identity, action, payload)), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                if(identity != c.library.identityToken || hub != c.library.identityHub) return
                checking = false
                if(!silent) c.busy = false
                try {
                    require(error.isEmpty()) { error }
                    val result = orderJson.decodeFromString<NativeAdminReply>(body)
                    if(action == "access") { checkedIdentity = identity; allowed = result.ok }
                    require(result.ok) { result.error }
                    allowed = true; result.dashboard?.let { dashboard = it }
                    if(action == "cleanup-preview") preview = result.preview
                    if(action == "cleanup-delete") preview = null
                    done()
                } catch(failure: Exception) { if(!silent) c.error = failure.message.orEmpty() }
                c.publish()
            }
        })
    }
    fun open() { c.page = GroupPage.ADMIN; request("dashboard") }
    fun tab(value: String) {
        tab = value; c.draft[GroupFieldKey.ADMIN_SEARCH] = ""
        if(value == "settings") dashboard?.settings?.let {
            c.draft[GroupFieldKey.ADMIN_REGISTRATION] = it.registrationsEnabled.toString()
            c.draft[GroupFieldKey.ADMIN_ROOMS] = it.roomCreationEnabled.toString()
            c.draft[GroupFieldKey.ADMIN_MESSAGE] = it.maintenanceMessage
        }
        if(value == "cleanup") { c.draft[GroupFieldKey.ADMIN_SCOPE] = "closedRooms"; c.draft[GroupFieldKey.ADMIN_DAYS] = "30"; c.draft[GroupFieldKey.ADMIN_CONFIRMATION] = ""; preview = null }
    }
    fun user(id: String) {
        userId = id
        val profile = dashboard?.users?.firstOrNull { it.id == id }?.profile
        c.draft[GroupFieldKey.ADMIN_NAME] = profile?.name.orEmpty()
        c.draft[GroupFieldKey.ADMIN_PHONE] = profile?.phone.orEmpty()
        c.draft[GroupFieldKey.ADMIN_PHOTO] = profile?.photo.orEmpty()
        c.draft[GroupFieldKey.ADMIN_LANGUAGE] = profile?.language ?: "en"
        c.draft[GroupFieldKey.ADMIN_DISCOVERABLE] = (profile?.discoverable ?: true).toString()
        c.draft[GroupFieldKey.ADMIN_EMAIL] = ""; c.draft[GroupFieldKey.ADMIN_PASSWORD] = ""
        c.draft[GroupFieldKey.ADMIN_METHOD] = profile?.payment?.let { if(it.method == PaymentMethod.AANI) "aani" else "bank" } ?: "none"
        c.draft[GroupFieldKey.ADMIN_HOLDER] = profile?.payment?.holder.orEmpty()
        c.draft[GroupFieldKey.ADMIN_BANK] = profile?.payment?.bank.orEmpty()
        c.draft[GroupFieldKey.ADMIN_IDENTIFIER] = profile?.payment?.identifier.orEmpty()
        c.page = GroupPage.ADMIN_USER
    }
    fun saveUser() {
        val change = if(userId.isEmpty()) AdminUserMutation(action = "create", email = c.text(GroupFieldKey.ADMIN_EMAIL).trim(), password = c.text(GroupFieldKey.ADMIN_PASSWORD),
            name = c.text(GroupFieldKey.ADMIN_NAME).trim(), phone = c.text(GroupFieldKey.ADMIN_PHONE).trim(), language = c.text(GroupFieldKey.ADMIN_LANGUAGE))
        else {
            val profile = requireNotNull(dashboard?.users?.single { it.id == userId }?.profile)
            val payment = when(c.text(GroupFieldKey.ADMIN_METHOD)) {
                "none" -> null
                else -> ReceivingAccount(profile.payment?.id ?: c.platform.uuid(), c.text(GroupFieldKey.ADMIN_HOLDER), c.text(GroupFieldKey.ADMIN_BANK), c.text(GroupFieldKey.ADMIN_IDENTIFIER), method = if(c.text(GroupFieldKey.ADMIN_METHOD) == "aani") PaymentMethod.AANI else PaymentMethod.BANK).normalized().also { it.validate() }
            }
            AdminUserMutation(userId = userId, action = "profile", profile = profile.copy(name = c.text(GroupFieldKey.ADMIN_NAME), phone = c.text(GroupFieldKey.ADMIN_PHONE), photo = c.text(GroupFieldKey.ADMIN_PHOTO), language = c.text(GroupFieldKey.ADMIN_LANGUAGE), discoverable = c.flag(GroupFieldKey.ADMIN_DISCOVERABLE), payment = payment).normalized().also { it.validate() })
        }
        c.draft.remove(GroupFieldKey.ADMIN_PASSWORD)
        request("user", orderJson.encodeToString(change)) { c.page = GroupPage.ADMIN }
    }
    fun action(value: String) {
        pendingAction = value.substringBefore('|'); pendingTarget = value.substringAfter('|')
        c.draft[GroupFieldKey.ADMIN_DURATION] = "24"; c.draft[GroupFieldKey.ADMIN_REASON] = ""
        c.draft[GroupFieldKey.ADMIN_SCOPE] = ""; c.draft[GroupFieldKey.ADMIN_CONFIRMATION] = ""
        c.page = GroupPage.ADMIN_CONFIRM
    }
    fun confirm() {
        val d = requireNotNull(dashboard)
        when(pendingAction) {
            "block", "unblock", "remove", "restore" -> {
                val target = d.users.single { it.id == pendingTarget }
                if(pendingAction == "remove") require(c.text(GroupFieldKey.ADMIN_CONFIRMATION) == target.name) { "Type the person's name to confirm removal." }
                request("user", orderJson.encodeToString(AdminUserMutation(userId = target.id, action = pendingAction,
                    durationHours = c.text(GroupFieldKey.ADMIN_DURATION).toIntOrNull() ?: 0, reason = c.text(GroupFieldKey.ADMIN_REASON),
                    scopeRoomId = c.text(GroupFieldKey.ADMIN_SCOPE), confirmation = if(pendingAction == "remove") target.id else ""))) { c.page = GroupPage.ADMIN }
            }
            "cancel", "delete" -> {
                val room = d.rooms.single { it.id == pendingTarget }
                require(c.text(GroupFieldKey.ADMIN_CONFIRMATION) == room.code) { "Type the room code to confirm." }
                request("room", orderJson.encodeToString(AdminRoomMutation(room.id, pendingAction, room.revision, room.code))) { c.page = GroupPage.ADMIN }
            }
            "delete-restaurant" -> {
                val restaurant = d.restaurants.single { it.id == pendingTarget }
                require(c.text(GroupFieldKey.ADMIN_CONFIRMATION) == restaurant.name) { "Type the restaurant name to confirm deletion." }
                request("restaurant", orderJson.encodeToString(AdminRestaurantMutation(action = "delete", restaurantId = restaurant.id))) { c.page = GroupPage.ADMIN }
            }
            "approve", "reject" -> request("block-request", orderJson.encodeToString(AdminBlockDecision(pendingTarget, pendingAction))) { c.page = GroupPage.ADMIN }
        }
    }
    fun editRestaurant(id: String) {
        c.restaurantEditor(dashboard?.restaurants?.firstOrNull { it.id == id }?.let { RestaurantExport(exportId = it.id, restaurant = it) })
        editingRestaurant = true
    }
    fun saveRestaurant(restaurant: Restaurant) {
        request("restaurant", orderJson.encodeToString(AdminRestaurantMutation(restaurant = restaurant))) { editingRestaurant = false; c.formDrafts.finishRestaurant(c.draft); c.page = GroupPage.ADMIN }
    }
    fun back() { editingRestaurant = false; c.page = GroupPage.ADMIN }
    fun saveSettings() { request("settings", orderJson.encodeToString(AdminSettings(c.flag(GroupFieldKey.ADMIN_REGISTRATION), c.flag(GroupFieldKey.ADMIN_ROOMS), c.text(GroupFieldKey.ADMIN_MESSAGE)))) }
    fun cleanup(delete: Boolean) {
        val scope = c.text(GroupFieldKey.ADMIN_SCOPE); val days = c.text(GroupFieldKey.ADMIN_DAYS).toIntOrNull() ?: error("Enter the number of days.")
        if(delete) { require(preview?.scope == scope && preview?.olderThanDays == days) { "Preview this selection again." }; require(c.text(GroupFieldKey.ADMIN_CONFIRMATION) == "DELETE") }
        request(if(delete) "cleanup-delete" else "cleanup-preview", orderJson.encodeToString(AdminCleanupRequest(scope, days, if(delete) preview!!.previewToken else "", c.text(GroupFieldKey.ADMIN_CONFIRMATION))))
    }
    fun content(): GroupFlowContent {
        if(c.page == GroupPage.ADMIN_USER) return userContent()
        if(c.page == GroupPage.ADMIN_CONFIRM) return confirmContent()
        val d = dashboard ?: return GroupFlowContent(buttons = listOf(button("Refresh", "تحديث", GroupAction.OPEN_ADMIN)))
        val fields = mutableListOf<GroupField>(); val cards = mutableListOf<GroupCard>(); val buttons = mutableListOf<GroupButton>()
        val tabs = listOf("overview" to tr("Overview", "نظرة عامة"), "users" to tr("Users", "المستخدمون"), "restaurants" to tr("Restaurants", "المطاعم"), "rooms" to tr("Rooms", "الغرف"), "history" to tr("Order history", "سجل الطلبات"), "wallets" to tr("Wallets", "المحافظ"), "blocks" to tr("Block requests", "طلبات الحظر"), "cleanup" to tr("Cleanup", "تنظيف البيانات"), "settings" to tr("Settings", "الإعدادات"))
        cards += GroupCard("admin-tabs", tr("Administration", "الإدارة"), buttons = tabs.map { GroupButton(it.second, GroupAction.ADMIN_TAB, it.first, enabled = tab != it.first) })
        val search = c.text(GroupFieldKey.ADMIN_SEARCH)
        when(tab) {
            "overview" -> {
                cards += GroupCard("admin-overview", tr("Overview", "نظرة عامة"), "${d.users.size} ${tr("users", "مستخدمين")} · ${d.rooms.size} ${tr("rooms", "غرف")} · ${d.restaurants.size} ${tr("restaurants", "مطاعم")}")
                cards += d.activity.take(50).mapIndexed { i, event -> GroupCard("activity:$i", event.action, "${event.target}\n${event.actorId}") }
            }
            "users" -> {
                fields += field(GroupFieldKey.ADMIN_SEARCH, "Search users", "ابحث عن مستخدم")
                buttons += button("Create user", "إنشاء مستخدم", GroupAction.ADMIN_NEW_USER)
                cards += d.users.filter { search.isBlank() || it.name.contains(search, true) || it.phone.contains(search) }.map { person -> GroupCard("admin-user:${person.id}", person.name, person.phone,
                    if(person.removed) tr("Removed", "محذوف") else if(person.disabled) tr("Blocked", "محظور") else "",
                    if(person.removed) listOf(button("Restore", "استعادة", GroupAction.ADMIN_ACTION, "restore|${person.id}")) else listOf(button("Edit all details", "تعديل جميع البيانات", GroupAction.ADMIN_USER, person.id),
                        button("Block", "حظر", GroupAction.ADMIN_ACTION, "block|${person.id}"), button("Unblock", "إلغاء الحظر", GroupAction.ADMIN_ACTION, "unblock|${person.id}"), button("Remove user", "إزالة المستخدم", GroupAction.ADMIN_ACTION, "remove|${person.id}", destructive = true))) }
            }
            "restaurants" -> {
                buttons += button("Add restaurant", "إضافة مطعم", GroupAction.ADMIN_NEW_RESTAURANT)
                cards += d.restaurants.map { r -> GroupCard("admin-restaurant:${r.id}", r.localizedName(c.library.language), "${r.menu.items.size} ${tr("items", "أصناف")}", buttons = listOf(button("Edit menu and prices", "تعديل القائمة والأسعار", GroupAction.ADMIN_RESTAURANT, r.id), button("Delete restaurant", "حذف المطعم", GroupAction.ADMIN_ACTION, "delete-restaurant|${r.id}", destructive = true))) }
            }
            "rooms", "history", "wallets" -> cards += (if(tab == "history") d.archivedOrders else d.rooms).mapIndexed { index, r -> GroupCard("admin-room:$index", "${r.name} · #${r.orderNumber}",
                "${r.restaurant} · ${r.phase}\n${tr("Total", "الإجمالي")} ${Money.format(r.totalMinor, r.currency)} · ${tr("Outstanding", "المتبقي")} ${Money.format(r.outstandingMinor, r.currency)}" +
                    if(tab == "wallets") "\n" + r.wallets.joinToString("\n") { "${it.name}: ${Money.format(it.balanceMinor, r.currency)}" } else "",
                buttons = if(tab == "rooms") listOfNotNull(
                    if(r.phase in listOf("LOBBY", "PREPARING_SPIN", "SPINNING", "ACCEPTING", "COLLECTING", "REVIEW")) button("Cancel room", "إلغاء الغرفة", GroupAction.ADMIN_ACTION, "cancel|${r.id}", destructive = true) else null,
                    if(r.canDelete) button("Delete room", "حذف الغرفة", GroupAction.ADMIN_ACTION, "delete|${r.id}", destructive = true) else null) else emptyList()) }
            "blocks" -> cards += d.blockRequests.map { r -> GroupCard("block:${r.id}", "${r.userName} · ${r.roomName}", "${r.reason}\n${r.durationHours} ${tr("hours", "ساعة")}", r.status,
                if(r.status == "pending") listOf(button("Approve block", "الموافقة على الحظر", GroupAction.ADMIN_ACTION, "approve|${r.id}"), button("Reject", "رفض", GroupAction.ADMIN_ACTION, "reject|${r.id}")) else emptyList()) }
            "settings" -> {
                fields += field(GroupFieldKey.ADMIN_REGISTRATION, "Allow registration", "السماح بالتسجيل", toggle = true)
                fields += field(GroupFieldKey.ADMIN_ROOMS, "Allow new rooms", "السماح بغرف جديدة", toggle = true)
                fields += field(GroupFieldKey.ADMIN_MESSAGE, "Maintenance message", "رسالة الصيانة")
                buttons += button("Save settings", "حفظ الإعدادات", GroupAction.ADMIN_SAVE_SETTINGS, primary = true)
            }
            "cleanup" -> {
                fields += field(GroupFieldKey.ADMIN_SCOPE, "Data to clean", "البيانات المطلوب تنظيفها", choices = listOf(GroupChoice("closedRooms", tr("Closed rooms", "الغرف المغلقة")), GroupChoice("history", tr("Order history", "سجل الطلبات"))))
                fields += field(GroupFieldKey.ADMIN_DAYS, "Older than days", "أقدم من عدد أيام")
                buttons += button("Preview selection", "معاينة البيانات", GroupAction.ADMIN_PREVIEW_CLEANUP)
                preview?.let { p -> cards += GroupCard("cleanup-count", "${p.count} ${tr("records", "سجلات")}", p.targets.take(30).joinToString("\n") { "${it.name} · #${it.orderNumber}" })
                    if(p.count > 0) { fields += field(GroupFieldKey.ADMIN_CONFIRMATION, "Type DELETE to confirm", "اكتب DELETE للتأكيد"); buttons += button("Delete selected data", "حذف البيانات المحددة", GroupAction.ADMIN_DELETE_CLEANUP, destructive = true) } }
            }
        }
        buttons += button("Refresh", "تحديث", GroupAction.OPEN_ADMIN)
        return GroupFlowContent(fields, cards, buttons)
    }
    private fun userContent(): GroupFlowContent {
        val fields = mutableListOf(field(GroupFieldKey.ADMIN_NAME, "Name", "الاسم"), field(GroupFieldKey.ADMIN_PHONE, "Phone", "رقم الهاتف"), field(GroupFieldKey.ADMIN_LANGUAGE, "Language", "اللغة", choices = listOf(GroupChoice("en", "English"), GroupChoice("ar", "العربية"))))
        if(userId.isEmpty()) fields += listOf(field(GroupFieldKey.ADMIN_EMAIL, "Email", "البريد الإلكتروني"), field(GroupFieldKey.ADMIN_PASSWORD, "Initial password", "كلمة المرور الأولية", secret = true))
        else fields += listOf(field(GroupFieldKey.ADMIN_PHOTO, "Profile photo", "صورة الملف"), field(GroupFieldKey.ADMIN_DISCOVERABLE, "Allow invitations", "السماح بالدعوات", toggle = true),
            field(GroupFieldKey.ADMIN_METHOD, "Payment method", "طريقة الدفع", choices = listOf(GroupChoice("none", tr("None", "بدون")), GroupChoice("aani", "Aani"), GroupChoice("bank", tr("Bank", "بنك")))),
            field(GroupFieldKey.ADMIN_HOLDER, "Account holder", "صاحب الحساب"), field(GroupFieldKey.ADMIN_BANK, "Bank", "البنك"), field(GroupFieldKey.ADMIN_IDENTIFIER, "Payment phone or IBAN", "هاتف الدفع أو الآيبان"))
        return GroupFlowContent(fields, buttons = listOf(button("Save user", "حفظ المستخدم", GroupAction.ADMIN_SAVE_USER, primary = true)))
    }
    private fun confirmContent(): GroupFlowContent {
        val fields = mutableListOf<GroupField>(); val d = requireNotNull(dashboard)
        val name = d.users.firstOrNull { it.id == pendingTarget }?.name ?: d.rooms.firstOrNull { it.id == pendingTarget }?.let { "${it.name} · ${it.code}" } ?: d.restaurants.firstOrNull { it.id == pendingTarget }?.name.orEmpty()
        if(pendingAction in listOf("block", "unblock")) {
            fields += field(GroupFieldKey.ADMIN_SCOPE, "Block scope", "نطاق الحظر", choices = listOf(GroupChoice("", tr("All rooms", "جميع الغرف"))) + d.rooms.map { GroupChoice(it.id, it.name) })
            fields += field(GroupFieldKey.ADMIN_DURATION, "Duration in hours · 0 means indefinite", "المدة بالساعات · صفر يعني دائماً")
            fields += field(GroupFieldKey.ADMIN_REASON, "Reason", "السبب")
        }
        if(pendingAction in listOf("remove", "delete-restaurant", "cancel", "delete")) fields += field(GroupFieldKey.ADMIN_CONFIRMATION, if(pendingAction in listOf("cancel", "delete")) "Type the room code to confirm" else "Type the name to confirm", if(pendingAction in listOf("cancel", "delete")) "اكتب رمز الغرفة للتأكيد" else "اكتب الاسم للتأكيد")
        return GroupFlowContent(fields, listOf(GroupCard("admin-confirm", name, tr("Review this action before confirming: $pendingAction", "راجع الإجراء قبل التأكيد: $pendingAction"))), listOf(button("Confirm action", "تأكيد الإجراء", GroupAction.ADMIN_CONFIRM, primary = true, destructive = true)))
    }
}
