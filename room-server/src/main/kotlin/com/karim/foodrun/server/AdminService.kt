package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
@Serializable data class AdminSettings(val registrationsEnabled: Boolean = true, val roomCreationEnabled: Boolean = true, val maintenanceMessage: String = "")
@Serializable data class AdminUserView(
    val id: String, val name: String, val phone: String, val discoverable: Boolean, val disabled: Boolean,
    val language: String, val paymentMethod: String = "", val paymentHolder: String = "",
    val paymentBank: String = "", val paymentIdentifier: String = "",
    val blockedUntil: Long = 0, val blockReason: String = "", val removed: Boolean = false, val roomBlocks: Map<String, AccessBlock> = emptyMap(),
)
@Serializable data class AdminWalletView(val memberId: String, val name: String, val totalMinor: Long, val paidMinor: Long, val balanceMinor: Long)
@Serializable data class AdminRoomView(
    val id: String, val code: String, val name: String, val phase: String, val orderNumber: Long,
    val restaurant: String, val members: Int, val payer: String = "", val totalMinor: Long = 0,
    val confirmedPaidMinor: Long = 0, val outstandingMinor: Long = 0, val currency: String = "AED", val updatedAt: Long,
    val wallets: List<AdminWalletView> = emptyList(), val revision: Long = 0, val canDelete: Boolean = false,
)
@Serializable data class AdminDashboard(
    val users: List<AdminUserView>, val rooms: List<AdminRoomView>, val archivedOrders: List<AdminRoomView>,
    val restaurants: List<Restaurant>, val settings: AdminSettings, val activity: List<AdminAuditEvent> = emptyList(), val blockRequests: List<AdminBlockRequest> = emptyList(),
)
@Serializable data class AdminUserMutation(
    val userId: String = "", val disabled: Boolean = false, val action: String = "status",
    val durationHours: Int = 0, val reason: String = "", val confirmation: String = "", val scopeRoomId: String = "",
    val email: String = "", val password: String = "", val name: String = "", val phone: String = "", val language: String = "en",
)
@Serializable data class AdminBlockRequest(val id: String, val roomId: String, val roomName: String, val requesterId: String, val requesterName: String,
    val userId: String, val userName: String, val reason: String, val durationHours: Int, val createdAt: Long, val status: String = "pending", val reviewedAt: Long = 0)
@Serializable data class AdminBlockDecision(val requestId: String, val action: String)
@Serializable data class AdminAuditEvent(val actorId: String, val action: String, val target: String, val at: Long)
@Serializable data class AdminCleanupRequest(val scope: String = "closedRooms", val olderThanDays: Int = 30, val previewToken: String = "", val confirmation: String = "")
@Serializable data class AdminCleanupPreview(val scope: String, val olderThanDays: Int, val count: Int, val targets: List<AdminRoomView>, val previewToken: String)
@Serializable data class AdminCleanupResult(val removedCount: Int)
@Serializable data class AdminRestaurantMutation(val action: String = "save", val restaurant: Restaurant? = null, val restaurantId: String = "")
@Serializable data class RestaurantCatalogPayload(val restaurants: List<Restaurant>, val deletedRestaurantIds: Set<String>)
@Serializable data class AdminRoomMutation(val roomId: String, val action: String, val expectedRevision: Long = 0, val confirmation: String = "")

