package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
import java.util.UUID
import java.security.SecureRandom
import java.util.Base64

@Serializable private data class AccountSession(val userId: String, val refreshToken: String, val idToken: String, val expires: Long, val cloudExpires: Long)
@Serializable private data class CloudOwner(val tokenHash: String, val hubId: String)

/** Invites and account sessions are hub-local; profiles and room records persist in the existing Firebase project. */
class AccountService(private val db: RoomDatabase, private val provider: IdentityProvider?, private val rooms: RoomService, private val clock: () -> Long) {
    private var cloudStatus = "Cloud storage has not been connected for this hub."
    private fun cloud() = requireNotNull(provider) { "Configure this hub with the existing Intrvioo Firebase project to use accounts." }
    private fun session(token: String): AccountSession {
        require(token.length in 32..128) { "Sign in to your Food Run account." }
        val saved = db.record("identity:${RoomService.hash(token)}") ?: error("Sign in again to reconnect your account.")
        return orderJson.decodeFromString<AccountSession>(saved).also {
            require(it.expires > clock()) { "Your account session expired. Sign in again." }
            require(db.record("admin:disabled:${it.userId}") == null) { "This account is disabled. Contact the Food Run administrator." }
        }
    }
    fun userId(token: String): String = session(token).userId
    private fun profile(uid: String) = db.record("profile:$uid")?.let { orderJson.decodeFromString<FoodProfile>(it) } ?: FoodProfile(userId = uid)
    private fun identity(hash: String, saved: AccountSession): CloudIdentity {
        if (saved.cloudExpires > clock()) return CloudIdentity(saved.userId, saved.idToken, saved.refreshToken)
        require(saved.refreshToken.isNotBlank()) { "Reconnect your Firebase account to save cloud changes." }
        val renewed = cloud().refresh(saved.refreshToken)
        require(renewed.userId == saved.userId)
        db.putRecord("identity:$hash", orderJson.encodeToString(saved.copy(idToken = renewed.idToken, refreshToken = renewed.refreshToken, cloudExpires = clock() + 3_300_000)))
        return renewed
    }
    fun link(token: String, result: RoomReply) {
        val uid = userId(token)
        val room = result.room ?: return
        val membership = AccountRoom(room.id, room.name, result.memberId, result.token)
        require(membership.token.isNotEmpty())
        db.putRecord("membership:$uid:${room.id}", orderJson.encodeToString(membership))
    }
    fun linked(token: String, roomId: String): AccountRoom? = db.record("membership:${userId(token)}:$roomId")?.let { orderJson.decodeFromString(it) }
    fun home(token: String): RoomReply {
        val uid = userId(token)
        val memberships = db.records("membership:$uid:").map { orderJson.decodeFromString<AccountRoom>(it.second) }
            .filter { m -> db.room(m.roomId)?.members?.any { it.id == m.memberId && !it.removed } == true }
        val invitations = db.records("invitation:$uid:").map { orderJson.decodeFromString<FoodInvitation>(it.second) }
            .filter { db.room(it.roomId)?.let { room -> room.orderNumber == it.orderNumber && room.phase == RoomPhase.LOBBY } == true }
        val people = db.records("profile:").map { orderJson.decodeFromString<FoodProfile>(it.second) }
            .filter { it.discoverable && it.userId != uid && it.name.isNotBlank() }.take(100).map { FoodPerson(it.userId, it.name) }
        val restaurants = db.record(AdminService.RESTAURANTS)?.let { orderJson.decodeFromString<List<Restaurant>>(it) } ?: BuiltInRestaurants.all.map { it.restaurant }
        return RoomReply(home = HomePayload(profile(uid), people, invitations, memberships, cloudStatus, restaurants, AdminService.deletedRestaurantIds(db)), serverTime = clock())
    }
    fun execute(c: RoomCommand): RoomReply {
        val request = requireNotNull(c.identity)
        require(request.email.length <= 254 && request.password.length <= 512 && request.userId.length <= 128 && request.invitationId.length <= 200)
        if (request.action == IdentityAction.RESET_PASSWORD) {
            require(request.email.contains('@')) { "Enter your account email." }
            cloud().resetPassword(request.email); return RoomReply()
        }
        if (request.action in listOf(IdentityAction.REGISTER, IdentityAction.SIGN_IN, IdentityAction.FIREBASE_SIGN_IN)) {
            if(request.action == IdentityAction.REGISTER) {
                val settings = db.record(AdminService.SETTINGS)?.let { orderJson.decodeFromString<AdminSettings>(it) } ?: AdminSettings()
                require(settings.registrationsEnabled) { "New registration is temporarily disabled by the administrator." }
            }
            if (request.action == IdentityAction.REGISTER) requireNotNull(request.profile).normalized().validate()
            val identity = if (request.action == IdentityAction.FIREBASE_SIGN_IN) cloud().exchange(request.firebaseToken)
                else { require(request.email.contains('@') && request.password.length >= 6) { "Enter your email and a password of at least 6 characters." }; cloud().signIn(request.email, request.password, request.action == IdentityAction.REGISTER) }
            val savedProfile = (cloud().profile(identity) ?: (request.profile ?: FoodProfile(name = identity.name))).copy(userId = identity.userId).let {
                if (it.phone.isBlank()) it else it.normalized()
            }
            require(db.record("admin:disabled:${identity.userId}") == null) { "This account is disabled. Contact the Food Run administrator." }
            if (request.action == IdentityAction.REGISTER) cloud().saveProfile(identity, savedProfile)
            db.putRecord("profile:${identity.userId}", orderJson.encodeToString(savedProfile))
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
            db.putRecord("identity:${RoomService.hash(token)}", orderJson.encodeToString(AccountSession(identity.userId, identity.refreshToken, identity.idToken, clock() + 30L * 86400_000, clock() + 3_300_000)))
            return home(token).copy(identityToken = token)
        }
        val saved = session(c.identityToken)
        return when(request.action) {
            IdentityAction.SAVE_PROFILE -> {
                val updated = requireNotNull(request.profile).copy(userId = saved.userId).normalized()
                updated.validate()
                cloud().saveProfile(identity(RoomService.hash(c.identityToken), saved), updated)
                db.putRecord("profile:${saved.userId}", orderJson.encodeToString(updated))
                home(c.identityToken)
            }
            IdentityAction.SIGN_OUT -> { db.deleteRecord("identity:${RoomService.hash(c.identityToken)}"); RoomReply() }
            IdentityAction.INVITE -> {
                val snapshot = rooms.snapshot(c.roomId, c.token)
                val room = requireNotNull(snapshot.room)
                require(snapshot.memberId == room.ownerId && room.phase == RoomPhase.LOBBY) { "Only the organizer can invite people while gathering." }
                require(db.record("profile:${request.userId}") != null && profile(request.userId).discoverable) { "This person is not available for invitations on this hub." }
                val invitation = FoodInvitation("${room.id}-${room.orderNumber}", request.userId, room.id, room.name, profile(saved.userId).name, room.orderNumber)
                db.putRecord("invitation:${request.userId}:${invitation.id}", orderJson.encodeToString(invitation))
                home(c.identityToken)
            }
            IdentityAction.ACCEPT_INVITE -> {
                val key = "invitation:${saved.userId}:${request.invitationId}"
                val invitation = db.record(key)?.let { orderJson.decodeFromString<FoodInvitation>(it) } ?: error("This invitation is no longer available.")
                val room = requireNotNull(db.room(invitation.roomId))
                require(room.phase == RoomPhase.LOBBY && room.orderNumber == invitation.orderNumber) { "This order already started. Ask the organizer for another invitation." }
                val result = rooms.execute(RoomCommand(commandId = c.commandId, kind = CommandKind.JOIN, code = room.code, name = profile(saved.userId).name, identityToken = c.identityToken))
                if (result.ok) db.deleteRecord(key)
                result
            }
            IdentityAction.ENABLE_CLOUD -> {
                require(saved.refreshToken.isNotEmpty()) { "Sign in from the mobile app with email and password before connecting persistent hub storage." }
                val owner = db.record("cloud-owner")?.let { orderJson.decodeFromString<CloudOwner>(it) }
                if (owner != null) {
                    val current = db.record("identity:${owner.tokenHash}")?.let { orderJson.decodeFromString<AccountSession>(it) }
                    require(current?.userId == saved.userId) { "This hub is already connected to another cloud owner." }
                }
                val next = CloudOwner(RoomService.hash(c.identityToken), owner?.hubId ?: UUID.randomUUID().toString())
                // Verify permission before enabling sync.
                cloud().saveHubRecord(identity(next.tokenHash, saved), next.hubId, "metadata", "Food Run hub")
                db.putRecord("cloud-owner", orderJson.encodeToString(next))
                db.allRooms().forEach { db.enqueueCloud("room-${it.id}", orderJson.encodeToString(it)) }
                cloudStatus = "Connected to Firebase · syncing room data"
                home(c.identityToken)
            }
            else -> error("Unsupported account action.")
        }
    }
    /** Called by a separate worker under the room-service lock; failures retain the durable outbox. */
    fun syncCloud() {
        val owner = db.record("cloud-owner")?.let { orderJson.decodeFromString<CloudOwner>(it) } ?: return
        val next = db.pendingCloud() ?: run { cloudStatus = "All room changes saved to Firebase"; return }
        try {
            val saved = db.record("identity:${owner.tokenHash}")?.let { orderJson.decodeFromString<AccountSession>(it) } ?: error("Cloud owner must sign in and reconnect storage.")
            val identity = identity(owner.tokenHash, saved)
            // Chunk records below Firestore's document limit; publish manifest after every chunk succeeds.
            val chunks = next.second.chunked(160_000)
            val version = RoomService.hash(next.second).take(24)
            chunks.forEachIndexed { index, chunk -> cloud().saveHubRecord(identity, owner.hubId, "${next.first}-$version-$index", chunk) }
            cloud().saveHubRecord(identity, owner.hubId, next.first, "{\"version\":\"$version\",\"chunks\":${chunks.size}}")
            db.finishCloud(next.first, next.second)
            cloudStatus = "Room changes saved to Firebase"
        } catch (_: Exception) { cloudStatus = "Saved on this hub · Firebase sync pending. Check connectivity, Firestore rules, and the cloud owner's session." }
    }
}
