package com.karim.foodrun

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/** Controlled input: value, error and submission permission come from shared state. */
@Composable
fun FoodTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorModifier: Modifier = Modifier,
    multiline: Boolean = false,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Words,
        autoCorrectEnabled = false,
        imeAction = ImeAction.Done,
    ),
    onSubmit: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 6 else 1,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            enabled = enabled,
            isError = error != null,
            textStyle = FoodType.Input,
            shape = RoundedCornerShape(FoodRadius.Input),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FoodColors.Ink,
                unfocusedTextColor = FoodColors.Ink,
                focusedContainerColor = FoodColors.White,
                unfocusedContainerColor = FoodColors.White,
                focusedBorderColor = FoodColors.Orange,
                unfocusedBorderColor = FoodColors.Line,
                cursorColor = FoodColors.Orange,
                errorBorderColor = FoodColors.Error,
                errorTextColor = FoodColors.Ink,
                errorContainerColor = FoodColors.White,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = FoodSize.Input),
        )
        error?.let {
            Text(
                text = it,
                color = FoodColors.Error,
                style = FoodType.Caption,
                modifier = errorModifier
                    .padding(top = FoodSpacing.XSmall)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
