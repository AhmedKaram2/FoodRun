import SwiftUI
import FoodRunShared

struct GroupHomeContent: View {
    let state: GroupState
    let dispatch: (GroupAction, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: FoodSpacing.s24) {
            HStack(spacing: FoodSpacing.s10) {
                Text(FoodStrings.text.brand).font(FoodTypography.brand).tracking(FoodSpacing.s2)
                Spacer()
                GroupLanguagePicker(state: state, dispatch: dispatch)
            }
            if let signIn = state.cards.first(where: { $0.id == "sign-in" }) {
                GroupCardContent(card: signIn, busy: state.busy, dispatch: dispatch)
                ForEach(state.buttons.filter { $0.action != .setLanguage }, id: \.renderID) { button in
                    GroupActionContent(button: button, busy: state.busy, dispatch: dispatch)
                }
            } else {
            VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                Text(GroupText.shared.localized(value: GroupText.shared.homeTitle, rtl: state.rtl)).font(FoodTypography.hero).tracking(-1)
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                Text(GroupText.shared.localized(value: GroupText.shared.homeSubtitle, rtl: state.rtl)).font(FoodTypography.subtitle).foregroundStyle(FoodTheme.muted)
            }
            VStack(alignment: .leading, spacing: FoodSpacing.s16) {
                HStack {
                    Text(GroupText.shared.localized(value: GroupText.shared.groupEyebrow, rtl: state.rtl)).font(FoodTypography.eyebrow).tracking(FoodSpacing.s1)
                    Spacer(minLength: FoodSpacing.s8)
                    Image(systemName: "person.3.fill").font(FoodTypography.brandIcon).accessibilityHidden(true)
                }.foregroundStyle(FoodTheme.onHero)
                Text(GroupText.shared.localized(value: GroupText.shared.groupTitle, rtl: state.rtl)).font(FoodTypography.formTitle).foregroundStyle(FoodTheme.white)
                Text(GroupText.shared.localized(value: GroupText.shared.groupDescription, rtl: state.rtl)).font(FoodTypography.subtitle).foregroundStyle(FoodTheme.onHero)
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
                    GroupSectionHeading(title: GroupText.shared.localized(value: GroupText.shared.savedRooms, rtl: state.rtl), count: rooms.count)
                    ForEach(rooms, id: \.renderID) { card in GroupCardContent(card: card, busy: state.busy, dispatch: dispatch) }
                }
            }
            let dashboard = state.cards.filter { $0.id.hasPrefix("dashboard:") }
            if !dashboard.isEmpty {
                VStack(alignment: .leading, spacing: FoodSpacing.s12) {
                    GroupSectionHeading(title: state.rtl ? "المحفظة والطلبات" : "Wallet & orders")
                    ForEach(dashboard, id: \.renderID) { card in
                        GroupCardContent(card: card, busy: state.busy, dispatch: dispatch)
                    }
                }
            }
            VStack(alignment: .leading, spacing: FoodSpacing.s12) {
                GroupSectionHeading(title: GroupText.shared.localized(value: GroupText.shared.explore, rtl: state.rtl))
                ForEach(state.buttons.filter { $0.action == .quickSpin || $0.action == .openLibrary }, id: \.renderID) { button in
                    Button { dispatch(button.action, button.value) } label: {
                        HStack(spacing: FoodSpacing.s14) {
                            GroupIcon(symbol: button.symbol, accented: button.action == .quickSpin)
                            VStack(alignment: .leading, spacing: FoodSpacing.s5) {
                                Text(button.title).font(FoodTypography.bodyBold).foregroundStyle(FoodTheme.ink)
                                Text(button.action == .quickSpin ? GroupText.shared.localized(value: GroupText.shared.quickDescription, rtl: state.rtl) : GroupText.shared.localized(value: GroupText.shared.libraryDescription, rtl: state.rtl))
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
            ForEach(state.buttons.filter { ![GroupAction.create, .join, .quickSpin, .openLibrary, .setLanguage].contains($0.action) }, id: \.renderID) { button in
                GroupActionContent(button: button, busy: state.busy, dispatch: dispatch)
            }
            if let about = state.cards.first(where: { $0.id == "about" }) {
                Label(about.detail, systemImage: "checkmark.shield")
                    .font(FoodTypography.footer).foregroundStyle(FoodTheme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            }
        }.foregroundStyle(FoodTheme.ink)
    }
}


struct GroupLanguagePicker: View {
    let state: GroupState
    let dispatch: (GroupAction, String) -> Void
    var body: some View {
        Menu {
            Picker("Language / اللغة", selection: Binding(get: { state.rtl ? "ar" : "en" }, set: { dispatch(.setLanguage, $0) })) {
                Text("English").tag("en")
                Text("العربية").tag("ar")
            }
        } label: {
            HStack(spacing: FoodSpacing.s8) {
                Image(systemName: "globe")
                Text(state.rtl ? "العربية" : "English")
                Image(systemName: "chevron.down").imageScale(.small)
            }
            .font(FoodTypography.setting)
            .padding(.horizontal, FoodSpacing.s12)
            .frame(minHeight: FoodSpacing.s44)
            .background(FoodTheme.white, in: Capsule())
        }
        .disabled(state.busy)
        .accessibilityLabel("Language / اللغة")
        .accessibilityValue(state.rtl ? "العربية" : "English")
        .accessibilityIdentifier("languagePicker")
    }
}
