package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class FriendContact(val email: String, val userId: String = "", val name: String = "")
fun FriendContact.memberKey(): String = if(userId.isNotEmpty()) "user:$userId" else "email:${email.lowercase()}"
@Serializable data class FriendGroup(val id: String, val name: String, val favourite: Boolean = true, val members: List<FriendContact> = emptyList(),
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val revision: Long = 0,
)
@Serializable data class FriendGroupMembership(val group: FriendGroup, val ownerId: String, val ownerName: String)

fun FriendGroupMembership.selectionKey(): String = "$ownerId:${group.id}"
fun HomePayload.roomFriendGroups(): List<FriendGroupMembership> =
    (friendGroups.filter { it.favourite }.map { FriendGroupMembership(it, profile.userId, profile.name) } + joinedFriendGroups)
        .distinctBy { it.selectionKey() }
