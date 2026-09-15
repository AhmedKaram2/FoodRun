package com.karim.foodrun

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.karim.foodrun.shared.*

@Composable
fun CrewSheet(state: FoodRunState, dispatch: (FoodRunEvent) -> Unit, reduceMotion: Boolean = false) {
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(state.crewItems.size) }
    LaunchedEffect(state.crewItems.size) {
        if (state.crewItems.size > previousCount) {
            if (reduceMotion) listState.scrollToItem(state.crewItems.size)
            else listState.animateScrollToItem(state.crewItems.size)
        }
        previousCount = state.crewItems.size
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = FoodSpacing.Page), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { dispatch(FoodRunEvent.Dismiss) }) { Text(FoodRunText.done, style = FoodType.RoundedBody) }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = FoodSpacing.Page, end = FoodSpacing.Page, bottom = FoodSpacing.Page)) {
            item {
                Text(FoodRunText.crewTitle, style = FoodType.Title, color = FoodColors.Ink)
                Spacer(Modifier.height(FoodSpacing.XSmall))
                Text(FoodRunText.crewDescription, style = FoodType.Body, color = FoodColors.Muted)
                Spacer(Modifier.height(FoodSpacing.Section))
                SecondaryButton(FoodRunText.addPerson, Icons.Default.Add, Modifier.testTag("addPersonButton"), enabled = state.canManageCrew) { dispatch(FoodRunEvent.OpenAddPerson) }
                Spacer(Modifier.height(FoodSpacing.Section))
            }
            itemsIndexed(state.crewItems, key = { _, item -> item.id }) { index, item ->
                val top = if (index == 0) FoodSpacing.Page else FoodSpacing.None
                val bottom = if (index == state.crewItems.lastIndex) FoodSpacing.Page else FoodSpacing.None
                Column(Modifier.clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)).background(FoodColors.Card)) {
                    CrewPersonRow(item, dispatch)
                    if (index < state.crewItems.lastIndex) FoodDivider(Modifier.padding(start = FoodSpacing.Empty))
                }
            }
            item {
                Spacer(Modifier.height(FoodSpacing.Large))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(FoodRunText.keepOneFriend, style = FoodType.SmallCaption, color = FoodColors.Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { dispatch(FoodRunEvent.IncludeEveryone) }) { Text(FoodRunText.selectAll, style = FoodType.RoundedCaption) }
                }
                Spacer(Modifier.height(FoodSpacing.Large))
                FoodCard(radius = FoodRadius.Toggle) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = FoodSpacing.Field, vertical = FoodSpacing.XSmall), verticalAlignment = Alignment.CenterVertically) {
                        Text(FoodRunText.hapticTicks, style = FoodType.RoundedBody, color = FoodColors.Ink, modifier = Modifier.weight(1f))
                        Switch(state.hapticsEnabled, { dispatch(FoodRunEvent.SetHaptics(it)) }, modifier = Modifier.testTag("hapticsToggle").semantics { contentDescription = FoodRunText.hapticTicks })
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewPersonRow(item: CrewPersonItem, dispatch: (FoodRunEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = FoodSpacing.XSmall), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)
            .clickable(enabled = item.canToggle, role = Role.Checkbox) { dispatch(FoodRunEvent.TogglePerson(item.id)) }
            .padding(horizontal = FoodSpacing.XSmall, vertical = FoodSpacing.Small).testTag("person_${item.id}")
            .semantics(mergeDescendants = true) { contentDescription = item.name; stateDescription = item.participationAccessibility }, verticalAlignment = Alignment.CenterVertically) {
            FoodAvatar(item.person)
            Spacer(Modifier.width(FoodSpacing.Content))
            Text(item.name, style = FoodType.Person, color = FoodColors.Ink, modifier = Modifier.weight(1f))
            Text(item.participationLabel, style = FoodType.SmallCaption, color = FoodColors.Muted)
            Spacer(Modifier.width(FoodSpacing.Small))
            FoodSelectionIndicator(item.included)
        }
        if (item.isCustom) IconButton(onClick = { dispatch(FoodRunEvent.RemoveAddedPerson(item.id)) }, enabled = item.canRemove, modifier = Modifier.testTag("removePerson_${item.id}")) {
            Icon(Icons.Default.Delete, item.removeAccessibility, tint = FoodColors.Muted, modifier = Modifier.size(FoodSize.IconSmall))
        }
    }
}
