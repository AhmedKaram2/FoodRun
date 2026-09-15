import SwiftUI
import class FoodRunShared.CrewPersonItem

struct CrewSheet: View {
    @Bindable var store: WheelStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let items = store.state.crewItems
        return NavigationStack {
            ScrollViewReader { scrollProxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: FoodSpacing.s22) {
                        VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                            Text(FoodStrings.text.crewTitle)
                                .font(FoodTypography.sheetTitle)
                            Text(FoodStrings.text.crewDescription)
                                .font(.subheadline)
                                .foregroundStyle(FoodTheme.muted)
                        }
                        if let message = store.state.persistenceErrorMessage {
                            Text(message)
                                .font(.caption)
                                .foregroundStyle(FoodTheme.orange)
                                .accessibilityIdentifier("crewPersistenceError")
                        }
                        SecondaryButton(
                            title: FoodStrings.text.addPerson,
                            symbol: "plus.circle.fill",
                            isDisabled: store.isSpinning,
                            action: store.openAddPerson
                        )
                        .accessibilityIdentifier("addPersonButton")
                        LazyVStack(spacing: FoodSpacing.s0) {
                            ForEach(items, id: \.id) { item in
                                personRow(item)
                                    .id(item.id)
                                if item.id != items.last?.id {
                                    FoodDivider().padding(.leading, FoodSpacing.s66)
                                }
                            }
                        }
                        .foodCard(radius: FoodRadius.group)
                        HStack {
                            Text(FoodStrings.text.keepOneFriend)
                                .font(.caption)
                                .foregroundStyle(FoodTheme.muted)
                            Spacer()
                            Button(FoodStrings.text.selectAll) { store.includeEveryone() }
                                .font(FoodTypography.captionButton)
                        }
                        Toggle(isOn: $store.hapticsEnabled) {
                            Label(FoodStrings.text.hapticTicks, systemImage: "waveform.path")
                                .font(FoodTypography.setting)
                        }
                        .padding(FoodSpacing.s18)
                        .foodCard(radius: FoodRadius.setting)
                        .accessibilityIdentifier("hapticsToggle")
                    }
                    .padding(FoodSpacing.s24)
                }
                .background(FoodTheme.cream)
                .foregroundStyle(FoodTheme.ink)
                .onChange(of: items.count) {
                    guard let item = items.last else { return }
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.25)) {
                        scrollProxy.scrollTo(item.id, anchor: .center)
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(FoodStrings.text.done, action: store.dismiss).fontWeight(.bold)
                    }
                }
                .toolbarBackground(FoodTheme.cream, for: .navigationBar)
            }
        }
        .presentationDragIndicator(.visible)
        .sheet(isPresented: Binding(
            get: { store.addPersonPresented },
            set: { if !$0 && store.addPersonPresented { store.dismiss() } }
        )) {
            AddPersonSheet(store: store)
        }
    }

    private func personRow(_ item: CrewPersonItem) -> some View {
        let person = Person(item.person)
        return HStack(spacing: FoodSpacing.s0) {
            participationButton(
                person: person,
                item: item
            )
            if item.isCustom {
                Button { store.removeAddedPerson(person) } label: {
                    Image(systemName: "trash")
                        .font(FoodTypography.removeIcon)
                        .foregroundStyle(FoodTheme.muted)
                        .frame(width: FoodSpacing.s44, height: FoodSpacing.s44)
                }
                .buttonStyle(.plain)
                .disabled(!item.canRemove)
                .accessibilityLabel(item.removeAccessibility)
                .accessibilityIdentifier("removePerson_\(person.id)")
                .padding(.trailing, FoodSpacing.s4)
            }
        }
    }

    private func participationButton(
        person: Person,
        item: CrewPersonItem
    ) -> some View {
        Button { store.toggle(person) } label: {
            HStack(spacing: FoodSpacing.s14) {
                FoodAvatar(person: person)
                Text(person.name)
                    .font(FoodTypography.bodyBold)
                Spacer()
                Text(item.participationLabel)
                    .font(.caption)
                    .foregroundStyle(FoodTheme.muted)
                Image(systemName: item.included ? "checkmark.circle.fill" : "circle")
                    .font(FoodTypography.selectionIcon)
                    .foregroundStyle(item.included ? FoodTheme.orange : FoodTheme.line)
            }
            .foregroundStyle(FoodTheme.ink)
            .padding(.horizontal, FoodSpacing.s16)
            .padding(.vertical, FoodSpacing.s10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!item.canToggle)
        .accessibilityLabel(person.name)
        .accessibilityValue(item.participationAccessibility)
        .accessibilityHint(!item.canToggle ? FoodStrings.text.participationLockedHint : FoodStrings.text.participationToggleHint)
        .accessibilityIdentifier("person_\(person.id)")
    }
}
