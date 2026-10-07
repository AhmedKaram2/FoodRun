package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.util.UUID

internal class FriendGroupService(private val db: RoomDatabase, private val accounts: AccountService,
    private val emails: EmailService, private val notifications: NotificationService, private val clock: () -> Long) {
    fun execute(c: RoomCommand): RoomReply {
        val uid = accounts.userId(c.identityToken)
        when(c.kind) {
            CommandKind.FRIEND_LOOKUP -> return RoomReply(friendContact = contact(db, c.text), serverTime = clock())
            CommandKind.SAVE_FRIEND_GROUP -> {
                require(c.friendGroupOwnerId.isEmpty() || c.friendGroupOwnerId == uid) { "Only the group owner can edit this group." }
                val incoming = requireNotNull(c.friendGroup)
                require(incoming.id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid friend group." }
                MenuValidation.label(incoming.name)
                require(incoming.members.size <= 30) { "Add up to 30 friends." }
                val members = incoming.members.map { contact(db, it.email) }.distinctBy { it.email }
                val key = "friend-group:$uid:${incoming.id}"
                val previous = db.record(key)?.let { orderJson.decodeFromString<FriendGroup>(it) }
                require(previous == null || previous.revision == incoming.revision) { "The group changed. Open it again before saving, or update your app." }
                require(previous != null || groups(db, uid).size < 20) { "Save up to 20 friend groups." }
                val saved = incoming.copy(name = incoming.name.trim(), members = members, revision = (previous?.revision ?: 0) + 1)
                db.putRecord(key, orderJson.encodeToString(saved))
                members.filter { it.userId.isEmpty() && previous?.members?.any { old -> old.email == it.email } != true }.forEach {
                    emails.friendInvitation(uid, saved, it, c.commandId, accounts.paymentRoomProfile(uid).name)
                }
            }
            CommandKind.DELETE_FRIEND_GROUP -> {
                require(c.friendGroupOwnerId.isEmpty() || c.friendGroupOwnerId == uid) { "Only the group owner can delete this group." }
                require(c.friendGroupId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid friend group." }
                db.deleteRecord("friend-group:$uid:${c.friendGroupId}")
            }
            CommandKind.LEAVE_FRIEND_GROUP -> {
                require(c.friendGroupOwnerId.length in 1..128 && c.friendGroupOwnerId != uid && c.friendGroupId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Choose a group you joined. Owners can edit or delete their own groups." }
                val key = "friend-group:${c.friendGroupOwnerId}:${c.friendGroupId}"
                val group = requireNotNull(db.record(key)) { "This group is no longer available." }.let { orderJson.decodeFromString<FriendGroup>(it) }
                require(group.members.any { isMember(db, it, uid) }) { "You are no longer a member of this group." }
                db.putRecord(key, orderJson.encodeToString(group.copy(members = group.members.filterNot { isMember(db, it, uid) }, revision = group.revision + 1)))
            }
            else -> error("Unsupported friend group action.")
        }
        return accounts.home(c.identityToken)
    }
    fun inviteRoom(c: RoomCommand, room: Room) {
        if(c.friendGroupId.isEmpty()) return
        val uid = accounts.userId(c.identityToken)
        val group = requireNotNull(db.record("friend-group:$uid:${c.friendGroupId}")) { "Choose one of your friend groups." }
            .let { orderJson.decodeFromString<FriendGroup>(it) }
        val ownerName = accounts.paymentRoomProfile(uid).name
        group.members.map { contact(db, it.email) }.forEach { member ->
            if(member.userId.isNotEmpty() && member.userId != uid && AccountRestrictions.current(db, member.userId, clock()) == null) {
                val id = UUID.nameUUIDFromBytes("${c.commandId}:${member.userId}".toByteArray()).toString()
                val invitation = FoodInvitation(id, member.userId, room.id, room.name, ownerName, room.orderNumber)
                db.putRecord("invitation:${member.userId}:$id", orderJson.encodeToString(invitation))
                notifications.wallet(member.userId, id, "friend_room_invitation", "You're invited to ${room.name}" to "دعوة للانضمام إلى ${room.name}",
                    "$ownerName invited you. Room code: ${room.code}." to "دعاك $ownerName. رمز الغرفة: ${room.code}.")
            }
            emails.friendInvitation(uid, group, member, c.commandId, ownerName, room)
        }
    }
    companion object {
        fun groups(db: RoomDatabase, uid: String): List<FriendGroup> = db.records("friend-group:$uid:")
            .map { resolved(db, orderJson.decodeFromString<FriendGroup>(it.second)) }.sortedBy { it.name.lowercase() }
        fun joined(db: RoomDatabase, uid: String): List<FriendGroupMembership> = db.records("friend-group:").mapNotNull { (key, body) ->
            val ownerId = key.removePrefix("friend-group:").substringBeforeLast(':')
            if(ownerId == uid) return@mapNotNull null
            val group = orderJson.decodeFromString<FriendGroup>(body)
            if(group.members.none { isMember(db, it, uid) }) return@mapNotNull null
            val owner = db.record("profile:$ownerId")?.let { orderJson.decodeFromString<FoodProfile>(it) } ?: return@mapNotNull null
            FriendGroupMembership(resolved(db, group), ownerId, owner.name)
        }.sortedBy { it.group.name.lowercase() }
        private fun isMember(db: RoomDatabase, member: FriendContact, uid: String): Boolean {
            if(member.userId.isNotEmpty()) return AccountAliases.resolve(db, member.userId) == uid
            val saved = db.record("account-email:${RoomService.hash(member.email.lowercase())}")
            return saved?.let { AccountAliases.resolve(db, it) == uid } == true || EmailContacts.searchAddress(db, uid)?.equals(member.email, true) == true
        }
        private fun resolved(db: RoomDatabase, group: FriendGroup) = group.copy(members = group.members.map { member ->
            val uid = member.userId.takeIf { it.isNotEmpty() } ?: db.record("account-email:${RoomService.hash(member.email.lowercase())}")
            val profile = uid?.let { db.record("profile:${AccountAliases.resolve(db, it)}") }?.let { orderJson.decodeFromString<FoodProfile>(it) }
            if(profile == null) member else member.copy(userId = profile.userId, name = profile.name)
        })
        fun contact(db: RoomDatabase, input: String): FriendContact {
            val email = input.trim().lowercase()
            require(EmailContacts.valid(email)) { "Enter a valid email address." }
            val uid = db.record("account-email:${RoomService.hash(email)}") ?: db.records("wallet-search-email:").firstOrNull { it.second.equals(email, true) }?.first?.removePrefix("wallet-search-email:")
                ?: db.records("email-contact:").firstOrNull { orderJson.decodeFromString<EmailContact>(it.second).address.equals(email, true) }?.first?.removePrefix("email-contact:")
            val canonical = uid?.let { AccountAliases.resolve(db, it) }
            val profile = canonical?.let { db.record("profile:$it") }?.let { orderJson.decodeFromString<FoodProfile>(it) }
            return if(profile == null || AccountRestrictions.current(db, profile.userId, System.currentTimeMillis())?.removed == true)
                FriendContact(email) else FriendContact(email, profile.userId, profile.name)
        }
    }
}
