import SwiftUI
import UIKit

/// A settings-style native screen built by `com.unciv.ui.nativeui.NativeFormPresenter`:
/// sections of toggles, pickers, sliders and buttons, with an optional primary action.
struct FormView: View {
    let form: FormState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var collapsed: [String: Bool] = [:]
    /// A long list pushed over the form, e.g. the nation picker
    @State private var picker: PickerRequest?

    struct PickerRequest: Identifiable {
        let id: String
        let title: String
        let value: String
        let options: [FormState.Option]
        /// Action id prefix; the picked option's id is appended
        let action: String
    }

    var body: some View {
        ZStack {
            FormBackground()

            VStack(spacing: 0) {
                FormTopBar(title: form.title, subtitle: form.subtitle,
                           closeSymbol: form.hasClose ? form.closeSymbol : nil) { perform("tap:close", .light) }

                ScrollViewReader { scroller in
                    ScrollView(showsIndicators: false) {
                        LazyVStack(alignment: .leading, spacing: 22) {
                            if let header = form.header {
                                ForEach(header.items) { item in row(item) }
                            }
                            ForEach(form.sections) { section in
                                sectionView(section).id("section." + section.id)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .padding(.bottom, form.primary == nil ? 32 : 110)
                        .frame(maxWidth: 680)
                        .frame(maxWidth: .infinity)
                    }
                    .onDebugCommand { command in
                        let argument = String(command.drop { $0 != ":" }.dropFirst())
                        if command.hasPrefix("scroll:") {
                            collapsed[argument] = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                withAnimation { scroller.scrollTo("section." + argument, anchor: .top) }
                            }
                        } else if command.hasPrefix("picker:"),
                                  let item = form.sections.flatMap(\.items).first(where: { $0.id == argument }) {
                            openPicker(for: item, action: "set:\(item.id):")
                        }
                    }
                }
            }

            if let primary = form.primary {
                VStack {
                    Spacer()
                    PrimaryBar(primary: primary) { perform("tap:primary", .medium) }
                }
            }

            if let picker {
                OptionPicker(request: picker, close: { self.picker = nil }) { option in
                    self.picker = nil
                    perform(picker.action + option, .light)
                }
                .transition(.move(edge: .trailing))
                .zIndex(2)
            }

            if let busy = form.busy {
                BusyOverlay(text: busy).zIndex(3)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.9), value: picker?.id)
        .alert(isPresented: Binding(get: { form.alert != nil && form.busy == nil }, set: { _ in })) {
            Alert(
                title: Text(form.alert?.title ?? ""),
                message: Text(form.alert?.message ?? ""),
                dismissButton: .default(Text(L("OK"))) { perform("tap:alert", .light) }
            )
        }
    }

    // MARK: Sections

    @ViewBuilder
    private func sectionView(_ section: FormState.Section) -> some View {
        let isOpen = !section.collapsible || !(collapsed[section.id] ?? !section.expanded)
        VStack(alignment: .leading, spacing: 8) {
            if let title = section.title {
                if section.collapsible {
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) { collapsed[section.id] = isOpen }
                    } label: {
                        HStack {
                            sectionIcon(section)
                            FormSectionTitle(text: title)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                                .rotationEffect(.degrees(isOpen ? 0 : -90))
                        }
                        .frame(minHeight: 32)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    HStack {
                        sectionIcon(section)
                        FormSectionTitle(text: title)
                    }
                }
            }
            if isOpen && !section.items.isEmpty {
                // Lazy: some sections hold hundreds of rows, e.g. the downloadable mods
                LazyVStack(spacing: 0) {
                    ForEach(Array(section.items.enumerated()), id: \.element.id) { index, item in
                        if index > 0 { Divider().overlay(Theme.hairline).padding(.leading, 16) }
                        row(item)
                    }
                }
                .card(cornerRadius: 18)
                .transition(.opacity)
            }
            if isOpen, let footer = section.footer {
                Text(footer)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
            }
        }
    }

