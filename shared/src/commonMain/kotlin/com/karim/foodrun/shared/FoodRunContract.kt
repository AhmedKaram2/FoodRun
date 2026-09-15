package com.karim.foodrun.shared

/** Immutable view state shared by the Android and iOS adapters. */
@ConsistentCopyVisibility
data class FoodRunState internal constructor(
    val people: List<Person>,
    val activePeople: List<Person>,
    val crewItems: List<CrewPersonItem>,
    val historyItems: List<PickupHistoryItem>,
    val hapticsEnabled: Boolean,
    val destination: FoodRunDestination = FoodRunDestination.MAIN,
    val nameDraft: String = "",
    val nameError: AddPersonError? = null,
    val winner: Person? = null,
    val spinPlan: SpinPlan? = null,
    val wheelRevision: Int = 0,
    val persistenceError: Boolean = false,
) {
    val isSpinning: Boolean get() = spinPlan != null
    val canSpin: Boolean get() = !isSpinning && destination == FoodRunDestination.MAIN
    val canManageCrew: Boolean get() = !isSpinning
    val canSubmitName: Boolean get() = !isSpinning && FoodRunRules.normalizedName(nameDraft).isNotEmpty()
    val showWinner: Boolean get() = destination == FoodRunDestination.WINNER
    val crewPresented: Boolean get() = destination == FoodRunDestination.CREW || destination == FoodRunDestination.ADD_PERSON
    val addPersonPresented: Boolean get() = destination == FoodRunDestination.ADD_PERSON
    val historyPresented: Boolean get() = destination == FoodRunDestination.HISTORY
    val historyIsEmpty: Boolean get() = historyItems.isEmpty()
    val crewPreview: List<Person> get() = activePeople.take(3)
    val statusLabel: String get() = FoodRunText.status(isSpinning, winner?.name, activePeople.size)
    val spinButtonLabel: String get() = FoodRunText.spinButton(isSpinning)
    val missionLabel: String get() = FoodRunText.mission(activePeople.size)
    val crewSummary: String get() = FoodRunText.crewSummary(activePeople.size)
    val crewAccessibility: String get() = FoodRunText.crewAccessibility(activePeople.size)
    val winnerAnnouncement: String? get() = winner?.let { FoodRunText.winnerAnnouncement(it.name) }
    val winnerTitle: String? get() = winner?.let { FoodRunText.winnerTitle(it.name) }
    val winnerShare: String? get() = winner?.let { FoodRunText.winnerShare(it.name) }
    val wheelAccessibilityState: String get() = FoodRunText.wheelState(isSpinning, winner?.name, activePeople.size)
    val nameErrorMessage: String? get() = nameError?.let { FoodRunText.addPersonError(it) }
    val persistenceErrorMessage: String? get() = if (persistenceError) FoodRunText.saveFailed else null
}

sealed class FoodRunEvent {
    data object OpenCrew : FoodRunEvent()
    data object OpenHistory : FoodRunEvent()
    data object OpenAddPerson : FoodRunEvent()
    data object Dismiss : FoodRunEvent()
    data class UpdateNameDraft(val name: String) : FoodRunEvent()
    data object SubmitName : FoodRunEvent()
    data class TogglePerson(val id: Int) : FoodRunEvent()
    data class RemoveAddedPerson(val id: Int) : FoodRunEvent()
    data object IncludeEveryone : FoodRunEvent()
    data class SetHaptics(val enabled: Boolean) : FoodRunEvent()
    data class BeginSpin(val startRotation: Double) : FoodRunEvent()
    data class FinishSpin(val timeMillis: Double) : FoodRunEvent()
    data object CancelSpin : FoodRunEvent()
    data object DismissError : FoodRunEvent()
}
