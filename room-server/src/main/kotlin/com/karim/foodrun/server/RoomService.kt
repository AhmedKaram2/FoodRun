package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class RoomService(private val db: RoomDatabase, private val clock: () -> Long = System::currentTimeMillis, private val random: SecureRandom = SecureRandom(), private val identityProvider: IdentityProvider? = null) {
    private val accounts = AccountService(db, identityProvider, this, clock)
    fun validateFirebaseSignIn(token: String) { requireNotNull(identityProvider) { "Firebase is unavailable." }.exchange(token) }
    val storageAvailable: Boolean get() = db.available
    val storageMode: String get() = if (db.cloudDurable) "firestore" else "sqlite"
    @Synchronized fun syncCloud() = accounts.syncCloud()
    @Synchronized fun adminCancel(roomId: String) {
        val room = requireNotNull(db.room(roomId)) { "Room was not found." }
        require(room.phase !in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED)) { "Room is already complete." }
        require(room.phase !in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED)) { "Placed orders must be settled and archived. Cancellation would hide outstanding payments." }
        db.save(room.copy(phase = RoomPhase.CANCELLED, revision = room.revision + 1, updatedAt = clock()))
        signalRoom(roomId)
    }
    private val presence = mutableMapOf<String, Long>()
    private val roomEvents = mutableMapOf<String, Long>()
    private var homeEvents = 1L
    private var eventSequence = 1L
    private val reducer = RoomReducer(::uuid, random::nextInt)
    init {
        // Admit members left waiting by older hubs without changing a locked order's participants.
        db.transaction {
            db.allRooms().filter { room -> room.members.any { !it.approved && !it.removed } }.forEach { room ->
                val members = room.members.map { if (!it.approved && !it.removed) admit(it, room.phase) else it }
                val addedOrderers = members.any { member -> member.participating && !member.guest && room.members.any { it.id == member.id && !it.approved } }
                db.save(room.copy(members = members, revision = room.revision + 1,
                    quoteRevision = room.quoteRevision + if (addedOrderers) 1 else 0, updatedAt = clock()))
            }
            db.allRooms().filter { it.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW) }.forEach { room ->
                val repaired = recoverTaxResetSubmissions(room, db::recordedReply)
                if (repaired != room) db.save(repaired.copy(updatedAt = clock()))
            }
        }
    }
    private fun admit(member: Member, phase: RoomPhase): Member {
        val participating = !member.guest && member.participating && phase in listOf(RoomPhase.LOBBY, RoomPhase.COLLECTING)
        return member.copy(approved = true, participating = participating, ready = participating,
            eligible = participating, latePayerApproved = false)
    }
    @Synchronized fun eventVersion(kind: CommandKind, roomId: String): Long =
        if (kind == CommandKind.HOME) homeEvents else roomEvents[roomId] ?: 0L
    @Synchronized fun touch(memberId: String) { if (memberId.isNotEmpty()) presence[memberId] = clock() }
    private fun signalRoom(roomId: String) {
        roomEvents[roomId] = ++eventSequence
        homeEvents = eventSequence
    }
    private fun signalHome() { homeEvents = ++eventSequence }
    @Synchronized fun execute(c: RoomCommand): RoomReply = try {
        require(c.roomId.length <= 160 && c.token.length <= 128 && c.code.length <= 16 && c.memberId.length <= 160 && c.transferId.length <= 160) { "Invalid request identifier." }
        require(c.name.length <= 160 && c.text.length <= 500 && c.destination.length <= 1000 && c.expectedNames.size <= 30) { "Request fields exceed the supported length." }
        require(c.restaurants.size <= 12) { "A restaurant poll supports up to 12 choices." }
        require(c.historyOffset in 0..1_000_000) { "Invalid history page." }
        require(c.protocolVersion == 1) { "Update Food Run: this protocol version is unsupported." }
        require(c.commandId.matches(Regex("[A-Za-z0-9-]{16,80}"))) { "Invalid command ID." }
        if (c.kind == CommandKind.IDENTITY) db.transaction { accounts.execute(c) }.also { reply ->
            signalHome()
            reply.room?.let { signalRoom(it.id) }
        }
        else if (c.kind == CommandKind.HOME) accounts.home(c.identityToken)
        else if (c.kind == CommandKind.SNAPSHOT) snapshot(c.roomId, c.token, c.historyOffset)
        else db.transaction {
            if (c.kind in listOf(CommandKind.CREATE, CommandKind.JOIN, CommandKind.CREATE_PAYMENT_ROOM)) {
                require(!c.guest) { "Guest mode is unavailable. Sign in to join the room." }
                if (identityProvider != null) accounts.userId(c.identityToken)
            }
            if (c.identityToken.isNotEmpty()) accounts.userId(c.identityToken)
            if (c.kind !in listOf(CommandKind.CREATE, CommandKind.JOIN, CommandKind.CREATE_PAYMENT_ROOM)) authenticate(c.roomId, c.token)
            val digest = hash(orderJson.encodeToString(c))
            db.previous(c.commandId, digest) ?: run {
                val reply = when(c.kind) {
                    CommandKind.CREATE_PAYMENT_ROOM -> createPaymentRoom(c)
                    CommandKind.CREATE -> create(c)
                    CommandKind.JOIN -> join(c)
                    CommandKind.REQUEST_BLOCK -> requestBlock(c)
                    else -> {
                        val actor = authenticate(c.roomId, c.token)
                        presence[actor] = clock()
                        val room = withPresence(finalizeSpin(requireNotNull(db.room(c.roomId))))
                        val result = reducer.apply(room, actor, c, clock())
                        if (c.kind == CommandKind.NEXT_ORDER) db.archive(room)
                        requireLoadable(result)
                        db.save(result)
                        signalRoom(result.id)
                        projection(result, actor)
                    }
                }
                if (c.identityToken.isNotEmpty() && reply.token.isNotEmpty()) accounts.link(c.identityToken, reply)
                db.record(c.commandId, digest, reply); reply
            }
        }
    } catch (e: AccountBlockedException) { RoomReply(ok = false, error = e.message.orEmpty(), code = if(e.block.removed) "ACCOUNT_BLOCKED" else "ROOM_BLOCKED", accessBlock = e.block, serverTime = clock()) }
      catch (e: IllegalArgumentException) { RoomReply(ok = false, error = e.message ?: "Invalid request.", code = "VALIDATION", serverTime = clock()) }
      catch (e: IllegalStateException) { RoomReply(ok = false, error = e.message ?: "Action unavailable.", code = "STATE", serverTime = clock()) }

    @Synchronized fun snapshot(roomId: String, token: String, historyOffset: Int = 0): RoomReply {
        val actor = authenticate(roomId, token)
        val room = finalizeSpin(requireNotNull(db.room(roomId)))
        presence[actor] = clock()
        return projection(withPresence(room), actor, historyOffset)
    }
    @Synchronized fun tick() {
        db.transaction { db.spinningRooms().forEach { room ->
            if (room.phase == RoomPhase.PREPARING_SPIN) {
                val candidates = room.orderingMembers.filter { it.eligible }.map { it.id }
                if (candidates.isNotEmpty()) db.save(room.copy(phase = RoomPhase.SPINNING, spin = SpinRound(uuid(), candidates, candidates[random.nextInt(candidates.size)], clock() + 3000), revision = room.revision + 1)).also { signalRoom(room.id) }
            } else finalizeSpin(room)
        } }
        presence.entries.removeIf { clock() - it.value > 5 * 60_000 }
    }
    private fun withPresence(room: Room): Room = room.copy(members = room.members.map { member -> member.copy(lastSeen = presence[member.id] ?: member.lastSeen) })
    private fun finalizeSpin(room: Room): Room {
        if (room.phase != RoomPhase.SPINNING || clock() < (room.spin?.endAt ?: Long.MAX_VALUE)) return room
        return room.copy(phase = RoomPhase.ACCEPTING, revision = room.revision + 1, updatedAt = clock()).also {
            db.save(it)
            signalRoom(it.id)
        }
    }
    private fun create(c: RoomCommand): RoomReply {
        if(c.identityToken.isNotBlank()) AccountRestrictions.requireRoomAllowed(db, accounts.userId(c.identityToken), "", clock())
        val settings = db.record(AdminService.SETTINGS)?.let { orderJson.decodeFromString<AdminSettings>(it) } ?: AdminSettings()
        require(settings.roomCreationEnabled) { "New room creation is temporarily disabled by the administrator." }
        require(db.activeRoomCount() < 100) { "Hub has reached its active-room limit." }
        MenuValidation.label(c.name); MenuValidation.label(c.text)
        val person = Member(uuid(), c.name.trim(), approved = true, eligible = true, ready = true, lastSeen = clock())
        var code: String
        do { code = (100000 + random.nextInt(900000)).toString() } while (db.roomByCode(code) != null)
        val selected = requireNotNull(c.restaurant)
        val options = c.restaurants.ifEmpty { listOf(selected) }.distinctBy { it.id }
        require(options.size <= 12 && options.any { it.id == selected.id }) { "Choose up to 12 restaurants, including the current choice." }
        require(options.all { it.currency == selected.currency }) { "Restaurant poll choices must use the same currency." }
        options.forEach(MenuValidation::validate)
        val room = Room(uuid(), code, person.id, c.text.trim(), selected, c.expectedNames, c.flag, c.destination, c.deadline, (c.fees ?: FeePolicy()).copy(automaticDelivery = c.flag),
            members = listOf(person), createdAt = clock(), updatedAt = clock(), restaurantOptions = options,
            restaurantVotes = listOf(RestaurantVote(person.id, selected.id)), restaurantPollOpen = options.size > 1)
        RoomRules.validateRoom(room); requireLoadable(room); db.save(room)
        signalRoom(room.id)
        val token = token(); db.addSession(hash(token), room.id, person.id)
        return projection(room, person.id).copy(token = token)
    }
    private fun createPaymentRoom(c: RoomCommand): RoomReply {
        val uid = accounts.userId(c.identityToken)
        AccountRestrictions.requireRoomAllowed(db, uid, "", clock())
        val request = requireNotNull(c.paymentRoom) { "Enter the payment room details." }
        request.details.validate()
        val settings = db.record(AdminService.SETTINGS)?.let { orderJson.decodeFromString<AdminSettings>(it) } ?: AdminSettings()
        require(settings.roomCreationEnabled && db.activeRoomCount() < 100) { "New room creation is currently unavailable." }
        MenuValidation.label(c.text)
        require(request.shares.size in 2..30 && request.shares.map { it.userId }.distinct().size == request.shares.size) { "Choose yourself and 1–29 different people." }
        require(request.shares.any { it.userId == uid }) { "Include your own share, even if it is zero." }
        val profiles = request.shares.associate { share ->
            require(share.userId.length in 1..128)
            MenuValidation.label(share.description); MenuValidation.price(share.amount)
            require(share.received in 0..share.amount && (share.userId != uid || share.received == 0L)) { "Received payments must not exceed a person's share. Your own share needs no transfer." }
            share.userId to accounts.paymentRoomProfile(share.userId).also { require(share.userId == uid || it.discoverable) { "This person is not available in the users list." } }
        }
        require(c.amount > 0 && request.shares.sumOf { it.amount } == c.amount) { "The shares must add up to the receipt total." }
        val account = requireNotNull(c.account) { "Add your receiving details in your profile first." }.normalized().also { it.validate() }
        val ids = profiles.keys.associateWith { uuid() }
        val ownerId = ids.getValue(uid)
        var code: String
        do { code = (100000 + random.nextInt(900000)).toString() } while (db.roomByCode(code) != null)
        val room = Room(uuid(), code, ownerId, c.text.trim(), Restaurant("payment-room", c.name.trim().ifBlank { c.text.trim() }, openOrdering = true),
            phase = RoomPhase.FULFILLED, payerId = ownerId, account = account, restaurantPaid = true,
            restaurantReference = "Already ordered and paid by ${profiles.getValue(uid).name}",
            members = profiles.map { (userId, profile) -> Member(ids.getValue(userId), profile.name, approved = true, ready = true) },
            carts = request.shares.map { share -> MemberCart(ids.getValue(share.userId), submitted = true,
                lines = if (share.amount == 0L) emptyList() else listOf(CartLine(uuid(), "", 1, description = share.description.trim(), unitPrice = share.amount))) },
            transfers = request.shares.filter { it.received > 0 }.map { Transfer(uuid(), ids.getValue(it.userId), it.received,
                "Payment received before this room was created", account, status = TransferStatus.CONFIRMED, createdAt = clock()) },
            paymentRoom = request.details, createdAt = clock(), updatedAt = clock(),
            audit = listOf(AuditEntry(c.commandId, ownerId, c.kind.name, "Already ordered; shares and received payments recorded by payer", clock())))
        RoomRules.validateRoom(room); Billing.receipts(room); requireLoadable(room); db.save(room)
        var ownerToken = ""
        profiles.keys.forEach { userId ->
            val token = token(); db.addSession(hash(token), room.id, ids.getValue(userId))
            accounts.linkPaymentMember(userId, room, ids.getValue(userId), token)
            if (userId == uid) ownerToken = token
        }
        signalRoom(room.id)
        return projection(room, ownerId).copy(token = ownerToken)
    }
    private fun join(c: RoomCommand): RoomReply {
        MenuValidation.label(c.name)
        require(c.code.matches(Regex("[0-9]{6}"))) { "Enter a six-digit room code." }
        val room = db.roomByCode(c.code) ?: error("Room code was not found.")
        if(c.identityToken.isNotBlank()) AccountRestrictions.requireRoomAllowed(db, accounts.userId(c.identityToken), room.id, clock())
        if (c.identityToken.isNotEmpty()) accounts.linked(c.identityToken, room.id)?.let { saved ->
            if (room.members.any { it.id == saved.memberId && !it.removed })
                return projection(room, saved.memberId).copy(token = saved.token)
        }
        require(room.paymentRoom == null) { "Payment rooms are private to the people selected by the organizer." }
        require(room.activeMembers.count { it.guest == c.guest } < if (c.guest) 10 else 30) { "Room capacity reached." }
        require(room.members.none { !it.removed && it.name.equals(c.name.trim(), true) }) { "This name is already in the room. Resume your saved session or use a distinct name." }
        val person = admit(Member(uuid(), c.name.trim(), guest = c.guest, lastSeen = clock()), room.phase)
        val next = room.copy(members = room.members + person, revision = room.revision + 1,
            quoteRevision = room.quoteRevision + if (person.participating) 1 else 0, updatedAt = clock()); requireLoadable(next); db.save(next)
        signalRoom(next.id)
        val token = token(); db.addSession(hash(token), room.id, person.id)
        return projection(next, person.id).copy(token = token)
    }
    private fun requestBlock(c: RoomCommand): RoomReply {
        val actor = authenticate(c.roomId, c.token)
        val room = requireNotNull(db.room(c.roomId))
        require(actor == room.ownerId) { "Only the room owner can request a block." }
        val requesterId = accounts.userId(c.identityToken)
        require(db.record("member-user:${room.id}:$actor") == requesterId ||
            db.record("membership:$requesterId:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it).memberId == actor } == true) { "Sign in with the room owner's account." }
        require(c.expectedOrderNumber == room.orderNumber && c.expectedRevision == room.revision) { "Refresh this room before requesting a block." }
        require(c.amount in 1..8760 && c.text.trim().length in 5..300) { "Choose 1 to 8760 hours and give a reason of 5 to 300 characters." }
        val target = RoomRules.member(room, c.memberId)
        require(target.id != actor) { "Choose another room member." }
        val targetId = db.record("member-user:${room.id}:${target.id}") ?: db.records("membership:").firstOrNull { (_, body) ->
            orderJson.decodeFromString<AccountRoom>(body).let { it.roomId == room.id && it.memberId == target.id }
        }?.first?.removePrefix("membership:")?.substringBefore(':') ?: error("This member has no registered account.")
        val pending = db.records("admin:block-request:").map { orderJson.decodeFromString<AdminBlockRequest>(it.second) }
        require(pending.none { it.roomId == room.id && it.userId == targetId && it.status == "pending" }) { "A block request for this member is already waiting for admin review." }
        require(pending.count { it.requesterId == requesterId && it.createdAt > clock() - 86_400_000 } < 10) { "You can submit up to 10 block requests per day." }
        val request = AdminBlockRequest(c.commandId, room.id, room.name, requesterId, room.members.single { it.id == actor }.name,
            targetId, target.name, c.text.trim(), c.amount.toInt(), clock())
        db.putRecord("admin:block-request:${request.id}", orderJson.encodeToString(request))
        return projection(room, actor)
    }
    @Synchronized fun adminChanged(roomIds: List<String> = emptyList()) {
        signalHome(); roomIds.forEach(::signalRoom)
    }
    private fun authenticate(roomId: String, token: String): String {
        require(token.length in 32..128) { "Room credentials are missing. Join or resume this order." }
        val session = db.session(hash(token)) ?: error("Session was removed. Rejoin this room using its link or code.")
        require(session.first == roomId) { "This credential belongs to another room." }
        val userId = db.record("member-user:$roomId:${session.second}") ?: db.records("membership:").firstOrNull { (_, body) ->
            val membership = orderJson.decodeFromString<AccountRoom>(body)
            membership.roomId == roomId && membership.memberId == session.second
        }?.first?.removePrefix("membership:")?.substringBefore(':')
        userId?.let { AccountRestrictions.requireRoomAllowed(db, it, roomId, clock()) }
        RoomRules.member(requireNotNull(db.room(roomId)), session.second)
        return session.second
    }
    private fun baseProjection(room: Room, actor: String): RoomReply {
        val member = RoomRules.member(room, actor)
        val authorized = member.approved && !member.removed
        val orderer = authorized && !member.guest && member.participating
        val payer = authorized && room.payerId == actor
        val receipts = if (orderer) Billing.receipts(room).filter { payer || it.memberId == actor } else emptyList()
        val visible = if (!authorized) room.copy(
            restaurant = Restaurant("pending", "Waiting for organizer approval"), expectedNames = emptyList(),
            destination = "", fees = FeePolicy(), members = listOf(member), carts = emptyList(), spin = null,
            pastSpins = emptyList(), payerId = null, account = null, transfers = emptyList(), audit = emptyList(),
            restaurantReference = "", preparationId = "", preparedIds = emptyList(), adjustmentApprovals = emptyList(),
            restaurantOptions = emptyList(), restaurantVotes = emptyList(), restaurantPollOpen = false, paymentRoom = null,
        ) else room.copy(
            // Legacy clients use this field as a payment gate. All members are exempt:
            // selected-payer price changes apply directly without another approval cycle.
            adjustmentApprovals = if (room.billRevision > 1)
                room.orderingMembers.map { it.id }
                else room.adjustmentApprovals,
            // Ordering progress is public; cart lines are private. Submitted/confirmation markers are safe.
            carts = room.carts.map { if (payer || (orderer && it.memberId == actor)) it else it.copy(lines = emptyList()) },
            account = room.account.takeIf { orderer },
            transfers = room.transfers.filter { orderer && (payer || it.memberId == actor) },
            audit = room.audit.filter { orderer && it.action in listOf("DECLINE_DUTY", "REMOVE", "REOPEN", "CANCEL", "ARCHIVE", "HANDOVER", "ADJUST_BILL", "UPDATE_RESTAURANT") },
        )
        return RoomReply(room = visible, memberId = actor, receipts = receipts, serverTime = clock(),
            progress = if (authorized) progress(room, actor) else null)
    }
    private fun progress(room: Room, actor: String): OrderProgress {
        val reviewStage = (actor == room.ownerId || actor == room.payerId) && room.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)
        val archiveStage = (actor == room.ownerId || actor == room.payerId) && room.phase == RoomPhase.FULFILLED
        fun blocker(validate: () -> Unit): String = try { validate(); "" }
            catch (invalid: IllegalArgumentException) { invalid.message ?: "Review the order before continuing." }
        val reviewBlocker = if (reviewStage) blocker { RoomRules.requirePlaceable(room) } else ""
        val archiveBlocker = if (archiveStage) blocker { RoomRules.requireArchive(room) } else ""
        return OrderProgress(accountShared = room.account != null,
            canReview = reviewStage && reviewBlocker.isEmpty(), reviewBlocker = reviewBlocker,
            canArchive = archiveStage && archiveBlocker.isEmpty(), archiveBlocker = archiveBlocker)
    }
    private fun requireLoadable(room: Room) {
        // Reserve half the native 4 MB response budget for a page of archived receipts.
        // Validate the payer's full projection even when another member performs the mutation.
        val viewers = listOfNotNull(room.payerId, room.ownerId).distinct()
        viewers.forEach { actor ->
            require(orderJson.encodeToString(baseProjection(room, actor)).toByteArray().size <= MAX_CURRENT_BYTES) {
                "This room is too large to sync safely. Use a smaller menu or fewer cart lines."
            }
        }
    }
    private fun projection(room: Room, actor: String, offset: Int = 0): RoomReply {
        require(offset in 0..1_000_000) { "Invalid history page." }
        val reply = baseProjection(room, actor).copy(deletedHistoryNumbers = db.deletedHistory(room.id))
        val member = RoomRules.member(room, actor)
        if (!member.approved || member.guest) return reply
        val orders = db.history(room.id, offset)
        val history = mutableListOf<PastOrder>()
        var consumed = 0
        for (old in orders.take(HISTORY_PAGE_SIZE)) {
            if (old.orderingMembers.any { it.id == actor }) {
                val past = PastOrder(old.orderNumber, old.restaurant.name, old.updatedAt,
                    if (old.phase == RoomPhase.CANCELLED) emptyList() else Billing.receipts(old).filter { it.memberId == actor || old.payerId == actor },
                    old.account.takeIf { old.phase != RoomPhase.CANCELLED },
                    old.restaurant.id,
                )
                val candidate = reply.copy(history = history + past, historyNextOffset = offset + consumed + 1)
                if (orderJson.encodeToString(candidate).toByteArray().size > MAX_REPLY_BYTES) break
                history.add(past)
            }
            consumed++
        }
        require(consumed > 0 || orders.isEmpty()) { "Archived receipt is too large to load. Contact the hub owner." }
        return reply.copy(history = history, historyNextOffset = if (orders.size > consumed) offset + consumed else -1)
    }
    private fun token(): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun uuid() = UUID.randomUUID().toString()
    companion object {
        private const val MAX_CURRENT_BYTES = 2 * 1024 * 1024 - 16 * 1024
        private const val MAX_REPLY_BYTES = 4 * 1024 * 1024 - 1024
        private const val HISTORY_PAGE_SIZE = 5
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) } }
}