    @ViewBuilder
    private func sectionIcon(_ section: FormState.Section) -> some View {
        if let icon = section.icon, GameIconStore.shared.image(icon) != nil {
            // Stat icons are coloured discs already; silhouettes take the accent
            GameIcon(name: icon, tint: icon.hasPrefix("StatIcons/") ? nil : Theme.accent)
                .frame(width: 18, height: 18)
                .padding(.leading, 6)
        }
    }

    // MARK: Rows

    @ViewBuilder
    private func row(_ item: FormState.Item) -> some View {
        Group {
            switch item.type {
            case "toggle": ToggleRow(item: item, perform: perform)
            case "choice": choiceRow(item)
            case "multi": MultiRow(item: item, perform: perform)
            case "slider": SliderRow(item: item, perform: perform)
            case "stepper": StepperRow(item: item, perform: perform)
            case "button": ButtonRow(item: item, perform: perform)
            case "tiles" where item.style == "icons": IconGridRow(item: item, perform: perform)
            case "tiles": TilesRow(item: item, perform: perform)
            case "row": ListRow(item: item, perform: perform)
            case "meter": MeterRow(item: item)
            case "search": SearchRow(item: item, perform: perform)
            case "field": FieldRow(item: item, perform: perform)
            case "chart": ChartRow(item: item)
            case "replay": ReplayRow(item: item)
            case "markup": MarkupView(item: item, perform: perform)
            case "player": PlayerRow(item: item, perform: perform) { openPicker(for: item, action: "set:\(item.id).civ:") }
            default: TextRow(item: item)
            }
        }
        .disabled(!item.enabled)
        .opacity(item.enabled ? 1 : 0.45)
    }

    private func options(of item: FormState.Item) -> [FormState.Option] {
        if let catalog = item.catalog { return form.catalogs[catalog] ?? [] }
        return item.options ?? []
    }

    private func openPicker(for item: FormState.Item, action: String) {
        picker = PickerRequest(id: item.id, title: item.label, value: item.value ?? "",
                               options: options(of: item), action: action)
    }

    @ViewBuilder
    private func choiceRow(_ item: FormState.Item) -> some View {
        let options = options(of: item)
        let current = options.first { $0.id == item.value }
        switch item.style {
        case "chips":
            ChipsRow(item: item, options: options, perform: perform)
        case "segmented":
            VStack(alignment: .leading, spacing: 8) {
                if !item.label.isEmpty { RowLabel(item: item) }
                Picker(item.label, selection: Binding(get: { item.value ?? "" }, set: { perform("set:\(item.id):\($0)", .light) })) {
                    ForEach(options) { option in Text(option.label).tag(option.id) }
                }
                .pickerStyle(.segmented)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        case "list":
            Button { openPicker(for: item, action: "set:\(item.id):") } label: {
                HStack(spacing: 12) {
                    if current?.icon != nil || current?.symbol != nil || item.icon != nil {
                        OptionBadge(icon: current?.icon ?? (current?.symbol == nil ? item.icon : nil), symbol: current?.symbol,
                                    background: current?.background, tint: current?.tint, size: 34)
                    }
                    RowLabel(item: item)
                    Spacer(minLength: 8)
                    Text(current?.label ?? item.value ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.tertiary)
                }
                .rowPadding()
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        default:
            HStack(spacing: 12) {
                RowLabel(item: item)
                Spacer(minLength: 8)
                Menu {
                    ForEach(options) { option in
                        Button {
                            perform("set:\(item.id):\(option.id)", .light)
                        } label: {
                            if option.id == item.value {
                                Label(option.label, systemImage: "checkmark")
                            } else {
                                Text(option.label)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(current?.label ?? item.value ?? "")
                            .lineLimit(1)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2.weight(.bold))
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Theme.accent)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
        }
    }
}

// MARK: - Chrome

struct FormBackground: View {
    var body: some View {
        ZStack {
            Theme.ink
            LinearGradient(colors: [Theme.inkRaised.opacity(0.9), Theme.ink], startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [Theme.accent.opacity(0.10), .clear], center: .topTrailing, startRadius: 10, endRadius: 520)
        }
        .ignoresSafeArea()
    }
}

struct FormTopBar: View {
    let title: String
    let subtitle: String?
    /// nil hides the close button
    let closeSymbol: String?
    let close: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            if let closeSymbol {
                Button(action: close) {
                    Image(systemName: Symbols.named(closeSymbol, fallback: "xmark"))
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 44, height: 44)
                        .background(CardBackground(shape: Circle()))
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(L("Close"))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(Theme.display(24))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                if let subtitle {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity)
    }
}

struct FormSectionTitle: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.bold))
            .tracking(0.8)
            .foregroundStyle(.white.opacity(0.55))
            .padding(.horizontal, 6)
    }
}

private struct PrimaryBar: View {
    let primary: FormState.Primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let symbol = primary.symbol {
                    Image(systemName: Symbols.named(symbol)).font(.system(size: 17, weight: .bold))
                }
                Text(primary.label).font(.headline)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .modifier(PrimaryCapsule())
        }
        .buttonStyle(PressableStyle())
        .disabled(!primary.enabled)
        .opacity(primary.enabled ? 1 : 0.5)
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 10)
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: [Theme.ink.opacity(0), Theme.ink.opacity(0.92)], startPoint: .top, endPoint: .center)
                .ignoresSafeArea()
        )
    }
}

