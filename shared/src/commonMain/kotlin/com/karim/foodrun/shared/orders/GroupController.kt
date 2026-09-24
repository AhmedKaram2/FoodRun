package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.SerializationException

class GroupController(val platform: GroupPlatform) {
    internal var library = GroupLibrary()
    internal var page = GroupPage.HOME
    internal var draft = mutableMapOf<GroupFieldKey, String>()
    internal val formDrafts = GroupDraftMemory()
    internal var error = ""
    internal var accessBlock: AccessBlock? = null
    internal var blockClockOffset = 0L
    internal var previousOrderLimit = 10
    internal var busy = false
    internal var online = false
    internal var reply: RoomReply? = null
    internal var session: StoredSession? = null
    internal var selectedRestaurant: RestaurantExport? = null
    internal var editingRestaurant: RestaurantExport? = null
    internal var editingRoomOrder: Pair<String, Long>? = null
    internal var importPreview: RestaurantExport? = null
    internal var selectedItem: MenuItem? = null
    internal var editingCartLineId: String? = null
    internal var variant: String? = null
    internal var options = emptyList<String>()
    internal var selectedAccount: ReceivingAccount? = null
    internal var joinMode = false
    internal var nextOrder = false
    internal var choosingPollRestaurants = false
    internal val pollRestaurantIds = linkedSetOf<String>()
    private var observer: GroupObserver? = null
    private var watching: GroupSubscription? = null
    internal var accountEditing = false
    internal var pricingTarget: Pair<String, String>? = null
    private var homeWatching: GroupSubscription? = null
    private val roomWatches = mutableMapOf<String, GroupSubscription>()
    private var homeGeneration = 0
    private var generation = 0
    private var offset: Long = 0
    private var libraryReturnPage = GroupPage.HOME
    private var storageReadable = true
    private var active = true
    val state: GroupState get() = GroupPresentation(this).render()
    init {
        try {
            val saved = platform.read("group-library-v1")
            if (saved.isNotBlank()) library = orderJson.decodeFromString(saved)
            library = library.copy(restaurants = restoreRestaurantMetadata(library.restaurants))
            val missingBuiltIns = BuiltInRestaurants.all.filter { builtIn -> builtIn.restaurant.id !in library.managedRestaurantIds && library.restaurants.none { it.restaurant.id == builtIn.restaurant.id } }
            if (missingBuiltIns.isNotEmpty()) library = library.copy(restaurants = library.restaurants + missingBuiltIns)
            if (library.pending != null && library.pendingHub == null) library = library.copy(pendingHub = library.selectedHub)
            draft[GroupFieldKey.NAME] = library.displayName
        } catch (_: Exception) { storageReadable = false; error = "Saved group data could not be opened. Restore its backup before creating new data." }
    }
    fun observe(observer: GroupObserver) { this.observer = observer; publish() }
    fun removeObserver(observer: GroupObserver) { if (this.observer === observer) this.observer = null }
    fun close() { stopHomeWatching(); watching?.cancel(); watching = null; observer = null; generation++ }
    fun foreground() { active = true; startHomeWatching(); if (session != null) startWatching() }
    fun background() { active = false; stopHomeWatching(); watching?.cancel(); watching = null; online = false; generation++; publish() }
    fun update(key: GroupFieldKey, value: String) {
        if (busy) return
        try {
            error = ""
            if (key == GroupFieldKey.ELIGIBLE) {
                val room = room()
                val member = room.members.single { it.id == me() }
                require(page == GroupPage.ROOM && room.phase == RoomPhase.LOBBY) { "Payment consent can only change while gathering. Refresh the room." }
                require(member.approved && !member.guest && member.participating) { "Join this order before choosing whether to pay." }
                require(library.pending == null) { "Retry the saved request before changing payment consent." }
                if (member.eligible != (value == "true")) command(CommandKind.READY, flag = member.ready, eligible = value == "true")
            } else {
                if (key == GroupFieldKey.AANI && value != text(key)) {
                    val previous = if (flag(GroupFieldKey.AANI)) GroupFieldKey.ACCOUNT_AANI_DRAFT else GroupFieldKey.ACCOUNT_IBAN_DRAFT
                    val next = if (value == "true") GroupFieldKey.ACCOUNT_AANI_DRAFT else GroupFieldKey.ACCOUNT_IBAN_DRAFT
                    draft[previous] = text(GroupFieldKey.ACCOUNT_IDENTIFIER)
                    draft[GroupFieldKey.ACCOUNT_IDENTIFIER] = text(next)
                }
                if (key == GroupFieldKey.RESTAURANT_EMIRATE && value != text(key)) draft.remove(GroupFieldKey.RESTAURANT_AREA)
                draft[key] = value.take(if (key == GroupFieldKey.JSON_MENU) MenuValidation.MAX_BYTES else if (key == GroupFieldKey.PHOTO) 180_000 else 4000)
                if (key == GroupFieldKey.RESTAURANT_POLL && value == "true") openPollRestaurants()
            }
        } catch (e: Exception) { error = e.message ?: "This change could not be saved." }
        publish()
    }
    fun dispatch(action: GroupAction, value: String = "") {
        if (busy && action !in listOf(GroupAction.BACK, GroupAction.REFRESH)) return
        try { error = ""; when (action) {
            GroupAction.OPEN_BLOCK_REQUEST -> { require(room().ownerId == me()); page = GroupPage.BLOCK_REQUEST }
            GroupAction.REQUEST_BLOCK -> command(CommandKind.REQUEST_BLOCK, memberId = text(GroupFieldKey.BLOCK_MEMBER).ifBlank {
                room().activeMembers.firstOrNull { it.id != me() && !it.guest }?.id.orEmpty()
            }, amount = text(GroupFieldKey.BLOCK_HOURS).ifBlank { "24" }.toLongOrNull() ?: 0, text = text(GroupFieldKey.BLOCK_REASON).trim())
            GroupAction.OPEN_POLL_RESTAURANTS -> openPollRestaurants()
            GroupAction.TOGGLE_POLL_RESTAURANT -> {
                require(choosingPollRestaurants) { "Open the poll restaurant selection first." }
                if (value in pollRestaurantIds) pollRestaurantIds.remove(value) else {
                    require(pollRestaurantIds.size < 12) { "Choose up to 12 restaurants." }
                    require(library.restaurants.any { it.restaurant.id == value }) { "Restaurant is no longer available." }
                    pollRestaurantIds.add(value)
                }
            }
            GroupAction.CONFIRM_POLL_RESTAURANTS -> {
                val first = pollChoices().first()
                if (selectedRestaurant?.restaurant?.id != first.id) seedFees(first)
                selectedRestaurant = RestaurantExport(exportId = first.id, restaurant = first)
                if (text(GroupFieldKey.ROOM_NAME).isBlank()) draft[GroupFieldKey.ROOM_NAME] = if (library.language == "ar") "تصويت مطعمنا" else "Our restaurant poll"
                choosingPollRestaurants = false
                page = GroupPage.SETUP
            }
            GroupAction.OPEN_PROFILE -> { openProfile() }
            GroupAction.OPEN_ORDER_PRICES -> { require(room().payerId == me()); page = GroupPage.PRICES }
            GroupAction.SET_LANGUAGE -> {
                require(value in listOf("en", "ar")) { "Choose Arabic or English." }
                replaceLibrary(library.copy(language = value))
            }
            GroupAction.GOOGLE_SIGN_IN -> {
                busy = true; publish()
                platform.googleSignIn(object : GroupReplyCallback {
                    override fun complete(body: String, error: String) {
                        busy = false
                        if (error.isNotBlank()) { this@GroupController.error = error; publish() }
                        else identity(IdentityAction.FIREBASE_SIGN_IN, body)
                    }
                })
            }
            GroupAction.SIGN_IN -> identity(IdentityAction.SIGN_IN)
            GroupAction.REGISTER -> identity(IdentityAction.REGISTER)
            GroupAction.SAVE_PROFILE -> identity(IdentityAction.SAVE_PROFILE, returnPage = GroupPage.PROFILE)
            GroupAction.RESET_PASSWORD -> identity(IdentityAction.RESET_PASSWORD)
            GroupAction.SIGN_OUT -> identity(IdentityAction.SIGN_OUT)
            GroupAction.ENABLE_CLOUD -> identity(IdentityAction.ENABLE_CLOUD)
            GroupAction.ENABLE_ALERTS -> platform.enableNotifications()
            GroupAction.OPEN_PEOPLE -> { require(library.identityToken.isNotEmpty()) { "Sign in from your profile to invite registered people." }; page = GroupPage.PEOPLE }
            GroupAction.INVITE_PERSON -> identity(IdentityAction.INVITE, value)
            GroupAction.ACCEPT_INVITE -> identity(IdentityAction.ACCEPT_INVITE, value)
            GroupAction.USE_OPEN_ORDER -> {
                val restaurant = Restaurant(platform.uuid(), text(GroupFieldKey.RESTAURANT_NAME).trim().ifEmpty { "Group food order" }, openOrdering = true)
                selectedRestaurant = RestaurantExport(exportId = restaurant.id, restaurant = restaurant)
            }
            GroupAction.OPEN_CUSTOM_ITEM -> { editingCartLineId = null; draft[GroupFieldKey.CUSTOM_NAME] = ""; draft[GroupFieldKey.QUANTITY] = "1"; draft[GroupFieldKey.NOTE] = ""; page = GroupPage.CUSTOM_ITEM }
            GroupAction.ADD_CUSTOM_ITEM -> {
                val cart = myCart()
                val line = CartLine(editingCartLineId ?: platform.uuid(), "", text(GroupFieldKey.QUANTITY).toIntOrNull() ?: 0, notes = text(GroupFieldKey.NOTE), description = text(GroupFieldKey.CUSTOM_NAME).trim())
                require(line.description.isNotEmpty()) { "Enter the food item." }
                val lines = if(editingCartLineId == null) cart.lines + line else cart.lines.map { if(it.id == editingCartLineId) line else it }
                Billing.lines(room().restaurant, cart.copy(lines = lines))
                command(CommandKind.CART, cart = cart.copy(lines = lines), revision = cart.revision)
            }
            GroupAction.OPEN_PRICE_ITEM -> { val parts = value.split(':'); require(parts.size == 2); pricingTarget = parts[0] to parts[1]; draft[GroupFieldKey.AMOUNT] = ""; page = GroupPage.PRICE_ITEM }
            GroupAction.SAVE_ITEM_PRICE -> { val target = requireNotNull(pricingTarget); command(CommandKind.PRICE_ITEM, memberId = target.first, text = target.second, amount = Money.parse(text(GroupFieldKey.AMOUNT), room().restaurant.currency)) }
            GroupAction.BACK -> back()
            GroupAction.QUICK_SPIN -> page = GroupPage.QUICK_SPIN
            GroupAction.CREATE, GroupAction.JOIN -> { joinMode = action == GroupAction.JOIN; nextOrder = false; page = GroupPage.CONNECT; seedHub() }
            GroupAction.USE_INTERNET -> {
                draft[GroupFieldKey.PAIRING_LINK] = ""
                draft[GroupFieldKey.HUB_URL] = FOOD_RUN_INTERNET_API
                draft[GroupFieldKey.FINGERPRINT] = ""
                connect()
            }
            GroupAction.CONNECT -> connect()
            GroupAction.DISCOVER -> platform.discover(callback { body -> draft[GroupFieldKey.HUB_URL] = body; error = "Hub found. Scan its setup QR or paste its fingerprint to verify its identity."; publish() })
            GroupAction.SCAN -> platform.scanPairing(callback { body -> draft[GroupFieldKey.PAIRING_LINK] = body; connect() })
            GroupAction.OPEN_LIBRARY -> { choosingPollRestaurants = false; libraryReturnPage = page; page = GroupPage.LIBRARY }
            GroupAction.NEW_RESTAURANT -> restaurantEditor(null)
            GroupAction.EDIT_RESTAURANT -> restaurantEditor(library.restaurants.single { it.restaurant.id == value })
            GroupAction.EDIT_ROOM_RESTAURANT -> {
                libraryReturnPage = GroupPage.ROOM
                val room = room()
                restaurantEditor(RestaurantExport(exportId = room.restaurant.id, restaurant = room.restaurant), forRoom = true)
            }
            GroupAction.SELECT_TAX_TREATMENT -> {
                val treatment = TaxTreatment.valueOf(value)
                editingRestaurant = editingRestaurant?.let { it.copy(restaurant = it.restaurant.copy(pricing = it.restaurant.pricing.copy(taxTreatment = treatment))) }
            }
            GroupAction.IMPORT_MENU -> platform.importMenu(callback { body -> importPreview = MenuValidation.import(body); draft[GroupFieldKey.JSON_MENU] = body; page = GroupPage.LIBRARY; publish() })
            GroupAction.PREVIEW_IMPORT -> { importPreview = MenuValidation.import(text(GroupFieldKey.JSON_MENU)); page = GroupPage.LIBRARY }
            GroupAction.CONFIRM_IMPORT -> { val item = requireNotNull(importPreview); saveRestaurant(item); importPreview = null; draft.remove(GroupFieldKey.JSON_MENU) }
            GroupAction.EXPORT_MENU -> { val item = library.restaurants.single { it.restaurant.id == value }; platform.share(orderJson.encodeToString(item), "restaurant-menu.json") }
            GroupAction.SAVE_RESTAURANT -> saveEditor()
            GroupAction.DELETE_RESTAURANT -> deleteRestaurant(value)
            GroupAction.SELECT_RESTAURANT -> {
                selectedRestaurant = library.restaurants.single { it.restaurant.id == value }
                if (text(GroupFieldKey.ROOM_NAME).isBlank()) draft[GroupFieldKey.ROOM_NAME] = selectedRestaurant!!.restaurant.localizedName(library.language)
                seedFees(selectedRestaurant!!.restaurant)
                joinMode = false
                page = if(library.selectedHub == null && !nextOrder) GroupPage.CONNECT else GroupPage.SETUP
            }
            GroupAction.ADD_MENU_ITEM -> addMenuItem()
            GroupAction.REMOVE_MENU_ITEM -> { editingRestaurant = editingRestaurant?.let { it.copy(restaurant = it.restaurant.copy(menu = it.restaurant.menu.copy(items = it.restaurant.menu.items.filterNot { item -> item.id == value }))) } }
            GroupAction.SAVE_ROOM_RESTAURANT -> { val r = room().restaurant; saveRestaurant(RestaurantExport(exportId = r.id, restaurant = r)); error = "Restaurant saved to your library." }
            GroupAction.UPDATE_ROOM_MENU -> command(CommandKind.UPDATE_RESTAURANT, restaurant = library.restaurants.single { it.restaurant.id == value }.restaurant, text = text(GroupFieldKey.REASON))
            GroupAction.RESUME -> resume(library.sessions.single { it.roomId == value })
            GroupAction.CREATE_ROOM -> createRoom()
            GroupAction.JOIN_ROOM -> send(RoomCommand(commandId = platform.uuid(), kind = CommandKind.JOIN, code = text(GroupFieldKey.ROOM_CODE).trim(), name = text(GroupFieldKey.NAME).trim(), guest = false))
            GroupAction.RETRY -> { val pending = library.pending; if (pending != null) send(pending, true) else startWatching() }
            GroupAction.REFRESH -> { accessBlock = null; startHomeWatching(); if (session != null) startWatching() }
            GroupAction.OPEN_ITEM -> { editingCartLineId = null; selectedItem = room().restaurant.menu.items.single { it.id == value }; variant = selectedItem!!.variants.firstOrNull()?.id; options = emptyList(); draft[GroupFieldKey.QUANTITY] = "1"; draft[GroupFieldKey.NOTE] = ""; page = GroupPage.ITEM }
            GroupAction.EDIT_CART_ITEM -> {
                val line = myCart().lines.single { it.id == value }; editingCartLineId = line.id
                draft[GroupFieldKey.QUANTITY] = line.quantity.toString(); draft[GroupFieldKey.NOTE] = line.notes
                if(line.description.isNotEmpty()) {
                    selectedItem = null; draft[GroupFieldKey.CUSTOM_NAME] = line.description; page = GroupPage.CUSTOM_ITEM
                } else {
                    selectedItem = room().restaurant.menu.items.single { it.id == line.itemId }
                    variant = line.variantId; options = line.optionIds; page = GroupPage.ITEM
                }
            }
            GroupAction.SELECT_VARIANT -> variant = value
            GroupAction.TOGGLE_OPTION -> options = if (value in options) options - value else options + value
            GroupAction.ADD_CART_ITEM -> addCartItem()
            GroupAction.QUICK_ADD_ITEM -> quickAddMenuItem(value)
            GroupAction.INCREASE_CART_QUANTITY -> changeCartQuantity(value, 1)
            GroupAction.DECREASE_CART_QUANTITY -> changeCartQuantity(value, -1)
            GroupAction.REMOVE_CART_ITEM -> { val cart = myCart(); command(CommandKind.CART, cart = cart.copy(lines = cart.lines.filterNot { it.id == value }), revision = cart.revision) }
            GroupAction.OPEN_ACCOUNT -> {
                reply?.room?.account?.let { selectedAccount = it; seedAccount(it) }
                if (reply?.room?.account == null) library.home?.profile?.payment?.let { selectedAccount = it; seedAccount(it) }
                page = GroupPage.ACCOUNT
            }
            GroupAction.NEW_ACCOUNT -> newAccount()
            GroupAction.SELECT_ACCOUNT -> { selectedAccount = library.accounts.single { it.id == value }; seedAccount(selectedAccount!!) }
            GroupAction.SAVE_ACCOUNT -> saveAccount()
            GroupAction.SHARE_ACCOUNT -> {
                require(library.pending == null) { "Retry the saved request before sharing another account." }
                saveAccount()
                if (reply?.room?.account?.let(::accountMatchesDraft) == true) page = GroupPage.ROOM
                else command(CommandKind.SHARE_ACCOUNT, account = selectedAccount)
            }
            GroupAction.DELETE_ACCOUNT -> deleteAccount(value)
            GroupAction.OPEN_RECEIPTS -> page = GroupPage.RECEIPTS
            GroupAction.OPEN_HISTORY -> page = GroupPage.HISTORY
            GroupAction.LOAD_OLDER_HISTORY -> loadOlderHistory()
            GroupAction.CALL_RESTAURANT -> {
                val contact = room().restaurant.contact.phoneE164 ?: room().restaurant.contact.whatsappE164 ?: error("Restaurant contact is missing.")
                platform.openLink("tel:" + contact.filter { it.isDigit() || it == '+' })
            }
            GroupAction.SHARE_RESTAURANT_ORDER -> {
                platform.copyToClipboard(GroupPresentation(this).restaurantOrderText())
                platform.notify("Order list copied", "Paste it directly into the restaurant chat or ordering app.")
            }
            GroupAction.SHARE_ORDER_WHATSAPP -> {
                val number = restaurantWhatsAppNumber(room().restaurant)
                platform.openLink("https://wa.me/$number?text=${queryEncode(GroupPresentation(this).restaurantOrderText())}")
            }
            GroupAction.COPY_RESTAURANT_PHONE -> platform.copyToClipboard(room().restaurant.contact.phoneE164 ?: room().restaurant.contact.whatsappE164 ?: error("Restaurant phone is unavailable."))
            GroupAction.WALLET_PAY, GroupAction.WALLET_REFUND, GroupAction.WALLET_CONFIRM, GroupAction.WALLET_REJECT, GroupAction.WALLET_COPY -> walletAction(action, value)
            GroupAction.SHARE_ROOM -> { val s = requireNotNull(session); platform.share(roomInvitation(room(), roomInviteLink(s.hub, room().code), library.language), "") }
            GroupAction.SHARE_RECEIPT -> platform.share(GroupPresentation(this).receiptText(value), "receipt.txt")
            GroupAction.COPY_PAYMENT_DETAILS -> platform.copyToClipboard(requireNotNull(room().account) { "Payment details are not shared yet." }.identifier)
            GroupAction.USE_REMAINING_AMOUNT -> {
                val receipt = requireNotNull(reply?.receipts?.firstOrNull { it.memberId == me() })
                require(receipt.balance > 0 && room().payerId != me()) { "No payment is due." }
                draft[GroupFieldKey.AMOUNT] = Money.format(receipt.balance, receipt.currency).substringAfter(' ')
            }
            GroupAction.MORE_PREVIOUS_ORDERS -> previousOrderLimit += 10
            GroupAction.REUSE_ORDER -> reuseOrder(value)
            GroupAction.FAVORITE_ORDER -> favoriteOrder(value)
            GroupAction.REMOVE_FAVORITE_ORDER -> removeFavoriteOrder(value)
            GroupAction.NEXT_ORDER -> prepareNextOrder()
            else -> dispatchRoom(action, value)
        } } catch (e: Exception) { error = e.message ?: "This action could not be completed." }
        publish()
    }
    internal fun room(): Room = requireNotNull(reply?.room) { "Open a saved room first." }
    internal fun me(): String = session?.memberId ?: ""
    internal fun myCart(): MemberCart = room().carts.singleOrNull { it.memberId == me() } ?: MemberCart(me())
    internal fun text(key: GroupFieldKey): String = if (key == GroupFieldKey.ELIGIBLE)
        (reply?.room?.members?.singleOrNull { it.id == me() }?.eligible ?: false).toString()
    else draft[key] ?: ""
    internal fun flag(key: GroupFieldKey) = text(key) == "true"
    internal fun serverNow() = platform.now() + offset
    internal fun serverOffset() = offset
    internal fun publish() { observer?.changed(state) }
    internal fun persist() { check(storageReadable) { "Saved data could not be read. Restore device data before saving changes." }; check(platform.write("group-library-v1", orderJson.encodeToString(library))) { "Could not save group data. Check device storage and retry." } }
    private fun callback(action: (String) -> Unit) = object : GroupReplyCallback {
        override fun complete(body: String, error: String) { try { if (error.isNotBlank()) this@GroupController.error = error else action(body) } catch (e: Exception) { this@GroupController.error = e.message ?: "Unable to read this file." }; publish() }
    }
    private fun seedHub() { library.selectedHub?.let { draft[GroupFieldKey.HUB_URL] = it.url; draft[GroupFieldKey.FINGERPRINT] = it.fingerprint }; draft[GroupFieldKey.PAIRING_LINK] = "" }
    private fun connect() {
        val link = text(GroupFieldKey.PAIRING_LINK).trim()
        if (link.isNotEmpty()) {
            require(link.startsWith("foodrun://pair?")) { "Use the pairing link shown by your Food Run server." }
            val params = link.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            val host = params["host"] ?: error("Missing host."); val port = params["port"]?.toIntOrNull() ?: 8443
            require(host.matches(Regex("[a-zA-Z0-9.-]+")) && port in 1..65535)
            draft[GroupFieldKey.HUB_URL] = "https://$host:$port"; draft[GroupFieldKey.FINGERPRINT] = params["fingerprint"] ?: ""
        }
        val url = text(GroupFieldKey.HUB_URL).trim().trimEnd('/')
        val fingerprint = text(GroupFieldKey.FINGERPRINT).trim().replace(":", "").lowercase()
        require(url.matches(Regex("https://[a-zA-Z0-9.-]+(:[0-9]{1,5})?"))) { "Enter an HTTPS Food Run API address, for example https://foodrun.example.com or https://192.168.1.20:8443." }
        val explicitPort = url.substringAfter("https://").substringAfter(':', "").toIntOrNull()
        require(explicitPort == null || explicitPort in 1..65535) { "API port must be between 1 and 65535." }
        require(fingerprint.isEmpty() || fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Use the full SHA-256 fingerprint for a private hub, or leave it empty for a public HTTPS API." }
        val hub = HubPairing(url, fingerprint)
        val previousSession = session
        replaceLibrary(library.copy(
            selectedHub = hub,
            sessions = library.sessions.map { if (sameHub(it.hub, hub)) it.copy(hub = hub) else it },
            pendingHub = library.pendingHub?.let { if (sameHub(it, hub)) hub else it },
        ))
        session = previousSession?.let { old -> library.sessions.singleOrNull { it.roomId == old.roomId } ?: old }
        page = if (accountEditing) GroupPage.PROFILE else GroupPage.SETUP
        accountEditing = false
    }
    private fun openPollRestaurants() {
        pollRestaurantIds.retainAll(library.restaurants.map { it.restaurant.id }.toSet())
        if (pollRestaurantIds.isEmpty()) selectedRestaurant?.restaurant?.id?.takeIf { id -> library.restaurants.any { it.restaurant.id == id } }?.let(pollRestaurantIds::add)
        choosingPollRestaurants = true
        libraryReturnPage = GroupPage.SETUP
        page = GroupPage.LIBRARY
    }

    private fun pollChoices(): List<Restaurant> {
        require(pollRestaurantIds.size in 2..12) { "Choose between 2 and 12 restaurants for the poll." }
        val choices = pollRestaurantIds.map { id -> requireNotNull(library.restaurants.find { it.restaurant.id == id }) { "A selected restaurant is no longer available. Update the poll choices." }.restaurant }
        require(choices.all { it.currency == choices.first().currency }) { "Poll restaurants must use the same currency." }
        return choices
    }

    private fun createRoom() {
        val choices = if(flag(GroupFieldKey.RESTAURANT_POLL)) pollChoices() else listOf(requireNotNull(selectedRestaurant) { "Choose or create a restaurant first." }.restaurant)
        val r = choices.first()
        val names = text(GroupFieldKey.EXPECTED_NAMES).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val fees = fees(r.currency)
        val destination = if(flag(GroupFieldKey.DELIVERY)) text(GroupFieldKey.DESTINATION).trim().ifBlank { "The selected orderer will arrange delivery with the restaurant." } else ""
        if (nextOrder) command(CommandKind.NEXT_ORDER, restaurant = r, restaurants = choices, fees = fees, expectedNames = names, flag = flag(GroupFieldKey.DELIVERY), destination = destination)
        else send(RoomCommand(commandId = platform.uuid(), kind = CommandKind.CREATE, name = text(GroupFieldKey.NAME).trim(), text = text(GroupFieldKey.ROOM_NAME).trim(), restaurant = r, restaurants = choices, expectedNames = names, flag = flag(GroupFieldKey.DELIVERY), destination = destination, fees = fees))
    }
    private fun back() {
        if(accessBlock != null) { accessBlock = null; page = GroupPage.HOME; return }
        if (page == GroupPage.LIBRARY) choosingPollRestaurants = false
        val roomRestaurantEditor = page == GroupPage.RESTAURANT && editingRoomOrder != null
        if (page in listOf(GroupPage.ITEM, GroupPage.CUSTOM_ITEM)) {
            selectedItem = null
            editingCartLineId = null
        }
        if (page == GroupPage.RESTAURANT) {
            formDrafts.finishRestaurant(draft)
            editingRestaurant = null
            editingRoomOrder = null
        }
        page = when (page) {
            GroupPage.BLOCK_REQUEST, GroupPage.ITEM, GroupPage.CUSTOM_ITEM, GroupPage.PRICE_ITEM, GroupPage.PRICES, GroupPage.PEOPLE, GroupPage.ACCOUNT, GroupPage.RECEIPTS, GroupPage.HISTORY -> GroupPage.ROOM
            GroupPage.RESTAURANT -> if (roomRestaurantEditor) GroupPage.ROOM else GroupPage.LIBRARY
            GroupPage.LIBRARY -> libraryReturnPage
            else -> GroupPage.HOME
        }
    }
    private fun resume(s: StoredSession) {
        watching?.cancel(); generation++; session = s; reply = library.snapshots[s.roomId]; online = false
        reply?.room?.let(::seedRoomDrafts)
        page = GroupPage.ROOM; startWatching()
    }
    private fun seedRoomDrafts(room: Room) {
        draft[GroupFieldKey.DELIVERY_FEE] = Money.format(room.fees.delivery, room.restaurant.currency).substringAfter(' ')
        draft[GroupFieldKey.SERVICE_FEE] = Money.format(room.fees.service, room.restaurant.currency).substringAfter(' ')
        draft[GroupFieldKey.DISCOUNT] = Money.format(room.fees.discount, room.restaurant.currency).substringAfter(' ')
        draft[GroupFieldKey.PROPORTIONAL] = room.fees.proportionalDelivery.toString()
        draft[GroupFieldKey.AUTOMATIC_DELIVERY] = room.fees.automaticDelivery.toString()
    }
    internal fun command(kind: CommandKind, memberId: String = "", text: String = "", flag: Boolean = false, eligible: Boolean = false, revision: Long = room().revision, cart: MemberCart? = null, account: ReceivingAccount? = null, fees: FeePolicy? = null, amount: Long = 0, transferId: String = "", restaurant: Restaurant? = null, restaurants: List<Restaurant> = emptyList(), expectedNames: List<String> = emptyList(), destination: String = "", name: String = "") {
        val s = requireNotNull(session)
        send(RoomCommand(commandId = platform.uuid(), kind = kind, roomId = s.roomId, token = s.token, expectedRevision = revision, expectedOrderNumber = room().orderNumber, memberId = memberId, text = text, flag = flag, eligible = eligible, cart = cart, account = account, fees = fees, amount = amount, transferId = transferId, restaurant = restaurant, restaurants = restaurants, expectedNames = expectedNames, destination = destination, name = name))
    }
    private fun walletAction(action: GroupAction, value: String) {
        val (roomId, memberId) = value.split('|', limit = 2).also { require(it.size == 2) }
        val saved = library.sessions.single { it.roomId == roomId }
        val cached = requireNotNull(library.snapshots[roomId])
        val room = requireNotNull(cached.room)
        if (action == GroupAction.WALLET_COPY) {
            platform.copyToClipboard(requireNotNull(room.account).identifier); return
        }
        val receipt = cached.receipts.single { it.memberId == memberId }
        val pending = room.transfers.firstOrNull { it.memberId == memberId && it.status == TransferStatus.DECLARED }
        val payer = room.payerId == saved.memberId
        val kind = when (action) {
            GroupAction.WALLET_PAY -> { require(!payer && memberId == saved.memberId && receipt.balance > 0 && pending == null && room.restaurantPaid); CommandKind.DECLARE_TRANSFER }
            GroupAction.WALLET_REFUND -> { require(payer && receipt.balance < 0 && pending == null && room.restaurantPaid); CommandKind.DECLARE_REFUND }
            GroupAction.WALLET_CONFIRM, GroupAction.WALLET_REJECT -> {
                requireNotNull(pending)
                require(if (pending.refund) memberId == saved.memberId else payer)
                if (action == GroupAction.WALLET_REJECT) CommandKind.REJECT_TRANSFER else if (pending.refund) CommandKind.CONFIRM_REFUND else CommandKind.CONFIRM_TRANSFER
            }
            else -> error("Unsupported wallet action.")
        }
        send(RoomCommand(commandId = platform.uuid(), kind = kind, roomId = roomId, token = saved.token,
            expectedRevision = room.revision, expectedOrderNumber = room.orderNumber, memberId = memberId,
            amount = kotlin.math.abs(receipt.balance), transferId = pending?.id.orEmpty(),
            text = if (action == GroupAction.WALLET_REJECT) "Payment was not received." else if (payer) "Refund sent" else "Payment sent"), returnPage = page)
    }
    private fun send(c: RoomCommand, retry: Boolean = false, returnPage: GroupPage? = null) {
        require(library.pending == null || retry) { "A previous request is awaiting confirmation. Retry it before making another change." }
        val requestSession = if(c.kind in listOf(CommandKind.CREATE, CommandKind.JOIN)) null else library.sessions.single { it.roomId == c.roomId }
        val hub = if (retry) requireNotNull(library.pendingHub ?: requestSession?.hub ?: library.selectedHub) else requestSession?.hub ?: requireNotNull(library.selectedHub)
        val outgoing = if (sameHub(library.identityHub, hub) && library.identityToken.isNotEmpty()) c.copy(identityToken = library.identityToken) else c
        replaceLibrary(library.copy(pending = outgoing, pendingHub = hub)); busy = true; publish()
        val sentAt = platform.now()
        try { platform.request(hub, encodeCommand(outgoing), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                busy = false
                if (error.isNotBlank()) { online = false; this@GroupController.error = "$error Your request is saved. Retry to confirm its result."; publish(); return }
                try {
                    val next = decodeReply(body)
                    if (!next.ok) {
                        if (next.code == "HUB_UNAVAILABLE") {
                            online = false
                            this@GroupController.error = "${next.error} Your request remains saved; retry to confirm its result."
                        } else {
                            replaceLibrary(library.copy(pending = null, pendingHub = null))
                            this@GroupController.error = next.error
                        }
                        publish(); return
                    }
                    if (requestSession != null && session?.roomId != requestSession.roomId) { session = requestSession; reply = library.snapshots[requestSession.roomId] }
                    if (next.token.isNotEmpty()) {
                        val room = requireNotNull(next.room)
                        val s = StoredSession(hub, room.id, next.token, next.memberId, room.name)
                        session = s; library = library.copy(sessions = library.sessions.filterNot { it.roomId == s.roomId } + s, displayName = c.name)
                    }
                    accept(next, sentAt); replaceLibrary(library.copy(pending = null, pendingHub = null))
                    if (c.kind == CommandKind.UPDATE_RESTAURANT && editingRoomOrder != null) {
                        formDrafts.finishRestaurant(draft); editingRoomOrder = null
                    }
                    if (c.kind == CommandKind.CART) { selectedItem = null; editingCartLineId = null }
                    if (c.kind in listOf(CommandKind.PLACE, CommandKind.PAY_RESTAURANT, CommandKind.DECLARE_TRANSFER, CommandKind.DECLARE_REFUND)) draft.remove(GroupFieldKey.REFERENCE)
                    page = returnPage ?: GroupPage.ROOM; startWatching(); startHomeWatching()
                } catch (e: Exception) { this@GroupController.error = e.message ?: "Invalid hub response." }
                publish()
            }
        }) } catch (failure: Exception) {
            busy = false; online = false
            error = "${failure.message ?: "Connection failed."} Your request is saved. Retry to confirm its result."
            publish()
        }
    }
    private fun accept(next: RoomReply, sentAt: Long) {
        require(next.protocolVersion == 1) { "Update Food Run to connect to this hub." }
        val s = requireNotNull(session); val room = requireNotNull(next.room)
        require(room.id == s.roomId && next.memberId == s.memberId) { "Hub returned a different room." }
        if (reply?.room?.id == room.id && room.revision < (reply?.room?.revision ?: 0)) return
        val needsSave = library.snapshots[room.id]?.deletedHistoryNumbers != next.deletedHistoryNumbers || library.snapshots[room.id]?.room?.revision != room.revision || platform.now() - (library.snapshots[room.id]?.serverTime ?: 0) > 60000
        offset = next.serverTime - (sentAt + platform.now()) / 2
        val previousRoom = reply?.room
        val changedOrder = previousRoom?.id != room.id || previousRoom.orderNumber != room.orderNumber
        val changedFees = previousRoom?.fees != room.fees
        val cached = library.snapshots[room.id]
        val combined = mergePaymentHistory(cached, next)
        if (needsSave || library.pending == null && next.token.isNotEmpty()) {
            replaceLibrary(library.copy(snapshots = library.snapshots + (room.id to combined.copy(token = ""))))
        }
        reply = combined; online = active
        if (room.phase == RoomPhase.COLLECTING && room.spin == null && room.payerId == s.memberId)
            alert("selected:${room.id}:${room.orderNumber}", "You're selected!", room.name)
        if (changedOrder || changedFees) formDrafts.acceptRoomFees(room, page, libraryReturnPage, draft)
        acceptFinancialDrafts(previousRoom, room, changedOrder)
        if (page in listOf(GroupPage.ITEM, GroupPage.CUSTOM_ITEM, GroupPage.ACCOUNT) && changedOrder) {
            page = GroupPage.ROOM; selectedItem = null; editingCartLineId = null; selectedAccount = null
            error = "A new order has started. Review it before choosing food or a receiving account."
        } else if (page in listOf(GroupPage.ITEM, GroupPage.CUSTOM_ITEM) && (room.phase !in listOf(RoomPhase.LOBBY, RoomPhase.PREPARING_SPIN, RoomPhase.SPINNING, RoomPhase.ACCEPTING, RoomPhase.COLLECTING) || previousRoom?.restaurant != room.restaurant)) {
            page = GroupPage.ROOM; selectedItem = null; editingCartLineId = null
            error = "Ordering or the menu changed. Review the latest order before choosing food."
        } else if (page == GroupPage.ACCOUNT && room.phase !in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)) {
            page = GroupPage.ROOM
            error = "The order has moved on. Its receiving account can no longer be changed."
        }
    }
    private fun startWatching() {
        if (!active) return
        val s = session ?: return
        watching?.cancel(); val epoch = ++generation
        val request = RoomCommand(commandId = platform.uuid(), kind = CommandKind.SNAPSHOT, roomId = s.roomId, token = s.token)
        watching = platform.watch(s.hub, encodeCommand(request), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                if (epoch != generation) return
                if (error.isNotBlank()) { online = false; this@GroupController.error = error; publish(); return }
                try {
                    val next = decodeReply(body)
                    if (!next.ok) { this@GroupController.error = next.error; online = false } else {
                        accept(next, platform.now())
                        if (this@GroupController.error.startsWith("Connection paused") || this@GroupController.error.startsWith("Hub unavailable")) this@GroupController.error = ""
                        val r = reply?.room ?: return
                        if (r.phase == RoomPhase.PREPARING_SPIN && r.orderingMembers.any { it.id == me() } && me() !in r.preparedIds && !busy && library.pending == null) {
                            command(CommandKind.ACK_SPIN, text = r.preparationId)
                        }
                    }
                } catch (e: Exception) { this@GroupController.error = e.message ?: "Could not synchronize this room." }
                publish()
            }
        })
    }
    private fun openProfile() {
        library.home?.profile?.let { profile ->
            draft[GroupFieldKey.NAME] = profile.name; draft[GroupFieldKey.PROFILE_PHONE] = profile.phone
            draft[GroupFieldKey.PHOTO] = profile.photo; draft[GroupFieldKey.DISCOVERABLE] = profile.discoverable.toString()
            selectedAccount = profile.payment
            listOf(GroupFieldKey.ACCOUNT_HOLDER, GroupFieldKey.ACCOUNT_BANK, GroupFieldKey.ACCOUNT_IDENTIFIER, GroupFieldKey.ACCOUNT_IBAN_DRAFT, GroupFieldKey.ACCOUNT_AANI_DRAFT).forEach(draft::remove)
            draft[GroupFieldKey.AANI] = "true"
            profile.payment?.let { seedAccount(it) }
        }
        if (library.selectedHub == null) { accountEditing = true; page = GroupPage.CONNECT; seedHub() }
        else page = GroupPage.PROFILE
    }
    private fun profileDraft(): FoodProfile {
        val name = text(GroupFieldKey.NAME).trim()
        val account = text(GroupFieldKey.ACCOUNT_IDENTIFIER).trim().takeIf { it.isNotEmpty() }?.let {
            ReceivingAccount(library.home?.profile?.payment?.id ?: platform.uuid(), text(GroupFieldKey.ACCOUNT_HOLDER).trim().ifEmpty { name },
                if(flag(GroupFieldKey.AANI)) "Aani" else text(GroupFieldKey.ACCOUNT_BANK).trim(), it,
                method = if(flag(GroupFieldKey.AANI)) PaymentMethod.AANI else PaymentMethod.BANK)
        }
        return FoodProfile(name = name, phone = text(GroupFieldKey.PROFILE_PHONE).trim(), photo = text(GroupFieldKey.PHOTO).trim(), payment = account,
            discoverable = text(GroupFieldKey.DISCOVERABLE) != "false", language = library.language,
            favoriteOrders = library.home?.profile?.favoriteOrders.orEmpty()).normalized().also { it.validate() }
    }
    internal fun saveProfileUpdate(profile: FoodProfile) = identity(IdentityAction.SAVE_PROFILE, profileOverride = profile, returnPage = page)
    private fun identity(action: IdentityAction, value: String = "", profileOverride: FoodProfile? = null, returnPage: GroupPage? = null) {
        val hub = if (action in listOf(IdentityAction.SIGN_IN, IdentityAction.REGISTER, IdentityAction.RESET_PASSWORD, IdentityAction.FIREBASE_SIGN_IN)) requireNotNull(library.selectedHub) else requireNotNull(library.identityHub ?: library.selectedHub)
        if (action == IdentityAction.INVITE) require(sameHub(session?.hub, hub)) { "Sign in on this room's server before inviting registered people." }
        val request = IdentityRequest(action, text(GroupFieldKey.EMAIL).trim(), text(GroupFieldKey.PASSWORD),
            if(action in listOf(IdentityAction.REGISTER, IdentityAction.SAVE_PROFILE)) profileOverride ?: profileDraft() else null,
            userId = if(action == IdentityAction.INVITE) value else "", invitationId = if(action == IdentityAction.ACCEPT_INVITE) value else "", firebaseToken = if(action == IdentityAction.FIREBASE_SIGN_IN) value else "")
        val c = RoomCommand(commandId = platform.uuid(), kind = CommandKind.IDENTITY, identity = request,
            identityToken = library.identityToken, roomId = session?.roomId ?: "", token = session?.token ?: "")
        busy = true; publish()
        // Passwords are deliberately never written into pending commands or secure storage.
        platform.request(hub, encodeCommand(c), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                busy = false; draft.remove(GroupFieldKey.PASSWORD)
                try {
                    require(error.isEmpty()) { error }
                    val result = decodeReply(body); require(result.ok) { result.error }
                    if(action == IdentityAction.SIGN_OUT) {
                        stopHomeWatching(); watching?.cancel(); watching = null; generation++
                        session = null; reply = null
                        replaceLibrary(library.copy(identityToken = "", identityHub = null, home = null, sessions = emptyList(), snapshots = emptyMap(), accounts = emptyList(), displayName = ""))
                        draft.clear(); page = GroupPage.HOME
                    } else if(action == IdentityAction.ACCEPT_INVITE) {
                        val room = requireNotNull(result.room)
                        val saved = StoredSession(hub, room.id, result.token, result.memberId, room.name)
                        replaceLibrary(library.copy(sessions = library.sessions.filterNot { it.roomId == saved.roomId } + saved))
                        resume(saved)
                    } else if(result.home != null) {
                        val home = requireNotNull(result.home)
                        replaceLibrary(library.copy(identityToken = result.identityToken.ifEmpty { library.identityToken }, identityHub = hub))
                        acceptHome(home, hub); page = returnPage ?: if(action == IdentityAction.INVITE) GroupPage.PEOPLE else GroupPage.HOME
                    } else this@GroupController.error = "If an account exists, a password reset email has been requested."
                    startHomeWatching()
                } catch(e: Exception) { this@GroupController.error = e.message ?: "Account update failed. Retry." }
                publish()
            }
        })
    }
    private fun alert(id: String, title: String, body: String) {
        if (id in library.seenAlerts) return
        replaceLibrary(library.copy(seenAlerts = (library.seenAlerts + id).takeLast(100)))
        platform.notify(title, body)
    }
    private fun acceptHome(home: HomePayload, hub: HubPairing) {
        val sessions = home.rooms.map { StoredSession(hub, it.roomId, it.token, it.memberId, it.roomName) }
        home.deletedRoomIds.forEach { id -> roomWatches.remove(id)?.cancel() }
        if(session?.roomId in home.deletedRoomIds) { watching?.cancel(); watching = null; generation++; session = null; reply = null; page = GroupPage.HOME }
        val catalog = mergeManagedRestaurantCatalog(library.restaurants, library.managedRestaurantIds, home.restaurants, deletedRestaurantIds = home.deletedRestaurantIds)
        val updated = library.copy(home = home, displayName = home.profile.name, language = home.profile.language,
            sessions = library.sessions.filterNot { old -> sessions.any { it.roomId == old.roomId } || old.roomId in home.deletedRoomIds } + sessions,
            snapshots = library.snapshots.filterKeys { it !in home.deletedRoomIds },
            restaurants = catalog.restaurants,
            managedRestaurantIds = catalog.managedIds)
        if (updated != library) replaceLibrary(updated)
        home.invitations.forEach { alert("invite:${it.id}", "Join ${it.roomName}", "${it.invitedBy} invited you. Open Food Run and tap Join on your home screen.") }
    }
    private fun stopHomeWatching() {
        homeGeneration++; homeWatching?.cancel(); homeWatching = null
        roomWatches.values.forEach { it.cancel() }; roomWatches.clear()
    }
    private fun startHomeWatching() {
        if (!active) return
        stopHomeWatching(); val epoch = homeGeneration
        val hub = library.identityHub
        if (hub != null && library.identityToken.isNotEmpty()) {
            val request = RoomCommand(commandId = platform.uuid(), kind = CommandKind.HOME, identityToken = library.identityToken)
            homeWatching = platform.watch(hub, encodeCommand(request), object : GroupReplyCallback {
                override fun complete(body: String, error: String) {
                    if(epoch != homeGeneration || error.isNotEmpty()) return
                    try { val next = decodeReply(body); if (next.ok) next.home?.let { acceptHome(it, hub); watchSavedRooms(epoch); publish() } }
                    catch (_: Exception) { /* Existing home stays available; the next update retries. */ }
                }
            })
        }
        watchSavedRooms(epoch)
    }
    private fun watchSavedRooms(epoch: Int) {
        library.sessions.forEach { saved ->
            // The room currently open on screen already has its authoritative subscription.
            // A second subscription would duplicate writes and can race the visible state.
            if (saved.roomId == session?.roomId) return@forEach
            if (roomWatches.containsKey(saved.roomId)) return@forEach
            val request = RoomCommand(commandId = platform.uuid(), kind = CommandKind.SNAPSHOT, roomId = saved.roomId, token = saved.token)
            roomWatches[saved.roomId] = platform.watch(saved.hub, encodeCommand(request), object : GroupReplyCallback {
                override fun complete(body: String, error: String) {
                    if(epoch != homeGeneration || error.isNotEmpty()) return
                    try {
                        val next = decodeReply(body); val room = next.room ?: return
                        if (!next.ok || next.memberId != saved.memberId || room.id != saved.roomId) return
                        if ((library.snapshots[room.id]?.room?.revision ?: 0) <= room.revision) {
                            if (library.snapshots[room.id]?.room?.revision != room.revision || library.snapshots[room.id]?.deletedHistoryNumbers != next.deletedHistoryNumbers)
                                replaceLibrary(library.copy(snapshots = library.snapshots + (room.id to mergePaymentHistory(library.snapshots[room.id], next).copy(token = ""))))
                            val spin = room.spin
                            if (room.phase == RoomPhase.ACCEPTING && spin?.winnerId == saved.memberId)
                                alert("selected:${spin.id}", "You're selected!", "Join ${room.name} to accept and collect everyone's food order.")
                            if (room.phase == RoomPhase.COLLECTING && room.spin == null && room.payerId == saved.memberId)
                                alert("selected:${room.id}:${room.orderNumber}", "You're selected!", room.name)
                            if (room.phase == RoomPhase.PREPARING_SPIN && saved.memberId !in room.preparedIds && room.orderingMembers.any { it.id == saved.memberId } && session?.roomId != saved.roomId) {
                                platform.request(saved.hub, encodeCommand(request.copy(commandId = platform.uuid(), kind = CommandKind.ACK_SPIN, expectedRevision = room.revision, expectedOrderNumber = room.orderNumber, text = room.preparationId)), object : GroupReplyCallback { override fun complete(body: String, error: String) {} })
                            }
                            publish()
                        }
                    } catch (_: Exception) { /* Reconnect keeps the last verified snapshot. */ }
                }
            })
        }
    }
    internal fun pairingLink(hub: HubPairing): String = if (hub.fingerprint.isEmpty()) hub.url else
        "foodrun://pair?host=${hub.url.substringAfter("https://").substringBefore(':')}&port=${hub.url.substringAfterLast(':')}&fingerprint=${hub.fingerprint}"
    private fun queryEncode(value: String): String = value.encodeToByteArray().joinToString("") { byte ->
            val number = byte.toInt() and 0xff
            val character = number.toChar()
            if (character.isLetterOrDigit() || character in "-._~") character.toString()
            else "%" + number.toString(16).uppercase().padStart(2, '0')
        }
    internal fun roomInviteLink(hub: HubPairing, code: String): String =
        "https://intrvioo.com/?room=${queryEncode(code)}&hub=${queryEncode(hub.url)}"
    internal fun sameHub(first: HubPairing?, second: HubPairing?): Boolean {
        if (first == null || second == null) return false
        return if (first.fingerprint.isNotEmpty() || second.fingerprint.isNotEmpty())
            first.fingerprint.isNotEmpty() && first.fingerprint == second.fingerprint
        else first.url.trimEnd('/').equals(second.url.trimEnd('/'), ignoreCase = true)
    }
    private fun loadOlderHistory() {
        val s = requireNotNull(session); val offset = reply?.historyNextOffset ?: -1
        require(offset >= 0) { "All available history is downloaded." }; busy = true
        val request = RoomCommand(commandId = platform.uuid(), kind = CommandKind.SNAPSHOT, roomId = s.roomId, token = s.token, historyOffset = offset)
        try { platform.request(s.hub, encodeCommand(request), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                busy = false
                if(session?.roomId != s.roomId) return
                try {
                    require(error.isEmpty()) { error }
                    val response = decodeReply(body); require(response.ok) { response.error }
                    require(response.protocolVersion == 1 && response.room?.id == s.roomId && response.memberId == s.memberId) { "Hub returned a different room or unsupported history format." }
                    val history = ((reply?.history ?: emptyList()) + response.history).filterNot { it.number in response.deletedHistoryNumbers }.distinctBy { it.number }.sortedByDescending { it.number }
                    val combined = requireNotNull(reply).copy(history = history, historyNextOffset = response.historyNextOffset)
                    replaceLibrary(library.copy(snapshots = library.snapshots + (s.roomId to combined)))
                    reply = combined
                } catch(e: Exception) { this@GroupController.error = e.message ?: "History could not be downloaded. Retry when connected." }
                publish()
            }
        }) } catch (failure: Exception) {
            busy = false
            error = failure.message ?: "History could not be downloaded. Retry when connected."
            publish()
        }
    }
    fun tickAccessBlock() { if (accessBlock != null) publish() }
    private fun encodeCommand(command: RoomCommand): String = orderJson.encodeToString(command.copy(selectionDetails = true))
    private fun decodeReply(body: String): RoomReply = try { orderJson.decodeFromString<RoomReply>(body).also {
        if (it.accessBlock != null && (it.accessBlock!!.roomId == session?.roomId || page in listOf(GroupPage.SETUP, GroupPage.CONNECT))) { accessBlock = it.accessBlock; blockClockOffset = it.serverTime - platform.now(); publish() }
        else if (it.ok && it.room != null && accessBlock?.roomId == it.room!!.id) accessBlock = null
    } }
        catch (_: SerializationException) { error("The hub response could not be read. Update the app and hub to matching versions, then retry.") }
}
