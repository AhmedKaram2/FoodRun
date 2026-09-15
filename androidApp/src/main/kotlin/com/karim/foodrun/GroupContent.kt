package com.karim.foodrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.karim.foodrun.shared.orders.GroupButton
import com.karim.foodrun.shared.orders.GroupCard
import com.karim.foodrun.shared.orders.GroupController
import com.karim.foodrun.shared.orders.GroupField
import com.karim.foodrun.shared.orders.GroupFieldKey

/** Kept outside the form's LazyColumn so a failed submit is visible at every scroll position. */
@Composable
internal fun GroupErrorBanner(message: String) {
    FoodCard(
        modifier = Modifier.padding(horizontal = FoodSpacing.Page, vertical = FoodSpacing.XSmall)
            .heightIn(max = FoodSize.ErrorBanner),
        fill = FoodColors.OrangeWash,
    ) {
        key(message) {
            Text(
                text = message,
                color = FoodColors.Error,
                style = FoodType.Body,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(FoodSpacing.Large).testTag("groupError").semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(message)
                    },
            )
        }
    }
}

@Composable
internal fun GroupFieldContent(field: GroupField, busy: Boolean, controller: GroupController) {
    if (field.toggle) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = field.value == "true",
                enabled = !busy,
                role = Role.Switch,
                onValueChange = { controller.update(field.key, it.toString()) },
            ).padding(vertical = FoodSpacing.XSmall).testTag(field.key.name),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Medium),
        ) {
            Text(text = field.label, modifier = Modifier.weight(1f), style = FoodType.Input, color = FoodColors.Ink)
            Switch(checked = field.value == "true", onCheckedChange = null, enabled = !busy)
        }
    } else {
        val focus = LocalFocusManager.current
        val keyboard = when (field.key) {
            GroupFieldKey.HUB_URL, GroupFieldKey.PAIRING_LINK -> KeyboardType.Uri
            GroupFieldKey.PHONE -> KeyboardType.Phone
            GroupFieldKey.QUANTITY -> KeyboardType.Number
            GroupFieldKey.MENU_ITEM_PRICE, GroupFieldKey.DELIVERY_FEE, GroupFieldKey.SERVICE_FEE,
            GroupFieldKey.DISCOUNT, GroupFieldKey.AMOUNT -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }
        FoodTextField(
            value = field.value,
            onValueChange = { controller.update(field.key, it) },
            label = field.label,
            error = null,
            enabled = !busy,
            multiline = field.multiline,
            secret = field.secret,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = keyboard,
                autoCorrectEnabled = false,
                imeAction = if (field.multiline) ImeAction.Default else ImeAction.Done,
            ),
            modifier = Modifier.testTag(field.key.name),
            onSubmit = { focus.clearFocus() },
        )
    }
}

@Composable
internal fun GroupActionButton(button: GroupButton, busy: Boolean, controller: GroupController) {
    val modifier = Modifier.testTag("action:${button.action.name}:${button.value}")
    val click = { controller.dispatch(button.action, button.value) }
    if (button.primary) PrimaryButton(
        text = button.title,
        icon = null,
        modifier = modifier,
        enabled = !busy && button.enabled,
        onClick = click,
    ) else SecondaryButton(
        text = button.title,
        icon = null,
        modifier = modifier,
        enabled = !busy && button.enabled,
        onClick = click,
    )
}

@Composable
internal fun GroupCardContent(card: GroupCard, busy: Boolean, controller: GroupController) {
    FoodCard {
        Column(
            modifier = Modifier.padding(FoodSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(FoodSpacing.Small),
        ) {
            Text(text = card.title, style = FoodType.SectionTitle, color = FoodColors.Ink)
            if (card.badge.isNotEmpty()) Text(text = card.badge, style = FoodType.Status, color = FoodColors.Orange)
            if (card.detail.isNotEmpty()) Text(text = card.detail, style = FoodType.Body, color = FoodColors.Muted)
            card.buttons.forEach { GroupActionButton(it, busy, controller) }
        }
    }
}
