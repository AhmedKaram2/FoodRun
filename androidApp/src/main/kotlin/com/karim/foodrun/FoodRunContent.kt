package com.karim.foodrun

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karim.foodrun.shared.FoodRunState
import com.karim.foodrun.shared.FoodRunText

@Composable
fun FoodRunContent(state: FoodRunState, rotation: Double, tick: Int, reduceMotion: Boolean, onSpin: () -> Unit, onCrew: () -> Unit, onHistory: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(FoodColors.Cream).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        val availableWheelHeight = maxHeight * 0.40f
        Column(Modifier.widthIn(max = FoodSize.MaxContentWidth).fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = FoodSpacing.Page).padding(top = FoodSpacing.XSmall, bottom = FoodSpacing.Page),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(FoodSize.Avatar).rotate(-7f).background(FoodColors.Orange, RoundedCornerShape(FoodRadius.Avatar)), contentAlignment = Alignment.Center) {
                        FoodBag(Modifier.size(FoodSize.IconLarge), FoodColors.White)
                    }
                    Spacer(Modifier.width(FoodSpacing.Small))
                    Text(FoodRunText.brand, style = FoodType.Brand, color = FoodColors.Ink)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onHistory, enabled = !state.isSpinning,
                        modifier = Modifier.size(FoodSize.TouchTarget).background(FoodColors.Card, CircleShape)
                            .border(FoodSize.Border, FoodColors.Line, CircleShape)
                            .semantics { contentDescription = FoodRunText.historyAccessibility }.testTag("historyButton")) {
                        HistoryIcon(Modifier.size(FoodSize.IconMedium))
                    }
                }
                Spacer(Modifier.height(FoodSpacing.Large))
                Text(FoodRunText.headlineFirst, style = FoodType.Hero, color = FoodColors.Ink, textAlign = TextAlign.Center)
                Text(FoodRunText.headlineSecond, style = FoodType.Hero, color = FoodColors.Orange, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FoodSpacing.Small))
                Text(FoodRunText.tagline, style = FoodType.Body, color = FoodColors.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FoodSpacing.Hero))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val wheelWidth = minOf(maxWidth - FoodSpacing.Large, availableWheelHeight, FoodSize.Wheel)
                    WheelView(state, rotation, tick, reduceMotion, Modifier.width(wheelWidth))
                    Text("✦", color = FoodColors.Orange, fontSize = 29.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-5).dp).clearAndSetSemantics {})
                    Text("✦", color = FoodColors.Sage, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = (-14).dp).clearAndSetSemantics {})
                }
                Spacer(Modifier.height(FoodSpacing.Field))
                Row(Modifier.background(FoodColors.Card, CircleShape).border(FoodSize.Border, FoodColors.Line, CircleShape).padding(horizontal = FoodSpacing.Content, vertical = FoodSpacing.XSmall), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (state.isSpinning) FoodColors.Orange else FoodColors.Success, CircleShape))
                    Spacer(Modifier.width(FoodSpacing.XSmall))
                    Text(state.statusLabel, style = FoodType.Status, color = FoodColors.Muted, modifier = Modifier.testTag("wheelStatus"))
                }
                Spacer(Modifier.height(FoodSpacing.Large))
                CrewCard(state, onCrew)
            }
            FoodDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = FoodSpacing.Page, vertical = FoodSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(state.spinButtonLabel, if (state.isSpinning) Icons.Default.Star else Icons.Default.Refresh,
                    Modifier.testTag("spinButton"), enabled = state.canSpin, onClick = onSpin)
                Spacer(Modifier.height(FoodSpacing.Medium))
                Text(state.missionLabel, style = FoodType.Caption, color = FoodColors.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}
