package com.karim.foodrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karim.foodrun.shared.FoodRunText
import com.karim.foodrun.shared.PickupHistoryItem

@Composable
fun HistorySheet(history: List<PickupHistoryItem>, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = FoodSpacing.Page), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(FoodRunText.done, style = FoodType.RoundedBody) }
        }
        LazyColumn(contentPadding = PaddingValues(start = FoodSpacing.Page, end = FoodSpacing.Page, bottom = FoodSpacing.Page), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
            item {
                Text(FoodRunText.historyTitle, style = FoodType.Title, color = FoodColors.Ink)
                Spacer(Modifier.height(FoodSpacing.XSmall))
                Text(FoodRunText.historyDescription, style = FoodType.Body, color = FoodColors.Muted)
                Spacer(Modifier.height(FoodSpacing.Medium))
            }
            if (history.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = FoodSpacing.Empty), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(112.dp).background(FoodColors.OrangeWash, CircleShape), contentAlignment = Alignment.Center) { FoodBag(Modifier.size(FoodSize.Input), FoodColors.Orange) }
                    Spacer(Modifier.height(FoodSpacing.XLarge))
                    Text(FoodRunText.historyEmptyTitle, style = FoodType.SectionTitle, color = FoodColors.Ink)
                    Spacer(Modifier.height(FoodSpacing.Medium))
                    Text(FoodRunText.historyEmptyDescription, style = FoodType.Body, color = FoodColors.Muted, textAlign = TextAlign.Center)
                }
            }
            items(history, key = { it.id }) { pickup ->
                FoodCard {
                    Row(Modifier.fillMaxWidth().padding(FoodSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                        FoodAvatar(pickup.person, size = FoodSize.AvatarLarge)
                        Spacer(Modifier.width(FoodSpacing.Content))
                        Column(Modifier.weight(1f)) {
                            Text(pickup.name, style = FoodType.Person, color = FoodColors.Ink)
                            Text(pickup.dateLabel, style = FoodType.Caption, color = FoodColors.Muted)
                        }
                        FoodBag(Modifier.size(FoodSize.IconLarge), FoodColors.Orange)
                    }
                }
            }
            if (history.isNotEmpty()) item { Text(FoodRunText.historyFooter, style = FoodType.Caption, color = FoodColors.Muted, modifier = Modifier.padding(top = FoodSpacing.Medium)) }
        }
    }
}
