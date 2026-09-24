package com.karim.foodrun

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

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
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    fun translated(value: String) = com.karim.foodrun.shared.orders.GroupText.localized(value, rtl)
    if (field.key in listOf(GroupFieldKey.PHOTO, GroupFieldKey.RECEIPT_PHOTO, GroupFieldKey.ADMIN_PHOTO)) {
        val receipt = field.key == GroupFieldKey.RECEIPT_PHOTO
        val context = LocalContext.current
        var photoError by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) scope.launch {
                try {
                photoError = ""
                val value = withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readNBytes(10 * 1024 * 1024 + 1) }
                    require(bytes.size <= 10 * 1024 * 1024) { "Choose a photo under 10 MB." }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo could not be opened." }
                    val decode = BitmapFactory.Options().apply { while(maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2800) inSampleSize *= 2 }
                    val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)) { "This photo could not be opened." }
                    val side = minOf(source.width, source.height)
                    val square = if(receipt) source else Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
                    val scale = if(receipt) minOf(1.0, 1400.0 / maxOf(source.width, source.height)) else 256.0 / side
                    val scaled = Bitmap.createScaledBitmap(square, maxOf(1, (square.width * scale).toInt()), maxOf(1, (square.height * scale).toInt()), true)
                    val output = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 78, output)
                    if(receipt && output.size() > 440_000) { output.reset(); scaled.compress(Bitmap.CompressFormat.JPEG, 50, output) }
                    require(output.size() <= if(receipt) 440_000 else 130_000) { "Choose a smaller photo." }
                    "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
                controller.update(field.key, value)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { photoError = failure.message ?: "This photo could not be opened. Choose another image." }
            }
        }
        val preview = remember(field.value) {
            field.value.takeIf { it.startsWith("data:image/") }?.substringAfter("base64,")?.let { encoded ->
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }
        FoodCard(bordered = true) {
            Column(Modifier.fillMaxWidth().padding(FoodSpacing.Large), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally) {
                if (photoError.isNotEmpty()) Text(translated(photoError), color = FoodColors.Orange, modifier = Modifier.semantics { error(translated(photoError)) })
                if (preview != null) Image(preview.asImageBitmap(), field.label, if(receipt) Modifier.fillMaxWidth().heightIn(max = 340.dp) else Modifier.size(FoodSize.AvatarLarge).clip(CircleShape), contentScale = if(receipt) ContentScale.Fit else ContentScale.Crop)
                Text(if(receipt) field.label else translated(if (field.value.isBlank()) "Add a profile photo" else "Profile photo selected"), style = FoodType.Input, color = FoodColors.Ink)
                PrimaryButton(translated(if (field.value.isBlank()) "Choose from gallery" else "Change photo"), null, enabled = !busy) { launcher.launch("image/*") }
                if (field.value.isNotBlank()) SecondaryButton(translated("Remove photo"), null, enabled = !busy, destructive = true) { controller.update(field.key, "") }
            }
        }
    } else if (field.key == GroupFieldKey.QUANTITY) {
        val quantity = (field.value.toIntOrNull() ?: 1).coerceIn(1, 99)
        Row(Modifier.fillMaxWidth().testTag(field.key.name), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
            Text(field.label, modifier = Modifier.weight(1f), style = FoodType.Input)
            TextButton(enabled = !busy && quantity > 1, onClick = { controller.update(field.key, (quantity - 1).toString()) }) { Text("−") }
            Text(quantity.toString(), style = FoodType.Input)
            TextButton(enabled = !busy && quantity < 99, onClick = { controller.update(field.key, (quantity + 1).toString()) }) { Text("+") }
        }
    } else if (field.choices.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Small)) {
            Text(field.label, style = FoodType.Input)
            Box {
                androidx.compose.material3.OutlinedButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag(field.key.name)) {
                    Text((field.choices.firstOrNull { it.value == field.value }?.label ?: field.value) + " ▾")
                }
                androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.choices.forEach { choice ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(choice.label) }, onClick = { expanded = false; controller.update(field.key, choice.value) })
                    }
                }
            }
        }
    } else if (field.toggle) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FoodColors.Card, RoundedCornerShape(FoodRadius.Card))
                .border(FoodSize.Border, FoodColors.Line, RoundedCornerShape(FoodRadius.Card)).toggleable(
                value = field.value == "true",
                enabled = !busy,
                role = Role.Switch,
                onValueChange = { controller.update(field.key, it.toString()) },
            ).padding(FoodSpacing.Large).testTag(field.key.name),
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
            GroupFieldKey.QUANTITY, GroupFieldKey.ROOM_CODE -> KeyboardType.Number
            GroupFieldKey.JSON_MENU, GroupFieldKey.FINGERPRINT, GroupFieldKey.ACCOUNT_IDENTIFIER -> KeyboardType.Ascii
            // Decimal input does not request a signed number pad. Keep minus accessible for bill reductions.
            GroupFieldKey.BILL_ADJUSTMENT -> KeyboardType.Ascii
            GroupFieldKey.PAYMENT_TOTAL, GroupFieldKey.PAYMENT_SHARE, GroupFieldKey.PAYMENT_RECEIVED, GroupFieldKey.AMOUNT, GroupFieldKey.MENU_ITEM_PRICE, GroupFieldKey.DELIVERY_FEE, GroupFieldKey.SERVICE_FEE,
            GroupFieldKey.DISCOUNT, GroupFieldKey.TAX_RATE, GroupFieldKey.MINIMUM_ORDER -> KeyboardType.Decimal
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
internal fun GroupActionButton(button: GroupButton, busy: Boolean, controller: GroupController, prominent: Boolean = button.primary) {
    val modifier = Modifier.testTag("action:${button.action.name}:${button.value}")
    val focus = LocalFocusManager.current
    val click = {
        focus.clearFocus()
        controller.dispatch(button.action, button.value)
    }
    if (prominent) PrimaryButton(
        text = button.title,
        icon = groupActionIcon(button),
        modifier = modifier,
        enabled = !busy && button.enabled,
        onClick = click,
    ) else SecondaryButton(
        destructive = button.destructive,
        text = button.title,
        icon = groupActionIcon(button),
        modifier = modifier,
        enabled = !busy && button.enabled,
        onClick = click,
    )
}

