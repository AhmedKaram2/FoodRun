package com.karim.foodrun.shared

import kotlinx.serialization.Serializable

@Serializable
data class Person(val id: Int, val name: String) {
    val initial: String get() = name.take(if (name.length > 1 && name[0].isHighSurrogate() && name[1].isLowSurrogate()) 2 else 1)
    val wheelLabel: String get() {
        if (name.codePointCount() <= 12) return name
        var end = 0
        repeat(11) {
            end += if (name[end].isHighSurrogate() && end + 1 < name.length && name[end + 1].isLowSurrogate()) 2 else 1
        }
        return name.take(end) + "…"
    }
}

@Serializable
data class Pickup(val id: String, val person: Person, val timeMillis: Double)

object FoodRunRules {
    val defaultCrew = listOf("Karim", "Karam", "Hassan", "Mersal", "Baraa", "Fayed", "Ayman", "Rayan", "Gaber", "Fakhr")
        .mapIndexed { index, name -> Person(index, name) }
    const val maxNameLength = 32
    const val historyLimit = 30
    const val spinDurationSeconds = 5.4

    fun normalizedName(name: String): String = name.trim().split(Regex("\\s+")).joinToString(" ")
}

enum class AddPersonError {
    EMPTY_NAME,
    DUPLICATE_NAME,
    NAME_TOO_LONG,
    SPIN_IN_PROGRESS;

    val message: String get() = FoodRunText.addPersonError(this)
}

data class AddPersonResult(val person: Person?, val error: AddPersonError?)

/** Each platform supplies its local preference store. The schema and rules live in common code. */
interface FoodRunStorage {
    fun read(): String?
    fun write(value: String)
}

@Serializable
internal data class SavedState(
    val version: Int = 1,
    val customPeople: List<Person> = emptyList(),
    val activeIds: List<Int> = FoodRunRules.defaultCrew.map { it.id },
    val history: List<Pickup> = emptyList(),
    val hapticsEnabled: Boolean = true,
)
