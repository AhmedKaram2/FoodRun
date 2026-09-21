package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

private fun localizedReceiptDescription(room: Room, line: ReceiptLine, language: String): String {
    if(language != "ar" || line.itemId.isBlank()) return line.description
    val item = room.restaurant.menu.items.firstOrNull { it.id == line.itemId } ?: return line.description
    val variant = item.variants.firstOrNull { it.id == line.variantId }
    val options = room.restaurant.menu.optionGroups.flatMap { it.options }.filter { it.id in line.optionIds }
    return (listOf(item.localizedName(language)) + listOfNotNull(variant?.localizedName(language)) + options.map { it.localizedName(language) }).joinToString(" · ")
}

internal fun restaurantWhatsAppNumber(restaurant: Restaurant): String {
    val explicit = restaurant.contact.whatsappE164
    val phone = explicit ?: restaurant.contact.phoneE164?.takeIf { it.matches(Regex("\\+9715[0-9]{8}")) }
    return phone.orEmpty().filter(Char::isDigit)
}

internal fun restaurantReadyText(room: Room, receipts: List<Receipt>, language: String = "en", expectedArrival: String = room.restaurantReference): String {
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
    return (listOf(room.restaurant.localizedName(language), if(room.deliveryMode) (if(language == "ar") "توصيل: ${room.destination}" else "Delivery: ${room.destination}") else if(language == "ar") "استلام من المطعم" else "Pickup",
        (if(language == "ar") "وقت الوصول أو الاستلام المتوقع: " else "Expected delivery / pickup: ") + expectedArrival.ifBlank { if(language == "ar") "يحدده المطعم" else "To be confirmed by restaurant" }, "") + lines).joinToString("\n")
}

