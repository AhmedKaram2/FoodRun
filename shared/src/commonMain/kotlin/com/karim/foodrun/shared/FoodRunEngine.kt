package com.karim.foodrun.shared

/** Compatibility bridge for earlier clients. Native screens use FoodRunController's state contract. */
class FoodRunEngine(storage: FoodRunStorage) {
    private val session = FoodRunSession(PreferencesFoodRunRepository(storage))
    val people: List<Person> get() = session.people
    val activePeople: List<Person> get() = session.activePeople
    val history: List<Pickup> get() = session.history
    val hapticsEnabled: Boolean get() = session.hapticsEnabled
    val isSpinning: Boolean get() = session.isSpinning
    fun addPerson(rawName: String): AddPersonResult = session.addPerson(rawName)
    fun togglePerson(id: Int): Boolean = session.togglePerson(id)
    fun includeEveryone(): Boolean = session.includeEveryone()
    fun removeAddedPerson(id: Int): Boolean = session.removeAddedPerson(id)
    fun setHapticsEnabled(enabled: Boolean) = session.setHapticsEnabled(enabled)
    fun beginSpin(startRotation: Double): SpinPlan? = session.beginSpin(startRotation)
    fun finishSpin(timeMillis: Double): Person? = session.finishSpin(timeMillis)
}
