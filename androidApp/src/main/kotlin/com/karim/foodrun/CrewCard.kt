package com.karim.foodrun

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.karim.foodrun.shared.FoodRunState
import com.karim.foodrun.shared.FoodRunText

@Composable
fun CrewCard(state: FoodRunState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(FoodColors.SubtleCard, RoundedCornerShape(FoodRadius.Card))
        .border(FoodSize.Border, FoodColors.Line, RoundedCornerShape(FoodRadius.Card))
        .clickable(enabled = state.canManageCrew, role = Role.Button, onClick = onClick)
        .padding(FoodSpacing.Large).testTag("crewButton")
        .semantics(mergeDescendants = true) { contentDescription = state.crewAccessibility }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((32 + maxOf(state.crewPreview.size - 1, 0) * 23).dp).height(FoodSize.AvatarSmall)) {
            state.crewPreview.forEachIndexed { index, person ->
                FoodAvatar(person, Modifier.offset(x = (index * 23).dp), size = FoodSize.AvatarSmall, circular = true, bordered = true)
            }
        }
        Spacer(Modifier.width(FoodSpacing.Medium))
        Column(Modifier.weight(1f)) {
            Text(FoodRunText.crewTitle, style = FoodType.RoundedBody, color = FoodColors.Ink)
            Text(state.crewSummary, style = FoodType.SmallCaption, color = FoodColors.Muted)
        }
        Text(FoodRunText.crewAction, style = FoodType.RoundedCaption, color = FoodColors.Orange)
        Text(" ›", style = FoodType.Person, color = FoodColors.Orange, modifier = Modifier.clearAndSetSemantics {})
    }
}
