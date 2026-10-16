package com.unciv.app.ios;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.objc.ObjCClass;

/** iOS entry point. The game and all screens remain in the shared Kotlin core module. */
public final class IOSLauncher extends IOSApplication.Delegate {

    private static String screenshotScene;
    private static boolean screenshotLandscape;
    /** {@code --legacy-ui} keeps the Scene2D main menu and world HUD, for side-by-side comparison */
    private static boolean legacyUi;
    /** {@code --ui-script "wait:2;do:menu:newGame;ui:openMenu"}: local-only native UI automation, see {@link IOSNativeUi#runScript} */
    private static String uiScript;

    public static String getUiScript() {
        return uiScript;
    }

    /** Returns the optional scene requested by the local App Store screenshot harness. */
    public static String getScreenshotScene() {
        return screenshotScene;
    }

    private static void readScreenshotArguments(String[] argv) {
        if (argv == null) return;
        for (int i = 0; i < argv.length; i++) {
            if ("--screenshot-scene".equals(argv[i]) && i + 1 < argv.length) {
                screenshotScene = argv[++i];
            } else if ("--screenshot-orientation".equals(argv[i]) && i + 1 < argv.length) {
                screenshotLandscape = "landscape".equalsIgnoreCase(argv[++i]);
            } else if ("--legacy-ui".equals(argv[i])) {
                legacyUi = true;
            } else if ("--ui-script".equals(argv[i]) && i + 1 < argv.length) {
                uiScript = argv[++i];
            }
        }
    }

    @Override
    protected IOSApplication createApplication() {
        IOSPlatform.install();
        IOSApplicationConfiguration configuration = new IOSApplicationConfiguration();
        configuration.orientationLandscape = screenshotScene == null || screenshotLandscape;
        configuration.orientationPortrait = screenshotScene == null || !screenshotLandscape;
        configuration.useAccelerometer = false;
        configuration.useCompass = false;
        configuration.hideHomeIndicator = true;
        return new IOSApplication(new IOSGame(!legacyUi), configuration);
    }

    public static void main(String[] argv) {
        readScreenshotArguments(argv);
        // Register the scene delegate's Objective-C class before UIKit looks it up by name.
        ObjCClass.getByType(IOSSceneDelegate.class);
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(argv, null, IOSLauncher.class);
        pool.close();
    }
}
