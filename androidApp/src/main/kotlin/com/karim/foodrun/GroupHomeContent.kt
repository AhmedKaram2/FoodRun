package com.karim.foodrun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.karim.foodrun.shared.FoodRunText
import com.karim.foodrun.shared.orders.*

@Composable
internal fun GroupHomeContent(state: GroupState, controller: GroupController) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Page)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Small)) {
            Box(Modifier.size(FoodSize.TouchTarget).background(FoodColors.AccentWash, RoundedCornerShape(FoodRadius.Avatar)), contentAlignment = Alignment.Center) {
                FoodBag(Modifier.size(FoodSize.IconLarge), FoodColors.Orange)
            }
            Text(FoodRunText.brand, style = FoodType.Brand, color = FoodColors.Ink, modifier = Modifier.weight(1f))
            Text("TOGETHER", style = FoodType.RoundedCaption, color = FoodColors.Success,
                modifier = Modifier.background(FoodColors.SuccessWash, RoundedCornerShape(FoodRadius.Card)).padding(FoodSpacing.XSmall))
        }
        Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
            Text(GroupText.homeTitle, style = FoodType.Hero, color = FoodColors.Ink, modifier = Modifier.semantics { heading() })
            Text(GroupText.homeSubtitle, style = FoodType.Body, color = FoodColors.Muted)
        }
        FoodCard(fill = FoodColors.Hero, radius = FoodSpacing.Page) {
            Column(Modifier.padding(FoodSpacing.Page), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Large)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(GroupText.groupEyebrow, style = FoodType.RoundedCaption, color = FoodColors.OnHero, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Person, null, tint = FoodColors.OnHero)
                }
                Text(GroupText.groupTitle, style = FoodType.DialogTitle, color = FoodColors.White)
                Text(GroupText.groupDescription, style = FoodType.Body, color = FoodColors.OnHero)
                state.buttons.filter { it.action in listOf(GroupAction.CREATE, GroupAction.JOIN) }.forEach {
                    GroupActionButton(it, state.busy, controller)
                }
            }
        }
        state.cards.filter { it.id.startsWith("invitation:") }.forEach { GroupCardContent(it, state.busy, controller) }
        val rooms = state.cards.filter { it.id.startsWith("session:") }
        if (rooms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
                GroupSectionHeading(GroupText.savedRooms, rooms.size)
                rooms.forEach { GroupCardContent(it, state.busy, controller) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
            GroupSectionHeading(GroupText.explore)
            state.buttons.filter { it.action in listOf(GroupAction.QUICK_SPIN, GroupAction.OPEN_LIBRARY) }.forEach { button ->
                FoodCard(bordered = true, modifier = Modifier.testTag("action:${button.action.name}:${button.value}")) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy && button.enabled, role = Role.Button) {
                        controller.dispatch(button.action, button.value)
                    }.padding(FoodSpacing.Large), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Content)) {
                        GroupIcon(groupActionIcon(button), accented = button.action == GroupAction.QUICK_SPIN)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XXSmall)) {
                            Text(button.title, style = FoodType.Person, color = FoodColors.Ink)
                            Text(if (button.action == GroupAction.QUICK_SPIN) GroupText.quickDescription else GroupText.libraryDescription, style = FoodType.Caption, color = FoodColors.Muted)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FoodColors.Muted, modifier = Modifier.size(FoodSize.IconSmall))
                    }
                }
            }
        }
        state.buttons.filter { it.action !in listOf(GroupAction.CREATE, GroupAction.JOIN, GroupAction.QUICK_SPIN, GroupAction.OPEN_LIBRARY) }.forEach {
            GroupActionButton(it, state.busy, controller)
        }
        state.cards.firstOrNull { it.id == "about" }?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Small)) {
                Icon(Icons.Default.Check, null, tint = FoodColors.Muted, modifier = Modifier.size(FoodSize.IconSmall))
                Text(it.detail, style = FoodType.Caption, color = FoodColors.Muted)
            }
        }
    }
}

@Composable
internal fun GroupIcon(icon: ImageVector, accented: Boolean = false) {
    Box(Modifier.size(FoodSize.TouchTarget).background(if (accented) FoodColors.AccentWash else FoodColors.SuccessWash, RoundedCornerShape(FoodRadius.Avatar)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (accented) FoodColors.Orange else FoodColors.Success, modifier = Modifier.size(FoodSize.IconMedium))
    }
}

@Composable
internal fun GroupSectionHeading(title: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(top = FoodSpacing.XSmall), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FoodType.Person, color = FoodColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
        count?.let { Text(it.toString(), style = FoodType.Status, color = FoodColors.Muted) }
    }
}

internal fun groupActionIcon(button: GroupButton): ImageVector = when {
    button.destructive -> Icons.Default.Delete
    else -> when (button.action) {
        GroupAction.CREATE, GroupAction.CREATE_ROOM, GroupAction.NEW_RESTAURANT, GroupAction.ADD_MENU_ITEM, GroupAction.ADD_CART_ITEM -> Icons.Default.Add
        GroupAction.JOIN, GroupAction.JOIN_ROOM, GroupAction.RESUME -> Icons.Default.Person
        GroupAction.QUICK_SPIN, GroupAction.PREPARE_SPIN, GroupAction.REFRESH, GroupAction.RETRY -> Icons.Default.Refresh
        GroupAction.OPEN_LIBRARY, GroupAction.OPEN_ITEM, GroupAction.OPEN_RECEIPTS -> Icons.Default.Menu
        GroupAction.OPEN_HISTORY -> Icons.Default.DateRange
        GroupAction.SHARE_ROOM, GroupAction.SHARE_RECEIPT, GroupAction.EXPORT_MENU -> Icons.Default.Share
        GroupAction.READY, GroupAction.CONFIRM_QUOTE, GroupAction.SAVE_RESTAURANT, GroupAction.ACCEPT_DUTY,
        GroupAction.SELECT_TAX_TREATMENT -> Icons.Default.Check
        GroupAction.EDIT_RESTAURANT, GroupAction.EDIT_ROOM_RESTAURANT -> Icons.Default.Edit
        else -> Icons.AutoMirrored.Filled.ArrowForward
    }
}
