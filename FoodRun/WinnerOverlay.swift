import SwiftUI

struct WinnerOverlay: View {
    let person: Person
    let reduceMotion: Bool
    let dismiss: () -> Void
    @State private var appeared = false
    @AccessibilityFocusState private var titleFocused: Bool

    var body: some View {
        ZStack {
            FoodTheme.ink.opacity(0.44)
                .ignoresSafeArea()
                .accessibilityHidden(true)
            if !reduceMotion {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            ScrollView(showsIndicators: false) {
                VStack(spacing: FoodSpacing.s0) {
                    HStack(spacing: FoodSpacing.s6) {
                        Image(systemName: "sparkles")
                        Text(FoodStrings.text.winnerEyebrow)
                            .tracking(1.6)
                    }
                    .font(FoodTypography.eyebrow)
                    .foregroundStyle(FoodTheme.orange)
                    .padding(.bottom, FoodSpacing.s24)

                    ZStack {
                        Circle()
                            .fill(FoodTheme.color(for: person).opacity(0.48))
                            .frame(width: FoodSpacing.s112, height: FoodSpacing.s112)
                        Image(systemName: "takeoutbag.and.cup.and.straw.fill")
                            .font(FoodTypography.winnerIcon)
                            .foregroundStyle(FoodTheme.ink)
                            .rotationEffect(.degrees(appeared ? -8 : 8))
                        Image(systemName: "star.fill")
                            .font(FoodTypography.starIcon)
                            .foregroundStyle(FoodTheme.orange)
                            .offset(x: 52, y: -37)
                        Image(systemName: "sparkle")
                            .font(FoodTypography.sparkleIcon)
                            .foregroundStyle(FoodTheme.orange)
                            .offset(x: -57, y: 19)
                    }
                    .padding(.bottom, FoodSpacing.s24)

                    Text(FoodStrings.text.winnerIntroduction)
                        .font(FoodTypography.winnerIntro)
                        .foregroundStyle(FoodTheme.muted)
                        .padding(.bottom, FoodSpacing.s4)
                    Text(FoodStrings.text.winnerTitle(name: person.name))
                        .font(FoodTypography.winner)
                        .tracking(-2)
                        .foregroundStyle(FoodTheme.ink)
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                        .accessibilityIdentifier("winnerName")
                        .accessibilityFocused($titleFocused)
                        .accessibilityAddTraits(.isHeader)
                    Text(FoodStrings.text.winnerDescription)
                        .font(FoodTypography.subtitle)
                        .foregroundStyle(FoodTheme.muted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(5)
                        .padding(.top, FoodSpacing.s12)

                    FoodDivider()
                        .padding(.vertical, FoodSpacing.s25)

                    PrimaryButton(title: FoodStrings.text.winnerAction, symbol: "checkmark", action: dismiss)
                        .accessibilityIdentifier("winnerDoneButton")
                    ShareLink(item: FoodStrings.text.winnerShare(name: person.name)) {
                        Label(FoodStrings.text.shareWithCrew, systemImage: "square.and.arrow.up")
                            .font(FoodTypography.share)
                            .foregroundStyle(FoodTheme.muted)
                            .frame(minHeight: FoodSpacing.s44)
                    }
                    .padding(.top, FoodSpacing.s8)
                }
                .padding(FoodSpacing.s28)
                .background(FoodTheme.cream, in: RoundedRectangle(cornerRadius: FoodRadius.winner))
                .overlay(RoundedRectangle(cornerRadius: FoodRadius.winner).strokeBorder(FoodTheme.white.opacity(0.8), lineWidth: FoodBorder.medium))
                .shadow(color: FoodTheme.black.opacity(0.14), radius: FoodSpacing.s30, y: 14)
                .scaleEffect(appeared || reduceMotion ? 1 : 0.82)
                .opacity(appeared ? 1 : 0)
                .padding(.horizontal, FoodSpacing.s28)
                .padding(.vertical, FoodSpacing.s50)
                .frame(maxWidth: FoodSpacing.s450)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)
        }
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, dismiss)
        .onAppear {
            withAnimation(reduceMotion ? .easeOut(duration: 0.15) : .spring(response: 0.55, dampingFraction: 0.66)) {
                appeared = true
            }
            titleFocused = true
        }
    }
}

private struct ConfettiView: View {
    @State private var start = Date()
    @State private var finished = false

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: finished)) { timeline in
            let elapsed = timeline.date.timeIntervalSince(start)
            Canvas { context, size in
                for index in 0..<85 {
                    let seed = Double(index)
                    let delay = Double(index % 9) * 0.045
                    let time = max(0, elapsed - delay)
                    let xStart = fraction(seed * 0.618033) * size.width
                    let drift = sin(seed * 2.4) * 85
                    let x = xStart + sin(time * 2 + seed) * 26 + drift * time * 0.2
                    let speed = 110 + fraction(seed * 0.713) * 140
                    let y = -35 + speed * time + 42 * time * time
                    guard elapsed >= delay, y < size.height + 30 else { continue }
                    var particle = context
                    particle.opacity = min(1, max(0, 4.5 - elapsed))
                    particle.translateBy(x: x, y: y)
                    particle.rotate(by: .radians(seed + time * (index.isMultiple(of: 2) ? 3 : -4)))
                    let width = 5 + fraction(seed * 0.37) * 5
                    let rect = CGRect(x: -width / 2, y: -4, width: width, height: index.isMultiple(of: 3) ? width : 13)
                    let path = index.isMultiple(of: 3) ? Path(ellipseIn: rect) : Path(roundedRect: rect, cornerRadius: 1.5)
                    particle.fill(path, with: .color(FoodTheme.palette[index % FoodTheme.palette.count]))
                }
            }
        }
        .task {
            try? await Task.sleep(for: .seconds(5))
            finished = true
        }
    }

    private func fraction(_ value: Double) -> Double { value - floor(value) }
}
