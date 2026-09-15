import SwiftUI
import IosComponents

enum FoodTheme {
    static let cream = Color(hex: 0xFFF9EF)
    static let ink = Color(hex: 0x29251F)
    static let muted = Color(hex: 0x82796D)
    static let orange = Color(hex: 0xEA5B2A)
    static let line = Color(hex: 0xEAE0D1)
    static let white = Color.white
    static let black = Color.black
    static let wheelRim = Color(hex: 0xFFFDF7)
    static let sage = Color(hex: 0x9BAF72)
    static let available = Color(hex: 0x6F9662)
    static let palette: [Color] = [
        Color(hex: 0xF49A79), Color(hex: 0xF5CB69), Color(hex: 0xBAD4AD),
        Color(hex: 0xB8CBEB), Color(hex: 0xCEBAE4), Color(hex: 0xF2B4BD),
        Color(hex: 0xEAB88A), Color(hex: 0xCADA85), Color(hex: 0x8DCBC3),
        Color(hex: 0xE7C896)
    ]
    static let components = ComponentsTheme(
        colors: ComponentsColors(
            primary: orange,
            onPrimary: white,
            secondary: ink,
            tertiary: muted,
            error: orange,
            background: cream,
            outline: line
        ),
        shapes: ComponentsShapes(
            small: RoundedRectangle(cornerRadius: FoodRadius.input),
            medium: RoundedRectangle(cornerRadius: FoodRadius.button)
        )
    )

    static func color(for person: Person) -> Color {
        palette[person.id % palette.count]
    }
}

enum FoodRadius {
    static let avatar: CGFloat = 13
    static let input: CGFloat = 16
    static let secondaryButton: CGFloat = 18
    static let setting: CGFloat = 20
    static let button: CGFloat = 22
    static let group: CGFloat = 24
    static let winner: CGFloat = 34
}

enum FoodBorder {
    static let thin: CGFloat = 1
    static let wheelSlice: CGFloat = 1.2
    static let wheelOutline: CGFloat = 1.5
    static let medium: CGFloat = 2
    static let heavy: CGFloat = 2.5
}

enum FoodMotion {
    static let pressedScale: CGFloat = 0.96
    static let press = Animation.spring(response: 0.28, dampingFraction: 0.65)
    static let reducedPress = Animation.easeOut(duration: 0.12)
}

enum FoodTypography {
    static let brand = Font.system(size: 15, weight: .black, design: .rounded)
    static let hero = Font.system(size: 37, weight: .heavy, design: .rounded)
    static let sheetTitle = Font.system(size: 30, weight: .heavy, design: .rounded)
    static let formTitle = Font.system(size: 25, weight: .heavy, design: .rounded)
    static let button = Font.system(.headline, design: .rounded, weight: .bold)
    static let bodyBold = Font.system(.body, design: .rounded, weight: .bold)
    static let input = Font.system(.body, design: .rounded, weight: .semibold)
    static let setting = Font.system(.subheadline, design: .rounded, weight: .semibold)
    static let captionButton = Font.system(.caption, design: .rounded, weight: .bold)
    static let emptyTitle = Font.system(.title3, design: .rounded, weight: .bold)
    static let subtitle = Font.system(size: 14, weight: .medium)
    static let footer = Font.system(size: 12, weight: .medium)
    static let metadata = Font.system(size: 11, weight: .medium)
    static let status = Font.system(size: 12, weight: .semibold, design: .rounded)
    static let crewTitle = Font.system(size: 14, weight: .bold, design: .rounded)
    static let crewAction = Font.system(size: 12, weight: .bold, design: .rounded)
    static let share = Font.system(size: 13, weight: .semibold, design: .rounded)
    static let eyebrow = Font.system(size: 10, weight: .heavy, design: .rounded)
    static let winnerIntro = Font.system(size: 16, weight: .semibold, design: .rounded)
    static let winner = Font.system(size: 57, weight: .heavy, design: .rounded)
    static let wheelCenter = Font.system(size: 8, weight: .black, design: .rounded)
    static let avatarSmall = Font.system(size: 13, weight: .heavy, design: .rounded)
    static let avatarMedium = Font.system(size: 16, weight: .heavy, design: .rounded)
    static let avatarLarge = Font.system(size: 20, weight: .heavy, design: .rounded)
    static let buttonIcon = Font.system(size: 19, weight: .bold)
    static let brandIcon = Font.system(size: 18, weight: .semibold)
    static let historyIcon = Font.system(size: 20, weight: .medium)
    static let largeSparkle = Font.system(size: 25, weight: .medium)
    static let smallSparkle = Font.system(size: 17, weight: .medium)
    static let chevron = Font.system(size: 10, weight: .bold)
    static let removeIcon = Font.system(size: 16, weight: .medium)
    static let selectionIcon = Font.system(size: 23, weight: .medium)
    static let emptyIcon = Font.system(size: 44)
    static let winnerIcon = Font.system(size: 51, weight: .semibold)
    static let starIcon = Font.system(size: 22)
    static let sparkleIcon = Font.system(size: 18)
    static let centerIcon = Font.system(size: 24, weight: .semibold)

    static func wheelLabel(size: CGFloat) -> Font {
        .system(size: size * 0.046, weight: .heavy, design: .rounded)
    }
}
