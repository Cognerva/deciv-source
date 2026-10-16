import SwiftUI
import UIKit

/// Native modal for tutorials, the leader introduction and simple Scene2D popups: an emblem, a
/// serif title, optional illustration, and pages of text with Next / Done, or the popup's buttons.
struct StoryCardView: View {
    let card: StoryCard
    let close: () -> Void
    /// A popup button by index
    let press: (Int) -> Void
    /// A popup input's new value by index
    var input: (Int, String) -> Void = { _, _ in }

    @State private var page = 0
    /// Measured height of explained choices, so the card hugs them
    @State private var choicesHeight: CGFloat?

    private var isLastPage: Bool { page >= card.pages.count - 1 }
    private var text: String { card.pages.indices.contains(page) ? card.pages[page].text : "" }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 16) {
                if !card.title.isEmpty || card.icon != nil {
                    header
                }
                if let url = card.image?.url, let image = UIImage(contentsOfFile: url.path) {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.high)
                        .aspectRatio(contentMode: .fit)
                        .frame(maxWidth: .infinity, maxHeight: 180)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                if !text.isEmpty { pageText }
                if let inputs = card.inputs, !inputs.isEmpty {
                    PopupInputs(inputs: inputs, cardID: card.id, send: input)
                }
                if let error = card.error {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Palette.unhappiness)
                }
                if card.buttons.contains(where: { $0.inline == true }) {
                    inlineButtons
                }
                if !card.buttons.isEmpty {
                    choices
                } else if card.doneLabel.isEmpty {
                    // A popup without buttons, e.g. "Working...": it closes itself
                    ProgressView().tint(Theme.accent).frame(maxWidth: .infinity)
                } else {
                    footer
                }
            }
            .padding(22)
            .frame(maxWidth: 540)
            .card(cornerRadius: 30)
            .padding(.horizontal, 20)
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            emblem
            VStack(alignment: .leading, spacing: 2) {
                Text(card.title)
                    .font(Theme.display(24))
                    .fixedSize(horizontal: false, vertical: true)
                if let subtitle = card.subtitle {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var emblem: some View {
        if let icon = card.icon {
            GameIcon(name: icon, fallback: "flag.fill", tint: card.iconTint.map { Color(hex: $0) })
                .padding(12)
                .frame(width: 58, height: 58)
                .background(Circle().fill(card.iconBackground.map { Color(hex: $0) } ?? Theme.inkRaised))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1.5))
                .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
        } else if !card.buttons.isEmpty || card.doneLabel.isEmpty {
            EmptyView()
        } else {
            Image(systemName: "lightbulb.fill")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Palette.gold)
                .frame(width: 50, height: 50)
                .background(Circle().fill(Palette.gold.opacity(0.18)))
        }
    }

    @ViewBuilder
    private var pageText: some View {
        let body = GameText(text)
            .font(.body)
            .foregroundStyle(.white.opacity(0.88))
            .lineSpacing(3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(page)
            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                    removal: .opacity))
        // A scroll view always takes its maximum height; only long pages need one
        if text.count > 520 {
            ScrollView(showsIndicators: true) { body }
                .frame(height: 300)
        } else {
            body.fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Small adjusting buttons such as "-50" and "+50", in one row
    private var inlineButtons: some View {
        HStack(spacing: 8) {
            ForEach(Array(card.buttons.enumerated()).filter { $0.element.inline == true }, id: \.offset) { index, button in
                Button { press(index) } label: {
                    Text(button.label)
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(Capsule().fill(Color.white.opacity(0.1)))
                }
                .buttonStyle(PressableStyle())
                .disabled(!button.enabled)
            }
        }
    }

    /// The popup's own buttons: one is the primary action, several stack full width
    @ViewBuilder
    private var choices: some View {
        if card.buttons.contains(where: { $0.detail != nil }) {
            explainedChoices
        } else {
            plainChoices
        }
    }

    /// Choices that each explain themselves, e.g. annex, puppet or raze a conquered city.
    /// They scroll only when taller than the card allows.
    private var explainedChoices: some View {
        ScrollView(showsIndicators: true) {
            VStack(spacing: 10) {
                ForEach(Array(card.buttons.enumerated()).filter { $0.element.inline != true }, id: \.offset) { index, button in
                    Button { press(index) } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(button.label)
                                    .font(.headline)
                                    .foregroundStyle(button.destructive ? Palette.unhappiness : .white)
                                Spacer(minLength: 8)
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                            }
                            if let detail = button.detail {
                                GameText(detail)
                                    .font(.footnote)
                                    .foregroundStyle(.white.opacity(0.7))
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color.white.opacity(0.07))
                                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(button.destructive ? Palette.unhappiness.opacity(0.5) : Theme.hairline, lineWidth: 1))
                        )
                    }
                    .buttonStyle(PressableStyle())
                    .disabled(!button.enabled)
                    .opacity(button.enabled ? 1 : 0.45)
                }
            }
            .background(GeometryReader { proxy in
                Color.clear.preference(key: ChoicesHeightKey.self, value: proxy.size.height)
            })
        }
        .frame(height: min(choicesHeight ?? 420, 420))
        .onPreferenceChange(ChoicesHeightKey.self) { choicesHeight = $0 }
    }

    private var plainChoices: some View {
        let main = Array(card.buttons.enumerated()).filter { $0.element.inline != true }
        let single = main.count == 1
        return VStack(spacing: 10) {
            ForEach(main, id: \.offset) { index, button in
                Button { press(index) } label: {
                    Text(button.label)
                        .font(.headline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(button.destructive ? Palette.unhappiness : (single ? Theme.ink : .white))
                        .padding(.horizontal, 18)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background {
                            if single && !button.destructive {
                                Capsule().fill(Theme.accentGradient)
                            } else {
                                Capsule().fill(Color.white.opacity(0.08))
                                    .overlay(Capsule().strokeBorder(button.destructive ? Palette.unhappiness.opacity(0.6) : Theme.hairline, lineWidth: 1))
                            }
                        }
                }
                .buttonStyle(PressableStyle())
                .disabled(!button.enabled)
                .opacity(button.enabled ? 1 : 0.45)
            }
        }
    }

    private var footer: some View {
        HStack(spacing: 12) {
            if card.pages.count > 1 {
                HStack(spacing: 6) {
                    ForEach(card.pages.indices, id: \.self) { index in
                        Capsule()
                            .fill(index == page ? Theme.accent : Color.white.opacity(0.22))
                            .frame(width: index == page ? 18 : 7, height: 7)
                    }
                }
                .animation(.spring(response: 0.3, dampingFraction: 0.8), value: page)
            }
            Spacer(minLength: 0)
            if page > 0 {
                Button {
                    withAnimation(.easeOut(duration: 0.2)) { page -= 1 }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 48, height: 48)
                        .background(Circle().fill(Color.white.opacity(0.1)))
                }
                .buttonStyle(PressableStyle())
            }
            Button {
                if isLastPage {
                    close()
                } else {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { page += 1 }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(isLastPage ? card.doneLabel : L("Next"))
                    if !isLastPage { Image(systemName: "arrow.right") }
                }
                .font(.headline)
                .padding(.horizontal, 24)
                .frame(minWidth: 120, minHeight: 48)
                .modifier(PrimaryCapsule())
            }
            .buttonStyle(PressableStyle())
        }
    }
}

