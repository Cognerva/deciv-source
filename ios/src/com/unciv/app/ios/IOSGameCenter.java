package com.unciv.app.ios;

import com.unciv.ui.nativeui.GameCenterProgress;

/** Game Center for {@link GameCenterProgress}, implemented in Swift ({@code GameCenter}). Main thread only. */
final class IOSGameCenter implements GameCenterProgress.Platform {
    @Override
    public void authenticate() {
        NativeUIBridge.gameCenterAuthenticate();
    }

    @Override
    public void submitScore(int score, String leaderboard) {
        NativeUIBridge.gameCenterSubmitScore(score, leaderboard);
    }

    @Override
    public void unlock(String achievement) {
        NativeUIBridge.gameCenterUnlock(achievement);
    }

    @Override
    public void showDashboard() {
        NativeUIBridge.gameCenterShowDashboard();
    }
}
