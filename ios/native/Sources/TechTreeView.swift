import SwiftUI
import UIKit

/// Native tech tree. Covers the Scene2D TechPickerScreen, which stays underneath as the
/// navigation entry and owns the research-path and queue logic.
struct TechTreeView: View {
    let tree: TechTreeState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var didScroll = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    private enum Layout {
        static let cardWidth: CGFloat = 206
        static let cardHeight: CGFloat = 70
        static let columnWidth: CGFloat = 250
        static let rowHeight: CGFloat = 88
        static let eraHeader: CGFloat = 44
        static let margin: CGFloat = 28
    }

    private var techsById: [String: TechTreeState.Tech] {
        Dictionary(uniqueKeysWithValues: tree.techs.map { ($0.id, $0) })
    }

    private var columns: Int { (tree.techs.map(\.column).max() ?? 0) + 1 }
    /// Unciv's row numbers are sparse; pack the used ones together
    private var rowIndex: [Int: Int] {
        Dictionary(uniqueKeysWithValues: Set(tree.techs.map(\.row)).sorted().enumerated().map { ($1, $0) })
    }
    private var rows: Int { rowIndex.count }
    private var contentSize: CGSize {
        CGSize(width: Layout.margin * 2 + CGFloat(columns) * Layout.columnWidth,
               height: Layout.eraHeader + Layout.margin + CGFloat(rows) * Layout.rowHeight + 16)
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.ink, Color(red: 0.04, green: 0.06, blue: 0.10)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                graph
                if let selected = tree.selected {
                    detailPanel(selected)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.9), value: tree.selected?.id)
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: 12) {
            Button { perform("close", .light) } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left").font(.system(size: 15, weight: .bold))
                    Text(L("Back")).font(.subheadline.weight(.semibold))
                }
                .padding(.horizontal, 14)
                .frame(height: 44)
                .capsuleCard()
            }
            .buttonStyle(PressableStyle())

            VStack(alignment: .leading, spacing: 0) {
                Text(L(tree.freeTechPick ? "Pick a free tech" : "Technologies"))
                    .font(Theme.display(22))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                HStack(spacing: 4) {
                    StatIcon(stat: "Science", size: 13)
                    Text("+\(tree.science) per turn")
                        .font(Theme.number(.caption, weight: .medium))
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            if sizeClass == .regular { legend }
        }
    }

    private var legend: some View {
        HStack(spacing: 10) {
            LegendDot(color: TechStyle.researched.accent, label: L("Known"))
            LegendDot(color: TechStyle.available.accent, label: L("Available"))
            LegendDot(color: TechStyle.current.accent, label: L("Researching"))
        }
        .font(.caption2.weight(.medium))
        .foregroundStyle(.secondary)
        .opacity(0.9)
    }

    // MARK: Graph

    private var graph: some View {
        ScrollViewReader { reader in
            ScrollView([.horizontal, .vertical], showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    eraBands
                    connections
                    ForEach(tree.techs) { tech in
                        TechCard(tech: tech, selected: tech.id == tree.selected?.id)
                            .frame(width: Layout.cardWidth, height: Layout.cardHeight)
                            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .onTapGesture { perform("select:\(tech.id)", .light) }
                            .contextMenu {
                                if !tree.freeTechPick {
                                    Button { perform("queue:\(tech.id)", .light) } label: {
                                        Label(L("Add to queue"), systemImage: "text.badge.plus")
                                    }
                                }
                                Button { perform("civilopedia:\(tech.id)", .light) } label: {
                                    Label(L("Civilopedia"), systemImage: "book.closed")
                                }
                            }
                            .id(tech.id)
                            .alignmentGuide(.leading) { _ in -origin(of: tech).x }
                            .alignmentGuide(.top) { _ in -origin(of: tech).y }
                    }
                }
                .frame(width: contentSize.width, height: contentSize.height, alignment: .topLeading)
            }
            .onAppear {
                guard !didScroll else { return }
                didScroll = true
                let target = tree.selected?.id
                    ?? tree.techs.first { $0.state == "current" }?.id
                    ?? tree.techs.first { $0.state == "available" }?.id
                guard let target else { return }
                DispatchQueue.main.async { reader.scrollTo(target, anchor: .center) }
            }
        }
    }

    private func origin(of tech: TechTreeState.Tech) -> CGPoint {
        CGPoint(x: Layout.margin + CGFloat(tech.column) * Layout.columnWidth,
                y: Layout.eraHeader + Layout.margin / 2 + CGFloat(rowIndex[tech.row] ?? tech.row) * Layout.rowHeight)
    }

    private var eraBands: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(tree.eras.enumerated()), id: \.element) { index, era in
                let x = Layout.margin + CGFloat(era.firstColumn) * Layout.columnWidth - 12
                let width = CGFloat(era.lastColumn - era.firstColumn + 1) * Layout.columnWidth
                VStack(alignment: .leading, spacing: 0) {
                    Text(era.name.uppercased())
                        .font(.caption.weight(.heavy))
                        .tracking(1.5)
                        .foregroundStyle(Theme.accent.opacity(0.9))
                        .padding(.leading, 12)
                        .frame(height: Layout.eraHeader)
                    Spacer(minLength: 0)
                }
                .frame(width: width, height: contentSize.height, alignment: .topLeading)
                .background(Color.white.opacity(index.isMultiple(of: 2) ? 0.025 : 0.0))
                .overlay(alignment: .leading) {
                    Rectangle().fill(Color.white.opacity(0.06)).frame(width: 1)
                }
                .alignmentGuide(.leading) { _ in -x }
            }
        }
    }

    private var connections: some View {
        let byId = techsById
        return Canvas { context, _ in
            for tech in tree.techs {
                let end = origin(of: tech)
                let target = CGPoint(x: end.x, y: end.y + Layout.cardHeight / 2)
                for prerequisite in tech.prerequisites {
                    guard let from = byId[prerequisite.id] else { continue }
                    let start = origin(of: from)
                    let source = CGPoint(x: start.x + Layout.cardWidth, y: start.y + Layout.cardHeight / 2)
                    var path = Path()
                    path.move(to: source)
                    let bend = (target.x - source.x) / 2
                    path.addCurve(to: target,
                                  control1: CGPoint(x: source.x + bend, y: source.y),
                                  control2: CGPoint(x: target.x - bend, y: target.y))
                    let (color, width) = lineStyle(from: from, to: tech)
                    context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
                }
            }
        }
        .frame(width: contentSize.width, height: contentSize.height)
        .allowsHitTesting(false)
    }

    private func lineStyle(from: TechTreeState.Tech, to: TechTreeState.Tech) -> (Color, CGFloat) {
        switch to.state {
        case "researched": return (TechStyle.researched.accent.opacity(0.55), 2)
        case "current", "queued": return (TechStyle.current.accent.opacity(0.9), 3)
        case "available": return (TechStyle.available.accent.opacity(0.6), 2)
        default: return (Color.white.opacity(0.13), 1.5)
        }
    }

    // MARK: Detail

    @ViewBuilder
    private func detailPanel(_ selected: TechTreeState.Selected) -> some View {
        if sizeClass == .regular {
            HStack(alignment: .top, spacing: 14) {
                detailIcon(selected)
                detailText(selected)
                VStack(spacing: 8) { detailButtons(selected) }
                    .frame(width: 220)
            }
            .padding(14)
            .card(cornerRadius: 24)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 12) {
                    detailIcon(selected)
                    detailText(selected)
                }
                HStack(spacing: 8) {
                    detailButtons(selected)
                }
            }
            .padding(14)
            .card(cornerRadius: 24)
        }
    }

    private func detailIcon(_ selected: TechTreeState.Selected) -> some View {
        GameIcon(name: selected.icon, fallback: "flask.fill", tint: .white)
            .padding(12)
            .frame(width: 58, height: 58)
            .background(Circle().fill(Palette.science.opacity(0.45)))
    }

    private func detailText(_ selected: TechTreeState.Selected) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(selected.name).font(Theme.display(19))
            if !selected.unlocks.isEmpty {
                HStack(spacing: 6) {
                    ForEach(selected.unlocks.prefix(8), id: \.self) { unlock in
                        UnlockIcon(icon: unlock.icon, size: 26)
                    }
                }
            }
            let description = Text(selected.description)
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.8))
                .frame(maxWidth: .infinity, alignment: .leading)
            if selected.description.count > 220 {
                ScrollView(showsIndicators: false) { description }.frame(height: 76)
            } else {
                description.fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func detailButtons(_ selected: TechTreeState.Selected) -> some View {
        Button { perform("confirm", .medium) } label: {
            // The game's label is "Research [tech]" with the science progress on a second line
            let lines = tree.pick.label.components(separatedBy: "\n")
            VStack(spacing: 1) {
                Text(lines.first ?? "")
                    .font(.subheadline.weight(.bold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)
                if lines.count > 1 {
                    Text(lines.dropFirst().joined(separator: " "))
                        .font(Theme.number(.caption2, weight: .medium))
                        .opacity(0.7)
                }
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: 50)
            .modifier(PrimaryCapsule())
        }
        .buttonStyle(PressableStyle())
        .disabled(!tree.pick.enabled)
        .opacity(tree.pick.enabled ? 1 : 0.45)
        if !tree.freeTechPick {
            Button { perform("queue:\(selected.id)", .light) } label: {
                Label(L("Add to queue"), systemImage: "text.badge.plus")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
                    .background(Capsule().fill(Color.white.opacity(0.08)))
            }
            .buttonStyle(PressableStyle())
        }
    }
}

// MARK: - Pieces

enum TechStyle {
    case researched, current, queued, available, locked

    init(_ state: String) {
        switch state {
        case "researched": self = .researched
        case "current": self = .current
        case "queued": self = .queued
        case "available": self = .available
        default: self = .locked
        }
    }

    var accent: Color {
        switch self {
        case .researched: return Palette.gold
        case .current: return Palette.science
        case .queued: return Color(red: 0.35, green: 0.78, blue: 0.80)
        case .available: return Palette.happiness
        case .locked: return Color.white.opacity(0.35)
        }
    }
}

private struct TechCard: View {
    let tech: TechTreeState.Tech
    let selected: Bool

    private var style: TechStyle { TechStyle(tech.state) }

    var body: some View {
        HStack(spacing: 10) {
            GameIcon(name: tech.icon, fallback: "flask.fill", tint: style == .locked ? .white.opacity(0.6) : .white)
                .padding(9)
                .frame(width: 44, height: 44)
                .background(Circle().fill(style.accent.opacity(style == .locked ? 0.15 : 0.45)))
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    Text(tech.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(style == .locked ? .white.opacity(0.6) : .white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Spacer(minLength: 0)
                    if style == .researched {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(Palette.gold)
                    } else if tech.turns > 0 {
                        Text("\(tech.turns)t")
                            .font(Theme.number(.caption2))
                            .foregroundStyle(.secondary)
                    }
                }
                HStack(spacing: 3) {
                    ForEach(tech.unlocks, id: \.self) { unlock in
                        UnlockIcon(icon: unlock.icon, size: 18)
                    }
                }
                if tech.progress > 0, style != .researched {
                    Meter(value: tech.progress, color: Palette.science, height: 3)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(style.accent.opacity(style == .locked ? 0.04 : 0.14))
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Theme.inkRaised.opacity(0.9)))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(selected ? Color.white : style.accent.opacity(style == .locked ? 0.15 : 0.6),
                              lineWidth: selected ? 2.5 : 1.2)
        )
        .overlay(alignment: .topTrailing) {
            if tech.queueIndex > 0 && style != .researched {
                Text("\(tech.queueIndex)")
                    .font(.system(size: 11, weight: .heavy, design: .rounded))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 20, height: 20)
                    .background(Circle().fill(style == .current ? Palette.science : TechStyle.queued.accent))
                    .offset(x: 6, y: -6)
            }
        }
        .shadow(color: selected ? style.accent.opacity(0.6) : .clear, radius: 10)
    }
}

/// What a tech unlocks: units and buildings on a light disc, resources and improvements as is.
struct UnlockIcon: View {
    let icon: String
    let size: CGFloat

    var body: some View {
        if icon.hasPrefix("UnitIcons/") || icon.hasPrefix("BuildingIcons/") {
            GameIcon(name: icon, fallback: "hammer.fill", tint: Theme.ink)
                .padding(size * 0.18)
                .frame(width: size, height: size)
                .background(Circle().fill(Color(white: 0.88)))
        } else {
            GameIcon(name: icon, fallback: "circle.fill")
                .frame(width: size, height: size)
        }
    }
}

private struct LegendDot: View {
    let color: Color
    let label: String

    var body: some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label)
        }
    }
}
