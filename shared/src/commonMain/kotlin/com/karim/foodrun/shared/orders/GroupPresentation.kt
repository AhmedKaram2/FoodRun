package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

private fun localizedReceiptDescription(room: Room, line: ReceiptLine, language: String): String {
    if(language != "ar" || line.itemId.isBlank()) return line.description
    val item = room.restaurant.menu.items.firstOrNull { it.id == line.itemId } ?: return line.description
    val variant = item.variants.firstOrNull { it.id == line.variantId }
    val options = room.restaurant.menu.optionGroups.flatMap { it.options }.filter { it.id in line.optionIds }
    return (listOf(item.localizedName(language)) + listOfNotNull(variant?.localizedName(language)) + options.map { it.localizedName(language) }).joinToString(" · ")
}

internal fun restaurantReadyText(room: Room, receipts: List<Receipt>, language: String = "en"): String {
    val combined = linkedMapOf<Pair<String, String>, Int>()
    receipts.flatMap { it.lines }.forEach { line ->
        val key = localizedReceiptDescription(room, line, language) to line.notes
        combined[key] = (combined[key] ?: 0) + line.quantity
    }
    val lines = combined.map { (key, quantity) ->
        val (description, notes) = key
        val rawQuantity = quantity.toString()
        val shownQuantity = if(description.any { it in '\u0600'..'\u06ff' }) {
            val arabic = "٠١٢٣٤٥٦٧٨٩"
            rawQuantity.map { if(it in '0'..'9') arabic[it - '0'] else it }.joinToString("")
        } else rawQuantity
        "$shownQuantity $description${if(notes.isNotBlank()) " — $notes" else ""}"
    }
    return (listOf(room.restaurant.localizedName(language), if(room.deliveryMode) (if(language == "ar") "توصيل: ${room.destination}" else "Delivery: ${room.destination}") else if(language == "ar") "استلام من المطعم" else "Pickup", "") + lines).joinToString("\n")
}

