import Foundation
import FoodRunShared

/// Migrates the original Swift preferences into the shared schema. Legacy keys stay intact.
final class IosFoodRunStorage: NSObject, FoodRunStorage {
    private let defaults: UserDefaults
    static let stateKey = "foodRunStateV1"

    init(defaults: UserDefaults) { self.defaults = defaults }

    func read() -> String? {
        if let saved = defaults.string(forKey: Self.stateKey) { return saved }
        let custom = defaults.data(forKey: "customPeople")
            .flatMap { try? JSONDecoder().decode([Person].self, from: $0) } ?? []
        let history = defaults.data(forKey: "pickupHistory")
            .flatMap { try? JSONDecoder().decode([LegacyPickup].self, from: $0) } ?? []
        let snapshot: [String: Any] = [
            "version": 1,
            "customPeople": custom.filter { Int32(exactly: $0.id) != nil }.map { ["id": $0.id, "name": $0.name] },
            "activeIds": (defaults.array(forKey: "activeIDs") as? [Int]) ?? (Person.crew + custom).map(\.id),
            "hapticsEnabled": defaults.object(forKey: "hapticsEnabled") as? Bool ?? true,
            "history": history.map { ["id": $0.id, "person": ["id": $0.person.id, "name": $0.person.name], "timeMillis": $0.date.timeIntervalSince1970 * 1_000] }
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: snapshot) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func write(value: String) { defaults.set(value, forKey: Self.stateKey) } 
}

private struct LegacyPickup: Decodable {
    let id: String
    let person: Person
    let date: Date
}
