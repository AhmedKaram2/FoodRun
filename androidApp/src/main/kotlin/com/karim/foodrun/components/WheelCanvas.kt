package com.karim.foodrun

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.karim.foodrun.shared.Person
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Custom wheel artwork: angular math and label fitting intentionally stay in the renderer. */
@Composable
internal fun WheelCanvas(
    people: List<Person>,
    rotation: Double,
    weights: List<Int> = emptyList(),
) {
    val context = LocalContext.current
    val paint = remember(context) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FoodColors.Ink.toArgb()
            typeface = ResourcesCompat.getFont(context, R.font.rounded_black)
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.width / 2 - FoodSpacing.Tiny.toPx()
        val innerRadius = radius - FoodWheelTokens.RimWidth.toPx()
        drawCircle(FoodColors.Orange.copy(alpha = 0.06f), radius + FoodSpacing.XSmall.toPx())
        drawCircle(
            color = FoodColors.Ink.copy(alpha = 0.1f),
            radius = radius + FoodWheelTokens.HubShadow.toPx(),
            center = center + Offset(0f, FoodWheelTokens.ShadowOffset.toPx()),
        )
        drawCircle(
            color = FoodColors.Ink,
            radius = radius,
            center = center + Offset(0f, FoodWheelTokens.PointerRounding.toPx()),
        )
        drawCircle(FoodColors.WheelRim, radius)
        drawCircle(
            color = FoodColors.Ink,
            radius = radius,
            style = Stroke(FoodSize.AvatarBorder.toPx()),
        )
        repeat(FoodWheelTokens.RimDotCount) { index ->
            val angle = index * PI / (FoodWheelTokens.RimDotCount / 2)
            val point = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius - FoodWheelTokens.RimDotInset.toPx())
            drawCircle(
                color = FoodColors.Ink.copy(alpha = if (index % 3 == 0) 0.6f else 0.2f),
                radius = FoodSize.SelectionBorder.toPx(),
                center = point,
            )
        }
        val count = people.size.coerceAtLeast(1)
        val shares = weights.takeIf { it.size == count } ?: List(count) { 1 }
        val total = shares.sum()
        rotate(rotation.toFloat()) {
            people.forEachIndexed { index, person ->
                val slice = shares[index] * 360f / total
                val middle = -90f + (shares.take(index).sum() + shares[index] / 2f - shares.first() / 2f) * 360f / total
                val origin = center - Offset(innerRadius, innerRadius)
                val wheelSize = Size(innerRadius * 2, innerRadius * 2)
                drawArc(
                    color = FoodColors.person(person),
                    startAngle = middle - slice / 2,
                    sweepAngle = slice,
                    useCenter = true,
                    topLeft = origin,
                    size = wheelSize,
                )
                drawArc(
                    color = FoodColors.Ink.copy(alpha = 0.65f),
                    startAngle = middle - slice / 2,
                    sweepAngle = slice,
                    useCenter = true,
                    topLeft = origin,
                    size = wheelSize,
                    style = Stroke(FoodWheelTokens.SliceBorder.toPx()),
                )
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.translate(center.x, center.y)
                canvas.rotate(middle)
                canvas.translate(innerRadius * FoodWheelTokens.LabelRadiusFraction, 0f)
                canvas.rotate(180f)
                val label = person.wheelLabel
                paint.textSize = size.width * FoodWheelTokens.LabelSizeFraction
                val availableHeight = innerRadius * FoodWheelTokens.LabelRadiusFraction * sin(min(slice, 180f) * PI / 360).toFloat() * 1.6f
                val scale = minOf(
                    1f,
                    innerRadius * 0.53f / paint.measureText(label).coerceAtLeast(1f),
                    availableHeight / paint.fontSpacing,
                )
                canvas.scale(scale, scale)
                canvas.drawText(label, 0f, -(paint.ascent() + paint.descent()) / 2, paint)
                canvas.restore()
            }
        }
        drawCircle(
            color = FoodColors.Ink,
            radius = innerRadius,
            style = Stroke(FoodSize.SelectionBorder.toPx()),
        )
    }
}
