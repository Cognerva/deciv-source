import SwiftUI
import UIKit

/// Native world-screen chrome. The map and notifications stay in Scene2D; this draws the top bar,
/// empire buttons, next turn, unit actions, the unit, city and tile cards, and the minimap.
struct WorldHudView: View {
    let hud: WorldHudState
    let minimap: MinimapStore
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void
    /// Sends without haptics, for continuous input such as dragging on the minimap
    let send: (String) -> Void

    @State private var showMenu = false
    @State private var showNotifications = false

    var body: some View {
        ZStack {
            hudContent
            if showNotifications {
                NotificationsPanel(notifications: hud.notifications, close: { showNotifications = false }) { index in
                    showNotifications = false
                    perform("note:\(index)", .light)
                }
                .transition(.opacity)
                .zIndex(4)
            }
            if showMenu {
                GameMenuDrawer(hud: hud, close: { showMenu = false }) { id in
                    showMenu = false
                    perform(id, .light)
                }
                .transition(.opacity)
                .zIndex(5)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.88), value: showMenu)
        .animation(.spring(response: 0.35, dampingFraction: 0.88), value: showNotifications)
        .onDebugCommand { if $0 == "openMenu" { showMenu = true } }
    }

    private var hudContent: some View {
        GeometryReader { proxy in
            // Portrait phone: narrow. Landscape phone: short, so everything stays small and to the sides.
            let compact = proxy.size.width < 640
            let short = proxy.size.height < 520
            let phone = compact || short
            let selected = hud.unit != nil || hud.city != nil
            ZStack(alignment: .topLeading) {
                Color.clear
                    .onAppear { compactStatsWidth = proxy.size.width - 24 - 16 }
                    .onChange(of: proxy.size.width) { compactStatsWidth = $0 - 24 - 16 }
                VStack(alignment: .leading, spacing: short ? 8 : 10) {
                    topBar(compact: compact)
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 8) {
                            empireButtons(compact: compact)
                            // Collapsed, the hint is a small button that sits with the empire buttons
                            if let tutorial = hud.tutorial, tutorial.collapsed || (short && selected) {
                                TutorialHint(tutorial: tutorial, collapsedOverride: true, perform: perform)
                            } else if let tutorial = hud.tutorial, short {
                                TutorialHint(tutorial: tutorial, perform: perform)
                                    .frame(maxWidth: 360, alignment: .leading)
                            }
                        }
                        Spacer(minLength: 12)
                        NextTurnButton(nextTurn: hud.nextTurn) { perform("nextTurn", .medium) }
                    }
                    if let tutorial = hud.tutorial, !tutorial.collapsed, !short {
                        TutorialHint(tutorial: tutorial, perform: perform)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, short ? 4 : 6)
                // Never wider than the screen, whatever a long label asks for
                .frame(width: proxy.size.width, alignment: .leading)

                VStack(alignment: .leading, spacing: short ? 8 : 10) {
                    if let battle = hud.battle {
                        BattleCard(battle: battle) { perform("battle:\($0)", .heavy) }
                            .frame(maxWidth: compact ? .infinity : 460)
                            .padding(.trailing, compact ? 12 : 0)
                            .interactiveRegion("battle")
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                    if short && !compact && hud.unit != nil && !hud.unitActions.isEmpty {
                        // Landscape phone: actions beside the unit card keep the middle of the map clear
                        HStack(alignment: .bottom, spacing: 10) {
                            selectionCard(compact: phone)
                            unitActions(maxWidth: max(200, proxy.size.width - 24 - 300 - 10 - 64))
                        }
                    } else {
                        if !hud.unitActions.isEmpty {
                            unitActions(maxWidth: compact ? proxy.size.width - 24 : min(620, proxy.size.width * (short ? 0.62 : 0.6)))
                        }
                        // Portrait phone: the card leaves room for the folded minimap button beside it
                        selectionCard(compact: phone)
                            .padding(.trailing, compact && selected ? HudIconButton.size + 8 : 0)
                    }
                }
                .padding(.leading, 12)
                .padding(.bottom, short ? 4 : 8)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .animation(.spring(response: 0.35, dampingFraction: 0.85), value: hud.unit?.name)

                VStack(alignment: .trailing, spacing: 8) {
                    // Phones only show the tile card when nothing else is selected
                    if let tile = hud.tile, !(phone && selected) {
                        TileCard(tile: tile, compact: short)
                            .frame(maxWidth: phone ? 210 : 260)
                            .interactiveRegion("tileCard")
                            .transition(.opacity)
                    }
                    // On phones a selected unit or city fills the bottom; the map folds into a button
                    MinimapCard(store: minimap, compact: phone, folded: phone && selected, send: send)
                }
                .padding(.trailing, 12)
                .padding(.bottom, short ? 4 : 8)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
        }
    }

    // MARK: Top bar

    /// Room for the stats row inside the portrait top bar card
    @State private var compactStatsWidth: CGFloat = 360

    private func topBar(compact: Bool) -> some View {
        Group {
            if compact {
                // Portrait iPhone: identity row, then the stats on their own row
                VStack(spacing: 6) {
                    HStack(spacing: 12) {
                        menuButton
                        civTitle
                        Spacer(minLength: 4)
                        overviewButton
                    }
                    stats
                        .shrinkToFit(maxWidth: compactStatsWidth)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 2)
                }
                .padding(8)
                .card(cornerRadius: 24)
            } else {
                HStack(spacing: 14) {
                    menuButton
                    civTitle
                    Spacer(minLength: 4)
                    stats
                    Spacer(minLength: 4)
                    overviewButton
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .capsuleCard()
            }
        }
        .interactiveRegion("topBar")
    }

    private var menuButton: some View {
        HudIconButton(symbol: "line.3.horizontal", label: L("Menu")) {
            if showMenu {
                // Still marked open (its close was lost): present it anew
                showMenu = false
                DispatchQueue.main.async { showMenu = true }
            } else {
                showMenu = true
            }
            perform("menu:opened", .light)
        }
    }

    private var overviewButton: some View {
        HudIconButton(symbol: "chart.bar.xaxis", label: L("Overview")) { perform("overview", .light) }
    }

    private var civTitle: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(hud.civName)
                .font(Theme.display(17, weight: .semibold))
                .lineLimit(1)
            Text("\(L("Turn")) \(hud.turn)\(hud.turnLimit.map { "/\($0)" } ?? "") · \(hud.year)")
                .font(Theme.number(.caption2, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .layoutPriority(1)
    }

    private var stats: some View {
        HStack(spacing: 14) {
            StatChip(stat: "Gold", value: "\(hud.stats.gold)", rate: signed(hud.stats.goldPerTurn)) { perform("stat:Gold", .light) }
            StatChip(stat: "Science", value: signed(hud.stats.science)) { perform("stat:Science", .light) }
            StatChip(stat: hud.stats.happiness < 0 ? "Malcontent" : "Happiness",
                     value: "\(hud.stats.happiness)",
                     rate: hud.stats.goldenAge ? "Golden Age" : nil,
                     valueColor: hud.stats.happiness < 0 ? Palette.unhappiness : nil) { perform("stat:Happiness", .light) }
            StatChip(stat: "Culture", value: signed(hud.stats.culture), rate: policyHint,
                     rateColor: hud.stats.turnsToPolicy == 0 ? Palette.culture : nil) { perform("stat:Culture", .light) }
            if hud.stats.religionEnabled {
                StatChip(stat: "Faith", value: "\(hud.stats.faith)", rate: signed(hud.stats.faithPerTurn)) { perform("stat:Faith", .light) }
            }
        }
        .fixedSize()
    }

    private var policyHint: String? {
        switch hud.stats.turnsToPolicy {
        case 0: return L("Policy ready")
        case let turns where turns > 0: return "\(turns)t"
        default: return nil
        }
    }

    // MARK: Empire buttons

    private func empireButtons(compact: Bool) -> some View {
        HStack(spacing: 8) {
            if hud.research.available && compact {
                // Portrait phone: next turn takes the row's width, so research is an icon with its turns
                Button { perform("research", .light) } label: {
                    GameIcon(name: hud.research.icon ?? "", fallback: "flask.fill", tint: .white)
                        .frame(width: 22, height: 22)
                        .frame(width: HudIconButton.size, height: HudIconButton.size)
                        .background(CardBackground(shape: Circle(), tint: hud.research.needsChoice ? Palette.science.opacity(0.9) : Palette.science.opacity(0.35)))
                        .overlay(alignment: .bottom) {
                            if let turns = hud.research.turns {
                                Text("\(turns)t")
                                    .font(.system(size: 10, weight: .heavy, design: .rounded))
                                    .padding(.horizontal, 5)
                                    .frame(minHeight: 16)
                                    .background(Capsule().fill(Palette.science))
                                    .fixedSize()
                                    .offset(y: 6)
                            }
                        }
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(hud.research.name ?? L("Technologies"))
                .interactiveRegion("research")
            } else if hud.research.available {
                Button { perform("research", .light) } label: {
                    HStack(spacing: 10) {
                        GameIcon(name: hud.research.icon ?? "", fallback: "flask.fill", tint: .white)
                            .frame(width: 20, height: 20)
                            .padding(7)
                            .background(Circle().fill(Palette.science.opacity(0.35)))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(hud.research.name ?? L(hud.research.needsChoice ? "Pick a tech" : "Technologies"))
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                            if let turns = hud.research.turns {
                                Text(L("[\(turns)] turns"))
                                    .font(Theme.number(.caption2, weight: .medium))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                    .fixedSize()
                            }
                        }
                    }
                    .padding(.leading, 5)
                    .padding(.trailing, 16)
                    .frame(minHeight: 46)
                    .capsuleCard(tint: hud.research.needsChoice ? Palette.science.opacity(0.9) : nil)
                }
                .buttonStyle(PressableStyle())
                .interactiveRegion("research")
            }
            if hud.showPolicies {
                HudIconButton(symbol: "scroll.fill", label: L("Policies"), material: true) { perform("policies", .light) }
                    .interactiveRegion("policies")
            }
            if hud.showDiplomacy {
                HudIconButton(symbol: "globe.europe.africa.fill", label: L("Diplomacy"), material: true) { perform("diplomacy", .light) }
                    .interactiveRegion("diplomacy")
            }
            if !hud.notifications.isEmpty {
                HudIconButton(symbol: "bell.fill", label: L("Notifications"), material: true) { showNotifications = true }
                    .overlay(alignment: .topTrailing) {
                        Text("\(hud.notifications.count)")
                            .font(.system(size: 10, weight: .heavy, design: .rounded))
                            .foregroundStyle(Theme.ink)
                            .padding(.horizontal, 5)
                            .frame(minWidth: 18, minHeight: 18)
                            .background(Capsule().fill(Theme.accentGradient))
                            .offset(x: 4, y: -2)
                    }
                    .interactiveRegion("notifications")
            }
            if hud.canUndo {
                HudIconButton(symbol: "arrow.uturn.backward", label: L("Undo"), material: true) { perform("undo", .medium) }
                    .interactiveRegion("undo")
            }
        }
    }

    // MARK: Selection

    @ViewBuilder
    private func selectionCard(compact: Bool) -> some View {
        if let unit = hud.unit {
            UnitCard(unit: unit, hasIdleUnits: hud.hasIdleUnits, compact: compact, perform: perform)
                .interactiveRegion("unitCard")
                .transition(.move(edge: .leading).combined(with: .opacity))
        } else if let city = hud.city {
            CityCard(city: city, perform: perform)
                .interactiveRegion("cityCard")
                .transition(.move(edge: .leading).combined(with: .opacity))
        } else if hud.hasIdleUnits {
            Button { perform("unit:next", .light) } label: {
                HStack(spacing: 8) {
                    Image(systemName: "figure.walk")
                    Text("\(hud.idleUnits) idle")
                        .font(Theme.number(.subheadline))
                    Image(systemName: "chevron.right").font(.caption.weight(.bold)).opacity(0.6)
                }
                .padding(.horizontal, 16)
                .frame(height: 44)
                .capsuleCard()
            }
            .buttonStyle(PressableStyle())
            .interactiveRegion("idleUnits")
        }
    }

    // MARK: Unit actions

    private func unitActions(maxWidth: CGFloat) -> some View {
        let buttonWidth: CGFloat = 66
        let width = min(CGFloat(hud.unitActions.count) * (buttonWidth + 4) + 12, maxWidth)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(Array(hud.unitActions.enumerated()), id: \.offset) { index, action in
                    Button { perform("action:\(hud.revision):\(index):\(action.type)", .light) } label: {
                        VStack(spacing: 5) {
                            Image(systemName: Symbols.unitAction(action.type))
                                .font(.system(size: 19, weight: .semibold))
                                .foregroundStyle(action.current ? Theme.ink : .white)
                                .frame(width: 38, height: 38)
                                .background(
                                    Circle().fill(action.current ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.white.opacity(0.10)))
                                )
                            Text(action.title)
                                .font(.system(size: 10, weight: .semibold))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .minimumScaleFactor(0.8)
                                .frame(height: 24, alignment: .top)
                        }
                        .frame(width: buttonWidth)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle())
                    .disabled(!action.enabled)
                    .opacity(action.enabled ? 1 : 0.4)
                    .accessibilityLabel(action.title)
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 8)
        }
        .frame(width: width)
        .card(cornerRadius: 22)
        .interactiveRegion("unitActions")
    }

