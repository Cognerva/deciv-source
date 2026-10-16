import SwiftUI

/// Native main menu drawn over the live, procedurally generated map that the Scene2D
/// main menu keeps rendering in the background.
struct MainMenuView: View {
    let menu: MainMenuState
    let perform: (String) -> Void

    @State private var appeared = false

    var body: some View {
        GeometryReader { proxy in
            let landscape = proxy.size.width > proxy.size.height
            ZStack {
                vignette.ignoresSafeArea()

                if landscape {
                    HStack(alignment: .center, spacing: 32) {
                        brand
                            .frame(maxWidth: 340, alignment: .leading)
                        Spacer(minLength: 0)
                        menuCard
                            .frame(width: min(420, proxy.size.width * 0.48))
                    }
                    .padding(.horizontal, 28)
                    .padding(.vertical, 16)
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        brand
                            .padding(.top, 24)
                        Spacer(minLength: 24)
                        menuCard
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
                }
            }
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 12)
            .onAppear {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.85)) { appeared = true }
            }
        }
    }

    private var vignette: some View {
        ZStack {
            // Keeps the brand legible over bright terrain
            LinearGradient(
                colors: [Theme.ink.opacity(0.7), Theme.ink.opacity(0.0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            LinearGradient(
                colors: [Theme.ink.opacity(0.75), Theme.ink.opacity(0.10), Theme.ink.opacity(0.85)],
                startPoint: .top,
                endPoint: .bottom
            )
            RadialGradient(
                colors: [.clear, Theme.ink.opacity(0.55)],
                center: .center,
                startRadius: 120,
                endRadius: 700
            )
        }
    }

    // MARK: Brand

    private var brand: some View {
        VStack(alignment: .leading, spacing: 10) {
            Emblem(size: 64)
            Text("DeCiv")
                .font(Theme.display(52))
                .shadow(color: .black.opacity(0.5), radius: 10, y: 3)
            Text(L("Hex strategy, one turn at a time"))
                .font(.title3.weight(.medium))
                .foregroundStyle(.white.opacity(0.75))
                .shadow(color: .black.opacity(0.6), radius: 6)

            HStack(spacing: 10) {
                RoundIconButton(symbol: "book.closed.fill", label: menu.labels.civilopedia) { perform("civilopedia") }
                if menu.supportAvailable {
                    RoundIconButton(
                        symbol: menu.supporter ? "heart.fill" : "heart",
                        label: menu.labels.support,
                        tint: menu.supporter ? Palette.gold : nil
                    ) { perform("support") }
                }
                RoundIconButton(symbol: "envelope.fill", label: menu.labels.contact) { perform("contact") }
            }
            .padding(.top, 8)
        }
    }

    // MARK: Menu

    private var menuCard: some View {
        let primary = menu.items.filter { !$0.secondary }
        let secondary = menu.items.filter { $0.secondary }
        return VStack(spacing: 10) {
            if let first = primary.first {
                Button { perform(first.id) } label: {
                    HStack(spacing: 12) {
                        Image(systemName: Symbols.mainMenu(first.id))
                            .font(.system(size: 18, weight: .bold))
                        Text(first.title)
                            .font(.headline)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .bold))
                            .opacity(0.6)
                    }
                    .padding(.horizontal, 20)
                    .frame(height: 56)
                    .modifier(PrimaryCapsule())
                }
                .buttonStyle(PressableStyle())
            }

            VStack(spacing: 0) {
                ForEach(Array(primary.dropFirst().enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider().overlay(Theme.hairline).padding(.leading, 58) }
                    Button { perform(item.id) } label: {
                        MenuRow(symbol: Symbols.mainMenu(item.id), title: item.title)
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            .card(cornerRadius: 18)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(secondary) { item in
                    Button { perform(item.id) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            IconTile(symbol: Symbols.mainMenu(item.id), color: tileColor(item.id), size: 30)
                            Text(item.title)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .card(cornerRadius: 16)
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
    }

    private func tileColor(_ id: String) -> Color {
        switch id {
        case "multiplayer": return Color(red: 0.35, green: 0.62, blue: 1.0)
        case "mapEditor": return Color(red: 0.35, green: 0.78, blue: 0.55)
        case "mods": return Color(red: 0.78, green: 0.52, blue: 0.98)
        case "gameCenter": return Color(red: 0.95, green: 0.45, blue: 0.55)
        default: return Color(white: 0.7)
        }
    }
}

private struct MenuRow: View {
    let symbol: String
    let title: String

    var body: some View {
        HStack(spacing: 14) {
            IconTile(symbol: symbol, color: Theme.accent, size: 32)
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white.opacity(0.35))
        }
        .padding(.horizontal, 14)
        .frame(height: 54)
        .contentShape(Rectangle())
    }
}

/// SF Symbol on a tinted rounded square, like Settings rows.
struct IconTile: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 32

    var body: some View {
        Image(systemName: Symbols.named(symbol))
            .font(.system(size: size * 0.48, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(
                RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                    .fill(color.opacity(0.18))
            )
    }
}

/// The app's hexagon mark.
struct Emblem: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Image(systemName: "hexagon.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .foregroundStyle(Theme.accentGradient)
                .shadow(color: Theme.accent.opacity(0.5), radius: 14)
            Image(systemName: "hexagon")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .foregroundStyle(.white.opacity(0.35))
                .padding(size * 0.16)
            Image(systemName: "building.columns.fill")
                .font(.system(size: size * 0.32, weight: .bold))
                .foregroundStyle(Theme.ink.opacity(0.85))
        }
        .frame(width: size, height: size)
    }
}

struct RoundIconButton: View {
    let symbol: String
    let label: String
    var tint: Color? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: Symbols.named(symbol))
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint ?? .white)
                .frame(width: 46, height: 46)
                .background(CardBackground(shape: Circle()))
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(label)
    }
}
