import SwiftUI
import FoodRunShared

struct GroupScreen: View {
    @ObservedObject var store: GroupStore
    let wheelStore: WheelStore
    @Environment(\.scenePhase) private var scenePhase
    private var state: GroupState { store.state }
    var body: some View {
        Group {
            if state.page == .quickSpin {
                VStack {
                    Button(GroupText.shared.backToRooms) { store.dispatch(.back) }
                        .font(FoodTypography.button)
                        .padding(FoodSpacing.s12)
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
        ScrollViewReader { scroll in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: FoodSpacing.s16) {
                    header.id("groupHeader")
                    if state.busy { ProgressView().frame(maxWidth: .infinity) }
                    if let wheel = state.wheel { GroupWheelContent(wheel: wheel).id(wheel.round.id) }
                    ForEach(state.fields, id: \.key.name) { field in
                        GroupFieldContent(field: field, enabled: !state.busy) { store.controller.update(key: field.key, value: $0) }
                    }
                    ForEach(state.buttons, id: \.viewID) { button in actionButton(button) }
                    ForEach(state.cards, id: \.id) { card in cardContent(card) }
                }
                .frame(maxWidth: FoodSpacing.s490)
                .padding(FoodSpacing.s24)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .accessibilityIdentifier("groupScreen")
            .safeAreaInset(edge: .top, spacing: FoodSpacing.s0) {
                if !state.error.isEmpty { GroupErrorBanner(message: state.error) }
            }
            .onChange(of: state.error, initial: true) { _, message in
                guard !message.isEmpty, scenePhase == .active else { return }
                UIAccessibility.post(notification: .announcement, argument: message)
            }
            .onChange(of: state.page) { _, _ in
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
        VStack(alignment: .leading, spacing: FoodSpacing.s8) {
            if state.canGoBack {
                Button(GroupText.shared.back) { store.dispatch(.back) }
                    .font(FoodTypography.button)
                    .frame(minHeight: FoodSpacing.s44)
            }
            Text(GroupText.shared.brand).font(FoodTypography.eyebrow).tracking(FoodSpacing.s2).foregroundStyle(FoodTheme.orange)
            Text(state.title).font(FoodTypography.hero).foregroundStyle(FoodTheme.ink)
            Text(state.subtitle).font(FoodTypography.input).foregroundStyle(FoodTheme.muted)
            HStack(spacing: FoodSpacing.s8) {
                Circle().fill(state.online ? FoodTheme.available : FoodTheme.muted).frame(width: FoodSpacing.s8, height: FoodSpacing.s8)
                Text(state.status).font(FoodTypography.status).foregroundStyle(FoodTheme.muted)
            }
            if !state.roomCode.isEmpty { Text(state.roomCode).font(FoodTypography.sheetTitle).foregroundStyle(FoodTheme.orange).textSelection(.enabled).accessibilityIdentifier("roomCode") }
        }
    }
    @ViewBuilder private func actionButton(_ button: GroupButton) -> some View {
        if button.primary {
            PrimaryButton(title: button.title, symbol: "arrow.right", isDisabled: state.busy || !button.enabled) { store.dispatch(button.action, button.value) }
                .accessibilityIdentifier("action:\(button.action.name):\(button.value)")
        } else {
            SecondaryButton(title: button.title, symbol: button.destructive ? "trash" : "arrow.right", isDisabled: state.busy || !button.enabled) { store.dispatch(button.action, button.value) }
                .accessibilityIdentifier("action:\(button.action.name):\(button.value)")
        }
    }
    private func cardContent(_ card: GroupCard) -> some View {
        VStack(alignment: .leading, spacing: FoodSpacing.s12) {
            Text(card.title).font(FoodTypography.bodyBold).foregroundStyle(FoodTheme.ink)
            if !card.badge.isEmpty { Text(card.badge).font(FoodTypography.status).foregroundStyle(FoodTheme.orange) }
            if !card.detail.isEmpty { Text(card.detail).font(FoodTypography.setting).foregroundStyle(FoodTheme.muted).textSelection(.enabled) }
            ForEach(card.buttons, id: \.viewID) { button in actionButton(button) }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(FoodSpacing.s20).foodCard(showsBorder: true)
    }
}

private extension GroupButton {
    var viewID: String { "\(action.name):\(value)" }
}
