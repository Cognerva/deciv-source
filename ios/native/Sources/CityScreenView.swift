import SwiftUI
import UIKit

/// Native city screen. The city's tile map stays in Scene2D underneath (tap tiles to select,
/// tap the population icons to work them); everything else is drawn here.
struct CityScreenView: View {
    let city: CityState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var showPicker = false
    @State private var detailFor: String?
    @State private var tab: Tab = .production

    enum Tab: String, CaseIterable {
        case production = "Production"
        case buildings = "Buildings"
        case citizens = "Citizens"
    }

    var body: some View {
        GeometryReader { proxy in
            let compact = proxy.size.width < 640
            ZStack(alignment: .top) {
                if compact {
                    compactLayout(proxy: proxy)
                } else if proxy.size.width < 900 {
                    mediumLayout(proxy: proxy)
                } else {
                    wideLayout(proxy: proxy)
                }

                if showPicker {
                    pickerOverlay(compact: compact)
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                        .zIndex(2)
                }
                if let selection = city.selectedConstruction, selection.id == detailFor {
                    detailOverlay(selection)
                        .transition(.opacity)
                        .zIndex(3)
                }
            }
            .animation(.spring(response: 0.32, dampingFraction: 0.9), value: showPicker)
            .animation(.easeOut(duration: 0.2), value: detailFor)
        }
    }

    // MARK: Layouts

