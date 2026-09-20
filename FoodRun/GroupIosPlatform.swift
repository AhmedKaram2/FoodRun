import Foundation
import UIKit
import UserNotifications
import FoodRunShared

/// The shared controller calls this adapter on the UI thread; callbacks return there too.
final class GroupIosPlatform: NSObject, GroupPlatform, UNUserNotificationCenterDelegate {
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
