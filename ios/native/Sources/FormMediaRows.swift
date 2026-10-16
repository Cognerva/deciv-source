import SwiftUI
import UIKit

// Form rows for text entry, charts and the map replay; see FormView for the others.

/// A text field that sends its text when editing ends, e.g. a religion's or a unit's name.
struct FieldRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                TextField(item.placeholder ?? "", text: $text)
                    .font(.body.weight(.medium))
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(send)
                Image(systemName: "pencil")
                    .foregroundStyle(.secondary)
            }
            if let detail = item.detail {
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Palette.unhappiness)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .onAppear { text = item.value ?? "" }
        .onChange(of: item.value) { if !focused { text = $0 ?? "" } }
        .onChange(of: focused) { if !$0 { send() } }
    }

    private func send() {
        guard text != (item.value ?? "") else { return }
        perform("set:\(item.id):\(text)", .light)
    }
}

/// Line chart over turns with the highlighted line drawn on top.
struct ChartRow: View {
    let item: FormState.Item

    private struct Line {
        let color: Color
        let highlighted: Bool
        let points: [CGPoint]
    }

    var body: some View {
        if let chart = item.chart {
            let lines = chart.series.map { series in
                Line(color: Color(hex: series.color), highlighted: series.highlighted,
                     points: stride(from: 0, to: series.points.count - 1, by: 2).map {
                         CGPoint(x: series.points[$0], y: series.points[$0 + 1])
                     })
            }
            let all = lines.flatMap(\.points)
            let minX = all.map(\.x).min() ?? 0, maxX = all.map(\.x).max() ?? 1
            let minY = min(all.map(\.y).min() ?? 0, 0), maxY = max(all.map(\.y).max() ?? 1, 1)

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top, spacing: 6) {
                    VStack(alignment: .trailing) {
                        Text(Self.format(maxY))
                        Spacer()
                        Text(Self.format((maxY + minY) / 2))
                        Spacer()
                        Text(Self.format(minY))
                    }
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.secondary)

                    Canvas { context, size in
                        func position(_ point: CGPoint) -> CGPoint {
                            let x = maxX > minX ? (point.x - minX) / (maxX - minX) : 0.5
                            let y = maxY > minY ? (point.y - minY) / (maxY - minY) : 0.5
                            return CGPoint(x: x * size.width, y: (1 - y) * size.height)
                        }
                        for fraction in [0.0, 0.5, 1.0] {
                            var grid = Path()
                            grid.move(to: CGPoint(x: 0, y: fraction * size.height))
                            grid.addLine(to: CGPoint(x: size.width, y: fraction * size.height))
                            context.stroke(grid, with: .color(.white.opacity(0.1)), lineWidth: 1)
                        }
                        // Highlighted line last, so it stays on top
                        for line in lines.sorted(by: { !$0.highlighted && $1.highlighted }) {
                            guard let first = line.points.first else { continue }
                            var path = Path()
                            path.move(to: position(first))
                            for point in line.points.dropFirst() { path.addLine(to: position(point)) }
                            context.stroke(path, with: .color(line.color.opacity(line.highlighted ? 1 : 0.55)),
                                           style: StrokeStyle(lineWidth: line.highlighted ? 3 : 1.5, lineCap: .round, lineJoin: .round))
                            if line.highlighted, let last = line.points.last {
                                let end = position(last)
                                context.fill(Path(ellipseIn: CGRect(x: end.x - 4, y: end.y - 4, width: 8, height: 8)),
                                             with: .color(line.color))
                            }
                        }
                    }
                    .frame(height: 220)
                }
                HStack {
                    Text("\(L("Turn")) \(Int(minX))")
                    Spacer()
                    Text("\(L("Turn")) \(Int(maxX))")
                }
                .font(.caption2.monospacedDigit())
                .foregroundStyle(.secondary)
                .padding(.leading, 30)
            }
            .padding(14)
        }
    }

    private static func format(_ value: Double) -> String {
        let magnitude = abs(value)
        if magnitude >= 1_000_000 { return String(format: "%.1fM", value / 1_000_000) }
        if magnitude >= 10_000 { return String(format: "%.0fk", value / 1000) }
        return String(Int(value.rounded()))
    }
}

/// The world map turn by turn: who owned which land, and where the cities stood.
struct ReplayRow: View {
    let item: FormState.Item

    /// Int.min until the replay first appears, when it starts playing from the first turn
    @State private var turn = Int.min
    @State private var playing = false
    @State private var image: UIImage?
    @Environment(\.displayScale) private var displayScale

    private let timer = Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()

