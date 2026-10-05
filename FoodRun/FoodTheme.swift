import SwiftUI
import UIKit
import IosComponents

enum FoodTheme {
    static let cream = Color(hex: 0xF7FAF8)
    static let ink = Color(hex: 0x172B26)
    static let muted = Color(hex: 0x61756C)
    static let orange = Color(hex: 0x167B58)
    static let line = Color(hex: 0xE1ECE7)
    static let accentWash = Color(hex: 0xEDF8F2)
    static let hero = Color(hex: 0x167B58)
    static let onHero = Color(hex: 0xE2F3EA)
    static let successWash = Color(hex: 0xEDF8F2)
    static let error = Color(hex: 0xAF3025)
    static let white = Color.white
    static let black = Color.black
    static let wheelRim = Color(hex: 0xFFFFFF)
    static let sage = Color(hex: 0x9BAF72)
    static let available = Color(hex: 0x167B58)
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
    static let input: CGFloat = 12
    static let secondaryButton: CGFloat = 12
    static let setting: CGFloat = 20
    static let button: CGFloat = 12
    static let group: CGFloat = 16
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
    // The Arabic font is a glyph fallback, so Arabic and mixed-language strings
    // use the same bundled typefaces without depending on the device language.
    private static func text(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let descriptor = UIFontDescriptor(name: "DMSans-9ptRegular", size: size)
            .addingAttributes([.cascadeList: [UIFontDescriptor(name: "NotoSansArabic-Regular", size: size)]])
        return Font(UIFontMetrics(forTextStyle: .body).scaledFont(for: UIFont(descriptor: descriptor, size: size))).weight(weight)
    }
    static let brand = text(15, .bold)
    static let hero = text(30, .bold)
    static let sheetTitle = text(26, .bold)
    static let formTitle = text(23, .bold)
    static let button = text(16, .bold)
    static let bodyBold = text(16, .semibold)
    static let input = text(16)
    static let setting = text(14)
    static let captionButton = text(12, .bold)
    static let emptyTitle = text(20, .bold)
    static let subtitle = text(14)
    static let footer = text(12)
    static let metadata = text(12)
    static let status = text(12, .semibold)
    static let crewTitle = text(15, .semibold)
    static let crewAction = text(12, .bold)
    static let share = text(13, .semibold)
    static let eyebrow = text(11, .bold)
    static let winnerIntro = text(16, .semibold)
    static let winner = text(48, .bold)
    static let wheelCenter = text(8, .bold)
    static let avatarSmall = text(13, .bold)
    static let avatarMedium = text(16, .bold)
    static let avatarLarge = text(20, .bold)
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
        text(size * 0.046, .bold)
    }
}
