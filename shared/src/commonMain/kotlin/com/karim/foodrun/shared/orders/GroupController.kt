package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.SerializationException

class GroupController(val platform: GroupPlatform) {
    internal var library = GroupLibrary()
    internal var page = GroupPage.HOME
    internal var draft = mutableMapOf<GroupFieldKey, String>()
    internal val formDrafts = GroupDraftMemory()
    internal var error = ""
    internal var busy = false
    internal var online = false
    internal var reply: RoomReply? = null
    internal var session: StoredSession? = null
    internal var selectedRestaurant: RestaurantExport? = null
    internal var editingRestaurant: RestaurantExport? = null
    internal var editingRoomOrder: Pair<String, Long>? = null
    internal var importPreview: RestaurantExport? = null
    internal var selectedItem: MenuItem? = null
    internal var variant: String? = null
    internal var options = emptyList<String>()
    internal var selectedAccount: ReceivingAccount? = null
    internal var joinMode = false
    internal var nextOrder = false
    private var observer: GroupObserver? = null
    private var watching: GroupSubscription? = null
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
            if (library.pending != null && library.pendingHub == null) library = library.copy(pendingHub = library.selectedHub)
            draft[GroupFieldKey.NAME] = library.displayName
        } catch (_: Exception) { storageReadable = false; error = "Saved group data could not be opened. Restore its backup before creating new data." }
    }
    fun observe(observer: GroupObserver) { this.observer = observer; publish() }
    fun removeObserver(observer: GroupObserver) { if (this.observer === observer) this.observer = null }
    fun close() { watching?.cancel(); watching = null; observer = null; generation++ }
    fun foreground() { active = true; if (session != null) startWatching() }
    fun background() { active = false; watching?.cancel(); watching = null; online = false; generation++; publish() }
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
            } else draft[key] = value.take(if (key == GroupFieldKey.JSON_MENU) MenuValidation.MAX_BYTES else 4000)
        } catch (e: Exception) { error = e.message ?: "This change could not be saved." }
        publish()
    }
    fun dispatch(action: GroupAction, value: String = "") {
        if (busy && action !in listOf(GroupAction.BACK, GroupAction.REFRESH)) return
        try { error = ""; when (action) {
            GroupAction.BACK -> back()
            GroupAction.QUICK_SPIN -> page = GroupPage.QUICK_SPIN
            GroupAction.CREATE, GroupAction.JOIN -> { joinMode = action == GroupAction.JOIN; nextOrder = false; page = GroupPage.CONNECT; seedHub() }
            GroupAction.CONNECT -> connect()
            GroupAction.DISCOVER -> platform.discover(callback { body -> draft[GroupFieldKey.HUB_URL] = body; error = "Hub found. Scan its setup QR or paste its fingerprint to verify its identity."; publish() })
            GroupAction.SCAN -> platform.scanPairing(callback { body -> draft[GroupFieldKey.PAIRING_LINK] = body; connect() })
            GroupAction.OPEN_LIBRARY -> { libraryReturnPage = page; page = GroupPage.LIBRARY }
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
            GroupAction.SELECT_RESTAURANT -> { selectedRestaurant = library.restaurants.single { it.restaurant.id == value }; seedFees(selectedRestaurant!!.restaurant); joinMode = false; page = if(library.selectedHub == null && !nextOrder) GroupPage.CONNECT else GroupPage.SETUP }
            GroupAction.ADD_MENU_ITEM -> addMenuItem()
            GroupAction.REMOVE_MENU_ITEM -> { editingRestaurant = editingRestaurant?.let { it.copy(restaurant = it.restaurant.copy(menu = it.restaurant.menu.copy(items = it.restaurant.menu.items.filterNot { item -> item.id == value }))) } }
            GroupAction.SAVE_ROOM_RESTAURANT -> { val r = room().restaurant; saveRestaurant(RestaurantExport(exportId = r.id, restaurant = r)); error = "Restaurant saved to your library." }
            GroupAction.UPDATE_ROOM_MENU -> command(CommandKind.UPDATE_RESTAURANT, restaurant = library.restaurants.single { it.restaurant.id == value }.restaurant, text = text(GroupFieldKey.REASON))
            GroupAction.RESUME -> resume(library.sessions.single { it.roomId == value })
            GroupAction.CREATE_ROOM -> createRoom()
            GroupAction.JOIN_ROOM -> send(RoomCommand(commandId = platform.uuid(), kind = CommandKind.JOIN, code = text(GroupFieldKey.ROOM_CODE).trim(), name = text(GroupFieldKey.NAME).trim(), guest = flag(GroupFieldKey.GUEST)))
            GroupAction.RETRY -> { val pending = library.pending; if (pending != null) send(pending, true) else startWatching() }
            GroupAction.REFRESH -> startWatching()
            GroupAction.OPEN_ITEM -> { selectedItem = room().restaurant.menu.items.single { it.id == value }; variant = selectedItem!!.variants.firstOrNull()?.id; options = emptyList(); draft[GroupFieldKey.QUANTITY] = "1"; draft[GroupFieldKey.NOTE] = ""; page = GroupPage.ITEM }
            GroupAction.SELECT_VARIANT -> variant = value
            GroupAction.TOGGLE_OPTION -> options = if (value in options) options - value else options + value
            GroupAction.ADD_CART_ITEM -> addCartItem()
            GroupAction.REMOVE_CART_ITEM -> { val cart = myCart(); command(CommandKind.CART, cart = cart.copy(lines = cart.lines.filterNot { it.id == value }), revision = cart.revision) }
            GroupAction.OPEN_ACCOUNT -> {
                reply?.room?.account?.let { selectedAccount = it; seedAccount(it) }
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
            GroupAction.SHARE_RESTAURANT_ORDER -> platform.share(GroupPresentation(this).restaurantOrderText(), "")
            GroupAction.SHARE_ROOM -> { val s = requireNotNull(session); platform.share("Food Run · ${room().name}\nRoom code: ${room().code}\n${pairingLink(s.hub)}\nJoin once; this room stays in your app.", "") }
            GroupAction.SHARE_RECEIPT -> platform.share(GroupPresentation(this).receiptText(value), "receipt.txt")
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
        require(url.matches(Regex("https://[a-zA-Z0-9.-]+:[0-9]{1,5}"))) { "Enter the hub address, for example https://192.168.1.20:8443." }
        require(url.substringAfterLast(':').toIntOrNull() in 1..65535) { "Hub port must be between 1 and 65535." }
        require(fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Paste the server's full SHA-256 fingerprint or scan its pairing QR." }
        val hub = HubPairing(url, fingerprint)
        val previousSession = session
        replaceLibrary(library.copy(
            selectedHub = hub,
            sessions = library.sessions.map { if (it.hub.fingerprint == fingerprint) it.copy(hub = hub) else it },
            pendingHub = library.pendingHub?.let { if (it.fingerprint == fingerprint) hub else it },
        ))
        session = previousSession?.let { old -> library.sessions.singleOrNull { it.roomId == old.roomId } ?: old }
        page = GroupPage.SETUP
    }
    private fun createRoom() {
        val r = requireNotNull(selectedRestaurant) { "Choose or create a restaurant first." }.restaurant
        val names = text(GroupFieldKey.EXPECTED_NAMES).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val fees = fees(r.currency)
        if (nextOrder) command(CommandKind.NEXT_ORDER, restaurant = r, fees = fees, expectedNames = names, flag = flag(GroupFieldKey.DELIVERY), destination = text(GroupFieldKey.DESTINATION))
        else send(RoomCommand(commandId = platform.uuid(), kind = CommandKind.CREATE, name = text(GroupFieldKey.NAME).trim(), text = text(GroupFieldKey.ROOM_NAME).trim(), restaurant = r, expectedNames = names, flag = flag(GroupFieldKey.DELIVERY), destination = text(GroupFieldKey.DESTINATION), fees = fees))
    }
    private fun back() {
        val roomRestaurantEditor = page == GroupPage.RESTAURANT && editingRoomOrder != null
        if (page == GroupPage.RESTAURANT) {
            formDrafts.finishRestaurant(draft)
            editingRestaurant = null
            editingRoomOrder = null
        }
        page = when (page) {
            GroupPage.ITEM, GroupPage.ACCOUNT, GroupPage.RECEIPTS, GroupPage.HISTORY -> GroupPage.ROOM
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
    }
    internal fun command(kind: CommandKind, memberId: String = "", text: String = "", flag: Boolean = false, eligible: Boolean = false, revision: Long = room().revision, cart: MemberCart? = null, account: ReceivingAccount? = null, fees: FeePolicy? = null, amount: Long = 0, transferId: String = "", restaurant: Restaurant? = null, expectedNames: List<String> = emptyList(), destination: String = "", name: String = "") {
        val s = requireNotNull(session)
        send(RoomCommand(commandId = platform.uuid(), kind = kind, roomId = s.roomId, token = s.token, expectedRevision = revision, expectedOrderNumber = room().orderNumber, memberId = memberId, text = text, flag = flag, eligible = eligible, cart = cart, account = account, fees = fees, amount = amount, transferId = transferId, restaurant = restaurant, expectedNames = expectedNames, destination = destination, name = name))
    }
    private fun send(c: RoomCommand, retry: Boolean = false) {
        require(library.pending == null || retry) { "A previous request is awaiting confirmation. Retry it before making another change." }
        val requestSession = if(c.kind in listOf(CommandKind.CREATE, CommandKind.JOIN)) null else library.sessions.single { it.roomId == c.roomId }
        val hub = if (retry) requireNotNull(library.pendingHub ?: requestSession?.hub ?: library.selectedHub) else requestSession?.hub ?: requireNotNull(library.selectedHub)
        replaceLibrary(library.copy(pending = c, pendingHub = hub)); busy = true; publish()
        val sentAt = platform.now()
        try { platform.request(hub, orderJson.encodeToString(c), object : GroupReplyCallback {
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
                    if (c.kind in listOf(CommandKind.PLACE, CommandKind.PAY_RESTAURANT, CommandKind.DECLARE_TRANSFER, CommandKind.DECLARE_REFUND)) draft.remove(GroupFieldKey.REFERENCE)
                    page = GroupPage.ROOM; startWatching()
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
        val needsSave = library.snapshots[room.id]?.room?.revision != room.revision || platform.now() - (library.snapshots[room.id]?.serverTime ?: 0) > 60000
        offset = next.serverTime - (sentAt + platform.now()) / 2
        val previousRoom = reply?.room
        val changedOrder = previousRoom?.id != room.id || previousRoom.orderNumber != room.orderNumber
        val changedFees = previousRoom?.fees != room.fees
        val cached = library.snapshots[room.id]
        val combinedHistory = (next.history + (cached?.history ?: emptyList())).distinctBy { it.number }.sortedByDescending { it.number }
        val nextOffset = if (cached?.room?.orderNumber == room.orderNumber && cached.history.size > next.history.size) cached.historyNextOffset else next.historyNextOffset
        val combined = next.copy(history = combinedHistory, historyNextOffset = nextOffset)
        if (needsSave || library.pending == null && next.token.isNotEmpty()) {
            replaceLibrary(library.copy(snapshots = library.snapshots + (room.id to combined.copy(token = ""))))
        }
        reply = combined; online = active
        if (changedOrder || changedFees) formDrafts.acceptRoomFees(room, page, libraryReturnPage, draft)
        acceptFinancialDrafts(previousRoom, room, changedOrder)
        if (page in listOf(GroupPage.ITEM, GroupPage.ACCOUNT) && changedOrder) {
            page = GroupPage.ROOM; selectedItem = null; selectedAccount = null
            error = "A new order has started. Review it before choosing food or a receiving account."
        } else if (page == GroupPage.ITEM && (room.phase != RoomPhase.COLLECTING || previousRoom?.restaurant?.menu != room.restaurant.menu)) {
            page = GroupPage.ROOM; selectedItem = null
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
        watching = platform.watch(s.hub, orderJson.encodeToString(request), object : GroupReplyCallback {
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
    internal fun pairingLink(hub: HubPairing): String = "foodrun://pair?host=${hub.url.substringAfter("https://").substringBefore(':')}&port=${hub.url.substringAfterLast(':')}&fingerprint=${hub.fingerprint}"
    private fun loadOlderHistory() {
        val s = requireNotNull(session); val offset = reply?.historyNextOffset ?: -1
        require(offset >= 0) { "All available history is downloaded." }; busy = true
        val request = RoomCommand(commandId = platform.uuid(), kind = CommandKind.SNAPSHOT, roomId = s.roomId, token = s.token, historyOffset = offset)
        try { platform.request(s.hub, orderJson.encodeToString(request), object : GroupReplyCallback {
            override fun complete(body: String, error: String) {
                busy = false
                if(session?.roomId != s.roomId) return
                try {
                    require(error.isEmpty()) { error }
                    val response = decodeReply(body); require(response.ok) { response.error }
                    require(response.protocolVersion == 1 && response.room?.id == s.roomId && response.memberId == s.memberId) { "Hub returned a different room or unsupported history format." }
                    val history = ((reply?.history ?: emptyList()) + response.history).distinctBy { it.number }.sortedByDescending { it.number }
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
    private fun decodeReply(body: String): RoomReply = try { orderJson.decodeFromString(body) }
        catch (_: SerializationException) { error("The hub response could not be read. Update the app and hub to matching versions, then retry.") }
}
