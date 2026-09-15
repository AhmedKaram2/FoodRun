import SwiftUI

struct FoodAvatar: View {
    enum Size {
        case small, medium, large

        var side: CGFloat {
            switch self {
            case .small: FoodSpacing.s32
            case .medium: FoodSpacing.s38
            case .large: FoodSpacing.s48
            }
        }

        var font: Font {
            switch self {
            case .small: FoodTypography.avatarSmall
            case .medium: FoodTypography.avatarMedium
            case .large: FoodTypography.avatarLarge
            }
        }

        var radius: CGFloat {
            switch self {
            case .small: FoodSpacing.s16
            case .medium: FoodRadius.avatar
            case .large: FoodRadius.input
            }
        }
    }

    let person: Person
    var size: Size = .medium

    var body: some View {
        Text(person.initial)
            .font(size.font)
            .foregroundStyle(FoodTheme.ink)
            .frame(width: size.side, height: size.side)
            .background(
                FoodTheme.color(for: person),
                in: RoundedRectangle(cornerRadius: size.radius)
            )
            .overlay {
                if size == .small {
                    Circle().strokeBorder(FoodTheme.cream, lineWidth: FoodBorder.heavy)
                }
            }
            .accessibilityHidden(true)
    }
}
