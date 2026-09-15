// Adapted from the user-supplied IosComponents BaseTextInputField.
import SwiftUI

public struct BaseTextInputField: View {
    private let text: String
    private let maxLen: Int
    private let label: String
    private let placeholder: String
    private let keyboardType: UIKeyboardType
    private let imeAction: UIReturnKeyType
    private let capitalization: UITextAutocapitalizationType
    private let backgroundColor: Color
    private let borderColor: Color
    private let font: Font
    private let singleLine: Bool
    private let maxLines: Int
    private let enabled: Bool
    private let readOnly: Bool
    private let textColor: Color
    private let minHeight: CGFloat
    private let error: String?
    private let endIconImage: Image?
    private let startIconImage: Image?
    private let iconSize: CGFloat
    private let iconTintColor: Color
    private let shape: RoundedRectangle
    private let allowDigitsOnly: Bool
    private let contentPadding: CGFloat
    private let borderWidth: CGFloat
    private let errorColor: Color
    private let errorFont: Font
    private let textContentType: UITextContentType?
    private let focus: FocusState<Bool>.Binding?
    private let inputIdentifier: String
    private let errorIdentifier: String
    private let onValueChange: (String) -> Void
    private let onSubmit: () -> Void
    private let onClick: (() -> Void)?
    @FocusState private var localFocus: Bool

    public init(
        label: String,
        placeholder: String,
        keyboardType: UIKeyboardType,
        imeAction: UIReturnKeyType,
        capitalization: UITextAutocapitalizationType,
        backgroundColor: Color,
        borderColor: Color,
        font: Font,
        singleLine: Bool,
        enabled: Bool,
        readOnly: Bool,
        textColor: Color,
        maxLines: Int,
        minHeight: CGFloat,
        error: String?,
        endIconImage: Image?,
        startIconImage: Image?,
        iconSize: CGFloat,
        iconTintColor: Color,
        shape: RoundedRectangle,
        allowDigitsOnly: Bool,
        text: String,
        maxLen: Int,
        onValueChange: @escaping (String) -> Void,
        onClick: (() -> Void)? = nil,
        contentPadding: CGFloat = 0,
        borderWidth: CGFloat = 1,
        errorColor: Color = .red,
        errorFont: Font = .callout,
        textContentType: UITextContentType? = nil,
        focus: FocusState<Bool>.Binding? = nil,
        inputIdentifier: String = "",
        errorIdentifier: String = "",
        onSubmit: @escaping () -> Void = {}
    ) {
        self.label = label
        self.placeholder = placeholder
        self.keyboardType = keyboardType
        self.imeAction = imeAction
        self.capitalization = capitalization
        self.backgroundColor = backgroundColor
        self.borderColor = borderColor
        self.font = font
        self.singleLine = singleLine
        self.maxLines = maxLines
        self.enabled = enabled
        self.readOnly = readOnly
        self.textColor = textColor
        self.minHeight = minHeight
        self.error = error
        self.endIconImage = endIconImage
        self.startIconImage = startIconImage
        self.iconSize = iconSize
        self.iconTintColor = iconTintColor
        self.shape = shape
        self.allowDigitsOnly = allowDigitsOnly
        self.text = text
        self.maxLen = maxLen
        self.contentPadding = contentPadding
        self.borderWidth = borderWidth
        self.errorColor = errorColor
        self.errorFont = errorFont
        self.textContentType = textContentType
        self.focus = focus
        self.inputIdentifier = inputIdentifier
        self.errorIdentifier = errorIdentifier
        self.onValueChange = onValueChange
        self.onSubmit = onSubmit
        self.onClick = onClick
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !label.isEmpty {
                Text(label)
                    .foregroundColor(.secondary)
                    .font(.callout)
            }
            HStack {
                if let startIconImage {
                    startIconImage
                        .font(.system(size: iconSize))
                        .foregroundColor(iconTintColor)
                }
                TextField(placeholder, text: controlledText)
                    .keyboardType(keyboardType)
                    .autocapitalization(capitalization)
                    .autocorrectionDisabled()
                    .textContentType(textContentType)
                    .foregroundColor(textColor)
                    .font(font)
                    .lineLimit(maxLines)
                    .multilineTextAlignment(singleLine ? .leading : .center)
                    .frame(minHeight: minHeight)
                    .disabled(!enabled || readOnly)
                    .focused(focus ?? $localFocus)
                    .submitLabel(imeAction == .done ? .done : .return)
                    .onSubmit(onSubmit)
                    .accessibilityLabel(label.isEmpty ? placeholder : label)
                    .accessibilityIdentifier(inputIdentifier)
                if let endIconImage {
                    endIconImage
                        .font(.system(size: iconSize))
                        .foregroundColor(iconTintColor)
                }
            }
            .padding(contentPadding)
            .background(backgroundColor, in: shape)
            .overlay(shape.strokeBorder(borderColor, lineWidth: borderWidth))
            if let error {
                Text(error)
                    .foregroundColor(errorColor)
                    .font(errorFont)
                    .accessibilityIdentifier(errorIdentifier)
            }
        }
        .onTapGesture { onClick?() }
    }

    private var controlledText: Binding<String> {
        Binding(
            get: { text },
            set: { newValue in
                let filtered = allowDigitsOnly ? newValue.filter(\.isNumber) : newValue
                onValueChange(String(filtered.prefix(max(0, maxLen))))
            }
        )
    }
}
