package com.karim.foodrun

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (wheel.style == "names") {
        val ar = LocalLayoutDirection.current == LayoutDirection.Rtl
        val index = wheel.round.runningNameIndex(now)
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFFFF4DF), Color(0xFFFFE2CA))), RoundedCornerShape(28.dp)).padding(24.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if(ar) "الأسماء المتحركة" else "Running names"
                stateDescription = if(spinning) { if(ar) "جارٍ الاختيار" else "Choosing" } else wheel.winner
            }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if(spinning) "✦" else "✓", fontSize = 36.sp, color = Color(0xFFDC632D))
            Spacer(Modifier.height(18.dp))
            if (!reduceMotion && spinning) Text(wheel.names[(index + wheel.names.size - 1) % wheel.names.size], color = Color(0x6661371C), fontSize = 21.sp, maxLines = 1)
            AnimatedContent(targetState = if(reduceMotion && spinning) { if(ar) "جارٍ الاختيار…" else "Choosing…" } else wheel.names[index],
                transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) }, label = "running names") { name ->
                Text(name, Modifier.fillMaxWidth().padding(vertical = 16.dp).background(Color.White, RoundedCornerShape(18.dp)).padding(22.dp),
                    fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF61371C), textAlign = TextAlign.Center)
            }
            if (!reduceMotion && spinning) Text(wheel.names[(index + 1) % wheel.names.size], color = Color(0x6661371C), fontSize = 21.sp, maxLines = 1)
            Spacer(Modifier.height(18.dp))
            Text(if(spinning) { if(ar) "من سيطلب؟" else "Who will order?" } else { if(ar) "مسؤول الطلب" else "Selected to order" })
        }
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics(mergeDescendants = true) {
            contentDescription = FoodRunText.wheelAccessibility
            stateDescription = FoodRunText.wheelState(spinning, if (spinning) null else wheel.winner, people.size)
        },
        contentAlignment = Alignment.Center,
    ) {
        WheelCanvas(people = people, rotation = rotation, weights = wheel.round.weights)
        WheelCenter()
        WheelPointer(angle = 0f, modifier = Modifier.align(Alignment.TopCenter))
    }
}
