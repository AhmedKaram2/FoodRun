package com.karim.foodrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karim.foodrun.shared.Person

/** Food Run's native rendering tokens. No application rules belong in this layer. */
object FoodColors {
    val Cream = Color(0xFFFFF9EF)
    val Ink = Color(0xFF29251F)
    val Muted = Color(0xFF82796D)
    val Orange = Color(0xFFEA5B2A)
    val OrangeLight = Color(0xFFFF7D46)
    val Line = Color(0xFFEAE0D1)
    val White = Color.White
    val Clear = Color.Transparent
    val WheelRim = Color(0xFFFFFDF7)
    val Sage = Color(0xFF9BAF72)
    val Success = Color(0xFF6F9662)
    val Error = Color(0xFFB53617)
    val Card = White.copy(alpha = 0.80f)
    val SubtleCard = White.copy(alpha = 0.62f)
    val OrangeWash = Orange.copy(alpha = 0.09f)
    val Palette = listOf(
        Color(0xFFF49A79),
        Color(0xFFF5CB69),
        Color(0xFFBAD4AD),
        Color(0xFFB8CBEB),
        Color(0xFFCEBAE4),
        Color(0xFFF2B4BD),
        Color(0xFFEAB88A),
        Color(0xFFCADA85),
        Color(0xFF8DCBC3),
        Color(0xFFE7C896),
    )

    fun person(person: Person): Color = Palette[person.id.mod(Palette.size)]
}

object FoodSpacing {
    val None = 0.dp
    val Hairline = 1.dp
    val Tiny = 2.dp
    val XXSmall = 4.dp
    val XSmall = 8.dp
    val Small = 10.dp
    val Medium = 12.dp
    val Content = 14.dp
    val Large = 16.dp
    val Field = 18.dp
    val XLarge = 20.dp
    val Section = 22.dp
    val Page = 24.dp
    val Hero = 26.dp
    val XXLarge = 32.dp
    val Empty = 60.dp
}

object FoodRadius {
    val Avatar = 13.dp
    val Input = 16.dp
    val Add = 18.dp
    val Toggle = 20.dp
    val Card = 22.dp
    val Dialog = 30.dp
    val Sheet = 32.dp
}

object FoodSize {
    val PrimaryButton = 64.dp
    val Input = 58.dp
    val TouchTarget = 48.dp
    val IconSmall = 16.dp
    val IconMedium = 22.dp
    val IconLarge = 24.dp
    val AvatarSmall = 32.dp
    val Avatar = 38.dp
    val AvatarLarge = 48.dp
    val Selection = 23.dp
    val SelectionIcon = 17.dp
    val MaxContentWidth = 490.dp
    val MaxDialogWidth = 420.dp
    val Wheel = 355.dp
    val ButtonShadow = 12.dp
    val Border = 1.dp
    val SelectionBorder = 1.5.dp
    val AvatarBorder = 2.5.dp
}

object FoodMotion {
    const val PressScale = 0.96f
    const val PressDamping = 0.65f
    const val DisabledOpacity = 0.62f
}

object FoodType {
    val Rounded = FontFamily(Font(R.font.rounded_black))
    val Brand = TextStyle(
        fontFamily = Rounded,
        fontSize = 15.sp,
        letterSpacing = 2.sp,
    )
    val Hero = TextStyle(
        fontFamily = Rounded,
        fontSize = 37.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.4).sp,
    )
    val Title = TextStyle(
        fontFamily = Rounded,
        fontSize = 30.sp,
    )
    val DialogTitle = TextStyle(
        fontFamily = Rounded,
        fontSize = 25.sp,
    )
    val SectionTitle = TextStyle(
        fontFamily = Rounded,
        fontSize = 20.sp,
    )
    val Person = TextStyle(
        fontFamily = Rounded,
        fontSize = 17.sp,
    )
    val Button = TextStyle(
        fontFamily = Rounded,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
    )
    val RoundedBody = TextStyle(
        fontFamily = Rounded,
        fontSize = 14.sp,
    )
    val RoundedCaption = TextStyle(
        fontFamily = Rounded,
        fontSize = 12.sp,
    )
    val Body = TextStyle(fontSize = 14.sp)
    val Input = TextStyle(fontSize = 16.sp)
    val Caption = TextStyle(fontSize = 12.sp)
    val SmallCaption = TextStyle(fontSize = 11.sp)
    val Status = Caption.copy(fontWeight = FontWeight.SemiBold)
    val Avatar = TextStyle(
        fontFamily = Rounded,
        fontSize = 16.sp,
    )
    val AvatarSmall = TextStyle(
        fontFamily = Rounded,
        fontSize = 13.sp,
    )
    val AvatarLarge = TextStyle(
        fontFamily = Rounded,
        fontSize = 20.sp,
    )
}

@Composable
fun roundedFont(): FontFamily = FoodType.Rounded

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FoodColors.Orange,
            onPrimary = FoodColors.White,
            background = FoodColors.Cream,
            onBackground = FoodColors.Ink,
            surface = FoodColors.Cream,
            onSurface = FoodColors.Ink,
            onSurfaceVariant = FoodColors.Muted,
            outline = FoodColors.Line,
            error = FoodColors.Error,
        ),
        typography = Typography(
            bodyLarge = FoodType.Input,
            bodyMedium = FoodType.Body,
            labelLarge = FoodType.Button,
            titleLarge = FoodType.SectionTitle,
        ),
        content = content,
    )
}
