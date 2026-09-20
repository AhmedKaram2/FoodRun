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
            GroupPage.CUSTOM_ITEM -> { title = if(c.editingCartLineId == null) "What would you like?" else "Change your item"; subtitle = "Add one item at a time. The selected person adds its price later."; field(GroupFieldKey.CUSTOM_NAME, "Food item"); field(GroupFieldKey.QUANTITY, "Quantity"); field(GroupFieldKey.NOTE, "Notes / extras"); button(if(c.editingCartLineId == null) "Add to my order" else "Update my order", GroupAction.ADD_CUSTOM_ITEM, primary = true) }
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
        if (c.library.pending != null && !c.busy) buttons.add(0, GroupButton("Retry saved request", GroupAction.RETRY, primary = true))
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
        card("about", "Your table, always here", "Join a room once. Return for tomorrow's order. Downloaded receipts stay with you offline.")
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
    }
    private fun profile() {
        title = if(c.library.home == null) tr("Your Food Run account", "حساب فود رن") else tr("Your profile", "ملفك الشخصي")
        subtitle = tr("Use your existing Intrvioo account, or register here.", "استخدم حساب إنترفيوو أو سجل من هنا.")
        button("English", GroupAction.SET_LANGUAGE, "en", enabled = ar); button("العربية", GroupAction.SET_LANGUAGE, "ar", enabled = !ar)
        if(c.library.home == null) {
            field(GroupFieldKey.EMAIL, "Email"); field(GroupFieldKey.PASSWORD, "Password")
            button("Sign in", GroupAction.SIGN_IN, primary = true); button("Reset password", GroupAction.RESET_PASSWORD)
        }
        field(GroupFieldKey.NAME, "Profile name"); field(GroupFieldKey.PROFILE_PHONE, "Phone · with country code")
        field(GroupFieldKey.PHOTO, "Profile photo")
        field(GroupFieldKey.DISCOVERABLE, "Allow people on this hub to invite me", toggle = true)
        field(GroupFieldKey.AANI, "Receive payments with Aani", toggle = true)
        field(GroupFieldKey.ACCOUNT_HOLDER, "Account holder · optional")
        if(!c.flag(GroupFieldKey.AANI)) field(GroupFieldKey.ACCOUNT_BANK, "Bank name · optional")
        field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) "Aani phone number" else "IBAN / account number · optional")
        if(c.library.home == null) button("Create account", GroupAction.REGISTER)
        else {
            button("Save profile", GroupAction.SAVE_PROFILE, primary = true)
            card("cloud", "Firebase storage", c.library.home!!.cloudStatus)
            button("Connect hub storage to my Firebase account", GroupAction.ENABLE_CLOUD)
            button("Sign out", GroupAction.SIGN_OUT)
        }
        card("profile-privacy", "Your receiving details", "Only you can read your cloud profile. Your receiving account is shared with participants when you are selected and choose to share it.")
    }
    private fun people() {
        title = "Invite your people"; subtitle = "People who signed in on this hub and enabled invitations."
        c.library.home?.people?.forEach { person -> card("person:${person.userId}", person.name, actions = listOf(GroupButton("Invite", GroupAction.INVITE_PERSON, person.userId))) }
        if(c.library.home?.people.isNullOrEmpty()) card("empty", "Bring the crew", "Ask people to sign in on this hub. You can also share the room code.")
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
        if (!c.nextOrder) field(GroupFieldKey.NAME, "Your name")
        if(c.joinMode && !c.nextOrder) {
            field(GroupFieldKey.ROOM_CODE, "Six-digit room code"); field(GroupFieldKey.GUEST, "Watch only — no food or payment", toggle = true)
            button("Request to join", GroupAction.JOIN_ROOM, primary = true); return
        }
        if (!c.nextOrder) field(GroupFieldKey.ROOM_NAME, "Room name")
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
        if(c.flag(GroupFieldKey.DELIVERY)) field(GroupFieldKey.DESTINATION, "Delivery address and contact")
        feeFields()
        card("restaurant", c.selectedRestaurant?.restaurant?.localizedName(language) ?: tr("Choose a restaurant", "اختر مطعماً"), c.selectedRestaurant?.restaurant?.menu?.items?.size?.let { tr("$it menu items", "$it صنفاً") } ?: tr("Create, import or select a saved menu.", "أنشئ أو استورد أو اختر قائمة محفوظة."))
        button("Choose restaurant", GroupAction.OPEN_LIBRARY)
        button(if(c.nextOrder) "Start next order" else "Create room", GroupAction.CREATE_ROOM, primary = true)
    }
    private fun library() {
        title = "Restaurant library"; subtitle = "Your favorites, saved on your device."
        button("Add restaurant", GroupAction.NEW_RESTAURANT, primary = c.library.restaurants.isEmpty() && c.importPreview == null); button("Import menu JSON", GroupAction.IMPORT_MENU)
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
        field(GroupFieldKey.RESTAURANT_NAME, "Restaurant name"); field(GroupFieldKey.BRANCH, "Branch")
        card("room-currency", "Currency", "AED · UAE Dirham")
        field(GroupFieldKey.PHONE, "Restaurant phone")
        field(GroupFieldKey.ADDRESS, "Restaurant address"); field(GroupFieldKey.DELIVERY_FEE, "Default delivery fee")
        field(GroupFieldKey.SERVICE_FEE, "Default service fee")
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
        button(if(editingRoom) "Save & update this order" else "Save restaurant", GroupAction.SAVE_RESTAURANT, primary = true)
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
            card("pending", "Waiting for approval", detail); button("Refresh", GroupAction.REFRESH); return
        }
        button("Invite people", GroupAction.SHARE_ROOM); if(owner && r.phase == RoomPhase.LOBBY && c.sameHub(c.library.identityHub, c.session?.hub)) button("Invite registered people", GroupAction.OPEN_PEOPLE); button("Receipts", GroupAction.OPEN_RECEIPTS); button("Past orders", GroupAction.OPEN_HISTORY)
        button("Save restaurant", GroupAction.SAVE_ROOM_RESTAURANT)
        if((owner || payer) && r.phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW)) button("Edit restaurant details", GroupAction.EDIT_ROOM_RESTAURANT)
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
                    button(if(me.participating) "Skip this order" else "Join this order", GroupAction.PARTICIPATE, (!me.participating).toString())
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
                if(payer) button(if(needsAccount) "Choose receiving account" else "Change receiving account", GroupAction.OPEN_ACCOUNT, primary = needsAccount)
                r.account?.let { card("account-shared", "Receiving account shared", "${it.holder} · ${it.bank}\n•••• ${it.identifier.takeLast(4)}\nEveryone will confirm this recipient with their total.") }
                if(!me.guest && me.participating) foodEditor(r, primary = true)
                if(payer) r.carts.forEach { cart -> cart.lines.filter { it.description.isNotEmpty() }.forEach { line ->
                    card("price:${cart.memberId}:${line.id}", "${r.members.single { it.id == cart.memberId }.name} · ${line.quantity} × ${line.description}", line.notes,
                        line.unitPrice?.let { Money.format(it * line.quantity, r.restaurant.currency) } ?: "Awaiting price",
                        listOf(GroupButton(if(line.unitPrice == null) "Enter unit price" else "Change unit price", GroupAction.OPEN_PRICE_ITEM, "${cart.memberId}:${line.id}")))
                } }
                val nextStep = when {
                    awaitingFood.isNotEmpty() -> "Waiting for food orders from ${awaitingFood.joinToString { it.name }}. Each person must submit their food or choose No food this time."
                    needsAccount -> "Waiting for ${r.members.single { it.id == r.payerId }.name} to share a receiving account."
                    owner && !progress?.reviewBlocker.isNullOrBlank() -> requireNotNull(progress).reviewBlocker
                    owner -> "Everyone has submitted. Review the totals so each person can confirm their share and recipient."
                    else -> "Waiting for ${r.members.single { it.id == r.ownerId }.name} to review everyone's totals. Then you can confirm your share and recipient."
                }
                card("order-next-step", "Next step", nextStep)
                if(owner) button("Review everyone's totals", GroupAction.REVIEW, primary = true, enabled = progress?.canReview ?: (awaitingFood.isEmpty() && !needsAccount))
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
        if(r.restaurant.openOrdering) button(tr("Add a food item", "إضافة صنف"), GroupAction.OPEN_CUSTOM_ITEM, primary = primary)
        r.restaurant.menu.items.forEach { i -> card("menu:${i.id}", i.localizedName(language), i.localizedDescription(language), Money.format(i.basePriceMinor, r.restaurant.currency), if(i.available) listOf(GroupButton(tr("Choose", "اختر"), GroupAction.OPEN_ITEM, i.id)) else emptyList()) }
        cart()
        if(!c.myCart().submitted) button(if(c.myCart().lines.isEmpty()) "No food this time" else "Submit my food order", GroupAction.SUBMIT_CART, primary = primary)
        else card("cart-submitted", "Your food order is saved", "You can edit it before or after the spin. Submit again after making changes.")
    }
    private fun cart() { val r = c.room(); c.myCart().lines.forEach { l -> val name = l.description.ifEmpty { r.restaurant.menu.items.single { it.id == l.itemId }.localizedName(language) }; card("cart:${l.id}", "${l.quantity} × $name", l.notes, if(l.description.isNotEmpty()) l.unitPrice?.let { Money.format(it * l.quantity, r.restaurant.currency) } ?: tr("Awaiting price", "بانتظار السعر") else "", actions = listOf(GroupButton(tr("Edit", "تعديل"), GroupAction.EDIT_CART_ITEM, l.id), GroupButton(tr("Remove", "حذف"), GroupAction.REMOVE_CART_ITEM, l.id, destructive = true))) }; c.reply?.receipts?.firstOrNull { it.memberId == c.me() }?.let { card("estimate", tr("Your estimated total", "إجمالي طلبك المتوقع"), it.totalText) } }
    private fun item() {
        val i = c.selectedItem ?: return; val r = c.room(); title = i.localizedName(language); subtitle = i.localizedDescription(language)
        i.variants.forEach { v -> card("variant:${v.id}", v.localizedName(language), Money.format(v.priceMinor, r.restaurant.currency), if(c.variant == v.id) tr("Selected", "محدد") else "", listOf(GroupButton(tr("Choose size", "اختر الحجم أو الخبز"), GroupAction.SELECT_VARIANT, v.id))) }
        r.restaurant.menu.optionGroups.filter { it.id in i.optionGroupIds }.forEach { g ->
            card("option-group:${g.id}", g.localizedName(language), tr("Select ${g.minSelections}–${g.maxSelections}", "اختر ${g.minSelections}–${g.maxSelections}"))
            g.options.forEach { o -> button("${if(o.id in c.options) "✓ " else ""}${o.localizedName(language)} · ${Money.format(o.priceDeltaMinor, r.restaurant.currency)}", GroupAction.TOGGLE_OPTION, o.id) }
        }
        field(GroupFieldKey.QUANTITY, "Quantity"); field(GroupFieldKey.NOTE, "Preparation notes")
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
        field(GroupFieldKey.AANI, "Receive with Aani", toggle = true); field(GroupFieldKey.ACCOUNT_HOLDER, "Account holder"); field(GroupFieldKey.ACCOUNT_BANK, "Bank name"); field(GroupFieldKey.ACCOUNT_IDENTIFIER, if(c.flag(GroupFieldKey.AANI)) "Aani phone number · with country code" else "IBAN / account identifier")
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
                field(GroupFieldKey.AMOUNT, "Refund amount"); field(GroupFieldKey.REFERENCE, "Refund reference / cash note")
            }
        }
        transferCards()
    }
    private fun history() { title = "Past orders"; subtitle = "Same room. Every meal remembered."; c.reply?.history?.forEach { past -> card("order:${past.number}", "Order #${past.number} · ${past.restaurantName}", timeLabel(past.completedAt)); receiptCards(past.receipts, "past:${past.number}:"); past.account?.let { accountCard(it, "past-account:${past.number}", "Account used for order #${past.number}") } }; if((c.reply?.historyNextOffset ?: -1) >= 0) button("Download older receipts", GroupAction.LOAD_OLDER_HISTORY); if(c.reply?.history.isNullOrEmpty()) card("empty", "No past orders downloaded yet", "Completed orders appear here when the next order starts.") }
    private fun receiptCards(receipts: List<Receipt>, prefix: String = "") {
        receipts.forEach { receipt ->
            val reviewingOwn = prefix.isEmpty() && c.reply?.room?.phase == RoomPhase.REVIEW && receipt.memberId == c.me()
            val account = c.reply?.room?.account
            val recipient = if (reviewingOwn && account != null) "\nRecipient: ${account.holder} · ${account.bank}\n${account.identifier} · account version ${account.version}" else ""
            val actions = mutableListOf(GroupButton("Share receipt", GroupAction.SHARE_RECEIPT, prefix + receipt.memberId))
            if (reviewingOwn) actions += GroupSettlementPresentation.quoteConfirmationAction(c.room(), c.me())
            card("receipt:$prefix${receipt.memberId}", "${receipt.name} · ${receipt.totalText}", receiptDetail(receipt, prefix.isEmpty()) + recipient, "Revision ${receipt.revision}", actions)
        }
    }
    private fun receiptDetail(r: Receipt, currentOrder: Boolean = true): String = (r.lines.map { "${it.quantity} × ${it.description} · ${Money.format(it.amount, r.currency)}${if(it.notes.isNotBlank()) "\n${it.notes}" else ""}" } + listOf("Delivery ${Money.format(r.delivery, r.currency)} · Service/adjustment ${Money.format(r.service, r.currency)}", "Discount ${Money.format(r.discount, r.currency)} · Tax ${Money.format(r.tax, r.currency)}", "Total ${r.totalText}", GroupSettlementPresentation.receiptBalanceText(r, if(currentOrder) c.reply?.room?.payerId else null))).joinToString("\n")
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
        account?.let { a -> card(id, "$label ${a.holder}", "${a.bank}\n${a.identifier}\n${a.currency} · account version ${a.version}") }
    }
    private fun transferCards() { val r = c.reply?.room ?: return; r.transfers.forEach { t ->
        val actions = if(t.status.name == "DECLARED") when { t.refund && t.memberId == c.me() -> listOf(GroupButton("Confirm refund received", GroupAction.CONFIRM_REFUND, t.id), GroupButton("Reject refund claim", GroupAction.REJECT_TRANSFER, t.id)); !t.refund && r.payerId == c.me() -> listOf(GroupButton("Confirm received", GroupAction.CONFIRM_TRANSFER, t.id), GroupButton("Reject claim", GroupAction.REJECT_TRANSFER, t.id)); else -> emptyList() } else emptyList()
        card("transfer:${t.id}", "${if(t.refund) "Refund" else "Transfer"} · ${Money.format(t.amount, r.restaurant.currency)}", "${r.members.single { it.id == t.memberId }.name} · ${t.reference}", t.status.name.lowercase(), actions)
    }; if(r.payerId == c.me()) c.reply?.receipts?.filter { GroupSettlementPresentation.refundAvailable(r, it) }?.forEach { button("Record refund to ${it.name}", GroupAction.DECLARE_REFUND, it.memberId) } }
    private fun feeFields() { field(GroupFieldKey.DELIVERY_FEE, "Delivery fee"); field(GroupFieldKey.SERVICE_FEE, "Service fee"); field(GroupFieldKey.DISCOUNT, "Shared discount"); field(GroupFieldKey.PROPORTIONAL, "Split delivery in proportion to food", toggle = true) }
    private fun stage(phase: RoomPhase): String = when(phase) { RoomPhase.LOBBY -> "Gathering"; RoomPhase.PREPARING_SPIN -> "Getting ready"; RoomPhase.SPINNING -> "Spinning"; RoomPhase.ACCEPTING -> "Accepting duty"; RoomPhase.COLLECTING -> "Choose food"; RoomPhase.REVIEW -> "Confirm totals"; RoomPhase.PLACED -> "Order placed"; RoomPhase.FULFILLED -> "Food arrived"; RoomPhase.ARCHIVED -> "Complete"; RoomPhase.CANCELLED -> "Cancelled" }
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
