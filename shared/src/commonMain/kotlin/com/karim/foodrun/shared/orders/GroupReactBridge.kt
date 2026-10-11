package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

/** View-only data: account/room session tokens and saved commands never cross the JS boundary. */
@Serializable data class ReactGroupScreen(
    val state: GroupState, val adminAvailable: Boolean, val supportActive: Boolean, val accountId: String, val authenticated: Boolean, val mainFields: List<GroupField>, val extraFields: List<GroupField>,
    val topCards: List<GroupCard>, val sections: List<GroupSection>, val primaryAction: GroupButton?,
    val inlineButtons: List<GroupButton>, val utilityButtons: List<GroupButton>, val quick: ReactQuickWheel, val profile: ReactProfile? = null,
    val notificationUnread: Int = 0, val tourAvailable: Boolean = false,
    val quickSelection: QuickChoiceSession? = null, val quickSelectionOffset: Long = 0,
)
@Serializable data class ReactProfile(val name: String, val photo: String)
@Serializable data class ReactWheelPerson(val id: String, val name: String, val active: Boolean, val removable: Boolean = true)
@Serializable data class ReactWheelHistory(val id: String, val name: String, val date: String, val purpose: String = "")
@Serializable data class ReactQuickGroup(val id: String, val name: String, val count: Int)
@Serializable data class ReactQuickWheel(
    val people: List<ReactWheelPerson>, val history: List<ReactWheelHistory>, val winner: String,
    val spinning: Boolean, val canSpin: Boolean, val startRotation: Double, val endRotation: Double,
    val destination: String, val nameDraft: String, val error: String, val revision: Int,
    val purpose: String = "", val style: String = "names", val groupId: String = "", val groups: List<ReactQuickGroup> = emptyList(), val audience: String = "participants",
)
class GroupReactBridge(val groups: GroupController) {
    private val quickChoice = GroupQuickChoice(groups.platform)
    private val viewJson = kotlinx.serialization.json.Json(orderJson) { encodeDefaults = true }
    private fun field(value: GroupField) = if(value.secret) value.copy(value = "") else value
    fun snapshotJson(): String {
        val state = groups.state
        quickChoice.account(groups.library.home?.profile?.userId.orEmpty())
        val quick = quickChoice
        val quickGroups = groups.library.home?.quickChoiceGroups().orEmpty()
        return viewJson.encodeToString(ReactGroupScreen(
            state.copy(fields = state.fields.map(::field)),groups.administration.allowed,groups.supportActive,groups.library.home?.profile?.userId.orEmpty(), groups.library.identityToken.isNotEmpty(),
            state.mainFields.map(::field),state.extraFields.map(::field),state.topCards,state.sections,state.primaryAction,state.inlineButtons,state.utilityButtons,
            ReactQuickWheel(quick.people.map { ReactWheelPerson(it.id,it.name,it.active) },
                emptyList(),"",groups.quickChoiceBusy,quick.canSpin && !groups.quickChoiceBusy,
                0.0,0.0,quick.destination,quick.nameDraft,GroupUiText.translate(quick.error,groups.library.language),quick.revision,
                quick.purpose,quick.style,quick.groupId,quickGroups.map { membership -> ReactQuickGroup(membership.selectionKey(),membership.group.name,
                    (membership.group.members.map { it.memberKey() } + "user:${membership.ownerId}").distinct().size) },quick.audience),
            groups.library.home?.profile?.let { ReactProfile(it.name,it.photo) },
            if(groups.library.identityToken.isNotEmpty()) groups.notifications.items.count { !it.read } else 0,
            groups.library.identityToken.isNotEmpty() && !groups.supportActive && !groups.notifications.tourCompleted,
            groups.quickSelection.takeIf { groups.library.identityToken.isNotEmpty() },groups.quickSelectionOffset,
        ))
    }
    fun dispatch(action: String, value: String) {
        groups.dispatch(GroupAction.valueOf(action),value)
        if(action in listOf("OPEN_PROFILE", "OPEN_WALLET") && groups.page == GroupPage.CONNECT && groups.library.selectedHub == null) groups.dispatch(GroupAction.USE_INTERNET)
    }
    fun update(key: String, value: String) { groups.update(GroupFieldKey.valueOf(key),value) }
    fun tick() { groups.tickAccessBlock(); groups.tickPaymentReminders() }
    fun quickAction(action: String, value: String) {
        quickChoice.account(groups.library.home?.profile?.userId.orEmpty())
        if(action == "spin") { groups.quickChoice(QuickChoiceRequest(purpose=quickChoice.purpose,style=quickChoice.style,groupId=quickChoice.groupId,audience=quickChoice.audience,
            people=quickChoice.people.filter { it.active }.map { QuickChoiceCandidate(it.id,it.name) })); return }
        if(action in listOf("retry-live","close-live")) { groups.quickChoice(QuickChoiceRequest(action=if(action == "retry-live") "retry" else "close",id=value)); return }
        quickChoice.action(action,value,groups.library.home?.quickChoiceGroups().orEmpty())
        groups.publish()
    }
}
