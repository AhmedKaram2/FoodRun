package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.UUID

/** Account identity owns membership. Display names and cached session tokens never identify a person. */
internal class AccountMemberships(private val db: RoomDatabase) {
    fun find(uid: String, room: Room): AccountRoom? {
        val saved = db.record("membership:$uid:${room.id}")?.let { orderJson.decodeFromString<AccountRoom>(it) }
        val active = room.members.filterNot { it.removed }
        val member = active.firstOrNull { it.id == saved?.memberId && owner(room.id, it.id).let { owner -> owner == null || owner == uid } }
            ?: active.firstOrNull { owner(room.id, it.id) == uid }
            ?: active.firstOrNull { it.id == memberId(room.id, uid) && owner(room.id, it.id).let { owner -> owner == null || owner == uid } }
            ?: return null
        return AccountRoom(room.id, room.name, member.id, saved?.takeIf { it.memberId == member.id }?.token.orEmpty())
    }

    fun all(uid: String): List<AccountRoom> = db.allRooms().mapNotNull { find(uid, it) }

    fun save(uid: String, room: Room, memberId: String, token: String) {
        require(room.members.any { it.id == memberId && !it.removed }) { "This room membership is no longer active." }
        require(owner(room.id, memberId).let { it == null || it == uid }) { "This room membership belongs to another account." }
        require(find(uid, room).let { it == null || it.memberId == memberId }) { "This account already has a membership in this room." }
        db.putRecord("membership:$uid:${room.id}", orderJson.encodeToString(AccountRoom(room.id, room.name, memberId, token)))
        db.putRecord("member-user:${room.id}:$memberId", uid)
    }

    private fun owner(roomId: String, memberId: String) = db.record("member-user:$roomId:$memberId")

    companion object {
        /** Stable per-room ID for newly created members, including recovery of missing indexes. */
        fun memberId(roomId: String, uid: String): String = UUID.nameUUIDFromBytes("foodrun:member:$roomId:$uid".toByteArray(Charsets.UTF_8)).toString()
    }
}
