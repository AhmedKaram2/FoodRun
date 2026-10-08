import Foundation
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseAuth
import GoogleSignIn
import FirebaseMessaging
import FoodRunShared

/// The shared controller calls this adapter on the UI thread; callbacks return there too.
final class GroupIosPlatform: NSObject, GroupPlatform, UNUserNotificationCenterDelegate, MessagingDelegate {
    var onNotification: ((String, String) -> Void)? {
        didSet { if let pending = pendingNotification, let onNotification { pendingNotification = nil; onNotification(pending.0, pending.1) } }
    }
    private var pendingNotification: (String, String)?
    private var pushCallback: GroupReplyCallback?
    private var pushGeneration = 0
    private var apnsObservers: [NSObjectProtocol] = []
    private var googleSignInPending = false
    func googleSignIn(callback: GroupReplyCallback) {
        guard !googleSignInPending else { callback.complete(body: "", error: "Finish the current Google sign-in first."); return }
        guard let clientID = FirebaseApp.app()?.options.clientID,
              let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap(\.windows).first(where: \.isKeyWindow),
              var presenter = window.rootViewController else {
            callback.complete(body: "", error: "Open FoodRun before signing in with Google."); return
        }
        while let shown = presenter.presentedViewController { presenter = shown }
        googleSignInPending = true
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        let finish: (String, String) -> Void = { [weak self] token, error in
            DispatchQueue.main.async {
                guard let self, self.googleSignInPending else { return }
                self.googleSignInPending = false
                callback.complete(body: token, error: error)
            }
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            guard error == nil, let user = result?.user, let idToken = user.idToken?.tokenString else {
                let cancelled = (error as NSError?)?.code == GIDSignInError.canceled.rawValue
                finish("", cancelled ? "Google sign-in was cancelled. Try again." : "Google sign-in could not finish. Please try again.")
                return
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: user.accessToken.tokenString)
            Auth.auth().signIn(with: credential) { result, error in
                guard error == nil, let user = result?.user else {
                    finish("", "Could not connect this Google account. Please try again."); return
                }
                user.getIDTokenForcingRefresh(true) { token, error in
                    guard error == nil, let token, !token.isEmpty else {
                        finish("", "Could not complete account sign-in. Please try again."); return
                    }
                    finish(token, "")
                }
            }
        }
    }
    func accountSignedOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }
    private let storage = GroupSecureStorage()
    private let documents = GroupDocuments()
    private let discovery = GroupDiscovery()
    private var requests: [UUID: PinnedHubSession] = [:]

    override init() {
        super.init()
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        let ar = Locale.preferredLanguages.first?.hasPrefix("ar") == true
        func action(_ id: String, _ en: String, _ arabic: String) -> UNNotificationAction { UNNotificationAction(identifier: id, title: ar ? arabic : en, options: [.foreground]) }
        let open = action("open", "Open room", "فتح الغرفة")
        UNUserNotificationCenter.current().setNotificationCategories([
            UNNotificationCategory(identifier: "FOODRUN_ORDER", actions: [action("order", "Review & send order", "مراجعة وإرسال الطلب"), action("copy", "Copy order", "نسخ الطلب"), action("share", "Share order", "مشاركة الطلب")], intentIdentifiers: []),
            UNNotificationCategory(identifier: "FOODRUN_CONFIRM", actions: [action("confirm", "Payment received", "استلمت الدفع"), open], intentIdentifiers: []),
            UNNotificationCategory(identifier: "FOODRUN_PAY", actions: [action("pay", "Payment sent", "أرسلت الدفع"), open], intentIdentifiers: []),
            UNNotificationCategory(identifier: "FOODRUN_OPEN", actions: [open], intentIdentifiers: [])
        ])
        apnsObservers = [
            NotificationCenter.default.addObserver(forName: .init("FoodRunAPNsReady"), object: nil, queue: .main) { [weak self] _ in self?.fetchPushToken() },
            NotificationCenter.default.addObserver(forName: .init("FoodRunAPNsFailed"), object: nil, queue: .main) { [weak self] _ in self?.finishPush("", "Push registration failed. Check the app signing and internet connection.") }
        ]
    }

    func adminRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) {
        let id = UUID(), transport = PinnedHubSession(hub: hub)
        requests[id] = transport
        transport.command(body, path: "/admin/native") { [weak self] body, error in
            self?.requests.removeValue(forKey: id); callback.complete(body: body, error: error)
        }
    }
    func notificationRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) {
        let id = UUID(), transport = PinnedHubSession(hub: hub)
        requests[id] = transport
        transport.command(body, path: "/notifications") { [weak self] body, error in
            self?.requests.removeValue(forKey: id); callback.complete(body: body, error: error)
        }
    }
    func pushToken(prompt: Bool, callback: GroupReplyCallback) {
        if !prompt && !UserDefaults.standard.bool(forKey: "foodrun-push-enabled") { callback.complete(body: "", error: ""); return }
        pushCallback?.complete(body: "", error: "Notification registration restarted.")
        pushGeneration += 1
        let generation = pushGeneration
        pushCallback = callback
        let begin = { [weak self] in
            DispatchQueue.main.async {
                guard let self, self.pushGeneration == generation, self.pushCallback != nil else { return }
                Messaging.messaging().isAutoInitEnabled = true
                if Messaging.messaging().apnsToken != nil { self.fetchPushToken() }
                else { UIApplication.shared.registerForRemoteNotifications() }
                DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in
                    if self?.pushGeneration == generation && self?.pushCallback != nil { self?.finishPush("", "Push registration timed out. Try again on a signed iPhone build.") }
                }
            }
        }
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional { begin() }
            else if prompt {
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { allowed, _ in
                    if allowed { begin() } else { DispatchQueue.main.async { if self?.pushGeneration == generation { self?.finishPush("", "Enable notifications in iPhone settings.") } } }
                }
            } else { DispatchQueue.main.async { if self?.pushGeneration == generation { self?.finishPush("", "") } } }
        }
    }
    private func fetchPushToken() {
        guard pushCallback != nil else { return }
        let generation = pushGeneration
        Messaging.messaging().token { [weak self] token, error in
            DispatchQueue.main.async {
                guard let self, self.pushGeneration == generation, self.pushCallback != nil else { return }
                guard let token, error == nil else { self.finishPush("", "Notifications could not be enabled. Try again."); return }
                let installation = UserDefaults.standard.string(forKey: "foodrun-push-installation") ?? UUID().uuidString
                UserDefaults.standard.set(installation, forKey: "foodrun-push-installation")
                UserDefaults.standard.set(true, forKey: "foodrun-push-enabled")
                let data = try? JSONSerialization.data(withJSONObject: ["token": token, "platform": "ios", "installationId": installation])
                self.finishPush(data.flatMap { String(data: $0, encoding: .utf8) } ?? "", "")
            }
        }
    }
    private func finishPush(_ body: String, _ error: String) { let callback = pushCallback; pushCallback = nil; callback?.complete(body: body, error: error) }
    func disablePush() {
        pushGeneration += 1
        finishPush("", "")
        UserDefaults.standard.set(false, forKey: "foodrun-push-enabled")
        Messaging.messaging().isAutoInitEnabled = false
        Messaging.messaging().deleteToken { _ in }
        UIApplication.shared.unregisterForRemoteNotifications()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken, UserDefaults.standard.bool(forKey: "foodrun-push-enabled"),
            let raw = read(key: "group-library-v1").data(using: .utf8),
            let library = (try? JSONSerialization.jsonObject(with: raw)) as? [String: Any],
            let identity = library["identityToken"] as? String, !identity.isEmpty,
            let pairing = library["identityHub"] as? [String: String], let url = pairing["url"],
            let installation = UserDefaults.standard.string(forKey: "foodrun-push-installation") else { return }
        let body = try? JSONSerialization.data(withJSONObject: ["identityToken": identity, "action": "register", "token": token, "platform": "ios", "installationId": installation, "language": library["language"] as? String ?? "en"])
        guard let body, let text = String(data: body, encoding: .utf8) else { return }
        let id = UUID(), transport = PinnedHubSession(hub: HubPairing(url: url, fingerprint: pairing["fingerprint"] ?? ""))
        requests[id] = transport
        transport.command(text, path: "/notifications") { [weak self] _, _ in self?.requests.removeValue(forKey: id) }
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if let id = response.notification.request.content.userInfo["notificationId"] as? String {
            let action = response.actionIdentifier == UNNotificationDefaultActionIdentifier ? "open" : response.actionIdentifier
            DispatchQueue.main.async { [weak self] in
                self?.receiveNotification(id: id, action: action)
            }
        }
        completionHandler()
    }
    func receiveNotification(id: String, action: String) {
        guard id.range(of: "^[a-f0-9]{40}$", options: .regularExpression) != nil else { return }
        if let handler = onNotification { handler(id, action) } else { pendingNotification = (id, action) }
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
    func schedule(delayMillis: Int64, callback: GroupScheduledCallback) { DispatchQueue.main.asyncAfter(deadline: .now() + Double(delayMillis) / 1_000) { callback.run() } }
    func localOffsetSeconds(timeMillis: Int64) -> Int32 {
        Int32(TimeZone.current.secondsFromGMT(for: Date(timeIntervalSince1970: Double(timeMillis) / 1_000)))
    }
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

    deinit {
        apnsObservers.forEach(NotificationCenter.default.removeObserver)
        requests.values.forEach { $0.session.invalidateAndCancel() }
    }
}