internal class GroupPresentation(private val c: GroupController) {
    private val fields = mutableListOf<GroupField>()
    private val cards = mutableListOf<GroupCard>()
    private val buttons = mutableListOf<GroupButton>()
    private var title = "Food Run"
    private var subtitle = "Good food. Great company."
    private val language get() = c.library.language
    private val ar get() = language == "ar"
    private fun ui(value: String) = GroupUiText.translate(value, ar)
    private fun tr(en: String, ar: String) = if(this.ar) GroupUiText.translate(en, true).takeIf { it != en } ?: ar else en
    private fun field(key: GroupFieldKey, label: String, multiline: Boolean = false, toggle: Boolean = false) { fields += GroupField(key, ui(label), c.text(key), multiline, toggle, secret = key == GroupFieldKey.PASSWORD) }
    private fun choiceField(key: GroupFieldKey, label: String, choices: List<GroupChoice>) { fields += GroupField(key, ui(label), c.text(key), choices = choices) }
    private fun button(title: String, action: GroupAction, value: String = "", primary: Boolean = false, enabled: Boolean = true) { buttons += GroupButton(ui(title), action, value, primary, enabled = enabled) }
    private fun card(id: String, title: String, detail: String = "", badge: String = "", actions: List<GroupButton> = emptyList()) { cards += GroupCard(id, title, detail, badge, actions.map { it.copy(title = ui(it.title)) }) }
    private fun append(content: GroupFlowContent) { fields += content.fields; cards += content.cards; buttons += content.buttons }
    fun render(): GroupState {
        c.accessBlock?.takeIf { c.page in listOf(GroupPage.ROOM, GroupPage.SETUP, GroupPage.CONNECT, GroupPage.BLOCK_REQUEST) }?.let { block ->
            val remaining = maxOf(0, (block.until - c.platform.now() - c.blockClockOffset + 999) / 1000)
            val countdown = "${remaining / 86400} ${ui("days")} · " + listOf(remaining % 86400 / 3600, remaining % 3600 / 60, remaining % 60).joinToString(":") { it.toString().padStart(2, '0') }
            val message = listOfNotNull(
                ui(if(block.removed) "Account removed" else "Room access blocked"),
                if(block.durationHours > 0) "${ui("Blocked duration")} · ${block.durationHours} ${ui("hours")}" else null,
                if(block.until > 0) countdown else ui("Until unblocked by admin"), block.reason.takeIf { it.isNotBlank() },
                if(block.until > 0 && remaining == 0L) ui("The block period ended. Reconnect to continue.") else null,
            ).joinToString("\n")
            return GroupState(page = GroupPage.PROFILE, title = ui("Room access"), error = message, rtl = ar, accessBlocked = true,
                buttons = listOf(GroupButton(ui("Check access again"), GroupAction.REFRESH), GroupButton(ui("Back to my account"), GroupAction.BACK)))
        }
        when(c.page) {
            GroupPage.BLOCK_REQUEST -> {
                title = ui("Request a user block")
                subtitle = ui("The admin reviews your request before any access is blocked.")
                val candidates = c.room().activeMembers.filter { it.id != c.me() && !it.guest }
                fields += GroupField(GroupFieldKey.BLOCK_MEMBER, ui("Member"), c.text(GroupFieldKey.BLOCK_MEMBER).ifBlank { candidates.firstOrNull()?.id.orEmpty() }, choices = candidates.map { GroupChoice(it.id, it.name) })
                fields += GroupField(GroupFieldKey.BLOCK_HOURS, ui("Requested duration"), c.text(GroupFieldKey.BLOCK_HOURS).ifBlank { "24" }, choices = listOf("1" to "1 hour", "6" to "6 hours", "24" to "1 day", "72" to "3 days", "168" to "7 days", "720" to "30 days").map { GroupChoice(it.first, ui(it.second)) })
                field(GroupFieldKey.BLOCK_REASON, ui("Reason"), multiline = true)
                button("Send block request", GroupAction.REQUEST_BLOCK, primary = true, enabled = candidates.isNotEmpty() && c.text(GroupFieldKey.BLOCK_REASON).trim().length in 5..300)
            }
            GroupPage.HOME -> home()
            GroupPage.PROFILE -> profile()
            GroupPage.PEOPLE -> people()
            GroupPage.CUSTOM_ITEM -> { title = if(c.editingCartLineId == null) "What would you like?" else "Change your item"; subtitle = "Add one item at a time. The selected person adds its price later."; field(GroupFieldKey.CUSTOM_NAME, tr("Food item", "الصنف")); field(GroupFieldKey.QUANTITY, tr("Quantity", "الكمية")); field(GroupFieldKey.NOTE, tr("Notes / extras", "ملاحظات وإضافات")); button(if(c.editingCartLineId == null) "Add to my order" else "Update my order", GroupAction.ADD_CUSTOM_ITEM, primary = true) }
            GroupPage.PRICE_ITEM -> { title = "Price this item"; subtitle = "Enter the price for one item; quantity is applied automatically."; field(GroupFieldKey.AMOUNT, "${ui("Unit price")} · ${c.room().restaurant.currency}"); button("Save price", GroupAction.SAVE_ITEM_PRICE, primary = true) }
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
        return GroupState(c.page, if(c.page in listOf(GroupPage.ROOM, GroupPage.ITEM)) title else ui(title), if(c.page == GroupPage.ITEM) subtitle else ui(subtitle), fields, cards, buttons, c.busy, c.online,
            ui(if (c.session == null) "Nearby or internet live rooms · Firebase account backup"
            else if(c.online && c.session?.hub?.fingerprint.isNullOrEmpty()) "Live over the internet · saved on this device"
            else if(c.online) "Live on the local network · saved on this device"
            else tr("Offline · saved receipt data · last sync", "من غير إنترنت · إيصالات محفوظة · آخر تحديث") + " ${c.reply?.serverTime?.let(::timeLabel) ?: tr("unavailable", "مش متاح")}"),
            ui(c.error), wheel, if (c.page == GroupPage.ROOM) room?.code ?: "" else "", c.page != GroupPage.HOME,
            if (c.page != GroupPage.ROOM) -1 else when (room?.phase) {
                RoomPhase.LOBBY -> if(room.restaurantPollOpen) 1 else 2
                RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING, RoomPhase.ACCEPTING -> 3
                RoomPhase.COLLECTING, RoomPhase.REVIEW -> 4
                RoomPhase.PLACED, RoomPhase.FULFILLED, RoomPhase.ARCHIVED -> 5
                else -> -1
            }, if(c.page == GroupPage.ROOM && room != null && c.session != null) c.roomInviteLink(c.session!!.hub, room.code) else "", ar,
            inviteSummary = if (c.page == GroupPage.ROOM && room != null) roomInvitation(room, "", language).lines().drop(1).take(2).joinToString("\n") else "")
    }
    private fun home() {
        title = tr("Food Run", "فود رن"); subtitle = tr("Gather your people. Share a meal.", "اجمع أصحابك وشاركوا وجبتكم.")
        if (c.library.home == null) {
            button(ui("Continue with Google"), GroupAction.GOOGLE_SIGN_IN, primary = true)
            button(ui("Register / sign in"), GroupAction.OPEN_PROFILE)
            button("English", GroupAction.SET_LANGUAGE, "en", enabled = ar)
            button("العربية", GroupAction.SET_LANGUAGE, "ar", enabled = !ar)
            card("sign-in", ui("Use your existing web account"), ui("Sign in or register with Google to open your rooms. Guest mode is unavailable."))
            return
        }
        button(tr("Create a room", "إنشاء غرفة"), GroupAction.CREATE, primary = true); button(tr("Join a room", "الانضمام إلى غرفة"), GroupAction.JOIN)
        button(if(c.library.home == null) tr("Register / sign in", "تسجيل أو دخول") else tr("My profile", "ملفي الشخصي"), GroupAction.OPEN_PROFILE); button(tr("Enable notifications", "تفعيل الإشعارات"), GroupAction.ENABLE_ALERTS)
        button(tr("Quick Spin", "اختيار سريع"), GroupAction.QUICK_SPIN); button(tr("Restaurant library", "المطاعم والقوائم"), GroupAction.OPEN_LIBRARY)
        button("English", GroupAction.SET_LANGUAGE, "en", enabled = ar); button("العربية", GroupAction.SET_LANGUAGE, "ar", enabled = !ar)
        card("about", tr("Your table, always here", "مجموعتك دائماً هنا"), tr("Join a room once. Return for tomorrow's order. Downloaded receipts stay with you offline.", "انضم مرة واحدة وعد للطلبات القادمة. تبقى الإيصالات المحفوظة متاحة دون إنترنت."))
        c.library.home?.invitations?.forEach { invite -> card("invitation:${invite.id}", "Join ${invite.roomName}", "${invite.invitedBy} invited you to order #${invite.orderNumber}", "Invitation", listOf(GroupButton(ui("Join"), GroupAction.ACCEPT_INVITE, invite.id, primary = true))) }
        c.library.sessions.sortedByDescending { c.library.snapshots[it.roomId]?.room?.createdAt ?: 0L }.forEach { s ->
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
            button(ui("Continue with Google"), GroupAction.GOOGLE_SIGN_IN, primary = true)
            button(tr("Sign in", "تسجيل الدخول"), GroupAction.SIGN_IN, primary = true); button(tr("Reset password", "إعادة تعيين كلمة المرور"), GroupAction.RESET_PASSWORD)
        }
        field(GroupFieldKey.NAME, tr("Profile name", "الاسم في الملف الشخصي")); field(GroupFieldKey.PROFILE_PHONE, tr("Phone · with country code", "رقم الهاتف الإماراتي"))
        field(GroupFieldKey.PHOTO, tr("Profile photo", "صورة الملف الشخصي"))
        field(GroupFieldKey.DISCOVERABLE, tr("Allow people on this hub to invite me", "السماح لمستخدمي الخادم بدعوتي"), toggle = true)
        field(GroupFieldKey.AANI, tr("Receive payments with Aani", "استلام الدفعات عبر آني"), toggle = true)
        field(GroupFieldKey.ACCOUNT_HOLDER, tr("Account holder · optional", "صاحب الحساب · اختياري"))
        if(!c.flag(GroupFieldKey.AANI)) field(GroupFieldKey.ACCOUNT_BANK, tr("Bank name · optional", "اسم البنك · اختياري"))
        field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) ui("UAE mobile registered with Aani") else ui("UAE IBAN · optional"))
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
            card("cloud", ui("Firebase storage"), c.library.home!!.cloudStatus)
            button("Connect hub storage to my Firebase account", GroupAction.ENABLE_CLOUD)
            button(tr("Sign out", "تسجيل الخروج"), GroupAction.SIGN_OUT)
        }
        card("profile-privacy", tr("Your receiving details", "بيانات استلام أموالك"), tr("Only you can read your cloud profile. Your receiving account is shared with participants when you are selected and choose to share it.", "ملفك السحابي خاص بك. تُشارك بيانات استلام الأموال عندما يتم اختيارك وتوافق على مشاركتها."))
    }
    private fun people() {
        title = tr("Invite your people", "ادعُ أصحابك"); subtitle = tr("People who signed in on this hub and enabled invitations.", "الأشخاص المسجلون في هذا الخادم والذين سمحوا بالدعوات.")
        c.library.home?.people?.forEach { person -> card("person:${person.userId}", person.name, actions = listOf(GroupButton(ui("Invite"), GroupAction.INVITE_PERSON, person.userId))) }
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
        card("trust", ui("Smart hybrid mode"), ui("Internet rooms work from any network. A nearby hub keeps live commands and updates on your local network to reduce server traffic."))
    }
    private fun setup() {
        title = if(c.joinMode && !c.nextOrder) "Join your people" else if(c.nextOrder) "A fresh order" else "Create your room"
        subtitle = "Your membership stays saved for future meals."
        if (!c.nextOrder) field(GroupFieldKey.NAME, tr("Your name", "اسمك"))
        if(c.joinMode && !c.nextOrder) {
            field(GroupFieldKey.ROOM_CODE, tr("Six-digit room code", "رمز الغرفة من ستة أرقام"))
            button("Request to join", GroupAction.JOIN_ROOM, primary = true); return
        }
        if (!c.nextOrder) field(GroupFieldKey.ROOM_NAME, tr("Room name", "اسم الغرفة"))
        if (c.selectedRestaurant == null) {
            field(GroupFieldKey.RESTAURANT_NAME, "Restaurant for an open order")
            button("Let everyone type their own items", GroupAction.USE_OPEN_ORDER)
        }
        field(GroupFieldKey.RESTAURANT_POLL, "Let the room vote for the restaurant", toggle = true)
        if (c.flag(GroupFieldKey.RESTAURANT_POLL)) {
            val choices = c.library.restaurants.filter { it.restaurant.id in c.pollRestaurantIds }
            card("poll-choices", tr("${choices.size} restaurants in the poll", "${choices.size} مطاعم في التصويت"), choices.joinToString(" · ") { it.restaurant.localizedName(language) })
            button(tr("Choose poll restaurants", "اختر مطاعم التصويت"), GroupAction.OPEN_POLL_RESTAURANTS)
        }
        card(
            "restaurant-choice-mode",
            if(c.flag(GroupFieldKey.RESTAURANT_POLL)) "Live restaurant poll" else "Restaurant chosen by organizer",
            if(c.flag(GroupFieldKey.RESTAURANT_POLL)) "Everyone votes in the room. Sandwich ordering opens after you finish the poll."
            else "The selected restaurant and its menu are available as soon as the room opens.",
        )
        field(GroupFieldKey.EXPECTED_NAMES, "Expected people today · comma separated")
        field(GroupFieldKey.DELIVERY, "Delivery to us", toggle = true)
        if(c.flag(GroupFieldKey.DELIVERY)) field(GroupFieldKey.DESTINATION, tr("Delivery address · optional; otherwise the selected orderer arranges it", "عنوان التوصيل · اختياري؛ وإلا يتولى الشخص المختار ترتيبه"))
        feeFields()
        card("restaurant", c.selectedRestaurant?.restaurant?.localizedName(language) ?: tr("Choose a restaurant", "اختر مطعماً"), c.selectedRestaurant?.restaurant?.menu?.items?.size?.let { tr("$it menu items", "$it صنفاً") } ?: tr("Create, import or select a saved menu.", "أنشئ أو استورد أو اختر قائمة محفوظة."))
        if (!c.flag(GroupFieldKey.RESTAURANT_POLL)) button("Choose restaurant", GroupAction.OPEN_LIBRARY)
        button(if(c.nextOrder) tr("Start next order", "بدء الطلب التالي") else "Create room", GroupAction.CREATE_ROOM, primary = true)
    }
    private fun library() {
        title = tr("Choose a restaurant", "اختر مطعماً"); subtitle = tr("Search by name, emirate, area, cuisine or meal.", "ابحث بالاسم أو الإمارة أو المنطقة أو نوع المطبخ أو الوجبة.")
        if (c.choosingPollRestaurants) {
            title = tr("Choose poll restaurants", "اختر مطاعم التصويت")
            subtitle = tr("${c.pollRestaurantIds.size}/12 selected · Choose at least 2. Only your selections enter the poll.", "${c.pollRestaurantIds.size}/12 محدد · اختر مطعمين على الأقل. المطاعم المختارة فقط تدخل التصويت.")
            button(tr("Use selected restaurants", "استخدم المطاعم المختارة"), GroupAction.CONFIRM_POLL_RESTAURANTS, primary = true, enabled = c.pollRestaurantIds.size in 2..12)
        }
        field(GroupFieldKey.RESTAURANT_SEARCH, tr("Search restaurants", "ابحث عن مطعم"))
        val allRestaurants = c.library.restaurants.map { it.restaurant }
        val emirates = allRestaurants.mapNotNull { restaurant ->
            val value = restaurant.emirate.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GroupChoice(value, if(ar && restaurant.emirateAr.isNotBlank()) restaurant.emirateAr else value)
        }.distinctBy { it.value }.sortedBy { it.label }
        choiceField(GroupFieldKey.RESTAURANT_EMIRATE, tr("Emirate", "الإمارة"), listOf(GroupChoice("", tr("All Emirates", "كل الإمارات"))) + emirates)
        val emirate = c.text(GroupFieldKey.RESTAURANT_EMIRATE)
        val areas = allRestaurants.filter { emirate.isBlank() || it.emirate == emirate }.mapNotNull { restaurant ->
            val value = restaurant.area.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GroupChoice(value, if(ar && restaurant.areaAr.isNotBlank()) restaurant.areaAr else value)
        }.distinctBy { it.value }.sortedBy { it.label }
        choiceField(GroupFieldKey.RESTAURANT_AREA, tr("Area", "المنطقة"), listOf(GroupChoice("", tr("All areas", "كل المناطق"))) + areas)
        choiceField(GroupFieldKey.RESTAURANT_MEAL, tr("Meal", "الوجبة"), listOf(
            GroupChoice("", tr("Any meal", "كل الوجبات")),
            GroupChoice("breakfast", tr("Breakfast", "فطور")),
            GroupChoice("lunch", tr("Lunch", "غداء")),
            GroupChoice("dinner", tr("Dinner", "عشاء")),
        ))
        if (!c.choosingPollRestaurants) {
        button(tr("Add restaurant", "إضافة مطعم"), GroupAction.NEW_RESTAURANT, primary = c.library.restaurants.isEmpty() && c.importPreview == null); button("Import menu JSON", GroupAction.IMPORT_MENU)
        field(GroupFieldKey.JSON_MENU, "Or paste a menu JSON file", multiline = true)
        if(c.text(GroupFieldKey.JSON_MENU).isNotBlank()) button("Preview JSON", GroupAction.PREVIEW_IMPORT)
        c.importPreview?.let { card("preview", "Import ${it.restaurant.name}", "${it.restaurant.menu.items.size} items · ${it.restaurant.currency}\nMatching restaurant IDs update the saved copy. Room menus stay unchanged."); button("Confirm import", GroupAction.CONFIRM_IMPORT, primary = true) }
        }
        val query = c.text(GroupFieldKey.RESTAURANT_SEARCH).trim()
        val area = c.text(GroupFieldKey.RESTAURANT_AREA)
        val meal = c.text(GroupFieldKey.RESTAURANT_MEAL)
        val visible = c.library.restaurants.filter { export ->
            val restaurant = export.restaurant
            val matchesQuery = query.isBlank() || listOf(restaurant.name, restaurant.nameAr, restaurant.branchName, restaurant.branchNameAr, restaurant.emirate, restaurant.emirateAr, restaurant.area, restaurant.areaAr, restaurant.cuisine, restaurant.cuisineAr, restaurant.contact.address.orEmpty()).any { it.contains(query, ignoreCase = true) }
            val matchesEmirate = emirate.isBlank() || restaurant.emirate == emirate
            val matchesArea = area.isBlank() || restaurant.area == area
            val matchesMeal = meal.isBlank() || restaurant.mealTypes.any { it.name.equals(meal, ignoreCase = true) }
            matchesQuery && matchesEmirate && matchesArea && matchesMeal
        }
        visible.forEach { e ->
            val restaurant = e.restaurant
            val selectedEmirate = if(ar && restaurant.emirateAr.isNotBlank()) restaurant.emirateAr else restaurant.emirate
            val selectedArea = if(ar && restaurant.areaAr.isNotBlank()) restaurant.areaAr else restaurant.area
            val location = listOf(selectedEmirate, selectedArea).filter { it.isNotBlank() }.joinToString(" · ")
            val cuisine = if(ar && restaurant.cuisineAr.isNotBlank()) restaurant.cuisineAr else restaurant.cuisine
            val rating = restaurant.googleRating?.let { "★ $it Google" }.orEmpty()
            val detail = listOf(location, cuisine, rating, "${restaurant.menu.items.size} ${tr("items", "صنفاً")}").filter { it.isNotBlank() }.joinToString(" · ")
            if (c.choosingPollRestaurants) {
                val selected = restaurant.id in c.pollRestaurantIds
                card("restaurant:${restaurant.id}", restaurant.localizedName(language), detail, actions = listOf(GroupButton(
                    if (selected) tr("✓ Selected · remove", "✓ محدد · إزالة") else tr("Add to poll", "إضافة للتصويت"),
                    GroupAction.TOGGLE_POLL_RESTAURANT, restaurant.id, enabled = selected || c.pollRestaurantIds.size < 12,
                )))
            } else card("restaurant:${restaurant.id}", restaurant.localizedName(language), detail, actions = listOf(
            GroupButton(tr("Use for order", "استخدام للطلب"), GroupAction.SELECT_RESTAURANT, e.restaurant.id), GroupButton(tr("Edit", "تعديل"), GroupAction.EDIT_RESTAURANT, e.restaurant.id), GroupButton(tr("Share JSON", "مشاركة القائمة"), GroupAction.EXPORT_MENU, e.restaurant.id), GroupButton(tr("Delete saved copy", "حذف النسخة"), GroupAction.DELETE_RESTAURANT, e.restaurant.id, destructive = true))) }
        if (visible.isEmpty() && c.library.restaurants.isNotEmpty()) card("empty-filter", tr("No matching restaurants", "لا توجد مطاعم مطابقة"), tr("Try another name, emirate, area or meal.", "جرّب اسماً أو إمارة أو منطقة أو وجبة أخرى."))
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
        card("tax-treatment", ui("How does the restaurant charge tax?"), when(tax) {
            TaxTreatment.INCLUDED -> "Tax is included in menu prices."
            TaxTreatment.ADDED -> "Tax is added to menu prices at the rate below."
            TaxTreatment.UNSPECIFIED -> "Choose the restaurant's tax treatment before placing an order."
        }, actions = listOf(
            GroupButton(ui("Tax included in prices"), GroupAction.SELECT_TAX_TREATMENT, TaxTreatment.INCLUDED.name, enabled = tax != TaxTreatment.INCLUDED),
            GroupButton(ui("Tax added to prices"), GroupAction.SELECT_TAX_TREATMENT, TaxTreatment.ADDED.name, enabled = tax != TaxTreatment.ADDED),
        ))
        if(tax == TaxTreatment.ADDED) field(GroupFieldKey.TAX_RATE, "Tax rate · percent")
        field(GroupFieldKey.MENU_ITEM_NAME, "New item name")
        field(GroupFieldKey.MENU_ITEM_PRICE, "New item price"); button("Add item", GroupAction.ADD_MENU_ITEM)
        val previewCurrency = c.text(GroupFieldKey.CURRENCY).trim().uppercase().takeIf { it in Money.currencies } ?: c.editingRestaurant?.restaurant?.currency ?: "AED"
        c.editingRestaurant?.restaurant?.menu?.items?.forEach { i -> card("menu-editor:${i.id}", i.name, Money.format(i.basePriceMinor, previewCurrency), actions = listOf(GroupButton(ui("Remove item"), GroupAction.REMOVE_MENU_ITEM, i.id, destructive = true))) }
        button(if(editingRoom) "Save & update this order" else tr("Save restaurant", "حفظ المطعم"), GroupAction.SAVE_RESTAURANT, primary = true)
    }
    private fun room() {
        val r = c.reply?.room ?: run { title = "Connecting…"; button("Retry connection", GroupAction.REFRESH); return }
        title = r.name; subtitle = "${tr("Order", "الطلب")} #${r.orderNumber} · ${stage(r.phase)} · ${r.restaurant.localizedName(language)}"
        val me = r.members.singleOrNull { it.id == c.me() } ?: return
        val owner = c.me() == r.ownerId; val payer = c.me() == r.payerId
        if(!me.approved) {
            card("sync-membership", ui("Reconnect to update room access"), ui("Refresh the room. If access is still unavailable, update the room server."))
            button(tr("Refresh", "تحديث"), GroupAction.REFRESH); return
        }
        if(!me.guest && !me.participating && r.phase != RoomPhase.LOBBY) {
            card("viewing-order", ui("You’re in the room"), ui("You’re viewing this order. You can join when the next order opens."))
        }
        button("Invite people", GroupAction.SHARE_ROOM); if(owner && r.phase == RoomPhase.LOBBY && c.sameHub(c.library.identityHub, c.session?.hub)) button(tr("Invite registered people", "دعوة مستخدمين مسجلين"), GroupAction.OPEN_PEOPLE); button("Receipts", GroupAction.OPEN_RECEIPTS); button("Past orders", GroupAction.OPEN_HISTORY)
        button(tr("Save restaurant", "حفظ المطعم"), GroupAction.SAVE_ROOM_RESTAURANT)
        if((owner || payer) && r.phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW)) button(tr("Edit restaurant details", "تعديل بيانات المطعم"), GroupAction.EDIT_ROOM_RESTAURANT)
        if (!c.online) card("offline", "Your saved order is available", "Reconnect to the same local hub to make changes. Cached payment status may have changed.", actions = listOf(GroupButton("Reconnect", GroupAction.REFRESH)))
        r.members.filterNot { it.removed }.forEach { m ->
            val actions = mutableListOf<GroupButton>()
            if(owner && m.id != r.ownerId && r.phase == RoomPhase.LOBBY) { actions += GroupButton(ui("Remove from room"), GroupAction.REMOVE, m.id, destructive = true); if(m.approved && !m.guest) actions += GroupButton(ui("Make organizer"), GroupAction.HANDOVER, m.id) }
            card("member:${m.id}", m.name, listOf(if(m.id == r.ownerId) "Organizer" else if(m.id == r.payerId) "Payer" else if(m.guest) "Watching" else "Member", if(c.serverNow() - m.lastSeen < 15000) "Connected" else "Away").joinToString(" · "), if(!m.guest && !m.participating) "Skipping this order" else if(m.eligible) "Joined · Can be selected" else "Joined", actions)
        }
        if(owner) r.expectedNames.filter { name -> r.orderingMembers.none { it.name.equals(name.trim(), true) } }.forEach { card("invite:$it", it, "Expected · not participating today", actions = if(r.phase == RoomPhase.LOBBY) listOf(GroupButton(ui("Remove invitation"), GroupAction.REMOVE, "invite:$it")) else emptyList()) }
        if(owner && r.activeMembers.any { it.id != c.me() && !it.guest }) button("Request a user block", GroupAction.OPEN_BLOCK_REQUEST)
        field(GroupFieldKey.REASON, "Reason for removal, reroll, reopening or adjustment")
        when(r.phase) {
            RoomPhase.LOBBY -> {
                if(!me.guest) {
                    button(if(me.participating) tr("Skip this order", "تخطي هذا الطلب") else tr("Join this order", "الانضمام إلى هذا الطلب"), GroupAction.PARTICIPATE, (!me.participating).toString())
                    if(me.participating && !me.eligible && r.pastSpins.any { it.winnerId == me.id }) card("declined-duty", ui("You're still part of this meal"), "You declined payment duty, so your name stays out of the next wheel while you can still add food.")
                }
                if(r.restaurantPollOpen) {
                    card("poll-heading", ui("Choose today's restaurant"), "Registered members vote live. The organizer closes the poll, then everyone can add sandwiches before the spin.")
                    val myVote = r.restaurantVotes.firstOrNull { it.memberId == c.me() }?.restaurantId
                    val canVote = !me.guest && me.participating && c.library.identityToken.isNotEmpty()
                    r.restaurantOptions.forEach { option ->
                        val count = r.restaurantVotes.count { it.restaurantId == option.id }
                        val voters = r.restaurantVotes.filter { it.restaurantId == option.id }.mapNotNull { vote -> r.members.firstOrNull { it.id == vote.memberId }?.name }
                        card("poll:${option.id}", option.localizedName(language), "${option.menu.items.size} ${tr("sandwiches/items", "سندويشات وأصناف")}${if(voters.isEmpty()) tr(" · No votes yet", " · لا أصوات بعد") else " · ${voters.joinToString()}"}",
                            tr("$count ${if(count == 1) "vote" else "votes"}", "$count أصوات"), if(canVote) listOf(GroupButton(if(myVote == option.id) tr("Your vote", "صوتك") else tr("Vote", "تصويت"), GroupAction.VOTE_RESTAURANT, option.id, primary = myVote != option.id, enabled = myVote != option.id)) else emptyList())
                    }
                    if(!canVote && !me.guest) card("poll-sign-in", ui("Sign in to vote"), ui("Restaurant voting is available to registered members."))
                    if(owner) button("Finish poll & use leading restaurant", GroupAction.FINALIZE_RESTAURANT, primary = true, enabled = r.restaurantVotes.isNotEmpty())
                } else if(!me.guest && me.participating) foodEditor(r, primary = false)
                if(owner) {
                    fields += GroupField(GroupFieldKey.PAYER_MODE, ui("Choose the ordering person"), c.text(GroupFieldKey.PAYER_MODE).ifBlank { "wheel" }, choices = listOf(GroupChoice("wheel", ui("Use the wheel")), GroupChoice("direct", ui("Choose person directly"))))
                    if (c.text(GroupFieldKey.PAYER_MODE) == "direct") {
                        fields += GroupField(GroupFieldKey.PAYER_CHOICE, ui("Ordering person"), c.text(GroupFieldKey.PAYER_CHOICE).takeIf { choice -> r.orderingMembers.any { it.id == choice } } ?: r.orderingMembers.firstOrNull()?.id.orEmpty(), choices = r.orderingMembers.map { GroupChoice(it.id, it.name) })
                        button("Choose person directly", GroupAction.SELECT_PAYER, primary = true, enabled = !r.restaurantPollOpen && r.orderingMembers.isNotEmpty())
                    } else {
                        val blocker = runCatching { RoomRules.spinReady(r) }.exceptionOrNull()?.message
                        button("Spin together", GroupAction.PREPARE_SPIN, primary = !r.restaurantPollOpen, enabled = blocker == null)
                        if(blocker != null) card("spin-next-step", ui("Before spinning"), blocker)
                    }
                    button("Cancel today's order", GroupAction.CANCEL)
                } else if(!r.restaurantPollOpen && me.participating) card("ready-next-step", ui("You are joined"), ui("Add or change your sandwiches while waiting for the organizer to start the shared spin."))
            }
            RoomPhase.PREPARING_SPIN -> { card("prepare", ui("Getting everyone in sync"), ui("Waiting for participating phones to acknowledge the countdown.")); if(owner) button("Stop waiting", GroupAction.ABORT_SPIN); if(!me.guest && me.participating) foodEditor(r, primary = false) }
            RoomPhase.SPINNING -> { card("spinning", ui("One spin. One result."), ui("Stay here to watch the wheel together.")); if(!me.guest && me.participating) foodEditor(r, primary = false) }
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
                accountCard()
                if(!me.guest && me.participating) foodEditor(r, primary = true)
                if(payer) c.reply!!.receipts.forEach { receipt ->
                    card("collected-order:${receipt.memberId}", receipt.name,
                        receipt.lines.joinToString("\n") { "${it.quantity} × ${localizedReceiptDescription(r, it, language)} · ${Money.format(it.amount, receipt.currency)}" },
                        receipt.totalText)
                }
                if(payer) itemPricingCards(r)
                val nextStep = when {
                    awaitingFood.isNotEmpty() -> "Waiting for food orders from ${awaitingFood.joinToString { it.name }}. Each person must submit their food or choose No food this time."
                    needsAccount -> "Waiting for ${r.members.single { it.id == r.payerId }.name} to share a receiving account."
                    (owner || payer) && !progress?.reviewBlocker.isNullOrBlank() -> requireNotNull(progress).reviewBlocker
                    owner || payer -> "Everyone has submitted. Send the order to the restaurant and record the expected arrival."
                    else -> "Waiting for ${r.members.single { it.id == r.payerId }.name} to send the order to the restaurant."
                }
                card("order-next-step", tr("Next step", "الخطوة التالية"), nextStep)
                if(payer) append(GroupSettlementPresentation(c).review())
                if(owner || payer) { feeFields(); button("Update fees and reopen", GroupAction.SET_FEES); button("Reopen ordering", GroupAction.REOPEN) }
                if(owner) button("Cancel today's order", GroupAction.CANCEL)
            }
            RoomPhase.REVIEW -> {
                accountCard(); if(payer) itemPricingCards(r); append(GroupSettlementPresentation(c).review())
                button("Order details", GroupAction.OPEN_RECEIPTS)
            }
            RoomPhase.PLACED, RoomPhase.FULFILLED -> {
                accountCard(); if(payer) itemPricingCards(r); append(GroupSettlementPresentation(c).settlement())
                button("Order details", GroupAction.OPEN_RECEIPTS)
                transferCards()
            }
            RoomPhase.ARCHIVED, RoomPhase.CANCELLED -> { card("complete", "Your room stays open", "This order is ${if(r.phase == RoomPhase.CANCELLED) "cancelled" else "complete"}. Everyone keeps their membership for the next meal."); if(owner) button("Start a new order", GroupAction.NEXT_ORDER, primary = true) }
        }
        val contact = r.restaurant.contact.phoneE164 ?: r.restaurant.contact.whatsappE164 ?: ""
        card("contact", r.restaurant.localizedName(language), "$contact\n${r.restaurant.contact.address ?: ""}\n${r.destination}", actions = (if(payer) listOf(
            GroupButton(tr("Share order via WhatsApp", "مشاركة الطلب عبر واتساب"), GroupAction.SHARE_ORDER_WHATSAPP, primary = r.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)),
            GroupButton(tr("Copy restaurant-ready list", "نسخ قائمة الطلب للمطعم"), GroupAction.SHARE_RESTAURANT_ORDER),
        ) else emptyList()) + listOf(
            GroupButton(ui("Copy phone number"), GroupAction.COPY_RESTAURANT_PHONE, enabled = contact.isNotBlank()),
            GroupButton(tr("Call restaurant", "الاتصال بالمطعم"), GroupAction.CALL_RESTAURANT, enabled = contact.isNotBlank()),
        ))
    }
    private fun itemPricingCards(r: Room) {
        card("pricing-help", tr("Item prices", "أسعار الأصناف"),
            tr("Submitted orders stay submitted. Members do not need to approve price changes.", "الطلبات المرسلة بتفضل متسجلة. مش محتاجين موافقة جديدة على تغيير الأسعار."))
        r.carts.filter { cart -> r.orderingMembers.any { it.id == cart.memberId } }.forEach { cart ->
            val priced = Billing.lines(r.restaurant, cart)
            cart.lines.forEachIndexed { index, line ->
                val receiptLine = priced[index]
                val unpriced = line.description.isNotEmpty() && line.unitPrice == null
                card("price:${cart.memberId}:${line.id}", "${r.members.single { it.id == cart.memberId }.name} · ${line.quantity} × ${localizedReceiptDescription(r, receiptLine, language)}", line.notes,
                    if (unpriced) tr("Awaiting price", "بانتظار التسعير") else Money.format(receiptLine.amount, r.restaurant.currency),
                    listOf(GroupButton(tr("Edit price", "تعديل السعر"), GroupAction.OPEN_PRICE_ITEM, "${cart.memberId}:${line.id}")))
            }
        }
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
        val sandwichCategoryIds = r.restaurant.menu.categories.filter { it.name.contains("sandwich", true) || it.nameAr.contains("سند") }.map { it.id }.toSet()
        val availableCategories = r.restaurant.menu.categories.filter { category -> r.restaurant.menu.items.any { it.available && it.categoryId == category.id } }
            .sortedWith(compareBy<MenuCategory> { if(it.id in sandwichCategoryIds) 0 else 1 }.thenBy { it.sortOrder })
        val choices = availableCategories.map { GroupChoice("category:${it.id}", it.localizedName(language)) } + GroupChoice("all", tr("All categories", "كل الأقسام"))
        val selectedCategory = c.text(GroupFieldKey.MENU_CATEGORY).takeIf { value -> choices.any { it.value == value } }
            ?: availableCategories.firstOrNull { it.id in sandwichCategoryIds }?.let { "category:${it.id}" } ?: "all"
        fields += GroupField(GroupFieldKey.MENU_CATEGORY, tr("Menu category", "قسم القائمة"), selectedCategory, choices = choices)
        val menuItems = r.restaurant.menu.items.filter { item -> item.available && (selectedCategory == "all" || selectedCategory == "category:${item.categoryId}") && (query.isBlank() || listOf(item.name, item.nameAr, item.description, item.descriptionAr).any { it.contains(query, ignoreCase = true) }) }
        if(menuItems.isEmpty()) card("menu-empty", tr("No matching items", "لا توجد أصناف مطابقة"), tr("Try a different name, clear the search or change the menu category.", "جرّب اسماً آخر أو امسح البحث أو غيّر قسم القائمة."))
        menuItems.sortedBy { if(it.categoryId in sandwichCategoryIds) 0 else 1 }.forEach { i ->
            val simple = i.variants.isEmpty() && i.optionGroupIds.isEmpty()
            val quantity = c.myCart().lines.filter { it.itemId == i.id }.sumOf { it.quantity }
            val actions = if(!i.available) emptyList() else if(simple) listOf(
                GroupButton(tr("+ Add", "+ إضافة"), GroupAction.QUICK_ADD_ITEM, i.id),
                GroupButton(tr("Quantity / notes", "الكمية والملاحظات"), GroupAction.OPEN_ITEM, i.id),
            ) else listOf(GroupButton(tr("Choose size / extras", "اختيار الحجم والإضافات"), GroupAction.OPEN_ITEM, i.id))
            val categoryName = r.restaurant.menu.categories.firstOrNull { it.id == i.categoryId }?.localizedName(language).orEmpty()
            val variant = i.variants.firstOrNull()
            card("menu:${i.id}", i.localizedName(language), listOf(categoryName, i.localizedDescription(language)).filter { it.isNotBlank() }.joinToString(" · "),
                Money.format(variant?.priceMinor ?: i.basePriceMinor, r.restaurant.currency) + (variant?.let { " · ${it.localizedName(language)}" } ?: "") + if(quantity > 0) tr(" · $quantity in your order", " · $quantity في طلبك") else "", actions)
        }
        cart()
        if(!c.myCart().submitted) button(if(c.myCart().lines.isEmpty()) "No food this time" else "Submit my food order", GroupAction.SUBMIT_CART, primary = primary)
        else card("cart-submitted", ui("Your food order is saved"), ui("You can edit it before or after the spin. Submit again after making changes."))
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
                GroupButton(ui("Delete saved account"), GroupAction.DELETE_ACCOUNT, a.id, destructive = true),
            ))
        }
        field(GroupFieldKey.AANI, "Receive with Aani", toggle = true); field(GroupFieldKey.ACCOUNT_HOLDER, tr("Account holder", "صاحب الحساب")); if(!c.flag(GroupFieldKey.AANI)) field(GroupFieldKey.ACCOUNT_BANK, tr("Bank name", "اسم البنك")); field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) ui("UAE mobile registered with Aani") else ui("UAE IBAN"))
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
        data class BalanceRow(val row: SnapshotRow, val receipt: Receipt, val person: String, val receive: Boolean)
        val balances = payableRows.flatMap { row ->
            if(row.room.payerId == row.session.memberId) row.reply.receipts.filter { it.memberId != row.session.memberId && it.balance != 0L }
                .map { BalanceRow(row, it, it.name, it.balance > 0) }
            else listOfNotNull(row.receipt?.takeIf { it.balance != 0L }?.let {
                BalanceRow(row, it, row.room.members.firstOrNull { member -> member.id == row.room.payerId }?.name ?: tr("Selected orderer", "الشخص المختار"), it.balance < 0)
            })
        }
        listOf(false, true).forEach { receive ->
            val entries = balances.filter { it.receive == receive }
            card("${prefix}wallet-direction:$receive", if(receive) tr("I need to receive", "مبالغ أحتاج إلى استلامها") else tr("I need to pay", "مبالغ يجب علي دفعها"),
                if(entries.isEmpty()) tr("No outstanding payments", "لا توجد دفعات مستحقة") else tr("Payments by person", "الدفعات حسب الشخص"), Money.format(if(receive) toReceive else toPay, "AED"))
            entries.forEach { (row, receipt, person, _) ->
                val pending = row.room.transfers.firstOrNull { it.memberId == receipt.memberId && it.status == TransferStatus.DECLARED }
                val status = pending?.let { tr("${Money.format(it.amount, receipt.currency)} sent · awaiting recipient approval", "تم إرسال ${Money.format(it.amount, receipt.currency)} · بانتظار موافقة المستلم") } ?: ""
                val value = "${row.room.id}:${receipt.memberId}"
                val target = "${row.room.id}|${receipt.memberId}"
                val payer = row.room.payerId == row.session.memberId
                val actions = mutableListOf<GroupButton>()
                if (pending != null && if (pending.refund) pending.memberId == row.session.memberId else payer) {
                    actions += GroupButton(ui("Confirm received"), GroupAction.WALLET_CONFIRM, target, primary = true)
                    actions += GroupButton(ui("Not received"), GroupAction.WALLET_REJECT, target)
                } else if (pending == null && row.room.restaurantPaid) {
                    if (payer && receipt.balance < 0) actions += GroupButton(ui("Mark refund sent"), GroupAction.WALLET_REFUND, target, primary = true)
                    if (!payer && receipt.balance > 0) actions += GroupButton(ui("Mark paid") + " · " + Money.format(receipt.balance, receipt.currency), GroupAction.WALLET_PAY, target, primary = true)
                }
                if (row.room.account != null) actions += GroupButton(ui("Copy payment details"), GroupAction.WALLET_COPY, target)
                actions += GroupButton(ui("Order and payment details"), GroupAction.RESUME, row.room.id)
                val account = row.room.account?.let { "${it.holder} · ${it.bank}\n${it.identifier}" }.orEmpty()
                card("${prefix}wallet:$value", person,
                    listOf("${row.room.name} · #${row.room.orderNumber} · ${row.room.restaurant.localizedName(language)}",
                        receiptSummary(receipt), "${ui("Order")} ${receipt.totalText} · ${ui("Paid")} ${Money.format(receipt.paid, receipt.currency)}",
                        account, status).filter { it.isNotBlank() }.joinToString("\n"), Money.format(kotlin.math.abs(receipt.balance), receipt.currency), actions)

            }
        }
        card("${prefix}payment-history", ui("Payment history"), ui("Order totals and confirmed payments, newest first."))
        data class PaymentRecord(val roomId: String, val roomName: String, val number: Long, val restaurant: String, val at: Long, val receipt: Receipt)
        val records = rows.flatMap { row ->
            val past = row.reply.history.mapNotNull { order -> order.receipts.firstOrNull { it.memberId == row.session.memberId }?.let {
                PaymentRecord(row.room.id, row.room.name, order.number, order.restaurantName, order.completedAt, it)
            } }
            val current = row.receipt?.takeIf { row.room.phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED, RoomPhase.ARCHIVED) }?.let {
                PaymentRecord(row.room.id, row.room.name, row.room.orderNumber, row.room.restaurant.localizedName(language), row.room.updatedAt, it)
            }
            listOfNotNull(current) + past
        }.distinctBy { "${it.roomId}:${it.number}" }.sortedByDescending { it.at }
        if(records.isEmpty()) card("${prefix}payment-history-empty", ui("No payment history yet"))
        records.take(if(c.page == GroupPage.HOME) 3 else 30).forEach { record ->
            card("${prefix}payment-history:${record.roomId}:${record.number}", "${record.restaurant} · #${record.number}",
                "${record.roomName} · ${timeLabel(record.at)}\n${ui("Order")} ${record.receipt.totalText} · ${ui("Paid")} ${Money.format(record.receipt.paid, record.receipt.currency)}",
                actions = listOf(GroupButton(ui("Order and payment details"), GroupAction.RESUME, record.roomId)))
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
            val actions = mutableListOf(GroupButton(ui("Share receipt"), GroupAction.SHARE_RECEIPT, prefix + receipt.memberId))

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
        return restaurantReadyText(r, c.reply!!.receipts, language, c.text(GroupFieldKey.REFERENCE).ifBlank { r.restaurantReference })
    }
    private fun accountCard(account: ReceivingAccount? = c.reply?.room?.account, id: String = "account", label: String = "Send to") {
        account?.let { a -> card(id, "$label ${a.holder}", "${ui(if(a.method == PaymentMethod.AANI) "Aani · UAE mobile number" else "Bank transfer · IBAN")}\n${a.bank}\n${a.identifier}\n${a.currency}", actions = if(id == "account") listOf(GroupButton(tr("Copy payment details", "نسخ بيانات الدفع"), GroupAction.COPY_PAYMENT_DETAILS)) else emptyList()) }
    }
    private fun transferCards() { val r = c.reply?.room ?: return; r.transfers.forEach { t ->
        val actions = if(t.status.name == "DECLARED") when { t.refund && t.memberId == c.me() -> listOf(GroupButton(ui("Confirm refund received"), GroupAction.CONFIRM_REFUND, t.id), GroupButton(ui("Reject refund claim"), GroupAction.REJECT_TRANSFER, t.id)); !t.refund && r.payerId == c.me() -> listOf(GroupButton(tr("Approve · money received", "موافقة · استلمت المبلغ"), GroupAction.CONFIRM_TRANSFER, t.id), GroupButton("Reject claim", GroupAction.REJECT_TRANSFER, t.id)); else -> emptyList() } else emptyList()
        card("transfer:${t.id}", "${if(t.refund) "Refund" else "Transfer"} · ${Money.format(t.amount, r.restaurant.currency)}", "${r.members.single { it.id == t.memberId }.name} · ${t.reference}", t.status.name.lowercase(), actions)
    }; if(r.payerId == c.me()) c.reply?.receipts?.filter { GroupSettlementPresentation.refundAvailable(r, it) }?.forEach { button("Record refund to ${it.name}", GroupAction.DECLARE_REFUND, it.memberId) } }
    private fun feeFields() {
        val delivery = if(c.page == GroupPage.SETUP) c.flag(GroupFieldKey.DELIVERY) else c.reply?.room?.deliveryMode == true
        if(delivery) card("automatic-delivery", tr("Automatic delivery", "حساب التوصيل تلقائياً"), tr("AED 5 split equally for 1–4 people with food; AED 1 each for 5 or more. Your own food counts too. Every receipt includes its share.", "٥ دراهم بالتساوي بين ١–٤ أشخاص لديهم طعام، ودرهم لكل شخص عند ٥ أشخاص فأكثر. يُحسب طلبك أيضاً وتضاف الحصة لكل إيصال."))
        else { field(GroupFieldKey.DELIVERY_FEE, tr("Delivery fee", "رسوم التوصيل")); field(GroupFieldKey.PROPORTIONAL, tr("Split delivery in proportion to food", "تقسيم التوصيل بنسبة قيمة الطعام"), toggle = true) }
        if(delivery && c.page != GroupPage.SETUP && c.reply?.room?.fees?.automaticDelivery == false) card("delivery-update", tr("Saved delivery fees", "رسوم التوصيل المحفوظة"), tr("Update fees to apply automatic delivery and request new total confirmations.", "حدّث الرسوم لتطبيق التوصيل التلقائي وإعادة تأكيد الإجمالي."))
        field(GroupFieldKey.SERVICE_FEE, tr("Service fee", "رسوم الخدمة")); field(GroupFieldKey.DISCOUNT, tr("Shared discount", "الخصم المشترك"))
    }
    private fun stage(phase: RoomPhase): String = when(phase) { RoomPhase.LOBBY -> tr("Gathering", "تجمع الأعضاء"); RoomPhase.PREPARING_SPIN -> tr("Getting ready", "جارٍ الاستعداد"); RoomPhase.SPINNING -> ui("Spinning"); RoomPhase.ACCEPTING -> ui("Accepting duty"); RoomPhase.COLLECTING -> ui("Choose food"); RoomPhase.REVIEW -> ui("Ready for the restaurant"); RoomPhase.PLACED -> tr("Order placed", "تم إرسال الطلب"); RoomPhase.FULFILLED -> tr("Food arrived", "وصل الطعام"); RoomPhase.ARCHIVED -> tr("Complete", "مكتمل"); RoomPhase.CANCELLED -> tr("Cancelled", "ملغى") }
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
