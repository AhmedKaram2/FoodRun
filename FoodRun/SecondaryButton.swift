import SwiftUI
import IosComponents

struct SecondaryButton: View {
    let title: String
    let symbol: String
    var isDisabled = false
    var destructive = false
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        FilledPrimaryButton(
            text: title,
            textStyle: FoodTypography.button,
            foregroundColor: destructive ? FoodTheme.error : FoodTheme.ink,
            startIconImage: Image(systemName: symbol),
            isEnabled: !isDisabled,
            shape: RoundedRectangle(cornerRadius: FoodRadius.secondaryButton),
            appearance: FilledButtonAppearance(
                background: AnyShapeStyle(destructive ? FoodTheme.error.opacity(0.07) : FoodTheme.cream),
                contentPadding: FoodSpacing.s16,
                iconSpacing: FoodSpacing.s8,
                iconFont: FoodTypography.button,
                borderColor: FoodTheme.line,
                borderWidth: FoodBorder.thin,
                disabledOpacity: 0.7,
                pressedOpacity: 1,
                pressedScale: reduceMotion ? 1 : FoodMotion.pressedScale,
                animation: reduceMotion ? FoodMotion.reducedPress : FoodMotion.press
            ),
            onClick: action
        )
        .environmentObject(FoodTheme.components)
    }
}
