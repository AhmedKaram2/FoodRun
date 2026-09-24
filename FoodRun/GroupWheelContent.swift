import SwiftUI
import FoodRunShared

struct GroupWheelContent: View {
    let wheel: GroupWheel
    private let people: [FoodRun.Person]
    @Environment(\.layoutDirection) private var direction
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
            Group {
            if wheel.style == "names" {
                let index = Int(wheel.round.runningNameIndex(now: now))
                let ar = direction == .rightToLeft
                VStack(spacing: 18) {
                    Text(ended ? "✓" : "✦").font(.system(size: 36)).foregroundStyle(Color.orange)
                    if !reduceMotion && !ended { Text(wheel.names[(index + wheel.names.count - 1) % wheel.names.count]).font(.title3).opacity(0.3).lineLimit(1) }
                    Text(reduceMotion && !ended ? (ar ? "جارٍ الاختيار…" : "Choosing…") : wheel.names[index])
                        .font(.system(size: 30, weight: .black)).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, minHeight: 85).padding(16)
                        .background(.white, in: RoundedRectangle(cornerRadius: 18))
                        .id(reduceMotion ? "static" : wheel.names[index])
                        .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity), removal: .move(edge: .top).combined(with: .opacity)))
                    if !reduceMotion && !ended { Text(wheel.names[(index + 1) % wheel.names.count]).font(.title3).opacity(0.3).lineLimit(1) }
                    Text(ended ? (ar ? "مسؤول الطلب" : "Selected to order") : (ar ? "من سيطلب؟" : "Who will order?"))
                }
                .padding(24).frame(maxWidth: .infinity)
                .background(LinearGradient(colors: [.orange.opacity(0.12), .orange.opacity(0.24)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 28))
                .clipped().animation(reduceMotion ? nil : .easeOut(duration: 0.1), value: index)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(ar ? "الأسماء المتحركة" : "Running names")
                .accessibilityValue(ended ? wheel.winner : (ar ? "جارٍ الاختيار" : "Choosing"))
            } else {
            WheelView(
                people: people,
                rotation: wheel.round.rotation(now: reduceMotion && !ended ? wheel.round.startAt : now),
                isSpinning: now >= wheel.round.startAt && !ended,
                tick: ended || reduceMotion ? 0 : Int(now / 90),
                winner: ended ? people.first { $0.name == wheel.winner } : nil,
                weights: wheel.round.weights.map { $0.doubleValue }
            )
            }
            }
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
