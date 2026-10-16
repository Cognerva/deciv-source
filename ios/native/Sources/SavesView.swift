import SwiftUI
import UIKit

/// Native load and save screens (`com.unciv.ui.nativeui.SavesPresenter`): the saved games as a
/// list with a preview of the selected one, plus a name field when saving.
struct SavesView: View {
    let state: SavesState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var name = ""
    @State private var deleting: SavesState.Save?
    @State private var overwriting = false

    private func label(_ key: String) -> String { state.labels[key] ?? key }

    var body: some View {
        GeometryReader { proxy in
            let wide = proxy.size.width >= 760
            ZStack {
                FormBackground()
                VStack(spacing: 0) {
                    FormTopBar(title: state.title, subtitle: nil, closeSymbol: "xmark") { perform("close", .light) }
                    if wide {
                        HStack(alignment: .top, spacing: 16) {
                            list
                            detail
                                .frame(width: 340)
                        }
                        .padding(.horizontal, 16)
                    } else {
                        VStack(spacing: 12) {
                            if state.saving || state.selected != nil {
                                detail
                            }
                            list
                        }
                        .padding(.horizontal, 16)
                    }
                }
                if let busy = state.busy {
                    BusyOverlay(text: busy)
                }
            }
        }
        .onAppear { name = state.defaultName }
        .onChange(of: state.selected?.name) { selected in
            if state.saving, let selected { name = selected }
        }
        .alert(isPresented: Binding(get: { state.error != nil && state.busy == nil }, set: { _ in })) {
            Alert(title: Text(state.error ?? ""), dismissButton: .default(Text(L("OK"))) { perform("dismissError", .light) })
        }
        .confirmationDialog(label("Are you sure you want to delete this save?"),
                            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible) {
            Button(label("Delete save"), role: .destructive) {
                if let save = deleting { perform("delete:" + save.name, .medium) }
                deleting = nil
            }
            Button(L("Cancel"), role: .cancel) { deleting = nil }
        }
        .confirmationDialog(label("Overwrite existing file?"), isPresented: $overwriting, titleVisibility: .visible) {
            Button(label("Overwrite"), role: .destructive) { perform("save:" + name, .medium) }
            Button(L("Cancel"), role: .cancel) {}
        }
    }

    // MARK: List

    private var list: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                FormSectionTitle(text: label("Saved games"))
                Spacer()
                Toggle(label("Show autosaves"), isOn: Binding(get: { state.showAutosaves }, set: { _ in perform("toggleAutosaves", .light) }))
                    .font(.footnote)
                    .tint(Theme.accent)
                    .fixedSize()
            }
            ScrollView(showsIndicators: false) {
                if state.saves.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "tray")
                            .font(.system(size: 34))
                            .foregroundStyle(.secondary)
                        Text(label("No saved games"))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 50)
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(state.saves.enumerated()), id: \.element.id) { index, save in
                            if index > 0 { Divider().overlay(Theme.hairline).padding(.leading, 60) }
                            saveRow(save)
                        }
                    }
                    .card(cornerRadius: 18)
                }
                if state.canPickFile {
                    Button { perform(state.saving ? "saveToFile" : "loadFromFile", .light) } label: {
                        Label(label(state.saving ? "Save to custom location" : "Load from custom location"),
                              systemImage: state.saving ? "square.and.arrow.up" : "folder")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .card(cornerRadius: 16)
                    }
                    .buttonStyle(PressableStyle())
                    .padding(.top, 12)
                }
                Spacer(minLength: 30)
            }
        }
    }

    private func saveRow(_ save: SavesState.Save) -> some View {
        let selected = state.selected?.name == save.name
        return Button { perform("select:" + save.name, .light) } label: {
            HStack(spacing: 12) {
                IconTile(symbol: save.autosave ? "clock.arrow.circlepath" : "doc.fill",
                         color: save.autosave ? Color(white: 0.7) : Theme.accent, size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(save.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(save.date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                if selected {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 60)
            .background(selected ? Theme.accent.opacity(0.10) : .clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive) { deleting = save } label: {
                Label(label("Delete save"), systemImage: "trash")
            }
        }
    }

    // MARK: Detail

    private var detail: some View {
        VStack(alignment: .leading, spacing: 12) {
            if state.saving {
                VStack(alignment: .leading, spacing: 6) {
                    Text(label("Saved game name"))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                    TextField(label("Saved game name"), text: $name)
                        .font(.body.weight(.medium))
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .padding(.horizontal, 12)
                        .frame(height: 46)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.white.opacity(0.08)))
                }
            }
            if let preview = state.selected {
                previewCard(preview)
            }
            actionButtons
        }
        .padding(14)
        .card(cornerRadius: 20)
    }

    private func previewCard(_ preview: SavesState.Preview) -> some View {
        HStack(alignment: .top, spacing: 12) {
            if let icon = preview.civIcon {
                OptionBadge(icon: icon, symbol: nil, background: preview.outerColor, tint: preview.innerColor, size: 46)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(preview.civ ?? preview.name)
                    .font(Theme.display(18, weight: .semibold))
                    .lineLimit(1)
                if preview.lines.isEmpty && !preview.failed {
                    ProgressView().tint(Theme.accent)
                }
                ForEach(preview.lines, id: \.self) { line in
                    Text(line)
                        .font(.caption)
                        .foregroundStyle(preview.failed ? Palette.unhappiness : .secondary)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 10) {
            if let selected = state.selected, !state.saving {
                Button { deleting = state.saves.first { $0.name == selected.name } } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Palette.unhappiness)
                        .frame(width: 50, height: 50)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(label("Delete save"))
            }
            Button(action: primaryAction) {
                Label(primaryTitle, systemImage: state.saving ? "square.and.arrow.down.fill" : "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .modifier(PrimaryCapsule())
            }
            .buttonStyle(PressableStyle())
            .disabled(!primaryEnabled)
            .opacity(primaryEnabled ? 1 : 0.5)
        }
    }

    private var primaryTitle: String {
        state.saving ? label("Save game") : label("Load game")
    }

    private var primaryEnabled: Bool {
        if state.saving { return !name.trimmingCharacters(in: .whitespaces).isEmpty && !name.contains("/") }
        return state.selected != nil && state.selected?.failed != true
    }

    private func primaryAction() {
        if state.saving {
            if state.saves.contains(where: { $0.name == name }) { overwriting = true } else { perform("save:" + name, .medium) }
        } else if let selected = state.selected {
            perform("load:" + selected.name, .medium)
        }
    }
}
