package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import org.robovm.apple.uikit.UIScene;
import org.robovm.apple.uikit.UISceneConnectionOptions;
import org.robovm.apple.uikit.UISceneSession;
import org.robovm.apple.uikit.UIWindow;
import org.robovm.apple.uikit.UIWindowScene;
import org.robovm.apple.uikit.UIWindowSceneDelegateAdapter;
import org.robovm.objc.annotation.CustomClass;

/**
 * UIScene lifecycle adoption. Apps linked against the iOS 27 SDK are terminated at launch
 * without a scene delegate. libGDX still creates its window in
 * {@code application:didFinishLaunchingWithOptions:}, which runs first; this attaches that
 * window to the scene. Named in Info.plist under {@code UISceneConfigurations}.
 */
@CustomClass("DecivSceneDelegate")
public final class IOSSceneDelegate extends UIWindowSceneDelegateAdapter {
    private UIWindow window;

    @Override
    public void willConnect(UIScene scene, UISceneSession session, UISceneConnectionOptions connectionOptions) {
        if (!(scene instanceof UIWindowScene) || !(Gdx.app instanceof IOSApplication)) return;
        window = ((IOSApplication) Gdx.app).getUIWindow();
        if (window == null) return;
        window.setWindowScene((UIWindowScene) scene);
        window.makeKeyAndVisible();
    }

    @Override
    public UIWindow getWindow() {
        return window;
    }

    @Override
    public void setWindow(UIWindow window) {
        this.window = window;
    }
}
