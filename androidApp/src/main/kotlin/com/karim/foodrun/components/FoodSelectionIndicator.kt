package com.karim.foodrun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Decorative only: the containing row owns its checkbox semantics and event. */
@Composable
fun FoodSelectionIndicator(
    included: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(FoodSize.Selection)
            .background(
                color = if (included) FoodColors.Orange else FoodColors.Clear,
                shape = CircleShape,
            )
            .then(
                if (included) Modifier
                else Modifier.border(
                    width = FoodSize.SelectionBorder,
                    color = FoodColors.Line,
                    shape = CircleShape,
                ),
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (included) Canvas(Modifier.size(FoodSize.SelectionIcon)) {
            val start = Offset(
                x = size.width * 0.20f,
                y = size.height * 0.52f,
            )
            val middle = Offset(
                x = size.width * 0.42f,
                y = size.height * 0.72f,
            )
            val end = Offset(
                x = size.width * 0.81f,
                y = size.height * 0.28f,
            )
            drawLine(
                color = FoodColors.White,
                start = start,
                end = middle,
                strokeWidth = FoodSize.SelectionBorder.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = FoodColors.White,
                start = middle,
                end = end,
                strokeWidth = FoodSize.SelectionBorder.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
