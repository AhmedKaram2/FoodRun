// Generate the architecture SVG/PNG and video title cards using macOS AppKit.
// From the repository root: swift Scripts/GenerateShowcase.swift
import AppKit
import CoreText

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let media = root.appendingPathComponent("Docs/media")
let work = root.appendingPathComponent(".build/showcase")
try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
try FileManager.default.createDirectory(at: media, withIntermediateDirectories: true)

let cream = "FFF8EE", ink = "29261F", orange = "EF602E", muted = "827C70", pale = "FCE6D6", green = "617E53"

func color(_ hex: String) -> NSColor {
    let n = UInt32(hex, radix: 16)!
    return NSColor(srgbRed: CGFloat((n >> 16) & 255) / 255, green: CGFloat((n >> 8) & 255) / 255, blue: CGFloat(n & 255) / 255, alpha: 1)
}
func escaped(_ text: String) -> String {
    text.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
}

final class Canvas {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1920, pixelsHigh: 1080, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1920\" height=\"1080\" viewBox=\"0 0 1920 1080\" role=\"img\" aria-labelledby=\"title desc\"><title id=\"title\">Food Run architecture</title><desc id=\"desc\">Native iOS and Android apps share Kotlin state, domain rules and protocol. Native adapters connect them to a local JVM hub with encrypted SQLite record bodies.</desc>"
    init() {
        NSGraphicsContext.saveGraphicsState()
        let context = NSGraphicsContext(bitmapImageRep: bitmap)!
        NSGraphicsContext.current = context
        context.cgContext.translateBy(x: 0, y: 1080)
        context.cgContext.scaleBy(x: 1, y: -1)
        rect(0, 0, 1920, 1080, cream, radius: 0)
    }
    func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ fill: String, radius: CGFloat = 24) {
        color(fill).setFill()
        NSBezierPath(roundedRect: NSRect(x: x, y: y, width: w, height: h), xRadius: radius, yRadius: radius).fill()
        svg += "<rect x=\"\(x)\" y=\"\(y)\" width=\"\(w)\" height=\"\(h)\" rx=\"\(radius)\" fill=\"#\(fill)\"/>"
    }
    func text(_ value: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat = 30, _ fill: String = ink, bold: Bool = false) {
        let font = NSFont.systemFont(ofSize: size, weight: bold ? .bold : .regular)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color(fill)]
        let context = NSGraphicsContext.current!.cgContext
        context.saveGState()
        context.translateBy(x: x, y: y + font.ascender)
        context.scaleBy(x: 1, y: -1)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: attributes))
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
        svg += "<text x=\"\(x)\" y=\"\(y + font.ascender)\" font-family=\"Arial, sans-serif\" font-size=\"\(size)\" font-weight=\"\(bold ? 700 : 400)\" fill=\"#\(fill)\">\(escaped(value))</text>"
    }
    func arrow(_ x: CGFloat, _ y: CGFloat, _ endX: CGFloat, _ endY: CGFloat) {
        let angle = atan2(endY - y, endX - x)
        let path = NSBezierPath()
        path.move(to: NSPoint(x: x, y: y)); path.line(to: NSPoint(x: endX, y: endY))
        path.move(to: NSPoint(x: endX - 14 * cos(angle - 0.5), y: endY - 14 * sin(angle - 0.5)))
        path.line(to: NSPoint(x: endX, y: endY))
        path.line(to: NSPoint(x: endX - 14 * cos(angle + 0.5), y: endY - 14 * sin(angle + 0.5)))
        path.lineWidth = 4; color(orange).setStroke(); path.stroke()
        svg += "<path d=\"M \(x) \(y) L \(endX) \(endY) M \(endX - 14 * cos(angle - 0.5)) \(endY - 14 * sin(angle - 0.5)) L \(endX) \(endY) L \(endX - 14 * cos(angle + 0.5)) \(endY - 14 * sin(angle + 0.5))\" fill=\"none\" stroke=\"#\(orange)\" stroke-width=\"4\"/>"
    }
    func finish(_ destination: URL, vector: Bool = false) throws {
        try bitmap.representation(using: .png, properties: [:])!.write(to: destination.appendingPathExtension("png"))
        if vector { try (svg + "</svg>\n").write(to: destination.appendingPathExtension("svg"), atomically: true, encoding: .utf8) }
        NSGraphicsContext.restoreGraphicsState()
    }
}

