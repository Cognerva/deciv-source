package com.unciv.app.ios;

import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.ObjCRuntime;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/**
 * Binding for the Swift {@code DCVNativeUI} class in {@code ios/native}, which draws the
 * SwiftUI overlay above the libGDX view. Calls must be made on the main thread.
 */
@NativeClass("DCVNativeUI")
final class NativeUIBridge extends NSObject {
    static {
        ObjCRuntime.bind(NativeUIBridge.class);
    }

    private NativeUIBridge() {}

    /** Adds the overlay to {@code viewController}; actions arrive at {@code actionTarget}'s {@code performNativeAction:}. */
    @Method(selector = "installInViewController:actionTarget:")
    static native void install(UIViewController viewController, NSObject actionTarget);

    @Method(selector = "showMainMenu:")
    static native void showMainMenu(String json);

    @Method(selector = "showWorldHud:")
    static native void showWorldHud(String json);

    @Method(selector = "showCityScreen:")
    static native void showCityScreen(String json);

    @Method(selector = "showTechTree:")
    static native void showTechTree(String json);

    @Method(selector = "showToast:duration:")
    static native void showToast(String message, double durationSeconds);

    @Method(selector = "showModal:")
    static native void showModal(String json);

    /** Replaces the content of the card on screen without presenting it anew */
    @Method(selector = "updateModal:")
    static native void updateModal(String json);

    @Method(selector = "hideModal")
    static native void hideModal();

    @Method(selector = "showMinimap:")
    static native void showMinimap(String json);

    @Method(selector = "setMinimapCameraX:y:width:height:")
    static native void setMinimapCamera(double x, double y, double width, double height);

    /** A whole native screen; {@code kind} selects the view, see {@code NativeScreenPresenter} */
    @Method(selector = "showMapEditor:")
    static native void showMapEditor(String json);

    @Method(selector = "showScreen:json:")
    static native void showScreen(String kind, String json);

    @Method(selector = "showSheet:json:")
    static native void showSheet(String kind, String json);

    // iCloud saves: these block and may be called from any thread except where noted

    @Method(selector = "gameCenterAuthenticate")
    static native void gameCenterAuthenticate();

    @Method(selector = "gameCenterSubmitScore:leaderboard:")
    static native void gameCenterSubmitScore(long score, String leaderboard);

    @Method(selector = "gameCenterUnlock:")
    static native void gameCenterUnlock(String achievement);

    @Method(selector = "gameCenterShowDashboard")
    static native void gameCenterShowDashboard();

    @Method(selector = "cloudSavesAvailable")
    static native boolean cloudSavesAvailable();

    @Method(selector = "cloudSavesConfigured")
    static native boolean cloudSavesConfigured();

    @Method(selector = "cloudUpload:name:")
    static native void cloudUpload(String path, String name);

    @Method(selector = "cloudDelete:")
    static native void cloudDelete(String name);

    @Method(selector = "cloudSyncInto:")
    static native int cloudSyncInto(String folder);

    @Method(selector = "hideSheet")
    static native void hideSheet();

    /** Covers the screen while the game switches screens; {@code loading} adds the logo and a spinner */
    @Method(selector = "showCurtainLoading:")
    static native void showCurtain(boolean loading);

    @Method(selector = "hideCurtain")
    static native void hideCurtain();

    /** Drops cached translations after the language changed */
    @Method(selector = "resetStrings")
    static native void resetStrings();

    /** Local-only automation, see {@code IOSNativeUi.runScript} */
    @Method(selector = "debugCommand:")
    static native void debugCommand(String command);

    @Method(selector = "hideAll")
    static native void hideAll();
}
