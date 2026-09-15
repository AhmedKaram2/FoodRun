import XCTest
import FoodRunShared
@testable import FoodRun
private typealias Person = FoodRun.Person
private typealias SpinPlan = FoodRun.SpinPlan

final class WheelTests: XCTestCase {
    func testRequestedCrewIsExactAndOrdered() {
        XCTAssertEqual(Person.crew.map(\.name), ["Karim", "Karam", "Hassan", "Mersal", "Baraa", "Fayed", "Ayman", "Rayan", "Gaber", "Fakhr"])
        XCTAssertEqual(Set(Person.crew.map(\.id)).count, 10)
    }

    func testEveryPossibleWinnerLandsUnderThePointer() {
        for count in 1...30 {
            for index in 0..<count {
                for start in [0.0, 13.5, 359.9, 2871.25] {
                    for offset in [-0.28, 0, 0.28] {
                        let plan = SpinPlan(winnerIndex: index, count: count, startRotation: start, turns: 7, landingOffset: offset)
                        XCTAssertEqual(SpinPlan.indexAtPointer(rotation: plan.endRotation, count: count), index)
                        XCTAssertGreaterThanOrEqual(plan.endRotation - start, 7 * 360)
                    }
                }
            }
        }
    }

    func testSpinMovesForwardAndSlowsToAStop() {
        let plan = SpinPlan(winnerIndex: 5, count: 9, startRotation: 122, turns: 7, landingOffset: 0.1)
        let samples = (0...100).map { plan.rotation(at: Double($0) / 100) }
        let speeds = zip(samples, samples.dropFirst()).map { $1 - $0 }
        XCTAssertEqual(samples.first!, plan.startRotation)
        XCTAssertEqual(samples.last!, plan.endRotation)
        XCTAssertTrue(speeds.allSatisfy { $0 >= 0 })
        for (earlier, later) in zip(speeds, speeds.dropFirst()) {
            XCTAssertGreaterThanOrEqual(earlier + 0.000001, later)
        }
    }

    @MainActor
    func testRosterKeepsOnePersonAndRestoresSelection() throws {
        let defaults = try isolatedDefaults()
        let store = WheelStore(defaults: defaults)
        for person in Person.crew { store.toggle(person) }
        XCTAssertEqual(store.activePeople, [Person.crew.last!])
        store.toggle(Person.crew.last!)
        XCTAssertEqual(store.activePeople.count, 1)
        XCTAssertEqual(WheelStore(defaults: defaults).activePeople, store.activePeople)
        store.includeEveryone()
        XCTAssertEqual(store.activePeople, Person.crew)
    }

    @MainActor
    func testInvalidSavedRosterRecoversToFullCrew() throws {
        let defaults = try isolatedDefaults()
        defaults.set([999], forKey: "activeIDs")
        defaults.set(Data("invalid json".utf8), forKey: "pickupHistory")
        let store = WheelStore(defaults: defaults)
        XCTAssertEqual(store.activePeople, Person.crew)
        XCTAssertTrue(store.history.isEmpty)
    }

