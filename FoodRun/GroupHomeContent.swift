import SwiftUI
import FoodRunShared

struct GroupHomeContent: View {
    let state: GroupState
    let dispatch: (GroupAction, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: FoodSpacing.s24) {
            HStack(spacing: FoodSpacing.s10) {
                GroupIcon(symbol: "takeoutbag.and.cup.and.straw.fill", accented: true)
                Text(FoodStrings.text.brand).font(FoodTypography.brand).tracking(FoodSpacing.s2)
                Spacer()
                Text("TOGETHER").font(FoodTypography.eyebrow).tracking(FoodSpacing.s1)
                    .foregroundStyle(FoodTheme.available)
                    .padding(FoodSpacing.s8).background(FoodTheme.successWash, in: Capsule())
            }
            VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                Text(GroupText.shared.homeTitle).font(FoodTypography.hero).tracking(-1)
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                Text(GroupText.shared.homeSubtitle).font(FoodTypography.subtitle).foregroundStyle(FoodTheme.muted)
            }
            VStack(alignment: .leading, spacing: FoodSpacing.s16) {
                HStack {
                    Text(GroupText.shared.groupEyebrow).font(FoodTypography.eyebrow).tracking(FoodSpacing.s1)
                    Spacer(minLength: FoodSpacing.s8)
                    Image(systemName: "person.3.fill").font(FoodTypography.brandIcon).accessibilityHidden(true)
                }.foregroundStyle(FoodTheme.onHero)
                Text(GroupText.shared.groupTitle).font(FoodTypography.formTitle).foregroundStyle(FoodTheme.white)
                Text(GroupText.shared.groupDescription).font(FoodTypography.subtitle).foregroundStyle(FoodTheme.onHero)
                ForEach(state.buttons.filter { $0.action == .create || $0.action == .join }, id: \.renderID) { button in
                    GroupActionContent(button: button, busy: state.busy, dispatch: dispatch)
                }
            }
            .padding(FoodSpacing.s24)
            .background(FoodTheme.hero, in: RoundedRectangle(cornerRadius: FoodRadius.group))

            ForEach(state.cards.filter { $0.id.hasPrefix("invitation:") }, id: \.renderID) { card in GroupCardContent(card: card, busy: state.busy, dispatch: dispatch) }
            let rooms = state.cards.filter { $0.id.hasPrefix("session:") }
            if !rooms.isEmpty {
                VStack(alignment: .leading, spacing: FoodSpacing.s12) {
                    GroupSectionHeading(title: GroupText.shared.savedRooms, count: rooms.count)
                    ForEach(rooms, id: \.renderID) { card in GroupCardContent(card: card, busy: state.busy, dispatch: dispatch) }
                }
            }
            VStack(alignment: .leading, spacing: FoodSpacing.s12) {
                GroupSectionHeading(title: GroupText.shared.explore)
                ForEach(state.buttons.filter { $0.action == .quickSpin || $0.action == .openLibrary }, id: \.renderID) { button in
                    Button { dispatch(button.action, button.value) } label: {
                        HStack(spacing: FoodSpacing.s14) {
                            GroupIcon(symbol: button.symbol, accented: button.action == .quickSpin)
                            VStack(alignment: .leading, spacing: FoodSpacing.s5) {
                                Text(button.title).font(FoodTypography.bodyBold).foregroundStyle(FoodTheme.ink)
                                Text(button.action == .quickSpin ? GroupText.shared.quickDescription : GroupText.shared.libraryDescription)
                                    .font(FoodTypography.footer).foregroundStyle(FoodTheme.muted)
                            }
                            Spacer(minLength: FoodSpacing.s0)
                            Image(systemName: "arrow.up.right").foregroundStyle(FoodTheme.muted).accessibilityHidden(true)
                        }.padding(FoodSpacing.s16).frame(maxWidth: .infinity, alignment: .leading).foodCard(showsBorder: true)
                    }
                    .buttonStyle(PressableStyle()).disabled(state.busy || !button.enabled)
                    .accessibilityIdentifier("action:\(button.action.name):\(button.value)")
                }
            }
            ForEach(state.buttons.filter { ![GroupAction.create, .join, .quickSpin, .openLibrary].contains($0.action) }, id: \.renderID) { button in
                GroupActionContent(button: button, busy: state.busy, dispatch: dispatch)
            }
            if let about = state.cards.first(where: { $0.id == "about" }) {
                Label(about.detail, systemImage: "checkmark.shield")
                    .font(FoodTypography.footer).foregroundStyle(FoodTheme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }.foregroundStyle(FoodTheme.ink)
    }
}
