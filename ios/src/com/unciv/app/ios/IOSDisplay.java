package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.unciv.models.metadata.GameSettings;
import com.unciv.utils.PlatformDisplay;
import com.unciv.utils.ScreenOrientation;

/** iOS is full-screen landscape for the first port; UIKit owns orientation changes. */
public final class IOSDisplay implements PlatformDisplay {

    @Override
    public boolean hasOrientation() {
        return true;
    }

    @Override
    public void setOrientation(ScreenOrientation orientation) {
        // Orientation is fixed by IOSApplicationConfiguration and Info.plist.
    }

    @Override
    public boolean hasCutout() {
        return Gdx.graphics != null && (
            Gdx.graphics.getSafeInsetLeft() > 0 ||
            Gdx.graphics.getSafeInsetTop() > 0 ||
            Gdx.graphics.getSafeInsetBottom() > 0 ||
            Gdx.graphics.getSafeInsetRight() > 0
        );
    }

    @Override
    public boolean hasSystemUiVisibility() {
        return true;
    }

    @Override
    public void setSystemUiVisibility(boolean hide) {
        // The RoboVM backend runs full-screen and hides the status bar.
    }

    @Override
    public void setCutout(boolean enabled) {
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    @Override
    public void setScreenMode(int id, GameSettings settings) {
        // There are no user-selectable window modes on iOS.
    }
}