    @MainActor
    func testSinglePersonSpinCannotBeRestartedAndPersistsTheWinner() async throws {
        let defaults = try isolatedDefaults()
        let store = WheelStore(defaults: defaults)
        for person in Person.crew.dropFirst() { store.toggle(person) }
        store.spin(reduceMotion: true)
        store.spin(reduceMotion: true)
        store.toggle(Person.crew[1])
        XCTAssertThrowsError(try store.addPerson(named: "Omar")) {
            XCTAssertEqual($0 as? AddPersonError, .spinInProgress)
        }
        XCTAssertTrue(store.isSpinning)
        XCTAssertEqual(store.activePeople, [Person.crew[0]])
        for _ in 0..<60 {
            if !store.isSpinning { break }
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertFalse(store.isSpinning)
        XCTAssertEqual(store.winner, Person.crew[0])
        XCTAssertEqual(store.currentPerson, store.winner)
        XCTAssertTrue(store.showWinner)
        XCTAssertEqual(store.history.count, 1)
        XCTAssertEqual(WheelStore(defaults: defaults).history.first?.person, Person.crew[0])
    }

    @MainActor
    func testAddedPeopleAreIncludedAndRestoreWithExistingChoices() throws {
        let defaults = try isolatedDefaults()
        // Upgrade a roster saved by the original app, including a sitting-out friend.
        defaults.set(Person.crew.dropFirst().map(\.id), forKey: "activeIDs")
        let store = WheelStore(defaults: defaults)
        let added = try store.addPerson(named: "  Ahmed \n Ali  ")
        XCTAssertEqual(added.name, "Ahmed Ali")
        XCTAssertTrue(store.activePeople.contains(added))
        XCTAssertFalse(store.activeIDs.contains(Person.crew[0].id))
        let restored = WheelStore(defaults: defaults)
        XCTAssertEqual(restored.people, Person.crew + [added])
        XCTAssertEqual(restored.activeIDs, store.activeIDs)
        restored.toggle(added)
        XCTAssertFalse(WheelStore(defaults: defaults).activeIDs.contains(added.id))
        restored.includeEveryone()
        XCTAssertEqual(restored.activePeople, restored.people)
        let second = try restored.addPerson(named: "Omar")
        XCTAssertNotEqual(second.id, added.id)
        XCTAssertEqual(WheelStore(defaults: defaults).people.suffix(2), [added, second])
    }

    @MainActor
    func testBlankDuplicateAndLongNamesDoNotChangeTheRoster() throws {
        let defaults = try isolatedDefaults()
        let store = WheelStore(defaults: defaults)
        try store.addPerson(named: "Ahmed Ali")
        let before = store.people
        for (name, expected) in [
            (" \n\t ", AddPersonError.emptyName),
            ("  kARiM ", .duplicateName),
            ("AHMED   ALI", .duplicateName),
            (String(repeating: "a", count: 33), .nameTooLong)
        ] {
            XCTAssertThrowsError(try store.addPerson(named: name)) {
                XCTAssertEqual($0 as? AddPersonError, expected)
            }
        }
        XCTAssertEqual(store.people, before)
        XCTAssertEqual(WheelStore(defaults: defaults).people, before)
    }

    @MainActor
    func testNewPersonCanWinAndIsSavedInHistory() async throws {
        let defaults = try isolatedDefaults()
        let store = WheelStore(defaults: defaults)
        let added = try store.addPerson(named: "Omar")
        for person in Person.crew { store.toggle(person) }
        store.spin(reduceMotion: true)
        for _ in 0..<60 {
            if !store.isSpinning { break }
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertFalse(store.isSpinning)
        XCTAssertEqual(store.winner, added)
        XCTAssertEqual(store.currentPerson, added)
        XCTAssertEqual(WheelStore(defaults: defaults).history.first?.person, added)
    }

    @MainActor
    func testInvalidCustomEntriesDoNotBreakRestoration() throws {
        let defaults = try isolatedDefaults()
        let entries = [
            Person(id: -1, name: "Negative"), Person(id: 0, name: "Reused"),
            Person(id: 10, name: "Omar"), Person(id: 10, name: "Another Omar"),
            Person(id: 11, name: "KARIM"), Person(id: 12, name: " "),
            Person(id: 13, name: "Ahmed")
        ]
        defaults.set(try JSONEncoder().encode(entries), forKey: "customPeople")
        let restored = WheelStore(defaults: defaults)
        XCTAssertEqual(restored.people, Person.crew + [entries[2], entries[6]])
        let added = try restored.addPerson(named: "Ali")
        XCTAssertEqual(added.id, 11)
        XCTAssertEqual(Set(restored.people.map(\.id)).count, restored.people.count)
    }

    @MainActor
    func testAddedPersonCanBeRemovedWithoutRemovingTheOriginalCrewOrLastParticipant() throws {
        let defaults = try isolatedDefaults()
        let store = WheelStore(defaults: defaults)
        let added = try store.addPerson(named: "Omar")
        store.removeAddedPerson(Person.crew[0])
        XCTAssertTrue(store.people.contains(Person.crew[0]))
        for person in Person.crew { store.toggle(person) }
        store.removeAddedPerson(added)
        XCTAssertEqual(store.activePeople, [added])
        store.toggle(Person.crew[0])
        store.removeAddedPerson(added)
        XCTAssertFalse(store.people.contains(added))
        let restored = WheelStore(defaults: defaults)
        XCTAssertEqual(restored.people, Person.crew)
        XCTAssertEqual(restored.activePeople, [Person.crew[0]])
    }

    private func isolatedDefaults() throws -> UserDefaults {
        let suite = "FoodRunTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        addTeardownBlock { defaults.removePersistentDomain(forName: suite) }
        return defaults
    }

    @MainActor
    func testLegacyUpgradePreservesCustomCrewHistoryDateAndHaptics() throws {
        let defaults = try isolatedDefaults()
        let custom = Person(id: 11, name: "Mohamed")
        let pickedAt = Date(timeIntervalSince1970: 1_789_464_600)
        // Literal legacy schema: Swift dates were seconds since 2001, not Unix milliseconds.
        let legacyHistory: [[String: Any]] = [[
            "id": "F3FC6D63-FF1D-4AC8-B5FD-18B64D1E51B3",
            "person": ["id": custom.id, "name": custom.name],
            "date": pickedAt.timeIntervalSinceReferenceDate
        ]]
        defaults.set(try JSONEncoder().encode([custom]), forKey: "customPeople")
        defaults.set([0, 11], forKey: "activeIDs")
        defaults.set(false, forKey: "hapticsEnabled")
        defaults.set(try JSONSerialization.data(withJSONObject: legacyHistory), forKey: "pickupHistory")
        let store = WheelStore(defaults: defaults)
        XCTAssertEqual(store.people.last, custom)
        XCTAssertEqual(store.activeIDs, [0, 11])
        XCTAssertEqual(store.history.first?.person, custom)
        XCTAssertEqual(store.history.first?.date, pickedAt)
        XCTAssertFalse(store.hapticsEnabled)
        // A normal edit writes the shared schema; reopening must no longer consult legacy keys.
        store.toggle(Person.crew[1])
        XCTAssertNotNil(defaults.string(forKey: IosFoodRunStorage.stateKey))
        let restored = WheelStore(defaults: defaults)
        XCTAssertEqual(restored.activeIDs, [0, 1, 11])
        XCTAssertEqual(restored.history.first?.date, pickedAt)
        XCTAssertFalse(restored.hapticsEnabled)
    }
}

enum AddPersonError: LocalizedError, Equatable {
    case emptyName, duplicateName, nameTooLong, spinInProgress
    init(_ error: FoodRunShared.AddPersonError) {
        switch error {
        case .emptyName: self = .emptyName
        case .duplicateName: self = .duplicateName
        case .nameTooLong: self = .nameTooLong
        default: self = .spinInProgress
        }
    }
    var errorDescription: String? {
        switch self {
        case .emptyName: FoodRunShared.AddPersonError.emptyName.message
        case .duplicateName: FoodRunShared.AddPersonError.duplicateName.message
        case .nameTooLong: FoodRunShared.AddPersonError.nameTooLong.message
        case .spinInProgress: FoodRunShared.AddPersonError.spinInProgress.message
        }
    }
}

@MainActor
private extension WheelStore {
    /// Test driver exercises the same shared form events as both native screens.
    @discardableResult func addPerson(named name: String) throws -> Person {
        if isSpinning { throw AddPersonError.spinInProgress }
        let previousDestination = state.destination
        openCrew()
        openAddPerson()
        updateNameDraft(name)
        submitName()
        if let error = state.nameError {
            let mapped = AddPersonError(error)
            dismiss()
            if previousDestination == .main { dismiss() }
            throw mapped
        }
        let added = try XCTUnwrap(people.last)
        XCTAssertFalse(addPersonPresented)
        XCTAssertNil(state.persistenceErrorMessage)
        if previousDestination == .main { dismiss() }
        return added
    }
}
