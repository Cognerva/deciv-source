package com.unciv.app.ios;

import com.unciv.utils.LogBackend;
import com.unciv.utils.Tag;

/** Small stdout/stderr logger that keeps the shared crash and diagnostics code usable on iOS. */
public final class IOSLogBackend implements LogBackend {

    @Override
    public void debug(Tag tag, String thread, String message) {
        System.out.println("[" + thread + "] [" + tag.getName() + "] " + message);
    }

    @Override
    public void error(Tag tag, String thread, String message) {
        System.err.println("[" + thread + "] [" + tag.getName() + "] [ERROR] " + message);
    }

    @Override
    public boolean isRelease() {
        return false;
    }

    @Override
    public String getSystemInfo() {
        return "iOS / RoboVM";
    }
}
