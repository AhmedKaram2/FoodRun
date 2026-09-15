import Foundation
import FoodRunShared

/// Lightweight Swift identity adapters; values and labels originate in shared Kotlin.
struct Person: Identifiable, Equatable, Codable {
    let id: Int
    let name: String
    static let crew = FoodRunRules.shared.defaultCrew.map(Person.init)
    static func normalizedName(_ name: String) -> String { FoodRunRules.shared.normalizedName(name: name) }
    var initial: String { shared.initial }
    var wheelLabel: String { shared.wheelLabel }
    private var shared: FoodRunShared.Person { FoodRunShared.Person(id: Int32(clamping: id), name: name) }
    init(id: Int, name: String) { self.id = id; self.name = name }
    init(_ person: FoodRunShared.Person) { id = Int(person.id); name = person.name }
}

struct Pickup: Identifiable {
    let id: String
    let person: Person
    let date: Date
    let dateLabel: String
    init(_ item: PickupHistoryItem) {
        id = item.id
        person = Person(item.person)
        date = Date(timeIntervalSince1970: item.pickup.timeMillis / 1_000)
        dateLabel = item.dateLabel
    }
}

struct SpinPlan {
    private let shared: FoodRunShared.SpinPlan
    var winnerIndex: Int { Int(shared.winnerIndex) }
    var startRotation: Double { shared.startRotation }
    var endRotation: Double { shared.endRotation }
    init(winnerIndex: Int, count: Int, startRotation: Double, turns: Int, landingOffset: Double) {
        shared = FoodRunShared.SpinPlan(winnerIndex: Int32(winnerIndex), count: Int32(count), startRotation: startRotation, turns: Int32(turns), landingOffset: landingOffset)
    }
    static func normalized(_ angle: Double) -> Double { FoodRunShared.SpinPlan.companion.normalized(angle: angle) }
    static func indexAtPointer(rotation: Double, count: Int) -> Int {
        Int(FoodRunShared.SpinPlan.companion.indexAtPointer(rotation: rotation, count: Int32(count)))
    }
    func rotation(at progress: Double) -> Double { shared.rotationAt(progress: progress) }
}
