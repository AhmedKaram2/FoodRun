package com.karim.foodrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karim.foodrun.shared.Person

/** Food Run's native rendering tokens. No application rules belong in this layer. */
object FoodColors {
    val Cream = Color(0xFFFFF7ED)
    val Ink = Color(0xFF1F2937)
    val Muted = Color(0xFF626874)
    val BrandCoral = Color(0xFFFF684A)
    val Orange = Color(0xFFCF442B)
    val AccentText = Color(0xFFB83D27)
    val FreshGreen = Color(0xFF22C55E)
    val Yellow = Color(0xFFFBBF24)
    val OrangeLight = Orange
    val Hero = Color(0xFFFFEBDD)
    val OnHero = Ink
    val SuccessWash = Color(0xFFE8F5E9)
    val AccentWash = Color(0xFFFFEBDD)
    val Line = Color(0xFFE9DDD1)
    val White = Color.White
    val Clear = Color.Transparent
    val WheelRim = Color(0xFFFFFFFF)
    val Sage = Color(0xFF9BAF72)
    val Success = Color(0xFF167347)
    val Error = Color(0xFFB53617)
    val Card = White
    val SubtleCard = White
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
    val Page = 16.dp
    val Hero = 26.dp
    val XXLarge = 32.dp
    val Empty = 60.dp
}

object FoodRadius {
    val Avatar = 13.dp
    val Input = 16.dp
    val Add = 18.dp
    val Toggle = 20.dp
    val Card = 16.dp
    val Dialog = 30.dp
    val Sheet = 32.dp
}

object FoodSize {
    val PrimaryButton = 52.dp
    val Input = 50.dp
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
    val ErrorBanner = 160.dp
    val Wheel = 355.dp
    val ButtonShadow = 1.dp
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
    private val English = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold),
    )
    private val Arabic = FontFamily(Font(R.font.noto_sans_arabic))
    val Rounded: FontFamily
        @Composable get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Arabic else English
    private fun style(font: FontFamily, size: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = (size * 1.5).sp, fontWeight = weight)
    val Brand: TextStyle @Composable get() = style(Rounded, 15, FontWeight.Bold)
    val Hero: TextStyle @Composable get() = style(Rounded, 30, FontWeight.Bold)
    val Title: TextStyle @Composable get() = style(Rounded, 26, FontWeight.Bold)
    val DialogTitle: TextStyle @Composable get() = style(Rounded, 23, FontWeight.Bold)
    val SectionTitle: TextStyle @Composable get() = style(Rounded, 20, FontWeight.Bold)
    val Person: TextStyle @Composable get() = style(Rounded, 17, FontWeight.SemiBold)
    val Button: TextStyle @Composable get() = style(Rounded, 16, FontWeight.Bold)
    val RoundedBody: TextStyle @Composable get() = style(Rounded, 14, FontWeight.Medium)
    val RoundedCaption: TextStyle @Composable get() = style(Rounded, 12, FontWeight.SemiBold)
    val Body: TextStyle @Composable get() = style(Rounded, 14)
    val Input: TextStyle @Composable get() = style(Rounded, 16)
    val Caption: TextStyle @Composable get() = style(Rounded, 12)
    val SmallCaption: TextStyle @Composable get() = style(Rounded, 11)
    val Status: TextStyle @Composable get() = style(Rounded, 12, FontWeight.SemiBold)
    val Avatar: TextStyle @Composable get() = style(Rounded, 16, FontWeight.Bold)
    val AvatarSmall: TextStyle @Composable get() = style(Rounded, 13, FontWeight.Bold)
    val AvatarLarge: TextStyle @Composable get() = style(Rounded, 20, FontWeight.Bold)
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
