// Adapted from FilledButton.swift, created by Karem on 10/24/23.
import SwiftUI

public struct FilledButtonAppearance {
    public var background: AnyShapeStyle?
    public var contentPadding: CGFloat?
    public var iconSpacing: CGFloat?
    public var iconFont: Font?
    public var borderColor: Color
    public var borderWidth: CGFloat
    public var shadowColor: Color
    public var shadowRadius: CGFloat
    public var shadowY: CGFloat
    public var disabledOpacity: Double
    public var pressedOpacity: Double
    public var pressedScale: CGFloat
    public var animation: Animation

    public init(
        background: AnyShapeStyle? = nil,
        contentPadding: CGFloat? = nil,
        iconSpacing: CGFloat? = nil,
        iconFont: Font? = nil,
        borderColor: Color = .clear,
        borderWidth: CGFloat = 0,
        shadowColor: Color = .clear,
        shadowRadius: CGFloat = 0,
        shadowY: CGFloat = 0,
        disabledOpacity: Double = 0.5,
        pressedOpacity: Double = 0.2,
        pressedScale: CGFloat = 1,
        animation: Animation = .easeOut(duration: 0.2)
    ) {
        self.background = background
        self.contentPadding = contentPadding
        self.iconSpacing = iconSpacing
        self.iconFont = iconFont
        self.borderColor = borderColor
        self.borderWidth = borderWidth
        self.shadowColor = shadowColor
        self.shadowRadius = shadowRadius
        self.shadowY = shadowY
        self.disabledOpacity = disabledOpacity
        self.pressedOpacity = pressedOpacity
        self.pressedScale = pressedScale
        self.animation = animation
    }
}

public struct FilledPrimaryButton: View {
    @EnvironmentObject var theme: ComponentsTheme

    private var text: String
    private var textStyle: Font?
    private var foregroundColor: Color?
    private var startIconImage: Image?
    private var endIconImage: Image?
    private var shape: RoundedRectangle?
    private var height: CGFloat?
    private var isEnabled: Bool
    private var isDimmed: Bool
    private var isSmallHeight: Bool
    private var appearance: FilledButtonAppearance
    private var onClick: () -> Void

    public init(
        text: String,
        textStyle: Font? = nil,
        foregroundColor: Color? = nil,
        startIconImage: Image? = nil,
        endIconImage: Image? = nil,
        isEnabled: Bool = true,
        isDimmed: Bool = false,
        shape: RoundedRectangle? = nil,
        height: CGFloat? = nil,
        isSmallHeight: Bool = false,
        appearance: FilledButtonAppearance = .init(),
        onClick: @escaping () -> Void
    ) {
        self.text = text
        self.textStyle = textStyle
        self.foregroundColor = foregroundColor
        self.startIconImage = startIconImage
        self.endIconImage = endIconImage
        self.isEnabled = isEnabled
        self.isDimmed = isDimmed
        self.shape = shape
        self.height = height
        self.isSmallHeight = isSmallHeight
        self.appearance = appearance
        self.onClick = onClick
    }

    public var body: some View {
        let buttonShape = shape ?? (isSmallHeight ? theme.shapes.xSmall : theme.shapes.medium)
        let minHeight = height ?? (isSmallHeight ? theme.spacings.btnMinHeightSmall : theme.spacings.btnMinHeightNormal)
        BaseButton(
            text: text,
            font: textStyle ?? theme.typography.bodyMediumFont,
            textColor: foregroundColor ?? theme.colors.onPrimary,
            shape: buttonShape,
            iconSize: theme.spacings.iconLarge,
            iconColor: foregroundColor ?? theme.colors.onPrimary,
            startIcon: startIconImage,
            endIcon: endIconImage,
            isEnabled: isEnabled,
            spacing: appearance.iconSpacing,
            iconFont: appearance.iconFont,
            onClick: onClick
        )
        .buttonStyle(FilledButtonStyle(
            background: appearance.background ?? AnyShapeStyle(theme.colors.primary),
            minHeight: minHeight,
            shape: buttonShape,
            isEnabled: isEnabled && !isDimmed,
            appearance: appearance
        ))
    }
}

private struct FilledButtonStyle: ButtonStyle {
    let background: AnyShapeStyle
    let minHeight: CGFloat
    let shape: RoundedRectangle
    let isEnabled: Bool
    let appearance: FilledButtonAppearance

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.vertical, appearance.contentPadding ?? 0)
            .frame(height: appearance.contentPadding == nil ? minHeight : nil)
            .background(background, in: shape)
            .overlay(shape.strokeBorder(appearance.borderColor, lineWidth: appearance.borderWidth))
            .shadow(
                color: appearance.shadowColor,
                radius: appearance.shadowRadius,
                y: appearance.shadowY
            )
            .opacity(isEnabled ? 1 : appearance.disabledOpacity)
            .opacity(configuration.isPressed ? appearance.pressedOpacity : 1)
            .scaleEffect(configuration.isPressed ? appearance.pressedScale : 1)
            .animation(appearance.animation, value: configuration.isPressed)
    }
}
