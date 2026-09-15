import SwiftUI

struct HistorySheet: View {
    let history: [Pickup]
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: FoodSpacing.s24) {
                    VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                        Text(FoodStrings.text.historyTitle)
                            .font(FoodTypography.sheetTitle)
                        Text(FoodStrings.text.historyDescription)
                            .font(.subheadline)
                            .foregroundStyle(FoodTheme.muted)
                    }
                    if history.isEmpty {
                        VStack(spacing: FoodSpacing.s16) {
                            Image(systemName: "takeoutbag.and.cup.and.straw.fill")
                                .font(FoodTypography.emptyIcon)
                                .foregroundStyle(FoodTheme.orange)
                                .padding(FoodSpacing.s25)
                                .background(FoodTheme.orange.opacity(0.08), in: Circle())
                            Text(FoodStrings.text.historyEmptyTitle)
                                .font(FoodTypography.emptyTitle)
                            Text(FoodStrings.text.historyEmptyDescription)
                                .font(.subheadline)
                                .foregroundStyle(FoodTheme.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, FoodSpacing.s70)
                    } else {
                        LazyVStack(spacing: FoodSpacing.s12) {
                            ForEach(history) { pickup in
                                HStack(spacing: FoodSpacing.s14) {
                                    FoodAvatar(
                                        person: pickup.person,
                                        size: .large
                                    )
                                    VStack(alignment: .leading, spacing: FoodSpacing.s4) {
                                        Text(pickup.person.name)
                                            .font(FoodTypography.button)
                                        Text(pickup.dateLabel)
                                            .font(.caption)
                                            .foregroundStyle(FoodTheme.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "bag.fill")
                                        .foregroundStyle(FoodTheme.orange)
                                }
                                .padding(FoodSpacing.s16)
                                .foodCard()
                            }
                        }
                        Text(FoodStrings.text.historyFooter)
                            .font(.caption)
                            .foregroundStyle(FoodTheme.muted)
                    }
                }
                .padding(FoodSpacing.s24)
            }
            .foregroundStyle(FoodTheme.ink)
            .background(FoodTheme.cream)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(FoodStrings.text.done, action: onDone).fontWeight(.bold)
                }
            }
            .toolbarBackground(FoodTheme.cream, for: .navigationBar)
        }
        .presentationDragIndicator(.visible)
    }
}
