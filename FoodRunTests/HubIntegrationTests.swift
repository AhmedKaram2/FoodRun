import XCTest
import FoodRunShared
@testable import FoodRun

/// Opt-in against the developer's local hub. No production account or restaurant is used.
final class HubIntegrationTests: XCTestCase {
    struct Configuration: Decodable {
        let url: String
        let fingerprint: String
        let roomCode: String?
    }

    @MainActor
    func testNativeHTTPSJoinAndWebSocketSnapshotReconnect() async throws {
        let config = try configuration()
        let hub = HubPairing(url: config.url, fingerprint: config.fingerprint)
        let platform = GroupIosPlatform()
        let roomCode: String
        if let configured = config.roomCode, !configured.isEmpty {
            roomCode = configured
        } else {
            roomCode = try await createTestRoom(platform, hub: hub)
        }
        let commandID = UUID().uuidString
        let command: [String: Any] = [
            "commandId": commandID, "kind": "JOIN", "code": roomCode,
            "name": "iOS Test \(commandID.prefix(8))", "guest": true
        ]
        let body = try json(command)
        let reply = try await request(platform, hub: hub, body: body)
        XCTAssertEqual(reply["ok"] as? Bool, true, "\(reply["error"] ?? "")")
        let token = try XCTUnwrap(reply["token"] as? String)
        let room = try XCTUnwrap(reply["room"] as? [String: Any])
        let roomID = try XCTUnwrap(room["id"] as? String)
        // Retrying the exact command must restore the same membership.
        let duplicate = try await request(platform, hub: hub, body: body)
        XCTAssertEqual(duplicate["token"] as? String, token)
        XCTAssertEqual(duplicate["memberId"] as? String, reply["memberId"] as? String)
        let watchBody = try json(["commandId": UUID().uuidString, "kind": "SNAPSHOT", "roomId": roomID, "token": token])
        for pass in 1...2 {
            let received = expectation(description: "WebSocket snapshot \(pass)")
            var completed = false
            let callback = ReplyCallback { body, error in
                guard !completed else { return }
                guard error.isEmpty else { return }
                completed = true
                XCTAssertTrue(Thread.isMainThread)
                let snapshot = try? JSONSerialization.jsonObject(with: Data(body.utf8)) as? [String: Any]
                XCTAssertEqual((snapshot?["room"] as? [String: Any])?["id"] as? String, roomID)
                received.fulfill()
            }
            let subscription = platform.watch(hub: hub, body: watchBody, callback: callback)
            await fulfillment(of: [received], timeout: 15)
            subscription.cancel()
            subscription.cancel()
        }
    }

    @MainActor
    private func createTestRoom(_ platform: GroupIosPlatform, hub: HubPairing) async throws -> String {
        let identifier = UUID().uuidString
        let restaurant: [String: Any] = [
            "id": identifier, "name": "iOS Transport Test Kitchen", "currency": "AED",
            "menu": [
                "categories": [["id": "mains", "name": "Mains"]],
                "items": [["id": "meal", "categoryId": "mains", "name": "Test meal", "basePriceMinor": 2_500]]
            ]
        ]
        let body = try json([
            "commandId": identifier, "kind": "CREATE", "name": "iOS Test Host",
            "text": "Native iOS test \(identifier.prefix(8))", "restaurant": restaurant
        ])
        let reply = try await request(platform, hub: hub, body: body)
        XCTAssertEqual(reply["ok"] as? Bool, true, "\(reply["error"] ?? "")")
        let room = try XCTUnwrap(reply["room"] as? [String: Any])
        return try XCTUnwrap(room["code"] as? String)
    }

    @MainActor
    func testNativeTransportRejectsIncorrectCertificatePin() async throws {
        let config = try configuration()
        let platform = GroupIosPlatform()
        let wrong = HubPairing(url: config.url, fingerprint: String(repeating: "0", count: 64))
        let result = await withCheckedContinuation { continuation in
            platform.request(hub: wrong, body: "{}", callback: ReplyCallback { body, error in
                continuation.resume(returning: (body, error))
            })
        }
        XCTAssertTrue(result.0.isEmpty)
        XCTAssertFalse(result.1.isEmpty)
    }

    private func configuration() throws -> Configuration {
        // Simulator-only developer configuration stays outside the app/test bundle.
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("HubIntegrationConfig.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Create ignored FoodRunTests/HubIntegrationConfig.json with url and fingerprint to test the local hub. roomCode is optional.")
        }
        return try JSONDecoder().decode(Configuration.self, from: Data(contentsOf: url))
    }

    @MainActor
    private func request(_ platform: GroupIosPlatform, hub: HubPairing, body: String) async throws -> [String: Any] {
        let result = await withCheckedContinuation { continuation in
            platform.request(hub: hub, body: body, callback: ReplyCallback { body, error in
                XCTAssertTrue(Thread.isMainThread)
                continuation.resume(returning: (body, error))
            })
        }
        XCTAssertEqual(result.1, "")
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(result.0.utf8)) as? [String: Any])
    }

    private func json(_ value: [String: Any]) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: value), as: UTF8.self)
    }
}

private final class ReplyCallback: GroupReplyCallback {
    let action: (String, String) -> Void
    init(_ action: @escaping (String, String) -> Void) { self.action = action }
    func complete(body: String, error: String) { action(body, error) }
}
