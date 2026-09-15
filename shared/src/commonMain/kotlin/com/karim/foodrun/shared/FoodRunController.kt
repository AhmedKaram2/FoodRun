package com.karim.foodrun.shared

/**
 * UI-thread-owned state and intent boundary. Native adapters supply preference storage, timezone
 * offsets and animation frames; every roster, draft, navigation and winner decision stays here.
 */
class FoodRunController internal constructor(
    private val session: FoodRunSession,
    timeZone: FoodRunTimeZone,
) {
    constructor(storage: FoodRunStorage, timeZone: FoodRunTimeZone) :
        this(FoodRunSession(PreferencesFoodRunRepository(storage)), timeZone)

    private val dateFormatter = PickupDateFormatter(timeZone)
    private val observers = mutableMapOf<Int, (FoodRunState) -> Unit>()
    private var nextObserverId = 0
    private val queuedEvents = ArrayDeque<FoodRunEvent>()
    private var handlingEvent = false

    var state: FoodRunState = snapshot()
        private set

    fun observe(observer: (FoodRunState) -> Unit): FoodRunObservation {
        val id = nextObserverId++
        observers[id] = observer
        observer(state)
        return FoodRunObservation { observers.remove(id) }
    }

    fun dispatch(event: FoodRunEvent) {
        queuedEvents.addLast(event)
        if (handlingEvent) return
        handlingEvent = true
        try {
            while (queuedEvents.isNotEmpty()) handle(queuedEvents.removeFirst())
        } finally {
            handlingEvent = false
        }
    }

    fun openCrew() = dispatch(FoodRunEvent.OpenCrew)
    fun openHistory() = dispatch(FoodRunEvent.OpenHistory)
    fun openAddPerson() = dispatch(FoodRunEvent.OpenAddPerson)
    fun dismiss() = dispatch(FoodRunEvent.Dismiss)
    fun updateNameDraft(name: String) = dispatch(FoodRunEvent.UpdateNameDraft(name))
    fun submitName() = dispatch(FoodRunEvent.SubmitName)
    fun togglePerson(id: Int) = dispatch(FoodRunEvent.TogglePerson(id))
    fun removeAddedPerson(id: Int) = dispatch(FoodRunEvent.RemoveAddedPerson(id))
    fun includeEveryone() = dispatch(FoodRunEvent.IncludeEveryone)
    fun setHaptics(enabled: Boolean) = dispatch(FoodRunEvent.SetHaptics(enabled))
    fun cancelSpin() = dispatch(FoodRunEvent.CancelSpin)
    fun dismissError() = dispatch(FoodRunEvent.DismissError)

    fun beginSpin(startRotation: Double): SpinPlan? {
        if (!state.canSpin) return null
        dispatch(FoodRunEvent.BeginSpin(startRotation))
        return state.spinPlan
    }

    fun finishSpin(timeMillis: Double): Person? {
        if (!state.isSpinning) return null
        dispatch(FoodRunEvent.FinishSpin(timeMillis))
        return state.winner
    }

    private fun handle(event: FoodRunEvent) {
        when (event) {
            FoodRunEvent.OpenCrew -> if (state.canManageCrew && state.destination == FoodRunDestination.MAIN) {
                publish(state.copy(destination = FoodRunDestination.CREW))
            }
            FoodRunEvent.OpenHistory -> if (!state.isSpinning && state.destination == FoodRunDestination.MAIN) {
                refresh(destination = FoodRunDestination.HISTORY)
            }
            FoodRunEvent.OpenAddPerson -> if (state.destination == FoodRunDestination.CREW && !state.isSpinning) {
                publish(state.copy(destination = FoodRunDestination.ADD_PERSON, nameDraft = "", nameError = null))
            }
            FoodRunEvent.Dismiss -> dismissDestination()
            is FoodRunEvent.UpdateNameDraft -> if (state.addPersonPresented) {
                publish(state.copy(nameDraft = event.name, nameError = null, persistenceError = false))
            }
            FoodRunEvent.SubmitName -> if (state.addPersonPresented) saveAction {
                val result = session.addPerson(state.nameDraft)
                if (result.error != null) publish(state.copy(nameError = result.error))
                else refresh(resetWheel = true, destination = FoodRunDestination.CREW, clearDraft = true)
            }
            is FoodRunEvent.TogglePerson -> saveAction { if (session.togglePerson(event.id)) refresh(resetWheel = true) }
            is FoodRunEvent.RemoveAddedPerson -> saveAction { if (session.removeAddedPerson(event.id)) refresh(resetWheel = true) }
            FoodRunEvent.IncludeEveryone -> saveAction { if (session.includeEveryone()) refresh(resetWheel = true) }
            is FoodRunEvent.SetHaptics -> saveAction { session.setHapticsEnabled(event.enabled); refresh() }
            is FoodRunEvent.BeginSpin -> if (state.canSpin) {
                session.beginSpin(event.startRotation)?.let { plan ->
                    publish(state.copy(spinPlan = plan, winner = null, persistenceError = false,
                        crewItems = crewItems(isSpinning = true)))
                }
            }
            is FoodRunEvent.FinishSpin -> saveAction {
                session.finishSpin(event.timeMillis)?.let { winner ->
                    refresh(destination = FoodRunDestination.WINNER, winner = winner, clearSpin = true)
                }
            }
            FoodRunEvent.CancelSpin -> if (state.isSpinning) {
                session.cancelSpin()
                refresh(clearSpin = true)
            }
            FoodRunEvent.DismissError -> publish(state.copy(persistenceError = false))
        }
    }

    private fun dismissDestination() {
        val destination = when (state.destination) {
            FoodRunDestination.ADD_PERSON -> FoodRunDestination.CREW
            FoodRunDestination.CREW, FoodRunDestination.HISTORY, FoodRunDestination.WINNER -> FoodRunDestination.MAIN
            FoodRunDestination.MAIN -> return
        }
        publish(state.copy(destination = destination, nameDraft = "", nameError = null, persistenceError = false))
    }

    private inline fun saveAction(action: () -> Unit) {
        try {
            action()
        } catch (_: FoodRunPersistenceException) {
            if (state.isSpinning) session.cancelSpin()
            refresh(clearSpin = true, persistenceError = true)
        }
    }

    private fun refresh(
        resetWheel: Boolean = false,
        destination: FoodRunDestination = state.destination,
        winner: Person? = state.winner,
        clearDraft: Boolean = false,
        clearSpin: Boolean = false,
        persistenceError: Boolean = false,
    ) {
        publish(snapshot().copy(
            destination = destination,
            nameDraft = if (clearDraft) "" else state.nameDraft,
            nameError = if (clearDraft) null else state.nameError,
            winner = if (resetWheel) null else winner,
            spinPlan = if (clearSpin) null else state.spinPlan,
            wheelRevision = state.wheelRevision + if (resetWheel) 1 else 0,
            persistenceError = persistenceError,
        ))
    }

    private fun snapshot() = FoodRunState(
        people = session.people,
        activePeople = session.activePeople,
        crewItems = crewItems(session.isSpinning),
        historyItems = session.history.map { PickupHistoryItem(it, dateFormatter.format(it.timeMillis)) },
        hapticsEnabled = session.hapticsEnabled,
    )

    private fun crewItems(isSpinning: Boolean): List<CrewPersonItem> {
        val activeIds = session.activePeople.map { it.id }.toSet()
        return session.people.map { person ->
            val included = person.id in activeIds
            val lastIncluded = included && activeIds.size == 1
            val custom = person.id >= FoodRunRules.defaultCrew.size
            CrewPersonItem(person, included, custom, !isSpinning && !lastIncluded, custom && !isSpinning && !lastIncluded)
        }
    }

    private fun publish(value: FoodRunState) {
        if (state == value) return
        state = value
        observers.values.toList().forEach { it(value) }
    }
}
