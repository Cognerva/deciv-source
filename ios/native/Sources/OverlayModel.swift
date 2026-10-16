import SwiftUI
import UIKit

final class OverlayModel: ObservableObject {
    enum Mode {
        case hidden
        case mainMenu(MainMenuState)
        case world(WorldHudState)
        case city(CityState)
        case techTree(TechTreeState)
        case screen(NativeScreen)
        case mapEditor(MapEditorState)

        var isVisible: Bool {
            if case .hidden = self { return false }
            return true
        }

        /// The main menu owns the whole screen; the world HUD only owns its controls.
        var passesTouchesThrough: Bool {
            switch self {
            case .world, .city: return true
            default: return false
            }
        }
    }

    @Published var mode: Mode = .hidden {
        didSet { reportTopInset() }
    }
    /// Shown above every mode, see `StoryCardView`
    @Published var modal: StoryCard?
    /// A native sheet above the current mode, e.g. the options
    @Published var sheet: NativeScreen?

    enum Curtain: Equatable { case plain, loading }
    /// Covers the game while it switches screens, see `DCVNativeUI.showCurtain`
    @Published var curtain: Curtain?
    private var curtainGeneration = 0

    func showCurtain(loading: Bool) {
        curtainGeneration += 1
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { curtain = loading ? .loading : .plain }
    }

    /// Lifts on the next run loop turn, once the new screen's overlay has been laid out underneath
    func hideCurtain() {
        let generation = curtainGeneration
        DispatchQueue.main.async { [weak self] in
            guard let self, self.curtainGeneration == generation else { return }
            withAnimation(.easeOut(duration: 0.18)) { self.curtain = nil }
        }
    }
    /// Kept apart from `mode`: the camera moves every frame while panning
    let minimap = MinimapStore()
    /// The toast on screen; later ones wait in `pendingToasts`
    @Published var toast: Toast?
    private var pendingToasts: [Toast] = []

    struct Toast: Equatable, Identifiable {
        let id = UUID()
        let message: String
        let duration: Double
    }

    func enqueueToast(_ message: String, duration: Double) {
        let toast = Toast(message: message, duration: max(duration, 1.5))
        if self.toast == nil { show(toast) } else { pendingToasts.append(toast) }
    }

    func dismissToast() {
        toast = nil
        if !pendingToasts.isEmpty { show(pendingToasts.removeFirst()) }
    }

    private func show(_ toast: Toast) {
        self.toast = toast
        DispatchQueue.main.asyncAfter(deadline: .now() + toast.duration) { [weak self] in
            guard let self, self.toast?.id == toast.id else { return }
            self.dismissToast()
        }
    }
    /// Window-space frames of the controls that should receive touches in pass-through modes.
    var interactiveFrames: [String: CGRect] = [:] {
        didSet { reportTopInset() }
    }
    var windowHeight: CGFloat = 0
    var send: (String) -> Void = { _ in }

    /// Tells the core how much of the screen's top and bottom right the HUD covers, so Scene2D
    /// widgets that stay (tutorial task, notifications) can move clear of it.
    private func reportTopInset() {
        guard case .world(let hud) = mode, windowHeight > 0 else { return }
        let topControls = ["topBar", "nextTurn", "research", "policies", "diplomacy", "undo", "tutorial"]
        let bottom = topControls.compactMap { interactiveFrames[$0]?.maxY }.max() ?? 0
        guard bottom > 0 else { return }
        let top = min(bottom / windowHeight, 0.5)
        let tileTop = ["tileCard", "minimap"].compactMap { interactiveFrames[$0]?.minY }.min()
        let bottomFraction = tileTop.map { min(max((windowHeight - $0) / windowHeight, 0), 0.6) } ?? 0
        // Compare with what the core last stored, so a freshly loaded world screen gets it too
        guard abs(top - CGFloat(hud.topFraction)) > 0.005 || abs(bottomFraction - CGFloat(hud.bottomFraction)) > 0.005
        else { return }
        send(String(format: "hud:layout:%.4f:%.4f", Double(top), Double(bottomFraction)))
    }

