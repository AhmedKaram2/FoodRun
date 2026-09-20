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
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.semantics.contentDescription
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
            Text(FoodRunText.brand, style = FoodType.Brand, color = FoodColors.Ink, modifier = Modifier.weight(1f))
            GroupLanguagePicker(state, controller)
        }
        Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
            Text(GroupText.localized(GroupText.homeTitle, state.rtl), style = FoodType.Hero, color = FoodColors.Ink, modifier = Modifier.semantics { heading() })
            Text(GroupText.localized(GroupText.homeSubtitle, state.rtl), style = FoodType.Body, color = FoodColors.Muted)
        }
        FoodCard(fill = FoodColors.Hero, radius = FoodSpacing.Page) {
            Column(Modifier.padding(FoodSpacing.Page), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Large)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(GroupText.localized(GroupText.groupEyebrow, state.rtl), style = FoodType.RoundedCaption, color = FoodColors.OnHero, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Person, null, tint = FoodColors.OnHero)
                }
                Text(GroupText.localized(GroupText.groupTitle, state.rtl), style = FoodType.DialogTitle, color = FoodColors.White)
                Text(GroupText.localized(GroupText.groupDescription, state.rtl), style = FoodType.Body, color = FoodColors.OnHero)
                state.buttons.filter { it.action in listOf(GroupAction.CREATE, GroupAction.JOIN) }.forEach {
                    GroupActionButton(it, state.busy, controller)
                }
            }
        }
        state.cards.filter { it.id.startsWith("invitation:") }.forEach { GroupCardContent(it, state.busy, controller) }
        val rooms = state.cards.filter { it.id.startsWith("session:") }
        if (rooms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
                GroupSectionHeading(GroupText.localized(GroupText.savedRooms, state.rtl), rooms.size)
                rooms.forEach { GroupCardContent(it, state.busy, controller) }
            }
        }
        val dashboard = state.cards.filter { it.id.startsWith("dashboard:") }
        if (dashboard.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
                GroupSectionHeading(if (state.rtl) "المحفظة والطلبات" else "Wallet & orders")
                dashboard.forEach { GroupCardContent(it, state.busy, controller) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
            GroupSectionHeading(GroupText.localized(GroupText.explore, state.rtl))
            state.buttons.filter { it.action in listOf(GroupAction.QUICK_SPIN, GroupAction.OPEN_LIBRARY) }.forEach { button ->
                FoodCard(bordered = true, modifier = Modifier.testTag("action:${button.action.name}:${button.value}")) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy && button.enabled, role = Role.Button) {
                        controller.dispatch(button.action, button.value)
                    }.padding(FoodSpacing.Large), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Content)) {
                        GroupIcon(groupActionIcon(button), accented = button.action == GroupAction.QUICK_SPIN)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XXSmall)) {
                            Text(button.title, style = FoodType.Person, color = FoodColors.Ink)
                            Text(if (button.action == GroupAction.QUICK_SPIN) GroupText.localized(GroupText.quickDescription, state.rtl) else GroupText.localized(GroupText.libraryDescription, state.rtl), style = FoodType.Caption, color = FoodColors.Muted)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FoodColors.Muted, modifier = Modifier.size(FoodSize.IconSmall))
                    }
                }
            }
        }
        state.buttons.filter { it.action !in listOf(GroupAction.CREATE, GroupAction.JOIN, GroupAction.QUICK_SPIN, GroupAction.OPEN_LIBRARY, GroupAction.SET_LANGUAGE) }.forEach {
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
        GroupAction.USE_INTERNET -> Icons.Default.Share
        GroupAction.QUICK_SPIN, GroupAction.PREPARE_SPIN, GroupAction.REFRESH, GroupAction.RETRY -> Icons.Default.Refresh
        GroupAction.OPEN_LIBRARY, GroupAction.OPEN_ITEM, GroupAction.OPEN_RECEIPTS -> Icons.Default.Menu
        GroupAction.OPEN_HISTORY -> Icons.Default.DateRange
        GroupAction.FAVORITE_ORDER, GroupAction.REMOVE_FAVORITE_ORDER -> Icons.Default.Favorite
        GroupAction.REUSE_ORDER -> Icons.Default.ShoppingCart
        GroupAction.SHARE_ROOM, GroupAction.SHARE_RECEIPT, GroupAction.EXPORT_MENU, GroupAction.SHARE_ORDER_WHATSAPP -> Icons.Default.Share
        GroupAction.READY, GroupAction.CONFIRM_QUOTE, GroupAction.SAVE_RESTAURANT, GroupAction.ACCEPT_DUTY,
        GroupAction.SELECT_TAX_TREATMENT -> Icons.Default.Check
        GroupAction.EDIT_RESTAURANT, GroupAction.EDIT_ROOM_RESTAURANT -> Icons.Default.Edit
        else -> Icons.AutoMirrored.Filled.ArrowForward
    }
}


@Composable
internal fun GroupLanguagePicker(state: GroupState, controller: GroupController) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = !state.busy,
            modifier = Modifier.heightIn(min = FoodSize.TouchTarget).testTag("languagePicker")
                .semantics { contentDescription = "Language / اللغة" }) {
            androidx.compose.foundation.Canvas(Modifier.size(FoodSize.IconSmall)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width / 14)
                drawCircle(FoodColors.Orange, radius = size.width * .44f, style = stroke)
                drawOval(FoodColors.Orange, topLeft = androidx.compose.ui.geometry.Offset(size.width * .28f, size.height * .06f), size = androidx.compose.ui.geometry.Size(size.width * .44f, size.height * .88f), style = stroke)
                drawLine(FoodColors.Orange, androidx.compose.ui.geometry.Offset(size.width * .06f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * .94f, size.height / 2), strokeWidth = stroke.width)
            }
            Text(if(state.rtl) "العربية" else "English", modifier = Modifier.padding(horizontal = FoodSpacing.XSmall))
            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(FoodSize.IconSmall))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("en" to "English", "ar" to "العربية").forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false; controller.dispatch(GroupAction.SET_LANGUAGE, code)
                }, trailingIcon = { if ((code == "ar") == state.rtl) Icon(Icons.Default.Check, null) })
            }
        }
    }
}
