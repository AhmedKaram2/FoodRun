import SwiftUI

struct FoodRunContent: View {
    let store: WheelStore
    let wheelWidth: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: FoodSpacing.s0) {
                navigationBar
                    .padding(.bottom, FoodSpacing.s16)
                introduction
                    .padding(.bottom, FoodSpacing.s20)
                wheelSection(width: wheelWidth)
                actionSection
                    .padding(.top, FoodSpacing.s16)
                crewCard
                    .padding(.top, FoodSpacing.s16)
                    .padding(.bottom, FoodSpacing.s24)
            }
            .padding(.horizontal, FoodSpacing.s24)
            .padding(.top, FoodSpacing.s8)
            .frame(maxWidth: FoodSpacing.s490)
            .frame(maxWidth: .infinity)
        }
    }

    private var navigationBar: some View {
        HStack(spacing: FoodSpacing.s10) {
            Image(systemName: "takeoutbag.and.cup.and.straw.fill")
                .font(FoodTypography.brandIcon)
                .foregroundStyle(FoodTheme.white)
                .frame(width: FoodSpacing.s38, height: FoodSpacing.s38)
                .background(FoodTheme.orange, in: RoundedRectangle(cornerRadius: FoodRadius.avatar))
                .rotationEffect(.degrees(-7))
            Text(FoodStrings.text.brand)
                .font(FoodTypography.brand)
                .tracking(2)
                .foregroundStyle(FoodTheme.ink)
            Spacer()
            Button(action: store.openHistory) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(FoodTypography.historyIcon)
                    .foregroundStyle(FoodTheme.ink)
                    .frame(width: FoodSpacing.s44, height: FoodSpacing.s44)
                    .background(FoodTheme.white.opacity(0.7), in: Circle())
                    .overlay(Circle().strokeBorder(FoodTheme.line, lineWidth: FoodBorder.thin))
            }
            .buttonStyle(PressableStyle())
            .disabled(store.isSpinning)
            .accessibilityLabel(FoodStrings.text.historyAccessibility)
            .accessibilityIdentifier("historyButton")
        }
    }

    private var introduction: some View {
        VStack(spacing: FoodSpacing.s10) {
            VStack(spacing: FoodSpacing.overlapSmall) {
                Text(FoodStrings.text.headlineFirst)
                    .foregroundStyle(FoodTheme.ink)
                Text(FoodStrings.text.headlineSecond)
                    .foregroundStyle(FoodTheme.orange)
            }
            .font(FoodTypography.hero)
            .tracking(-1.8)
            .multilineTextAlignment(.center)
            .accessibilityElement(children: .combine)
            Text(FoodStrings.text.tagline)
                .font(FoodTypography.subtitle)
                .foregroundStyle(FoodTheme.muted)
        }
    }

    private func wheelSection(width: CGFloat) -> some View {
        VStack(spacing: FoodSpacing.s14) {
            ZStack(alignment: .topTrailing) {
                WheelView(
                    people: store.activePeople,
                    rotation: store.rotation,
                    isSpinning: store.isSpinning,
                    tick: store.tick,
                    winner: store.winner
                )
                    .frame(width: width, height: width)
                Image(systemName: "sparkle")
                    .font(FoodTypography.largeSparkle)
                    .foregroundStyle(FoodTheme.orange)
                    .offset(x: 7, y: 0)
                    .accessibilityHidden(true)
                Image(systemName: "sparkle")
                    .font(FoodTypography.smallSparkle)
                    .foregroundStyle(FoodTheme.sage)
                    .offset(x: -width + 9, y: width - 38)
                    .accessibilityHidden(true)
            }

            HStack(spacing: FoodSpacing.s7) {
                Circle()
                    .fill(store.isSpinning ? FoodTheme.orange : FoodTheme.available)
                    .frame(width: FoodSpacing.s6, height: FoodSpacing.s6)
                Text(store.statusLabel)
                    .font(FoodTypography.status)
                    .contentTransition(.numericText())
            }
            .foregroundStyle(FoodTheme.muted)
            .padding(.horizontal, FoodSpacing.s13)
            .padding(.vertical, FoodSpacing.s8)
            .background(FoodTheme.white.opacity(0.75), in: Capsule())
            .overlay(Capsule().strokeBorder(FoodTheme.line.opacity(0.8), lineWidth: FoodBorder.thin))
            .accessibilityIdentifier("wheelStatus")
        }
    }

    private var actionSection: some View {
        VStack(spacing: FoodSpacing.s12) {
            PrimaryButton(
                title: store.spinButtonLabel,
                symbol: store.isSpinning ? "sparkles" : "arrow.triangle.2.circlepath",
                isDisabled: !store.state.canSpin
            ) {
                store.spin(reduceMotion: reduceMotion)
            }
            .accessibilityIdentifier("spinButton")
            Text(store.missionLabel)
                .font(FoodTypography.footer)
                .foregroundStyle(FoodTheme.muted)
        }
    }

    private var crewCard: some View {
        Button(action: store.openCrew) {
            HStack(spacing: FoodSpacing.s12) {
                HStack(spacing: FoodSpacing.overlapAvatars) {
                    ForEach(store.state.crewPreview, id: \.id) { person in
                        FoodAvatar(
                            person: Person(person),
                            size: .small
                        )
                    }
                }
                VStack(alignment: .leading, spacing: FoodSpacing.s4) {
                    Text(FoodStrings.text.crewTitle)
                        .font(FoodTypography.crewTitle)
                        .foregroundStyle(FoodTheme.ink)
                    Text(store.crewSummary)
                        .font(FoodTypography.metadata)
                        .foregroundStyle(FoodTheme.muted)
                }
                Spacer(minLength: FoodSpacing.s4)
                Text(FoodStrings.text.crewAction)
                    .font(FoodTypography.crewAction)
                    .foregroundStyle(FoodTheme.orange)
                Image(systemName: "chevron.right")
                    .font(FoodTypography.chevron)
                    .foregroundStyle(FoodTheme.orange)
            }
            .padding(FoodSpacing.s16)
            .frame(maxWidth: .infinity)
            .foodCard(
                opacity: 0.62,
                showsBorder: true
            )
        }
        .buttonStyle(PressableStyle())
        .disabled(store.isSpinning)
        .accessibilityLabel(store.crewAccessibility)
        .accessibilityIdentifier("crewButton")
    }
}
