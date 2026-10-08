import XCTest
import CryptoKit
import Security
import FoodRunShared
@testable import FoodRun

final class GroupNativeTests: XCTestCase {
    @MainActor
    func testNotificationTapWaitsForTheControllerOnColdLaunch() {
        let platform = GroupIosPlatform()
        let id = String(repeating: "a", count: 40)
        platform.receiveNotification(id: id, action: "confirm")
        var received: [(String, String)] = []
        platform.onNotification = { received.append(($0, $1)) }
        XCTAssertEqual(received.count, 1)
        XCTAssertEqual(received.first?.0, id)
        XCTAssertEqual(received.first?.1, "confirm")
        platform.receiveNotification(id: "invalid", action: "open")
        XCTAssertEqual(received.count, 1)
    }
    func testGoogleAndRoomCallbacksAreRegisteredInTheBuiltApp() throws {
        let types = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]])
        let schemes = types.flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        XCTAssertTrue(schemes.contains("foodrun"))
        let url = try XCTUnwrap(Bundle.main.url(forResource:"GoogleService-Info",withExtension:"plist"))
        let configuration = try XCTUnwrap(NSDictionary(contentsOf:url))
        let clientID = try XCTUnwrap(configuration["CLIENT_ID"] as? String)
        let reversed = clientID.split(separator: ".").reversed().joined(separator: ".")
        XCTAssertTrue(schemes.contains(reversed))
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String,"FoodRun")
    }
    @MainActor
    func testDefaultPlatformPersistsReceiptUsingRealKeychain() throws {
        // Injected test keys cannot detect a missing application signing entitlement.
        XCTAssertEqual(try GroupSecureStorage.deviceKey().bitCount, 256)
        let name = "native-test-\(UUID().uuidString)"
        let directory = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("FoodRunGroups", isDirectory: true)
        let file = directory.appendingPathComponent(name + ".enc")
        addTeardownBlock {
            if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
        }
        let platform = GroupIosPlatform()
        XCTAssertTrue(platform.write(key: name, value: "Keychain-backed offline receipt"))
        XCTAssertEqual(GroupIosPlatform().read(key: name), "Keychain-backed offline receipt")
    }

    func testAnimationClockUsesMonotonicElapsedTime() {
        let clock = GroupSpinClock(serverMillis: 10_000, uptime: 50)
        XCTAssertEqual(clock.now(uptime: 50), 10_000)
        XCTAssertEqual(clock.now(uptime: 52.5), 12_500)
        XCTAssertEqual(clock.now(uptime: 49), 10_000)
    }

    func testAppDeclaresLocalDiscoveryAndCameraPurpose() {
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "NSBonjourServices") as? [String], ["_foodrun._tcp"])
        XCTAssertNotNil(Bundle.main.object(forInfoDictionaryKey: "NSLocalNetworkUsageDescription") as? String)
        XCTAssertNotNil(Bundle.main.object(forInfoDictionaryKey: "NSCameraUsageDescription") as? String)
    }

    func testEncryptedSnapshotRestoresAndTamperingCannotBecomeEmptyLibrary() throws {
        let directory = try temporaryDirectory()
        let key = SymmetricKey(size: .bits256)
        let store = GroupSecureStorage(directory: directory, keyProvider: { key })
        let value = "{\"receipt\":\"AED 105.00\",\"account\":\"private test account\"}"
        XCTAssertEqual(store.read("group-library-v1"), "")
        XCTAssertTrue(store.write(value, for: "group-library-v1"))
        let file = directory.appendingPathComponent("group-library-v1.enc")
        let encrypted = try Data(contentsOf: file)
        XCTAssertNil(String(data: encrypted, encoding: .utf8))
        XCTAssertEqual(GroupSecureStorage(directory: directory, keyProvider: { key }).read("group-library-v1"), value)
        var changed = encrypted
        changed[changed.count / 2] ^= 1
        try changed.write(to: file)
        XCTAssertEqual(store.read("group-library-v1"), "storage_unavailable")
    }

    func testWrongStorageKeyCannotReadReceiptAndInvalidPathsCannotWrite() throws {
        let directory = try temporaryDirectory()
        let key = SymmetricKey(size: .bits256)
        let store = GroupSecureStorage(directory: directory, keyProvider: { key })
        XCTAssertTrue(store.write("saved receipt", for: "receipt"))
        let wrongKey = GroupSecureStorage(directory: directory, keyProvider: { SymmetricKey(size: .bits256) })
        XCTAssertEqual(wrongKey.read("receipt"), "storage_unavailable")
        XCTAssertFalse(store.write("secret", for: "../outside"))
        XCTAssertFalse(store.write("secret", for: ""))
        XCTAssertEqual(store.read("receipt"), "saved receipt")
    }

    func testOversizedSnapshotDoesNotOverwriteSavedData() throws {
        let store = GroupSecureStorage(directory: try temporaryDirectory(), keyProvider: { SymmetricKey(data: Data(repeating: 7, count: 32)) })
        XCTAssertTrue(store.write("original", for: "receipt"))
        XCTAssertFalse(store.write(String(repeating: "x", count: GroupSecureStorage.maximumBytes + 1), for: "receipt"))
        XCTAssertEqual(store.read("receipt"), "original")
    }

    func testMenuReaderAllowsUTF8AndRejectsLargeBinaryAndDirectory() throws {
        let directory = try temporaryDirectory()
        let url = directory.appendingPathComponent("menu.json")
        let menu = "{\"name\":\"مطعم\"}"
        try Data(menu.utf8).write(to: url)
        XCTAssertEqual(try GroupDocuments.menuText(at: url), menu)
        try Data([0xff, 0xfe, 0xfd]).write(to: url)
        XCTAssertThrowsError(try GroupDocuments.menuText(at: url))
        try Data(repeating: 0, count: 2 * 1_024 * 1_024 + 1).write(to: url)
        XCTAssertThrowsError(try GroupDocuments.menuText(at: url))
        XCTAssertThrowsError(try GroupDocuments.menuText(at: directory))
    }

    func testTransportRejectsUnsafeEndpointsAndCreatesSecureWebSocket() {
        let fingerprint = String(repeating: "a", count: 64)
        for invalid in ["http://localhost:8443", "https://user:secret@localhost:8443", "https://localhost:8443/?token=secret", "https://localhost:8443/redirect", "https://localhost:0", "https://localhost:99999"] {
            XCTAssertNil(PinnedHubSession(hub: HubPairing(url: invalid, fingerprint: fingerprint)).endpoint("/command"))
        }
        let transport = PinnedHubSession(hub: HubPairing(url: "https://localhost:8443", fingerprint: fingerprint))
        XCTAssertEqual(transport.endpoint("/events", websocket: true)?.absoluteString, "wss://localhost:8443/events")
        XCTAssertNil(PinnedHubSession(hub: HubPairing(url: "https://localhost:8443", fingerprint: "bad")).endpoint("/command"))
    }

    @MainActor
    func testSharedObserverDoesNotRetainStore() {
        weak var weakStore: GroupStore?
        autoreleasepool {
            let store = GroupStore(platform: MemoryGroupPlatform())
            weakStore = store
            store.dispatch(.quickSpin)
            XCTAssertEqual(store.state.page, .quickSpin)
        }
        XCTAssertNil(weakStore)
    }

    private func temporaryDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: url) }
        return url
    }
}

