package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

@Serializable data class QuickChoicePerson(val id: String, val name: String, val active: Boolean = true)
@Serializable internal data class QuickChoiceSaved(
    val people: List<QuickChoicePerson> = emptyList(),
    val purpose: String = "", val style: String = "names", val groupId: String = "", val audience: String = "participants",
)

/** A private, account-scoped picker. Editing its roster never changes a friend group or room. */
internal class GroupQuickChoice(private val platform: GroupPlatform) {
    private var account: String? = null
    private var saved = QuickChoiceSaved()
    var destination = "MAIN"; private set
    var nameDraft = ""; private set
    var error = ""; private set
    var revision = 0; private set
    val people get() = saved.people
    val purpose get() = saved.purpose
    val style get() = saved.style
    val groupId get() = saved.groupId
    val audience get() = saved.audience
    val canSpin get() = destination == "MAIN" && people.any { it.active }

    fun account(userId: String) {
        if(account == userId) return
        account = userId; destination = "MAIN"; nameDraft = ""; error = ""; revision++
        saved = QuickChoiceSaved()
    }

    fun action(action: String, value: String, groups: List<FriendGroupMembership>) {
        error = ""
        try {
            when(action) {
                "crew" -> destination = "CREW"
                "settings" -> destination = "SETTINGS"
                "groups" -> destination = "GROUPS"
                "add" -> { destination = "ADD_PERSON"; nameDraft = "" }
                "dismiss" -> { destination = if(destination == "ADD_PERSON") "CREW" else "MAIN"; nameDraft = "" }
                "name" -> nameDraft = value.take(80)
                "save" -> {
                    val name = value.ifBlank { nameDraft }.trim().replace(Regex("\\s+"), " ")
                    require(name.isNotEmpty()) { "Enter a name." }
                    require(name.length <= 80) { "Keep the name to 80 characters or fewer." }
                    require(people.none { it.name.equals(name, true) }) { "This name is already included." }
                    save(saved.copy(people = people + QuickChoicePerson("name:${platform.uuid()}", name)), reset = true)
                    destination = "CREW"; nameDraft = ""
                }
                "toggle" -> save(saved.copy(people = people.map { if(it.id == value) it.copy(active = !it.active) else it }), reset = true)
                "remove" -> save(saved.copy(people = people.filterNot { it.id == value }), reset = true)
                "everyone" -> save(saved.copy(people = people.map { it.copy(active = true) }), reset = true)
                "clear" -> save(saved.copy(people = emptyList(), groupId = ""), reset = true)
                "purpose" -> save(saved.copy(purpose = value.take(160)), reset = true)
                "style" -> { require(value in listOf("names", "wheel")); save(saved.copy(style = value), reset = true) }
                "audience" -> { require(value in listOf("participants","all")); save(saved.copy(audience=value)) }
                "group" -> {
                    val selected = requireNotNull(groups.singleOrNull { it.selectionKey() == value }) { "This group is no longer available." }
                    val contacts = listOf(FriendContact(email = "", userId = selected.ownerId, name = selected.ownerName)) + selected.group.members
                    val roster = contacts.mapNotNull { contact ->
                        val id = if(contact.userId.isNotBlank()) "user:${contact.userId}" else contact.email.trim().lowercase().takeIf { it.isNotEmpty() }?.let { "email:$it" }
                        id?.let { QuickChoicePerson(it, contact.name.trim().ifBlank { contact.email }) }
                    }.filter { it.name.isNotBlank() }.distinctBy { it.id }
                    save(saved.copy(people = roster, groupId = value), reset = true); destination = "CREW"
                }
                else -> error("Unknown quick choice action.")
            }
        } catch(failure: Exception) {
            error = failure.message ?: "Could not save this change. Try again."
        }
    }

    private fun save(value: QuickChoiceSaved, reset: Boolean = false) {
        saved = value
        if(reset) revision++
    }
}

internal fun HomePayload.quickChoiceGroups(): List<FriendGroupMembership> =
    (friendGroups.map { FriendGroupMembership(it, profile.userId, profile.name) } + joinedFriendGroups).distinctBy { it.selectionKey() }
