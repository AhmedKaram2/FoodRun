package com.karim.foodrun

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.karim.foodrun.shared.FoodRunState
import com.karim.foodrun.shared.FoodRunText

/** Native frame animation surrounds the immutable shared roster and status. */
@Composable
fun WheelView(
    state: FoodRunState,
    rotation: Double,
    tick: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val pointerAngle by animateFloatAsState(
        targetValue = if (state.isSpinning && tick % 2 == 0 && !reduceMotion) FoodWheelTokens.PointerDeflection else 0f,
        animationSpec = spring(
            dampingRatio = FoodWheelTokens.PointerDamping,
            stiffness = FoodWheelTokens.PointerStiffness,
        ),
        label = "pointer tick",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) {
                contentDescription = FoodRunText.wheelAccessibility
                stateDescription = state.wheelAccessibilityState
            },
        contentAlignment = Alignment.Center,
    ) {
        WheelCanvas(
            people = state.activePeople,
            rotation = rotation,
        )
        WheelCenter()
        WheelPointer(
            angle = pointerAngle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun WheelCenter() {
    Column(
        modifier = Modifier
            .size(FoodWheelTokens.Hub)
            .shadow(FoodWheelTokens.HubShadow, CircleShape)
            .background(FoodColors.Cream, CircleShape)
            .border(FoodSize.AvatarBorder, FoodColors.Ink, CircleShape)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FoodBag(Modifier.size(FoodWheelTokens.HubIcon))
        Text(
            text = FoodRunText.wheelCenter,
            style = FoodWheelTokens.HubType,
            color = FoodColors.Ink,
        )
    }
}

@Composable
internal fun WheelPointer(
    angle: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .offset(y = -FoodWheelTokens.PointerOffset)
            .size(FoodWheelTokens.PointerWidth, FoodWheelTokens.PointerHeight)
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .clearAndSetSemantics {},
    ) {
        val small = FoodSpacing.XXSmall.toPx()
        val rounding = FoodWheelTokens.PointerRounding.toPx()
        val tip = FoodWheelTokens.PointerTip.toPx()
        val path = Path().apply {
            moveTo(small, 0f)
            lineTo(size.width - small, 0f)
            quadraticBezierTo(size.width, 0f, size.width, rounding)
            lineTo(size.width / 2 + tip, size.height - tip)
            quadraticBezierTo(size.width / 2, size.height + FoodSpacing.Tiny.toPx(), size.width / 2 - tip, size.height - tip)
            lineTo(0f, rounding)
            quadraticBezierTo(0f, 0f, small, 0f)
            close()
        }
        drawPath(path, FoodColors.Orange)
        drawPath(
            path = path,
            color = FoodColors.Ink,
            style = Stroke(FoodSize.AvatarBorder.toPx()),
        )
    }
}