    private func signed(_ value: Int) -> String {
        value > 0 ? "+\(value)" : "\(value)"
    }
}

// MARK: - Menu

/// The in-game menu as a drawer from the leading edge, replacing the Scene2D menu popup.
struct GameMenuDrawer: View {
    let hud: WorldHudState
    let close: () -> Void
    let select: (String) -> Void

    @State private var shown = false

    var body: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(shown ? 0.45 : 0)
                .ignoresSafeArea()
                .onTapGesture { dismiss() }

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        Emblem(size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(hud.civName).font(Theme.display(22)).lineLimit(1)
                            Text("\(L("Turn")) \(hud.turn)\(hud.turnLimit.map { "/\($0)" } ?? "") · \(hud.year)")
                                .font(Theme.number(.caption, weight: .medium))
                                .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        SmallIconButton(symbol: "xmark") { dismiss() }
                    }

                    section("Game", rows: [
                        hud.menu.showSave ? Row(id: "menu:save", title: L("Save game"), symbol: "square.and.arrow.down.fill", color: Palette.science) : nil,
                        Row(id: "menu:load", title: L("Load game"), symbol: "folder.fill", color: Theme.accent),
                        Row(id: "menu:newGame", title: L("Start new game"), symbol: "plus", color: Palette.happiness),
                        Row(id: "menu:mainMenu", title: L("Main menu"), symbol: "house.fill", color: Color(white: 0.75)),
                    ])
                    section("Empire", rows: [
                        Row(id: "overview", title: L("Empire overview"), symbol: "chart.bar.xaxis", color: Theme.accent),
                        Row(id: "menu:victory", title: L("Victory status"), symbol: "trophy.fill", color: Palette.gold),
                        hud.menu.showDiplomacy ? Row(id: "diplomacy", title: L("Diplomacy"), symbol: "globe.europe.africa.fill", color: Palette.science) : nil,
                        Row(id: "menu:civilopedia", title: L("Civilopedia"), symbol: "book.closed.fill", color: Palette.culture),
                    ])
                    section("Settings", rows: [
                        Row(id: "menu:options", title: L("Options"), symbol: "gearshape.fill", color: Color(white: 0.75)),
                        hud.menu.showMusic ? Row(id: "menu:music", title: L("Music"), symbol: "music.note", color: Palette.culture) : nil,
                    ])
                }
                .padding(18)
            }
            .frame(width: 320)
            .frame(maxHeight: .infinity)
            .background(
                CardBackground(shape: RoundedRectangle(cornerRadius: 30, style: .continuous))
                    .ignoresSafeArea(edges: [.top, .bottom, .leading])
            )
            .offset(x: shown ? 0 : -360)
        }
        // Takes the whole screen's touches only while open; a closing drawer must not swallow taps
        .interactiveRegion("menuDrawer", enabled: shown)
        .onAppear {
            withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) { shown = true }
        }
    }

    private struct Row: Identifiable {
        let id: String
        let title: String
        let symbol: String
        let color: Color
    }

    private func section(_ title: String, rows: [Row?]) -> some View {
        let rows = rows.compactMap { $0 }
        return VStack(alignment: .leading, spacing: 6) {
            SectionTitle(title)
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    if index > 0 { Divider().overlay(Theme.hairline).padding(.leading, 56) }
                    Button { select(row.id) } label: {
                        HStack(spacing: 14) {
                            IconTile(symbol: row.symbol, color: row.color, size: 32)
                            Text(row.title)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.white)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.35))
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 52)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        }
    }

    private func dismiss() {
        guard shown else { return }
        withAnimation(.easeIn(duration: 0.2)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { close() }
    }
}