    /// `.soft` sends without a haptic, e.g. for each keystroke in a search field
    func perform(_ id: String, haptic: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        if haptic != .soft { UIImpactFeedbackGenerator(style: haptic).impactOccurred() }
        send(id)
    }
}

// MARK: - Snapshots (produced by the Kotlin core, see com.unciv.ui.nativeui)

struct TechTreeState: Decodable, Equatable {
    struct Era: Decodable, Equatable, Hashable {
        let name: String
        let firstColumn: Int
        let lastColumn: Int
    }

    struct IconRef: Decodable, Equatable, Hashable {
        let icon: String
    }

    struct Ref: Decodable, Equatable, Hashable {
        let id: String
    }

    struct Tech: Decodable, Equatable, Identifiable {
        let id: String
        let name: String
        let icon: String
        let column: Int
        let row: Int
        /// researched, current, queued, available or locked
        let state: String
        let turns: Int
        let queueIndex: Int
        let progress: Double
        let prerequisites: [Ref]
        let unlocks: [IconRef]
    }

    struct Selected: Decodable, Equatable {
        let id: String
        let name: String
        let icon: String
        let description: String
        let unlocks: [IconRef]
    }

    struct Pick: Decodable, Equatable {
        let label: String
        let enabled: Bool
    }

    let freeTechPick: Bool
    let science: Int
    let eras: [Era]
    let techs: [Tech]
    let selected: Selected?
    let pick: Pick
}

struct StoryCard: Decodable, Equatable, Identifiable {
    struct Page: Decodable, Equatable {
        let text: String
    }

    /// A choice on a card standing in for a Scene2D popup
    struct Button: Decodable, Equatable {
        let label: String
        let destructive: Bool
        let enabled: Bool
        /// What the choice does, e.g. what annexing a city means
        let detail: String?
        /// A small adjusting button, e.g. "+50", shown in a row
        let inline: Bool?
    }

    /// A popup's text field or switch
    struct Input: Decodable, Equatable {
        /// `text`, `number` or `toggle`
        let kind: String
        let label: String
        let value: String
        let placeholder: String?
    }

    struct ImageFile: Decodable, Equatable {
        let path: String
        /// "bundle" for game assets, "local" for files in the game's data folder (mods)
        let location: String

        var url: URL? {
            location == "bundle"
                ? Bundle.main.resourceURL?.appendingPathComponent(path)
                : URL(fileURLWithPath: NSHomeDirectory()).appendingPathComponent("Library/local").appendingPathComponent(path)
        }
    }

    /// Each card gets a fresh identity, so its page position resets; an update keeps it
    var id = UUID()
    let title: String
    let subtitle: String?
    let pages: [Page]
    let doneLabel: String
    let icon: String?
    let iconBackground: String?
    let iconTint: String?
    let image: ImageFile?
    let buttons: [Button]
    let inputs: [Input]?
    let error: String?

    private enum CodingKeys: String, CodingKey {
        case title, subtitle, pages, doneLabel, icon, iconBackground, iconTint, image, buttons, inputs, error
    }
}

struct MainMenuState: Decodable, Equatable {
    struct Item: Decodable, Equatable, Identifiable {
        let id: String
        let title: String
        let secondary: Bool
    }

    struct Labels: Decodable, Equatable {
        let civilopedia: String
        let support: String
        let contact: String
    }

    let version: String
    let supportAvailable: Bool
    let supporter: Bool
    let items: [Item]
    let labels: Labels
}

struct WorldHudState: Decodable, Equatable {
    struct Stats: Decodable, Equatable {
        let gold: Int
        let goldPerTurn: Int
        let science: Int
        let happiness: Int
        let goldenAge: Bool
        let culture: Int
        /// 0 = a policy can be adopted now, -1 = no culture income
        let turnsToPolicy: Int
        let religionEnabled: Bool
        let faith: Int
        let faithPerTurn: Int
    }

    struct Research: Decodable, Equatable {
        let available: Bool
        let name: String?
        let icon: String?
        let turns: String?
        let needsChoice: Bool
    }