struct BusyOverlay: View {
    let text: String

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            VStack(spacing: 14) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Theme.accent)
                    .scaleEffect(1.3)
                Text(text)
                    .font(.headline)
                    .multilineTextAlignment(.center)
            }
            .padding(28)
            .frame(minWidth: 200)
            .card(cornerRadius: 24)
        }
        .transition(.opacity)
    }
}

private extension View {
    func rowPadding() -> some View {
        padding(.horizontal, 16).padding(.vertical, 8).frame(minHeight: 52)
    }
}

// MARK: - Rows

private struct RowLabel: View {
    let item: FormState.Item

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            GameText(item.label)
                .font(.body)
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
            if let detail = item.detail {
                GameText(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

private struct ToggleRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        Toggle(isOn: Binding(get: { item.on ?? false }, set: { perform("set:\(item.id):\($0)", .light) })) {
            RowLabel(item: item)
        }
        .tint(Theme.accent)
        .rowPadding()
    }
}

private struct TextRow: View {
    let item: FormState.Item

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            if let symbol = item.symbol {
                Image(systemName: Symbols.named(symbol)).foregroundStyle(Theme.accent)
            }
            RowLabel(item: item)
            Spacer(minLength: 8)
            if let value = item.value {
                GameText(value)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.trailing)
            }
        }
        .rowPadding()
    }
}

private struct ButtonRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var confirming = false

    private var color: Color {
        switch item.role {
        case "destructive": return Palette.unhappiness
        case "primary": return Theme.accent
        default: return .white
        }
    }

    var body: some View {
        Button {
            if item.confirm != nil { confirming = true } else { perform("tap:\(item.id)", .light) }
        } label: {
            HStack(spacing: 12) {
                if let symbol = item.symbol {
                    IconTile(symbol: symbol, color: item.role == "default" ? Theme.accent : color, size: 30)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.label)
                        .font(.body.weight(.medium))
                        .foregroundStyle(color)
                    if let detail = item.detail {
                        Text(detail).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
            .rowPadding()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .confirmationDialog(item.confirm ?? "", isPresented: $confirming, titleVisibility: .visible) {
            Button(item.label, role: item.role == "destructive" ? .destructive : nil) { perform("tap:\(item.id)", .medium) }
            Button(L("Cancel"), role: .cancel) {}
        }
    }
}

private struct SliderRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var value: Double = 0
    @State private var editing = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                RowLabel(item: item)
                Spacer()
                Text(formatted(editing ? value : (item.number ?? 0)))
                    .font(Theme.number(.subheadline))
                    .foregroundStyle(Theme.accent)
            }
            Slider(value: $value, in: (item.min ?? 0)...max(item.max ?? 1, (item.min ?? 0) + 0.0001), step: item.step ?? 1) { isEditing in
                editing = isEditing
                if !isEditing { perform("set:\(item.id):\(value)", .light) }
            }
            .tint(Theme.accent)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .onAppear { value = item.number ?? 0 }
        .onChange(of: item.number) { if !editing { value = $0 ?? 0 } }
    }

    private func formatted(_ value: Double) -> String {
        switch item.format {
        case "percent": return "\(Int((value * 100).rounded()))%"
        case "decimal": return String(format: "%.1f", value)
        default: return "\(Int(value.rounded()))"
        }
    }
}