class AdminService(
    private val db: RoomDatabase, private val rooms: RoomService, private val clock: () -> Long = System::currentTimeMillis,
    private val identityProvider: IdentityProvider? = FirebaseIdentity.configured(),
) {
    fun authorize(header: String?): CloudIdentity {
        require(header?.startsWith("Bearer ") == true) { "Sign in with the administrator account." }
        val identity = requireNotNull(identityProvider) { "Firebase identity is not configured." }.exchange(header.removePrefix("Bearer "))
        require(identity.emailVerified && identity.email.equals(ADMIN_EMAIL, ignoreCase = true)) { "This account cannot access administration." }
        synchronized(rooms) { AccountRestrictions.requireAllowed(db, identity.userId, clock()) }
        return identity
    }

    fun settings(): AdminSettings = synchronized(rooms) { db.record(SETTINGS)?.let { orderJson.decodeFromString(it) } ?: AdminSettings() }
    fun saveSettings(value: AdminSettings): AdminSettings = synchronized(rooms) {
        require(value.maintenanceMessage.length <= 500) { "Maintenance message is too long." }
        db.putRecord(SETTINGS, orderJson.encodeToString(value)); value
    }
    fun catalog(): List<Restaurant> = synchronized(rooms) { catalogUnlocked() }
    fun catalogPayload(): RestaurantCatalogPayload = synchronized(rooms) { RestaurantCatalogPayload(catalogUnlocked(), deletedRestaurantIds(db)) }
    private fun catalogUnlocked(): List<Restaurant> = db.record(RESTAURANTS)?.let { orderJson.decodeFromString(it) } ?: BuiltInRestaurants.all.map { it.restaurant }
    fun mutateRestaurant(change: AdminRestaurantMutation): List<Restaurant> = synchronized(rooms) {
        val current = catalogUnlocked()
        val next = when(change.action) {
            "save" -> requireNotNull(change.restaurant).also(MenuValidation::validate).let { saved -> current.filterNot { it.id == saved.id } + saved }
            "delete" -> current.filterNot { it.id == change.restaurantId }
            else -> error("Unsupported restaurant action.")
        }
        require(next.size <= 100) { "Restaurant catalog limit reached." }
        val deleted = deletedRestaurantIds(db).let { if (change.action == "delete") it + change.restaurantId else it - requireNotNull(change.restaurant).id }
        db.transaction {
            db.putRecord(DELETED_RESTAURANTS, orderJson.encodeToString(deleted))
            db.putRecord(RESTAURANTS, orderJson.encodeToString(next))
        }
        next.sortedBy { it.name }
    }
    private fun audit(actorId: String, action: String, target: String) {
        db.putRecord("admin:audit:${clock()}:${java.util.UUID.randomUUID()}", orderJson.encodeToString(AdminAuditEvent(actorId, action, target, clock())))
    }
    fun mutateUser(change: AdminUserMutation, actorId: String = "") = synchronized(rooms) {
        require(change.reason.length <= 300 && change.userId.length <= 128)
        if (change.action == "create") {
            require(change.email.length <= 254 && change.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Enter a valid email address." }
            require(change.password.length in 8..128) { "Use a temporary password of 8 to 128 characters." }
            val profile = FoodProfile(name = change.name.trim(), phone = change.phone, language = change.language).normalized().also(FoodProfile::validate)
            val provider = requireNotNull(identityProvider) { "Firebase identity is not configured." }
            val identity = provider.signIn(change.email.trim(), change.password, true)
            val created = profile.copy(userId = identity.userId)
            // Save locally before optional cloud profile synchronization; a created login remains usable if Firestore is temporarily unavailable.
            db.putRecord("profile:${identity.userId}", orderJson.encodeToString(created))
            runCatching { provider.saveProfile(identity, created) }
            audit(actorId, "create-user", identity.userId); rooms.adminChanged()
            return@synchronized
        }
        require(change.userId.isNotBlank()) { "Choose a user." }
        require(change.action == "rename" || change.userId != actorId) { "You cannot block or remove your own administrator account." }
        require(db.record("profile:${change.userId}") != null || db.record("admin:restriction:${change.userId}") != null) { "User was not found." }
        val memberships = db.records("membership:${change.userId}:").map { orderJson.decodeFromString<AccountRoom>(it.second) }
        when (change.action) {
            "rename" -> {
                val name = change.name.trim().also(MenuValidation::label)
                val profile = requireNotNull(db.record("profile:${change.userId}")) { "Restore this removed user first." }
                    .let { orderJson.decodeFromString<FoodProfile>(it) }
                val linkedRooms = memberships.mapNotNull { membership -> db.room(membership.roomId)?.let { membership to it } }
                linkedRooms.forEach { (membership, room) ->
                    require(room.members.none { it.id != membership.memberId && !it.removed && it.name.equals(name, true) }) {
                        "Another member in ${room.name} already uses this name."
                    }
                }
                db.putRecord("profile:${change.userId}", orderJson.encodeToString(profile.copy(name = name)))
                db.putRecord("admin:profile-name:${change.userId}", name)
                linkedRooms.forEach { (membership, room) ->
                    db.save(room.copy(
                        members = room.members.map { if (it.id == membership.memberId) it.copy(name = name) else it },
                        revision = room.revision + 1,
                        updatedAt = clock(),
                    ))
                }
                audit(actorId, "rename-user", change.userId)
            }
            "status", "block", "unblock" -> {
                val block = change.action == "block" || change.action == "status" && change.disabled
                require(change.durationHours in 0..8760) { "Choose a duration of up to 365 days." }
                require(AccountRestrictions.current(db, change.userId, clock())?.removed != true) { "Restore this removed user first." }
                require(change.scopeRoomId.isEmpty() || db.room(change.scopeRoomId) != null) { "Room was not found." }
                val restrictionKey = if(change.scopeRoomId.isEmpty()) "admin:restriction:${change.userId}" else "admin:room-restriction:${change.userId}:${change.scopeRoomId}"
                if(change.scopeRoomId.isEmpty()) db.deleteRecord("admin:disabled:${change.userId}")
                if (block) db.putRecord(restrictionKey, orderJson.encodeToString(AccountRestriction(
                    until = if(change.durationHours == 0) 0 else clock() + change.durationHours * 3_600_000L, reason = change.reason.trim(), durationHours = change.durationHours)))
                else db.deleteRecord(restrictionKey)
                audit(actorId, if(block) "block-user:${change.durationHours}h" else "unblock-user", change.userId)
            }
            "remove" -> {
                require(change.confirmation == change.userId) { "Confirm the selected user before removal." }
                require(memberships.none { membership -> db.room(membership.roomId)?.let { room ->
                    room.phase !in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED) && room.activeMembers.any { it.id == membership.memberId }
                } == true }) { "Finish or cancel this user's active rooms before removing them." }
                db.transaction {
                    db.revokeUserSessions(change.userId)
                    db.deleteRecord("profile:${change.userId}")
                    db.records("invitation:${change.userId}:").forEach { db.deleteRecord(it.first) }
                    db.putRecord("admin:restriction:${change.userId}", orderJson.encodeToString(AccountRestriction(reason = change.reason.trim(), removed = true)))
                    audit(actorId, "remove-foodrun-user", change.userId)
                }
            }
            "restore" -> {
                require(AccountRestrictions.current(db, change.userId, clock())?.removed == true) { "This user is not removed." }
                db.deleteRecord("admin:restriction:${change.userId}"); db.deleteRecord("admin:disabled:${change.userId}")
                audit(actorId, "restore-foodrun-user", change.userId)
            }
            else -> error("Unsupported user action.")
        }
        rooms.adminChanged(memberships.map { it.roomId })
    }
    fun reviewBlockRequest(change: AdminBlockDecision, actorId: String) = synchronized(rooms) {
        require(change.action in listOf("approve", "reject")) { "Choose approve or reject." }
        val key = "admin:block-request:${change.requestId}"
        val request = requireNotNull(db.record(key)) { "Block request was not found." }.let { orderJson.decodeFromString<AdminBlockRequest>(it) }
        require(request.status == "pending") { "This request was already reviewed." }
        if(change.action == "approve") mutateUser(AdminUserMutation(userId = request.userId, action = "block", durationHours = request.durationHours, reason = request.reason, scopeRoomId = request.roomId), actorId)
        db.putRecord(key, orderJson.encodeToString(request.copy(status = if(change.action == "approve") "approved" else "rejected", reviewedAt = clock())))
        audit(actorId, "${change.action}-block-request", request.id)
    }
    private fun deletable(room: Room): Boolean = room.phase == RoomPhase.CANCELLED ||
        room.phase == RoomPhase.ARCHIVED && runCatching { RoomRules.requireArchive(room) }.isSuccess
    fun mutateRoom(change: AdminRoomMutation, actorId: String = "") = synchronized(rooms) {
        when(change.action) {
            "cancel" -> rooms.adminCancel(change.roomId)
            "delete" -> {
                val room = requireNotNull(db.room(change.roomId)) { "Room was not found." }
                require(deletable(room)) { "Only cancelled or fully settled archived rooms can be deleted." }
                require(change.expectedRevision == room.revision && change.confirmation == room.code) { "The room changed or its confirmation code does not match. Refresh and try again." }
                db.transaction { db.deleteRoom(room.id); audit(actorId, "delete-room", room.id) }
                rooms.adminChanged(listOf(room.id))
            }
            else -> error("Unsupported room action.")
        }
        if(change.action == "cancel") audit(actorId, "cancel-room", change.roomId)
    }
    fun cleanupPreview(request: AdminCleanupRequest): AdminCleanupPreview = synchronized(rooms) {
        require(request.olderThanDays in 0..3650) { "Choose an age between 0 and 3650 days." }
        require(request.scope in listOf("closedRooms", "history")) { "Choose rooms or order history." }
        val cutoff = clock() - request.olderThanDays * 86_400_000L
        val targets = (if(request.scope == "closedRooms") db.allRooms() else db.allHistory())
            .filter { it.updatedAt <= cutoff && deletable(it) }.sortedWith(compareBy<Room> { it.id }.thenBy { it.orderNumber })
        val token = RoomService.hash(request.scope + ":" + request.olderThanDays + ":" + targets.joinToString("|") { "${it.id}:${it.orderNumber}:${it.revision}:${it.updatedAt}" })
        AdminCleanupPreview(request.scope, request.olderThanDays, targets.size, targets.map(::roomView), token)
    }
    fun cleanup(request: AdminCleanupRequest, actorId: String): AdminCleanupResult = synchronized(rooms) {
        require(request.confirmation == "DELETE") { "Type DELETE to confirm this cleanup." }
        val preview = cleanupPreview(request)
        require(request.previewToken.isNotBlank() && request.previewToken == preview.previewToken) { "The cleanup selection changed. Preview it again before deleting." }
        db.transaction {
            preview.targets.forEach { if(request.scope == "closedRooms") db.deleteRoom(it.id) else db.deleteHistory(it.id, it.orderNumber) }
            audit(actorId, "cleanup:${request.scope}", "${preview.count} records older than ${request.olderThanDays} days")
        }
        rooms.adminChanged(preview.targets.map { it.id })
        AdminCleanupResult(preview.count)
    }
    fun dashboard(): AdminDashboard = synchronized(rooms) { AdminDashboard(
        users = (db.records("profile:").map { (_, body) -> orderJson.decodeFromString<FoodProfile>(body) } +
            db.records("admin:restriction:").filter { orderJson.decodeFromString<AccountRestriction>(it.second).removed }
                .map { FoodProfile(userId = it.first.removePrefix("admin:restriction:"), name = "Removed user") }).distinctBy { it.userId }.map {
            val restriction = AccountRestrictions.current(db, it.userId, clock())
            AdminUserView(it.userId, it.name, it.phone, it.discoverable, restriction != null,
                it.language, it.payment?.method?.name.orEmpty(), it.payment?.holder.orEmpty(), it.payment?.bank.orEmpty(), it.payment?.identifier.orEmpty(),
                restriction?.until ?: 0, restriction?.reason.orEmpty(), restriction?.removed == true,
                db.records("admin:room-restriction:${it.userId}:").mapNotNull { (key, body) ->
                    val value = orderJson.decodeFromString<AccountRestriction>(body)
                    val roomId = key.substringAfterLast(':')
                    if(value.until == 0L || value.until > clock()) roomId to AccountRestrictions.block(value, roomId) else null
                }.toMap())
        }.sortedBy { it.name },
        rooms = db.allRooms().map(::roomView).sortedByDescending { it.updatedAt },
        archivedOrders = db.allHistory().map(::roomView).sortedByDescending { it.updatedAt },
        restaurants = catalogUnlocked().sortedBy { it.name },
        settings = db.record(SETTINGS)?.let { orderJson.decodeFromString(it) } ?: AdminSettings(),
        blockRequests = db.records("admin:block-request:").map { orderJson.decodeFromString<AdminBlockRequest>(it.second) }.sortedByDescending { it.createdAt },
        activity = db.records("admin:audit:").map { orderJson.decodeFromString<AdminAuditEvent>(it.second) }.sortedByDescending { it.at }.take(100),
    ) }
    private fun roomView(room: Room): AdminRoomView {
        val receipts = runCatching { Billing.receipts(room) }.getOrDefault(emptyList())
        return AdminRoomView(room.id, room.code, room.name, room.phase.name, room.orderNumber, room.restaurant.name,
            room.activeMembers.size, room.members.firstOrNull { it.id == room.payerId }?.name.orEmpty(), receipts.sumOf { it.total },
            receipts.sumOf { it.paid }, receipts.filterNot { it.memberId == room.payerId }.sumOf { maxOf(0, it.balance) }, room.restaurant.currency, room.updatedAt,
            receipts.map { AdminWalletView(it.memberId, it.name, it.total, it.paid, it.balance) }, room.revision, deletable(room))
    }
    companion object {
        const val ADMIN_EMAIL = "1ahmedkaram1@gmail.com"
        const val SETTINGS = "admin:settings"
        const val RESTAURANTS = "admin:restaurants"
        const val DELETED_RESTAURANTS = "admin:deleted-restaurants"
        fun deletedRestaurantIds(db: RoomDatabase): Set<String> = db.record(DELETED_RESTAURANTS)?.let { orderJson.decodeFromString(it) } ?: emptySet()
    }
}
