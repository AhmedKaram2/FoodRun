import SwiftUI
import IosComponents

enum FoodTheme {
    static let cream = Color(hex: 0xFAF7F2)
    static let ink = Color(hex: 0x292C28)
    static let muted = Color(hex: 0x696C63)
    static let orange = Color(hex: 0xBE431E)
    static let line = Color(hex: 0xE6E4DC)
    static let accentWash = Color(hex: 0xFBEEE7)
    static let hero = Color(hex: 0x263B32)
    static let onHero = Color(hex: 0xDCE5D8)
    static let successWash = Color(hex: 0xEAF1E5)
    static let error = Color(hex: 0xAF3025)
    static let white = Color.white
    static let black = Color.black
    static let wheelRim = Color(hex: 0xFFFDF7)
    static let sage = Color(hex: 0x9BAF72)
    static let available = Color(hex: 0x476B3F)
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
            error: error,
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
    static let button: CGFloat = 18
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
    static let hero = Font.system(.largeTitle, design: .rounded, weight: .heavy)
    static let sheetTitle = Font.system(.title, design: .rounded, weight: .heavy)
    static let formTitle = Font.system(.title2, design: .rounded, weight: .bold)
    static let button = Font.system(.headline, design: .rounded, weight: .bold)
    static let bodyBold = Font.system(.body, design: .rounded, weight: .bold)
    static let input = Font.system(.body, design: .rounded, weight: .semibold)
    static let setting = Font.system(.subheadline, design: .rounded, weight: .semibold)
    static let captionButton = Font.system(.caption, design: .rounded, weight: .bold)
    static let emptyTitle = Font.system(.title3, design: .rounded, weight: .bold)
    static let subtitle = Font.system(.subheadline, weight: .medium)
    static let footer = Font.system(.caption, weight: .medium)
    static let metadata = Font.system(.caption, weight: .medium)
    static let status = Font.system(.caption, design: .rounded, weight: .semibold)
    static let crewTitle = Font.system(.subheadline, design: .rounded, weight: .bold)
    static let crewAction = Font.system(.caption, design: .rounded, weight: .bold)
    static let share = Font.system(size: 13, weight: .semibold, design: .rounded)
    static let eyebrow = Font.system(.caption2, design: .rounded, weight: .heavy)
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
