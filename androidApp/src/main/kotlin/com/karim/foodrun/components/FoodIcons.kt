package com.karim.foodrun

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

// Normalized vector coordinates are artwork, not application layout values.
@Composable
fun FoodBag(
    modifier: Modifier = Modifier,
    tint: Color = FoodColors.Ink,
) {
    Canvas(modifier) {
        val unit = size.minDimension / 100
        val bag = Path().apply {
            moveTo(12 * unit, 29 * unit)
            lineTo(48 * unit, 26 * unit)
            lineTo(54 * unit, 80 * unit)
            quadraticBezierTo(51 * unit, 88 * unit, 44 * unit, 88 * unit)
            lineTo(15 * unit, 91 * unit)
            quadraticBezierTo(7 * unit, 91 * unit, 7 * unit, 83 * unit)
            close()
        }
        drawPath(bag, tint)
        drawPath(
            path = Path().apply {
                moveTo(19 * unit, 29 * unit)
                lineTo(22 * unit, 12 * unit)
                lineTo(42 * unit, 10 * unit)
                lineTo(48 * unit, 28 * unit)
            },
            color = tint,
            style = Stroke(7 * unit),
        )
        drawPath(
            path = Path().apply {
                moveTo(55 * unit, 42 * unit)
                lineTo(91 * unit, 42 * unit)
                lineTo(86 * unit, 87 * unit)
                quadraticBezierTo(73 * unit, 94 * unit, 60 * unit, 87 * unit)
                close()
            },
            color = tint,
        )
        drawPath(
            path = Path().apply {
                moveTo(73 * unit, 45 * unit)
                lineTo(76 * unit, 22 * unit)
                lineTo(93 * unit, 9 * unit)
            },
            color = tint,
            style = Stroke(
                width = 6 * unit,
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
fun HistoryIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.width * 0.075f
        drawArc(
            color = FoodColors.Ink,
            startAngle = -125f,
            sweepAngle = 315f,
            useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
            size = size * 0.76f,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = FoodColors.Ink,
            start = center,
            end = Offset(center.x, size.height * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = FoodColors.Ink,
            start = center,
            end = Offset(size.width * 0.65f, size.height * 0.59f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.10f)
                lineTo(size.width * 0.08f, size.height * 0.35f)
                lineTo(size.width * 0.31f, size.height * 0.35f)
            },
            color = FoodColors.Ink,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
