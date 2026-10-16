import SwiftUI

/// Shared look for every native surface: dark "ink" glass cards with a warm bronze accent,
/// a serif display face for names and headings, and rounded digits for numbers.
enum Theme {
    static let accent = Palette.accent
    static let accentDeep = Color(red: 0.70, green: 0.45, blue: 0.16)
    static let ink = Color(red: 0.06, green: 0.08, blue: 0.13)
    static let inkRaised = Color(red: 0.11, green: 0.14, blue: 0.21)
    static let hairline = Color.white.opacity(0.10)
    static let highlight = Color.white.opacity(0.18)

    static func display(_ size: CGFloat, weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }

    static func number(_ style: Font.TextStyle, weight: Font.Weight = .semibold) -> Font {
        .system(style, design: .rounded).weight(weight).monospacedDigit()
    }

    static let accentGradient = LinearGradient(
        colors: [Color(red: 0.98, green: 0.76, blue: 0.40), Color(red: 0.86, green: 0.56, blue: 0.22)],
        startPoint: .top,
        endPoint: .bottom
    )
}

/// Translucent ink card with a soft top highlight, used for every floating HUD element.
/// Set on full-screen native views whose own background is opaque: their cards have nothing to
/// blur, and blurs and shadows on every card make long lists stutter while scrolling.
private struct OpaqueCardsKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var opaqueCards: Bool {
        get { self[OpaqueCardsKey.self] }
        set { self[OpaqueCardsKey.self] = newValue }
    }
}

struct CardBackground<S: InsettableShape>: View {
    let shape: S
    var tint: Color? = nil

    @Environment(\.opaqueCards) private var opaque

    var body: some View {
        ZStack {
            if opaque {
                shape.fill(Theme.ink)
            } else {
                shape.fill(.ultraThinMaterial)
            }
            shape.fill(
                LinearGradient(
                    colors: [(tint ?? Theme.inkRaised).opacity(0.70), Theme.ink.opacity(0.78)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            shape.strokeBorder(
                LinearGradient(colors: [Theme.highlight, Theme.hairline], startPoint: .top, endPoint: .bottom),
                lineWidth: 1
            )
        }
        .shadow(color: .black.opacity(opaque ? 0 : 0.35), radius: opaque ? 0 : 10, y: opaque ? 0 : 4)
    }
}

extension View {
    func card(cornerRadius: CGFloat = 20, tint: Color? = nil) -> some View {
        background(CardBackground(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), tint: tint))
    }

    func capsuleCard(tint: Color? = nil) -> some View {
        background(CardBackground(shape: Capsule(), tint: tint))
    }
}

/// Press feedback shared by all native buttons.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .brightness(configuration.isPressed ? -0.06 : 0)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Bronze primary action.
struct PrimaryCapsule: ViewModifier {
    func body(content: Content) -> some View {
        content
            .foregroundStyle(Color.black.opacity(0.85))
            .background(
                Capsule()
                    .fill(Theme.accentGradient)
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.35), lineWidth: 1))
                    .shadow(color: Theme.accent.opacity(0.45), radius: 12, y: 3)
            )
    }
}

/// Small rounded "tag", e.g. turn counts and costs.
struct Tag: View {
    let text: String
    var symbol: String? = nil
    var color: Color = .white

    var body: some View {
        HStack(spacing: 3) {
            if let symbol {
                Image(systemName: Symbols.named(symbol)).font(.system(size: 9, weight: .bold))
            }
            Text(text).font(Theme.number(.caption2))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(Capsule().fill(color.opacity(0.16)))
    }
}

/// Thin progress bar with a coloured fill.
struct Meter: View {
    let value: Double
    var color: Color = Theme.accent
    var height: CGFloat = 5

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.12))
                Capsule()
                    .fill(color)
                    .frame(width: max(height, proxy.size.width * min(max(value, 0), 1)))
            }
        }
        .frame(height: height)
    }
}

extension Color {
    /// The stat colours the game uses, keyed by Unciv's stat names.
    static func stat(_ name: String) -> Color {
        switch name {
        case "Gold": return Palette.gold
        case "Science": return Palette.science
        case "Happiness": return Palette.happiness
        case "Culture": return Palette.culture
        case "Faith": return Palette.faith
        case "Food": return Color(red: 0.45, green: 0.82, blue: 0.35)
        case "Production": return Color(red: 0.93, green: 0.55, blue: 0.25)
        case "Malcontent": return Palette.unhappiness
        default: return .white
        }
    }
}

/// Game text with the font's stat and unit glyphs (¤ gold, ⚙ production, † strength…) drawn as
/// coloured SF Symbols; the Scene2D font has pictures for them, the system font does not.
func GameText(_ string: String) -> Text {
    var result = Text("")
    var run = ""
    func flush() {
        if !run.isEmpty { result = result + Text(run); run = "" }
    }
    for character in string {
        // The emoji variation selector would turn ➡ into an emoji; the glyph is replaced anyway
        if character.unicodeScalars.first?.value == 0xFE0F { continue }
        guard let glyph = GameGlyph.map[character.unicodeScalars.first.map { Character($0) } ?? character] else {
            run.append(character)
            continue
        }
        flush()
        result = result + Text(Image(systemName: Symbols.named(glyph.symbol))).foregroundColor(glyph.color)
    }
    flush()
    return result
}

private enum GameGlyph {
    struct Glyph {
        let symbol: String
        let color: Color
    }

    static let map: [Character: Glyph] = [
        "⏳": Glyph(symbol: "hourglass", color: Color(white: 0.8)),
        "†": Glyph(symbol: "shield.lefthalf.filled", color: Color(white: 0.85)),
        "‡": Glyph(symbol: "scope", color: Color(white: 0.85)),
        "➡": Glyph(symbol: "figure.walk", color: Color(white: 0.85)),
        "…": Glyph(symbol: "arrow.up.right", color: Color(white: 0.85)),
        "♡": Glyph(symbol: "heart.fill", color: Palette.unhappiness),
        "⚙": Glyph(symbol: "hammer.fill", color: .stat("Production")),
        "¤": Glyph(symbol: "dollarsign.circle.fill", color: .stat("Gold")),
        "⁂": Glyph(symbol: "leaf.fill", color: .stat("Food")),
        "⍾": Glyph(symbol: "flask.fill", color: .stat("Science")),
        "♪": Glyph(symbol: "theatermasks.fill", color: .stat("Culture")),
        "⌣": Glyph(symbol: "face.smiling", color: .stat("Happiness")),
        "☮": Glyph(symbol: "sparkles", color: .stat("Faith")),
        "♬": Glyph(symbol: "paintpalette.fill", color: Palette.culture),
        "⚒": Glyph(symbol: "wrench.and.screwdriver.fill", color: .stat("Production")),
        "⛤": Glyph(symbol: "star.circle.fill", color: Palette.gold),
        "⚖": Glyph(symbol: "scalemass.fill", color: Palette.gold),
        "⚛": Glyph(symbol: "atom", color: Palette.science),
        "☠": Glyph(symbol: "xmark.octagon.fill", color: Palette.unhappiness),
        "⛏": Glyph(symbol: "gearshape.2.fill", color: Color(white: 0.85)),
        "∞": Glyph(symbol: "infinity", color: Color(white: 0.85)),
        "⌚": Glyph(symbol: "clock", color: Color(white: 0.85)),
        "✯": Glyph(symbol: "star.fill", color: Palette.gold),
        "◉": Glyph(symbol: "smallcircle.filled.circle", color: Color(white: 0.85)),
    ]
}