internal class GroupPresentation(private val c: GroupController) {
    private val fields = mutableListOf<GroupField>()
    private val cards = mutableListOf<GroupCard>()
    private val buttons = mutableListOf<GroupButton>()
    private var title = "Food Run"
    private var subtitle = "Good food. Great company."
    private val language get() = c.library.language
    private val ar get() = language == "ar"
    private fun tr(en: String, ar: String) = if(this.ar) ar else en
    private fun field(key: GroupFieldKey, label: String, multiline: Boolean = false, toggle: Boolean = false) { fields += GroupField(key, label, c.text(key), multiline, toggle, secret = key == GroupFieldKey.PASSWORD) }
    private fun button(title: String, action: GroupAction, value: String = "", primary: Boolean = false, enabled: Boolean = true) { buttons += GroupButton(title, action, value, primary, enabled = enabled) }
    private fun card(id: String, title: String, detail: String = "", badge: String = "", actions: List<GroupButton> = emptyList()) { cards += GroupCard(id, title, detail, badge, actions) }
    private fun append(content: GroupFlowContent) { fields += content.fields; cards += content.cards; buttons += content.buttons }
    fun render(): GroupState {
        when(c.page) {
            GroupPage.HOME -> home()
            GroupPage.PROFILE -> profile()
            GroupPage.PEOPLE -> people()
            GroupPage.CUSTOM_ITEM -> { title = if(c.editingCartLineId == null) "What would you like?" else "Change your item"; subtitle = "Add one item at a time. The selected person adds its price later."; field(GroupFieldKey.CUSTOM_NAME, tr("Food item", "الصنف")); field(GroupFieldKey.QUANTITY, tr("Quantity", "الكمية")); field(GroupFieldKey.NOTE, tr("Notes / extras", "ملاحظات وإضافات")); button(if(c.editingCartLineId == null) "Add to my order" else "Update my order", GroupAction.ADD_CUSTOM_ITEM, primary = true) }
            GroupPage.PRICE_ITEM -> { title = "Price this item"; subtitle = "Enter the price for one item; quantity is applied automatically."; field(GroupFieldKey.AMOUNT, "Unit price · ${c.room().restaurant.currency}"); button("Save price", GroupAction.SAVE_ITEM_PRICE, primary = true) }
            GroupPage.QUICK_SPIN -> Unit
            GroupPage.CONNECT -> connect()
            GroupPage.SETUP -> setup()
            GroupPage.LIBRARY -> library()
            GroupPage.RESTAURANT -> restaurant()
            GroupPage.ROOM -> room()
            GroupPage.ITEM -> item()
            GroupPage.ACCOUNT -> accounts()
            GroupPage.RECEIPTS -> receipts()
            GroupPage.HISTORY -> history()
        }
        val room = c.reply?.room
        val spin = room?.spin?.takeIf { c.page == GroupPage.ROOM && room.phase in listOf(RoomPhase.SPINNING, RoomPhase.ACCEPTING) }
        val wheel = spin?.let { GroupWheel(it.memberIds.map { id -> room.members.single { m -> m.id == id }.name }, it, c.serverOffset(), room.members.single { m -> m.id == it.winnerId }.name) }
        if (c.library.pending != null && !c.busy) buttons.add(0, GroupButton(tr("Retry saved request", "إعادة إرسال الطلب المحفوظ"), GroupAction.RETRY, primary = true))
        return GroupState(c.page, title, subtitle, fields, cards, buttons, c.busy, c.online,
            if (c.session == null) "Nearby or internet live rooms · Firebase account backup"
            else if(c.online && c.session?.hub?.fingerprint.isNullOrEmpty()) "Live over the internet · saved on this device"
            else if(c.online) "Live on the local network · saved on this device"
            else "Offline · saved receipt data · last sync ${c.reply?.serverTime?.let(::timeLabel) ?: "unavailable"}",
            c.error, wheel, if (c.page == GroupPage.ROOM) room?.code ?: "" else "", c.page != GroupPage.HOME,
            if (c.page != GroupPage.ROOM) -1 else when (room?.phase) {
                RoomPhase.LOBBY -> if(room.restaurantPollOpen) 1 else 2
                RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING, RoomPhase.ACCEPTING -> 3
                RoomPhase.COLLECTING, RoomPhase.REVIEW -> 4
                RoomPhase.PLACED, RoomPhase.FULFILLED, RoomPhase.ARCHIVED -> 5
                else -> -1
            }, if(c.page == GroupPage.ROOM && room != null && c.session != null) c.roomInviteLink(c.session!!.hub, room.code) else "", ar)
    }
    private fun home() {
        title = tr("Food Run", "فود رن"); subtitle = tr("Gather your people. Share a meal.", "اجمع أصحابك وشاركوا وجبتكم.")
        button(tr("Create a room", "إنشاء غرفة"), GroupAction.CREATE, primary = true); button(tr("Join a room", "الانضمام إلى غرفة"), GroupAction.JOIN)
        button(if(c.library.home == null) tr("Register / sign in", "تسجيل أو دخول") else tr("My profile", "ملفي الشخصي"), GroupAction.OPEN_PROFILE); button(tr("Enable notifications", "تفعيل الإشعارات"), GroupAction.ENABLE_ALERTS)
        button(tr("Quick Spin", "اختيار سريع"), GroupAction.QUICK_SPIN); button(tr("Restaurant library", "المطاعم والقوائم"), GroupAction.OPEN_LIBRARY)
        button("English", GroupAction.SET_LANGUAGE, "en", enabled = ar); button("العربية", GroupAction.SET_LANGUAGE, "ar", enabled = !ar)
        card("about", tr("Your table, always here", "مجموعتك دائماً هنا"), tr("Join a room once. Return for tomorrow's order. Downloaded receipts stay with you offline.", "انضم مرة واحدة وعد للطلبات القادمة. تبقى الإيصالات المحفوظة متاحة دون إنترنت."))
        c.library.home?.invitations?.forEach { invite -> card("invitation:${invite.id}", "Join ${invite.roomName}", "${invite.invitedBy} invited you to order #${invite.orderNumber}", "Invitation", listOf(GroupButton("Join", GroupAction.ACCEPT_INVITE, invite.id, primary = true))) }
        c.library.sessions.forEach { s ->
            val snapshot = c.library.snapshots[s.roomId]; val room = snapshot?.room
            val winner = room?.spin?.takeIf { room.phase !in listOf(RoomPhase.LOBBY, RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING) }?.winnerId
            val person = room?.members?.singleOrNull { it.id == (room.payerId ?: winner) }
            val receipt = snapshot?.receipts?.singleOrNull { it.memberId == s.memberId }
            val detail = listOfNotNull(room?.let { "Order #${it.orderNumber} · ${stage(it.phase)}" }, person?.let { "${it.name} is selected to order" }, receipt?.takeIf { room?.phase in listOf(RoomPhase.REVIEW, RoomPhase.PLACED, RoomPhase.FULFILLED) }?.let { "Your total ${it.totalText} · To pay ${it.balanceText}" }).joinToString("\n").ifEmpty { "Saved room · tap to reconnect" }
            val selectedMe = room?.phase == RoomPhase.ACCEPTING && winner == s.memberId
            card("session:${s.roomId}", s.roomName, detail, if(selectedMe) "You're selected!" else "", listOf(GroupButton(if(selectedMe) "Join & accept" else if(room?.phase == RoomPhase.LOBBY) "Join this order" else "Open room", GroupAction.RESUME, s.roomId)))
        }
        dashboardCards("dashboard:")
    }
    private fun profile() {
        title = if(c.library.home == null) tr("Your Food Run account", "حساب فود رن") else tr("Your profile", "ملفك الشخصي")
        subtitle = tr("Use your existing Intrvioo account, or register here.", "استخدم حساب إنترفيوو أو سجل من هنا.")
        button("English", GroupAction.SET_LANGUAGE, "en", enabled = ar); button("العربية", GroupAction.SET_LANGUAGE, "ar", enabled = !ar)
        if(c.library.home == null) {
            field(GroupFieldKey.EMAIL, tr("Email", "البريد الإلكتروني")); field(GroupFieldKey.PASSWORD, tr("Password", "كلمة المرور"))
            button(tr("Sign in", "تسجيل الدخول"), GroupAction.SIGN_IN, primary = true); button(tr("Reset password", "إعادة تعيين كلمة المرور"), GroupAction.RESET_PASSWORD)
        }
        field(GroupFieldKey.NAME, tr("Profile name", "الاسم في الملف الشخصي")); field(GroupFieldKey.PROFILE_PHONE, tr("Phone · with country code", "رقم الهاتف الإماراتي"))
        field(GroupFieldKey.PHOTO, tr("Profile photo", "صورة الملف الشخصي"))
        field(GroupFieldKey.DISCOVERABLE, tr("Allow people on this hub to invite me", "السماح لمستخدمي الخادم بدعوتي"), toggle = true)
        field(GroupFieldKey.AANI, tr("Receive payments with Aani", "استلام الدفعات عبر آني"), toggle = true)
        field(GroupFieldKey.ACCOUNT_HOLDER, tr("Account holder · optional", "صاحب الحساب · اختياري"))
        if(!c.flag(GroupFieldKey.AANI)) field(GroupFieldKey.ACCOUNT_BANK, tr("Bank name · optional", "اسم البنك · اختياري"))
        field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) tr("Aani phone number", "رقم الهاتف المسجل في آني") else tr("IBAN / account number · optional", "رقم آيبان أو الحساب · اختياري"))
        if(c.library.home == null) button(tr("Create account", "إنشاء حساب"), GroupAction.REGISTER)
        else {
            button(tr("Save profile", "حفظ الملف الشخصي"), GroupAction.SAVE_PROFILE, primary = true)
            dashboardCards("profile-dashboard:")
            val favorites = c.library.home!!.profile.favoriteOrders
            favorites.forEach { favorite ->
                card("favorite:${favorite.id}", favorite.restaurantName, favoriteSummary(favorite), tr("Favorite order", "طلب مفضل"), listOf(
                    GroupButton(tr("Remove favorite", "إزالة من المفضلة"), GroupAction.REMOVE_FAVORITE_ORDER, favorite.id, destructive = true),
                ))
            }
            if (favorites.isEmpty()) card("favorite-empty", tr("No favorite orders yet", "لا توجد طلبات مفضلة بعد"), tr("Save a unique previous order below, then add it to future rooms in one tap.", "احفظ طلباً سابقاً مختلفاً ثم أضفه إلى الغرف القادمة بضغطة واحدة."))
            val favoriteKeys = favorites.map { it.selectionKey() }.toSet()
            c.previousOrderChoices().take(c.previousOrderLimit).forEach { choice ->
                val key = choice.receipt.orderSelectionKey(choice.restaurantId.ifBlank { "name:${choice.order.restaurantName.lowercase()}" })
                card("previous:${choice.value}", choice.order.restaurantName, receiptSummary(choice.receipt),
                    if (choice.repeatCount > 1) tr("Repeated ${choice.repeatCount} times", "تكرر ${choice.repeatCount} مرات") else timeLabel(choice.order.completedAt),
                    if (key in favoriteKeys) emptyList() else listOf(GroupButton(tr("Save as favorite", "حفظ كمفضل"), GroupAction.FAVORITE_ORDER, choice.value)))
            }
            if(c.previousOrderChoices().size > c.previousOrderLimit) button(tr("Show more unique orders", "عرض طلبات سابقة أخرى"), GroupAction.MORE_PREVIOUS_ORDERS)
            card("cloud", "Firebase storage", c.library.home!!.cloudStatus)
            button("Connect hub storage to my Firebase account", GroupAction.ENABLE_CLOUD)
            button(tr("Sign out", "تسجيل الخروج"), GroupAction.SIGN_OUT)
        }
        card("profile-privacy", tr("Your receiving details", "بيانات استلام أموالك"), tr("Only you can read your cloud profile. Your receiving account is shared with participants when you are selected and choose to share it.", "ملفك السحابي خاص بك. تُشارك بيانات استلام الأموال عندما يتم اختيارك وتوافق على مشاركتها."))
    }
    private fun people() {
        title = tr("Invite your people", "ادعُ أصحابك"); subtitle = tr("People who signed in on this hub and enabled invitations.", "الأشخاص المسجلون في هذا الخادم والذين سمحوا بالدعوات.")
        c.library.home?.people?.forEach { person -> card("person:${person.userId}", person.name, actions = listOf(GroupButton("Invite", GroupAction.INVITE_PERSON, person.userId))) }
        if(c.library.home?.people.isNullOrEmpty()) card("empty", tr("Bring the crew", "اجمع أصحابك"), tr("Ask people to sign in on this hub. You can also share the room code.", "اطلب من أصحابك تسجيل الدخول في هذا الخادم أو شارك رمز الغرفة."))
    }
    private fun connect() {
        title = "Connect to your table"; subtitle = "Use the internet from anywhere, or keep live traffic on a nearby hub."
        button("Use internet room", GroupAction.USE_INTERNET, primary = true)
        field(GroupFieldKey.PAIRING_LINK, "Paste the server pairing link", multiline = true)
        field(GroupFieldKey.HUB_URL, "Local API address · https://192.168.1.20:8443")
        field(GroupFieldKey.FINGERPRINT, "Private hub certificate SHA-256 · optional for public HTTPS", multiline = true)
        button("Scan server QR", GroupAction.SCAN); button("Find nearby hub", GroupAction.DISCOVER)
        button("Use nearby hub", GroupAction.CONNECT)
        card("trust", "Smart hybrid mode", "Internet rooms work from any network. A nearby hub keeps live commands and updates on your local network to reduce server traffic.")
    }
    private fun setup() {
        title = if(c.joinMode && !c.nextOrder) "Join your people" else if(c.nextOrder) "A fresh order" else "Create your room"
        subtitle = "Your membership stays saved for future meals."
        if (!c.nextOrder) field(GroupFieldKey.NAME, tr("Your name", "اسمك"))
        if(c.joinMode && !c.nextOrder) {
            field(GroupFieldKey.ROOM_CODE, tr("Six-digit room code", "رمز الغرفة من ستة أرقام")); field(GroupFieldKey.GUEST, "Watch only — no food or payment", toggle = true)
            button("Request to join", GroupAction.JOIN_ROOM, primary = true); return
        }
        if (!c.nextOrder) field(GroupFieldKey.ROOM_NAME, tr("Room name", "اسم الغرفة"))
        field(GroupFieldKey.RESTAURANT_NAME, "Restaurant for an open order")
        button("Let everyone type their own items", GroupAction.USE_OPEN_ORDER)
        field(GroupFieldKey.RESTAURANT_POLL, "Let the room vote for the restaurant", toggle = true)
        card(
            "restaurant-choice-mode",
            if(c.flag(GroupFieldKey.RESTAURANT_POLL)) "Live restaurant poll" else "Restaurant chosen by organizer",
            if(c.flag(GroupFieldKey.RESTAURANT_POLL)) "Everyone votes in the room. Sandwich ordering opens after you finish the poll."
            else "The selected restaurant and its menu are available as soon as the room opens.",
        )
        field(GroupFieldKey.EXPECTED_NAMES, "Expected people today · comma separated")
        field(GroupFieldKey.DELIVERY, "Delivery to us", toggle = true)
        if(c.flag(GroupFieldKey.DELIVERY)) field(GroupFieldKey.DESTINATION, tr("Delivery address and contact", "عنوان التوصيل ووسيلة التواصل"))
        feeFields()
        card("restaurant", c.selectedRestaurant?.restaurant?.localizedName(language) ?: tr("Choose a restaurant", "اختر مطعماً"), c.selectedRestaurant?.restaurant?.menu?.items?.size?.let { tr("$it menu items", "$it صنفاً") } ?: tr("Create, import or select a saved menu.", "أنشئ أو استورد أو اختر قائمة محفوظة."))
        button("Choose restaurant", GroupAction.OPEN_LIBRARY)
        button(if(c.nextOrder) tr("Start next order", "بدء الطلب التالي") else "Create room", GroupAction.CREATE_ROOM, primary = true)
    }
    private fun library() {
        title = "Restaurant library"; subtitle = "Your favorites, saved on your device."
        button(tr("Add restaurant", "إضافة مطعم"), GroupAction.NEW_RESTAURANT, primary = c.library.restaurants.isEmpty() && c.importPreview == null); button("Import menu JSON", GroupAction.IMPORT_MENU)
        field(GroupFieldKey.JSON_MENU, "Or paste a menu JSON file", multiline = true)
        if(c.text(GroupFieldKey.JSON_MENU).isNotBlank()) button("Preview JSON", GroupAction.PREVIEW_IMPORT)
        c.importPreview?.let { card("preview", "Import ${it.restaurant.name}", "${it.restaurant.menu.items.size} items · ${it.restaurant.currency}\nMatching restaurant IDs update the saved copy. Room menus stay unchanged."); button("Confirm import", GroupAction.CONFIRM_IMPORT, primary = true) }
        c.library.restaurants.forEach { e -> card("restaurant:${e.restaurant.id}", e.restaurant.localizedName(language), "${if(ar && e.restaurant.branchNameAr.isNotBlank()) e.restaurant.branchNameAr else e.restaurant.branchName} · ${e.restaurant.menu.items.size} ${tr("items", "صنفاً")} · ${e.restaurant.currency}", actions = listOf(
            GroupButton(tr("Use for order", "استخدام للطلب"), GroupAction.SELECT_RESTAURANT, e.restaurant.id), GroupButton(tr("Edit", "تعديل"), GroupAction.EDIT_RESTAURANT, e.restaurant.id), GroupButton(tr("Share JSON", "مشاركة القائمة"), GroupAction.EXPORT_MENU, e.restaurant.id), GroupButton(tr("Delete saved copy", "حذف النسخة"), GroupAction.DELETE_RESTAURANT, e.restaurant.id, destructive = true))) }
        if (c.library.restaurants.isEmpty() && c.importPreview == null) card("empty", "Your next favorite starts here", "Add a restaurant and its menu, or import a menu shared by a friend.")
        c.reply?.room?.takeIf { (it.ownerId == c.me() || it.payerId == c.me()) && it.phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW) }?.let { room ->
            c.library.restaurants.firstOrNull { it.restaurant.id == room.restaurant.id }?.let { saved ->
                field(GroupFieldKey.REASON, "Reason for updating the room menu")
                button("Update current room menu", GroupAction.UPDATE_ROOM_MENU, saved.restaurant.id)
            }
        }
    }
    private fun restaurant() {
        val editingRoom = c.editingRoomOrder != null
        title = if(editingRoom) "Restaurant details" else "Build your menu"
        subtitle = if(editingRoom) "Contact changes keep this order's progress. Menu or pricing changes reopen food selection and require confirmation again." else "Enter menu prices and how tax is charged. Import JSON for sizes and extras."
        field(GroupFieldKey.RESTAURANT_NAME, tr("Restaurant name", "اسم المطعم")); field(GroupFieldKey.BRANCH, tr("Branch", "الفرع"))
        card("room-currency", tr("Currency", "العملة"), "AED · UAE Dirham")
        field(GroupFieldKey.PHONE, tr("Restaurant phone", "رقم المطعم"))
        field(GroupFieldKey.ADDRESS, "Restaurant address"); field(GroupFieldKey.DELIVERY_FEE, tr("Default delivery fee", "رسوم التوصيل الافتراضية"))
        field(GroupFieldKey.SERVICE_FEE, tr("Default service fee", "رسوم الخدمة الافتراضية"))
        field(GroupFieldKey.MINIMUM_ORDER, "Minimum food order")
        val tax = c.editingRestaurant?.restaurant?.pricing?.taxTreatment ?: TaxTreatment.INCLUDED
        card("tax-treatment", "How does the restaurant charge tax?", when(tax) {
            TaxTreatment.INCLUDED -> "Tax is included in menu prices."
            TaxTreatment.ADDED -> "Tax is added to menu prices at the rate below."
            TaxTreatment.UNSPECIFIED -> "Choose the restaurant's tax treatment before placing an order."
        }, actions = listOf(
            GroupButton("Tax included in prices", GroupAction.SELECT_TAX_TREATMENT, TaxTreatment.INCLUDED.name, enabled = tax != TaxTreatment.INCLUDED),
            GroupButton("Tax added to prices", GroupAction.SELECT_TAX_TREATMENT, TaxTreatment.ADDED.name, enabled = tax != TaxTreatment.ADDED),
        ))
        if(tax == TaxTreatment.ADDED) field(GroupFieldKey.TAX_RATE, "Tax rate · percent")
        field(GroupFieldKey.MENU_ITEM_NAME, "New item name")
        field(GroupFieldKey.MENU_ITEM_PRICE, "New item price"); button("Add item", GroupAction.ADD_MENU_ITEM)
        val previewCurrency = c.text(GroupFieldKey.CURRENCY).trim().uppercase().takeIf { it in Money.currencies } ?: c.editingRestaurant?.restaurant?.currency ?: "AED"
        c.editingRestaurant?.restaurant?.menu?.items?.forEach { i -> card("menu-editor:${i.id}", i.name, Money.format(i.basePriceMinor, previewCurrency), actions = listOf(GroupButton("Remove item", GroupAction.REMOVE_MENU_ITEM, i.id, destructive = true))) }
        button(if(editingRoom) "Save & update this order" else tr("Save restaurant", "حفظ المطعم"), GroupAction.SAVE_RESTAURANT, primary = true)
    }
    private fun room() {
        val r = c.reply?.room ?: run { title = "Connecting…"; button("Retry connection", GroupAction.REFRESH); return }
        title = r.name; subtitle = "${tr("Order", "الطلب")} #${r.orderNumber} · ${stage(r.phase)} · ${r.restaurant.localizedName(language)}"
        val me = r.members.singleOrNull { it.id == c.me() } ?: return
        val owner = c.me() == r.ownerId; val payer = c.me() == r.payerId
        if(!me.approved) {
            val detail = when {
                r.phase !in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING) -> "This meal is already in progress. Your join request stays saved for organizer approval when the next order opens."
                r.phase == RoomPhase.COLLECTING && !me.guest && !me.latePayerApproved -> "The selected payer and room organizer must approve your late food order."
                else -> "The room organizer will approve your join request."
            }
            card("pending", tr("Waiting for approval", "بانتظار الموافقة"), detail); button(tr("Refresh", "تحديث"), GroupAction.REFRESH); return
        }
        button("Invite people", GroupAction.SHARE_ROOM); if(owner && r.phase == RoomPhase.LOBBY && c.sameHub(c.library.identityHub, c.session?.hub)) button(tr("Invite registered people", "دعوة مستخدمين مسجلين"), GroupAction.OPEN_PEOPLE); button("Receipts", GroupAction.OPEN_RECEIPTS); button("Past orders", GroupAction.OPEN_HISTORY)
        button(tr("Save restaurant", "حفظ المطعم"), GroupAction.SAVE_ROOM_RESTAURANT)
        if((owner || payer) && r.phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW)) button(tr("Edit restaurant details", "تعديل بيانات المطعم"), GroupAction.EDIT_ROOM_RESTAURANT)
        if (!c.online) card("offline", "Your saved order is available", "Reconnect to the same local hub to make changes. Cached payment status may have changed.", actions = listOf(GroupButton("Reconnect", GroupAction.REFRESH)))
        r.members.filterNot { it.removed }.forEach { m ->
            val actions = mutableListOf<GroupButton>()
            if(owner && !m.approved && r.phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING) && (r.phase == RoomPhase.LOBBY || m.guest || payer || m.latePayerApproved)) actions += GroupButton("Approve join", GroupAction.APPROVE, m.id)
            if(payer && !m.approved && !m.guest && !m.latePayerApproved && r.phase == RoomPhase.COLLECTING) actions += GroupButton("Accept this late orderer", GroupAction.APPROVE_LATE_JOIN, m.id)
            if(owner && m.id != r.ownerId && r.phase == RoomPhase.LOBBY) { actions += GroupButton("Remove from room", GroupAction.REMOVE, m.id, destructive = true); if(m.approved && !m.guest) actions += GroupButton("Make organizer", GroupAction.HANDOVER, m.id) }
            card("member:${m.id}", m.name, listOf(if(m.id == r.ownerId) "Organizer" else if(m.id == r.payerId) "Payer" else if(m.guest) "Watching" else "Member", if(c.serverNow() - m.lastSeen < 15000) "Connected" else "Away").joinToString(" · "), if(!m.approved) "Pending" else if(!m.guest && !m.participating) "Skipping this order" else if(m.eligible) "Joined · Can be selected" else "Joined", actions)
        }
        if(owner) r.expectedNames.filter { name -> r.orderingMembers.none { it.name.equals(name.trim(), true) } }.forEach { card("invite:$it", it, "Expected · not participating today", actions = if(r.phase == RoomPhase.LOBBY) listOf(GroupButton("Remove invitation", GroupAction.REMOVE, "invite:$it")) else emptyList()) }
        field(GroupFieldKey.REASON, "Reason for removal, reroll, reopening or adjustment")
        when(r.phase) {
            RoomPhase.LOBBY -> {
                if(!me.guest) {
                    button(if(me.participating) tr("Skip this order", "تخطي هذا الطلب") else tr("Join this order", "الانضمام إلى هذا الطلب"), GroupAction.PARTICIPATE, (!me.participating).toString())
                    if(me.participating && !me.eligible && r.pastSpins.any { it.winnerId == me.id }) card("declined-duty", "You're still part of this meal", "You declined payment duty, so your name stays out of the next wheel while you can still add food.")
                }
                if(r.restaurantPollOpen) {
                    card("poll-heading", "Choose today's restaurant", "Registered members vote live. The organizer closes the poll, then everyone can add sandwiches before the spin.")
                    val myVote = r.restaurantVotes.firstOrNull { it.memberId == c.me() }?.restaurantId
                    val canVote = !me.guest && me.participating && c.library.identityToken.isNotEmpty()
                    r.restaurantOptions.forEach { option ->
                        val count = r.restaurantVotes.count { it.restaurantId == option.id }
                        val voters = r.restaurantVotes.filter { it.restaurantId == option.id }.mapNotNull { vote -> r.members.firstOrNull { it.id == vote.memberId }?.name }
                        card("poll:${option.id}", option.localizedName(language), "${option.menu.items.size} ${tr("sandwiches/items", "سندويشات وأصناف")}${if(voters.isEmpty()) tr(" · No votes yet", " · لا أصوات بعد") else " · ${voters.joinToString()}"}",
                            tr("$count ${if(count == 1) "vote" else "votes"}", "$count أصوات"), if(canVote) listOf(GroupButton(if(myVote == option.id) tr("Your vote", "صوتك") else tr("Vote", "تصويت"), GroupAction.VOTE_RESTAURANT, option.id, primary = myVote != option.id, enabled = myVote != option.id)) else emptyList())
                    }
                    if(!canVote && !me.guest) card("poll-sign-in", "Sign in to vote", "Restaurant voting is available to registered members.")
                    if(owner) button("Finish poll & use leading restaurant", GroupAction.FINALIZE_RESTAURANT, primary = true, enabled = r.restaurantVotes.isNotEmpty())
                } else if(!me.guest && me.participating) foodEditor(r, primary = false)
                if(owner) {
                    val blocker = runCatching { RoomRules.spinReady(r) }.exceptionOrNull()?.message
                    button("Spin together", GroupAction.PREPARE_SPIN, primary = !r.restaurantPollOpen, enabled = blocker == null)
                    if(blocker != null) card("spin-next-step", "Before spinning", blocker)
                    button("Cancel today's order", GroupAction.CANCEL)
                } else if(!r.restaurantPollOpen && me.participating) card("ready-next-step", "You are joined", "Add or change your sandwiches while waiting for the organizer to start the shared spin.")
            }
            RoomPhase.PREPARING_SPIN -> { card("prepare", "Getting everyone in sync", "Waiting for participating phones to acknowledge the countdown."); if(owner) button("Stop waiting", GroupAction.ABORT_SPIN); if(!me.guest && me.participating) foodEditor(r, primary = false) }
            RoomPhase.SPINNING -> { card("spinning", "One spin. One result.", "Stay here to watch the wheel together."); if(!me.guest && me.participating) foodEditor(r, primary = false) }
            RoomPhase.ACCEPTING -> {
                val name = r.members.single { it.id == r.spin!!.winnerId }.name
                card("winner", "$name is selected!", "Food entry stays open while $name confirms ordering duty.")
                if(r.spin!!.winnerId == c.me()) { button("I'll take care of it", GroupAction.ACCEPT_DUTY, primary = true); button("I can't this time", GroupAction.DECLINE_DUTY) }
                if(!me.guest && me.participating) foodEditor(r, primary = false)
            }
            RoomPhase.COLLECTING -> {
                val progress = c.reply!!.progress
                val needsAccount = progress?.accountShared?.not() ?: (r.account == null && !me.guest && me.participating)
                val awaitingFood = r.orderingMembers.filter { m -> r.carts.none { it.memberId == m.id && it.submitted } }
                if(payer) button(if(needsAccount) "Choose receiving account" else tr("Change receiving account", "تغيير حساب الاستلام"), GroupAction.OPEN_ACCOUNT, primary = needsAccount)
                r.account?.let { card("account-shared", "Receiving account shared", "${it.holder} · ${it.bank}\n•••• ${it.identifier.takeLast(4)}\nEveryone will confirm this recipient with their total.") }
                if(!me.guest && me.participating) foodEditor(r, primary = true)
                if(payer) c.reply!!.receipts.forEach { receipt ->
                    card("collected-order:${receipt.memberId}", receipt.name,
                        receipt.lines.joinToString("\n") { "${it.quantity} × ${localizedReceiptDescription(r, it, language)} · ${Money.format(it.amount, receipt.currency)}" },
                        receipt.totalText)
                }
                if(payer) r.carts.forEach { cart -> cart.lines.filter { it.description.isNotEmpty() }.forEach { line ->
                    card("price:${cart.memberId}:${line.id}", "${r.members.single { it.id == cart.memberId }.name} · ${line.quantity} × ${line.description}", line.notes,
                        line.unitPrice?.let { Money.format(it * line.quantity, r.restaurant.currency) } ?: tr("Awaiting price", "بانتظار التسعير"),
                        listOf(GroupButton(if(line.unitPrice == null) "Enter unit price" else "Change unit price", GroupAction.OPEN_PRICE_ITEM, "${cart.memberId}:${line.id}")))
                } }
                val nextStep = when {
                    awaitingFood.isNotEmpty() -> "Waiting for food orders from ${awaitingFood.joinToString { it.name }}. Each person must submit their food or choose No food this time."
                    needsAccount -> "Waiting for ${r.members.single { it.id == r.payerId }.name} to share a receiving account."
                    (owner || payer) && !progress?.reviewBlocker.isNullOrBlank() -> requireNotNull(progress).reviewBlocker
                    owner || payer -> "Everyone has submitted. Review the totals so each person can confirm their share and recipient."
                    else -> "Waiting for ${r.members.single { it.id == r.payerId }.name} to review everyone's totals. Then you can confirm your share and recipient."
                }
                card("order-next-step", tr("Next step", "الخطوة التالية"), nextStep)
                if(owner || payer) button(tr("Review everyone's totals", "مراجعة إجمالي طلبات الجميع"), GroupAction.REVIEW, primary = true, enabled = progress?.canReview ?: (awaitingFood.isEmpty() && !needsAccount))
                if(owner || payer) { feeFields(); button("Update fees and reopen", GroupAction.SET_FEES); button("Reopen ordering", GroupAction.REOPEN) }
                if(owner) button("Cancel today's order", GroupAction.CANCEL)
            }
            RoomPhase.REVIEW -> {
                receiptCards(c.reply!!.receipts); accountCard()
                append(GroupSettlementPresentation(c).review())
            }
            RoomPhase.PLACED, RoomPhase.FULFILLED -> {
                receiptCards(c.reply!!.receipts); accountCard()
                append(GroupSettlementPresentation(c).settlement())
                transferCards()
            }
            RoomPhase.ARCHIVED, RoomPhase.CANCELLED -> { card("complete", "Your room stays open", "This order is ${if(r.phase == RoomPhase.CANCELLED) "cancelled" else "complete"}. Everyone keeps their membership for the next meal."); if(owner) button("Start a new order", GroupAction.NEXT_ORDER, primary = true) }
        }
        val contact = r.restaurant.contact.phoneE164 ?: r.restaurant.contact.whatsappE164 ?: ""
        if(payer) card("contact", r.restaurant.localizedName(language), "$contact\n${r.restaurant.contact.address ?: ""}\n${r.destination}", actions = listOf(
            GroupButton(tr("Share order via WhatsApp", "مشاركة الطلب عبر واتساب"), GroupAction.SHARE_ORDER_WHATSAPP, primary = r.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)),
            GroupButton(tr("Copy restaurant-ready list", "نسخ قائمة الطلب للمطعم"), GroupAction.SHARE_RESTAURANT_ORDER),
            GroupButton(tr("Call restaurant", "الاتصال بالمطعم"), GroupAction.CALL_RESTAURANT),
        ))
    }
    private fun foodEditor(r: Room, primary: Boolean) {
        if(!primary) card("early-order", tr("Add your food before the spin", "أضف طلبك قبل الاختيار"), tr("Your saved items stay with this order. You can edit them before or after the payer is selected.", "يبقى طلبك محفوظاً ويمكنك تعديله قبل أو بعد اختيار من سيطلب."))
        val favorites = c.library.home?.profile?.favoriteOrders.orEmpty().filter { it.restaurantId == r.restaurant.id }
        favorites.forEach { favorite ->
            card("reuse-favorite:${favorite.id}", "★ ${favorite.title}", favoriteSummary(favorite), tr("Favorite", "مفضل"),
                listOf(GroupButton(tr("Add favorite order", "إضافة الطلب المفضل"), GroupAction.REUSE_ORDER, "favorite|${favorite.id}")))
        }
        val favoriteKeys = favorites.map { it.selectionKey() }.toSet()
        c.previousOrderChoices(r.restaurant).filter { choice ->
            choice.receipt.orderSelectionKey(choice.restaurantId.ifBlank { "name:${choice.order.restaurantName.lowercase()}" }) !in favoriteKeys
        }.take(4).forEach { choice ->
            card("reuse-past:${choice.value}", tr("Previous order", "طلب سابق"), receiptSummary(choice.receipt),
                if(choice.repeatCount > 1) tr("Repeated ${choice.repeatCount} times", "تكرر ${choice.repeatCount} مرات") else timeLabel(choice.order.completedAt),
                listOf(GroupButton(tr("Add all to my order", "إضافة الكل إلى طلبي"), GroupAction.REUSE_ORDER, choice.value),
                    GroupButton(tr("Save as favorite", "حفظ كمفضل"), GroupAction.FAVORITE_ORDER, choice.value)))
        }
        if(r.restaurant.openOrdering) button(tr("Add a food item", "إضافة صنف"), GroupAction.OPEN_CUSTOM_ITEM, primary = primary)
        field(GroupFieldKey.MENU_SEARCH, tr("Find food · Arabic or English", "ابحث عن طعام بالعربية أو الإنجليزية"))
        val query = c.text(GroupFieldKey.MENU_SEARCH).trim()
        val menuItems = r.restaurant.menu.items.filter { item -> query.isBlank() || listOf(item.name, item.nameAr, item.description, item.descriptionAr).any { it.contains(query, ignoreCase = true) } }
        if(menuItems.isEmpty()) card("menu-empty", tr("No matching items", "لا توجد أصناف مطابقة"), tr("Try a different name or clear the search.", "جرّب اسماً آخر أو امسح البحث."))
        val sandwichCategoryIds = r.restaurant.menu.categories.filter { it.name.contains("sandwich", true) || it.nameAr.contains("سند") }.map { it.id }.toSet()
        menuItems.sortedBy { if(it.categoryId in sandwichCategoryIds) 0 else 1 }.forEach { i ->
            val simple = i.variants.isEmpty() && i.optionGroupIds.isEmpty()
            val quantity = c.myCart().lines.filter { it.itemId == i.id }.sumOf { it.quantity }
            val actions = if(!i.available) emptyList() else if(simple) listOf(
                GroupButton(tr("+ Add", "+ إضافة"), GroupAction.QUICK_ADD_ITEM, i.id),
                GroupButton(tr("Quantity / notes", "الكمية والملاحظات"), GroupAction.OPEN_ITEM, i.id),
            ) else listOf(GroupButton(tr("Choose size / extras", "اختيار الحجم والإضافات"), GroupAction.OPEN_ITEM, i.id))
            card("menu:${i.id}", i.localizedName(language), i.localizedDescription(language),
                Money.format(i.basePriceMinor, r.restaurant.currency) + if(quantity > 0) tr(" · $quantity in your order", " · $quantity في طلبك") else "", actions)
        }
        cart()
        if(!c.myCart().submitted) button(if(c.myCart().lines.isEmpty()) "No food this time" else "Submit my food order", GroupAction.SUBMIT_CART, primary = primary)
        else card("cart-submitted", "Your food order is saved", "You can edit it before or after the spin. Submit again after making changes.")
    }
    private fun cart() {
        val r = c.room()
        val cart = c.myCart()
        val priced = runCatching { Billing.lines(r.restaurant, cart) }.getOrNull()
        cart.lines.forEachIndexed { index, line ->
            val name = line.description.ifEmpty { r.restaurant.menu.items.single { it.id == line.itemId }.localizedName(language) }
            val total = if(line.description.isNotEmpty() && line.unitPrice == null) null else priced?.getOrNull(index)?.amount
            card("cart:${line.id}", "${line.quantity} × $name", line.notes,
                total?.let { Money.format(it, r.restaurant.currency) } ?: tr("Awaiting price", "بانتظار السعر"),
                listOf(GroupButton("−", GroupAction.DECREASE_CART_QUANTITY, line.id),
                    GroupButton("+", GroupAction.INCREASE_CART_QUANTITY, line.id, enabled = line.quantity < 99),
                    GroupButton(tr("Edit", "تعديل"), GroupAction.EDIT_CART_ITEM, line.id),
                    GroupButton(tr("Remove", "حذف"), GroupAction.REMOVE_CART_ITEM, line.id, destructive = true)))
        }
        c.reply?.receipts?.firstOrNull { it.memberId == c.me() }?.let { card("estimate", tr("Your estimated total", "إجمالي طلبك المتوقع"), it.totalText) }
    }
    private fun item() {
        val i = c.selectedItem ?: return; val r = c.room(); title = i.localizedName(language); subtitle = i.localizedDescription(language)
        i.variants.forEach { v -> card("variant:${v.id}", v.localizedName(language), Money.format(v.priceMinor, r.restaurant.currency), if(c.variant == v.id) tr("Selected", "محدد") else "", listOf(GroupButton(tr("Choose size", "اختر الحجم أو الخبز"), GroupAction.SELECT_VARIANT, v.id))) }
        r.restaurant.menu.optionGroups.filter { it.id in i.optionGroupIds }.forEach { g ->
            card("option-group:${g.id}", g.localizedName(language), tr("Select ${g.minSelections}–${g.maxSelections}", "اختر ${g.minSelections}–${g.maxSelections}"))
            g.options.forEach { o -> button("${if(o.id in c.options) "✓ " else ""}${o.localizedName(language)} · ${Money.format(o.priceDeltaMinor, r.restaurant.currency)}", GroupAction.TOGGLE_OPTION, o.id) }
        }
        val preview = runCatching { Billing.lines(r.restaurant, MemberCart(c.me(), lines = listOf(CartLine("preview", i.id, c.text(GroupFieldKey.QUANTITY).toInt(), c.variant, c.options)))).single() }.getOrNull()
        preview?.let { card("item-price", tr("Food subtotal", "إجمالي الطعام"), "${it.quantity} × ${Money.format(it.amount / it.quantity, r.restaurant.currency)}", Money.format(it.amount, r.restaurant.currency)) }
        field(GroupFieldKey.QUANTITY, tr("Quantity", "الكمية")); field(GroupFieldKey.NOTE, tr("Preparation notes", "ملاحظات التحضير"))
        button(if(c.editingCartLineId == null) "Add to my order" else "Update my order", GroupAction.ADD_CART_ITEM, primary = true)
    }
    private fun accounts() {
        title = "Receiving account"; subtitle = "Select a saved account or enter its details, then share it with this order to continue."
        if(c.library.accounts.isNotEmpty()) button("Add another account", GroupAction.NEW_ACCOUNT)
        c.library.accounts.forEach { a ->
            val selected = c.selectedAccount?.id == a.id
            val matches = c.accountMatchesDraft(a)
            card("saved-account:${a.id}", "${a.holder} · ${a.bank}", "•••• ${a.identifier.takeLast(4)}", if(matches) "Selected" else if(selected) "Editing" else "", listOf(
                GroupButton(if(matches) "Selected" else if(selected) "Restore saved details" else "Select account", GroupAction.SELECT_ACCOUNT, a.id, enabled = !matches),
                GroupButton("Delete saved account", GroupAction.DELETE_ACCOUNT, a.id, destructive = true),
            ))
        }
        field(GroupFieldKey.AANI, "Receive with Aani", toggle = true); field(GroupFieldKey.ACCOUNT_HOLDER, tr("Account holder", "صاحب الحساب")); field(GroupFieldKey.ACCOUNT_BANK, tr("Bank name", "اسم البنك")); field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) "Aani phone number · with country code" else "IBAN / account identifier")
        button("Save on this device", GroupAction.SAVE_ACCOUNT)
        if(c.reply?.room?.account?.let(c::accountMatchesDraft) == true) {
            subtitle = "This account is already shared with this order. Continue to ${if(c.reply?.room?.phase == RoomPhase.REVIEW) "review the totals" else "submit your food"}."
            button("Continue to order", GroupAction.BACK, primary = true)
        } else button("Share account & continue", GroupAction.SHARE_ACCOUNT, primary = true)
    }
    private fun receipts() {
        title = "Receipts"; subtitle = "Downloaded for offline access · Food Run breakdowns"
        c.reply?.let { receiptCards(it.receipts) }; accountCard()
        c.reply?.room?.takeIf { it.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED) }?.let { room ->
            if (room.transfers.any { it.status == TransferStatus.DECLARED && (room.payerId == c.me() || it.refund && it.memberId == c.me()) }) field(GroupFieldKey.REASON, "Reason for rejecting a transfer or refund")
            if (room.payerId == c.me() && c.reply!!.receipts.any { GroupSettlementPresentation.refundAvailable(room, it) }) {
                field(GroupFieldKey.AMOUNT, "Refund amount"); field(GroupFieldKey.REFERENCE, tr("Refund reference / cash note", "مرجع الإرجاع أو ملاحظة النقد"))
            }
        }
        transferCards()
    }
    private fun history() {
        title = tr("Past orders", "الطلبات السابقة"); subtitle = tr("Repeated orders are grouped. Changed items, sizes, extras, quantities, or notes stay separate.", "تُجمع الطلبات المتكررة، وتبقى الاختلافات في الأصناف والأحجام والإضافات والكميات والملاحظات منفصلة.")
        val choices = c.currentRoomPreviousOrderChoices(includeEmpty = true)
        val favorites = c.library.home?.profile?.favoriteOrders.orEmpty()
        val favoriteKeys = favorites.map { it.selectionKey() }.toSet()
        choices.forEach { choice ->
            val key = choice.receipt.orderSelectionKey(choice.restaurantId.ifBlank { "name:${choice.order.restaurantName.lowercase()}" })
            val summary = receiptSummary(choice.receipt)
            card("order:${choice.value}", choice.order.restaurantName + if(summary.isBlank()) "" else " · $summary", timeLabel(choice.order.completedAt),
                if(choice.repeatCount > 1) tr("Repeated ${choice.repeatCount} times", "تكرر ${choice.repeatCount} مرات") else "",
                if(choice.receipt.lines.isEmpty() || key in favoriteKeys || c.library.home == null) emptyList() else listOf(GroupButton(tr("Save as favorite", "حفظ كمفضل"), GroupAction.FAVORITE_ORDER, choice.value)))
            receiptCards(listOf(choice.receipt), "past:${choice.order.number}:")
            choice.order.account?.let { accountCard(it, "past-account:${choice.order.number}", tr("Account used for order #${choice.order.number}", "حساب الدفع للطلب #${choice.order.number}")) }
        }
        if((c.reply?.historyNextOffset ?: -1) >= 0) button(tr("Download older receipts", "تحميل إيصالات أقدم"), GroupAction.LOAD_OLDER_HISTORY)
        if(choices.isEmpty()) card("empty", tr("No unique past orders downloaded yet", "لا توجد طلبات سابقة مختلفة بعد"), tr("Completed orders appear here when the next order starts.", "تظهر الطلبات المكتملة هنا عند بدء الطلب التالي."))
    }

    private fun dashboardCards(prefix: String) {
        data class SnapshotRow(val session: StoredSession, val reply: RoomReply, val room: Room, val receipt: Receipt?)
        val rows = c.library.sessions.mapNotNull { session ->
            val reply = c.library.snapshots[session.roomId] ?: return@mapNotNull null
            val room = reply.room ?: return@mapNotNull null
            SnapshotRow(session, reply, room, reply.receipts.firstOrNull { it.memberId == session.memberId })
        }
        val payableRows = rows.filter { it.room.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED) && it.room.payerId != null }
        var toPay = 0L; var toReceive = 0L
        payableRows.forEach { row ->
            if (row.room.payerId == row.session.memberId) {
                row.reply.receipts.filterNot { it.memberId == row.session.memberId }.forEach { receipt ->
                    if (receipt.balance > 0) toReceive += receipt.balance else toPay += -receipt.balance
                }
            } else row.receipt?.let { if (it.balance > 0) toPay += it.balance else toReceive += -it.balance }
        }
        card("${prefix}wallet-summary", tr("Wallet dashboard", "لوحة المحفظة"),
            tr("You need to pay ${Money.format(toPay, "AED")} · You need to receive ${Money.format(toReceive, "AED")}", "عليك دفع ${Money.format(toPay, "AED")} · لك لدى الآخرين ${Money.format(toReceive, "AED")}"),
            tr("${payableRows.count { it.receipt != null }} active balances", "${payableRows.count { it.receipt != null }} أرصدة حالية"))
        payableRows.forEach { row ->
            val payerName = row.room.members.firstOrNull { it.id == row.room.payerId }?.name ?: tr("selected payer", "الشخص المختار")
            if (row.room.payerId == row.session.memberId) {
                row.reply.receipts.filterNot { it.memberId == row.session.memberId }.filter { it.balance != 0L }.forEach { receipt ->
                    val detail = if(receipt.balance > 0) tr("${receipt.name} needs to pay you ${Money.format(receipt.balance, receipt.currency)}", "على ${receipt.name} دفع ${Money.format(receipt.balance, receipt.currency)} لك")
                        else tr("You need to refund ${Money.format(-receipt.balance, receipt.currency)} to ${receipt.name}", "عليك رد ${Money.format(-receipt.balance, receipt.currency)} إلى ${receipt.name}")
                    val pending = row.room.transfers.firstOrNull { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }
                    val status = pending?.let { tr("${Money.format(it.amount, receipt.currency)} sent · awaiting recipient approval", "تم إرسال ${Money.format(it.amount, receipt.currency)} · بانتظار موافقة المستلم") } ?: stage(row.room.phase)
                    card("${prefix}wallet:${row.room.id}:${receipt.memberId}", row.room.name, detail, status, listOf(GroupButton(if(pending != null && !pending.refund) tr("Review and approve receipt", "مراجعة وتأكيد الاستلام") else tr("Open payment", "فتح الدفع"), GroupAction.RESUME, row.room.id)))
                }
            } else row.receipt?.takeIf { it.balance != 0L }?.let { receipt ->
                val recipient = row.room.account?.holder ?: payerName
                val detail = if(receipt.balance > 0) tr("Pay ${Money.format(receipt.balance, receipt.currency)} to $recipient", "ادفع ${Money.format(receipt.balance, receipt.currency)} إلى $recipient")
                    else tr("$payerName needs to refund you ${Money.format(-receipt.balance, receipt.currency)}", "على $payerName رد ${Money.format(-receipt.balance, receipt.currency)} لك")
                val pending = row.room.transfers.firstOrNull { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }
                val status = pending?.let { tr("${Money.format(it.amount, receipt.currency)} sent · awaiting recipient approval", "تم إرسال ${Money.format(it.amount, receipt.currency)} · بانتظار موافقة المستلم") } ?: stage(row.room.phase)
                val action = if(pending != null) { if(pending.refund) tr("Review and approve receipt", "مراجعة وتأكيد الاستلام") else tr("View pending payment", "عرض الدفعة المعلقة") } else tr("Pay now / mark paid", "ادفع الآن / سجل الدفع")
                card("${prefix}wallet:${row.room.id}", row.room.name, detail, status, listOf(GroupButton(action, GroupAction.RESUME, row.room.id)))
            }
        }
        val uniquePast = c.previousOrderChoices().size
        card("${prefix}orders-summary", tr("Orders dashboard", "لوحة الطلبات"),
            tr("${rows.size} current rooms · $uniquePast unique previous orders · ${c.library.home?.profile?.favoriteOrders?.size ?: 0} favorites", "${rows.size} غرف حالية · $uniquePast طلبات سابقة مختلفة · ${c.library.home?.profile?.favoriteOrders?.size ?: 0} مفضلة"))
    }

    private fun receiptSummary(receipt: Receipt): String = receipt.lines.take(3).joinToString(" · ") { "${it.quantity} × ${it.description}" } +
        if(receipt.lines.size > 3) tr(" · +${receipt.lines.size - 3} more", " · +${receipt.lines.size - 3} إضافية") else ""
    private fun favoriteSummary(favorite: FavoriteOrder): String = favorite.lines.take(3).joinToString(" · ") { "${it.quantity} × ${it.label}" } +
        if(favorite.lines.size > 3) tr(" · +${favorite.lines.size - 3} more", " · +${favorite.lines.size - 3} إضافية") else ""
    private fun receiptCards(receipts: List<Receipt>, prefix: String = "") {
        receipts.forEach { receipt ->
            val reviewingOwn = prefix.isEmpty() && c.reply?.room?.phase == RoomPhase.REVIEW && receipt.memberId == c.me()
            val account = c.reply?.room?.account
            val recipient = if (reviewingOwn && account != null) "\nRecipient: ${account.holder} · ${account.bank}\n${account.identifier} · account version ${account.version}" else ""
            val actions = mutableListOf(GroupButton("Share receipt", GroupAction.SHARE_RECEIPT, prefix + receipt.memberId))
            if (reviewingOwn) actions += GroupSettlementPresentation.quoteConfirmationAction(c.room(), c.me(), language)
            card("receipt:$prefix${receipt.memberId}", "${receipt.name} · ${receipt.totalText}", receiptDetail(receipt, prefix.isEmpty()) + recipient, "Revision ${receipt.revision}", actions)
        }
    }
    private fun receiptDetail(r: Receipt, currentOrder: Boolean = true): String = (r.lines.map { "${it.quantity} × ${it.description} · ${Money.format(it.amount, r.currency)}${if(it.notes.isNotBlank()) "\n${it.notes}" else ""}" } + listOf("Delivery ${Money.format(r.delivery, r.currency)} · Service/adjustment ${Money.format(r.service, r.currency)}", "Discount ${Money.format(r.discount, r.currency)} · Tax ${Money.format(r.tax, r.currency)}", "Total ${r.totalText}", GroupSettlementPresentation.receiptBalanceText(r, if(currentOrder) c.reply?.room?.payerId else null, language))).joinToString("\n")
    fun receiptText(value: String): String {
        val parts = value.split(':')
        val past = if (parts.first() == "past") c.reply!!.history.single { it.number == parts[1].toLong() } else null
        val receipt = if (past != null) past.receipts.single { it.memberId == parts[2] } else c.reply!!.receipts.single { it.memberId == value }
        val account = if (past != null) past.account else c.room().account
        val orderNumber = past?.number ?: c.room().orderNumber
        val restaurantName = past?.restaurantName ?: c.room().restaurant.name
        val accountText = account?.let { "Recipient: ${it.holder} · ${it.bank}\nAccount: ${it.identifier} · ${it.currency} · version ${it.version}" } ?: "Receiving account unavailable"
        return "Food Run · ${c.room().name} · order #$orderNumber\n$restaurantName\n${receipt.name}\n${receiptDetail(receipt, past == null)}\n$accountText\nRevision ${receipt.revision}\nDownloaded ${timeLabel(c.reply!!.serverTime)}\nFood Run breakdown; restaurant invoice is separate."
    }
    fun restaurantOrderText(): String {
        val r = c.room(); require(r.payerId == c.me()) { "Only the payer can share the combined order." }
        return restaurantReadyText(r, c.reply!!.receipts, language)
    }
    private fun accountCard(account: ReceivingAccount? = c.reply?.room?.account, id: String = "account", label: String = "Send to") {
        account?.let { a -> card(id, "$label ${a.holder}", "${a.bank}\n${a.identifier}\n${a.currency} · account version ${a.version}", actions = if(id == "account") listOf(GroupButton(tr("Copy payment details", "نسخ بيانات الدفع"), GroupAction.COPY_PAYMENT_DETAILS)) else emptyList()) }
    }
    private fun transferCards() { val r = c.reply?.room ?: return; r.transfers.forEach { t ->
        val actions = if(t.status.name == "DECLARED") when { t.refund && t.memberId == c.me() -> listOf(GroupButton("Confirm refund received", GroupAction.CONFIRM_REFUND, t.id), GroupButton("Reject refund claim", GroupAction.REJECT_TRANSFER, t.id)); !t.refund && r.payerId == c.me() -> listOf(GroupButton(tr("Approve · money received", "موافقة · استلمت المبلغ"), GroupAction.CONFIRM_TRANSFER, t.id), GroupButton("Reject claim", GroupAction.REJECT_TRANSFER, t.id)); else -> emptyList() } else emptyList()
        card("transfer:${t.id}", "${if(t.refund) "Refund" else "Transfer"} · ${Money.format(t.amount, r.restaurant.currency)}", "${r.members.single { it.id == t.memberId }.name} · ${t.reference}", t.status.name.lowercase(), actions)
    }; if(r.payerId == c.me()) c.reply?.receipts?.filter { GroupSettlementPresentation.refundAvailable(r, it) }?.forEach { button("Record refund to ${it.name}", GroupAction.DECLARE_REFUND, it.memberId) } }
    private fun feeFields() {
        val delivery = if(c.page == GroupPage.SETUP) c.flag(GroupFieldKey.DELIVERY) else c.reply?.room?.deliveryMode == true
        if(delivery) card("automatic-delivery", tr("Automatic delivery", "حساب التوصيل تلقائياً"), tr("AED 5 split equally for 1–4 people with food; AED 1 each for 5 or more. Your own food counts too. Every receipt includes its share.", "٥ دراهم بالتساوي بين ١–٤ أشخاص لديهم طعام، ودرهم لكل شخص عند ٥ أشخاص فأكثر. يُحسب طلبك أيضاً وتضاف الحصة لكل إيصال."))
        else { field(GroupFieldKey.DELIVERY_FEE, tr("Delivery fee", "رسوم التوصيل")); field(GroupFieldKey.PROPORTIONAL, tr("Split delivery in proportion to food", "تقسيم التوصيل بنسبة قيمة الطعام"), toggle = true) }
        if(delivery && c.page != GroupPage.SETUP && c.reply?.room?.fees?.automaticDelivery == false) card("delivery-update", tr("Saved delivery fees", "رسوم التوصيل المحفوظة"), tr("Update fees to apply automatic delivery and request new total confirmations.", "حدّث الرسوم لتطبيق التوصيل التلقائي وإعادة تأكيد الإجمالي."))
        field(GroupFieldKey.SERVICE_FEE, tr("Service fee", "رسوم الخدمة")); field(GroupFieldKey.DISCOUNT, tr("Shared discount", "الخصم المشترك"))
    }
    private fun stage(phase: RoomPhase): String = when(phase) { RoomPhase.LOBBY -> tr("Gathering", "تجمع الأعضاء"); RoomPhase.PREPARING_SPIN -> tr("Getting ready", "جارٍ الاستعداد"); RoomPhase.SPINNING -> "Spinning"; RoomPhase.ACCEPTING -> "Accepting duty"; RoomPhase.COLLECTING -> "Choose food"; RoomPhase.REVIEW -> tr("Confirm totals", "تأكيد الإجمالي"); RoomPhase.PLACED -> tr("Order placed", "تم إرسال الطلب"); RoomPhase.FULFILLED -> tr("Food arrived", "وصل الطعام"); RoomPhase.ARCHIVED -> tr("Complete", "مكتمل"); RoomPhase.CANCELLED -> tr("Cancelled", "ملغى") }
    private fun timeLabel(millis: Long): String = PickupDateFormatterForGroups.format(millis)
}

internal object PickupDateFormatterForGroups {
    fun format(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0); val shifted = seconds / 86400 + 719468
        val era = shifted / 146097; val dayOfEra = shifted - era * 146097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val marchMonth = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * marchMonth + 2) / 5 + 1
        val month = marchMonth + if (marchMonth < 10) 3 else -9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        fun two(value: Long) = value.toString().padStart(2, '0')
        return "$year-${two(month)}-${two(day)} · ${two(seconds % 86400 / 3600)}:${two(seconds % 3600 / 60)} UTC"
    }
}
