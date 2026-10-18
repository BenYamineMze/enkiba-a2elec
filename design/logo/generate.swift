// Génère le logo Enkiba (concept B, variante 2) et toutes ses déclinaisons Android
// à partir d'une seule description. macOS uniquement (CoreGraphics, CoreText).
//   swift design/logo/generate.swift <racine du dépôt> [dossier d'aperçus]
import AppKit
import CoreText

// ---------------------------------------------------------------- Charte
let BG = "#F4F8F6", GREEN = "#34C98B", GREEN_TEXT = "#0E7A4F", ON_GREEN = "#0B3B27"
let PASTEL = "#DDF5EA", ORANGE = "#F0904A", MUTED = "#5E6E68"

final class RR {
    let x, y, w, h, r: Double; let color: String; let hole: RR?
    init(x: Double, y: Double, w: Double, h: Double, r: Double, color: String, hole: RR? = nil) {
        self.x = x; self.y = y; self.w = w; self.h = h; self.r = r; self.color = color; self.hole = hole
    }
}

// Dessin sur une grille de 512 ; la tuile de fond n'en fait pas partie
let corner = RR(x: 96, y: 96, w: 120, h: 120, r: 20, color: GREEN_TEXT,
                hole: RR(x: 124, y: 124, w: 64, h: 64, r: 10, color: BG))
let mark: [RR] = [
    corner,
    RR(x: 140, y: 140, w: 32, h: 32, r: 6, color: ORANGE),
    RR(x: 244, y: 96, w: 60, h: 60, r: 12, color: GREEN),
    RR(x: 332, y: 96, w: 84, h: 60, r: 12, color: GREEN),
    RR(x: 244, y: 184, w: 172, h: 60, r: 12, color: GREEN),
    RR(x: 96, y: 272, w: 60, h: 144, r: 12, color: GREEN),
    RR(x: 184, y: 272, w: 232, h: 60, r: 12, color: GREEN),
    RR(x: 184, y: 356, w: 120, h: 60, r: 12, color: GREEN),
    RR(x: 332, y: 356, w: 84, h: 60, r: 12, color: ORANGE),
]

let root = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let previews = CommandLine.arguments.count > 2 ? CommandLine.arguments[2] : nil
func write(_ path: String, _ text: String) { try! text.write(toFile: "\(root)/\(path)", atomically: true, encoding: .utf8) }
func f(_ v: Double) -> String { let s = String(format: "%.2f", v); return s.replacingOccurrences(of: #"\.?0+$"#, with: "", options: .regularExpression) }

// Rectangle arrondi en tracé (sens horaire), avec transformation x' = ox + s·x
func rrPath(_ r: RR, s: Double = 1, ox: Double = 0, oy: Double = 0) -> String {
    let x = ox + r.x * s, y = oy + r.y * s, w = r.w * s, h = r.h * s, k = r.r * s
    return "M\(f(x + k)),\(f(y))H\(f(x + w - k))A\(f(k)),\(f(k)) 0 0 1 \(f(x + w)),\(f(y + k))V\(f(y + h - k))" +
        "A\(f(k)),\(f(k)) 0 0 1 \(f(x + w - k)),\(f(y + h))H\(f(x + k))A\(f(k)),\(f(k)) 0 0 1 \(f(x)),\(f(y + h - k))" +
        "V\(f(y + k))A\(f(k)),\(f(k)) 0 0 1 \(f(x + k)),\(f(y))Z"
}

// ---------------------------------------------------------------- SVG
func svgMark(color: ((RR) -> String)? = nil) -> String {
    mark.map { r in
        let fill = color?(r) ?? r.color
        if let hole = r.hole {   // anneau : le trou laisse voir le fond, quel qu'il soit
            return "  <path fill=\"\(fill)\" fill-rule=\"evenodd\" d=\"\(rrPath(r)) \(rrPath(hole))\"/>"
        }
        return "  <path fill=\"\(fill)\" d=\"\(rrPath(r))\"/>"
    }.joined(separator: "\n")
}
let tile = "  <rect width=\"512\" height=\"512\" rx=\"112\" fill=\"\(BG)\"/>"
write("design/logo/enkiba-logo.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\">\n\(tile)\n\(svgMark())\n</svg>\n")
write("design/logo/enkiba-foreground.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\">\n\(svgMark())\n</svg>\n")
write("design/logo/enkiba-logo-mono.svg",
      "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\">\n  <rect width=\"512\" height=\"512\" fill=\"#FFFFFF\"/>\n\(svgMark { _ in "#000000" })\n</svg>\n")

// Texte converti en tracés (police du dépôt) : le SVG ne dépend d'aucune police installée
func textPath(_ text: String, font file: String, size: Double, x: Double, baseline: Double) -> String {
    let data = CGDataProvider(url: URL(fileURLWithPath: "\(root)/app/src/main/res/font/\(file)") as CFURL)!
    let font = CTFontCreateWithGraphicsFont(CGFont(data)!, size, nil, nil)
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
    var d = ""
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
        let n = CTRunGetGlyphCount(run)
        var glyphs = [CGGlyph](repeating: 0, count: n); var pos = [CGPoint](repeating: .zero, count: n)
        CTRunGetGlyphs(run, CFRange(location: 0, length: n), &glyphs); CTRunGetPositions(run, CFRange(location: 0, length: n), &pos)
        for i in 0..<n {
            var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x + pos[i].x, ty: baseline)   // y vers le bas en SVG
            guard let p = CTFontCreatePathForGlyph(font, glyphs[i], &t) else { continue }
            p.applyWithBlock { el in
                let e = el.pointee; let pt = e.points
                switch e.type {
                case .moveToPoint: d += "M\(f(pt[0].x)),\(f(pt[0].y))"
                case .addLineToPoint: d += "L\(f(pt[0].x)),\(f(pt[0].y))"
                case .addQuadCurveToPoint: d += "Q\(f(pt[0].x)),\(f(pt[0].y)) \(f(pt[1].x)),\(f(pt[1].y))"
                case .addCurveToPoint: d += "C\(f(pt[0].x)),\(f(pt[0].y)) \(f(pt[1].x)),\(f(pt[1].y)) \(f(pt[2].x)),\(f(pt[2].y))"
                case .closeSubpath: d += "Z"
                @unknown default: break
                }
            }
        }
    }
    return d
}
let s144 = 0.28125, o = 8.0
let horizontal = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 560 160">
  <rect x="4" y="4" width="152" height="152" rx="\(f(112 * s144 + 4))" fill="\(PASTEL)"/>
  <rect x="\(f(o))" y="\(f(o))" width="144" height="144" rx="\(f(112 * s144))" fill="\(BG)"/>
\(mark.map { r in
    let p = r.hole.map { "\(rrPath(r, s: s144, ox: o, oy: o)) \(rrPath($0, s: s144, ox: o, oy: o))" } ?? rrPath(r, s: s144, ox: o, oy: o)
    return "  <path fill=\"\(r.color)\"\(r.hole != nil ? " fill-rule=\"evenodd\"" : "") d=\"\(p)\"/>"
}.joined(separator: "\n"))
  <path fill="\(ON_GREEN)" d="\(textPath("Enkiba", font: "plus_jakarta_sans_extrabold.ttf", size: 64, x: 176, baseline: 92))"/>
  <path fill="\(MUTED)" d="\(textPath("Gestion de stock et caisse", font: "plus_jakarta_sans_medium.ttf", size: 22, x: 178, baseline: 124))"/>