    struct NextTurn: Decodable, Equatable {
        let action: String
        let title: String
        let subtitle: String?
        let enabled: Bool
        let color: String
    }

    struct Unit: Decodable, Equatable {
        struct Promotion: Decodable, Equatable {
            let name: String
            let icon: String
        }
        let name: String
        let icon: String
        let outerColor: String
        let innerColor: String
        let health: Int
        let movement: String
        let strength: Int
        let rangedStrength: Int
        let range: Int
        /// -1 for civilians
        let xp: Int
        let xpNext: Int
        let canPromote: Bool
        let promotions: [Promotion]
    }

    struct City: Decodable, Equatable {
        let name: String
        let outerColor: String
        let innerColor: String
        let health: Int
        let maxHealth: Int
        let strength: Int
        let bombard: Int
        let population: Int
        let canEnter: Bool
    }

    struct Tile: Decodable, Equatable {
        let title: String
        let lines: [Line]
        let stats: [StatValue]
    }

    struct UnitAction: Decodable, Equatable {
        let type: String
        let title: String
        let enabled: Bool
        let current: Bool
    }

    let revision: Int
    let visible: Bool
    let civName: String
    let turn: Int
    /// Set for short games that end at a turn limit
    let turnLimit: Int?
    let year: String
    let stats: Stats
    let research: Research
    let canUndo: Bool
    let showPolicies: Bool
    let showDiplomacy: Bool
    let nextTurn: NextTurn
    struct Tutorial: Decodable, Equatable {
        let title: String
        let text: String
        let collapsed: Bool
    }

    struct Menu: Decodable, Equatable {
        let showSave: Bool
        let showMusic: Bool
        let showDiplomacy: Bool
    }

    struct Battle: Decodable, Equatable {
        struct Row: Decodable, Equatable, Hashable {
            let left: String?
            let right: String?
        }
        struct Button: Decodable, Equatable, Hashable {
            let label: String
            let enabled: Bool
        }
        let rows: [Row]
        let buttons: [Button]
    }

    struct Notification: Decodable, Equatable {
        let text: String
        let icon: String?
        let background: String?
        let tint: String?
        let category: String
        let actionable: Bool
    }

    let menu: Menu
    let battle: Battle?
    let notifications: [Notification]
    let tutorial: Tutorial?
    let unit: Unit?
    let city: City?
    let tile: Tile?
    let idleUnits: Int
    let hasIdleUnits: Bool
    let unitActions: [UnitAction]
    /// The HUD extents the core currently lays out around, see `OverlayModel.reportTopInset`
    let topFraction: Double
    let bottomFraction: Double
}

struct Line: Decodable, Equatable, Hashable {
    let text: String
}

struct StatValue: Decodable, Equatable, Hashable {
    let stat: String
    let value: Int?
}

struct CityState: Decodable, Equatable {
    struct Growth: Decodable, Equatable {
        let starving: Bool
        let growing: Bool
        let turns: Int
        let foodStored: Int
        let foodNeeded: Int
    }

    struct FocusOption: Decodable, Equatable, Identifiable {
        let id: String
        let label: String
        let stats: [StatValue]
    }

    struct QueueEntry: Decodable, Equatable {
        let id: String
        let name: String
        let icon: String
        let perpetual: Bool
        let turns: Int
        let progress: Double
        let canRaise: Bool
        let canLower: Bool
        let canRemove: Bool
    }

    struct BuyOption: Decodable, Equatable, Hashable {
        let stat: String
        let cost: Int
        let enabled: Bool
    }

    struct Item: Decodable, Equatable, Identifiable {
        let id: String
        let name: String
        let icon: String
        let perpetual: Bool
        let turns: Int
        let cost: Int
        let rejection: String?
        let statHints: [StatValue]?
        let canAdd: Bool
        let buy: [BuyOption]
    }

    struct Category: Decodable, Equatable, Identifiable {
        let id: String
        let title: String
        let items: [Item]
    }