// MARK: - Cards

/// The current tutorial task as a compact hint; tapping collapses it to a light bulb, and the
/// close button turns tips off.
struct TutorialHint: View {
    let tutorial: WorldHudState.Tutorial
    /// Show the bulb even when the player left the hint open, e.g. when space is short
    var collapsedOverride = false
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var confirmingOff = false

    var body: some View {
        Group {
            if tutorial.collapsed || collapsedOverride {
                Button { perform("tutorial:toggle", .light) } label: {
                    Image(systemName: "lightbulb.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Palette.gold)
                        .frame(width: 44, height: 44)
                        .background(CardBackground(shape: Circle()))
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(tutorial.title)
            } else {
                HStack(alignment: .top, spacing: 10) {
                    Button { perform("tutorial:toggle", .light) } label: {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "lightbulb.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Palette.gold)
                                .frame(width: 28, height: 28)
                                .background(Circle().fill(Palette.gold.opacity(0.18)))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(tutorial.title)
                                    .font(.subheadline.weight(.semibold))
                                Text(tutorial.text)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(L("Collapse"))

                    Button { confirmingOff = true } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white.opacity(0.7))
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(Color.white.opacity(0.10)))
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(L("Turn off tips"))
                }
                .padding(.leading, 10)
                .padding(.vertical, 6)
                .padding(.trailing, 2)
                .frame(maxWidth: 420, alignment: .leading)
                .card(cornerRadius: 18, tint: Palette.gold.opacity(0.25))
                .confirmationDialog(L("Turn off tips?"), isPresented: $confirmingOff, titleVisibility: .visible) {
                    Button(L("Turn off tips"), role: .destructive) { perform("tutorial:off", .medium) }
                    Button(L("Just hide this one")) { perform("tutorial:toggle", .light) }
                    Button(L("Cancel"), role: .cancel) {}
                } message: {
                    Text(L("You can turn them back on in Options."))
                }
            }
        }
        .interactiveRegion("tutorial")
    }
}

struct UnitCard: View {
    let unit: WorldHudState.Unit
    let hasIdleUnits: Bool
    let compact: Bool
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            CivBadge(icon: unit.icon, fallback: "person.fill", outer: unit.outerColor, inner: unit.innerColor,
                     health: Double(unit.health) / 100, size: 58)

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(unit.name)
                        .font(Theme.display(18, weight: .semibold))
                        .lineLimit(1)
                    if unit.health < 100 {
                        Tag(text: "\(unit.health) HP", color: unit.health < 40 ? Palette.unhappiness : Palette.gold)
                    }
                }
                HStack(spacing: 10) {
                    UnitStat(symbol: "figure.walk", text: unit.movement)
                    if unit.strength > 0 { UnitStat(symbol: "shield.lefthalf.filled", text: "\(unit.strength)") }
                    if unit.rangedStrength > 0 { UnitStat(symbol: "scope", text: "\(unit.rangedStrength)") }
                    if unit.range > 0 { UnitStat(symbol: "arrow.up.right", text: "\(unit.range)") }
                }
                if unit.xp >= 0 {
                    xpRow
                }
                if !unit.promotions.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(unit.promotions, id: \.name) { promotion in
                            GameIcon(name: promotion.icon, fallback: "star.fill", tint: Palette.gold)
                                .frame(width: 14, height: 14)
                                .padding(3)
                                .background(Circle().fill(Color.white.opacity(0.1)))
                                .accessibilityLabel(promotion.name)
                        }
                    }
                }
            }
            .frame(minWidth: compact ? 0 : 170, alignment: .leading)

            VStack(spacing: 6) {
                SmallIconButton(symbol: "xmark") { perform("unit:close", .light) }
                if hasIdleUnits {
                    SmallIconButton(symbol: "chevron.right") { perform("unit:next", .light) }
                }
            }
        }
        .padding(10)
        .padding(.trailing, 2)
        .card(cornerRadius: 24)
    }

    private var xpRow: some View {
        HStack(spacing: 8) {
            Text("XP")
                .font(.system(size: 10, weight: .heavy))
                .foregroundStyle(.secondary)
            Meter(value: Double(unit.xp) / Double(max(unit.xpNext, 1)), color: unit.canPromote ? Palette.gold : Palette.science)
                .frame(width: 70)
            Text("\(unit.xp)/\(unit.xpNext)")
                .font(Theme.number(.caption2, weight: .medium))
                .foregroundStyle(.secondary)
            if unit.canPromote {
                Button { perform("unit:promote", .medium) } label: {
                    Label(L("Promote"), systemImage: "star.fill")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 10)
                        .frame(height: 26)
                        .modifier(PrimaryCapsule())
                }
                .buttonStyle(PressableStyle())
            }
        }
    }
}

