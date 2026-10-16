import SwiftUI

// MARK: - Screens and sheets (see com.unciv.ui.nativeui.NativeScreenPresenter)

/// A whole native screen or sheet; the Kotlin presenter's `kind` selects the case.
enum NativeScreen: Equatable {
    case form(FormState)
    case saves(SavesState)
    case languages(LanguagesState)

    static func decode(kind: String, json: String) -> NativeScreen? {
        let data = Data(json.utf8)
        do {
            switch kind {
            case "form": return .form(try JSONDecoder().decode(FormState.self, from: data))
            case "saves": return .saves(try JSONDecoder().decode(SavesState.self, from: data))
            case "languages": return .languages(try JSONDecoder().decode(LanguagesState.self, from: data))
            default:
                NSLog("[DecivNativeUI] Unknown screen kind %@", kind)
                return nil
            }
        } catch {
            NSLog("[DecivNativeUI] Could not decode %@ screen: %@", kind, String(describing: error))
            return nil
        }
    }
}

/// Renders a native screen; `perform` already carries the `screen:` or `sheet:` prefix.
struct NativeScreenView: View {
    let screen: NativeScreen
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        switch screen {
        case .form(let form): FormView(form: form, perform: perform)
        case .saves(let saves): SavesView(state: saves, perform: perform)
        case .languages(let languages): LanguagePickerView(state: languages, perform: perform)
        }
    }
}

struct FormState: Decodable, Equatable {
    struct Option: Decodable, Equatable, Identifiable, Hashable {
        let id: String
        let label: String
        let detail: String?
        let icon: String?
        let symbol: String?
        let background: String?
        let tint: String?
    }

    struct Item: Decodable, Equatable, Identifiable {
        let type: String
        let id: String
        let label: String
        let detail: String?
        let enabled: Bool
        let on: Bool?
        let value: String?
        let style: String?
        let catalog: String?
        let icon: String?
        let symbol: String?
        let role: String?
        let confirm: String?
        let options: [Option]?
        let selected: [String]?
        let number: Double?
        let min: Double?
        let max: Double?
        let step: Double?
        let format: String?
        let background: String?
        let tint: String?
        let tag: String?
        let canChangeType: Bool?
        let canRemove: Bool?
        let lines: [MarkupLine]?
        let chart: ChartData?
        let replay: ReplayData?
        /// Shown in an empty text field
        let placeholder: String?
    }

    /// Lines over turns, see `NativeForm.Section.chart`
    struct ChartData: Decodable, Equatable {
        struct Series: Decodable, Equatable {
            let name: String
            let color: String
            let highlighted: Bool
            /// Turn and value pairs
            let points: [Double]
        }
        let series: [Series]
    }

    /// The map's ownership history, see `NativeForm.Section.replay`
    struct ReplayData: Decodable, Equatable {
        let width: Double
        let height: Double
        let tileSize: Double
        let palette: [String]
        /// x, y, terrain palette index per tile
        let tiles: [Double]
        /// Outer and inner palette index per owner
        let owners: [Double]
        /// Turn, tile index, owner index (-1 for none), city type (0 none, 1 city, 2 capital)
        let changes: [Double]
        let firstTurn: Int
        /// Year text per turn from `firstTurn`
        let years: [String]
    }

    /// One of the game's formatted lines (Civilopedia text)
    struct MarkupLine: Decodable, Equatable, Hashable {
        let text: String
        let header: Int
        let indent: Int
        let separator: Bool
        let starred: Bool
        let centered: Bool
        let color: String?
        let link: String?
        let external: Bool
        let icon: String?
    }

    struct Section: Decodable, Equatable, Identifiable {
        let id: String
        let title: String?
        let icon: String?
        let footer: String?
        let collapsible: Bool
        let expanded: Bool
        let items: [Item]
    }

    struct Header: Decodable, Equatable {
        let items: [Item]
    }

    struct Primary: Decodable, Equatable {
        let label: String
        let enabled: Bool
        let symbol: String?
    }

    struct Alert: Decodable, Equatable {
        let title: String
        let message: String
    }

    let title: String
    let subtitle: String?
    let closeSymbol: String
    let hasClose: Bool
    let primary: Primary?
    let busy: String?
    let alert: Alert?
    let catalogs: [String: [Option]]
    let header: Header?
    let sections: [Section]
}

struct SavesState: Decodable, Equatable {
    struct Save: Decodable, Equatable, Identifiable {
        var id: String { name }
        let name: String
        let date: String
        let autosave: Bool
    }

    struct Preview: Decodable, Equatable {
        let name: String
        /// Empty while loading
        let lines: [String]
        let civ: String?
        let civIcon: String?
        let outerColor: String?
        let innerColor: String?
        let failed: Bool
    }

    let saving: Bool
    let title: String
    let defaultName: String
    let showAutosaves: Bool
    let saves: [Save]
    let selected: Preview?
    let busy: String?
    let error: String?
    let canPickFile: Bool
    let labels: [String: String]
}

struct LanguagesState: Decodable, Equatable {
    struct Language: Decodable, Equatable, Identifiable {
        let id: String
        let name: String
        /// BCP 47 tag, e.g. `pt-BR`
        let tag: String?
        let percent: Int

        /// The language's name in that language, e.g. "Deutsch"
        var nativeName: String? {
            guard let tag else { return nil }
            let locale = Locale(identifier: tag)
            let code = String(tag.prefix { $0 != "-" })
            // Keep the region for languages Unciv offers in more than one variant
            let name = ["zh", "pt"].contains(code)
                ? locale.localizedString(forIdentifier: tag)
                : locale.localizedString(forLanguageCode: code)
            return name.map { $0.prefix(1).uppercased() + $0.dropFirst() }
        }
    }

    let title: String
    let disclaimer: String
    let chosen: String
    let confirm: String
    let languages: [Language]
}

// MARK: - Minimap

struct MinimapState: Decodable, Equatable {
    struct Layers: Decodable, Equatable {
        let movement: Bool
        let yields: Bool
        let worked: Bool
        let resources: Bool
        let improvements: Bool
    }

    let visible: Bool
    let layers: Layers
    let width: Double?
    let height: Double?
    let tileSize: Double?
    let palette: [String]?
    /// x, y, palette index per tile, in the Scene2D minimap's space (y up)
    let tiles: [Double]?
    /// x, y, outer colour, inner colour, capital flag per city
    let cities: [Double]?
}

final class MinimapStore: ObservableObject {
    @Published var state: MinimapState?
    /// Fractions of the minimap from the bottom left
    @Published var camera: CGRect = .zero
    /// The tiles drawn once into an image; redrawn only when they change
    private(set) var rendered: UIImage?
    private var renderedFor: MinimapState?

    func image(scale: CGFloat) -> UIImage? {
        guard let state, state.visible else { return nil }
        if state == renderedFor, let rendered { return rendered }
        rendered = MinimapRenderer.render(state, scale: scale)
        renderedFor = state
        return rendered
    }
}

// MARK: - Strings

/// The game's translations for strings that only exist on the native side. Keys missing from the
/// game's translation files come back unchanged, so English keys double as the English text.
enum Strings {
    static var translator: ((String) -> String?)?
    private static var cache: [String: String] = [:]

    static func reset() {
        cache.removeAll()
    }

    static func translate(_ key: String) -> String {
        if let cached = cache[key] { return cached }
        let value = translator?(key) ?? key
        cache[key] = value
        return value
    }
}

/// Translates a native-only string through the game's translations, e.g. `L("Next turn")`.
func L(_ key: String) -> String {
    Strings.translate(key)
}
