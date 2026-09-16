import SwiftUI
import FoodRunShared

struct GroupScreen: View {
    @ObservedObject var store: GroupStore
    let wheelStore: WheelStore
    @Environment(\.scenePhase) private var scenePhase
    private var state: GroupState { store.state }
    @State private var optionsExpanded = false
    var body: some View {
        Group {
            if state.page == .quickSpin {
                VStack(spacing: FoodSpacing.s0) {
                    Button { store.dispatch(.back) } label: {
                        Label(GroupText.shared.backToRooms, systemImage: "chevron.left")
                            .font(FoodTypography.setting).frame(minHeight: FoodSpacing.s44)
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, FoodSpacing.s24)
                    ContentView(store: wheelStore)
                }
            } else { content }
        }
        .background(FoodTheme.cream.ignoresSafeArea())
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.controller.foreground() }
            else if phase == .background { store.controller.background() }
        }
    }
    private var content: some View {
        let state = store.state
        return ScrollViewReader { scroll in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: FoodSpacing.s16) {
                    if state.page == .home {
                        GroupHomeContent(state: state, dispatch: store.dispatch).id("groupHeader")
                    } else {
                        header.id("groupHeader")
                        if state.progressStep >= 0 { GroupProgress(step: Int(state.progressStep)) }
                        if let wheel = state.wheel { GroupWheelContent(wheel: wheel).id(wheel.round.id) }
                        ForEach(state.mainFields, id: \.key.name) { field in fieldContent(field) }
                        if state.page != .room { extraOptions }
                        ForEach(state.inlineButtons, id: \.renderID) { button in
                            GroupActionContent(button: button, busy: state.busy, dispatch: store.dispatch, prominent: false)
                        }
                        ForEach(Array(state.sections.enumerated()), id: \.offset) { _, section in
                            if !section.title.isEmpty { GroupSectionHeading(title: section.title, count: section.cards.count) }
                            ForEach(section.cards, id: \.renderID) { card in
                                GroupCardContent(card: card, busy: state.busy, dispatch: store.dispatch)
                            }
                        }
                        if state.page == .room { extraOptions }
                    }
                }
                .frame(maxWidth: FoodSpacing.s490)
                .padding(FoodSpacing.s24)
                .frame(maxWidth: .infinity)
            }
            .id(state.page.name)
            .scrollDismissesKeyboard(.interactively)
            .accessibilityIdentifier("groupScreen")
            .safeAreaInset(edge: .top, spacing: FoodSpacing.s0) {
                VStack(spacing: FoodSpacing.s0) {
                    if !state.error.isEmpty { GroupErrorBanner(message: state.error) }
                    if state.busy {
                        ProgressView(GroupText.shared.working).font(FoodTypography.status)
                            .padding(FoodSpacing.s8).frame(maxWidth: .infinity).background(FoodTheme.cream)
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: FoodSpacing.s0) {
                if state.page != .home, let primary = state.primaryAction {
                    actionButton(primary).padding(FoodSpacing.s16)
                        .frame(maxWidth: FoodSpacing.s490).frame(maxWidth: .infinity)
                        .background(FoodTheme.cream)
                        .overlay(alignment: .top) { FoodDivider() }
                }
            }
            .onChange(of: state.error, initial: true) { _, message in
                guard !message.isEmpty, scenePhase == .active else { return }
                if !state.extraFields.isEmpty { optionsExpanded = true }
                UIAccessibility.post(notification: .announcement, argument: message)
            }
            .onChange(of: state.page) { _, _ in
                UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                optionsExpanded = false
                scroll.scrollTo("groupHeader", anchor: .top)
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(FoodStrings.text.done) {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
        }
    }
    private var header: some View {
        VStack(alignment: .leading, spacing: FoodSpacing.s12) {
            HStack {
                if state.canGoBack {
                    Button { store.dispatch(.back) } label: {
                        Label(GroupText.shared.back, systemImage: "chevron.left").font(FoodTypography.setting)
                            .frame(minHeight: FoodSpacing.s44)
                    }
                }
                Spacer()
                Text(FoodStrings.text.brand).font(FoodTypography.eyebrow).tracking(FoodSpacing.s2).foregroundStyle(FoodTheme.muted)
            }
            Text(state.title).font(FoodTypography.hero).foregroundStyle(FoodTheme.ink)
                .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            if !state.subtitle.isEmpty {
                Text(state.subtitle).font(FoodTypography.subtitle).foregroundStyle(FoodTheme.muted)
            }
            if !state.status.isEmpty {
                Label(state.status, systemImage: state.online ? "wifi" : "externaldrive")
                    .font(FoodTypography.status).foregroundStyle(FoodTheme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !state.roomCode.isEmpty {
                HStack {
                    VStack(alignment: .leading, spacing: FoodSpacing.s4) {
                        Text(GroupText.shared.roomCode).font(FoodTypography.eyebrow).tracking(FoodSpacing.s1).foregroundStyle(FoodTheme.muted)
                        Text(state.roomCode).font(FoodTypography.formTitle).tracking(FoodSpacing.s3)
                            .foregroundStyle(FoodTheme.ink).textSelection(.enabled).accessibilityIdentifier("roomCode")
                    }
                    Spacer()
                    Button { store.dispatch(.shareRoom) } label: {
                        Image(systemName: "square.and.arrow.up").font(FoodTypography.brandIcon)
                            .frame(width: FoodSpacing.s48, height: FoodSpacing.s48)
                    }.disabled(state.busy).accessibilityLabel("Invite people")
                }.padding(FoodSpacing.s16).foodCard(showsBorder: true)
            }
        }
    }

    @ViewBuilder private var extraOptions: some View {
        if !state.extraFields.isEmpty || !state.utilityButtons.isEmpty {
            VStack(spacing: FoodSpacing.s16) {
                Button { optionsExpanded.toggle() } label: {
                    HStack(spacing: FoodSpacing.s12) {
                        Text(state.page == .connect ? GroupText.shared.manualConnection : state.page == .library ? GroupText.shared.pasteMenu : GroupText.shared.roomOptions)
                            .font(FoodTypography.setting).foregroundStyle(FoodTheme.ink)
                        Spacer(minLength: FoodSpacing.s8)
                        Image(systemName: optionsExpanded ? "chevron.up" : "chevron.down")
                            .foregroundStyle(FoodTheme.muted).accessibilityHidden(true)
                    }
                    .frame(maxWidth: .infinity, minHeight: FoodSpacing.s44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(optionsExpanded ? "Expanded" : "Collapsed")
                .accessibilityIdentifier("groupOptions")
                if optionsExpanded {
                    ForEach(state.extraFields, id: \.key.name) { field in fieldContent(field) }
                    ForEach(state.utilityButtons, id: \.renderID) { button in actionButton(button) }
                }
            }
            .padding(FoodSpacing.s16).foodCard(showsBorder: true)
        }
    }
    private func fieldContent(_ field: GroupField) -> some View {
        GroupFieldContent(field: field, enabled: !state.busy) { store.controller.update(key: field.key, value: $0) }
    }
    private func actionButton(_ button: GroupButton) -> some View {
        GroupActionContent(button: button, busy: state.busy, dispatch: store.dispatch)
    }
}