struct CityCard: View {
    let city: WorldHudState.City
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        HStack(spacing: 12) {
            CivBadge(icon: "", fallback: "building.columns.fill", outer: city.outerColor, inner: city.innerColor,
                     health: Double(city.health) / Double(max(city.maxHealth, 1)), size: 58)
            VStack(alignment: .leading, spacing: 6) {
                Text(city.name)
                    .font(Theme.display(18, weight: .semibold))
                    .lineLimit(1)
                HStack(spacing: 10) {
                    UnitStat(symbol: "person.2.fill", text: "\(city.population)")
                    UnitStat(symbol: "shield.lefthalf.filled", text: "\(city.strength)")
                    if city.bombard > 0 { UnitStat(symbol: "scope", text: "\(city.bombard)") }
                }
                // Numbers never wrap; a long city name truncates instead
                .fixedSize()
            }
            if city.canEnter {
                Button { perform("city:enter", .medium) } label: {
                    HStack(spacing: 6) {
                        Text(L("Enter"))
                        Image(systemName: "arrow.right")
                    }
                    .font(.subheadline.weight(.bold))
                    .padding(.horizontal, 16)
                    .frame(height: 40)
                    .modifier(PrimaryCapsule())
                    .fixedSize()
                }
                .buttonStyle(PressableStyle())
            }
            SmallIconButton(symbol: "xmark") { perform("unit:close", .light) }
        }
        .padding(10)
        .card(cornerRadius: 24)
    }
}

