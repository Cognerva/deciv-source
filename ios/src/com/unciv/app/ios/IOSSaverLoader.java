package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.unciv.logic.files.PlatformSaverLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSURL;
import org.robovm.apple.foundation.NSStringEncoding;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIDocumentPickerDelegate;
import org.robovm.apple.uikit.UIDocumentPickerMode;
import org.robovm.apple.uikit.UIDocumentPickerViewController;
import org.robovm.apple.uikit.UIViewController;

/** Bridges Unciv's existing custom save/load flow to the iOS Files picker. */
public final class IOSSaverLoader implements PlatformSaverLoader {
    @Override
    public void saveGame(
        String data,
        String suggestedLocation,
        Function1<? super String, Unit> onSaved,
        Function1<? super Exception, Unit> onError
    ) {
        try {
            String fileName = fileNameFor(suggestedLocation);
            File temporaryFile = new File(Gdx.files.getLocalStoragePath(), fileName);
            File parent = temporaryFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new java.io.IOException("Could not create the temporary save directory");

            try (FileOutputStream stream = new FileOutputStream(temporaryFile)) {
                stream.write(data.getBytes(Charset.forName("UTF-8")));
            }

            UIDocumentPickerViewController picker = new UIDocumentPickerViewController(
                new NSURL(temporaryFile), UIDocumentPickerMode.ExportToService
            );
            picker.setShouldShowFileExtensions(true);
            present(picker, new PickerCallbacks() {
                @Override
                public void picked(NSURL url) {
                    onSaved.invoke(url.getAbsoluteString());
                }

                @Override
                public void cancelled() {
                    onError.invoke(new PlatformSaverLoader.Cancelled());
                }

                @Override
                public void failed(Exception exception) {
                    onError.invoke(exception);
                }
            });
        } catch (Exception exception) {
            onError.invoke(exception);
        }
    }

    @Override
    public void loadGame(
        Function2<? super String, ? super String, Unit> onLoaded,
        Function1<? super Exception, Unit> onError
    ) {
        UIDocumentPickerViewController picker = new UIDocumentPickerViewController(
            Arrays.asList("public.data"), UIDocumentPickerMode.Import
        );
        picker.setAllowsMultipleSelection(false);
        picker.setShouldShowFileExtensions(true);
        present(picker, new PickerCallbacks() {
            @Override
            public void picked(NSURL url) {
                boolean accessed = url.startAccessingSecurityScopedResource();
                try {
                    NSData data = NSData.read(url);
                    onLoaded.invoke(
                        new String(data.getBytes(), Charset.forName("UTF-8")),
                        url.getAbsoluteString()
                    );
                } catch (Exception exception) {
                    onError.invoke(exception);
                } finally {
                    if (accessed) url.stopAccessingSecurityScopedResource();
                }
            }

            @Override
            public void cancelled() {
                onError.invoke(new PlatformSaverLoader.Cancelled());
            }

            @Override
            public void failed(Exception exception) {
                onError.invoke(exception);
            }
        });
    }

    private static void present(UIDocumentPickerViewController picker, PickerCallbacks callbacks) {
        Gdx.app.postRunnable(() -> {
            UIViewController presenter = UIApplication.getSharedApplication().getKeyWindow().getRootViewController();
            if (presenter == null) {
                callbacks.failed(new IllegalStateException("The iOS view controller is not available"));
                return;
            }
            while (presenter.getPresentedViewController() != null)
                presenter = presenter.getPresentedViewController();
            picker.setDelegate(new IOSPickerDelegate(callbacks));
            presenter.presentViewController(picker, true, null);
        });
    }

    private static String fileNameFor(String suggestedLocation) {
        String name = suggestedLocation == null ? "DeCiv-save" : suggestedLocation;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(File.separatorChar));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.startsWith("file:")) name = "DeCiv-save";
        if (name.isEmpty()) name = "DeCiv-save";
        if (!name.endsWith(".json")) name += ".json";
        return name;
    }

    private interface PickerCallbacks {
        void picked(NSURL url);
        void cancelled();
        void failed(Exception exception);
    }

    private static final class IOSPickerDelegate implements UIDocumentPickerDelegate {
        private final PickerCallbacks callbacks;

        IOSPickerDelegate(PickerCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void didPickDocuments(UIDocumentPickerViewController controller, org.robovm.apple.foundation.NSArray<NSURL> urls) {
            if (urls == null || urls.isEmpty()) callbacks.cancelled();
            else callbacks.picked(urls.get(0));
        }

        @Override
        public void didPickDocument(UIDocumentPickerViewController controller, NSURL url) {
            callbacks.picked(url);
        }

        @Override
        public void wasCancelled(UIDocumentPickerViewController controller) {
            callbacks.cancelled();
        }
    }
}
