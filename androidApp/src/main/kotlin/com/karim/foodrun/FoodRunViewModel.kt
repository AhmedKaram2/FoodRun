package com.karim.foodrun

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karim.foodrun.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Platform lifecycle, preferences and animation frames; application decisions live in KMP. */
class FoodRunViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("food_run", 0)
    private val controller = FoodRunController(object : FoodRunStorage {
        override fun read() = preferences.getString("state_v1", null)
        override fun write(value: String) { preferences.edit { putString("state_v1", value) } }
    }, object : FoodRunTimeZone {
        override fun offsetSecondsAt(timeMillis: Double) = TimeZone.getDefault().getOffset(timeMillis.toLong()) / 1_000
    })

    var state by mutableStateOf(controller.state)
        private set
    var rotation by mutableDoubleStateOf(0.0)
        private set
    var tick by mutableIntStateOf(0)
        private set
    private val observation = controller.observe { updated ->
        if (updated.wheelRevision != state.wheelRevision) rotation = 0.0
        state = updated
    }
    private var lastFeedbackPickupId: String? = null
    fun consumeWinnerFeedback(): String? {
        val latest = state.historyItems.firstOrNull()?.id ?: return null
        if (!state.showWinner || latest == lastFeedbackPickupId) return null
        lastFeedbackPickupId = latest
        return state.winnerAnnouncement
    }

    fun dispatch(event: FoodRunEvent) = controller.dispatch(event)
    fun dismiss() = controller.dismiss()
    fun openCrew() = controller.openCrew()
    fun openHistory() = controller.openHistory()
    fun openAddPerson() = controller.openAddPerson()
    fun updateNameDraft(value: String) = controller.updateNameDraft(value)
    fun submitName() = controller.submitName()
    fun toggle(person: Person) = controller.togglePerson(person.id)
    fun includeEveryone() = controller.includeEveryone()
    fun remove(person: Person) = controller.removeAddedPerson(person.id)
    fun setHaptics(enabled: Boolean) = controller.setHaptics(enabled)

    fun spin(reduceMotion: Boolean) {
        val plan = controller.beginSpin(rotation) ?: return
        val count = state.activePeople.size
        viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtimeNanos()
                val duration = if (reduceMotion) 0.45 else FoodRunRules.spinDurationSeconds
                var previousIndex = SpinPlan.indexAtPointer(plan.startRotation, count)
                while (true) {
                    val progress = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000_000.0 / duration).coerceAtMost(1.0)
                    if (!reduceMotion) {
                        rotation = plan.rotationAt(progress)
                        val current = SpinPlan.indexAtPointer(rotation, count)
                        if (current != previousIndex) tick++
                        previousIndex = current
                    }
                    if (progress >= 1) break
                    delay(16)
                }
                rotation = SpinPlan.normalized(plan.endRotation)
                controller.finishSpin(System.currentTimeMillis().toDouble())
            } finally {
                if (state.isSpinning) controller.cancelSpin()
            }
        }
    }

    override fun onCleared() {
        observation.cancel()
        controller.cancelSpin()
        super.onCleared()
    }
}
