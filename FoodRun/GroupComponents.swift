import SwiftUI
import FoodRunShared

struct GroupIcon: View {
    let symbol: String
    var accented = false
    var body: some View {
        Image(systemName: symbol).font(FoodTypography.brandIcon)
            .foregroundStyle(accented ? FoodTheme.orange : FoodTheme.available)
            .frame(width: FoodSpacing.s44, height: FoodSpacing.s44)
            .background(accented ? FoodTheme.accentWash : FoodTheme.successWash, in: RoundedRectangle(cornerRadius: FoodRadius.avatar))
            .accessibilityHidden(true)
    }
}

struct GroupSectionHeading: View {
    let title: String
    var count: Int? = nil
    var body: some View {
        HStack {
            Text(title).font(FoodTypography.bodyBold).accessibilityAddTraits(.isHeader)
            Spacer()
            if let count { Text("\(count)").font(FoodTypography.status).foregroundStyle(FoodTheme.muted) }
        }.foregroundStyle(FoodTheme.ink).padding(.top, FoodSpacing.s8)
    }
}

struct GroupActionContent: View {
    let button: GroupButton
    let busy: Bool
    let dispatch: (GroupAction, String) -> Void
    var prominent: Bool? = nil
    var body: some View {
        Group {
            if prominent ?? button.primary {
                PrimaryButton(title: button.title, symbol: button.symbol, isDisabled: busy || !button.enabled, action: performAction)
            } else {
                SecondaryButton(title: button.title, symbol: button.symbol, isDisabled: busy || !button.enabled, destructive: button.destructive, action: performAction)
            }
        }.accessibilityIdentifier("action:\(button.action.name):\(button.value)")
    }

    private func performAction() {
        // A submitted form or a saved selection must not leave its old input over the next step.
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        dispatch(button.action, button.value)
    }
}

struct GroupCardContent: View {
    let card: GroupCard
    let busy: Bool
    let dispatch: (GroupAction, String) -> Void
    private var member: Bool { card.id.hasPrefix("member:") || card.id.hasPrefix("invite:") }
    var body: some View {
        VStack(alignment: .leading, spacing: FoodSpacing.s14) {
            HStack(alignment: .top, spacing: FoodSpacing.s12) {
                if member {
                    Text(String(card.title.prefix(1))).font(FoodTypography.avatarMedium)
                        .foregroundStyle(FoodTheme.available).frame(width: FoodSpacing.s40, height: FoodSpacing.s40)
                        .background(FoodTheme.successWash, in: Circle()).accessibilityHidden(true)
                } else if card.id.hasPrefix("session:") || card.id.hasPrefix("restaurant:") || card.id == "empty" {
                    GroupIcon(symbol: card.id.hasPrefix("session:") ? "person.2" : "fork.knife")
                }
                VStack(alignment: .leading, spacing: FoodSpacing.s6) {
                    Text(card.title).font(FoodTypography.bodyBold).foregroundStyle(FoodTheme.ink)
                    if !card.badge.isEmpty {
                        Text(card.badge).font(FoodTypography.status).foregroundStyle(FoodTheme.orange)
                            .padding(.horizontal, FoodSpacing.s8).padding(.vertical, FoodSpacing.s4)
                            .background(FoodTheme.accentWash, in: Capsule())
                    }
                    if member && !card.detail.isEmpty { detail }
                }
                Spacer(minLength: FoodSpacing.s0)
            }
            if !member && !card.detail.isEmpty { detail }
            if !card.image.isEmpty, let marker = card.image.range(of: "base64,"), let data = Data(base64Encoded: String(card.image[marker.upperBound...])), let photo = UIImage(data: data) {
                Image(uiImage: photo).resizable().scaledToFit().frame(maxHeight: 600).accessibilityLabel(card.title)
            }
            if !card.buttons.isEmpty {
                if card.buttons.count > 1 { FoodDivider() }
                ForEach(card.buttons, id: \.renderID) { button in
                    GroupActionContent(button: button, busy: busy, dispatch: dispatch)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(FoodSpacing.s20).foodCard(showsBorder: true)
    }
    private var detail: some View {
        Text(card.detail).font(FoodTypography.setting).foregroundStyle(FoodTheme.muted)
            .lineSpacing(FoodSpacing.s4).fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
    }
}

struct GroupProgress: View {
    let step: Int
    var rtl = false
    var body: some View {
        HStack(alignment: .top, spacing: FoodSpacing.s8) {
            ForEach(Array(GroupText.shared.progressSteps.enumerated()), id: \.offset) { index, title in
                VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                    Capsule().fill(index <= step ? FoodTheme.available : FoodTheme.line).frame(height: FoodSpacing.s4)
                    Text(GroupText.shared.localized(value: title, rtl: rtl)).font(FoodTypography.status).foregroundStyle(index == step ? FoodTheme.ink : FoodTheme.muted)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.vertical, FoodSpacing.s12)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(rtl ? "الخطوة \(step + 1) من \(GroupText.shared.progressSteps.count): \(GroupText.shared.localized(value: GroupText.shared.progressSteps[step], rtl: true))" : "Step \(step + 1) of \(GroupText.shared.progressSteps.count): \(GroupText.shared.progressSteps[step])")
    }
}

extension GroupButton {
    var viewID: String { "\(action.name):\(value)" }
    // Kotlin data objects are reference types in Swift. Include visible state in the
    // diffing identity so SwiftUI does not retain an old title or enabled state.
    var renderID: String { "\(viewID):\(title):\(primary):\(destructive):\(enabled)" }
    var symbol: String {
        if destructive { return "trash" }
        switch action {
        case .create, .createRoom, .theNewRestaurant, .addMenuItem, .addCartItem: return "plus"
        case .join, .joinRoom, .resume: return "person.2"
        case .quickSpin, .prepareSpin: return "arrow.triangle.2.circlepath"
        case .openLibrary, .openItem, .selectRestaurant: return "fork.knife"
        case .openReceipts, .shareReceipt: return "doc.text"
        case .openHistory: return "clock.arrow.circlepath"
        case .favoriteOrder, .removeFavoriteOrder: return "star.fill"
        case .reuseOrder: return "cart.badge.plus"
        case .shareRoom, .exportMenu, .shareRestaurantOrder, .shareOrderWhatsapp: return "square.and.arrow.up"
        case .scan: return "qrcode.viewfinder"
        case .useInternet: return "globe"
        case .discover, .connect: return "wifi"
        case .ready, .confirmQuote, .saveRestaurant, .acceptDuty, .selectTaxTreatment: return "checkmark"
        case .editRestaurant, .editRoomRestaurant: return "pencil"
        case .openAccount, .shareAccount, .declareTransfer: return "creditcard"
        case .importMenu: return "square.and.arrow.down"
        default: return "arrow.right"
        }
    }
}

extension GroupCard {
    // Keep account and receipt details out of the identity while still invalidating
    // a row whenever its rendered content or actions change.
    var renderID: String {
        "\(id):\(title):\(detail.hashValue):\(image.hashValue):\(badge):\(buttons.map(\.renderID).joined(separator: "|"))"
    }
}
