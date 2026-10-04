import SwiftUI

/// Feedback remains visible above long forms until dismissed or its timer expires.
struct GroupErrorBanner: View {
    let message: String
    var isError = true
    var rtl = false
    var dismiss: () -> Void = {}

    var body: some View {
        HStack(alignment: .top, spacing: FoodSpacing.s12) {
            Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .foregroundStyle(isError ? FoodTheme.error : FoodTheme.available).accessibilityHidden(true)
            ViewThatFits(in: .vertical) {
                messageText.fixedSize(horizontal: false, vertical: true)
                ScrollView { messageText }.scrollIndicators(.visible)
            }
            Button(action: dismiss) { Image(systemName: "xmark").padding(FoodSpacing.s4) }
                .buttonStyle(.plain).foregroundStyle(FoodTheme.muted)
                .accessibilityLabel(rtl ? "إغلاق" : "Close")
        }
        .frame(maxHeight: FoodSpacing.s112)
        .fixedSize(horizontal: false, vertical: true)
        .padding(FoodSpacing.s16)
        .background(isError ? FoodTheme.accentWash : FoodTheme.successWash, in: RoundedRectangle(cornerRadius: FoodRadius.input))
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
            .foregroundStyle(isError ? FoodTheme.error : FoodTheme.available)
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
            .accessibilityIdentifier(isError ? "groupError" : "groupSuccess")
    }
}