private final class MemoryGroupPlatform: GroupPlatform {
    func accountSignedOut() {}
    func adminRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) { callback.complete(body: "", error: "Not configured in this fixture") }
    func disablePush() {}
    func googleSignIn(callback: GroupReplyCallback) { callback.complete(body: "", error: "Not configured in this fixture") }
    func localOffsetSeconds(timeMillis: Int64) -> Int32 { 0 }
    func notificationRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) { callback.complete(body: "", error: "Not configured in this fixture") }
    func pushToken(prompt: Bool, callback: GroupReplyCallback) { callback.complete(body: "", error: "") }
    func schedule(delayMillis: Int64, callback: GroupScheduledCallback) {}
    func notify(title: String, body: String) {}
    func enableNotifications() {}
    func read(key: String) -> String { "" }
    func write(key: String, value: String) -> Bool { true }
    func now() -> Int64 { 0 }
    func uuid() -> String { UUID().uuidString }
    func request(hub: HubPairing, body: String, callback: GroupReplyCallback) {}
    func watch(hub: HubPairing, body: String, callback: GroupReplyCallback) -> GroupSubscription { NoSubscription() }
    func share(text: String, fileName: String) {}
    func doCopyToClipboard(text: String) {}
    func importMenu(callback: GroupReplyCallback) {}
    func scanPairing(callback: GroupReplyCallback) {}
    func discover(callback: GroupReplyCallback) {}
    func openLink(url: String) {}
}

private final class NoSubscription: GroupSubscription { func cancel() {} }