struct TileCard: View {
    let tile: WorldHudState.Tile
    /// Fewer lines, for short landscape screens
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(tile.title)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            if !tile.stats.isEmpty {
                StatRow(stats: tile.stats, size: 14)
            }
            ForEach(compact ? Array(tile.lines.prefix(2)) : tile.lines, id: \.self) { line in
                Text(line.text)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(cornerRadius: 18)
    }
}

// MARK: - Pieces

/// Unit or city emblem: the icon tinted in the civ's inner colour on its outer colour, ringed by health.
struct CivBadge: View {
    let icon: String
    let fallback: String
    let outer: String
    let inner: String
    let health: Double
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(colors: [Color(hex: outer).opacity(0.95), Color(hex: outer).opacity(0.65)],
                                   center: .topLeading, startRadius: 0, endRadius: size)
                )
                .padding(5)
            GameIcon(name: icon, fallback: fallback, tint: Color(hex: inner))
                .padding(size * 0.25)
            Circle()
                .stroke(Color.white.opacity(0.12), lineWidth: 3)
            Circle()
                .trim(from: 0, to: min(max(health, 0), 1))
                .stroke(healthColor, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: size, height: size)
    }

    private var healthColor: Color {
        health > 0.66 ? Palette.happiness : health > 0.33 ? Palette.gold : Palette.unhappiness
    }
}

