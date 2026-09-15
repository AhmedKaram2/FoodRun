import Foundation
import FoodRunShared

/// The shared controller calls this adapter on the UI thread; callbacks return there too.
final class GroupIosPlatform: NSObject, GroupPlatform {
    private let storage = GroupSecureStorage()
    private let documents = GroupDocuments()
    private let discovery = GroupDiscovery()
    private var requests: [UUID: PinnedHubSession] = [:]

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

    func share(text: String, fileName: String) { documents.share(text: text, fileName: fileName) }
    func importMenu(callback: GroupReplyCallback) { documents.importMenu(callback) }
    func scanPairing(callback: GroupReplyCallback) { documents.scanPairing(callback) }
    func discover(callback: GroupReplyCallback) { discovery.discover(callback) }
    func openLink(url: String) { GroupDocuments.openLink(url) }

    deinit { requests.values.forEach { $0.session.invalidateAndCancel() } }
}
