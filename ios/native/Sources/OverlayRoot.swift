import SwiftUI

struct OverlayRoot: View {
    @ObservedObject var model: OverlayModel

    var body: some View {
        ZStack {
            GeometryReader { proxy in
                Color.clear
                    .onAppear { model.windowHeight = proxy.size.height }
                    .onChange(of: proxy.size.height) { model.windowHeight = $0 }
            }
            .ignoresSafeArea()

            switch model.mode {
            case .hidden:
                Color.clear
            case .mainMenu(let menu):
                MainMenuView(menu: menu, perform: { model.perform("menu:" + $0) })
                    .transition(.opacity)
            case .world(let hud):
                if hud.visible {
                    WorldHudView(hud: hud, minimap: model.minimap,
                                 perform: { id, haptic in model.perform("hud:" + id, haptic: haptic) },
                                 send: { model.send("hud:" + $0) })
                        .transition(.opacity)
                }
            case .techTree(let tree):
                TechTreeView(tree: tree, perform: { id, haptic in model.perform("tech:" + id, haptic: haptic) })
                    .transition(.opacity)
            case .city(let city):
                CityScreenView(city: city, perform: { id, haptic in model.perform("city:" + id, haptic: haptic) })
                    .transition(.opacity)
            case .mapEditor(let editor):
                MapEditorView(editor: editor, perform: { id, haptic in model.perform("editor:" + id, haptic: haptic) })
                    .transition(.opacity)
            case .screen(let screen):
                NativeScreenView(screen: screen, perform: { id, haptic in model.perform("screen:" + id, haptic: haptic) })
                    .environment(\.opaqueCards, true)
                    .transition(.opacity)
            }
        }
        .overlay {
            if let sheet = model.sheet {
                SheetContainer {
                    NativeScreenView(screen: sheet, perform: { id, haptic in model.perform("sheet:" + id, haptic: haptic) })
                        .environment(\.opaqueCards, true)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.38, dampingFraction: 0.88), value: model.sheet != nil)
        .overlay(alignment: .top) {
            if let toast = model.toast {
                ToastView(message: toast.message) { model.dismissToast() }
                    .id(toast.id)
                    .padding(.top, 6)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: model.toast?.id)
        .overlay {
            if let card = model.modal {
                StoryCardView(card: card,
                              close: { model.perform("modal:close", haptic: .medium) },
                              press: { model.perform("modal:button:\($0)", haptic: .medium) },
                              input: { model.send("modal:input:\($0):\($1)") })
                    .id(card.id)
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: model.modal?.id)
        // Above everything, popup cards included: while a game loads, the logo is the loading UI
        .overlay {
            if let curtain = model.curtain {
                SwitchCurtain(loading: curtain == .loading)
                    .transition(.opacity)
            }
        }
        // No fade between modes: the switch curtain covers a screen change and fades by itself.
        // Fading the modes as well let the map show through both for a moment.
        .collectInteractiveRegions(into: model)
        .environment(\.colorScheme, .dark)
        .tint(Palette.accent)
    }
}

enum Palette {
    static let accent = Color(red: 0.91, green: 0.66, blue: 0.30)
    static let gold = Color(red: 0.98, green: 0.80, blue: 0.27)
    static let science = Color(red: 0.42, green: 0.71, blue: 1.0)
    static let happiness = Color(red: 0.45, green: 0.80, blue: 0.38)
    static let unhappiness = Color(red: 0.95, green: 0.38, blue: 0.33)
    static let culture = Color(red: 0.80, green: 0.55, blue: 0.98)
    static let faith = Color(red: 0.95, green: 0.95, blue: 0.80)
}

/// Covers the game while it switches screens. Loading a game shows the logo and a spinner.
struct SwitchCurtain: View {
    let loading: Bool

    var body: some View {
        ZStack {
            Theme.ink.ignoresSafeArea()
            if loading {
                VStack(spacing: 22) {
                    if let logo = GameIconStore.shared.image("ExtraImages/Icons/Preview-Monolith.png") {
                        Image(uiImage: logo)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 112, height: 112)
                            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                            .shadow(color: .black.opacity(0.4), radius: 16, y: 6)
                    }
                    ProgressView().tint(Theme.accent)
                }
                .transition(.opacity)
            }
        }
        .interactiveRegion("curtain")
    }
}

/// Short message at the top of the screen; tap to dismiss early.
struct ToastView: View {
    let message: String
    let dismiss: () -> Void

    var body: some View {
        Button(action: dismiss) {
            HStack(spacing: 10) {
                Image(systemName: "bell.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.accent)
                Text(message)
                    .font(.subheadline.weight(.medium))
                    .multilineTextAlignment(.leading)
                    .lineLimit(3)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: 460)
            .capsuleCard()
        }
        .buttonStyle(PressableStyle())
        .padding(.horizontal, 20)
        .interactiveRegion("toast")
    }
}

/// Presents a native screen as a sheet: full screen on iPhone, a centred card on wide screens.
struct SheetContainer<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        GeometryReader { proxy in
            let wide = proxy.size.width >= 700
            ZStack {
                Color.black.opacity(0.5).ignoresSafeArea()
                if wide {
                    content()
                        .frame(maxWidth: 640, maxHeight: min(proxy.size.height - 40, 900))
                        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                        .shadow(color: .black.opacity(0.4), radius: 30, y: 10)
                } else {
                    // Full screen: the content's own background runs under the safe areas
                    content()
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }
}
