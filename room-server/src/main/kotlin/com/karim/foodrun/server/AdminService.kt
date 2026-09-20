package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
@Serializable data class AdminSettings(val registrationsEnabled: Boolean = true, val roomCreationEnabled: Boolean = true, val maintenanceMessage: String = "")
@Serializable data class AdminUserView(
    val id: String, val name: String, val phone: String, val discoverable: Boolean, val disabled: Boolean,
    val language: String, val paymentMethod: String = "", val paymentHolder: String = "",
    val paymentBank: String = "", val paymentIdentifier: String = "",
)
@Serializable data class AdminWalletView(val memberId: String, val name: String, val totalMinor: Long, val paidMinor: Long, val balanceMinor: Long)
@Serializable data class AdminRoomView(
    val id: String, val code: String, val name: String, val phase: String, val orderNumber: Long,
    val restaurant: String, val members: Int, val payer: String = "", val totalMinor: Long = 0,
    val confirmedPaidMinor: Long = 0, val outstandingMinor: Long = 0, val currency: String = "AED", val updatedAt: Long,
    val wallets: List<AdminWalletView> = emptyList(),
)
@Serializable data class AdminDashboard(
    val users: List<AdminUserView>, val rooms: List<AdminRoomView>, val archivedOrders: List<AdminRoomView>,
    val restaurants: List<Restaurant>, val settings: AdminSettings,
)
@Serializable data class AdminUserMutation(val userId: String, val disabled: Boolean)
@Serializable data class AdminRestaurantMutation(val action: String = "save", val restaurant: Restaurant? = null, val restaurantId: String = "")
@Serializable data class AdminRoomMutation(val roomId: String, val action: String)

class AdminService(
    private val db: RoomDatabase, private val rooms: RoomService, private val clock: () -> Long = System::currentTimeMillis,
    private val identityProvider: IdentityProvider? = FirebaseIdentity.configured(),
) {
    fun authorize(header: String?) {
        require(header?.startsWith("Bearer ") == true) { "Sign in with the administrator account." }
        val identity = requireNotNull(identityProvider) { "Firebase identity is not configured." }.exchange(header.removePrefix("Bearer "))
        require(identity.emailVerified && identity.email.equals(ADMIN_EMAIL, ignoreCase = true)) { "This account cannot access administration." }
        require(db.record("admin:disabled:${identity.userId}") == null) { "This account is disabled." }
    }

    fun settings(): AdminSettings = synchronized(rooms) { db.record(SETTINGS)?.let { orderJson.decodeFromString(it) } ?: AdminSettings() }
    fun saveSettings(value: AdminSettings): AdminSettings = synchronized(rooms) {
        require(value.maintenanceMessage.length <= 500) { "Maintenance message is too long." }
        db.putRecord(SETTINGS, orderJson.encodeToString(value)); value
    }
    fun catalog(): List<Restaurant> = synchronized(rooms) { catalogUnlocked() }
    private fun catalogUnlocked(): List<Restaurant> = db.record(RESTAURANTS)?.let { orderJson.decodeFromString(it) } ?: BuiltInRestaurants.all.map { it.restaurant }
    fun mutateRestaurant(change: AdminRestaurantMutation): List<Restaurant> = synchronized(rooms) {
        val current = catalogUnlocked()
        val next = when(change.action) {
            "save" -> requireNotNull(change.restaurant).also(MenuValidation::validate).let { saved -> current.filterNot { it.id == saved.id } + saved }
            "delete" -> current.filterNot { it.id == change.restaurantId }
            else -> error("Unsupported restaurant action.")
        }
        require(next.size <= 100) { "Restaurant catalog limit reached." }
        db.putRecord(RESTAURANTS, orderJson.encodeToString(next)); next.sortedBy { it.name }
    }
    fun mutateUser(change: AdminUserMutation) = synchronized(rooms) {
        require(db.record("profile:${change.userId}") != null) { "User was not found." }
        if(change.disabled) db.putRecord("admin:disabled:${change.userId}", "true") else db.deleteRecord("admin:disabled:${change.userId}")
    }
    fun mutateRoom(change: AdminRoomMutation) {
        require(change.action == "cancel") { "Unsupported room action." }
        rooms.adminCancel(change.roomId)
    }
    fun dashboard(): AdminDashboard = synchronized(rooms) { AdminDashboard(
        users = db.records("profile:").map { (_, body) -> orderJson.decodeFromString<FoodProfile>(body) }.map {
            AdminUserView(it.userId, it.name, it.phone, it.discoverable, db.record("admin:disabled:${it.userId}") != null,
                it.language, it.payment?.method?.name.orEmpty(), it.payment?.holder.orEmpty(), it.payment?.bank.orEmpty(), it.payment?.identifier.orEmpty())
        }.sortedBy { it.name },
        rooms = db.allRooms().map(::roomView).sortedByDescending { it.updatedAt },
        archivedOrders = db.allHistory().map(::roomView).sortedByDescending { it.updatedAt },
        restaurants = catalogUnlocked().sortedBy { it.name },
        settings = db.record(SETTINGS)?.let { orderJson.decodeFromString(it) } ?: AdminSettings(),
    ) }
    private fun roomView(room: Room): AdminRoomView {
        val receipts = runCatching { Billing.receipts(room) }.getOrDefault(emptyList())
        return AdminRoomView(room.id, room.code, room.name, room.phase.name, room.orderNumber, room.restaurant.name,
            room.activeMembers.size, room.members.firstOrNull { it.id == room.payerId }?.name.orEmpty(), receipts.sumOf { it.total },
            receipts.sumOf { it.paid }, receipts.filterNot { it.memberId == room.payerId }.sumOf { maxOf(0, it.balance) }, room.restaurant.currency, room.updatedAt,
            receipts.map { AdminWalletView(it.memberId, it.name, it.total, it.paid, it.balance) })
    }
    companion object { const val ADMIN_EMAIL = "1ahmedkaram1@gmail.com"; const val SETTINGS = "admin:settings"; const val RESTAURANTS = "admin:restaurants" }
}
