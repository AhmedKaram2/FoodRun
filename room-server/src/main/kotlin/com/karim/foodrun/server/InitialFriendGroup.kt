package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

/** An explicitly configured one-time owner action; never recreates a deleted group. */
internal object InitialFriendGroup {
    @Serializable data class Request(val id: String, val name: String)
    fun configured(db: RoomDatabase, clock: () -> Long = System::currentTimeMillis, env: (String) -> String? = System::getenv): Int? {
        val input = env("FOODRUN_INITIAL_FRIEND_GROUP")?.takeIf { it.isNotBlank() } ?: return null
        return apply(db,orderJson.decodeFromString<Request>(input),clock)
    }
    fun apply(db: RoomDatabase, request: Request, clock: () -> Long): Int? = db.transaction {
        require(request.id.matches(Regex("[A-Za-z0-9-]{1,80}"))); MenuValidation.label(request.name)
        val owner = NotificationPreferencesStore.emailUser(db,AdminService.ADMIN_EMAIL)?.let { AccountAliases.resolve(db,it) }
            ?: error("The verified owner must sign in before creating the initial friend group.")
        require(EmailContacts.address(db,owner)?.equals(AdminService.ADMIN_EMAIL,true) == true) { "The initial group requires the verified owner email." }
        val marker = "initial-friend-group:$owner:${request.id}"
        if(db.record(marker) != null) return@transaction null
        val groupKey = "friend-group:$owner:${request.id}"
        require(db.record(groupKey) == null) { "This group already exists. Choose another initial group ID." }
        val members = db.records("profile:").map { orderJson.decodeFromString<FoodProfile>(it.second) }
            .filter { it.name.isNotBlank() && AccountAliases.resolve(db,it.userId) == it.userId && AccountRestrictions.current(db,it.userId,clock())?.removed != true }
            .map { FriendGroupService.registered(db,it.userId) }.distinctBy { it.memberKey() }
        require(members.size <= 30) { "The initial group exceeds the 30-member limit." }
        val group = FriendGroup(request.id,request.name.trim(),members = members,revision = 1)
        db.putRecord(groupKey,orderJson.encodeToString(group)); db.putRecord(marker,"${clock()}:${members.size}")
        val creator = members.single { it.userId == owner }.name
        val notifications = NotificationService(db,clock)
        members.filter { it.userId != owner }.forEach { notifications.friendGroup(it.userId,group,creator,"initial:${request.id}:${it.userId}") }
        members.size
    }
}
