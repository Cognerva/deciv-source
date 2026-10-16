import GameKit
import UIKit

/// Game Center for single-player games: sign-in, leaderboard scores, achievements and the
/// dashboard. The game side (`com.unciv.ui.nativeui.GameCenterProgress`) decides what to report;
/// everything here is fire-and-forget and silently does nothing while the player is signed out.
enum GameCenter {
    private static var reported = Set<String>()

    static var isSignedIn: Bool { GKLocalPlayer.local.isAuthenticated }

    /// Shows Apple's sign-in sheet if needed; Game Center then shows its own welcome banner
    static func authenticate(presentingFrom host: @escaping () -> UIViewController?) {
        GKLocalPlayer.local.authenticateHandler = { viewController, error in
            if let viewController, let host = host() {
                host.present(viewController, animated: true)
            } else if let error {
                NSLog("[DecivNativeUI] Game Center sign-in: %@", error.localizedDescription)
            }
        }
    }

    static func submitScore(_ score: Int, leaderboard: String) {
        guard isSignedIn else { return }
        GKLeaderboard.submitScore(score, context: 0, player: GKLocalPlayer.local, leaderboardIDs: [leaderboard]) { error in
            if let error { NSLog("[DecivNativeUI] Game Center score %@: %@", leaderboard, error.localizedDescription) }
        }
    }

    static func unlock(_ achievement: String) {
        guard isSignedIn, !reported.contains(achievement) else { return }
        reported.insert(achievement)
        let progress = GKAchievement(identifier: achievement)
        progress.percentComplete = 100
        progress.showsCompletionBanner = true
        GKAchievement.report([progress]) { error in
            if let error {
                reported.remove(achievement)
                NSLog("[DecivNativeUI] Game Center achievement %@: %@", achievement, error.localizedDescription)
            }
        }
    }

    static func showDashboard(from host: UIViewController) {
        let dashboard = GKGameCenterViewController(state: .default)
        dashboard.gameCenterDelegate = DashboardDelegate.shared
        host.present(dashboard, animated: true)
    }

    private final class DashboardDelegate: NSObject, GKGameCenterControllerDelegate {
        static let shared = DashboardDelegate()
        func gameCenterViewControllerDidFinish(_ controller: GKGameCenterViewController) {
            controller.dismiss(animated: true)
        }
    }
}
