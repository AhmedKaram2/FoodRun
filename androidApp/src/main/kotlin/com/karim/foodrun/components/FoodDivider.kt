package com.karim.foodrun

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun FoodDivider(
    modifier: Modifier = Modifier,
    color: Color = FoodColors.Line,
    thickness: Dp = FoodSize.Border,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
    )
}