    struct Building: Decodable, Equatable, Identifiable {
        let id: String
        let name: String
        let icon: String
        let free: Bool
        let stats: [StatValue]
    }

    struct BuildingGroup: Decodable, Equatable, Identifiable {
        var id: String { title }
        let title: String
        let items: [Building]
    }

    struct Selection: Decodable, Equatable {
        let id: String
        let name: String
        let icon: String
        let description: String
        let sellGold: Int
        let canSell: Bool
    }

    struct SelectedTile: Decodable, Equatable {
        let title: String
        let lines: [Line]
        let stats: [StatValue]
        let isWorked: Bool
        let isLocked: Bool
        let canBuy: Bool
        let buyCost: Int
        let canAffordBuy: Bool
        let canLock: Bool
    }

    let name: String
    let population: Int
    let freePopulation: Int
    let isCapital: Bool
    let isPuppet: Bool
    let isRazing: Bool
    let inResistance: Bool
    let canPage: Bool
    let canChange: Bool
    let isSpying: Bool
    let outerColor: String
    let innerColor: String
    let growth: Growth
    /// -1 when the city cannot currently expand
    let turnsToExpand: Int
    let stats: [StatValue]
    let focus: String
    let avoidGrowth: Bool
    let focusOptions: [FocusOption]
    let queue: [QueueEntry]
    let categories: [Category]
    let buildings: [BuildingGroup]
    let selectedConstruction: Selection?
    let selectedTile: SelectedTile?
}

// MARK: - Hit regions

private struct InteractiveFramesKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

extension View {
    /// Marks this view as touchable while the overlay passes other touches to the map.
    /// @param enabled false keeps the view but lets touches through, e.g. a drawer sliding away
    func interactiveRegion(_ id: String, enabled: Bool = true) -> some View {
        background(GeometryReader { proxy in
            Color.clear.preference(key: InteractiveFramesKey.self, value: enabled ? [id: proxy.frame(in: .global)] : [:])
        })
    }

    func collectInteractiveRegions(into model: OverlayModel) -> some View {
        onPreferenceChange(InteractiveFramesKey.self) { model.interactiveFrames = $0 }
    }
}

extension Color {
    /// Formatted-line colours are `#rrggbb` or libGDX colour names such as `GOLD`
    init(gameColor: String) {
        if gameColor.hasPrefix("#") { self.init(hex: gameColor); return }
        switch gameColor.uppercased() {
        case "GOLD", "GOLDENROD": self = Palette.gold
        case "RED", "FIREBRICK", "SCARLET": self = Palette.unhappiness
        case "GREEN", "LIME", "FOREST": self = Palette.happiness
        case "BLUE", "ROYAL", "SKY": self = Palette.science
        case "YELLOW": self = Color.yellow
        case "ORANGE": self = Theme.accent
        case "PURPLE", "VIOLET", "MAGENTA": self = Palette.culture
        case "GRAY", "GREY", "LIGHT_GRAY": self = Color(white: 0.7)
        default: self = .white
        }
    }

