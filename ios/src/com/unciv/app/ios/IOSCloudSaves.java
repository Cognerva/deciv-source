package com.unciv.app.ios;

import com.unciv.logic.files.CloudSaves;

/** iCloud Drive storage for {@link CloudSaves}, implemented in Swift ({@code CloudSaveStore}). */
final class IOSCloudSaves implements CloudSaves.Platform {
    @Override
    public boolean isAvailable() {
        return NativeUIBridge.cloudSavesAvailable();
    }

    @Override
    public boolean isConfigured() {
        return NativeUIBridge.cloudSavesConfigured();
    }

    @Override
    public void upload(String localPath, String name) {
        NativeUIBridge.cloudUpload(localPath, name);
    }

    @Override
    public void delete(String name) {
        NativeUIBridge.cloudDelete(name);
    }

    @Override
    public int syncInto(String localDir) {
        return NativeUIBridge.cloudSyncInto(localDir);
    }
}
