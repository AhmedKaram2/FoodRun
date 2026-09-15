package com.karim.foodrun.shared

enum class FoodRunDestination { MAIN, CREW, ADD_PERSON, HISTORY, WINNER }

data class CrewPersonItem(
    val person: Person,
    val included: Boolean,
    val isCustom: Boolean,
    val canToggle: Boolean,
    val canRemove: Boolean,
) {
    val id: Int get() = person.id
    val name: String get() = person.name
    val initial: String get() = person.initial
    val participationLabel: String get() = FoodRunText.participation(included)
    val participationAccessibility: String get() = FoodRunText.participationAccessibility(included)
    val removeAccessibility: String get() = FoodRunText.removePersonAccessibility(person.name)
}

data class PickupHistoryItem(val pickup: Pickup, val dateLabel: String) {
    val id: String get() = pickup.id
    val person: Person get() = pickup.person
    val name: String get() = person.name
    val initial: String get() = person.initial
}

/** A platform supplies only zone rules. Calendar conversion and display formatting stay shared. */
interface FoodRunTimeZone {
    fun offsetSecondsAt(timeMillis: Double): Int
}

class UtcFoodRunTimeZone : FoodRunTimeZone {
    override fun offsetSecondsAt(timeMillis: Double): Int = 0
}

class FoodRunObservation internal constructor(private var onCancel: (() -> Unit)?) {
    fun cancel() { onCancel?.invoke(); onCancel = null }
}
