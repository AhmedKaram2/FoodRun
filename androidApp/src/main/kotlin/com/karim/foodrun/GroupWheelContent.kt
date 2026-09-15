package com.karim.foodrun

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.karim.foodrun.shared.FoodRunText
import com.karim.foodrun.shared.Person
import com.karim.foodrun.shared.orders.GroupWheel
import kotlinx.coroutines.delay

@Composable
internal fun GroupWheelContent(wheel: GroupWheel) {
    val context = LocalContext.current
    val reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val people = remember(wheel.names) { wheel.names.mapIndexed { index, name -> Person(index, name) } }
    var now by remember(wheel.round.id) { mutableLongStateOf(System.currentTimeMillis() + wheel.serverOffset) }
    LaunchedEffect(wheel.round.id, wheel.serverOffset, reduceMotion) {
        val startTime = System.currentTimeMillis() + wheel.serverOffset
        val monotonicStart = SystemClock.elapsedRealtime()
        now = startTime
        if (reduceMotion) {
            delay((wheel.round.endAt - now).coerceAtLeast(0))
            now = wheel.round.endAt
        } else while (now < wheel.round.endAt) {
            withFrameMillis { now = startTime + SystemClock.elapsedRealtime() - monotonicStart }
        }
    }
    val spinning = now < wheel.round.endAt
    val rotation = if (reduceMotion && spinning) 0.0 else wheel.round.rotation(now)
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics(mergeDescendants = true) {
            contentDescription = FoodRunText.wheelAccessibility
            stateDescription = FoodRunText.wheelState(spinning, if (spinning) null else wheel.winner, people.size)
        },
        contentAlignment = Alignment.Center,
    ) {
        WheelCanvas(people = people, rotation = rotation)
        WheelCenter()
        WheelPointer(angle = 0f, modifier = Modifier.align(Alignment.TopCenter))
    }
}
