import Foundation
import CoreGraphics
import ImageIO

// Reproducible vector artwork, rendered at App Store resolution.
let side = 1024
let context = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8,
                        bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.setAllowsAntialiasing(true)
func color(_ hex: UInt32) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255, alpha: 1)
}
func circle(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat, _ hex: UInt32) {
    context.setFillColor(color(hex))
    context.fillEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
}
context.setFillColor(color(0xEA5B2A))
context.fill(CGRect(x: 0, y: 0, width: side, height: side))
circle(512, 486, 373, 0xBA4320)
circle(512, 512, 373, 0x29251F)
circle(512, 512, 363, 0xFFF9EF)
let palette: [UInt32] = [0xF49A79, 0xF5CB69, 0xBAD4AD, 0xB8CBEB, 0xCEBAE4, 0xF2B4BD, 0xEAB88A, 0xCADA85, 0x8DCBC3, 0xE7C896]
for index in 0..<10 {
    let middle = Double.pi / 2 - Double(index) * 2 * Double.pi / 10
    context.beginPath()
    context.move(to: CGPoint(x: 512, y: 512))
    context.addArc(center: CGPoint(x: 512, y: 512), radius: 337,
                   startAngle: middle - Double.pi / 10, endAngle: middle + Double.pi / 10, clockwise: false)
    context.closePath()
    context.setFillColor(color(palette[index]))
    context.setStrokeColor(color(0x29251F))
    context.setLineWidth(3)
    context.drawPath(using: .fillStroke)
}
for index in 0..<36 {
    let angle = Double(index) * Double.pi / 18
    circle(512 + cos(angle) * 352, 512 + sin(angle) * 352, 3, 0x82796D)
}
circle(512, 506, 101, 0x29251F)
circle(512, 512, 95, 0xFFF9EF)
context.setFillColor(color(0x29251F))
context.addPath(CGPath(roundedRect: CGRect(x: 468, y: 466, width: 88, height: 84), cornerWidth: 12, cornerHeight: 12, transform: nil))
context.fillPath()
context.setStrokeColor(color(0x29251F))
context.setLineWidth(9)
context.addArc(center: CGPoint(x: 512, y: 547), radius: 22, startAngle: 0, endAngle: .pi, clockwise: false)
context.strokePath()
context.setStrokeColor(color(0xFFF9EF))
context.setLineWidth(5)
context.move(to: CGPoint(x: 495, y: 504))
context.addQuadCurve(to: CGPoint(x: 529, y: 504), control: CGPoint(x: 512, y: 483))
context.strokePath()
context.beginPath()
context.move(to: CGPoint(x: 480, y: 914))
context.addLine(to: CGPoint(x: 544, y: 914))
context.addQuadCurve(to: CGPoint(x: 550, y: 902), control: CGPoint(x: 553, y: 914))
context.addLine(to: CGPoint(x: 519, y: 826))
context.addQuadCurve(to: CGPoint(x: 505, y: 826), control: CGPoint(x: 512, y: 812))
context.addLine(to: CGPoint(x: 474, y: 902))
context.addQuadCurve(to: CGPoint(x: 480, y: 914), control: CGPoint(x: 471, y: 914))
context.closePath()
context.setFillColor(color(0xFFF9EF))
context.setStrokeColor(color(0x29251F))
context.setLineWidth(7)
context.drawPath(using: .fillStroke)
let image = context.makeImage()!
let url = URL(fileURLWithPath: "FoodRun/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(destination, image, nil)
precondition(CGImageDestinationFinalize(destination))
print("Generated 1024 × 1024 Food Run icon")
