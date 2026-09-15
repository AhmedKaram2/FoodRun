import SwiftUI

public struct BaseButton: View {
    @EnvironmentObject var theme: ComponentsTheme

    var text: String
    var font: Font
    var textColor: Color
    var shape: RoundedRectangle
    var iconSize: CGFloat
    var iconColor: Color?
    var startIcon: Image?
    var endIcon: Image?
    var isEnabled: Bool
    var spacing: CGFloat? = nil
    var iconFont: Font? = nil
    var onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(
                alignment: .center,
                spacing: spacing ?? theme.spacings.paddingXLarge
            ) {
                if let startIcon {
                    startIcon
                        .font(iconFont ?? .system(size: iconSize))
                        .imageScale(iconFont == nil ? .large : .medium)
                        .foregroundColor(iconColor ?? textColor)
                }
                Text(text)
                    .font(font)
                    .foregroundColor(textColor)
                    .multilineTextAlignment(.center)
                if let endIcon {
                    endIcon
                        .font(iconFont ?? .system(size: iconSize))
                        .imageScale(iconFont == nil ? .large : .medium)
                        .foregroundColor(iconColor ?? textColor)
                }
            }
            .frame(maxWidth: .infinity)
            .contentShape(shape)
        }
        .disabled(!isEnabled)
    }
}
