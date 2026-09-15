package com.karim.foodrun

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Measurements for the custom wheel and winner animation, separate from general controls. */
object FoodWheelTokens {
    val Hub = 76.dp
    val HubIcon = 30.dp
    val HubShadow = 3.dp
    val RimWidth = 13.dp
    val RimDotInset = 6.dp
    val SliceBorder = 1.2.dp
    val ShadowOffset = 11.dp
    val PointerOffset = 12.dp
    val PointerWidth = 30.dp
    val PointerHeight = 40.dp
    val PointerRounding = 7.dp
    val PointerTip = 3.dp
    val WinnerImage = 112.dp
    val WinnerIcon = 62.dp
    val WinnerInset = 28.dp
    val ConfettiRadius = 3.dp
    val ConfettiWidth = 5.dp
    val ConfettiHeight = 9.dp
    const val RimDotCount = 36
    const val PointerDeflection = -12f
    const val PointerDamping = 0.5f
    const val PointerStiffness = 1500f
    const val LabelRadiusFraction = 0.66f
    const val LabelSizeFraction = 0.046f
    const val WinnerEntranceScale = 0.8f
    const val WinnerStiffness = 180f
    const val ConfettiDurationMillis = 5_000
    const val ConfettiPieceCount = 85
    val HubType = TextStyle(
        fontFamily = FoodType.Rounded,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
    )
    val WinnerNameType = TextStyle(
        fontFamily = FoodType.Rounded,
        fontSize = 42.sp,
    )
    val WinnerEyebrowType = TextStyle(
        fontFamily = FoodType.Rounded,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )
}
