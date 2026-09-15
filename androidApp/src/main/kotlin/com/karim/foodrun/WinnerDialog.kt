package com.karim.foodrun

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karim.foodrun.shared.FoodRunText
import com.karim.foodrun.shared.Person

@Composable
fun WinnerDialog(
    person: Person,
    reduceMotion: Boolean,
    onDismiss: () -> Unit,
) {
    val scale = remember(person.id) {
        Animatable(if (reduceMotion) 1f else FoodWheelTokens.WinnerEntranceScale)
    }
    LaunchedEffect(person.id, reduceMotion) {
        if (reduceMotion) scale.snapTo(1f)
        else scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = FoodMotion.PressDamping,
                stiffness = FoodWheelTokens.WinnerStiffness,
            ),
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (!reduceMotion) Confetti(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .padding(FoodSpacing.Page)
                    .widthIn(max = FoodSize.MaxDialogWidth)
                    .fillMaxWidth()
                    .scale(scale.value)
                    .background(FoodColors.Cream, RoundedCornerShape(FoodRadius.Sheet))
                    .verticalScroll(rememberScrollState())
                    .padding(FoodWheelTokens.WinnerInset)
                    .testTag("winnerCard"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = FoodRunText.winnerEyebrow,
                    style = FoodWheelTokens.WinnerEyebrowType,
                    color = FoodColors.Orange,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(FoodSpacing.Page))
                Box(
                    modifier = Modifier
                        .size(FoodWheelTokens.WinnerImage)
                        .background(FoodColors.person(person).copy(alpha = 0.48f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FoodBag(
                        modifier = Modifier.size(FoodWheelTokens.WinnerIcon),
                        tint = FoodColors.Ink,
                    )
                }
                Spacer(Modifier.height(FoodSpacing.Page))
                Text(
                    text = FoodRunText.winnerIntroduction,
                    style = FoodType.RoundedBody,
                    color = FoodColors.Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(FoodSpacing.XXSmall))
                Text(
                    text = FoodRunText.winnerTitle(person.name),
                    style = FoodWheelTokens.WinnerNameType,
                    color = FoodColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("winnerName").semantics { heading() },
                )
                Spacer(Modifier.height(FoodSpacing.Medium))
                Text(
                    text = FoodRunText.winnerDescription,
                    style = FoodType.Body,
                    color = FoodColors.Muted,
                    textAlign = TextAlign.Center,
                )
                FoodDivider(Modifier.padding(vertical = FoodSpacing.Page))
                PrimaryButton(
                    text = FoodRunText.winnerAction,
                    icon = Icons.Default.Check,
                    modifier = Modifier.testTag("winnerDoneButton"),
                    onClick = onDismiss,
                )
                ShareWinnerButton(person)
            }
        }
    }
}

@Composable
private fun ShareWinnerButton(person: Person) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, FoodRunText.winnerShare(person.name))
            }
            context.startActivity(Intent.createChooser(share, FoodRunText.shareWithCrew))
        },
        modifier = Modifier.padding(top = FoodSpacing.XSmall),
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(FoodSize.IconSmall),
            tint = FoodColors.Muted,
        )
        Spacer(Modifier.width(FoodSpacing.XSmall))
        Text(
            text = FoodRunText.shareWithCrew,
            color = FoodColors.Muted,
            style = FoodType.RoundedCaption,
        )
    }
}