    var body: some View {
        if let replay = item.replay {
            let lastTurn = replay.firstTurn + max(replay.years.count - 1, 0)
            VStack(spacing: 12) {
                Group {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .interpolation(.medium)
                            .aspectRatio(contentMode: .fit)
                    } else {
                        Color.clear
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 300)

                HStack(spacing: 12) {
                    Button {
                        if turn >= lastTurn { turn = replay.firstTurn }
                        playing.toggle()
                    } label: {
                        Image(systemName: playing ? "pause.fill" : "play.fill")
                            .font(.system(size: 16, weight: .bold))
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(Theme.accent.opacity(0.25)))
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(L(playing ? "Pause" : "Play"))

                    Slider(value: Binding(get: { Double(turn) }, set: { turn = Int($0.rounded()); playing = false }),
                           in: Double(replay.firstTurn)...Double(max(lastTurn, replay.firstTurn + 1)))

                    VStack(alignment: .trailing, spacing: 0) {
                        Text(replay.years[safe: turn - replay.firstTurn] ?? "")
                            .font(.footnote.weight(.semibold))
                        Text("\(L("Turn")) \(turn)")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                    .frame(minWidth: 76, alignment: .trailing)
                }
            }
            .padding(14)
            .onAppear {
                if turn == Int.min {
                    turn = replay.firstTurn
                    playing = lastTurn > replay.firstTurn
                }
                image = Self.render(replay, turn: turn, scale: displayScale)
            }
            .onChange(of: turn) { image = Self.render(replay, turn: $0, scale: displayScale) }
            .onReceive(timer) { _ in
                guard playing else { return }
                if turn >= lastTurn { playing = false } else { turn += 1 }
            }
        }
    }

    /// Replays the ownership changes up to `turn` and draws the result like the minimap
    static func render(_ replay: FormState.ReplayData, turn: Int, scale: CGFloat) -> UIImage? {
        let tileCount = replay.tiles.count / 3
        var owner = [Int](repeating: -1, count: tileCount)
        var city = [Int](repeating: 0, count: tileCount)
        var index = 0
        let changes = replay.changes
        while index + 3 < changes.count {
            let tile = Int(changes[index + 1])
            if Int(changes[index]) <= turn && tile >= 0 && tile < tileCount {
                owner[tile] = Int(changes[index + 2])
                city[tile] = Int(changes[index + 3])
            }
            index += 4
        }
        var palette = replay.palette
        func paletteIndex(ofOwner ownerIndex: Int, inner: Bool) -> Double? {
            let slot = ownerIndex * 2 + (inner ? 1 : 0)
            return replay.owners[safe: slot]
        }
        var tiles = replay.tiles
        var cities: [Double] = []
        for tile in 0..<tileCount where owner[tile] >= 0 {
            let isCity = city[tile] > 0
            if let color = paletteIndex(ofOwner: owner[tile], inner: isCity) { tiles[tile * 3 + 2] = color }
            if isCity, let outer = paletteIndex(ofOwner: owner[tile], inner: false),
               let inner = paletteIndex(ofOwner: owner[tile], inner: true) {
                cities += [replay.tiles[tile * 3], replay.tiles[tile * 3 + 1], outer, inner, city[tile] == 2 ? 1 : 0]
            }
        }
        if palette.isEmpty { palette = ["#808080"] }
        let state = MinimapState(visible: true,
                                 layers: .init(movement: false, yields: false, worked: false, resources: false, improvements: false),
                                 width: replay.width, height: replay.height, tileSize: replay.tileSize,
                                 palette: palette, tiles: tiles, cities: cities)
        return MinimapRenderer.render(state, scale: scale)
    }
}

/// Pictures to pick from in a grid, e.g. the app icons; `symbol` marks a locked option.
struct IconGridRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 12, alignment: .top)], spacing: 16) {
            ForEach(item.options ?? []) { option in
                let selected = option.id == item.value
                Button { perform("set:\(item.id):\(option.id)", .medium) } label: {
                    VStack(spacing: 7) {
                        ZStack(alignment: .topTrailing) {
                            Group {
                                if let icon = option.icon, let image = GameIconStore.shared.image(icon) {
                                    Image(uiImage: image).resizable().scaledToFill()
                                } else {
                                    Color.white.opacity(0.08)
                                }
                            }
                            .frame(width: 64, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .strokeBorder(selected ? Theme.accent : .clear, lineWidth: 2.5)
                                    .padding(-4)
                            )
                            if selected {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundStyle(Theme.ink, Theme.accent)
                                    .offset(x: 8, y: -8)
                            } else if let symbol = option.symbol {
                                Image(systemName: symbol)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 20, height: 20)
                                    .background(Circle().fill(Color.black.opacity(0.7)))
                                    .offset(x: 6, y: -6)
                            }
                        }
                        .padding(.top, 6)
                        Text(option.label)
                            .font(.caption.weight(selected ? .semibold : .regular))
                            .foregroundStyle(selected ? .white : .white.opacity(0.75))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PressableStyle())
            }
        }
        .padding(14)
    }
}
