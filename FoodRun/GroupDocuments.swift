import UIKit
import UniformTypeIdentifiers
import FoodRunShared

final class GroupDocuments: NSObject, UIDocumentPickerDelegate {
    private var documentCallback: GroupReplyCallback?
    private let fileQueue = DispatchQueue(label: "com.karim.foodrun.documents", qos: .userInitiated)

    func share(text: String, fileName: String) {
        guard !fileName.isEmpty else { presentShare(text); return }
        fileQueue.async { [weak self] in
            do {
                let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                let url = folder.appendingPathComponent(URL(fileURLWithPath: fileName).lastPathComponent)
                try Data(text.utf8).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                DispatchQueue.main.async { self?.presentShare(url, temporaryFolder: folder) }
            } catch {
                DispatchQueue.main.async { Self.showError("The export could not be saved. Check device storage and retry.") }
            }
        }
    }

    private func presentShare(_ item: Any, temporaryFolder: URL? = nil) {
        guard let root = Self.presenter else { return }
        let sheet = UIActivityViewController(activityItems: [item], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = root.view
        sheet.popoverPresentationController?.sourceRect = CGRect(
            x: root.view.bounds.midX,
            y: root.view.bounds.midY,
            width: FoodSpacing.s1,
            height: FoodSpacing.s1
        )
        sheet.completionWithItemsHandler = { _, _, _, error in
            if let temporaryFolder { try? FileManager.default.removeItem(at: temporaryFolder) }
            if error != nil { Self.showError("Sharing failed. Try exporting again.") }
        }
        root.present(sheet, animated: true)
    }

    func importMenu(_ callback: GroupReplyCallback) {
        guard documentCallback == nil, let root = Self.presenter else {
            callback.complete(body: "", error: "Close the current sheet and try importing again.")
            return
        }
        documentCallback = callback
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText], asCopy: true)
        picker.delegate = self
        root.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let callback = documentCallback else { return }
        documentCallback = nil
        guard let url = urls.first else {
            callback.complete(body: "", error: "No menu was selected.")
            return
        }
        fileQueue.async {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let text = try Self.menuText(at: url)
                DispatchQueue.main.async { callback.complete(body: text, error: "") }
            } catch {
                DispatchQueue.main.async {
                    callback.complete(body: "", error: "Could not read the menu. Use a UTF-8 JSON file under 2 MB.")
                }
            }
        }
    }

    static func menuText(at url: URL) throws -> String {
        let maximum = 2 * 1_024 * 1_024
        let properties = try url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
        guard properties.isRegularFile == true, let size = properties.fileSize, size <= maximum else {
            throw CocoaError(.fileReadTooLarge)
        }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let data = try handle.read(upToCount: maximum + 1) ?? Data()
        guard data.count <= maximum, let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        return text
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let callback = documentCallback
        documentCallback = nil
        callback?.complete(body: "", error: "Menu import cancelled. Your library is unchanged.")
    }

    func scanPairing(_ callback: GroupReplyCallback) {
        guard let root = Self.presenter else {
            callback.complete(body: "", error: "The scanner cannot open. Paste the server pairing link instead.")
            return
        }
        let scanner = GroupQrScanner { result in
            callback.complete(
                body: result ?? "",
                error: result == nil ? "Camera unavailable or scan cancelled. Paste the server pairing link instead." : ""
            )
        }
        root.present(scanner, animated: true)
    }

    static func openLink(_ text: String) {
        guard let url = URL(string: text), ["tel", "https"].contains(url.scheme?.lowercased() ?? "") else {
            showError("This restaurant contact link is not supported.")
            return
        }
        UIApplication.shared.open(url) { opened in
            if !opened { Self.showError("This device cannot open the restaurant contact. Copy its phone number instead.") }
        }
    }

    private static func showError(_ message: String) {
        let alert = UIAlertController(title: GroupText.shared.brand, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: FoodStrings.text.done, style: .default))
        presenter?.present(alert, animated: true)
    }

    static var presenter: UIViewController? {
        var root = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .flatMap(\.windows).first { $0.isKeyWindow }?.rootViewController
        while let presented = root?.presentedViewController { root = presented }
        return root
    }
}
