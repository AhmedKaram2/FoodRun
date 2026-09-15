import SwiftUI
import IosComponents

struct PrimaryButton: View {
    let title: String
    var symbol = "arrow.triangle.2.circlepath"
    var isDisabled = false
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        FilledPrimaryButton(
            text: title,
            textStyle: FoodTypography.button,
            startIconImage: Image(systemName: symbol),
            isEnabled: !isDisabled,
            shape: RoundedRectangle(cornerRadius: FoodRadius.button),
            appearance: FilledButtonAppearance(
                background: AnyShapeStyle(FoodTheme.orange),
                contentPadding: FoodSpacing.s18,
                iconSpacing: FoodSpacing.s12,
                iconFont: FoodTypography.buttonIcon,
                borderColor: FoodTheme.black.opacity(0.08),
                borderWidth: FoodBorder.thin,
                shadowColor: FoodTheme.orange.opacity(0.12),
                shadowRadius: FoodSpacing.s8,
                shadowY: FoodSpacing.s4,
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

struct PressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? FoodMotion.pressedScale : 1)
            .animation(
                reduceMotion ? FoodMotion.reducedPress : FoodMotion.press,
                value: configuration.isPressed
            )
    }
}
