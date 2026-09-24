package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class AdminSettings(val registrationsEnabled: Boolean = true, val roomCreationEnabled: Boolean = true, val maintenanceMessage: String = "")
@Serializable data class AdminUserView(
    val id: String, val name: String, val phone: String, val discoverable: Boolean, val disabled: Boolean,
    val language: String, val paymentMethod: String = "", val paymentHolder: String = "",
    val paymentBank: String = "", val paymentIdentifier: String = "",
    val blockedUntil: Long = 0, val blockReason: String = "", val removed: Boolean = false, val roomBlocks: Map<String, AccessBlock> = emptyMap(),
    val profile: FoodProfile? = null,
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
    val profile: FoodProfile? = null,
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

@Serializable data class NativeAdminRequest(val identityToken: String, val action: String = "dashboard", val payload: String = "")
@Serializable data class NativeAdminReply(val ok: Boolean = true, val error: String = "", val dashboard: AdminDashboard? = null, val preview: AdminCleanupPreview? = null)
