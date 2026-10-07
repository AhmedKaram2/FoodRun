package com.karim.foodrun.server

import com.karim.foodrun.orders.*

/** One profile update also refreshes the current recipient in the user's active rooms. */
internal class ProfileUpdates(private val db: RoomDatabase, private val clock: () -> Long) {
    fun save(profile: FoodProfile): List<String> {
        val previous = db.record("profile:${profile.userId}")?.let { orderJson.decodeFromString<FoodProfile>(it) }
        val memberships = AccountMemberships(db).all(profile.userId)
        val changed = memberships.mapNotNull { membership ->
            val room = db.room(membership.roomId) ?: return@mapNotNull null
            if (room.phase in listOf(RoomPhase.ARCHIVED, RoomPhase.CANCELLED)) return@mapNotNull null
            val member = room.members.singleOrNull { it.id == membership.memberId && !it.removed } ?: return@mapNotNull null
            val name = profile.name.ifBlank { member.name }
            if (name != member.name) require(room.members.none { it.id != member.id && !it.removed && it.name.equals(name, true) }) {
                "Another member in ${room.name} already uses this name."
            }
            val syncPayment = room.payerId == member.id && (profile.receivingAccounts.isNotEmpty() || previous?.receivingAccounts?.isNotEmpty() == true)
            val methods = if (syncPayment) profile.receivingAccounts.filter { it.currency == room.restaurant.currency } else room.receivingAccounts
            val account = if (syncPayment) methods.firstOrNull()?.let { payment ->
                val candidate = payment.copy(version = room.account?.version ?: 1)
                if (candidate == room.account) room.account else candidate.copy(version = (room.account?.version ?: 0) + 1)
            } else room.account
            val alternatives = methods.filterNot { it.id == account?.id }
            if (name == member.name && account == room.account && alternatives == room.accounts) return@mapNotNull null
            room.copy(
                members = room.members.map { if (it.id == member.id) it.copy(name = name) else it },
                lastChosenName = if (room.lastChosenMemberId == member.id) name else room.lastChosenName,
                account = account, accounts = alternatives,
                quoteRevision = room.quoteRevision + if ((account != room.account || alternatives != room.accounts) && room.phase in listOf(RoomPhase.COLLECTING, RoomPhase.REVIEW)) 1 else 0,
                revision = room.revision + 1, updatedAt = clock(),
            )
        }
        db.transaction {
            db.putRecord("profile:${profile.userId}", orderJson.encodeToString(profile))
            db.putRecord("profile-sync:${profile.userId}", orderJson.encodeToString(profile))
            db.deleteRecord("admin:profile-name:${profile.userId}")
            changed.forEach(db::save)
        }
        return changed.map { it.id }
    }
}
