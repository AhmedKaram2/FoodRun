package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class FriendContact(val email: String, val userId: String = "", val name: String = "")
@Serializable data class FriendGroup(val id: String, val name: String, val favourite: Boolean = true, val members: List<FriendContact> = emptyList())
