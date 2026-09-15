import SwiftUI
import IosComponents

struct SecondaryButton: View {
    let title: String
    let symbol: String
    var isDisabled = false
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        FilledPrimaryButton(
            text: title,
            textStyle: FoodTypography.button,
            foregroundColor: FoodTheme.orange,
            startIconImage: Image(systemName: symbol),
            isEnabled: !isDisabled,
            shape: RoundedRectangle(cornerRadius: FoodRadius.secondaryButton),
            appearance: FilledButtonAppearance(
                background: AnyShapeStyle(FoodTheme.orange.opacity(0.09)),
                contentPadding: FoodSpacing.s16,
                iconSpacing: FoodSpacing.s8,
                iconFont: FoodTypography.button,
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
