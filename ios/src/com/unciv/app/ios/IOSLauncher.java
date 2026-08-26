package com.unciv.app.ios;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;

/** iOS entry point. The game and all screens remain in the shared Kotlin core module. */
public final class IOSLauncher extends IOSApplication.Delegate {

    private static String screenshotScene;
    private static boolean screenshotLandscape;

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
        return new IOSApplication(new IOSGame(), configuration);
    }

    public static void main(String[] argv) {
        readScreenshotArguments(argv);
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(argv, null, IOSLauncher.class);
        pool.close();
    }
}
