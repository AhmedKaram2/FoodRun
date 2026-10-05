import SwiftUI

struct FoodCard: ViewModifier {
    var radius: CGFloat = FoodRadius.button
    var opacity = 1.0
    var showsBorder = false

    func body(content: Content) -> some View {
        content
            .background(
                FoodTheme.white.opacity(opacity),
                in: RoundedRectangle(cornerRadius: radius)
            )
            .overlay {
                if showsBorder {
                    RoundedRectangle(cornerRadius: radius)
                        .strokeBorder(FoodTheme.line, lineWidth: FoodBorder.thin)
                }
            }
    }
}

extension View {
    func foodCard(
        radius: CGFloat = FoodRadius.button,
        opacity: Double = 1,
        showsBorder: Bool = false
    ) -> some View {
        modifier(FoodCard(
            radius: radius,
            opacity: opacity,
            showsBorder: showsBorder
        ))
    }
}
