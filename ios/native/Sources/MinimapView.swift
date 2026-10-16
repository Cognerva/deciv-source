import SwiftUI
import UIKit

/// Native minimap: the explored region drawn as hexes, the camera rectangle, tap or drag to look
/// somewhere else, and the map layer switches that used to be a column of Scene2D buttons.
struct MinimapCard: View {
    @ObservedObject var store: MinimapStore
    let compact: Bool
    /// Show only a button until the player opens the map
    var folded = false
    /// Sends `minimap:*` ids without haptics; dragging sends many
    let send: (String) -> Void

    @State private var expanded = false
    @State private var showLayers = false
    @State private var lastSent: CGPoint?

    var body: some View {
        content.onDebugCommand { command in
            if command == "expandMinimap" { expanded = true }
            if command == "minimapLayers" { showLayers = true }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let state = store.state, state.visible, let width = state.width, let height = state.height, width > 0, height > 0 {
            if folded && !expanded {
                HudIconButton(symbol: "map.fill", label: L("Minimap"), material: true) {
                    expanded = true
                }
                .interactiveRegion("minimap")
            } else {
                card(state: state, size: mapSize(aspect: width / height))
            }
        }
    }

    private func card(state: MinimapState, size: CGSize) -> some View {
        Group {
            VStack(alignment: .trailing, spacing: 6) {
                if showLayers {
                    LayersPanel(layers: state.layers) { layer in
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        send("minimap:layer:" + layer)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                VStack(spacing: 0) {
                    map(size: size)
                    toolbar(layers: state.layers)
                        .frame(width: max(size.width, 96))
                }
                .padding(5)
                .card(cornerRadius: 16)
            }
        }
        .animation(.spring(response: 0.32, dampingFraction: 0.86), value: expanded)
        .animation(.spring(response: 0.3, dampingFraction: 0.86), value: showLayers)
        .interactiveRegion("minimap")
    }

    private func mapSize(aspect: Double) -> CGSize {
        let box: CGSize = expanded
            ? CGSize(width: compact ? 300 : 440, height: compact ? 260 : 330)
            : CGSize(width: compact ? 112 : 190, height: compact ? 84 : 130)
        let width = min(box.width, box.height * aspect)
        return CGSize(width: width, height: width / aspect)
    }

    private func map(size: CGSize) -> some View {
        ZStack {
            if let image = store.image(scale: UIScreen.main.scale) {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.medium)
            }
        }
        .frame(width: size.width, height: size.height)
        // A shape, not a framed view: early in a game the camera is larger than the map and must not affect layout
        .overlay(CameraShape(camera: store.camera).fill(Color.white.opacity(0.08)))
        .overlay(CameraShape(camera: store.camera).stroke(Color.white.opacity(0.9), lineWidth: 1.5))
        .background(Theme.ink.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { drag in look(at: drag.location, in: size) }
                .onEnded { _ in lastSent = nil }
        )
        .accessibilityLabel(L("Minimap"))
    }

    private func look(at point: CGPoint, in size: CGSize) {
        if let lastSent, hypot(point.x - lastSent.x, point.y - lastSent.y) < 4 { return }
        if lastSent == nil { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        lastSent = point
        let x = min(max(point.x / size.width, 0), 1)
        let y = 1 - min(max(point.y / size.height, 0), 1)
        send(String(format: "minimap:tap:%.4f:%.4f", x, y))
    }

    private func toolbar(layers: MinimapState.Layers) -> some View {
        HStack(spacing: 0) {
            toolbarButton(symbol: "square.3.layers.3d", active: showLayers, label: L("Map layers")) { showLayers.toggle() }
            Spacer(minLength: 0)
            toolbarButton(symbol: expanded ? (folded ? "xmark" : "arrow.down.right.and.arrow.up.left") : "arrow.up.left.and.arrow.down.right",
                          active: false, label: L(expanded ? "Reduce" : "Enlarge")) {
                expanded.toggle()
                if !expanded { showLayers = false }
            }
        }
        .padding(.top, 2)
    }

    private func toolbarButton(symbol: String, active: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            Image(systemName: Symbols.named(symbol, fallback: "square.stack.3d.up"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(active ? Theme.accent : .white.opacity(0.85))
                .frame(width: 44, height: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(label)
    }
}

/// Switches for the map overlays: movement arrows, yields, worked tiles, resources, improvements.
private struct LayersPanel: View {
    let layers: MinimapState.Layers
    let toggle: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            row("movement", "Show unit movement arrows", "arrow.triangle.turn.up.right.diamond.fill", layers.movement)
            row("yields", "Show tile yields", "leaf.fill", layers.yields)
            row("worked", "Show worked tiles", "person.fill", layers.worked)
            row("resources", "Show resources and improvements", "diamond.fill", layers.resources)
            row("improvements", "Show pixel improvements", "hammer.fill", layers.improvements)
        }
        .padding(6)
        .frame(width: 250)
        .card(cornerRadius: 16)
    }

    private func row(_ id: String, _ title: String, _ symbol: String, _ on: Bool) -> some View {
        Button { toggle(id) } label: {
            HStack(spacing: 10) {
                Image(systemName: Symbols.named(symbol))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(on ? Theme.accent : .white.opacity(0.45))
                    .frame(width: 22)
                Text(L(title))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(on ? .white : .white.opacity(0.6))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 4)
                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(on ? Theme.accent : .white.opacity(0.3))
            }
            .padding(.horizontal, 8)
            .frame(minHeight: 40)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

/// Draws the minimap tiles once into an image, in the Scene2D minimap's proportions.
enum MinimapRenderer {
    static func render(_ state: MinimapState, scale: CGFloat) -> UIImage? {
        guard let width = state.width, let height = state.height, width > 0, height > 0,
              let tiles = state.tiles, let palette = state.palette, let tileSize = state.tileSize, tileSize > 0
        else { return nil }
        // Draw at a fixed resolution: enough for the expanded size, cheap to redraw
        let longest = 440.0
        let factor = longest / max(width, height)
        let size = CGSize(width: width * factor, height: height * factor)
        let colors = palette.map { UIColor(Color(hex: $0)) }

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            // Flat-topped hexes; slightly oversized so neighbours overlap without seams
            let radius = tileSize / 2 * factor * 1.04
            var index = 0
            while index + 2 < tiles.count {
                let center = CGPoint(x: tiles[index] * factor, y: (height - tiles[index + 1]) * factor)
                let color = colors[safe: Int(tiles[index + 2])] ?? .gray
                cg.setFillColor(color.cgColor)
                cg.addPath(hexagon(center: center, radius: radius))
                cg.fillPath()
                index += 3
            }

            guard let cities = state.cities else { return }
            index = 0
            while index + 4 < cities.count {
                let center = CGPoint(x: cities[index] * factor, y: (height - cities[index + 1]) * factor)
                let outer = colors[safe: Int(cities[index + 2])] ?? .white
                let inner = colors[safe: Int(cities[index + 3])] ?? .black
                let diameter = tileSize * factor * (cities[index + 4] > 0 ? 1.667 : 1.25)
                let ring = CGRect(x: center.x - diameter / 2, y: center.y - diameter / 2, width: diameter, height: diameter)
                cg.setFillColor(outer.cgColor)
                cg.fillEllipse(in: ring)
                cg.setFillColor(inner.cgColor)
                cg.fillEllipse(in: ring.insetBy(dx: diameter * 0.22, dy: diameter * 0.22))
                cg.setStrokeColor(UIColor.black.withAlphaComponent(0.45).cgColor)
                cg.setLineWidth(max(diameter * 0.06, 0.6))
                cg.strokeEllipse(in: ring)
                index += 5
            }
        }
    }

    private static func hexagon(center: CGPoint, radius: CGFloat) -> CGPath {
        let path = CGMutablePath()
        for corner in 0..<6 {
            let angle = CGFloat(corner) * .pi / 3
            let point = CGPoint(x: center.x + radius * cos(angle), y: center.y + radius * sin(angle))
            if corner == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

/// The part of the map on screen, from camera fractions measured from the bottom left.
private struct CameraShape: Shape {
    let camera: CGRect

    func path(in rect: CGRect) -> Path {
        let frame = CGRect(
            x: camera.minX * rect.width,
            y: (1 - camera.minY - camera.height) * rect.height,
            width: max(camera.width * rect.width, 6),
            height: max(camera.height * rect.height, 6)
        )
        return Path(roundedRect: frame, cornerRadius: 3, style: .continuous)
    }
}
