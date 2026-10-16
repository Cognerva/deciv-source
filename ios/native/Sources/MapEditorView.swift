import SwiftUI
import UIKit

/// Snapshot of the map editor chrome, see `com.unciv.ui.nativeui.MapEditorPresenter`.
struct MapEditorState: Decodable, Equatable {
    struct Category: Decodable, Equatable {
        let name: String
        let disabled: Bool
        /// Only the shown category carries its items
        let items: [Item]?
    }

    struct Item: Decodable, Equatable {
        let label: String
        let icon: String?
    }

    let title: String
    let subtitle: String
    let category: Int
    /// 1…5 is the brush radius, -1 fills similar tiles
    let brushSize: Int
    /// "<category>:<item>"
    let selected: String?
    let categories: [Category]
}

/// Native map editor chrome: the map stays Scene2D and is painted by tapping it. A top bar
/// with the editor's pages as sheets, and a brush palette at the bottom.
struct MapEditorView: View {
    let editor: MapEditorState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        GeometryReader { proxy in
            let compact = proxy.size.width < 640
            VStack(spacing: 0) {
                topBar(compact: compact)
                    .padding(.horizontal, 12)
                    .padding(.top, 6)
                Spacer(minLength: 0)
                palette
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
                    .frame(maxWidth: 760)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }

    // MARK: Top bar

    private func topBar(compact: Bool) -> some View {
        HStack(spacing: 10) {
            HudIconButton(symbol: "chevron.left", label: L("Back")) { perform("back", .light) }
            VStack(alignment: .leading, spacing: 1) {
                Text(editor.title)
                    .font(Theme.display(17, weight: .semibold))
                    .lineLimit(1)
                Text(editor.subtitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .layoutPriority(1)
            Spacer(minLength: 4)
            if compact {
                Menu {
                    menuItems
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(width: HudIconButton.size, height: HudIconButton.size)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                }
                .accessibilityLabel(L("More"))
                HudIconButton(symbol: "square.and.arrow.down", label: L("Save")) { perform("sheet:save", .light) }
            } else {
                ForEach(pages, id: \.id) { page in
                    HudIconButton(symbol: page.symbol, label: L(page.title)) { perform("sheet:\(page.id)", .light) }
                }
            }
        }
        .padding(8)
        .capsuleCard()
        .interactiveRegion("editorTop")
    }

    private struct Page { let id: String; let title: String; let symbol: String }
    private let pages = [
        Page(id: "generate", title: "Generate", symbol: "wand.and.stars"),
        Page(id: "load", title: "Load", symbol: "folder"),
        Page(id: "save", title: "Save", symbol: "square.and.arrow.down"),
        Page(id: "view", title: "View", symbol: "info.circle"),
        Page(id: "mods", title: "Mods", symbol: "puzzlepiece.extension"),
        Page(id: "options", title: "Options", symbol: "slider.horizontal.3"),
    ]

    @ViewBuilder
    private var menuItems: some View {
        ForEach(pages.filter { $0.id != "save" }, id: \.id) { page in
            Button { perform("sheet:\(page.id)", .light) } label: {
                Label(L(page.title), systemImage: page.symbol)
            }
        }
    }

    // MARK: Palette

    private var palette: some View {
        VStack(alignment: .leading, spacing: 10) {
            categoryChips
            items
            brushSizes
        }
        .padding(10)
        .card(cornerRadius: 24)
        .interactiveRegion("editorPalette")
    }

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(editor.categories.enumerated()), id: \.offset) { index, category in
                    let active = index == editor.category
                    Button { perform("category:\(index)", .light) } label: {
                        Text(category.name)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(active ? Theme.ink : .white)
                            .padding(.horizontal, 12)
                            .frame(height: 30)
                            .background {
                                if active { Capsule().fill(Theme.accentGradient) }
                                else { Capsule().fill(Color.white.opacity(0.08)) }
                            }
                    }
                    .buttonStyle(PressableStyle())
                    .disabled(category.disabled)
                    .opacity(category.disabled ? 0.4 : 1)
                }
            }
        }
    }

    private var items: some View {
        let category = editor.categories[safe: editor.category]
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 8) {
                ForEach(Array((category?.items ?? []).enumerated()), id: \.offset) { index, item in
                    let id = "\(editor.category):\(index)"
                    let active = editor.selected == id
                    Button { perform("item:\(id)", .light) } label: {
                        VStack(spacing: 5) {
                            Group {
                                if let icon = item.icon, GameIconStore.shared.image(icon) != nil {
                                    GameIcon(name: icon)
                                } else {
                                    Image(systemName: "square.dashed").foregroundStyle(.secondary)
                                }
                            }
                            .frame(width: 40, height: 40)
                            Text(item.label)
                                .font(.caption2.weight(active ? .semibold : .regular))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .frame(width: 70)
                        }
                        .padding(.vertical, 6)
                        .padding(.horizontal, 2)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(active ? Theme.accent.opacity(0.22) : Color.white.opacity(0.05))
                                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(active ? Theme.accent : .clear, lineWidth: 1.5))
                        )
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
        .frame(height: 96)
    }

    private var brushSizes: some View {
        HStack(spacing: 6) {
            Image(systemName: "paintbrush.pointed.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
            ForEach([1, 2, 3, 4, 5, -1], id: \.self) { size in
                let active = editor.brushSize == size
                Button { perform("brush:\(size)", .light) } label: {
                    Group {
                        if size < 0 { Image(systemName: "drop.fill") } else { Text("\(size)") }
                    }
                    .font(.footnote.weight(.semibold).monospacedDigit())
                    .foregroundStyle(active ? Theme.ink : .white)
                    .frame(maxWidth: .infinity, minHeight: 30)
                    .background {
                        if active { Capsule().fill(Theme.accentGradient) }
                        else { Capsule().fill(Color.white.opacity(0.08)) }
                    }
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(size < 0 ? L("Floodfill") : "\(L("Brush")) \(size)")
            }
        }
    }
}
