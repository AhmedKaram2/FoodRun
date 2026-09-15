//
//  File.swift
//  
//
//  Created by Karem on 10/23/23.
//

import Foundation
import SwiftUI

public extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}


public let gold = Color(hex: 0xFFB68A35)
public let lightGold = Color(hex: 0xFFC3A05C)
public let white = Color(hex: 0xFFFFFFFF)
public let backgroundGray = Color(hex:0xFF9F9F9)
public let gray = Color(hex: 0xFF909090)
public let darkGray = Color(hex: 0xFF494A4D)
public let lightGray = Color(hex: 0xFFB7B7B7)
public let lightGray30 = Color(hex: 0xFFB7B7B7).opacity(0.3)
public let red = Color(hex: 0xFFE11200)


public struct ComponentsColors {
    public var primary: Color
    public var onPrimary: Color
    public var primaryContainer: Color
    public var onPrimaryContainer: Color
    public var secondary: Color
    public var onSecondary: Color
    public var secondaryContainer: Color
    public var onSecondaryContainer: Color
    public var tertiary: Color
    public var onTertiary: Color
    public var error: Color
    public var background: Color
    public var outline: Color

    public init(
        primary: Color = gold,
        onPrimary: Color = white,
        primaryContainer: Color = lightGold,
        onPrimaryContainer: Color = white,
        secondary: Color = darkGray,
        onSecondary: Color = white,
        secondaryContainer: Color = lightGray,
        onSecondaryContainer: Color = white,
        tertiary: Color = gray,
        onTertiary: Color = white,
        error: Color = red,
        background: Color = backgroundGray,
        outline: Color = lightGray30
    ) {
        self.primary = primary
        self.onPrimary = onPrimary
        self.primaryContainer = primaryContainer
        self.onPrimaryContainer = onPrimaryContainer
        self.secondary = secondary
        self.onSecondary = onSecondary
        self.secondaryContainer = secondaryContainer
        self.onSecondaryContainer = onSecondaryContainer
        self.tertiary = tertiary
        self.onTertiary = onTertiary
        self.error = error
        self.background = background
        self.outline = outline
    }
}