</svg>

"""
write("design/logo/enkiba-logo-horizontal.svg", horizontal)

// ---------------------------------------------------------------- Android (VectorDrawable)
func vd(_ w: Double, _ paths: [(String, String, Bool)]) -> String {
    let body = paths.map { (color, d, evenOdd) in
        "    <path\n        android:fillColor=\"\(color)\"\(evenOdd ? "\n        android:fillType=\"evenOdd\"" : "")\n        android:pathData=\"\(d)\" />"
    }.joined(separator: "\n")
    return """
<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par design/logo/generate.swift : ne pas modifier à la main -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="\(f(w))dp"
    android:height="\(f(w))dp"
    android:viewportWidth="\(f(w))"
    android:viewportHeight="\(f(w))">
\(body)
</vector>

"""
}
func markPaths(s: Double, ox: Double, color: ((RR) -> String)? = nil) -> [(String, String, Bool)] {
    mark.map { r in
        let c = color?(r) ?? r.color
        if let hole = r.hole { return (c, "\(rrPath(r, s: s, ox: ox, oy: ox)) \(rrPath(hole, s: s, ox: ox, oy: ox))", true) }
        return (c, rrPath(r, s: s, ox: ox, oy: ox), false)
    }
}
// Icône adaptive (108 dp) : dessin de 48 dp centré, dans la zone visible même en masque rond
let aScale = 48.0 / 320.0, aOff = 54.0 - 256.0 * aScale
write("app/src/main/res/drawable/ic_launcher_foreground.xml", vd(108, markPaths(s: aScale, ox: aOff)))
write("app/src/main/res/drawable/ic_launcher_monochrome.xml", vd(108, markPaths(s: aScale, ox: aOff) { _ in "#FF000000" }))
// Logo complet (tuile + dessin) pour l'interface, 512 → 96 dp
let uScale = 96.0 / 512.0
write("app/src/main/res/drawable/enkiba_logo.xml",
      vd(96, [(BG, rrPath(RR(x: 0, y: 0, w: 512, h: 512, r: 112, color: BG), s: uScale), false)] + markPaths(s: uScale, ox: 0)))

// ---------------------------------------------------------------- PNG
func color(_ hex: String) -> CGColor {
    let v = UInt32(hex.dropFirst(), radix: 16)!
    return CGColor(srgbRed: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
}
func cgRR(_ r: RR, s: Double, ox: Double, oy: Double) -> CGPath {
    CGPath(roundedRect: CGRect(x: ox + r.x * s, y: oy + r.y * s, width: r.w * s, height: r.h * s), cornerWidth: r.r * s, cornerHeight: r.r * s, transform: nil)
}
/// background(ctx, size) dessine le fond ; le dessin occupe [96,416] mis à l'échelle de [contentFrom, contentTo]
func png(_ path: String, size: Int, background: (CGContext, Double) -> Void, contentFrom: Double, contentTo: Double) {
    let n = Double(size)
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.translateBy(x: 0, y: n); ctx.scaleBy(x: 1, y: -1)     // repère SVG (y vers le bas)
    background(ctx, n)
    let s = (contentTo - contentFrom) * n / 320.0, off = contentFrom * n - 96 * s
    for r in mark {
        ctx.setFillColor(color(r.color)); ctx.addPath(cgRR(r, s: s, ox: off, oy: off))
        if let hole = r.hole { ctx.addPath(cgRR(hole, s: s, ox: off, oy: off)); ctx.fillPath(using: .evenOdd) } else { ctx.fillPath() }
    }
    let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: path))
}
let tileBg: (CGContext, Double) -> Void = { c, n in
    c.setFillColor(color(BG)); c.addPath(CGPath(roundedRect: CGRect(x: 0, y: 0, width: n, height: n), cornerWidth: n * 112 / 512, cornerHeight: n * 112 / 512, transform: nil)); c.fillPath()
}
let circleBg: (CGContext, Double) -> Void = { c, n in c.setFillColor(color(BG)); c.fillEllipse(in: CGRect(x: 0, y: 0, width: n, height: n)) }
let squareBg: (CGContext, Double) -> Void = { c, n in c.setFillColor(color(BG)); c.fill(CGRect(x: 0, y: 0, width: n, height: n)) }

for (dir, px) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    png("\(root)/app/src/main/res/mipmap-\(dir)/ic_launcher.png", size: px, background: tileBg, contentFrom: 96.0 / 512, contentTo: 416.0 / 512)
    // Rond : dessin réduit pour que ses coins restent dans le cercle
    png("\(root)/app/src/main/res/mipmap-\(dir)/ic_launcher_round.png", size: px, background: circleBg, contentFrom: 0.2, contentTo: 0.8)
}
// Play Store : carré plein 512 (Google applique son propre arrondi)
png("\(root)/app/src/main/ic_launcher-playstore.png", size: 512, background: squareBg, contentFrom: 96.0 / 512, contentTo: 416.0 / 512)

// ---------------------------------------------------------------- Aperçus de l'icône adaptive
if let dir = previews {
    // Couche de 108 dp rendue à 432 px ; masques : cercle, carré arrondi, goutte (72 dp visibles)
    let n = 432.0, k = n / 108.0
    for (name, clip) in [("cercle", 0), ("carre", 1), ("goutte", 2)] {
        let ctx = CGContext(data: nil, width: Int(n), height: Int(n), bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.setFillColor(CGColor(gray: 0.85, alpha: 1)); ctx.fill(CGRect(x: 0, y: 0, width: n, height: n))
        ctx.translateBy(x: 0, y: n); ctx.scaleBy(x: 1, y: -1)
        let v = CGRect(x: 18 * k, y: 18 * k, width: 72 * k, height: 72 * k)
        let mask: CGPath
        switch clip {
        case 0: mask = CGPath(ellipseIn: v, transform: nil)
        case 1: mask = CGPath(roundedRect: v, cornerWidth: 16 * k, cornerHeight: 16 * k, transform: nil)
        default:
            let m = CGMutablePath(); m.move(to: CGPoint(x: v.midX, y: v.minY))
            m.addArc(tangent1End: CGPoint(x: v.maxX, y: v.minY), tangent2End: CGPoint(x: v.maxX, y: v.maxY), radius: 36 * k)
            m.addArc(tangent1End: CGPoint(x: v.maxX, y: v.maxY), tangent2End: CGPoint(x: v.minX, y: v.maxY), radius: 36 * k)
            m.addArc(tangent1End: CGPoint(x: v.minX, y: v.maxY), tangent2End: CGPoint(x: v.minX, y: v.minY), radius: 36 * k)
            m.addLine(to: CGPoint(x: v.minX, y: v.minY)); m.closeSubpath(); mask = m
        }
        ctx.addPath(mask); ctx.clip()
        ctx.setFillColor(color(BG)); ctx.fill(CGRect(x: 0, y: 0, width: n, height: n))
        for r in mark {
            ctx.setFillColor(color(r.color)); ctx.addPath(cgRR(r, s: aScale * k, ox: aOff * k, oy: aOff * k))
            if let hole = r.hole { ctx.addPath(cgRR(hole, s: aScale * k, ox: aOff * k, oy: aOff * k)); ctx.fillPath(using: .evenOdd) } else { ctx.fillPath() }
        }
        let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
        try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: "\(dir)/masque-\(name).png"))
    }
}
print("Logo généré.")
