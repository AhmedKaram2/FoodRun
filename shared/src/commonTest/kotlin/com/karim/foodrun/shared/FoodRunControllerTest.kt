package com.karim.foodrun.shared

import kotlin.random.Random
import kotlin.test.*

class FoodRunControllerTest {
    private class MemoryStorage(var value: String? = null) : FoodRunStorage {
        var failWrites = false
        override fun read() = value
        override fun write(value: String) {
            check(!failWrites) { "Preference write failed" }
            this.value = value
        }
    }

    private class MemoryRepository(var saved: SavedState = SavedState()) : FoodRunRepository {
        override fun load() = saved
        override fun save(state: SavedState) { saved = state }
    }

    private fun controller(repository: MemoryRepository = MemoryRepository()) =
        FoodRunController(FoodRunSession(repository, Random(42)), UtcFoodRunTimeZone())

    @Test fun initialContractIsCompleteAndReadOnlyForBothPlatforms() {
        val state = controller().state
        assertEquals(FoodRunDestination.MAIN, state.destination)
        assertEquals(FoodRunRules.defaultCrew, state.people)
        assertEquals(10, state.crewItems.size)
        assertTrue(state.canSpin)
        assertFalse(state.canSubmitName)
        assertTrue(state.historyIsEmpty)
        assertEquals("10 friends. Equal chances.", state.statusLabel)
        assertEquals("10 friends in", state.crewSummary)
        assertTrue(state.crewItems.all { it.included && it.canToggle && !it.canRemove && !it.isCustom })
    }

    @Test fun sharedPersonDisplayDoesNotSplitEmojiSurrogatePairs() {
        val person = Person(10, "🙂".repeat(13))
        assertEquals("🙂", person.initial)
        assertEquals("🙂".repeat(11) + "…", person.wheelLabel)
        assertEquals("Hassan", Person(2, "Hassan").wheelLabel)
    }

    @Test fun nestedAddDismissClosesOnlyAddAndReentryStartsFresh() {
        val controller = controller()
        controller.openCrew()
        controller.openAddPerson()
        controller.updateNameDraft("Omar")
        assertTrue(controller.state.crewPresented)
        assertTrue(controller.state.addPersonPresented)
        controller.dismiss()
        assertEquals(FoodRunDestination.CREW, controller.state.destination)
        assertEquals("", controller.state.nameDraft)
        controller.openAddPerson()
        assertEquals("", controller.state.nameDraft)
        controller.dismiss()
        controller.dismiss()
        assertEquals(FoodRunDestination.MAIN, controller.state.destination)
        controller.dismiss()
        assertEquals(FoodRunDestination.MAIN, controller.state.destination)
    }

    @Test fun nameValidationStaysOnSheetAndEditingClearsError() {
        val controller = controller()
        controller.openCrew()
        controller.openAddPerson()
        controller.submitName()
        assertEquals(AddPersonError.EMPTY_NAME, controller.state.nameError)
        assertNotNull(controller.state.nameErrorMessage)
        controller.updateNameDraft(" kArIm ")
        assertNull(controller.state.nameError)
        controller.submitName()
        assertEquals(AddPersonError.DUPLICATE_NAME, controller.state.nameError)
        assertEquals(FoodRunDestination.ADD_PERSON, controller.state.destination)
        controller.updateNameDraft("a".repeat(33))
        controller.submitName()
        assertEquals(AddPersonError.NAME_TOO_LONG, controller.state.nameError)
        assertEquals(10, controller.state.people.size)
    }

    @Test fun successfulAddSavesOnceIncludesPersonAndReturnsToCrew() {
        val storage = MemoryStorage()
        val controller = FoodRunController(storage, UtcFoodRunTimeZone())
        controller.openCrew()
        controller.openAddPerson()
        controller.updateNameDraft("  Omar   Ali ")
        controller.submitName()
        controller.submitName()
        assertEquals(FoodRunDestination.CREW, controller.state.destination)
        assertEquals("", controller.state.nameDraft)
        assertNull(controller.state.nameError)
        assertEquals(1, controller.state.wheelRevision)
        val row = controller.state.crewItems.last()
        assertEquals("Omar Ali", row.name)
        assertTrue(row.included && row.canRemove && row.isCustom)
        assertEquals(11, FoodRunController(storage, UtcFoodRunTimeZone()).state.people.size)
    }

    @Test fun lastParticipantFlagsMatchDomainGuardsAndRosterChangesResetWinner() {
        val controller = controller()
        (1..9).forEach(controller::togglePerson)
        val remaining = controller.state.crewItems.first()
        assertFalse(remaining.canToggle)
        val revision = controller.state.wheelRevision
        controller.togglePerson(0)
        assertEquals(revision, controller.state.wheelRevision)
        assertEquals("1 friend. Equal chances.", controller.state.statusLabel)
        assertEquals("A solo food mission. You’ve got this.", controller.state.missionLabel)
        controller.beginSpin(0.0)
        assertEquals("Karim", controller.finishSpin(1000.0)?.name)
        controller.dismiss()
        controller.includeEveryone()
        assertNull(controller.state.winner)
        assertEquals(10, controller.state.activePeople.size)
    }

