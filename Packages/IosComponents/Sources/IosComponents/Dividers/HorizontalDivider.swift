// Adapted from HorizontalDivider.swift, created by Karem on 10/24/23.
import SwiftUI

public struct HorizontalDivider: View {
    @EnvironmentObject var theme: ComponentsTheme
    private let height: CGFloat
    private let color: Color?

    public init(
        height: CGFloat = 1,
        color: Color? = nil
    ) {
        self.height = height
        self.color = color
    }

    public var body: some View {
        Rectangle()
            .fill(color ?? theme.colors.outline)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .accessibilityHidden(true)
    }
}
