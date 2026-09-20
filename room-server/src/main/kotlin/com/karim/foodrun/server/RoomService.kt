package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class RoomService(private val db: RoomDatabase, private val clock: () -> Long = System::currentTimeMillis, private val random: SecureRandom = SecureRandom(), identityProvider: IdentityProvider? = null) {
    private val accounts = AccountService(db, identityProvider, this, clock)
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
        if (c.kind == CommandKind.IDENTITY) accounts.execute(c).also { reply ->
            signalHome()
            reply.room?.let { signalRoom(it.id) }
        }
        else if (c.kind == CommandKind.HOME) accounts.home(c.identityToken)
        else if (c.kind == CommandKind.SNAPSHOT) snapshot(c.roomId, c.token, c.historyOffset)
        else db.transaction {
            if (c.identityToken.isNotEmpty()) accounts.userId(c.identityToken)
            if (c.kind !in listOf(CommandKind.CREATE, CommandKind.JOIN)) authenticate(c.roomId, c.token)
            val digest = hash(orderJson.encodeToString(c))
            db.previous(c.commandId, digest) ?: run {
                val reply = when(c.kind) {
                    CommandKind.CREATE -> create(c)
                    CommandKind.JOIN -> join(c)
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
    } catch (e: IllegalArgumentException) { RoomReply(ok = false, error = e.message ?: "Invalid request.", code = "VALIDATION", serverTime = clock()) }
      catch (e: IllegalStateException) { RoomReply(ok = false, error = e.message ?: "Action unavailable.", code = "STATE", serverTime = clock()) }

    @Synchronized fun snapshot(roomId: String, token: String, historyOffset: Int = 0): RoomReply {
        val actor = authenticate(roomId, token)
        val room = finalizeSpin(requireNotNull(db.room(roomId)))
        presence[actor] = clock()
        return projection(withPresence(room), actor, historyOffset)
    }
    @Synchronized fun tick() {
        db.transaction { db.spinningRooms().forEach { finalizeSpin(it) } }
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
    private fun join(c: RoomCommand): RoomReply {
        MenuValidation.label(c.name)
        require(c.code.matches(Regex("[0-9]{6}"))) { "Enter a six-digit room code." }
        val room = db.roomByCode(c.code) ?: error("Room code was not found.")
        if (c.identityToken.isNotEmpty()) accounts.linked(c.identityToken, room.id)?.let { saved ->
            if (room.members.any { it.id == saved.memberId && !it.removed })
                return projection(room, saved.memberId).copy(token = saved.token)
        }
        require(room.members.count { !it.removed } < 60) { "Too many pending participants. Ask the organizer to remove unused requests." }
        require(room.members.none { !it.removed && it.name.equals(c.name.trim(), true) }) { "This name is already in the room. Resume your saved session or use a distinct name." }
        val person = Member(uuid(), c.name.trim(), guest = c.guest, eligible = !c.guest, lastSeen = clock())
        val next = room.copy(members = room.members + person, revision = room.revision + 1, updatedAt = clock()); requireLoadable(next); db.save(next)
        signalRoom(next.id)
        val token = token(); db.addSession(hash(token), room.id, person.id)
        return projection(next, person.id).copy(token = token)
    }
    private fun authenticate(roomId: String, token: String): String {
        require(token.length in 32..128) { "Room credentials are missing. Join or resume this order." }
        val session = db.session(hash(token)) ?: error("Session was removed. Rejoin this room with organizer approval.")
        require(session.first == roomId) { "This credential belongs to another room." }
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
            restaurantOptions = emptyList(), restaurantVotes = emptyList(), restaurantPollOpen = false,
        ) else room.copy(
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
        val reviewStage = (actor == room.ownerId || actor == room.payerId) && room.phase == RoomPhase.COLLECTING
        val archiveStage = (actor == room.ownerId || actor == room.payerId) && room.phase == RoomPhase.FULFILLED
        fun blocker(validate: () -> Unit): String = try { validate(); "" }
            catch (invalid: IllegalArgumentException) { invalid.message ?: "Review the order before continuing." }
        val reviewBlocker = if (reviewStage) blocker { RoomRules.requireReview(room) } else ""
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
        val reply = baseProjection(room, actor)
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