struct UnitStat: View {
    let symbol: String
    let text: String

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: Symbols.named(symbol, fallback: "circle.fill"))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.secondary)
            Text(text)
                .font(Theme.number(.footnote))
        }
    }
}

/// Stat icons from the game's atlas with their values.
struct StatRow: View {
    let stats: [StatValue]
    var size: CGFloat = 14

    var body: some View {
        HStack(spacing: 8) {
            ForEach(stats, id: \.stat) { stat in
                HStack(spacing: 2) {
                    StatIcon(stat: stat.stat, size: size)
                    if let value = stat.value {
                        Text("\(value)")
                            .font(Theme.number(.caption))
                    }
                }
            }
        }
    }
}

struct StatIcon: View {
    let stat: String
    var size: CGFloat = 16

    var body: some View {
        // The game's stat icons are already coloured discs; tinting would flatten them
        GameIcon(name: "StatIcons/\(stat)", fallback: Symbols.stat(stat))
            .foregroundStyle(Color.stat(stat))
            .frame(width: size, height: size)
    }
}

struct StatChip: View {
    let stat: String
    let value: String
    var rate: String? = nil
    var valueColor: Color? = nil
    var rateColor: Color? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                StatIcon(stat: stat, size: 18)
                Text(value)
                    .font(Theme.number(.subheadline))
                    .foregroundStyle(valueColor ?? .white)
                if let rate {
                    Text(rate)
                        .font(Theme.number(.caption2, weight: .medium))
                        .foregroundStyle(rateColor ?? .secondary)
                }
            }
            .frame(minHeight: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

struct NextTurnButton: View {
    let nextTurn: WorldHudState.NextTurn
    let action: () -> Void

    @State private var pulse = false

    private var isReady: Bool { nextTurn.action == "NextTurn" }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: Symbols.nextTurn(nextTurn.action))
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(isReady ? Theme.ink.opacity(0.15) : Color(hex: nextTurn.color).opacity(0.35)))
                VStack(alignment: .leading, spacing: 0) {
                    Text(nextTurn.title)
                        .font(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    if let subtitle = nextTurn.subtitle {
                        Text(subtitle)
                            .font(.caption2.weight(.medium))
                            .lineLimit(1)
                            .opacity(0.75)
                    }
                }
            }
            .padding(.leading, 8)
            .padding(.trailing, 18)
            .frame(minWidth: 150, minHeight: 52, alignment: .leading)
            .modifier(NextTurnStyle(isReady: isReady, color: Color(hex: nextTurn.color)))
            .scaleEffect(isReady && pulse ? 1.03 : 1)
        }
        .buttonStyle(PressableStyle())
        .disabled(!nextTurn.enabled)
        .opacity(nextTurn.enabled ? 1 : 0.55)
        .interactiveRegion("nextTurn")
        .onAppear { startPulse() }
        .onChange(of: isReady) { _ in startPulse() }
    }

    private func startPulse() {
        pulse = false
        guard isReady else { return }
        withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = true }
    }
}