@Composable
internal fun GroupCardContent(card: GroupCard, busy: Boolean, controller: GroupController) {
    FoodCard(bordered = true) {
        Column(
            modifier = Modifier.padding(FoodSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(FoodSpacing.Content),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FoodSpacing.Medium), verticalAlignment = Alignment.Top) {
                if (card.id.startsWith("member:") || card.id.startsWith("invite:")) {
                    Box(Modifier.size(FoodSize.Avatar).background(FoodColors.SuccessWash, CircleShape), contentAlignment = Alignment.Center) {
                        Text(card.title.take(1), style = FoodType.Avatar, color = FoodColors.Success)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
                    Text(text = card.title, style = FoodType.Person, color = FoodColors.Ink)
                    if (card.badge.isNotEmpty()) Text(text = card.badge, style = FoodType.Status, color = FoodColors.Orange,
                        modifier = Modifier.background(FoodColors.AccentWash, RoundedCornerShape(FoodRadius.Card))
                            .padding(horizontal = FoodSpacing.XSmall, vertical = FoodSpacing.XXSmall))
                }
            }
            if(card.image.isNotEmpty()) {
                val receipt = remember(card.image) { runCatching { Base64.decode(card.image.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                receipt?.let { Image(it.asImageBitmap(), card.title, Modifier.fillMaxWidth().heightIn(max = 600.dp), contentScale = ContentScale.Fit) }
            }
            if (card.detail.isNotEmpty()) SelectionContainer {
                Text(text = card.detail, style = FoodType.Body, color = FoodColors.Muted)
            }
            if (card.buttons.size > 1) FoodDivider()
            card.buttons.forEach { GroupActionButton(it, busy, controller) }
        }
    }
}
