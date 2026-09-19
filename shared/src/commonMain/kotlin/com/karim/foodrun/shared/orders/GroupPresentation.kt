package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupPresentation(private val c: GroupController) {
    private val fields = mutableListOf<GroupField>()
    private val cards = mutableListOf<GroupCard>()
    private val buttons = mutableListOf<GroupButton>()
    private var title = "Food Run"
    private var subtitle = "Good food. Great company."
    private fun field(key: GroupFieldKey, label: String, multiline: Boolean = false, toggle: Boolean = false) { fields += GroupField(key, label, c.text(key), multiline, toggle, secret = key == GroupFieldKey.PASSWORD) }
    private fun button(title: String, action: GroupAction, value: String = "", primary: Boolean = false, enabled: Boolean = true) { buttons += GroupButton(title, action, value, primary, enabled = enabled) }
    private fun card(id: String, title: String, detail: String = "", badge: String = "", actions: List<GroupButton> = emptyList()) { cards += GroupCard(id, title, detail, badge, actions) }
    private fun append(content: GroupFlowContent) { fields += content.fields; cards += content.cards; buttons += content.buttons }
    fun render(): GroupState {
        when(c.page) {
            GroupPage.HOME -> home()
            GroupPage.PROFILE -> profile()
            GroupPage.PEOPLE -> people()
            GroupPage.CUSTOM_ITEM -> { title = "What would you like?"; subtitle = "Add one item at a time. The selected person adds its price later."; field(GroupFieldKey.CUSTOM_NAME, "Food item"); field(GroupFieldKey.QUANTITY, "Quantity"); field(GroupFieldKey.NOTE, "Notes / extras"); button("Add to my order", GroupAction.ADD_CUSTOM_ITEM, primary = true) }
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
                RoomPhase.LOBBY -> 0
                RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING, RoomPhase.ACCEPTING -> 1
                RoomPhase.COLLECTING, RoomPhase.REVIEW -> 2
                RoomPhase.PLACED, RoomPhase.FULFILLED, RoomPhase.ARCHIVED -> 3
                else -> -1
            })
    }
    private fun home() {
        subtitle = "Gather your people. Share a meal."
        button("Create a room", GroupAction.CREATE, primary = true); button("Join a room", GroupAction.JOIN)
        button(if(c.library.home == null) "Register / sign in" else "My profile", GroupAction.OPEN_PROFILE); button("Enable notifications", GroupAction.ENABLE_ALERTS)
        button("Quick Spin", GroupAction.QUICK_SPIN); button("Restaurant library", GroupAction.OPEN_LIBRARY)
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
        title = if(c.library.home == null) "Your Food Run account" else "Your profile"
        subtitle = "Use your existing Intrvioo account, or register here."
        if(c.library.home == null) {
            field(GroupFieldKey.EMAIL, "Email"); field(GroupFieldKey.PASSWORD, "Password")
            button("Sign in", GroupAction.SIGN_IN, primary = true); button("Reset password", GroupAction.RESET_PASSWORD)
        }
        field(GroupFieldKey.NAME, "Profile name"); field(GroupFieldKey.PROFILE_PHONE, "Phone · with country code")
        field(GroupFieldKey.PHOTO, "Profile photo · HTTPS URL")
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
        field(GroupFieldKey.EXPECTED_NAMES, "Expected people today · comma separated")
        field(GroupFieldKey.DELIVERY, "Delivery to us", toggle = true)
        if(c.flag(GroupFieldKey.DELIVERY)) field(GroupFieldKey.DESTINATION, "Delivery address and contact")
        feeFields()
        card("restaurant", c.selectedRestaurant?.restaurant?.name ?: "Choose a restaurant", c.selectedRestaurant?.restaurant?.menu?.items?.size?.let { "$it menu items" } ?: "Create, import or select a saved menu.")
        button("Choose restaurant", GroupAction.OPEN_LIBRARY)
        button(if(c.nextOrder) "Start next order" else "Create room", GroupAction.CREATE_ROOM, primary = true)
    }
    private fun library() {
        title = "Restaurant library"; subtitle = "Your favorites, saved on your device."
        button("Add restaurant", GroupAction.NEW_RESTAURANT, primary = c.library.restaurants.isEmpty() && c.importPreview == null); button("Import menu JSON", GroupAction.IMPORT_MENU)
        field(GroupFieldKey.JSON_MENU, "Or paste a menu JSON file", multiline = true)
        if(c.text(GroupFieldKey.JSON_MENU).isNotBlank()) button("Preview JSON", GroupAction.PREVIEW_IMPORT)
        c.importPreview?.let { card("preview", "Import ${it.restaurant.name}", "${it.restaurant.menu.items.size} items · ${it.restaurant.currency}\nMatching restaurant IDs update the saved copy. Room menus stay unchanged."); button("Confirm import", GroupAction.CONFIRM_IMPORT, primary = true) }
        c.library.restaurants.forEach { e -> card("restaurant:${e.restaurant.id}", e.restaurant.name, "${e.restaurant.branchName} · ${e.restaurant.menu.items.size} items · ${e.restaurant.currency}", actions = listOf(
            GroupButton("Use for order", GroupAction.SELECT_RESTAURANT, e.restaurant.id), GroupButton("Edit", GroupAction.EDIT_RESTAURANT, e.restaurant.id), GroupButton("Share JSON", GroupAction.EXPORT_MENU, e.restaurant.id), GroupButton("Delete saved copy", GroupAction.DELETE_RESTAURANT, e.restaurant.id, destructive = true))) }
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
        if(editingRoom) card("room-currency", "Order currency", c.room().restaurant.currency)
        else field(GroupFieldKey.CURRENCY, "Currency · AED, USD, EGP…")
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
        title = r.name; subtitle = "Order #${r.orderNumber} · ${stage(r.phase)} · ${r.restaurant.name}"
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
            card("member:${m.id}", m.name, listOf(if(m.id == r.ownerId) "Organizer" else if(m.id == r.payerId) "Payer" else if(m.guest) "Watching" else "Member", if(c.serverNow() - m.lastSeen < 15000) "Connected" else "Away").joinToString(" · "), if(!m.approved) "Pending" else if(!m.guest && !m.participating) "Skipping this order" else if(m.ready && m.eligible) "Ready · Willing to pay" else if(m.ready) "Ready" else if(m.eligible) "Willing to pay" else "Joined", actions)
        }
        if(owner) r.expectedNames.filter { name -> r.orderingMembers.none { it.name.equals(name.trim(), true) } }.forEach { card("invite:$it", it, "Expected · not participating today", actions = if(r.phase == RoomPhase.LOBBY) listOf(GroupButton("Remove invitation", GroupAction.REMOVE, "invite:$it")) else emptyList()) }
        field(GroupFieldKey.REASON, "Reason for removal, reroll, reopening or adjustment")
        when(r.phase) {
            RoomPhase.LOBBY -> {
                if(!me.guest) {
                    button(if(me.participating) "Skip this order" else "Join this order", GroupAction.PARTICIPATE, (!me.participating).toString())
                    if(me.participating) {
                        val needsReady = !me.ready || !me.eligible && r.pastSpins.none { it.winnerId == me.id }
                        button(if(needsReady) "I'm ready" else "Ready", GroupAction.READY, primary = needsReady, enabled = needsReady)
                        if(!me.eligible && r.pastSpins.any { it.winnerId == me.id }) card("declined-duty", "You're still part of this meal", "You declined payment duty, so your name stays out of the next wheel. Mark yourself ready for the other members to spin.")
                    }
                }
                if(owner) {
                    val blocker = runCatching { RoomRules.spinReady(r, c.serverNow()) }.exceptionOrNull()?.message
                    button("Spin together", GroupAction.PREPARE_SPIN, primary = me.guest || !me.participating || me.ready, enabled = blocker == null)
                    if(blocker != null) card("spin-next-step", "Before spinning", blocker)
                    button("Cancel today's order", GroupAction.CANCEL)
                } else if(me.ready) card("ready-next-step", "Your readiness is saved", "Waiting for the organizer to start the shared spin.")
            }
            RoomPhase.PREPARING_SPIN -> { card("prepare", "Getting everyone in sync", "Waiting for participating phones to acknowledge the countdown."); if(owner) button("Stop waiting", GroupAction.ABORT_SPIN) }
            RoomPhase.SPINNING -> card("spinning", "One spin. One result.", "Stay here to watch the wheel together.")
            RoomPhase.ACCEPTING -> {
                val name = r.members.single { it.id == r.spin!!.winnerId }.name
                card("winner", "$name is selected!", "Waiting for acceptance before ordering opens.")
                if(r.spin!!.winnerId == c.me()) { button("I'll take care of it", GroupAction.ACCEPT_DUTY, primary = true); button("I can't this time", GroupAction.DECLINE_DUTY) }
            }
            RoomPhase.COLLECTING -> {
                val progress = c.reply!!.progress
                val needsAccount = progress?.accountShared?.not() ?: (r.account == null && !me.guest && me.participating)
                val awaitingFood = r.orderingMembers.filter { m -> r.carts.none { it.memberId == m.id && it.submitted } }
                if(payer) button(if(needsAccount) "Choose receiving account" else "Change receiving account", GroupAction.OPEN_ACCOUNT, primary = needsAccount)
                r.account?.let { card("account-shared", "Receiving account shared", "${it.holder} · ${it.bank}\n•••• ${it.identifier.takeLast(4)}\nEveryone will confirm this recipient with their total.") }
                if(!me.guest && me.participating) {
                    if(r.restaurant.openOrdering) button("Add a food item", GroupAction.OPEN_CUSTOM_ITEM, primary = true)
                    r.restaurant.menu.items.forEach { i -> card("menu:${i.id}", i.name, i.description, Money.format(i.basePriceMinor, r.restaurant.currency), if(i.available) listOf(GroupButton("Choose", GroupAction.OPEN_ITEM, i.id)) else emptyList()) }
                    cart()
                    if(!c.myCart().submitted) button(if(c.myCart().lines.isEmpty()) "No food this time" else "Submit my food order", GroupAction.SUBMIT_CART, primary = true)
                    else card("cart-submitted", "Your food order is submitted", "You can still change your food. Submit again after making changes.")
                }
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
        if(payer) card("contact", r.restaurant.name, "$contact\n${r.restaurant.contact.address ?: ""}\n${r.destination}", actions = listOf(GroupButton("Call restaurant", GroupAction.CALL_RESTAURANT), GroupButton("Share combined food order", GroupAction.SHARE_RESTAURANT_ORDER)))
    }
    private fun cart() { val r = c.room(); c.myCart().lines.forEach { l -> val name = l.description.ifEmpty { r.restaurant.menu.items.single { it.id == l.itemId }.name }; card("cart:${l.id}", "${l.quantity} × $name", l.notes, if(l.description.isNotEmpty()) l.unitPrice?.let { Money.format(it * l.quantity, r.restaurant.currency) } ?: "Awaiting price" else "", actions = listOf(GroupButton("Remove", GroupAction.REMOVE_CART_ITEM, l.id))) }; c.reply?.receipts?.firstOrNull { it.memberId == c.me() }?.let { card("estimate", "Your estimated total", it.totalText) } }
    private fun item() {
        val i = c.selectedItem ?: return; val r = c.room(); title = i.name; subtitle = i.description
        i.variants.forEach { v -> card("variant:${v.id}", v.name, Money.format(v.priceMinor, r.restaurant.currency), if(c.variant == v.id) "Selected" else "", listOf(GroupButton("Choose size", GroupAction.SELECT_VARIANT, v.id))) }
        r.restaurant.menu.optionGroups.filter { it.id in i.optionGroupIds }.forEach { g ->
            card("option-group:${g.id}", g.name, "Select ${g.minSelections}–${g.maxSelections}")
            g.options.forEach { o -> button("${if(o.id in c.options) "✓ " else ""}${o.name} · ${Money.format(o.priceDeltaMinor, r.restaurant.currency)}", GroupAction.TOGGLE_OPTION, o.id) }
        }
        field(GroupFieldKey.QUANTITY, "Quantity"); field(GroupFieldKey.NOTE, "Preparation notes")
        button("Add to my order", GroupAction.ADD_CART_ITEM, primary = true)
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
        return (listOf("Food Run · ${r.name} · order #${r.orderNumber}", r.restaurant.name, if(r.deliveryMode) "Delivery: ${r.destination}" else "Pickup") +
            c.reply!!.receipts.flatMap { receipt -> listOf("\n${receipt.name}") + receipt.lines.map { "${it.quantity} × ${it.description}${if(it.notes.isNotBlank()) " — ${it.notes}" else ""}" } }).joinToString("\n")
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