private struct NextTurnStyle: ViewModifier {
    let isReady: Bool
    let color: Color

    func body(content: Content) -> some View {
        if isReady {
            content.modifier(PrimaryCapsule())
        } else {
            content
                .foregroundStyle(.white)
                .capsuleCard(tint: color.opacity(0.55))
        }
    }
}

struct HudIconButton: View {
    let symbol: String
    let label: String
    var material = false
    let action: () -> Void

    static let size: CGFloat = 44

    var body: some View {
        Button(action: action) {
            Image(systemName: Symbols.named(symbol))
                .font(.system(size: 17, weight: .semibold))
                .frame(width: Self.size, height: Self.size)
                .background {
                    if material {
                        CardBackground(shape: Circle())
                    } else {
                        Circle().fill(Color.white.opacity(0.08))
                    }
                }
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(label)
    }
}

struct SmallIconButton: View {
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(.white.opacity(0.8))
                .frame(width: 30, height: 30)
                .background(Circle().fill(Color.white.opacity(0.10)))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

// MARK: - Battle preview

/// Attacker against defender, line by line as the game's battle preview computes it, with the attack button.
struct BattleCard: View {
    let battle: WorldHudState.Battle
    let press: (Int) -> Void

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(battle.rows.enumerated()), id: \.offset) { index, row in
                if index == 0 {
                    HStack(alignment: .firstTextBaseline) {
                        Text(row.left ?? "").font(Theme.display(16, weight: .semibold)).lineLimit(1)
                        Spacer(minLength: 8)
                        Image(systemName: "bolt.fill").font(.caption).foregroundStyle(Palette.unhappiness)
                        Spacer(minLength: 8)
                        Text(row.right ?? "").font(Theme.display(16, weight: .semibold)).lineLimit(1)
                    }
                    Divider().overlay(Theme.hairline)
                } else {
                    HStack(alignment: .top) {
                        GameText(row.left ?? "")
                            .frame(maxWidth: .infinity, alignment: .leading)
                        GameText(row.right ?? "")
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .multilineTextAlignment(.trailing)
                    }
                    .font(Theme.number(.footnote, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                }
            }
            ForEach(Array(battle.buttons.enumerated()), id: \.offset) { index, button in
                Button { press(index) } label: {
                    Label(button.label, systemImage: "bolt.fill")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(Capsule().fill(Palette.unhappiness.opacity(button.enabled ? 0.85 : 0.3)))
                }
                .buttonStyle(PressableStyle())
                .disabled(!button.enabled)
            }
        }
        .padding(12)
        .card(cornerRadius: 20, tint: Palette.unhappiness.opacity(0.25))
    }
}

// MARK: - Notifications

/// This turn's notifications; tapping one does what tapping it on the map did.
struct NotificationsPanel: View {
    let notifications: [WorldHudState.Notification]
    let close: () -> Void
    let select: (Int) -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(0.4).ignoresSafeArea().onTapGesture(perform: close)
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(L("Notifications")).font(Theme.display(20))
                    Spacer()
                    SmallIconButton(symbol: "xmark", action: close)
                }
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        ForEach(Array(notifications.enumerated()), id: \.offset) { index, note in
                            if index > 0 { Divider().overlay(Theme.hairline).padding(.leading, 52) }
                            Button { select(index) } label: {
                                HStack(alignment: .top, spacing: 12) {
                                    OptionBadge(icon: note.icon, symbol: note.icon == nil ? "bell.fill" : nil,
                                                background: note.background, tint: note.tint, size: 36)
                                    GameText(note.text)
                                        .font(.subheadline)
                                        .foregroundStyle(.white)
                                        .multilineTextAlignment(.leading)
                                        .fixedSize(horizontal: false, vertical: true)
                                    Spacer(minLength: 0)
                                    if note.actionable {
                                        Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(.tertiary)
                                    }
                                }
                                .padding(.vertical, 10)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: 420, maxHeight: 520)
            .card(cornerRadius: 24)
            .padding(.top, 80)
            .padding(.horizontal, 12)
        }
        .interactiveRegion("notificationsPanel")
    }
}


/// Lays out content at its natural size and scales it down when it is wider than `maxWidth`,
/// e.g. the stats row when gold and science grow to several digits on a narrow phone.
private struct ShrinkToFit: ViewModifier {
    let maxWidth: CGFloat
    @State private var natural: CGSize = .zero

    func body(content: Content) -> some View {
        let scale = natural.width > maxWidth && natural.width > 0 ? maxWidth / natural.width : 1
        content
            .fixedSize()
            .background(GeometryReader { proxy in
                Color.clear.preference(key: NaturalSizeKey.self, value: proxy.size)
            })
            .onPreferenceChange(NaturalSizeKey.self) { natural = $0 }
            .scaleEffect(scale)
            .frame(width: natural == .zero ? nil : natural.width * scale,
                   height: natural == .zero ? nil : natural.height * scale)
    }
}

private struct NaturalSizeKey: PreferenceKey {
    static var defaultValue: CGSize = .zero
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) { value = nextValue() }
}

extension View {
    func shrinkToFit(maxWidth: CGFloat) -> some View { modifier(ShrinkToFit(maxWidth: maxWidth)) }
}
