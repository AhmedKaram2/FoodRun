import SwiftUI
import PhotosUI
import IosComponents
import FoodRunShared

struct GroupFieldContent: View {
    let field: GroupField
    let enabled: Bool
    let onChange: (String) -> Void
    @State private var photoError = ""
    @State private var selectedPhoto: PhotosPickerItem?
    @Environment(\.layoutDirection) private var layoutDirection
    private func translated(_ value: String) -> String {
        GroupText.shared.localized(value: value, rtl: layoutDirection == .rightToLeft)
    }

    var body: some View {
        if field.key == .photo {
            VStack(spacing: FoodSpacing.s14) {
                if let image = profileImage {
                    Image(uiImage: image).resizable().scaledToFill().frame(width: 88, height: 88).clipShape(Circle())
                        .accessibilityLabel(translated("Selected profile photo"))
                }
                Text(translated(field.value.isEmpty ? "Add a profile photo" : "Profile photo selected"))
                    .font(FoodTypography.setting).foregroundStyle(FoodTheme.ink)
                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    Label(translated(field.value.isEmpty ? "Choose from Photos" : "Change photo"), systemImage: "photo.on.rectangle")
                        .font(FoodTypography.button).frame(maxWidth: .infinity, minHeight: FoodSpacing.s48)
                        .foregroundStyle(.white).background(FoodTheme.orange, in: RoundedRectangle(cornerRadius: FoodRadius.input))
                }.disabled(!enabled)
                if !photoError.isEmpty { Text(translated(photoError)).foregroundStyle(FoodTheme.orange).accessibilityAddTraits(.isStaticText) }
                if !field.value.isEmpty {
                    Button(role: .destructive) { onChange("") } label: { Text(translated("Remove photo")).frame(maxWidth: .infinity) }
                        .disabled(!enabled)
                }
            }
            .padding(FoodSpacing.s16).foodCard(showsBorder: true)
            .onChange(of: selectedPhoto) { _, photo in
                guard let photo else { return }
                Task {
                    guard let data = try? await photo.loadTransferable(type: Data.self), data.count <= 10 * 1_024 * 1_024,
                          let source = UIImage(data: data), let encoded = resizedPhoto(source) else { await MainActor.run { photoError = "Choose a readable photo under 10 MB." }; return }
                    await MainActor.run { photoError = ""; onChange("data:image/jpeg;base64," + encoded.base64EncodedString()) }
                }
            }
        } else if field.key == .quantity {
            Stepper(value: Binding(get: { min(99, max(1, Int(field.value) ?? 1)) }, set: { onChange(String($0)) }), in: 1...99) {
                Text("\(field.label): \(field.value)").font(FoodTypography.setting)
            }.padding(FoodSpacing.s16).foodCard(showsBorder: true).disabled(!enabled)
                .accessibilityIdentifier(field.key.name)
        } else if !field.choices.isEmpty {
            Picker(field.label, selection: Binding(get: { field.value }, set: onChange)) {
                ForEach(field.choices, id: \.value) { choice in
                    Text(choice.label).tag(choice.value)
                }
            }.pickerStyle(.menu).padding(FoodSpacing.s16).foodCard(showsBorder: true)
                .disabled(!enabled).accessibilityIdentifier(field.key.name)
        } else if field.toggle {
            Toggle(field.label, isOn: Binding(
                get: { field.value == "true" },
                set: { onChange($0 ? "true" : "false") }
            ))
            .font(FoodTypography.setting)
            .foregroundStyle(FoodTheme.ink)
            .padding(FoodSpacing.s16)
            .foodCard(showsBorder: true)
            .disabled(!enabled)
            .accessibilityIdentifier(field.key.name)
        } else {
            VStack(alignment: .leading, spacing: FoodSpacing.s8) {
                Text(field.label).font(FoodTypography.setting).foregroundStyle(FoodTheme.ink)
                if field.secret {
                    SecureField(field.label, text: Binding(get: { field.value }, set: onChange))
                        .textContentType(.password).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .padding(FoodSpacing.s16).foodCard(showsBorder: true).disabled(!enabled)
                        .accessibilityIdentifier(field.key.name)
                } else { input }
            }
        }
    }

    private var profileImage: UIImage? {
        guard field.value.hasPrefix("data:image/"), let marker = field.value.range(of: "base64,"),
              let data = Data(base64Encoded: String(field.value[marker.upperBound...])) else { return nil }
        return UIImage(data: data)
    }

    private func resizedPhoto(_ source: UIImage) -> Data? {
        let side = min(source.size.width, source.size.height)
        guard side > 0 else { return nil }
        let crop = CGRect(x: (source.size.width - side) / 2, y: (source.size.height - side) / 2, width: side, height: side)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 256, height: 256))
        let image = renderer.image { _ in source.draw(in: CGRect(x: -crop.minX * 256 / side, y: -crop.minY * 256 / side, width: source.size.width * 256 / side, height: source.size.height * 256 / side)) }
        return image.jpegData(compressionQuality: 0.78)
    }

    private var input: some View {
        BaseTextInputField(
            label: "",
            placeholder: field.label,
            keyboardType: keyboard,
            imeAction: .done,
            capitalization: .none,
            backgroundColor: FoodTheme.white,
            borderColor: FoodTheme.line,
            font: FoodTypography.input,
            singleLine: !field.multiline,
            enabled: enabled,
            readOnly: false,
            textColor: FoodTheme.ink,
            maxLines: field.multiline ? 6 : 1,
            minHeight: FoodSpacing.s0,
            error: nil,
            endIconImage: nil,
            startIconImage: nil,
            iconSize: FoodSpacing.s18,
            iconTintColor: FoodTheme.muted,
            shape: RoundedRectangle(cornerRadius: FoodRadius.input),
            allowDigitsOnly: false,
            text: field.value,
            maxLen: field.key == .jsonMenu ? 2 * 1_024 * 1_024 : 4_000,
            onValueChange: onChange,
            contentPadding: FoodSpacing.s16,
            borderWidth: FoodBorder.thin,
            errorColor: FoodTheme.orange,
            errorFont: FoodTypography.captionButton,
            inputIdentifier: field.key.name,
            onSubmit: {
                UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
            }
        )
        .privacySensitive(field.secret)
    }

    private var keyboard: UIKeyboardType {
        switch field.key {
        case .hubUrl, .pairingLink: .URL
        case .phone, .profilePhone: .phonePad
        case .email: .emailAddress
        case .jsonMenu, .fingerprint, .accountIdentifier: .asciiCapable
        // Bill adjustments accept a leading minus; decimalPad has no minus key.
        case .billAdjustment: .numbersAndPunctuation
        case .amount, .deliveryFee, .serviceFee, .discount, .menuItemPrice, .taxRate, .minimumOrder: .decimalPad
        case .quantity, .roomCode: .numberPad
        default: .default
        }
    }
}
