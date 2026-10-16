import SwiftUI
import UIKit

/// Objective-C entry point called from the RoboVM side (`com.unciv.app.ios.NativeUIBridge`).
///
/// The Java side pushes JSON snapshots produced by the Kotlin core and receives string action
/// ids back through `performNativeAction:` on the target it installs. Everything runs on the
/// main thread, which is also libGDX's render thread on iOS.
@objc(DCVNativeUI)
public final class DCVNativeUI: NSObject {
    private static var overlay: OverlayController?

    @objc(installInViewController:actionTarget:)
    public static func install(in viewController: UIViewController, actionTarget: NSObject) {
        guard overlay == nil else { return }
        overlay = OverlayController(host: viewController, actionTarget: actionTarget)
    }

    @objc(showMainMenu:)
    public static func showMainMenu(_ json: String) {
        guard let menu = decode(MainMenuState.self, from: json) else { return }
        overlay?.model.mode = .mainMenu(menu)
    }

    @objc(showWorldHud:)
    public static func showWorldHud(_ json: String) {
        guard let hud = decode(WorldHudState.self, from: json) else { return }
        overlay?.model.mode = .world(hud)
    }

    @objc(showCityScreen:)
    public static func showCityScreen(_ json: String) {
        guard let city = decode(CityState.self, from: json) else { return }
        overlay?.model.mode = .city(city)
    }

    @objc(showTechTree:)
    public static func showTechTree(_ json: String) {
        guard let tree = decode(TechTreeState.self, from: json) else { return }
        overlay?.model.mode = .techTree(tree)
    }

    @objc(showToast:duration:)
    public static func showToast(_ message: String, duration: Double) {
        overlay?.model.enqueueToast(message, duration: duration)
    }

    @objc(showModal:)
    public static func showModal(_ json: String) {
        guard let card = decode(StoryCard.self, from: json) else { return }
        overlay?.model.modal = card
    }

    @objc(updateModal:)
    public static func updateModal(_ json: String) {
        guard var card = decode(StoryCard.self, from: json) else { return }
        if let current = overlay?.model.modal { card.id = current.id }
        overlay?.model.modal = card
    }

    @objc(hideModal)
    public static func hideModal() {
        overlay?.model.modal = nil
    }

    @objc(showMinimap:)
    public static func showMinimap(_ json: String) {
        guard let minimap = decode(MinimapState.self, from: json) else { return }
        overlay?.model.minimap.state = minimap
    }

    @objc(setMinimapCameraX:y:width:height:)
    public static func setMinimapCamera(x: Double, y: Double, width: Double, height: Double) {
        overlay?.model.minimap.camera = CGRect(x: x, y: y, width: width, height: height)
    }

    @objc(showMapEditor:)
    public static func showMapEditor(_ json: String) {
        guard let editor = decode(MapEditorState.self, from: json) else { return }
        overlay?.model.mode = .mapEditor(editor)
    }

    @objc(showScreen:json:)
    public static func showScreen(_ kind: String, json: String) {
        guard let screen = NativeScreen.decode(kind: kind, json: json) else { return }
        overlay?.model.mode = .screen(screen)
    }

    @objc(showSheet:json:)
    public static func showSheet(_ kind: String, json: String) {
        guard let sheet = NativeScreen.decode(kind: kind, json: json) else { return }
        overlay?.model.sheet = sheet
    }

    @objc(hideSheet)
    public static func hideSheet() {
        overlay?.model.sheet = nil
    }

    @objc(showCurtainLoading:)
    public static func showCurtain(loading: Bool) {
        overlay?.model.showCurtain(loading: loading)
    }

    @objc(hideCurtain)
    public static func hideCurtain() {
        overlay?.model.hideCurtain()
    }

    // MARK: Game Center

