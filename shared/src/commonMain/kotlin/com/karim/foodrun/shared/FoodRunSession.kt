package com.karim.foodrun.shared

import kotlin.random.Random

/** Synchronous domain operations, confined by the platform adapter to its UI thread. */
internal class FoodRunSession(
    private val repository: FoodRunRepository,
    private val random: Random = Random.Default,
) {
    private var saved = repository.load()
    private var pending: Person? = null

    val people: List<Person> get() = FoodRunRules.defaultCrew + saved.customPeople
    val activePeople: List<Person> get() = people.filter { it.id in saved.activeIds }
    val history: List<Pickup> get() = saved.history
    val hapticsEnabled: Boolean get() = saved.hapticsEnabled
    val isSpinning: Boolean get() = pending != null

    fun addPerson(rawName: String): AddPersonResult {
        if (isSpinning) return AddPersonResult(null, AddPersonError.SPIN_IN_PROGRESS)
        val name = FoodRunRules.normalizedName(rawName)
        val error = when {
            name.isEmpty() -> AddPersonError.EMPTY_NAME
            name.codePointCount() > FoodRunRules.maxNameLength -> AddPersonError.NAME_TOO_LONG
            people.any { it.name.equals(name, ignoreCase = true) } -> AddPersonError.DUPLICATE_NAME
            else -> null
        }
        if (error != null) return AddPersonResult(null, error)
        val usedIds = people.map { it.id }.toSet()
        var nextId = FoodRunRules.defaultCrew.size
        while (nextId in usedIds) nextId++
        val person = Person(nextId, name)
        save(saved.copy(customPeople = saved.customPeople + person, activeIds = saved.activeIds + person.id))
        return AddPersonResult(person, null)
    }

    fun togglePerson(id: Int): Boolean {
        if (isSpinning || people.none { it.id == id }) return false
        val active = saved.activeIds
        if (id in active && active.size == 1) return false
        save(saved.copy(activeIds = if (id in active) active - id else active + id))
        return true
    }

    fun includeEveryone(): Boolean {
        if (isSpinning) return false
        save(saved.copy(activeIds = people.map { it.id }))
        return true
    }

    fun removeAddedPerson(id: Int): Boolean {
        if (isSpinning || saved.customPeople.none { it.id == id }) return false
        if (id in saved.activeIds && saved.activeIds.size == 1) return false
        save(saved.copy(customPeople = saved.customPeople.filterNot { it.id == id }, activeIds = saved.activeIds - id))
        return true
    }

    fun setHapticsEnabled(enabled: Boolean) { save(saved.copy(hapticsEnabled = enabled)) }

    fun beginSpin(startRotation: Double): SpinPlan? {
        if (isSpinning || activePeople.isEmpty() || !startRotation.isFinite()) return null
        val eligible = activePeople
        val index = random.nextInt(eligible.size)
        val plan = SpinPlan(index, eligible.size, startRotation, random.nextInt(6, 9), random.nextDouble(-0.28, 0.28))
        pending = eligible[index]
        return plan
    }

    fun finishSpin(timeMillis: Double): Person? {
        val person = pending ?: return null
        if (!timeMillis.isFinite()) return null
        val pickup = Pickup("${timeMillis.toLong()}_${random.nextLong()}", person, timeMillis)
        save(saved.copy(history = (listOf(pickup) + saved.history).take(FoodRunRules.historyLimit)))
        pending = null
        return person
    }

    fun cancelSpin() { pending = null }

    private fun save(value: SavedState) {
        repository.save(value)
        saved = value
    }
}

internal fun String.codePointCount(): Int = indices.count { index ->
    !this[index].isLowSurrogate() || index == 0 || !this[index - 1].isHighSurrogate()
}