    /// iPad and other wide windows: production | header, map, tile | buildings and citizens
    private func wideLayout(proxy: GeometryProxy) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                exitButton
                productionPanel(maxHeight: proxy.size.height - 70)
            }
            .frame(width: 320)

            VStack(spacing: 10) {
                header
                statsStrip
                Spacer(minLength: 0)
                if let tile = city.selectedTile {
                    tileCard(tile)
                        .frame(maxWidth: 420)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .frame(maxWidth: .infinity)

            sidePanel(maxHeight: proxy.size.height * 0.75)
                .frame(width: 300)
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
        .padding(.bottom, 8)
    }

    /// Landscape iPhone and narrow iPad windows: production on the left, everything else on the right
    private func mediumLayout(proxy: GeometryProxy) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                exitButton
                productionPanel(maxHeight: proxy.size.height - 60)
            }
            .frame(width: 290)

            Spacer(minLength: 0)

            VStack(spacing: 8) {
                header
                statsStrip
                if let tile = city.selectedTile {
                    tileCard(tile)
                        .transition(.move(edge: .top).combined(with: .opacity))
                } else {
                    sidePanel(maxHeight: proxy.size.height * 0.5)
                }
            }
            .frame(width: 330)
        }
        .padding(.horizontal, 8)
        .padding(.top, 4)
        .padding(.bottom, 6)
    }

    private func compactLayout(proxy: GeometryProxy) -> some View {
        VStack(spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                exitButton
                header
            }
            statsStrip
            Spacer(minLength: 0)
            if let tile = city.selectedTile {
                tileCard(tile)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            VStack(spacing: 10) {
                Picker("", selection: $tab) {
                    ForEach(Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                Group {
                    switch tab {
                    case .production: productionContent()
                    case .buildings: buildingsContent()
                    case .citizens: citizensContent()
                    }
                }
                .frame(height: proxy.size.height * 0.34)
            }
            .padding(12)
            .card(cornerRadius: 26)
            .interactiveRegion("cityPanel")
        }
        .padding(.horizontal, 10)
        .padding(.top, 4)
        .padding(.bottom, 6)
    }

    // MARK: Header

    private var exitButton: some View {
        Button { perform("exit", .light) } label: {
            HStack(spacing: 6) {
                Image(systemName: "chevron.left").font(.system(size: 15, weight: .bold))
                Text(L("Map")).font(.subheadline.weight(.semibold))
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .capsuleCard()
        }
        .buttonStyle(PressableStyle())
        .interactiveRegion("exit")
    }

    private var header: some View {
        HStack(spacing: 10) {
            if city.canPage {
                SmallIconButton(symbol: "chevron.left") { perform("prev", .light) }
            }
            ZStack {
                Circle().fill(Color(hex: city.outerColor))
                Text("\(city.population)")
                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color(hex: city.innerColor))
            }
            .frame(width: 38, height: 38)
            .overlay(Circle().strokeBorder(Color(hex: city.innerColor).opacity(0.6), lineWidth: 2))

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if city.isCapital { Image(systemName: "star.fill").font(.caption).foregroundStyle(Palette.gold) }
                    Text(city.name)
                        .font(Theme.display(20))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    if city.isPuppet { Tag(text: "Puppet", color: .gray) }
                    if city.isRazing { Tag(text: "Razing", symbol: "flame.fill", color: Palette.unhappiness) }
                    if city.inResistance { Tag(text: "Resistance", color: Palette.unhappiness) }
                }
                growthLine
            }
            Spacer(minLength: 0)
            if city.canPage {
                SmallIconButton(symbol: "chevron.right") { perform("next", .light) }
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
        .card(cornerRadius: 22, tint: Color(hex: city.outerColor).opacity(0.5))
        .interactiveRegion("header")
    }

    private var growthLine: some View {
        HStack(spacing: 8) {
            StatIcon(stat: "Food", size: 12)
            Meter(value: Double(city.growth.foodStored) / Double(max(city.growth.foodNeeded, 1)),
                  color: city.growth.starving ? Palette.unhappiness : Color.stat("Food"), height: 4)
                .frame(width: 60)
            Text(growthText)
                .font(Theme.number(.caption2, weight: .medium))
                .foregroundStyle(city.growth.starving ? Palette.unhappiness : .secondary)
                .lineLimit(1)
            if city.turnsToExpand > 0 {
                Text("· border in \(city.turnsToExpand)")
                    .font(Theme.number(.caption2, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }

    private var growthText: String {
        if city.growth.starving { return "Starving · \(city.growth.turns)t" }
        if city.growth.growing && city.growth.turns > 0 { return "Grows in \(city.growth.turns)" }
        return "Stagnant"
    }

    /// Yields; tapping one focuses citizens on it (tap again to clear), as in the Scene2D screen.
    private var statsStrip: some View {
        HStack(spacing: 0) {
            ForEach(city.stats, id: \.stat) { stat in
                let focusId = focusId(for: stat.stat)
                let focused = focusId != nil && focusId == city.focus
                Button {
                    if let focusId { perform("focus:\(focusId)", .light) }
                } label: {
                    VStack(spacing: 2) {
                        StatIcon(stat: stat.stat, size: 18)
                        Text("\(stat.value ?? 0)")
                            .font(Theme.number(.footnote))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(focused ? Color.stat(stat.stat).opacity(0.25) : .clear)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(focused ? Color.stat(stat.stat).opacity(0.7) : .clear)
                    )
                }
                .buttonStyle(PressableStyle())
                .disabled(!city.canChange || focusId == nil)
            }
        }
        .padding(4)
        .card(cornerRadius: 18)
        .interactiveRegion("stats")
    }

    private func focusId(for stat: String) -> String? {
        city.focusOptions.first { $0.stats.count == 1 && $0.stats.first?.stat == stat }?.id
    }

    // MARK: Panels

    private func productionPanel(maxHeight: CGFloat) -> some View {
        productionContent(scrolls: estimatedProductionHeight > maxHeight)
            .frame(maxHeight: estimatedProductionHeight > maxHeight ? maxHeight - 24 : nil)
            .padding(12)
            .card(cornerRadius: 24)
            .interactiveRegion("production")
    }

    private func sidePanel(maxHeight: CGFloat) -> some View {
        let scrolls = (tab == .citizens ? 320 : estimatedBuildingsHeight) > maxHeight - 60
        return VStack(spacing: 10) {
            Picker("", selection: $tab) {
                Text(Tab.buildings.rawValue).tag(Tab.buildings)
                Text(Tab.citizens.rawValue).tag(Tab.citizens)
            }
            .pickerStyle(.segmented)
            Group {
                if tab == .citizens { citizensContent(scrolls: scrolls) } else { buildingsContent(scrolls: scrolls) }
            }
            .frame(maxHeight: scrolls ? maxHeight - 60 : nil)
        }
        .padding(12)
        .card(cornerRadius: 24)
        .interactiveRegion("side")
        .onAppear { if tab == .production { tab = .buildings } }
    }

    /// Rough content heights, so panels size to their content and only scroll when they must;
    /// a SwiftUI scroll view always takes all the height it is offered.
    private var estimatedProductionHeight: CGFloat {
        CGFloat(30 + 130 + 56 + (city.queue.count > 1 ? 30 : 0) + max(city.queue.count - 1, 0) * 52)
    }

    private var estimatedBuildingsHeight: CGFloat {
        CGFloat(city.buildings.count * 30 + city.buildings.reduce(0) { $0 + $1.items.count } * 52 + 20)
    }

    // MARK: Production

    private func productionContent(scrolls: Bool = true) -> some View {
        OptionalScroll(scrolls: scrolls) {
            VStack(alignment: .leading, spacing: 10) {
                SectionTitle("Producing")
                if let current = city.queue.first {
                    currentConstruction(current)
                } else {
                    Text(L("Nothing in production"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 6)
                }

                if city.canChange {
                    Button { showPicker = true } label: {
                        Label(city.queue.isEmpty ? "Choose production" : "Add to queue", systemImage: "plus")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .modifier(PrimaryCapsule())
                    }
                    .buttonStyle(PressableStyle())
                }

                if city.queue.count > 1 {
                    SectionTitle("Queue")
                    ForEach(Array(city.queue.enumerated().dropFirst()), id: \.offset) { index, entry in
                        queueRow(entry, index: index)
                    }
                }
            }
        }
    }

    private func currentConstruction(_ entry: CityState.QueueEntry) -> some View {
        let item = availableItem(entry.id)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                ConstructionIcon(icon: entry.icon, size: 52)
                VStack(alignment: .leading, spacing: 5) {
                    Text(entry.name)
                        .font(Theme.display(17, weight: .semibold))
                        .lineLimit(2)
                    if entry.perpetual {
                        Text(L("Ongoing")).font(.caption).foregroundStyle(.secondary)
                    } else {
                        HStack(spacing: 6) {
                            Meter(value: entry.progress, color: Color.stat("Production"), height: 6)
                            Text(entry.turns > 0 ? "\(entry.turns)t" : "–")
                                .font(Theme.number(.caption))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer(minLength: 0)
                Button { detailFor = entry.id; perform("select:\(entry.id)", .light) } label: {
                    Image(systemName: "info.circle").font(.system(size: 18))
                        .foregroundStyle(.secondary)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(PressableStyle())
            }
            if let item, !item.buy.isEmpty {
                BuyButtons(options: item.buy) { stat in perform("buy:\(entry.id):\(stat)", .medium) }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.white.opacity(0.06)))
    }

    private func queueRow(_ entry: CityState.QueueEntry, index: Int) -> some View {
        HStack(spacing: 10) {
            ConstructionIcon(icon: entry.icon, size: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                if entry.turns > 0 {
                    Text(L("[\(entry.turns)] turns")).font(Theme.number(.caption2, weight: .medium)).foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            if entry.canRaise {
                SmallIconButton(symbol: "arrow.up") { perform("raise:\(index)", .light) }
            }
            if entry.canRemove {
                SmallIconButton(symbol: "xmark") { perform("remove:\(index)", .light) }
            }
        }
        .padding(.vertical, 2)
        .contextMenu {
            if entry.canLower { Button("Move down") { perform("lower:\(index)", .light) } }
            Button("Details") { detailFor = entry.id; perform("select:\(entry.id)", .light) }
        }
    }

    private func availableItem(_ id: String) -> CityState.Item? {
        for category in city.categories {
            if let item = category.items.first(where: { $0.id == id }) { return item }
        }
        return nil
    }

    // MARK: Buildings

    private func buildingsContent(scrolls: Bool = true) -> some View {
        OptionalScroll(scrolls: scrolls) {
            VStack(alignment: .leading, spacing: 10) {
                if city.buildings.isEmpty {
                    Text(L("No buildings yet"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                ForEach(city.buildings) { group in
                    SectionTitle(group.title)
                    ForEach(group.items) { building in
                        Button { detailFor = building.id; perform("select:\(building.id)", .light) } label: {
                            HStack(spacing: 10) {
                                ConstructionIcon(icon: building.icon, size: 32)
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 4) {
                                        Text(building.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                                        if building.free { Tag(text: "Free", color: Palette.happiness) }
                                    }
                                    StatRow(stats: building.stats, size: 11)
                                }
                                Spacer(minLength: 0)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
            }
        }
    }

    // MARK: Citizens

    private func citizensContent(scrolls: Bool = true) -> some View {
        OptionalScroll(scrolls: scrolls) {
            VStack(alignment: .leading, spacing: 12) {
                SectionTitle("Focus")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 80), spacing: 8)], spacing: 8) {
                    ForEach(city.focusOptions) { option in
                        let selected = option.id == city.focus
                        Button { perform("focus:\(option.id)", .light) } label: {
                            HStack(spacing: 3) {
                                if option.stats.isEmpty {
                                    Text(option.label).font(.caption.weight(.semibold)).lineLimit(1)
                                } else {
                                    ForEach(option.stats, id: \.stat) { StatIcon(stat: $0.stat, size: 16) }
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(selected ? Theme.accent.opacity(0.28) : Color.white.opacity(0.07))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(selected ? Theme.accent : .clear, lineWidth: 1.5)
                            )
                        }
                        .buttonStyle(PressableStyle())
                        .disabled(!city.canChange)
                    }
                }
                if city.freePopulation > 0 {
                    Label("\(city.freePopulation) unassigned citizen\(city.freePopulation == 1 ? "" : "s")", systemImage: "person.fill.questionmark")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Palette.gold)
                }
                HStack(spacing: 8) {
                    Button { perform("reset", .light) } label: {
                        Label(L("Reset citizens"), systemImage: "arrow.counterclockwise")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .frame(minHeight: 40)
                            .background(Capsule().fill(Color.white.opacity(0.08)))
                    }
                    .buttonStyle(PressableStyle())
                    Button { perform("avoidGrowth", .light) } label: {
                        Label(L("Avoid growth"), systemImage: city.avoidGrowth ? "checkmark.circle.fill" : "circle")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .frame(minHeight: 40)
                            .background(Capsule().fill(city.avoidGrowth ? Theme.accent.opacity(0.25) : Color.white.opacity(0.08)))
                    }
                    .buttonStyle(PressableStyle())
                }
                .disabled(!city.canChange)
                Text(L("Tap a tile's citizen on the map to work or free it."))
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
    }

    // MARK: Tile

    private func tileCard(_ tile: CityState.SelectedTile) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(tile.title).font(.subheadline.weight(.bold)).lineLimit(1)
                    if tile.isWorked { Tag(text: tile.isLocked ? "Worked · locked" : "Worked", color: Palette.happiness) }
                }
                if !tile.stats.isEmpty { StatRow(stats: tile.stats, size: 15) }
                if !tile.lines.isEmpty {
                    Text(tile.lines.map(\.text).joined(separator: " · "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 6) {
                if tile.canBuy {
                    Button { perform("tile:buy", .medium) } label: {
                        HStack(spacing: 4) {
                            Text(L("Buy"))
                            StatIcon(stat: "Gold", size: 14)
                            Text("\(tile.buyCost)").font(Theme.number(.subheadline))
                        }
                        .font(.subheadline.weight(.bold))
                        .padding(.horizontal, 12)
                        .frame(height: 36)
                        .modifier(PrimaryCapsule())
                    }
                    .buttonStyle(PressableStyle())
                    .disabled(!tile.canAffordBuy)
                    .opacity(tile.canAffordBuy ? 1 : 0.5)
                }
                if tile.canLock {
                    Button { perform(tile.isLocked ? "tile:unlock" : "tile:lock", .light) } label: {
                        Label(tile.isLocked ? "Unlock" : "Lock", systemImage: tile.isLocked ? "lock.open.fill" : "lock.fill")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .frame(height: 36)
                            .background(Capsule().fill(Color.white.opacity(0.1)))
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            SmallIconButton(symbol: "xmark") { perform("deselect", .light) }
        }
        .padding(.vertical, 8)
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .card(cornerRadius: 20)
        .interactiveRegion("tile")
    }

    // MARK: Picker

    private func pickerOverlay(compact: Bool) -> some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { showPicker = false }
            VStack(spacing: 0) {
                HStack {
                    Text(L("Production"))
                        .font(Theme.display(22))
                    Spacer()
                    Button("Done") { showPicker = false }
                        .font(.headline)
                        .foregroundStyle(Theme.accent)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                ScrollView(showsIndicators: true) {
                    LazyVStack(alignment: .leading, spacing: 6, pinnedViews: [.sectionHeaders]) {
                        ForEach(city.categories) { category in
                            Section {
                                ForEach(category.items) { item in
                                    PickerRow(item: item,
                                              add: { perform("add:\(item.id)", .medium); showPicker = false },
                                              addFirst: { perform("addFirst:\(item.id)", .medium); showPicker = false },
                                              info: { detailFor = item.id; perform("select:\(item.id)", .light) },
                                              buy: { stat in perform("buy:\(item.id):\(stat)", .medium) })
                                }
                            } header: {
                                Text(category.title.uppercased())
                                    .font(.caption.weight(.heavy))
                                    .tracking(1)
                                    .foregroundStyle(Theme.accent)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 6)
                                    .background(Theme.ink.opacity(0.92))
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 14)
                }
            }
            .frame(maxWidth: compact ? .infinity : 560, maxHeight: .infinity)
            .card(cornerRadius: 28)
            .padding(compact ? 8 : 24)
        }
        .interactiveRegion("picker")
    }

    // MARK: Detail

    private func detailOverlay(_ selection: CityState.Selection) -> some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { close() }
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    ConstructionIcon(icon: selection.icon, size: 56)
                    Text(selection.name).font(Theme.display(22)).lineLimit(2)
                    Spacer()
                    SmallIconButton(symbol: "xmark") { close() }
                }
                let description = Text(selection.description)
                    .font(.callout)
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(maxWidth: .infinity, alignment: .leading)
                // A scroll view would always take its maximum height; only long texts need one
                if selection.description.count > 360 {
                    ScrollView(showsIndicators: false) { description }
                        .frame(height: 280)
                } else {
                    description.fixedSize(horizontal: false, vertical: true)
                }
                if selection.sellGold > 0 {
                    Button { perform("sell:\(selection.id)", .medium) } label: {
                        HStack(spacing: 6) {
                            Text(L("Sell for"))
                            StatIcon(stat: "Gold", size: 15)
                            Text("\(selection.sellGold)").font(Theme.number(.subheadline))
                        }
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Capsule().fill(Palette.unhappiness.opacity(0.35)))
                    }
                    .buttonStyle(PressableStyle())
                    .disabled(!selection.canSell)
                    .opacity(selection.canSell ? 1 : 0.5)
                }
            }
            .padding(18)
            .frame(maxWidth: 460)
            .card(cornerRadius: 28)
            .padding(20)
        }
        .interactiveRegion("detail")
    }

    private func close() {
        detailFor = nil
        perform("deselect", .light)
    }
}

// MARK: - Pieces

private struct PickerRow: View {
    let item: CityState.Item
    let add: () -> Void
    let addFirst: () -> Void
    let info: () -> Void
    let buy: (String) -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: add) {
                HStack(spacing: 12) {
                    ConstructionIcon(icon: item.icon, size: 44)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(item.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(item.rejection == nil ? .white : .white.opacity(0.55))
                                .lineLimit(1)
                            if let hints = item.statHints, !hints.isEmpty {
                                StatRow(stats: hints, size: 11)
                            }
                        }
                        if let rejection = item.rejection {
                            Text(rejection)
                                .font(.caption2)
                                .foregroundStyle(Palette.unhappiness.opacity(0.9))
                                .lineLimit(2)
                        } else if !item.perpetual {
                            HStack(spacing: 6) {
                                Tag(text: item.turns > 0 ? L("[\(item.turns)] turns") : "–", symbol: "hourglass", color: .white)
                                Tag(text: "\(item.cost)", symbol: "hammer.fill", color: Color.stat("Production"))
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle())
            .disabled(!item.canAdd)

            if !item.buy.isEmpty {
                BuyButtons(options: item.buy, compact: true, buy: buy)
            }
            Button(action: info) {
                Image(systemName: "info.circle")
                    .font(.system(size: 17))
                    .foregroundStyle(.secondary)
                    .frame(width: 32, height: 44)
            }
            .buttonStyle(PressableStyle())
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(item.canAdd ? 0.06 : 0.025)))
        .contextMenu {
            if item.canAdd {
                Button("Add to queue", action: add)
                Button("Build next", action: addFirst)
            }
            Button("Details", action: info)
        }
    }
}

struct BuyButtons: View {
    let options: [CityState.BuyOption]
    var compact = false
    let buy: (String) -> Void

    var body: some View {
        HStack(spacing: 6) {
            ForEach(options, id: \.stat) { option in
                Button { buy(option.stat) } label: {
                    HStack(spacing: 4) {
                        if !compact { Text(L("Buy")).font(.caption.weight(.bold)) }
                        StatIcon(stat: option.stat, size: 13)
                        Text("\(option.cost)").font(Theme.number(.caption))
                    }
                    .padding(.horizontal, 10)
                    .frame(minHeight: 34)
                    .background(Capsule().fill(Color.stat(option.stat).opacity(option.enabled ? 0.28 : 0.10)))
                    .overlay(Capsule().strokeBorder(Color.stat(option.stat).opacity(option.enabled ? 0.6 : 0.2)))
                }
                .buttonStyle(PressableStyle())
                .disabled(!option.enabled)
                .opacity(option.enabled ? 1 : 0.5)
            }
        }
    }
}

/// Construction icon on a light disc, matching how the game shows construction portraits.
struct ConstructionIcon: View {
    let icon: String
    let size: CGFloat

    var body: some View {
        GameIcon(name: icon, fallback: "hammer.fill", tint: Theme.ink)
            .padding(size * 0.2)
            .frame(width: size, height: size)
            .background(
                Circle().fill(
                    LinearGradient(colors: [Color(white: 0.96), Color(white: 0.78)], startPoint: .top, endPoint: .bottom)
                )
            )
            .overlay(Circle().strokeBorder(Theme.accent.opacity(0.6), lineWidth: 1.5))
    }
}

/// Scrolls only when asked to, so short content keeps its natural height.
struct OptionalScroll<Content: View>: View {
    let scrolls: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        if scrolls {
            ScrollView(showsIndicators: false) { content() }
        } else {
            content()
        }
    }
}

struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.heavy))
            .tracking(1)
            .foregroundStyle(Theme.accent)
    }
}