let a = Canvas()
a.text("FOOD RUN  /  ARCHITECTURE", 80, 48, 24, orange, bold: true)
a.text("One shared core. Two native apps.", 80, 99, 66, ink, bold: true)
a.text("Permanent rooms on your local network. Native UI on every phone.", 80, 182, 30, muted)
a.rect(80, 262, 440, 154, "FFFFFF")
a.text("iOS", 110, 286, 36, ink, bold: true)
a.text("SwiftUI + IosComponents", 110, 337, 25)
a.text("Keychain · URLSession · native motion", 110, 375, 19, muted)
a.rect(560, 262, 440, 154, "FFFFFF")
a.text("Android", 590, 286, 36, ink, bold: true)
a.text("Jetpack Compose + components", 590, 337, 25)
a.text("Keystore · OkHttp · native motion", 590, 375, 19, muted)
a.arrow(300, 423, 300, 467); a.arrow(780, 423, 780, 467)
a.rect(80, 480, 920, 174, pale)
a.text("shared / Kotlin Multiplatform", 110, 506, 37, ink, bold: true)
a.text("State · actions · navigation · validation · offline cache", 110, 561, 27)
a.text("GroupController + GroupPlatform native service boundary", 110, 608, 23, muted)
a.arrow(300, 665, 300, 711); a.arrow(780, 665, 780, 711)
a.rect(80, 728, 440, 160, "FFFFFF")
a.text("order-domain", 110, 755, 32, ink, bold: true)
a.text("Menu · money · billing", 110, 805, 25)
a.text("Rules + shared spin curve", 110, 847, 22, muted)
a.rect(560, 728, 440, 160, "FFFFFF")
a.text("order-contract", 590, 755, 32, ink, bold: true)
a.text("Commands · replies · pairing", 590, 805, 25)
a.text("Protocol v1 · Kotlin serialization", 590, 847, 22, muted)
a.rect(1110, 262, 730, 126, pale)
a.text("Same reachable local network", 1140, 287, 32, ink, bold: true)
a.text("HTTPS / WSS · certificate fingerprint pinning", 1140, 338, 25)
a.arrow(1011, 566, 1096, 566)
a.rect(1110, 480, 730, 232, orange)
a.text("Local Mac / PC hub", 1144, 509, 38, "FFFFFF", bold: true)
a.text("Ktor / JVM · room-server", 1144, 565, 30, "FFFFFF")
a.text("Authoritative spins, roles and settlement", 1144, 616, 26, "FFFFFF")
a.text("Native adapters send commands + receive snapshots", 1144, 662, 23, "FFFFFF")
a.arrow(1475, 723, 1475, 757)
a.rect(1110, 774, 730, 114, "FFFFFF")
a.text("SQLite + encrypted record bodies", 1140, 795, 31, ink, bold: true)
a.text("Rooms · sessions · command results · order history", 1140, 845, 24, muted)
a.rect(80, 936, 1760, 76, "EEEEDD")
a.text("Quick Spin is offline. Group updates need the hub. Downloaded receipts stay on the phone.", 110, 959, 27, green, bold: true)
try a.finish(media.appendingPathComponent("architecture"), vector: true)

let scenes: [(String, String, [String], [String], String)] = [
    ("home", "01 / WELCOME", ["Your table,", "always here."], ["Create a room. Invite your friends.", "Return to the same table for the next meal."], "iOS simulator · actual app screen"),
    ("spin", "02 / QUICK SPIN", ["Let the wheel", "decide."], ["Ten friends. Equal chances.", "Add anyone in Who’s in?", "Smooth native motion and a winner reveal."], "iOS simulator · actual wheel recording"),
    ("room", "03 / TOGETHER", ["One room.", "Every next meal."], ["Approve your people and get ready.", "Spin together on Android and iOS.", "Choose one restaurant for each order."], "Android emulator · saved room, order two"),
    ("library", "04 / RESTAURANTS", ["Save a favorite.", "Share its menu."], ["Keep a restaurant library on your device.", "Create, edit, import and share menu JSON.", "Set contact details and order fees."], "iOS simulator · actual restaurant library"),
    ("receipt", "05 / RECEIPTS", ["Know your total.", "Know who to pay."], ["Confirm the quote and receiving account.", "Track declared and confirmed transfers.", "Downloaded receipts remain available offline."], "iOS simulator · fictitious bank and payment data")
]
for (name, label, lines, details, caption) in scenes {
    let c = Canvas()
    c.text("FOOD RUN", 96, 68, 32, orange, bold: true)
    c.text("GOOD FOOD. GREAT COMPANY.", 96, 117, 19, muted, bold: true)
    c.rect(96, 220, 520, 50, pale, radius: 25)
    c.text(label, 119, 231, 22, orange, bold: true)
    for (i, line) in lines.enumerated() { c.text(line, 90, 314 + CGFloat(i) * 102, 87, ink, bold: true) }
    for (i, detail) in details.enumerated() { c.text(detail, 96, 581 + CGFloat(i) * 53, 30, muted) }
    c.rect(96, 806, 752, 82, pale)
    c.text(caption, 120, 834, 23, orange, bold: true)
    c.text("Android + iOS  /  Kotlin Multiplatform", 96, 984, 23, muted)
    c.rect(1230, 57, 470, 970, "E9DFD0", radius: 54)
    c.rect(1220, 48, 470, 970, ink, radius: 50)
    try c.finish(work.appendingPathComponent(name))
}
print("Generated architecture.svg, architecture.png and five video cards.")
