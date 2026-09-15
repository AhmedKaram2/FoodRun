package com.karim.foodrun.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface FoodRunRepository {
    fun load(): SavedState
    fun save(state: SavedState)
}

internal class FoodRunPersistenceException(cause: Exception) : Exception(cause)

/** Owns the versioned preferences schema; neither presentation adapter parses shared data. */
internal class PreferencesFoodRunRepository(private val storage: FoodRunStorage) : FoodRunRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun save(state: SavedState) {
        try {
            storage.write(json.encodeToString(state))
        } catch (error: Exception) {
            throw FoodRunPersistenceException(error)
        }
    }

    override fun load(): SavedState {
        val decoded = storage.read()?.let {
            runCatching { json.decodeFromString<SavedState>(it) }.getOrNull()
        } ?: SavedState()
        val restoredPeople = FoodRunRules.defaultCrew.toMutableList()
        for (person in decoded.customPeople) {
            val name = FoodRunRules.normalizedName(person.name)
            if (person.id < FoodRunRules.defaultCrew.size || person.id == Int.MAX_VALUE ||
                name.isEmpty() || name.codePointCount() > FoodRunRules.maxNameLength) continue
            if (restoredPeople.any { it.id == person.id || it.name.equals(name, ignoreCase = true) }) continue
            restoredPeople += person.copy(name = name)
        }
        val validIds = restoredPeople.map { it.id }.toSet()
        return decoded.copy(
            customPeople = restoredPeople.drop(FoodRunRules.defaultCrew.size),
            activeIds = decoded.activeIds.distinct().filter { it in validIds }.ifEmpty { validIds.toList() },
            history = decoded.history.filter {
                it.timeMillis.isFinite() && it.person.id >= 0 && it.person.name.isNotBlank()
            }.distinctBy { it.id }.take(FoodRunRules.historyLimit),
        )
    }
}
