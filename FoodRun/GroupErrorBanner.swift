import SwiftUI

/// Stays visible when a submission fails at the bottom of a long form.
struct GroupErrorBanner: View {
    let message: String

    var body: some View {
        ViewThatFits(in: .vertical) {
            messageText.fixedSize(horizontal: false, vertical: true)
            ScrollView { messageText }
                .scrollIndicators(.visible)
        }
        .frame(maxHeight: FoodSpacing.s112)
        .fixedSize(horizontal: false, vertical: true)
        .padding(FoodSpacing.s16)
        .foodCard(showsBorder: true)
        .frame(maxWidth: FoodSpacing.s490)
        .padding(.horizontal, FoodSpacing.s24)
        .padding(.vertical, FoodSpacing.s8)
        .frame(maxWidth: .infinity)
        .background(FoodTheme.cream)
        .accessibilitySortPriority(1)
    }

    private var messageText: some View {
        Text(message)
            .font(FoodTypography.setting)
            .foregroundStyle(FoodTheme.error)
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
            .accessibilityIdentifier("groupError")
    }
}
