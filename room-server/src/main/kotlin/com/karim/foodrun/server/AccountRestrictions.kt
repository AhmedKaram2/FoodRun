package com.karim.foodrun.server

import com.karim.foodrun.orders.orderJson
import com.karim.foodrun.orders.AccessBlock
import kotlinx.serialization.Serializable

@Serializable data class AccountRestriction(val until: Long = 0, val reason: String = "", val removed: Boolean = false, val durationHours: Int = 0)

internal object AccountRestrictions {
    fun current(db: RoomDatabase, userId: String, now: Long): AccountRestriction? {
        val saved = db.record("admin:restriction:$userId")?.let { orderJson.decodeFromString<AccountRestriction>(it) }
        if (saved != null && (saved.removed || saved.until == 0L || saved.until > now)) return saved
        return if (db.record("admin:disabled:$userId") != null) AccountRestriction() else null
    }

    fun forRoom(db: RoomDatabase, userId: String, roomId: String, now: Long): AccountRestriction? {
        current(db, userId, now)?.let { return it }
        val restriction = db.record("admin:room-restriction:$userId:$roomId")?.let { orderJson.decodeFromString<AccountRestriction>(it) }
        return restriction?.takeIf { it.until == 0L || it.until > now }
    }
    fun block(restriction: AccountRestriction, roomId: String = "") = AccessBlock(restriction.until, restriction.reason, restriction.durationHours, restriction.removed, roomId)
    fun requireAllowed(db: RoomDatabase, userId: String, now: Long) {
        val restriction = current(db, userId, now) ?: return
        if (restriction.removed) throw AccountBlockedException(block(restriction))
    }
    fun requireRoomAllowed(db: RoomDatabase, userId: String, roomId: String, now: Long) {
        forRoom(db, userId, roomId, now)?.let { throw AccountBlockedException(block(it, roomId)) }
    }
}
internal class AccountBlockedException(val block: AccessBlock) : IllegalStateException(
    if (block.removed) "This Food Run account was removed. Contact the administrator." else "Your access to this room is temporarily blocked.")
