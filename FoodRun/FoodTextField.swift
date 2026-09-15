import SwiftUI
import IosComponents

struct FoodTextField: View {
    let value: String
    let onValueChange: (String) -> Void
    let label: String
    var error: String?
    var enabled = true
    let onSubmit: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        BaseTextInputField(
            label: "",
            placeholder: label,
            keyboardType: .namePhonePad,
            imeAction: .done,
            capitalization: .words,
            backgroundColor: FoodTheme.white,
            borderColor: FoodTheme.line,
            font: FoodTypography.input,
            singleLine: true,
            enabled: enabled,
            readOnly: false,
            textColor: FoodTheme.ink,
            maxLines: 1,
            minHeight: FoodSpacing.s0,
            error: error,
            endIconImage: nil,
            startIconImage: nil,
            iconSize: FoodSpacing.s18,
            iconTintColor: FoodTheme.muted,
            shape: RoundedRectangle(cornerRadius: FoodRadius.input),
            allowDigitsOnly: false,
            text: value,
            maxLen: .max,
            onValueChange: onValueChange,
            contentPadding: FoodSpacing.s18,
            borderWidth: FoodBorder.thin,
            errorColor: FoodTheme.orange,
            errorFont: .caption,
            textContentType: .givenName,
            focus: $focused,
            inputIdentifier: "newPersonNameField",
            errorIdentifier: "addPersonError",
            onSubmit: onSubmit
        )
        .task { focused = true }
    }
}
