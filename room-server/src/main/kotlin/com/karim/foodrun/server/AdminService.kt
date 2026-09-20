package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Serializable data class AdminLogin(val username: String, val password: String)
@Serializable data class AdminSessionReply(val token: String, val expiresAt: Long)
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
    private val username: String = System.getenv("FOODRUN_ADMIN_USERNAME")?.trim().orEmpty(),
    private val password: String = System.getenv("FOODRUN_ADMIN_PASSWORD").orEmpty(),
) {
    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, Long>()
    val configured get() = username.isNotBlank() && password.length >= 12

    fun login(request: AdminLogin): AdminSessionReply {
        require(configured) { "Admin access has not been configured on this server." }
        require(request.username.length <= 160 && request.password.length <= 512) { "Invalid admin credentials." }
        val validUser = MessageDigest.isEqual(request.username.encodeToByteArray(), username.encodeToByteArray())
        val validPassword = MessageDigest.isEqual(request.password.encodeToByteArray(), password.encodeToByteArray())
        require(validUser && validPassword) { "Invalid admin credentials." }
        sessions.entries.removeIf { it.value <= clock() }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val expires = clock() + 8 * 60 * 60_000L
        sessions[token] = expires
        return AdminSessionReply(token, expires)
    }

    fun authorize(header: String?) {
        val token = header?.removePrefix("Bearer ").orEmpty()
        val expires = sessions[token] ?: error("Admin session expired. Sign in again.")
        require(expires > clock()) { sessions.remove(token); "Admin session expired. Sign in again." }
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
    companion object { const val SETTINGS = "admin:settings"; const val RESTAURANTS = "admin:restaurants" }
}
