import SwiftUI
import IosComponents
import FoodRunShared

struct GroupFieldContent: View {
    let field: GroupField
    let enabled: Bool
    let onChange: (String) -> Void

    var body: some View {
        if field.toggle {
            Toggle(field.label, isOn: Binding(
                get: { field.value == "true" },
                set: { onChange($0 ? "true" : "false") }
            ))
            .font(FoodTypography.setting)
            .foregroundStyle(FoodTheme.ink)
            .padding(FoodSpacing.s16)
            .foodCard(showsBorder: true)
            .disabled(!enabled)
            .accessibilityIdentifier(field.key.name)
        } else {
            VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                Text(field.label).font(FoodTypography.setting).foregroundStyle(FoodTheme.ink)
                input
            }
        }
    }

    private var input: some View {
        BaseTextInputField(
            label: "",
            placeholder: field.label,
            keyboardType: keyboard,
            imeAction: .done,
            capitalization: .none,
            backgroundColor: FoodTheme.white,
            borderColor: FoodTheme.line,
            font: FoodTypography.input,
            singleLine: !field.multiline,
            enabled: enabled,
            readOnly: false,
            textColor: FoodTheme.ink,
            maxLines: field.multiline ? 6 : 1,
            minHeight: FoodSpacing.s0,
            error: nil,
            endIconImage: nil,
            startIconImage: nil,
            iconSize: FoodSpacing.s18,
            iconTintColor: FoodTheme.muted,
            shape: RoundedRectangle(cornerRadius: FoodRadius.input),
            allowDigitsOnly: false,
            text: field.value,
            maxLen: field.key == .jsonMenu ? 2 * 1_024 * 1_024 : 4_000,
            onValueChange: onChange,
            contentPadding: FoodSpacing.s16,
            borderWidth: FoodBorder.thin,
            errorColor: FoodTheme.orange,
            errorFont: FoodTypography.captionButton,
            inputIdentifier: field.key.name
        )
        .privacySensitive(field.secret)
    }

    private var keyboard: UIKeyboardType {
        switch field.key {
        case .hubUrl, .pairingLink: .URL
        case .phone: .phonePad
        case .amount, .deliveryFee, .serviceFee, .discount, .menuItemPrice: .decimalPad
        case .quantity, .roomCode: .numberPad
        default: .default
        }
    }
}
