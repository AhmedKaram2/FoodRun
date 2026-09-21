import Foundation
import AuthenticationServices
import CryptoKit
import UIKit
import UserNotifications
import FoodRunShared

/// The shared controller calls this adapter on the UI thread; callbacks return there too.
final class GroupIosPlatform: NSObject, GroupPlatform, UNUserNotificationCenterDelegate, ASWebAuthenticationPresentationContextProviding {
    private var googleSession: ASWebAuthenticationSession?
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
    func googleSignIn(callback: GroupReplyCallback) {
        let verifier = (UUID().uuidString + UUID().uuidString).replacingOccurrences(of: "-", with: "")
        let challenge = SHA256.hash(data: Data(verifier.utf8)).map { String(format: "%02x", $0) }.joined()
        guard let url = URL(string: "https://intrvioo.com/?nativeSignIn=\(challenge)") else { return }
        googleSession = ASWebAuthenticationSession(url: url, callbackURLScheme: "foodrun") { [weak self] result, error in
            guard let result, error == nil,
                  result.scheme == "foodrun", result.host == "signin",
                  let items = URLComponents(url: result, resolvingAgainstBaseURL: false)?.queryItems,
                  items.first(where: { $0.name == "state" })?.value == challenge,
                  let code = items.first(where: { $0.name == "code" })?.value, code.count == 43 else {
                DispatchQueue.main.async { callback.complete(body: "", error: "Google sign-in was cancelled. Try again.") }
                return
            }
            var request = URLRequest(url: URL(string: "https://foodrun-api-q6b9.onrender.com/auth/native/exchange")!)
            request.httpMethod = "POST"; request.timeoutInterval = 20
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: ["code": code, "verifier": verifier])
            URLSession.shared.dataTask(with: request) { data, response, error in
                let object = data.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: String] }
                let token = object?["firebaseToken"] ?? ""
                let valid = error == nil && (response as? HTTPURLResponse)?.statusCode == 200 && !token.isEmpty
                DispatchQueue.main.async {
                    self?.googleSession = nil
                    callback.complete(body: valid ? token : "", error: valid ? "" : "Google sign-in could not finish. Start again in the app.")
                }
            }.resume()
        }
        googleSession?.presentationContextProvider = self
        googleSession?.prefersEphemeralWebBrowserSession = false
        if googleSession?.start() != true { callback.complete(body: "", error: "Could not open Google sign-in. Try again.") }
    }
    private let storage = GroupSecureStorage()
    private let documents = GroupDocuments()
    private let discovery = GroupDiscovery()
    private var requests: [UUID: PinnedHubSession] = [:]

    override init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
    }

    func enableNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }
    func notify(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title; content.body = body; content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
    func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1_000) }
    func uuid() -> String { UUID().uuidString }
    func read(key: String) -> String { storage.read(key) }
    func write(key: String, value: String) -> Bool { storage.write(value, for: key) }

    func request(hub: HubPairing, body: String, callback: GroupReplyCallback) {
        let id = UUID()
        let transport = PinnedHubSession(hub: hub)
        requests[id] = transport
        transport.command(body) { [weak self] body, error in
            self?.requests.removeValue(forKey: id)
            callback.complete(body: body, error: error)
        }
    }

    func watch(hub: HubPairing, body: String, callback: GroupReplyCallback) -> GroupSubscription {
        HubWatch(hub: hub, body: body, callback: callback)
    }

    func copyToClipboard(text: String) {
        UIPasteboard.general.string = text
    }

    func share(text: String, fileName: String) { documents.share(text: text, fileName: fileName) }
    func doCopyToClipboard(text: String) { UIPasteboard.general.string = text }
    func importMenu(callback: GroupReplyCallback) { documents.importMenu(callback) }
    func scanPairing(callback: GroupReplyCallback) { documents.scanPairing(callback) }
    func discover(callback: GroupReplyCallback) { discovery.discover(callback) }
    func openLink(url: String) { GroupDocuments.openLink(url) }

    deinit { requests.values.forEach { $0.session.invalidateAndCancel() } }
}
