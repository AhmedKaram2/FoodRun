package com.karim.foodrun

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val color: Color,
    val circle: Boolean,
)

/** A five-second decorative burst; state is native motion only and never changes selection. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val pieces = remember {
        val random = Random(48)
        List(FoodWheelTokens.ConfettiPieceCount) { index ->
            ConfettiPiece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.3f,
                speed = 0.75f + random.nextFloat() * 0.55f,
                drift = random.nextFloat() * 100 - 50,
                color = FoodColors.Palette[index % FoodColors.Palette.size],
                circle = index % 3 == 0,
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FoodWheelTokens.ConfettiDurationMillis,
                easing = LinearEasing,
            ),
        )
    }
    if (progress.value < 1f) Canvas(modifier.clearAndSetSemantics {}) {
        val elapsed = progress.value
        pieces.forEachIndexed { index, piece ->
            val fall = (elapsed - piece.delay) * 1.7f * piece.speed
            if (fall in 0f..1.2f) {
                val x = piece.x * size.width + sin(fall * 8 + index) * piece.drift
                val y = fall * (size.height + 100) - 50
                val alpha = ((1 - elapsed) * 5).coerceIn(0f, 1f)
                rotate(
                    degrees = fall * 600 + index * 23f,
                    pivot = Offset(x, y),
                ) {
                    if (piece.circle) drawCircle(
                        color = piece.color.copy(alpha = alpha),
                        radius = FoodWheelTokens.ConfettiRadius.toPx(),
                        center = Offset(x, y),
                    )
                    else drawRect(
                        color = piece.color.copy(alpha = alpha),
                        topLeft = Offset(x, y),
                        size = Size(
                            width = FoodWheelTokens.ConfettiWidth.toPx(),
                            height = FoodWheelTokens.ConfettiHeight.toPx(),
                        ),
                    )
                }
            }
        }
    }
}
