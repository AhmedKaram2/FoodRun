package com.karim.foodrun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun FoodCard(
    modifier: Modifier = Modifier,
    fill: Color = FoodColors.Card,
    bordered: Boolean = false,
    radius: Dp = FoodRadius.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = fill,
        contentColor = FoodColors.Ink,
        border = if (bordered) BorderStroke(
            width = FoodSize.Border,
            color = FoodColors.Line,
        ) else null,
    ) {
        Column(content = content)
    }
}

/** Clips the first and last rows to the same grouped-list corners on both platforms. */
@Composable
fun FoodListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    FoodCard(
        modifier = modifier,
        radius = FoodSpacing.Page,
        content = content,
    )
}
