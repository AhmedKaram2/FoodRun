import SwiftUI
import UIKit

struct AddPersonSheet: View {
    let store: WheelStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: FoodSpacing.s20) {
                    Text(FoodStrings.text.addPersonTitle)
                        .font(FoodTypography.formTitle)
                    Text(FoodStrings.text.addPersonDescription)
                        .font(.subheadline)
                        .foregroundStyle(FoodTheme.muted)
                    FoodTextField(
                        value: store.nameDraft,
                        onValueChange: store.updateNameDraft,
                        label: FoodStrings.text.nameLabel,
                        error: store.nameError,
                        enabled: !store.isSpinning,
                        onSubmit: addPerson
                    )
                    if let message = store.state.persistenceErrorMessage {
                        Text(message)
                            .font(.caption)
                            .foregroundStyle(FoodTheme.orange)
                            .accessibilityIdentifier("addPersonPersistenceError")
                    }
                    PrimaryButton(
                        title: FoodStrings.text.addPersonAction,
                        symbol: "person.badge.plus",
                        isDisabled: !store.canSubmitName,
                        action: addPerson
                    )
                    .accessibilityIdentifier("confirmAddPersonButton")
                }
                .padding(FoodSpacing.s24)
            }
            .scrollBounceBehavior(.basedOnSize)
            .background(FoodTheme.cream)
            .foregroundStyle(FoodTheme.ink)
            .navigationTitle(FoodStrings.text.addPerson)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(FoodStrings.text.cancel, action: store.dismiss)
                        .accessibilityIdentifier("cancelAddPersonButton")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func addPerson() {
        let name = store.nameDraft
        let accepted = store.submitName()
        let announcement = accepted
            ? FoodStrings.text.personAddedAnnouncement(name: name)
            : store.nameError ?? store.state.persistenceErrorMessage
        if let announcement {
            UIAccessibility.post(
                notification: .announcement,
                argument: announcement
            )
        }
    }
}
