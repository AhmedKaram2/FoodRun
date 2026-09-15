package com.karim.foodrun.shared

import kotlin.test.*

class FoodRunBehaviorBaselineTest {
    private class MemoryStorage(var content: String? = null) : FoodRunStorage {
        var writes = 0
        override fun read() = content
        override fun write(value: String) { content = value; writes++ }
    }

    @Test fun normalizedUnicodeNamesRespectCharacterBoundary() {
        val engine = FoodRunEngine(MemoryStorage())
        assertNotNull(engine.addPerson("🙂".repeat(32)).person)
        assertEquals(AddPersonError.NAME_TOO_LONG, engine.addPerson("🙂".repeat(33)).error)
        assertNotNull(engine.addPerson("  أحمد   علي  ").person)
        assertEquals(AddPersonError.DUPLICATE_NAME, engine.addPerson("أحمد علي").error)
    }

    @Test fun rejectedRosterActionsDoNotPersistOrChangeSelection() {
        val storage = MemoryStorage()
        val engine = FoodRunEngine(storage)
        val original = engine.activePeople
        assertFalse(engine.togglePerson(-1))
        assertFalse(engine.removeAddedPerson(99))
        assertFalse(engine.removeAddedPerson(0))
        assertNull(engine.finishSpin(1000.0))
        assertEquals(AddPersonError.EMPTY_NAME, engine.addPerson("").error)
        assertEquals(0, storage.writes)
        assertEquals(original, engine.activePeople)
    }

    @Test fun removedNamesCanBeAddedAgainAndOldHistoryStaysIntact() {
        val storage = MemoryStorage()
        val engine = FoodRunEngine(storage)
        val person = assertNotNull(engine.addPerson("Omar").person)
        FoodRunRules.defaultCrew.forEach { engine.togglePerson(it.id) }
        engine.beginSpin(0.0)
        engine.finishSpin(1000.0)
        engine.includeEveryone()
        assertTrue(engine.removeAddedPerson(person.id))
        assertEquals(person, FoodRunEngine(storage).history.single().person)
        assertNotNull(engine.addPerson("Omar").person)
    }

    @Test fun restoreNormalizesAndDeduplicatesRosterAndHistory() {
        val storage = MemoryStorage("""{"customPeople":[{"id":10,"name":"  Omar  Ali  "},{"id":11,"name":""},{"id":12,"name":"omar ali"},{"id":2147483647,"name":"Too large"}],"activeIds":[10,10,-1,999],"history":[{"id":"a","person":{"id":10,"name":"Omar Ali"},"timeMillis":1000.0},{"id":"a","person":{"id":0,"name":"Karim"},"timeMillis":2000.0},{"id":"b","person":{"id":-1,"name":"Bad"},"timeMillis":3000.0}],"hapticsEnabled":false}""")
        val engine = FoodRunEngine(storage)
        assertEquals(listOf(Person(10, "Omar Ali")), engine.activePeople)
        assertEquals(11, engine.people.size)
        assertEquals(listOf("a"), engine.history.map { it.id })
        assertFalse(engine.hapticsEnabled)
        assertEquals(0, storage.writes)
    }

    @Test fun pendingSpinIsTransientAndHapticsMayChangeDuringAnimation() {
        val storage = MemoryStorage()
        val engine = FoodRunEngine(storage)
        engine.beginSpin(0.0)
        engine.setHapticsEnabled(false)
        assertTrue(engine.isSpinning)
        val restarted = FoodRunEngine(storage)
        assertFalse(restarted.isSpinning)
        assertTrue(restarted.history.isEmpty())
        assertFalse(restarted.hapticsEnabled)
        assertNotNull(restarted.beginSpin(0.0))
    }

    @Test fun animationClampsProgressAndRejectsInvalidPlans() {
        val plan = SpinPlan(0, 1, -120.0, 6, 0.0)
        assertEquals(plan.startRotation, plan.rotationAt(-1.0))
        assertEquals(plan.endRotation, plan.rotationAt(2.0))
        assertEquals(0, SpinPlan.indexAtPointer(1.0, 0))
        assertFailsWith<IllegalArgumentException> { SpinPlan(0, 0, 0.0, 6, 0.0) }
        assertFailsWith<IllegalArgumentException> { SpinPlan(2, 2, 0.0, 6, 0.0) }
        assertFailsWith<IllegalArgumentException> { SpinPlan(0, 1, Double.NaN, 6, 0.0) }
        assertFailsWith<IllegalArgumentException> { SpinPlan(0, 1, 0.0, -1, 0.0) }
    }
}
