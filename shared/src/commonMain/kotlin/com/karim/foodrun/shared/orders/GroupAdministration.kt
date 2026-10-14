package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupAdministration(private val c: GroupController) {
    var allowed = false; private set
    var editingRestaurant = false; private set
    private var checkedIdentity = ""
    val verifiedAccess: Boolean get() = allowed && checkedIdentity == c.library.identityToken
    private var checking = false
    private var dashboard: AdminDashboard? = null
    private var preview: AdminCleanupPreview? = null
    private var tab = "overview"
    private var userId = ""
    private var pendingAction = ""
    private var pendingTarget = ""
    private fun tr(en: String, ar: String) = if(c.library.language.startsWith("ar")) GroupUiText.translate(en, c.library.language).takeIf { it != en } ?: ar else en
    private fun field(key: GroupFieldKey, en: String, ar: String, toggle: Boolean = false, secret: Boolean = false, choices: List<GroupChoice> = emptyList()) = GroupField(key, tr(en, ar), c.text(key), toggle = toggle, secret = secret, choices = choices)
    private fun button(en: String, ar: String, action: GroupAction, value: String = "", primary: Boolean = false, destructive: Boolean = false) = GroupButton(tr(en, ar), action, value, primary, destructive)
    fun clear() { allowed = false; checking = false; checkedIdentity = ""; dashboard = null; preview = null; editingRestaurant = false; tab = "overview" }
    fun checkAccess() {
        if(c.supportActive || c.library.identityToken.isBlank() || checkedIdentity == c.library.identityToken || checking) return
        checking = true
        request("access", silent = true)
    }
    private fun request(action: String, payload: String = "", silent: Boolean = false, done: () -> Unit = {}) {
        val hub = c.library.identityHub ?: return
        val identity = c.library.identityToken
        if(!silent) { c.busy = true; c.publish() }
        c.platform.adminRequest(hub, orderJson.encodeToString(NativeAdminRequest(identity, action, payload, emailDetails = true)), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                if(identity != c.library.identityToken || hub != c.library.identityHub) return
                checking = false
                if(!silent) c.busy = false
                try {
                    require(error.isEmpty()) { error }
                    val result = orderJson.decodeFromString<NativeAdminReply>(body)
                    if(action == "access") { checkedIdentity = identity; allowed = result.ok }
                    require(result.ok) { result.error }
                    result.support?.let { c.startSupport(it,hub); return }
                    allowed = true; result.dashboard?.let { dashboard = it }
                    if(action == "cleanup-preview") {
                        val selection = orderJson.decodeFromString<AdminCleanupRequest>(payload)
                        val candidate = requireNotNull(result.preview) { "Preview this selection again." }
                        require(candidate.scope == selection.scope && candidate.olderThanDays == selection.olderThanDays && candidate.previewToken.isNotBlank()) { "Preview this selection again." }
                        if(selection.fromDate.isNotEmpty()) require(candidate.fromDate == selection.fromDate && candidate.toDate == selection.toDate && candidate.timeZone == selection.timeZone) { "The server could not verify this date range. Refresh and preview again." }
                        preview = candidate
                    }
                    if(action == "cleanup-delete") preview = null
                    done()
                } catch(failure: Exception) { if(action.startsWith("cleanup-")) preview = null; if(!silent) c.error = failure.message.orEmpty() }
                c.publish()
            }
        })
    }
    fun support(id: String) { require(verifiedAccess); request("support-start",orderJson.encodeToString(AdminSupportRequest(id))) }
    fun open() { c.page = GroupPage.ADMIN; request("dashboard") }
    fun tab(value: String) {
        tab = if(value == "cleanup-date") "cleanup" else value; c.draft[GroupFieldKey.ADMIN_SEARCH] = ""
        if(value == "settings") dashboard?.settings?.let {
            c.draft[GroupFieldKey.ADMIN_REGISTRATION] = it.registrationsEnabled.toString()
            c.draft[GroupFieldKey.ADMIN_ROOMS] = it.roomCreationEnabled.toString()
            c.draft[GroupFieldKey.ADMIN_MESSAGE] = it.maintenanceMessage
        }
        if(tab == "cleanup") {
            c.draft[GroupFieldKey.ADMIN_SCOPE] = "closedRooms"; c.draft[GroupFieldKey.ADMIN_DAYS] = "30"; c.draft[GroupFieldKey.ADMIN_CONFIRMATION] = ""
            c.draft[GroupFieldKey.ADMIN_CLEANUP_FILTER] = if(value == "cleanup-date") "date" else "age"
            c.draft[GroupFieldKey.ADMIN_FROM_DATE] = ""; c.draft[GroupFieldKey.ADMIN_TO_DATE] = ""; c.draft[GroupFieldKey.ADMIN_TIME_ZONE] = "Asia/Dubai"
            preview = null
        }
    }
    fun cleanupFilterChanged(key: GroupFieldKey) {
        if(key in listOf(GroupFieldKey.ADMIN_SCOPE, GroupFieldKey.ADMIN_DAYS, GroupFieldKey.ADMIN_CLEANUP_FILTER, GroupFieldKey.ADMIN_FROM_DATE, GroupFieldKey.ADMIN_TO_DATE, GroupFieldKey.ADMIN_TIME_ZONE)) {
            preview = null; c.draft[GroupFieldKey.ADMIN_CONFIRMATION] = ""
        }
    }
    fun user(id: String) {
        userId = id
        val person = dashboard?.users?.firstOrNull { it.id == id }
        val profile = person?.profile ?: person?.let { FoodProfile(it.id, it.name, it.phone, language = it.language, discoverable = it.discoverable) }
        c.draft[GroupFieldKey.ADMIN_NAME] = profile?.name.orEmpty()
        c.draft[GroupFieldKey.ADMIN_PHONE] = profile?.phone.orEmpty()
        c.draft[GroupFieldKey.ADMIN_PHOTO] = profile?.photo.orEmpty()
        c.draft[GroupFieldKey.ADMIN_LANGUAGE] = profile?.language?.let { if(it == "ar") "ar-EG" else it } ?: "en"
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
            val person = requireNotNull(dashboard).users.single { it.id == userId }
            val profile = person.profile ?: FoodProfile(person.id, person.name, person.phone, language = person.language, discoverable = person.discoverable)
            val payment = when(c.text(GroupFieldKey.ADMIN_METHOD)) {
                "none" -> null
                else -> ReceivingAccount(profile.payment?.id ?: c.platform.uuid(), c.text(GroupFieldKey.ADMIN_HOLDER), c.text(GroupFieldKey.ADMIN_BANK), c.text(GroupFieldKey.ADMIN_IDENTIFIER), currency = profile.payment?.currency ?: "AED", version = profile.payment?.version ?: 1, method = if(c.text(GroupFieldKey.ADMIN_METHOD) == "aani") PaymentMethod.AANI else PaymentMethod.BANK).normalized().also { it.validate() }
            }
            AdminUserMutation(userId = userId, action = "profile", profile = profile.copy(name = c.text(GroupFieldKey.ADMIN_NAME), phone = c.text(GroupFieldKey.ADMIN_PHONE), photo = c.text(GroupFieldKey.ADMIN_PHOTO), language = c.text(GroupFieldKey.ADMIN_LANGUAGE), discoverable = c.flag(GroupFieldKey.ADMIN_DISCOVERABLE), payment = payment).normalized().also { it.validate() })
        }
        c.draft.remove(GroupFieldKey.ADMIN_PASSWORD)
        request("user", orderJson.encodeToString(change)) { c.page = GroupPage.ADMIN }
    }
    fun action(value: String) {
        val parts = value.split('|', limit = 3)
        pendingAction = parts[0]; pendingTarget = parts.getOrElse(1) { "" }
        c.draft[GroupFieldKey.ADMIN_DURATION] = "24"; c.draft[GroupFieldKey.ADMIN_REASON] = ""
        c.draft[GroupFieldKey.ADMIN_SCOPE] = parts.getOrElse(2) { "" }; c.draft[GroupFieldKey.ADMIN_CONFIRMATION] = ""
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
        val scope = c.text(GroupFieldKey.ADMIN_SCOPE)
        val dateRange = c.text(GroupFieldKey.ADMIN_CLEANUP_FILTER) == "date"
        val days = if(dateRange) 0 else c.text(GroupFieldKey.ADMIN_DAYS).toIntOrNull()?.takeIf { it in 0..3650 } ?: error("Choose an age between 0 and 3650 days.")
        val from = if(dateRange) c.text(GroupFieldKey.ADMIN_FROM_DATE) else ""
        val to = if(dateRange) c.text(GroupFieldKey.ADMIN_TO_DATE) else ""
        val zone = c.text(GroupFieldKey.ADMIN_TIME_ZONE).ifEmpty { "Asia/Dubai" }
        if(dateRange) {
            require(from.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && to.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Choose both a valid start date and an end date." }
            require(to >= from) { "The end date must be on or after the start date." }
        }
        if(delete) {
            require(c.text(GroupFieldKey.ADMIN_SEARCH).isBlank()) { "Clear search to review the full cleanup selection before deleting." }
            require(preview?.scope == scope && preview?.olderThanDays == days && preview?.fromDate == from && preview?.toDate == to && (!dateRange || preview?.timeZone == zone)) { "Preview this selection again." }
            require(c.text(GroupFieldKey.ADMIN_CONFIRMATION) == "DELETE") { "Type DELETE to confirm this cleanup." }
        }
        request(if(delete) "cleanup-delete" else "cleanup-preview", orderJson.encodeToString(AdminCleanupRequest(scope, days, if(delete) preview!!.previewToken else "", c.text(GroupFieldKey.ADMIN_CONFIRMATION), from, to, zone)))
    }
    fun content(): GroupFlowContent {
        if(c.page == GroupPage.ADMIN_USER) return userContent()
        if(c.page == GroupPage.ADMIN_CONFIRM) return confirmContent()
        val d = dashboard ?: return GroupFlowContent(buttons = listOf(button("Refresh", "تحديث", GroupAction.OPEN_ADMIN)))
        val fields = mutableListOf<GroupField>(); val cards = mutableListOf<GroupCard>(); val buttons = mutableListOf<GroupButton>()
        val tabs = listOf("overview" to tr("Overview", "نظرة عامة"), "users" to tr("Users", "المستخدمون"), "restaurants" to tr("Restaurants", "المطاعم"), "rooms" to tr("Rooms", "الغرف"), "history" to tr("Order history", "سجل الطلبات"), "wallets" to tr("Wallets", "المحافظ"), "blocks" to tr("Block requests", "طلبات الحظر"), "cleanup" to tr("Cleanup", "تنظيف البيانات"), "settings" to tr("Settings", "الإعدادات"))
        cards += GroupCard("admin-tabs", tr("Admin panel", "لوحة الإدارة"), buttons = tabs.map { GroupButton(it.second, GroupAction.ADMIN_TAB, it.first, primary = tab == it.first, enabled = tab != it.first) })
        if(tab == "rooms") cards += GroupCard("admin-clear-rooms", tr("Clear rooms by date", "مسح الغرف حسب التاريخ"), buttons = listOf(button("Clear rooms by date", "مسح الغرف حسب التاريخ", GroupAction.ADMIN_TAB, "cleanup-date")))
        val search = c.text(GroupFieldKey.ADMIN_SEARCH).trim()
        fields += field(GroupFieldKey.ADMIN_SEARCH, "Search this tab", "ابحث في هذا القسم")
        when(tab) {
            "overview" -> {
                cards += listOf(
                    GroupCard("admin-metric:users", tr("Registered users", "المستخدمون المسجلون"), d.users.size.toString()),
                    GroupCard("admin-metric:rooms", tr("Rooms", "الغرف"), d.rooms.size.toString()),
                    GroupCard("admin-metric:orders", tr("Saved orders", "الطلبات المحفوظة"), d.archivedOrders.size.toString()),
                    GroupCard("admin-metric:restaurants", tr("Restaurants", "المطاعم"), d.restaurants.size.toString()),
                )
                cards += d.rooms.groupBy { it.currency }.map { (currency, rooms) -> GroupCard("admin-metric:value:$currency", tr("Current order value", "قيمة الطلبات الحالية"), Money.format(rooms.sumOf { it.totalMinor }, currency)) }
                cards += d.activity.take(50).mapIndexed { i, event -> GroupCard("activity:$i", event.action, "${event.target}\n${event.actorId}") }
            }
            "users" -> {
                buttons += button("Create user", "إنشاء مستخدم", GroupAction.ADMIN_NEW_USER)
                cards += d.users.filter { search.isBlank() || it.name.contains(search, true) || it.phone.contains(search) || it.email.contains(search,true) || it.id.contains(search,true) }.map { person ->
                    val self = person.id == c.library.home?.profile?.userId
                    val actions = mutableListOf<GroupButton>()
                    if(!person.removed) actions += button("Edit user details", "تعديل بيانات المستخدم", GroupAction.ADMIN_USER, person.id)
                    if(!self) {
                        if(person.removed) actions += button("Restore access", "استعادة الدخول", GroupAction.ADMIN_ACTION, "restore|${person.id}")
                        else {
                            if(!person.disabled) actions += button("Sign in as this user", "الدخول بحساب هذا المستخدم", GroupAction.ADMIN_SUPPORT_START, person.id)
                            actions += if(person.disabled) button("Unblock user", "إلغاء حظر المستخدم", GroupAction.ADMIN_ACTION, "unblock|${person.id}") else button("Block user", "حظر المستخدم", GroupAction.ADMIN_ACTION, "block|${person.id}")
                            person.roomBlocks.forEach { (roomId, _) ->
                                val name = d.rooms.firstOrNull { it.id == roomId }?.name ?: roomId
                                actions += button("Unblock in $name", "إلغاء الحظر في $name", GroupAction.ADMIN_ACTION, "unblock|${person.id}|$roomId")
                            }
                            actions += button("Remove user", "إزالة المستخدم", GroupAction.ADMIN_ACTION, "remove|${person.id}", destructive = true)
                        }
                    }
                    val blocks = person.roomBlocks.map { (roomId, block) -> "${d.rooms.firstOrNull { it.id == roomId }?.name ?: roomId}: ${block.reason}" }
                    GroupCard("admin-user:${person.id}", person.name, (listOf(person.phone, person.email, person.blockReason) + blocks).filter { it.isNotBlank() }.joinToString("\n"),
                        if(person.removed) tr("Removed", "محذوف") else if(person.disabled) tr("Blocked", "محظور") else tr("Active", "نشط"), actions)
                }
            }
            "restaurants" -> {
                buttons += button("Add restaurant", "إضافة مطعم", GroupAction.ADMIN_NEW_RESTAURANT)
                cards += d.restaurants.map { r -> GroupCard("admin-restaurant:${r.id}", r.localizedName(c.library.language), "${r.menu.items.size} ${tr("items", "أصناف")}", buttons = listOf(button("Edit menu and prices", "تعديل القائمة والأسعار", GroupAction.ADMIN_RESTAURANT, r.id), button("Delete restaurant", "حذف المطعم", GroupAction.ADMIN_ACTION, "delete-restaurant|${r.id}", destructive = true))) }
            }
            "rooms", "history", "wallets" -> cards += (if(tab == "history") d.archivedOrders else d.rooms).mapIndexed { index, r -> GroupCard("admin-room:$index", "${r.name} · #${r.orderNumber}",
                "${r.restaurant} · ${r.phase}\n${tr("Room code", "رمز الغرفة")}: ${r.code}\n${tr("Members", "الأعضاء")}: ${r.members}" +
                    "\n${tr("Ordering person", "مسؤول الطلب")}: ${r.payer.ifBlank { tr("Not selected", "لم يتم الاختيار") }}" +
                    "\n${tr("Total", "الإجمالي")} ${Money.format(r.totalMinor, r.currency)} · ${tr("Confirmed", "المؤكد")} ${Money.format(r.confirmedPaidMinor, r.currency)} · ${tr("Outstanding", "المتبقي")} ${Money.format(r.outstandingMinor, r.currency)}" +
                    if(tab == "wallets") "\n" + r.wallets.joinToString("\n") { "${it.name}: ${tr("Order", "الطلب")} ${Money.format(it.totalMinor, r.currency)} · ${tr("Paid", "المدفوع")} ${Money.format(it.paidMinor, r.currency)} · ${if(it.balanceMinor < 0) tr("Refund", "مبلغ مرتجع") else tr("Due", "المستحق")} ${Money.format(kotlin.math.abs(it.balanceMinor), r.currency)}" } else "",
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
                fields += field(GroupFieldKey.ADMIN_CLEANUP_FILTER, "Time filter", "فلتر الوقت", choices = listOf(GroupChoice("age", tr("Older than a number of days", "أقدم من عدد أيام")), GroupChoice("date", tr("Date range", "فترة زمنية"))))
                if(c.text(GroupFieldKey.ADMIN_CLEANUP_FILTER) == "date") {
                    fields += field(GroupFieldKey.ADMIN_FROM_DATE, "Start date · YYYY-MM-DD", "تاريخ البداية · YYYY-MM-DD")
                    fields += field(GroupFieldKey.ADMIN_TO_DATE, "End date · YYYY-MM-DD", "تاريخ النهاية · YYYY-MM-DD")
                    fields += field(GroupFieldKey.ADMIN_TIME_ZONE, "Time zone", "المنطقة الزمنية")
                } else fields += field(GroupFieldKey.ADMIN_DAYS, "Older than days", "أقدم من عدد أيام")
                cards += GroupCard("cleanup-help", tr("Review rooms before clearing", "راجع الغرف قبل المسح"), tr("The range uses last activity and includes both dates. Active orders and unsettled payments are protected.", "الفترة حسب آخر نشاط وتشمل يوم البداية والنهاية. الطلبات النشطة والمدفوعات غير المسددة محمية."))
                buttons += button("Preview selection", "معاينة البيانات", GroupAction.ADMIN_PREVIEW_CLEANUP)
                preview?.let { p -> cards += GroupCard("cleanup-count", "${p.count} ${tr("records", "سجلات")}")
                    cards += p.targets.filter { search.isBlank() || it.name.contains(search,true) || it.restaurant.contains(search,true) }.mapIndexed { index, target -> GroupCard("cleanup-target:$index", "${target.name} · #${target.orderNumber}", target.restaurant) }
                    if(p.count > 0) { fields += field(GroupFieldKey.ADMIN_CONFIRMATION, "Type DELETE to confirm", "اكتب DELETE للتأكيد"); buttons += button("Delete selected data", "حذف البيانات المحددة", GroupAction.ADMIN_DELETE_CLEANUP, destructive = true).copy(enabled = c.text(GroupFieldKey.ADMIN_CONFIRMATION) == "DELETE" && search.isBlank()) } }
            }
        }
        buttons += button("Refresh", "تحديث", GroupAction.OPEN_ADMIN)
        val searchableFields = fields.filter { it.key == GroupFieldKey.ADMIN_SEARCH || tab != "settings" || search.isBlank() || it.label.contains(search,true) || it.value.contains(search,true) }
        val searchableCards = if(search.isBlank() || tab in listOf("users","cleanup")) cards else cards.filter { it.id == "admin-tabs" || it.title.contains(search,true) || it.detail.contains(search,true) || it.badge.contains(search,true) }
        return GroupFlowContent(searchableFields, searchableCards, buttons)
    }
    private fun userContent(): GroupFlowContent {
        val fields = mutableListOf(field(GroupFieldKey.ADMIN_NAME, "Name", "الاسم"), field(GroupFieldKey.ADMIN_PHONE, "Phone", "رقم الهاتف"), field(GroupFieldKey.ADMIN_LANGUAGE, "Language", "اللغة", choices = listOf(GroupChoice("en", "English"), GroupChoice("ar-EG", "العربية المصرية"), GroupChoice("ar-AE", "العربية الإماراتية"), GroupChoice("ar-SD", "العربية السودانية"), GroupChoice("ar-001", "العربية الفصحى"))))
        if(userId.isEmpty()) fields += listOf(field(GroupFieldKey.ADMIN_EMAIL, "Email", "البريد الإلكتروني"), field(GroupFieldKey.ADMIN_PASSWORD, "Initial password", "كلمة المرور الأولية", secret = true))
        else fields += listOf(field(GroupFieldKey.ADMIN_PHOTO, "Profile photo", "صورة الملف"), field(GroupFieldKey.ADMIN_DISCOVERABLE, "Allow invitations", "السماح بالدعوات", toggle = true),
            field(GroupFieldKey.ADMIN_METHOD, "Payment method", "طريقة الدفع", choices = listOf(GroupChoice("none", tr("None", "بدون")), GroupChoice("aani", "Aani"), GroupChoice("bank", tr("Bank", "بنك")))),
            field(GroupFieldKey.ADMIN_HOLDER, "Account holder", "صاحب الحساب"), field(GroupFieldKey.ADMIN_BANK, "Bank", "البنك"), field(GroupFieldKey.ADMIN_IDENTIFIER, "Payment phone or IBAN", "هاتف الدفع أو الآيبان"))
        return GroupFlowContent(fields, buttons = listOf(button("Save user", "حفظ المستخدم", GroupAction.ADMIN_SAVE_USER, primary = true)))
    }
    private fun confirmContent(): GroupFlowContent {
        val fields = mutableListOf<GroupField>(); val d = requireNotNull(dashboard)
        val targetUser = d.users.firstOrNull { it.id == pendingTarget }
        val targetRoom = d.rooms.firstOrNull { it.id == pendingTarget }
        val targetRestaurant = d.restaurants.firstOrNull { it.id == pendingTarget }
        val name = targetUser?.name ?: targetRoom?.let { "${it.name} · ${it.code}" } ?: targetRestaurant?.name
            ?: d.blockRequests.firstOrNull { it.id == pendingTarget }?.let { "${it.userName} · ${it.roomName}" }.orEmpty()
        if(pendingAction in listOf("block", "unblock")) {
            fields += field(GroupFieldKey.ADMIN_SCOPE, "Block scope", "نطاق الحظر", choices = listOf(GroupChoice("", tr("All rooms", "جميع الغرف"))) + d.rooms.map { GroupChoice(it.id, it.name) })
            if(pendingAction == "block") {
                fields += field(GroupFieldKey.ADMIN_DURATION, "Duration in hours · 0 means indefinite", "المدة بالساعات · صفر يعني دائماً")
                fields += field(GroupFieldKey.ADMIN_REASON, "Reason", "السبب")
            }
        }
        if(pendingAction in listOf("remove", "delete-restaurant", "cancel", "delete")) fields += field(GroupFieldKey.ADMIN_CONFIRMATION, if(pendingAction in listOf("cancel", "delete")) "Type the room code to confirm" else "Type the name to confirm", if(pendingAction in listOf("cancel", "delete")) "اكتب رمز الغرفة للتأكيد" else "اكتب الاسم للتأكيد")
        val description = when(pendingAction) {
            "block" -> tr("Block this user's room access for the selected duration and scope.", "حظر دخول هذا المستخدم للغرف حسب المدة والنطاق المحددين.")
            "unblock" -> tr("Restore this user's access in the selected scope.", "استعادة دخول هذا المستخدم في النطاق المحدد.")
            "remove" -> tr("Remove this user from FoodRun after their active orders and wallet payments are settled.", "إزالة هذا المستخدم من FoodRun بعد إنهاء طلباته وتسوية مدفوعات المحفظة.")
            "restore" -> tr("Restore this user's FoodRun access.", "استعادة دخول هذا المستخدم إلى FoodRun.")
            "cancel" -> tr("Cancel this room's current order for all members.", "إلغاء الطلب الحالي في هذه الغرفة لكل الأعضاء.")
            "delete" -> tr("Permanently delete this room and its order history. The server checks that all payments are settled.", "حذف هذه الغرفة وسجل طلباتها نهائياً. يتحقق الخادم من تسوية جميع المدفوعات.")
            "delete-restaurant" -> tr("Delete this restaurant from the shared catalog.", "حذف هذا المطعم من القائمة المشتركة.")
            "approve" -> tr("Approve this room owner's block request.", "الموافقة على طلب الحظر المقدم من منشئ الغرفة.")
            "reject" -> tr("Reject this block request.", "رفض طلب الحظر.")
            else -> error("Action unavailable.")
        }
        val confirmation = c.text(GroupFieldKey.ADMIN_CONFIRMATION)
        val ready = when(pendingAction) {
            "remove" -> targetUser != null && confirmation == targetUser.name
            "cancel", "delete" -> targetRoom != null && confirmation == targetRoom.code
            "delete-restaurant" -> targetRestaurant != null && confirmation == targetRestaurant.name
            "block" -> c.text(GroupFieldKey.ADMIN_DURATION).toIntOrNull()?.let { it in 0..8760 } == true && c.text(GroupFieldKey.ADMIN_REASON).trim().length in 5..300
            else -> true
        }
        return GroupFlowContent(fields, listOf(GroupCard("admin-confirm", name, description)), listOf(button("Confirm action", "تأكيد الإجراء", GroupAction.ADMIN_CONFIRM, primary = true, destructive = pendingAction in listOf("block", "remove", "cancel", "delete", "delete-restaurant")).copy(enabled = ready)))
    }
}
