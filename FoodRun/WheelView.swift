import SwiftUI

struct WheelView: View {
    let people: [Person]
    let rotation: Double
    let isSpinning: Bool
    let tick: Int
    let winner: Person?
    var weights: [Double] = []
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size.width
            let wheelSize = size - 30
            ZStack {
                Circle()
                    .fill(FoodTheme.orange.opacity(0.07))
                    .frame(width: size + 10, height: size + 10)
                    .blur(radius: FoodSpacing.s18)

                Circle()
                    .fill(FoodTheme.ink)
                    .frame(width: size - 4, height: size - 4)
                    .offset(y: 7)
                    .shadow(color: FoodTheme.ink.opacity(0.14), radius: FoodSpacing.s14, y: 10)

                Circle()
                    .fill(FoodTheme.wheelRim)
                    .overlay(Circle().strokeBorder(FoodTheme.ink, lineWidth: FoodBorder.heavy))
                    .frame(width: size - 4, height: size - 4)

                rimDots(size: size)

                Canvas { context, canvasSize in
                    let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
                    let radius = canvasSize.width / 2
                    let shares = weights.count == people.count ? weights : Array(repeating: 1.0, count: max(people.count, 1))
                    let total = shares.reduce(0, +)
                    for (index, person) in people.enumerated() {
                        let slice = shares[index] * 360 / total
                        let middle = -90 + (shares.prefix(index).reduce(0, +) + shares[index] / 2 - shares[0] / 2) * 360 / total
                        var wedge = Path()
                        wedge.move(to: center)
                        wedge.addArc(center: center, radius: radius,
                                     startAngle: .degrees(middle - slice / 2),
                                     endAngle: .degrees(middle + slice / 2), clockwise: false)
                        wedge.closeSubpath()
                        context.fill(wedge, with: .color(FoodTheme.color(for: person)))
                        context.stroke(wedge, with: .color(FoodTheme.ink.opacity(0.65)), lineWidth: FoodBorder.wheelSlice)

                        var labelContext = context
                        labelContext.translateBy(x: center.x, y: center.y)
                        labelContext.rotate(by: .degrees(middle))
                        labelContext.translateBy(x: radius * 0.66, y: 0)
                        // Radial labels stay within their slice, even with the full crew.
                        labelContext.rotate(by: .degrees(180))
                        let label = person.wheelLabel
                        let text = Text(label)
                            .font(FoodTypography.wheelLabel(size: size))
                            .foregroundStyle(FoodTheme.ink)
                        let resolved = labelContext.resolve(text)
                        let measured = resolved.measure(in: CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
                        let availableHeight = radius * 0.66 * sin(min(slice, 180) * .pi / 360) * 1.6
                        let scale = min(1, radius * 0.53 / max(measured.width, 1), availableHeight / max(measured.height, 1))
                        labelContext.scaleBy(x: scale, y: scale)
                        labelContext.draw(resolved, at: .zero)
                    }
                }
                .frame(width: wheelSize, height: wheelSize)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(FoodTheme.ink, lineWidth: FoodBorder.wheelOutline))
                .rotationEffect(.degrees(rotation))

                Circle()
                    .fill(FoodTheme.ink.opacity(0.15))
                    .frame(width: FoodSpacing.s79, height: FoodSpacing.s79)
                    .offset(y: 4)
                Circle()
                    .fill(FoodTheme.cream)
                    .frame(width: FoodSpacing.s76, height: FoodSpacing.s76)
                    .overlay(Circle().strokeBorder(FoodTheme.ink, lineWidth: FoodBorder.heavy))
                VStack(spacing: FoodSpacing.s1) {
                    Image(systemName: "takeoutbag.and.cup.and.straw.fill")
                        .font(FoodTypography.centerIcon)
                    Text(FoodStrings.text.wheelCenter)
                        .font(FoodTypography.wheelCenter)
                        .tracking(1)
                }
                .foregroundStyle(FoodTheme.ink)

                PointerShape()
                    .fill(FoodTheme.orange)
                    .overlay(PointerShape().stroke(FoodTheme.ink, lineWidth: FoodBorder.heavy))
                    .frame(width: FoodSpacing.s30, height: FoodSpacing.s40)
                    .shadow(color: FoodTheme.ink.opacity(0.15), radius: FoodSpacing.s2, y: 3)
                    .rotationEffect(.degrees(isSpinning && tick % 2 == 0 && !reduceMotion ? -12 : 0), anchor: .top)
                    .animation(.spring(response: 0.12, dampingFraction: 0.5), value: tick)
                    .offset(y: -size / 2 + 7)
            }
            .frame(width: size, height: size)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(FoodStrings.text.wheelAccessibility)
        .accessibilityValue(FoodStrings.text.wheelState(
            isSpinning: isSpinning,
            winnerName: winner?.name,
            activeCount: Int32(people.count)
        ))
    }

    private func rimDots(size: CGFloat) -> some View {
        ForEach(0..<36, id: \.self) { index in
            Circle()
                .fill(FoodTheme.ink.opacity(index % 3 == 0 ? 0.6 : 0.2))
                .frame(width: FoodSpacing.s3, height: FoodSpacing.s3)
                .offset(y: -(size - 17) / 2)
                .rotationEffect(.degrees(Double(index) * 10))
        }
    }
}

private struct PointerShape: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.minX + 4, y: rect.minY))
            path.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.minY + 7),
                              control: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.midX - 4, y: rect.maxY - 3))
            path.addQuadCurve(to: CGPoint(x: rect.midX + 4, y: rect.maxY - 3),
                              control: CGPoint(x: rect.midX, y: rect.maxY + 3))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + 7))
            path.addQuadCurve(to: CGPoint(x: rect.maxX - 4, y: rect.minY),
                              control: CGPoint(x: rect.maxX, y: rect.minY))
            path.closeSubpath()
        }
    }
}
