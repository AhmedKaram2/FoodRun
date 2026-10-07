package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Serializable internal data class SupportSession(val actorId: String, val userId: String, val expiresAt: Long)
internal class SupportSessionEnded : IllegalArgumentException("Support session ended. Return to your owner account.")
internal class SupportSessions(private val db: RoomDatabase, private val clock: () -> Long) {
    fun find(token: String): SupportSession? = db.record("support-session:${RoomService.hash(token)}")?.let { orderJson.decodeFromString<SupportSession>(it) }
    fun requireActive(token: String): SupportSession? = find(token)?.also {
        if(it.expiresAt <= clock()) throw SupportSessionEnded()
        AccountRestrictions.requireAllowed(db, it.actorId, clock())
        AccountRestrictions.requireAllowed(db, it.userId, clock())
    }
    fun start(actor: CloudIdentity, target: String): String {
        require(actor.emailVerified && actor.email.equals(AdminService.ADMIN_EMAIL, true)) { "This account cannot sign in as another user." }
        val uid = AccountAliases.resolve(db, target)
        require(uid != actor.userId && db.record("profile:$uid") != null) { "Choose another registered user." }
        AccountRestrictions.requireAllowed(db, uid, clock())
        val token = freshToken()
        db.putRecord("support-session:${RoomService.hash(token)}", orderJson.encodeToString(SupportSession(actor.userId, uid, clock() + 30 * 60_000)))
        audit(actor.userId, "support-start", uid)
        return token
    }
    fun end(token: String) {
        find(token)?.let { audit(it.actorId, "support-end", it.userId) }
        // Keep an expired tombstone so every issued room token stays revoked.
        find(token)?.let { db.putRecord("support-session:${RoomService.hash(token)}", orderJson.encodeToString(it.copy(expiresAt = 0))) }
    }
    fun roomToken(identityToken: String, member: AccountRoom): AccountRoom {
        requireActive(identityToken) ?: return member
        val hash = RoomService.hash(identityToken)
        val key = "support-room:$hash:${member.roomId}"
        val token = db.record(key) ?: freshToken().also { db.putRecord(key, it) }
        db.addSession(RoomService.hash(token), member.roomId, member.memberId)
        db.putRecord("support-room-session:${RoomService.hash(token)}", hash)
        return member.copy(token = token)
    }
    fun protect(identityToken: String, roomToken: String) {
        if(requireActive(identityToken) != null) db.putRecord("support-room-session:${RoomService.hash(roomToken)}", RoomService.hash(identityToken))
    }
    fun validateRoom(roomToken: String) {
        val identityHash = db.record("support-room-session:${RoomService.hash(roomToken)}") ?: return
        val saved = db.record("support-session:$identityHash")?.let { orderJson.decodeFromString<SupportSession>(it) }
        if(saved == null || saved.expiresAt <= clock()) throw SupportSessionEnded()
        AccountRestrictions.requireAllowed(db, saved.actorId, clock())
        AccountRestrictions.requireAllowed(db, saved.userId, clock())
    }
    fun record(c: RoomCommand) {
        val saved = if(c.identityToken.isNotEmpty()) find(c.identityToken) else null
        val support = saved ?: db.record("support-room-session:${RoomService.hash(c.token)}")?.let { db.record("support-session:$it") }
            ?.let { orderJson.decodeFromString<SupportSession>(it) } ?: return
        if(c.kind !in listOf(CommandKind.HOME, CommandKind.SNAPSHOT, CommandKind.FRIEND_LOOKUP, CommandKind.WALLET_PEOPLE, CommandKind.WALLET_RECIPIENT, CommandKind.PAYMENT_REMINDER_STATUS))
            audit(support.actorId, "support:${c.kind.name}", "${support.userId}:${c.roomId}")
    }
    private fun audit(actorId: String, action: String, target: String) = db.putRecord("admin:audit:${clock()}:${UUID.randomUUID()}", orderJson.encodeToString(AdminAuditEvent(actorId, action, target, clock())))
    companion object { fun freshToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes)) }
}