private struct ChoicesHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

/// A popup's text fields and switches. Edits go straight back to the Scene2D widgets.
private struct PopupInputs: View {
    let inputs: [StoryCard.Input]
    let cardID: UUID
    let send: (Int, String) -> Void

    @State private var values: [Int: String] = [:]
    @FocusState private var focused: Int?

    var body: some View {
        VStack(spacing: 10) {
            ForEach(Array(inputs.enumerated()), id: \.offset) { index, input in
                if input.kind == "toggle" {
                    Toggle(isOn: Binding(
                        get: { (values[index] ?? input.value) == "true" },
                        set: { on in values[index] = on ? "true" : "false"; send(index, on ? "true" : "false") }
                    )) {
                        Text(input.label).font(.subheadline)
                    }
                    .tint(Theme.accent)
                } else {
                    TextField(input.placeholder ?? input.label, text: Binding(
                        get: { values[index] ?? input.value },
                        set: { text in values[index] = text; send(index, text) }
                    ))
                    .keyboardType(input.kind == "number" ? .numberPad : .default)
                    .multilineTextAlignment(input.kind == "number" ? .center : .leading)
                    .font(input.kind == "number" ? .title3.weight(.semibold).monospacedDigit() : .body)
                    .focused($focused, equals: index)
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.white.opacity(0.08)))
                }
            }
        }
        .onAppear {
            // The first text field gets the keyboard, like the Scene2D popup's focus
            if let first = inputs.firstIndex(where: { $0.kind != "toggle" }) {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { focused = first }
            }
        }
        // Content changed on the game's side (e.g. "+50" or clamping): show the game's values
        .onChange(of: inputs) { _ in values = [:] }
    }
}
