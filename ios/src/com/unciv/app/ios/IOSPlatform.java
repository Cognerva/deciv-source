package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.unciv.logic.files.PlatformSaverLoader;
import com.unciv.logic.PlatformHttp;
import com.unciv.logic.files.UncivFiles;
import com.unciv.ui.components.fonts.Fonts;
import com.unciv.utils.Display;
import com.unciv.utils.Log;
import com.unciv.utils.PurchaseResult;
import com.unciv.utils.SafeAreaInsetSide;
import com.unciv.utils.StoreProduct;
import java.util.List;
import java.util.Set;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIInterfaceOrientation;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.apple.uikit.UIWindow;
import org.robovm.apple.uikit.UIWindowScene;

/** Centralized setup for the iOS platform adapters. */
public final class IOSPlatform {
    private static IOSStore store;
    private static UIInterfaceOrientation lastLoggedOrientation;
    private static SafeAreaInsetSide cachedSafeAreaInsetSide = SafeAreaInsetSide.Both;
    private static boolean safeAreaInsetSideInitialized;

    private IOSPlatform() {}

    public static void install() {
        lastLoggedOrientation = null;
        cachedSafeAreaInsetSide = SafeAreaInsetSide.Both;
        safeAreaInsetSideInitialized = false;
        PlatformHttp.INSTANCE.setEngineFactory(IOSHttpClientEngine.Factory);
        Log.INSTANCE.setBackend(new IOSLogBackend());
        Display.INSTANCE.setPlatform(new IOSDisplay());
        Fonts.INSTANCE.setFontImplementation(new IOSFont());
        UncivFiles.Companion.setPreferExternalStorage(false);
        UncivFiles.Companion.setSaverLoader(new IOSSaverLoader());
        store = new IOSStore();
    }

    static boolean isStoreAvailable() {
        return store != null && store.isAvailable();
    }

    static void fetchStoreProducts(Set<String> productIds, Function1<? super List<StoreProduct>, Unit> callback) {
        if (store == null) callback.invoke(java.util.Collections.<StoreProduct>emptyList());
        else store.fetchProducts(productIds, callback);
    }

    static void purchaseStoreProduct(String productId, Function1<? super PurchaseResult, Unit> callback) {
        if (store == null) callback.invoke(PurchaseResult.Unavailable);
        else store.purchase(productId, callback);
    }

    static void restoreStorePurchases(Function1<? super Set<String>, Unit> callback) {
        if (store == null) callback.invoke(java.util.Collections.<String>emptySet());
        else store.restore(callback);
    }

    static boolean setAlternateAppIcon(String iconName, Function1<? super Boolean, Unit> callback) {
        return store != null && store.setAlternateIcon(iconName, callback);
    }

    static String getAlternateAppIconName() {
        return store == null ? null : store.getAlternateIconName();
    }

    static String getDeviceDescription() {
        return store == null ? null : store.getDeviceDescription();
    }

    static SafeAreaInsetSide getSafeAreaInsetSide() {
        if (!safeAreaInsetSideInitialized) refreshSafeAreaInsetSide();
        return cachedSafeAreaInsetSide;
    }

    /** Refreshes the cached physical housing edge once UIKit reports a new orientation. */
    static void refreshSafeAreaInsetSide() {
        UIInterfaceOrientation orientation = getInterfaceOrientation();
        if (orientation == null) {
            if (!safeAreaInsetSideInitialized) safeAreaInsetSideInitialized = true;
            return;
        }

        if (orientation != lastLoggedOrientation) {
            lastLoggedOrientation = orientation;
            safeAreaInsetSideInitialized = true;
            int left = Gdx.graphics == null ? 0 : Gdx.graphics.getSafeInsetLeft();
            int right = Gdx.graphics == null ? 0 : Gdx.graphics.getSafeInsetRight();
            int bottom = Gdx.graphics == null ? 0 : Gdx.graphics.getSafeInsetBottom();
            if (Gdx.app != null)
                Gdx.app.log("DeCivSafeArea", "orientation=" + orientation +
                    " reportedInsets=" + left + "," + right + "," + bottom);

            // UIInterfaceOrientationLandscapeLeft == UIDeviceOrientationLandscapeRight (see
            // UIApplication.h): the home button is on the left, so the sensor housing is on the
            // RIGHT edge of the screen. Do not simplify this to match the enum names.
            if (orientation == UIInterfaceOrientation.LandscapeLeft)
                cachedSafeAreaInsetSide = SafeAreaInsetSide.Right;
            else if (orientation == UIInterfaceOrientation.LandscapeRight)
                cachedSafeAreaInsetSide = SafeAreaInsetSide.Left;
            else
                cachedSafeAreaInsetSide = SafeAreaInsetSide.Both;
        }
    }

    static String getLastAlternateAppIconError() {
        return store == null ? null : store.getLastAlternateIconError();
    }

    private static UIInterfaceOrientation getInterfaceOrientation() {
        UIApplication application = UIApplication.getSharedApplication();
        UIWindow window = application.getKeyWindow();
        if (window != null) {
            UIWindowScene scene = window.getWindowScene();
            if (scene != null && scene.getInterfaceOrientation() != UIInterfaceOrientation.Unknown)
                return scene.getInterfaceOrientation();
            UIViewController root = window.getRootViewController();
            if (root != null && root.getInterfaceOrientation() != UIInterfaceOrientation.Unknown)
                return root.getInterfaceOrientation();
        }
        UIInterfaceOrientation statusBarOrientation = application.getStatusBarOrientation();
        return statusBarOrientation == UIInterfaceOrientation.Unknown ? null : statusBarOrientation;
    }
}
