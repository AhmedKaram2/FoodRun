package com.karim.foodrun.shared

import kotlin.test.*

class FoodRunEngineTest {
    private class MemoryStorage(var content: String? = null) : FoodRunStorage {
        override fun read() = content
        override fun write(value: String) { content = value }
    }

    @Test fun initialCrewMatchesRequestedNames() {
        assertEquals(listOf("Karim", "Karam", "Hassan", "Mersal", "Baraa", "Fayed", "Ayman", "Rayan", "Gaber", "Fakhr"), FoodRunEngine(MemoryStorage()).people.map { it.name })
    }

    @Test fun everyWinnerLandsUnderPointer() {
        for (count in 1..30) for (winner in 0 until count) {
            for (start in listOf(0.0, 13.5, 359.9, 2871.25)) for (offset in listOf(-0.28, 0.0, 0.28)) {
                val plan = SpinPlan(winner, count, start, 7, offset)
                assertEquals(winner, SpinPlan.indexAtPointer(plan.endRotation, count))
                assertTrue(plan.endRotation - start >= 7 * 360)
            }
        }
    }

    @Test fun spinMovesForwardAndSlowsDown() {
        val plan = SpinPlan(5, 10, 122.0, 7, 0.1)
        val samples = (0..100).map { plan.rotationAt(it / 100.0) }
        val speeds = samples.zipWithNext { a, b -> b - a }
        assertEquals(plan.startRotation, samples.first())
        assertEquals(plan.endRotation, samples.last())
        assertTrue(speeds.all { it >= 0 })
        assertTrue(speeds.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test fun additionsAndParticipationSurviveRestart() {
        val storage = MemoryStorage()
        val engine = FoodRunEngine(storage)
        engine.togglePerson(0)
        val added = assertNotNull(engine.addPerson("  Ahmed \n Ali ").person)
        assertEquals("Ahmed Ali", added.name)
        val restored = FoodRunEngine(storage)
        assertTrue(added in restored.activePeople)
        assertTrue(restored.activePeople.none { it.id == 0 })
        restored.togglePerson(added.id)
        assertTrue(FoodRunEngine(storage).activePeople.none { it.id == added.id })
        restored.includeEveryone()
        assertEquals(restored.people, restored.activePeople)
    }

    @Test fun invalidNamesDoNotChangeRoster() {
        val engine = FoodRunEngine(MemoryStorage())
        engine.addPerson("Ahmed Ali")
        val original = engine.people
        assertEquals(AddPersonError.EMPTY_NAME, engine.addPerson(" \n\t").error)
        assertEquals(AddPersonError.DUPLICATE_NAME, engine.addPerson(" kARiM ").error)
        assertEquals(AddPersonError.DUPLICATE_NAME, engine.addPerson("AHMED   ALI").error)
        assertEquals(AddPersonError.NAME_TOO_LONG, engine.addPerson("a".repeat(33)).error)
        assertEquals(original, engine.people)
    }

    @Test fun originalCrewAndLastParticipantCannotBeRemoved() {
        val engine = FoodRunEngine(MemoryStorage())
        val added = assertNotNull(engine.addPerson("Omar").person)
        assertFalse(engine.removeAddedPerson(0))
        FoodRunRules.defaultCrew.forEach { engine.togglePerson(it.id) }
        assertEquals(listOf(added), engine.activePeople)
        assertFalse(engine.togglePerson(added.id))
        assertFalse(engine.removeAddedPerson(added.id))
        engine.togglePerson(0)
        assertTrue(engine.removeAddedPerson(added.id))
        assertEquals(FoodRunRules.defaultCrew, engine.people)
    }

    @Test fun newPersonCanWinAndHistoryIsPersistedOnce() {
        val storage = MemoryStorage()
        val engine = FoodRunEngine(storage)
        val added = assertNotNull(engine.addPerson("Omar").person)
        FoodRunRules.defaultCrew.forEach { engine.togglePerson(it.id) }
        assertNotNull(engine.beginSpin(0.0))
        assertNull(engine.beginSpin(0.0))
        assertFalse(engine.togglePerson(0))
        assertFalse(engine.includeEveryone())
        assertFalse(engine.removeAddedPerson(added.id))
        assertEquals(AddPersonError.SPIN_IN_PROGRESS, engine.addPerson("Ali").error)
        assertEquals(added, engine.finishSpin(1000.0))
        assertNull(engine.finishSpin(1001.0))
        assertEquals(listOf(added), FoodRunEngine(storage).history.map { it.person })
    }

    @Test fun historyIsCappedAtThirtyAndOnlyEligiblePeopleWin() {
        val engine = FoodRunEngine(MemoryStorage())
        engine.togglePerson(0)
        repeat(100) { index ->
            assertNotNull(engine.beginSpin(0.0))
            val winner = assertNotNull(engine.finishSpin(index.toDouble()))
            assertTrue(winner in engine.activePeople)
            assertNotEquals(0, winner.id)
        }
        assertEquals(30, engine.history.size)
        assertEquals(99.0, engine.history.first().timeMillis)
    }

    @Test fun invalidSavedDataRecoversSafely() {
        val corrupted = FoodRunEngine(MemoryStorage("not json"))
        assertEquals(FoodRunRules.defaultCrew, corrupted.activePeople)
        val storage = MemoryStorage("""{"customPeople":[{"id":-1,"name":"Bad"},{"id":10,"name":"Omar"},{"id":10,"name":"Ali"},{"id":11,"name":"KARIM"}],"activeIds":[999]}""")
        val engine = FoodRunEngine(storage)
        assertEquals(FoodRunRules.defaultCrew + Person(10, "Omar"), engine.people)
        assertEquals(engine.people, engine.activePeople)
    }

    @Test fun hapticsPreferencePersists() {
        val storage = MemoryStorage()
        FoodRunEngine(storage).setHapticsEnabled(false)
        assertFalse(FoodRunEngine(storage).hapticsEnabled)
    }
}
