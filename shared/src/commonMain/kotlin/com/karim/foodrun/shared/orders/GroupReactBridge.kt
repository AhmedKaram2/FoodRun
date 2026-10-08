package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.orderJson
import com.karim.foodrun.shared.FoodRunController
import kotlinx.serialization.Serializable

/** View-only data: account/room session tokens and saved commands never cross the JS boundary. */
@Serializable data class ReactGroupScreen(
    val state: GroupState, val adminAvailable: Boolean, val supportActive: Boolean, val accountId: String, val authenticated: Boolean, val mainFields: List<GroupField>, val extraFields: List<GroupField>,
    val topCards: List<GroupCard>, val sections: List<GroupSection>, val primaryAction: GroupButton?,
    val inlineButtons: List<GroupButton>, val utilityButtons: List<GroupButton>, val quick: ReactQuickWheel, val profile: ReactProfile? = null,
)
@Serializable data class ReactProfile(val name: String, val photo: String)
@Serializable data class ReactWheelPerson(val id: Int, val name: String, val active: Boolean, val removable: Boolean)
@Serializable data class ReactWheelHistory(val id: String, val name: String, val date: String)
@Serializable data class ReactQuickWheel(
    val people: List<ReactWheelPerson>, val history: List<ReactWheelHistory>, val winner: String,
    val spinning: Boolean, val canSpin: Boolean, val startRotation: Double, val endRotation: Double,
    val destination: String, val nameDraft: String, val error: String, val revision: Int,
)
class GroupReactBridge(val groups: GroupController, private val wheel: FoodRunController) {
    private val viewJson = kotlinx.serialization.json.Json(orderJson) { encodeDefaults = true }
    private fun field(value: GroupField) = if(value.secret) value.copy(value = "") else value
    fun snapshotJson(): String {
        val state = groups.state
        val quick = wheel.state
        return viewJson.encodeToString(ReactGroupScreen(
            state.copy(fields = state.fields.map(::field)),groups.administration.allowed,groups.supportActive,groups.library.home?.profile?.userId.orEmpty(), groups.library.identityToken.isNotEmpty(),
            state.mainFields.map(::field),state.extraFields.map(::field),state.topCards,state.sections,state.primaryAction,state.inlineButtons,state.utilityButtons,
            ReactQuickWheel(quick.people.map { ReactWheelPerson(it.id,it.name,quick.activePeople.any { person -> person.id == it.id },quick.crewItems.any { person -> person.id == it.id && person.isCustom }) },
                quick.historyItems.map { ReactWheelHistory(it.id,it.person.name,it.dateLabel) },quick.winner?.name.orEmpty(),quick.isSpinning,quick.canSpin,
                quick.spinPlan?.startRotation ?: 0.0,quick.spinPlan?.endRotation ?: 0.0,quick.destination.name,quick.nameDraft,quick.nameErrorMessage.orEmpty(),quick.wheelRevision),
            groups.library.home?.profile?.let { ReactProfile(it.name,it.photo) },
        ))
    }
    fun dispatch(action: String, value: String) {
        groups.dispatch(GroupAction.valueOf(action),value)
        if(action in listOf("OPEN_PROFILE", "OPEN_WALLET") && groups.page == GroupPage.CONNECT && groups.library.selectedHub == null) groups.dispatch(GroupAction.USE_INTERNET)
    }
    fun update(key: String, value: String) { groups.update(GroupFieldKey.valueOf(key),value) }
    fun tick() { groups.tickAccessBlock(); groups.tickPaymentReminders() }
    fun quickAction(action: String, value: String) {
        when(action) {
            "crew" -> wheel.openCrew(); "history" -> wheel.openHistory(); "add" -> wheel.openAddPerson(); "dismiss" -> wheel.dismiss()
            "name" -> wheel.updateNameDraft(value); "save" -> wheel.submitName(); "toggle" -> wheel.togglePerson(value.toInt())
            "remove" -> wheel.removeAddedPerson(value.toInt()); "everyone" -> wheel.includeEveryone()
            "spin" -> wheel.beginSpin(value.toDoubleOrNull() ?: 0.0); "finish" -> wheel.finishSpin(groups.platform.now().toDouble())
            "cancel" -> wheel.cancelSpin()
            else -> error("Unknown wheel action.")
        }
    }
}
