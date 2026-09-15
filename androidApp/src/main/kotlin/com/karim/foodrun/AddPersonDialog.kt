package com.karim.foodrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karim.foodrun.shared.FoodRunState
import com.karim.foodrun.shared.FoodRunText

@Composable
fun AddPersonDialog(state: FoodRunState, onNameChanged: (String) -> Unit, onSubmit: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.imePadding().padding(FoodSpacing.Page).widthIn(max = FoodSize.MaxDialogWidth).fillMaxWidth()
            .background(FoodColors.Cream, RoundedCornerShape(FoodRadius.Dialog)).verticalScroll(rememberScrollState()).padding(FoodSpacing.Page)) {
            Text(FoodRunText.addPersonTitle, style = FoodType.DialogTitle, color = FoodColors.Ink)
            Spacer(Modifier.height(FoodSpacing.XSmall))
            Text(FoodRunText.addPersonDescription, style = FoodType.Body, color = FoodColors.Muted)
            Spacer(Modifier.height(FoodSpacing.Section))
            FoodTextField(state.nameDraft, onNameChanged, FoodRunText.nameLabel, state.nameErrorMessage,
                Modifier.fillMaxWidth().focusRequester(focus).testTag("newPersonNameField"),
                enabled = !state.isSpinning, errorModifier = Modifier.testTag("addPersonError"), onSubmit = onSubmit)
            Spacer(Modifier.height(FoodSpacing.XLarge))
            PrimaryButton(FoodRunText.addPersonAction, Icons.Default.Add, Modifier.testTag("confirmAddPersonButton"), enabled = state.canSubmitName, onClick = onSubmit)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = FoodSpacing.XSmall)) { Text(FoodRunText.cancel, color = FoodColors.Muted) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
