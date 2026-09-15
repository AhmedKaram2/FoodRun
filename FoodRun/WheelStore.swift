import Foundation
import Observation
import UIKit
import FoodRunShared

/// Native lifecycle, haptics and animation frames around one observed KMP state.
@MainActor @Observable
final class WheelStore {
    private(set) var state: FoodRunState
    private(set) var rotation = 0.0
    private(set) var tick = 0
    @ObservationIgnored private let controller: FoodRunController
    @ObservationIgnored private var observation: FoodRunObservation?
    @ObservationIgnored private var spinTask: Task<Void, Never>?
    @ObservationIgnored private let tickFeedback = UISelectionFeedbackGenerator()
    @ObservationIgnored private let winFeedback = UINotificationFeedbackGenerator()

    var people: [Person] { state.people.map(Person.init) }
    var activePeople: [Person] { state.activePeople.map(Person.init) }
    var activeIDs: Set<Int> { Set(state.activePeople.map { Int($0.id) }) }
    var history: [Pickup] { state.historyItems.map(Pickup.init) }
    var winner: Person? { state.winner.map(Person.init) }
    var isSpinning: Bool { state.isSpinning }
    var showWinner: Bool { state.showWinner }
    var statusLabel: String { state.statusLabel }
    var spinButtonLabel: String { state.spinButtonLabel }
    var missionLabel: String { state.missionLabel }
    var crewSummary: String { state.crewSummary }
    var crewAccessibility: String { state.crewAccessibility }
    var nameDraft: String { state.nameDraft }
    var nameError: String? { state.nameErrorMessage }
    var canSubmitName: Bool { state.canSubmitName }
    var crewPresented: Bool { state.crewPresented }
    var addPersonPresented: Bool { state.addPersonPresented }
    var historyPresented: Bool { state.historyPresented }
    var hapticsEnabled: Bool {
        get { state.hapticsEnabled }
        set { controller.setHaptics(enabled: newValue) }
    }
    var currentPerson: Person? {
        guard !activePeople.isEmpty else { return nil }
        return activePeople[SpinPlan.indexAtPointer(rotation: rotation, count: activePeople.count)]
    }

    init(defaults: UserDefaults = .standard) {
        controller = FoodRunController(storage: IosFoodRunStorage(defaults: defaults), timeZone: IosFoodRunTimeZone())
        state = controller.state
        observation = controller.observe { [weak self] updated in
            guard let self else { return }
            if updated.wheelRevision != self.state.wheelRevision { self.rotation = 0 }
            self.state = updated
        }
    }

    deinit { observation?.cancel(); spinTask?.cancel() }

    func openCrew() { controller.openCrew() }
    func openHistory() { controller.openHistory() }
    func openAddPerson() { controller.openAddPerson() }
    func dismiss() { controller.dismiss() }
    func dismissError() { controller.dismissError() }
    func updateNameDraft(_ value: String) { controller.updateNameDraft(name: value) }
    @discardableResult func submitName() -> Bool {
        controller.submitName()
        return !state.addPersonPresented
    }
    func toggle(_ person: Person) {
        guard let id = Int32(exactly: person.id) else { return }
        controller.togglePerson(id: id)
    }
    func includeEveryone() { controller.includeEveryone() }
    func removeAddedPerson(_ person: Person) {
        guard let id = Int32(exactly: person.id) else { return }
        controller.removeAddedPerson(id: id)
    }

    func spin(reduceMotion: Bool = false) {
        guard let plan = controller.beginSpin(startRotation: rotation) else { return }
        let count = activePeople.count
        if hapticsEnabled { tickFeedback.prepare(); winFeedback.prepare() }
        spinTask = Task { [weak self] in
            let start = ProcessInfo.processInfo.systemUptime
            let duration = reduceMotion ? 0.45 : FoodRunRules.shared.spinDurationSeconds
            var previousIndex = SpinPlan.indexAtPointer(rotation: plan.startRotation, count: count)
            while !Task.isCancelled {
                let progress = min((ProcessInfo.processInfo.systemUptime - start) / duration, 1)
                guard let self else { return }
                if !reduceMotion {
                    self.rotation = plan.rotationAt(progress: progress)
                    let currentIndex = SpinPlan.indexAtPointer(rotation: self.rotation, count: count)
                    if currentIndex != previousIndex {
                        self.tick += 1
                        if self.hapticsEnabled { self.tickFeedback.selectionChanged() }
                        previousIndex = currentIndex
                    }
                }
                if progress >= 1 {
                    self.rotation = SpinPlan.normalized(plan.endRotation)
                    if self.controller.finishSpin(timeMillis: Date().timeIntervalSince1970 * 1_000) != nil {
                        if self.hapticsEnabled { self.winFeedback.notificationOccurred(.success) }
                        UIAccessibility.post(notification: .announcement, argument: self.state.winnerAnnouncement)
                    }
                    self.spinTask = nil
                    return
                }
                do { try await Task.sleep(for: .milliseconds(16)) }
                catch { self.controller.cancelSpin(); return }
            }
        }
    }
}

private final class IosFoodRunTimeZone: NSObject, FoodRunTimeZone {
    func offsetSecondsAt(timeMillis: Double) -> Int32 {
        Int32(TimeZone.current.secondsFromGMT(for: Date(timeIntervalSince1970: timeMillis / 1_000)))
    }
}