private struct StepperRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        let value = Int(item.number ?? 0)
        HStack(spacing: 12) {
            RowLabel(item: item)
            Spacer(minLength: 8)
            HStack(spacing: 0) {
                stepButton("minus", enabled: Double(value) > (item.min ?? 0)) { perform("set:\(item.id):\(value - 1)", .light) }
                Text("\(value)")
                    .font(Theme.number(.headline))
                    .frame(minWidth: 34)
                stepButton("plus", enabled: Double(value) < (item.max ?? 0)) { perform("set:\(item.id):\(value + 1)", .light) }
            }
            .background(Capsule().fill(Color.white.opacity(0.08)))
        }
        .rowPadding()
    }

    private func stepButton(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .bold))
                .frame(width: 44, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
    }
}

/// A list row: icon badge, title and detail, a trailing value or status tag; tappable when it has an action.
private struct ListRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    private var tagColor: Color {
        switch item.style {
        case "good": return Palette.happiness
        case "accent": return Theme.accent
        case "bad": return Palette.unhappiness
        default: return Color(white: 0.7)
        }
    }

    var body: some View {
        let content = HStack(spacing: 12) {
            if item.icon != nil || item.symbol != nil {
                OptionBadge(icon: item.icon, symbol: item.symbol, background: item.background, tint: item.tint, size: 40)
            }
            VStack(alignment: .leading, spacing: 2) {
                GameText(item.label)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                if let detail = item.detail {
                    GameText(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let value = item.value {
                Text(value)
                    .font(Theme.number(.subheadline))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.trailing)
            }
            if let tag = item.tag {
                Text(tag)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(tagColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(tagColor.opacity(0.16)))
            }
            if item.role == "action" {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .frame(minHeight: 56)
        .background(item.on == true ? Theme.accent.opacity(0.12) : .clear)
        .contentShape(Rectangle())

        if item.role == "action" {
            Button { perform("tap:\(item.id)", .light) } label: { content }
                .buttonStyle(.plain)
        } else {
            content
        }
    }
}

/// Tabs as a scrolling row of chips, e.g. the overview's categories.
private struct ChipsRow: View {
    let item: FormState.Item
    let options: [FormState.Option]
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        ScrollViewReader { scroller in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options) { option in
                        let selected = option.id == item.value
                        Button { perform("set:\(item.id):\(option.id)", .light) } label: {
                            HStack(spacing: 6) {
                                if let symbol = option.symbol {
                                    Image(systemName: Symbols.named(symbol)).font(.system(size: 13, weight: .semibold))
                                }
                                Text(option.label).font(.subheadline.weight(.semibold))
                            }
                            .foregroundStyle(selected ? Theme.ink : .white)
                            .padding(.horizontal, 14)
                            .frame(height: 40)
                            .background {
                                if selected { Capsule().fill(Theme.accentGradient) } else { CardBackground(shape: Capsule()) }
                            }
                        }
                        .buttonStyle(PressableStyle())
                        .id(option.id)
                    }
                }
                .padding(.vertical, 2)
            }
            .onAppear { scroller.scrollTo(item.value ?? "", anchor: .center) }
        }
    }
}

/// A search field that sends its text as the player types, keeping its own text so typing stays smooth.
private struct SearchRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    @State private var text = ""

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField(item.label, text: $text)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.white.opacity(0.08)))
        .onAppear { text = item.value ?? "" }
        .onChange(of: text) { value in
            guard value != (item.value ?? "") else { return }
            perform("set:\(item.id):\(value)", .soft)
        }
    }
}