    private static var topViewController: UIViewController? {
        let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
            ?? UIApplication.shared.windows.first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    @objc(gameCenterAuthenticate)
    public static func gameCenterAuthenticate() {
        GameCenter.authenticate { topViewController }
    }

    @objc(gameCenterSignedIn)
    public static func gameCenterSignedIn() -> Bool {
        GameCenter.isSignedIn
    }

    @objc(gameCenterSubmitScore:leaderboard:)
    public static func gameCenterSubmitScore(_ score: Int, leaderboard: String) {
        GameCenter.submitScore(score, leaderboard: leaderboard)
    }

    @objc(gameCenterUnlock:)
    public static func gameCenterUnlock(_ achievement: String) {
        GameCenter.unlock(achievement)
    }

    @objc(gameCenterShowDashboard)
    public static func gameCenterShowDashboard() {
        guard let host = topViewController else { return }
        GameCenter.showDashboard(from: host)
    }

    // MARK: iCloud saves (blocking; called from a background thread)

    @objc(cloudSavesAvailable)
    public static func cloudSavesAvailable() -> Bool {
        CloudSaveStore.savesURL != nil
    }

    /// Signed in to iCloud but no container: this build lacks the iCloud entitlement
    @objc(cloudSavesConfigured)
    public static func cloudSavesConfigured() -> Bool {
        FileManager.default.ubiquityIdentityToken == nil || CloudSaveStore.savesURL != nil
    }

    @objc(cloudUpload:name:)
    public static func cloudUpload(_ path: String, name: String) {
        CloudSaveStore.upload(localPath: path, name: name)
    }

    @objc(cloudDelete:)
    public static func cloudDelete(_ name: String) {
        CloudSaveStore.delete(name: name)
    }

    @objc(cloudSyncInto:)
    public static func cloudSync(into folder: String) -> Int32 {
        Int32(CloudSaveStore.sync(localFolder: folder))
    }

    @objc(resetStrings)
    public static func resetStrings() {
        Strings.reset()
    }

    /// Local-only automation from the `--ui-script` launch argument; views observe `.debugCommand`.
    @objc(debugCommand:)
    public static func debugCommand(_ command: String) {
        NSLog("[DecivNativeUI] debug command %@", command)
        if command.hasPrefix("rotate:") {
            rotate(landscape: command == "rotate:landscape")
            return
        }
        NotificationCenter.default.post(name: .debugCommand, object: command)
    }

    /// Asks the scene to turn, for checking layouts without touching the simulator
    private static func rotate(landscape: Bool) {
        guard #available(iOS 16.0, *),
              let scene = UIApplication.shared.connectedScenes.first(where: { $0 is UIWindowScene }) as? UIWindowScene
        else { return }
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: landscape ? .landscapeRight : .portrait)) { error in
            NSLog("[DecivNativeUI] rotate: %@", String(describing: error))
        }
    }

    @objc(hideAll)
    public static func hideAll() {
        overlay?.model.mode = .hidden
    }

    private static func decode<T: Decodable>(_ type: T.Type, from json: String) -> T? {
        do {
            return try JSONDecoder().decode(type, from: Data(json.utf8))
        } catch {
            NSLog("[DecivNativeUI] Could not decode %@: %@", String(describing: type), String(describing: error))
            return nil
        }
    }
}

/// Hosts the SwiftUI overlay above the libGDX view and forwards actions to Java.
final class OverlayController {
    let model = OverlayModel()
    private let container = PassthroughView()
    private let hosting: UIHostingController<OverlayRoot>

    init(host: UIViewController, actionTarget: NSObject) {
        let model = self.model
        hosting = UIHostingController(rootView: OverlayRoot(model: model))
        model.send = { [weak actionTarget] id in
            actionTarget?.perform(NSSelectorFromString("performNativeAction:"), with: id)
        }
        Strings.translator = { [weak actionTarget] key in
            actionTarget?.perform(NSSelectorFromString("translate:"), with: key)?.takeUnretainedValue() as? String
        }

        hosting.view.backgroundColor = .clear
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        container.translatesAutoresizingMaskIntoConstraints = false
        container.backgroundColor = .clear
        container.model = model

        host.addChild(hosting)
        host.view.addSubview(container)
        container.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            container.leadingAnchor.constraint(equalTo: host.view.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: host.view.trailingAnchor),
            container.topAnchor.constraint(equalTo: host.view.topAnchor),
            container.bottomAnchor.constraint(equalTo: host.view.bottomAnchor),
            hosting.view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            hosting.view.topAnchor.constraint(equalTo: container.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        ])
        hosting.didMove(toParent: host)
    }
}

/// Lets touches through to the libGDX map except where native controls are drawn.
///
/// SwiftUI does not create a UIView per control, so hit-testing the hosting view cannot tell a
/// button from empty space. The views report their frames instead (see `interactiveRegion`).
final class PassthroughView: UIView {
    weak var model: OverlayModel?

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let model else { return nil }
        // A modal card or a sheet takes every touch until it is dismissed
        if model.modal != nil || model.sheet != nil { return super.hitTest(point, with: event) }
        guard model.mode.isVisible else { return nil }
        if !model.mode.passesTouchesThrough { return super.hitTest(point, with: event) }
        let pointInWindow = convert(point, to: nil)
        guard model.interactiveFrames.values.contains(where: { $0.contains(pointInWindow) }) else { return nil }
        return super.hitTest(point, with: event)
    }
}

extension Notification.Name {
    /// Carries a `--ui-script` `ui:` command string, e.g. `openMenu` or `picker:civ`
    static let debugCommand = Notification.Name("DCVDebugCommand")
}

extension View {
    /// Runs `action` for `--ui-script` commands (local-only automation)
    func onDebugCommand(_ action: @escaping (String) -> Void) -> some View {
        onReceive(NotificationCenter.default.publisher(for: .debugCommand)) { note in
            if let command = note.object as? String { action(command) }
        }
    }
}