    @Test fun spinLocksRosterAndNavigationAndFinishesOnceWithHistoryAndWinner() {
        val controller = controller()
        val original = controller.state
        val plan = assertNotNull(controller.beginSpin(123.0))
        assertTrue(controller.state.isSpinning)
        assertFalse(controller.state.canSpin)
        assertFalse(controller.state.canManageCrew)
        assertTrue(controller.state.crewItems.none { it.canToggle || it.canRemove })
        controller.openCrew()
        controller.openHistory()
        controller.togglePerson(0)
        controller.includeEveryone()
        assertNull(controller.beginSpin(0.0))
        assertEquals(original.activePeople, controller.state.activePeople)
        assertEquals(FoodRunDestination.MAIN, controller.state.destination)
        val winner = assertNotNull(controller.finishSpin(1000.0))
        assertEquals(original.activePeople[plan.winnerIndex], winner)
        assertTrue(controller.state.showWinner)
        assertFalse(controller.state.isSpinning)
        assertEquals(winner, controller.state.historyItems.single().person)
        assertEquals("Jan 1, 12:00 AM", controller.state.historyItems.single().dateLabel)
        assertEquals("${winner.name} is on pickup duty!", controller.state.statusLabel)
        assertEquals("${winner.name} is picking up the food!", controller.state.winnerAnnouncement)
        assertNull(controller.finishSpin(2000.0))
        controller.dismiss()
        assertTrue(controller.state.canSpin)
        assertEquals(winner, controller.state.winner)
    }

    @Test fun cancelledAnimationDoesNotRecordPickupAndCanRestart() {
        val controller = controller()
        controller.beginSpin(0.0)
        controller.cancelSpin()
        assertFalse(controller.state.isSpinning)
        assertTrue(controller.state.historyIsEmpty)
        assertNull(controller.finishSpin(1000.0))
        assertNotNull(controller.beginSpin(0.0))
    }

    @Test fun observersReceiveInitialAndOneSnapshotPerActionAndStopAfterCancel() {
        val controller = controller()
        val states = mutableListOf<FoodRunState>()
        val observation = controller.observe(states::add)
        controller.openCrew()
        controller.openCrew()
        assertEquals(2, states.size)
        controller.togglePerson(0)
        assertEquals(10, states.first().activePeople.size)
        assertEquals(9, states.last().activePeople.size)
        observation.cancel()
        observation.cancel()
        controller.dismiss()
        assertEquals(3, states.size)
    }

    @Test fun observerTriggeredEventsAreDeliveredInOrderToAllObservers() {
        val controller = controller()
        val destinations = mutableListOf<FoodRunDestination>()
        val first = controller.observe { if (it.destination == FoodRunDestination.CREW) controller.openAddPerson() }
        val second = controller.observe { destinations += it.destination }
        controller.openCrew()
        assertEquals(listOf(FoodRunDestination.MAIN, FoodRunDestination.CREW, FoodRunDestination.ADD_PERSON), destinations)
        first.cancel()
        second.cancel()
    }

    @Test fun preferenceWriteFailurePreservesDraftAndRosterAndRetrySucceeds() {
        val storage = MemoryStorage()
        val controller = FoodRunController(storage, UtcFoodRunTimeZone())
        controller.openCrew()
        controller.openAddPerson()
        controller.updateNameDraft("Omar")
        storage.failWrites = true
        controller.submitName()
        assertEquals(10, controller.state.people.size)
        assertEquals("Omar", controller.state.nameDraft)
        assertEquals(FoodRunDestination.ADD_PERSON, controller.state.destination)
        assertNotNull(controller.state.persistenceErrorMessage)
        storage.failWrites = false
        controller.submitName()
        assertEquals(11, controller.state.people.size)
        assertNull(controller.state.persistenceErrorMessage)
    }

    @Test fun failedHistorySaveUnlocksWheelWithoutPublishingUnpersistedWinner() {
        val storage = MemoryStorage()
        val controller = FoodRunController(storage, UtcFoodRunTimeZone())
        controller.beginSpin(0.0)
        storage.failWrites = true
        assertNull(controller.finishSpin(1000.0))
        assertFalse(controller.state.isSpinning)
        assertTrue(controller.state.historyIsEmpty)
        assertFalse(controller.state.showWinner)
        assertNotNull(controller.state.persistenceErrorMessage)
        controller.dismissError()
        assertNull(controller.state.persistenceErrorMessage)
        storage.failWrites = false
        assertNotNull(controller.beginSpin(0.0))
    }

    @Test fun historyNavigationAndDraftEventsCannotChangeUnderlyingMainScreen() {
        val controller = controller()
        controller.updateNameDraft("Omar")
        controller.submitName()
        controller.openAddPerson()
        assertEquals(FoodRunDestination.MAIN, controller.state.destination)
        assertEquals("", controller.state.nameDraft)
        controller.openHistory()
        assertTrue(controller.state.historyPresented)
        controller.openCrew()
        assertTrue(controller.state.historyPresented)
        controller.dismiss()
        assertEquals(FoodRunDestination.MAIN, controller.state.destination)
    }

    @Test fun invalidAnimationInputsCannotLeaveWheelLocked() {
        val controller = controller()
        assertNull(controller.beginSpin(Double.NaN))
        assertFalse(controller.state.isSpinning)
        assertNotNull(controller.beginSpin(0.0))
        assertNull(controller.finishSpin(Double.POSITIVE_INFINITY))
        assertTrue(controller.state.isSpinning)
        assertNotNull(controller.finishSpin(0.0))
    }
}