/// The game's formatted text: headers, separators, colours, indentation and tappable links.
private struct MarkupView: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array((item.lines ?? []).enumerated()), id: \.offset) { _, line in
                lineView(line)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func lineView(_ line: FormState.MarkupLine) -> some View {
        if line.separator {
            Divider().overlay(Theme.hairline).padding(.vertical, 4)
        } else if line.text.isEmpty && line.icon == nil {
            Spacer().frame(height: 6)
        } else {
            let content = HStack(alignment: .firstTextBaseline, spacing: 8) {
                if line.starred { Image(systemName: "star.fill").font(.caption2).foregroundStyle(Palette.gold) }
                if let icon = line.icon, GameIconStore.shared.image(icon) != nil {
                    GameIcon(name: icon)
                        .frame(width: line.header > 0 ? 28 : 20, height: line.header > 0 ? 28 : 20)
                        .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 5 }
                }
                GameText(line.text)
                    .font(font(for: line))
                    .foregroundStyle(color(for: line))
                    .multilineTextAlignment(line.centered ? .center : .leading)
                    .fixedSize(horizontal: false, vertical: true)
                if line.link != nil {
                    Image(systemName: line.external ? "arrow.up.right.square" : "chevron.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(Theme.accent.opacity(0.8))
                }
            }
            .padding(.leading, CGFloat(line.indent) * 18)
            .frame(maxWidth: .infinity, alignment: line.centered ? .center : .leading)

            if let link = line.link {
                Button { perform("set:\(item.id):\(link)", .light) } label: { content.contentShape(Rectangle()) }
                    .buttonStyle(.plain)
            } else {
                content
            }
        }
    }

    private func font(for line: FormState.MarkupLine) -> Font {
        switch line.header {
        case 1: return Theme.display(26)
        case 2: return Theme.display(22)
        case 3: return Theme.display(19, weight: .semibold)
        case 4: return .headline
        case 5: return .subheadline.weight(.semibold)
        default: return .body
        }
    }

    private func color(for line: FormState.MarkupLine) -> Color {
        if line.link != nil { return Theme.accent }
        // Ruleset colours were picked for a light page; dark ones are lifted to stay readable here
        if let color = line.color { return Color(gameColor: color).readableOnDark }
        return .white.opacity(0.88)
    }
}

/// A labelled progress bar.
private struct MeterRow: View {
    let item: FormState.Item

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(item.label).font(.subheadline.weight(.semibold))
                Spacer()
                if let value = item.value {
                    Text(value).font(Theme.number(.subheadline)).foregroundStyle(.secondary)
                }
            }
            Meter(value: item.number ?? 0, color: item.tint.map { Color(hex: $0) } ?? Theme.accent, height: 6)
            if let detail = item.detail {
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

/// Wrapping chips, several of which can be on.
private struct MultiRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            RowLabel(item: item)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], alignment: .leading, spacing: 8) {
                ForEach(item.options ?? []) { option in
                    let on = item.selected?.contains(option.id) == true
                    Button { perform("set:\(item.id):\(option.id)", .light) } label: {
                        HStack(spacing: 6) {
                            Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(on ? Theme.accent : .white.opacity(0.35))
                            Text(option.label)
                                .font(.subheadline.weight(.medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 10)
                        .frame(minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(on ? Theme.accent.opacity(0.16) : Color.white.opacity(0.05))
                        )
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

/// Big selectable cards in a row, e.g. game presets.
private struct TilesRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(item.options ?? []) { option in
                    let selected = option.id == item.value
                    Button { perform("set:\(item.id):\(option.id)", .medium) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                if let icon = option.icon, let image = GameIconStore.shared.image(icon) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 56, height: 56)
                                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                                } else {
                                    IconTile(symbol: option.symbol ?? "circle.fill",
                                             color: option.tint.map { Color(hex: $0) } ?? Theme.accent, size: 34)
                                }
                                Spacer()
                                if selected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 20))
                                        .foregroundStyle(Theme.accent)
                                }
                            }
                            Text(option.label)
                                .font(.headline)
                                .foregroundStyle(.white)
                            if let detail = option.detail {
                                Text(detail)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14)
                        .frame(width: 168, height: 150, alignment: .topLeading)
                        .background(
                            ZStack {
                                CardBackground(shape: RoundedRectangle(cornerRadius: 20, style: .continuous))
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .strokeBorder(selected ? Theme.accent : .clear, lineWidth: 2)
                            }
                        )
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 2)
        }
    }
}