    /// The same hue, lightened until it reads on the dark native backgrounds
    var readableOnDark: Color {
        var hue: CGFloat = 0, saturation: CGFloat = 0, brightness: CGFloat = 0, alpha: CGFloat = 0
        guard UIColor(self).getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha) else { return self }
        if brightness >= 0.75 { return self }
        return Color(hue: hue, saturation: min(saturation, 0.7), brightness: 0.85, opacity: alpha)
    }

    /// Parses `#rrggbb`, as written by the Kotlin snapshot for libGDX colours.
    init(hex: String) {
        let digits = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        let value = UInt32(digits, radix: 16) ?? 0xFFFFFF
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

enum Symbols {
    /// SF Symbols added after iOS 15 fall back to a neutral glyph.
    static func named(_ name: String, fallback: String = "circle") -> String {
        UIImage(systemName: name) != nil ? name : fallback
    }

    static func stat(_ stat: String) -> String {
        switch stat {
        case "Gold": return "dollarsign.circle.fill"
        case "Science": return "flask.fill"
        case "Happiness": return "face.smiling.inverse"
        case "Malcontent": return "face.dashed.fill"
        case "Culture": return "theatermasks.fill"
        case "Faith": return "sparkles"
        case "Food": return "leaf.fill"
        case "Production": return "hammer.fill"
        default: return "circle.fill"
        }
    }

    static func unitAction(_ type: String) -> String {
        let name: String
        switch type {
        case "StopEscortFormation": name = "person.2.slash"
        case "EscortFormation": name = "person.2.fill"
        case "SwapUnits": name = "arrow.left.arrow.right"
        case "Automate": name = "gearshape.2.fill"
        case "ConnectRoad": name = "point.topleft.down.curvedto.point.bottomright.up"
        case "StopAutomation", "StopExploration": name = "stop.circle"
        case "StopMovement": name = "hand.raised.fill"
        case "ShowUnitDestination": name = "mappin.and.ellipse"
        case "Sleep": name = "moon.zzz.fill"
        case "SleepUntilHealed": name = "bed.double.fill"
        case "Fortify": name = "shield.fill"
        case "FortifyUntilHealed": name = "shield.lefthalf.filled"
        case "Guard": name = "eye.fill"
        case "Explore": name = "binoculars.fill"
        case "Promote": name = "star.circle.fill"
        case "Upgrade": name = "arrow.up.circle.fill"
        case "Transform": name = "arrow.triangle.2.circlepath"
        case "Pillage": name = "flame.fill"
        case "Paradrop": name = "arrow.down.to.line"
        case "AirSweep": name = "wind"
        case "SetUp": name = "scope"
        case "FoundCity": name = "building.2.fill"
        case "ConstructImprovement", "CreateImprovement": name = "hammer.fill"
        case "Repair": name = "wrench.and.screwdriver.fill"
        case "HurryResearch": name = "flask.fill"
        case "HurryPolicy": name = "scroll.fill"
        case "HurryWonder": name = "building.columns.fill"
        case "HurryBuilding": name = "building.fill"
        case "ConductTradeMission": name = "bag.fill"
        case "FoundReligion", "EnhanceReligion": name = "sparkles"
        case "SpreadReligion": name = "sparkle"
        case "RemoveHeresy": name = "xmark.seal.fill"
        case "TriggerUnique": name = "bolt.fill"
        case "DisbandUnit": name = "trash.fill"
        case "GiftUnit": name = "gift.fill"
        case "Skip": name = "forward.fill"
        case "AddInCapital": name = "star.square.fill"
        default: name = "circle.fill"
        }
        return named(name, fallback: "circle.fill")
    }

    static func nextTurn(_ action: String) -> String {
        let name: String
        switch action {
        case "NextTurn": name = "arrow.right.circle.fill"
        case "NextUnit": name = "figure.walk"
        case "PickTech": name = "flask.fill"
        case "PickPolicy": name = "scroll.fill"
        case "PickConstruction": name = "hammer.fill"
        case "Working", "Waiting": name = "hourglass"
        case "AutoPlay": name = "play.fill"
        case "RetryUpload": name = "arrow.clockwise"
        case "MoveSpies": name = "eye.fill"
        case "MoveAutomatedUnits": name = "gearshape.2.fill"
        case "WorldCongressVote": name = "checkmark.seal.fill"
        case "FoundPantheon", "ExpandPantheon", "FoundReligion", "EnhanceReligion", "ReformReligion": name = "sparkles"
        default: name = "arrow.right.circle.fill"
        }
        return named(name, fallback: "arrow.right.circle.fill")
    }

    static func mainMenu(_ id: String) -> String {
        switch id {
        case "resume": return "play.fill"
        case "quickstart": return "bolt.fill"
        case "newGame": return "plus"
        case "loadGame": return "folder.fill"
        case "multiplayer": return "person.2.fill"
        case "gameCenter": return "gamecontroller.fill"
        case "mapEditor": return "map.fill"
        case "mods": return "puzzlepiece.extension.fill"
        case "options": return "gearshape.fill"
        default: return "circle.fill"
        }
    }
}
