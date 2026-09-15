package com.karim.foodrun

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/** Android counterpart of the themed IosComponents filled primary button. */
@Composable
fun PrimaryButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FoodActionButton(
        text = text,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        primary = true,
        onClick = onClick,
    )
}

@Composable
fun SecondaryButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FoodActionButton(
        text = text,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        primary = false,
        onClick = onClick,
    )
}

@Composable
private fun FoodActionButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) FoodMotion.PressScale else 1f,
        animationSpec = spring(dampingRatio = FoodMotion.PressDamping),
        label = "button press",
    )
    val shape = RoundedCornerShape(if (primary) FoodRadius.Card else FoodRadius.Add)
    val foreground = if (primary) FoodColors.White else FoodColors.Orange
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else FoodMotion.DisabledOpacity
            }
            .fillMaxWidth()
            .heightIn(min = if (primary) FoodSize.PrimaryButton else FoodSize.TouchTarget)
            .shadow(
                elevation = if (primary) FoodSize.ButtonShadow else FoodSpacing.None,
                shape = shape,
                ambientColor = FoodColors.Orange.copy(alpha = 0.22f),
                spotColor = FoodColors.Orange.copy(alpha = 0.22f),
            )
            .background(
                brush = Brush.verticalGradient(
                    if (primary) listOf(FoodColors.OrangeLight, FoodColors.Orange)
                    else listOf(FoodColors.OrangeWash, FoodColors.OrangeWash),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = FoodSpacing.Medium,
                vertical = if (primary) FoodSpacing.Field else FoodSpacing.Large,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(FoodSize.IconMedium),
            )
            Spacer(Modifier.width(FoodSpacing.Small))
        }
        Text(
            text = text,
            color = foreground,
            style = FoodType.Button,
        )
    }
}
