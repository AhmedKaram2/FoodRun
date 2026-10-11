package com.karim.foodrun.orders

import kotlinx.serialization.Serializable

@Serializable data class QuickChoiceCandidate(val id: String, val name: String)
@Serializable data class QuickChoiceRequest(val action: String = "start", val id: String = "", val purpose: String = "", val style: String = "names",
    val groupId: String = "", val people: List<QuickChoiceCandidate> = emptyList(), val audience: String = "participants")
@Serializable data class QuickChoiceSession(val id: String, val ownerId: String, val ownerName: String, val purpose: String, val style: String,
    val people: List<QuickChoiceCandidate>, val spin: SpinRound, val expiresAt: Long)
