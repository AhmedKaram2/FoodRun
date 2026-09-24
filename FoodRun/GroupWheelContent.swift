import SwiftUI
import FoodRunShared

struct GroupWheelContent: View {
    let wheel: GroupWheel
    private let people: [FoodRun.Person]
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var finished = false
    @State private var clock: GroupSpinClock

    init(wheel: GroupWheel) {
        self.wheel = wheel
        people = wheel.names.enumerated().map { FoodRun.Person(id: $0.offset, name: $0.element) }
        _clock = State(initialValue: GroupSpinClock(
            serverMillis: Int64(Date().timeIntervalSince1970 * 1_000) + wheel.serverOffset,
            uptime: ProcessInfo.processInfo.systemUptime
        ))
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: reduceMotion ? 0.1 : 1.0 / 60, paused: finished)) { _ in
            let now = clock.now(uptime: ProcessInfo.processInfo.systemUptime)
            let ended = now >= wheel.round.endAt
            WheelView(
                people: people,
                rotation: wheel.round.rotation(now: reduceMotion && !ended ? wheel.round.startAt : now),
                isSpinning: now >= wheel.round.startAt && !ended,
                tick: ended || reduceMotion ? 0 : Int(now / 90),
                winner: ended ? people.first { $0.name == wheel.winner } : nil,
                weights: wheel.round.weights.map { $0.doubleValue }
            )
            .onChange(of: ended, initial: true) { _, ended in finished = ended }
        }
    }
}

/// Once a spin begins, changing the phone's wall clock cannot move the wheel backwards.
struct GroupSpinClock {
    let serverMillis: Int64
    let uptime: TimeInterval

    func now(uptime current: TimeInterval) -> Int64 {
        serverMillis + Int64(max(0, current - uptime) * 1_000)
    }
}