/// A player slot: nation, Human/AI switch and remove.
private struct PlayerRow: View {
    let item: FormState.Item
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void
    let chooseNation: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: chooseNation) {
                HStack(spacing: 12) {
                    OptionBadge(icon: item.icon, symbol: item.icon == nil ? "questionmark" : nil,
                                background: item.background, tint: item.tint, size: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.label)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        if let detail = item.detail {
                            Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                    Spacer(minLength: 4)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button { perform("tap:\(item.id).type", .light) } label: {
                Text(item.tag ?? "")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(item.on == true ? Theme.ink : .white)
                    .padding(.horizontal, 12)
                    .frame(minWidth: 64, minHeight: 32)
                    .background(Capsule().fill(item.on == true ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.white.opacity(0.10))))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle())
            .disabled(item.canChangeType != true)
            .opacity(item.canChangeType == true ? 1 : 0.5)

            if item.canRemove == true {
                Button { perform("tap:\(item.id).remove", .light) } label: {
                    Image(systemName: "minus.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(Palette.unhappiness.opacity(0.9))
                        .frame(width: 36, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(L("Remove"))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .frame(minHeight: 60)
    }
}

/// An option's icon: an atlas icon on a coloured disc (nations), or an SF Symbol.
struct OptionBadge: View {
    let icon: String?
    let symbol: String?
    let background: String?
    let tint: String?
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(background.map { Color(hex: $0) } ?? Color.white.opacity(0.10))
            if let icon {
                GameIcon(name: icon, fallback: symbol ?? "circle.fill", tint: tint.map { Color(hex: $0) })
                    .padding(size * (background == nil ? 0.12 : 0.2))
            } else if let symbol {
                Image(systemName: Symbols.named(symbol))
                    .font(.system(size: size * 0.42, weight: .bold))
                    .foregroundStyle(tint.map { Color(hex: $0) } ?? .white)
            }
        }
        .frame(width: size, height: size)
        .overlay(Circle().strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
    }
}

/// A long option list pushed over the form, with search for long lists.
private struct OptionPicker: View {
    let request: FormView.PickerRequest
    let close: () -> Void
    let pick: (String) -> Void

    @State private var query = ""

    private var filtered: [FormState.Option] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return request.options }
        return request.options.filter {
            $0.label.localizedCaseInsensitiveContains(trimmed) || ($0.detail?.localizedCaseInsensitiveContains(trimmed) ?? false)
        }
    }

    var body: some View {
        ZStack {
            FormBackground()
            VStack(spacing: 0) {
                FormTopBar(title: request.title, subtitle: nil, closeSymbol: "chevron.left", close: close)
                if request.options.count > 10 {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField(L("Search"), text: $query)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.white.opacity(0.08)))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                    .frame(maxWidth: 680)
                }
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered) { option in
                            Button { pick(option.id) } label: {
                                HStack(spacing: 12) {
                                    if option.icon != nil || option.symbol != nil {
                                        OptionBadge(icon: option.icon, symbol: option.symbol,
                                                    background: option.background, tint: option.tint, size: 44)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(option.label)
                                            .font(.body.weight(.semibold))
                                            .foregroundStyle(.white)
                                        if let detail = option.detail {
                                            Text(detail)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                                .lineLimit(2)
                                        }
                                    }
                                    Spacer(minLength: 8)
                                    if option.id == request.value {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                                    }
                                }
                                .padding(.horizontal, 16)
                                .frame(minHeight: 60)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Theme.hairline).padding(.leading, 16)
                        }
                    }
                    .card(cornerRadius: 18)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 30)
                    .frame(maxWidth: 680)
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}
