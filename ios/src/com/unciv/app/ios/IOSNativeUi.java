package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import org.robovm.apple.foundation.NSThread;
import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.unciv.UncivGame;
import com.unciv.models.translations.TranslationsKt;
import com.unciv.ui.nativeui.MinimapPresenter;
import com.unciv.ui.nativeui.NativeScreenHost;
import com.unciv.logic.files.CloudSaves;
import com.unciv.ui.nativeui.GameCenterProgress;
import com.unciv.ui.nativeui.MapEditorPresenter;
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen;
import com.unciv.ui.screens.LoadingScreen;
import com.unciv.ui.screens.GameStartScreen;
import com.unciv.ui.nativeui.NativeScriptScreens;
import com.unciv.ui.nativeui.NativeScreenPresenter;
import com.unciv.ui.nativeui.NativeStoryCard;
import com.unciv.ui.nativeui.NativeUi;
import com.unciv.ui.popups.PopupKt;
import com.unciv.ui.screens.basescreen.BaseScreen;
import com.unciv.ui.screens.cityscreen.CityScreen;
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen;
import com.unciv.ui.screens.pickerscreens.TechPickerScreen;
import com.unciv.ui.screens.worldscreen.WorldScreen;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/**
 * Native SwiftUI chrome for the main menu and world screen.
 *
 * The Kotlin core builds JSON snapshots and handles action ids; this class only decides which
 * overlay is visible and moves data between the core and {@link NativeUIBridge}. Both sides run
 * on the main thread, which is libGDX's render thread on iOS.
 */
final class IOSNativeUi implements NativeUi {

    private enum Mode { HIDDEN, MAIN_MENU, WORLD, CITY, TECH_TREE, SCREEN, MAP_EDITOR }

    private final boolean enabled;
    private boolean installed;
    /** Kept here as well as in Swift so the Java peer outlives every callback */
    private ActionTarget actionTarget;
    private Mode mode = Mode.HIDDEN;
    private BaseScreen shownScreen;
    /** Native sheet above the current screen, e.g. the options */
    private NativeScreenPresenter sheet;
    private Function0<Unit> sheetClose;
    /** Language the native strings were translated for */
    private String language;

    IOSNativeUi(boolean enabled) {
        this.enabled = enabled;
    }

    /** Adds the overlay above the libGDX view. Leaves the Scene2D UI in place if the framework cannot be loaded. */
    boolean install() {
        if (!enabled) return false;
        try {
            actionTarget = new ActionTarget(this);
            NativeUIBridge.install(((IOSApplication) Gdx.app).getUIViewController(), actionTarget);
            installed = true;
            CloudSaves.INSTANCE.setPlatform(new IOSCloudSaves());
            GameCenterProgress.INSTANCE.setPlatform(new IOSGameCenter());
        } catch (Throwable error) {
            System.err.println("[NativeUI] Falling back to the Scene2D UI: " + error);
            installed = false;
        }
        return installed;
    }

    @Override
    public boolean replacesMainMenu() {
        return installed;
    }

    @Override
    public boolean replacesWorldHud() {
        return installed;
    }

    /**
     * The overlay is UIKit and the modal queue is not synchronized: calls that arrive from a
     * background thread (e.g. a loading popup closed by its loader) move to the GL thread, which
     * is the main thread on iOS. @return true when the caller may go on here
     */
    private static boolean onGlThread(Runnable later) {
        if (NSThread.getCurrentThread().isMainThread()) return true;
        Gdx.app.postRunnable(later);
        Gdx.graphics.requestRendering();
        return false;
    }

    /** A native card waiting for or on screen: a story card (closed with Done) or a popup's content (buttons). */
    private static final class Modal {
        final Object owner;
        String json;
        final Function0<Unit> onClose;
        final Function1<? super Integer, Unit> onButton;
        final Function2<? super Integer, ? super String, Unit> onInput;

        Modal(Object owner, String json, Function0<Unit> onClose, Function1<? super Integer, Unit> onButton,
              Function2<? super Integer, ? super String, Unit> onInput) {
            this.owner = owner;
            this.json = json;
            this.onClose = onClose;
            this.onButton = onButton;
            this.onInput = onInput;
        }
    }

    /** The card on screen first, then the ones waiting */
    private final java.util.ArrayDeque<Modal> modals = new java.util.ArrayDeque<>();
    /** Set while a card's own action runs, so cards it opens (chained tutorials) go next instead of last */
    private boolean resolvingModal;

    private void enqueue(Modal modal) {
        boolean wasEmpty = modals.isEmpty();
        if (resolvingModal && !wasEmpty) {
            // Behind the card being resolved, which is removed right after
            Modal current = modals.pollFirst();
            modals.addFirst(modal);
            modals.addFirst(current);
        } else {
            modals.addLast(modal);
        }
        if (wasEmpty) NativeUIBridge.showModal(modal.json);
    }

    /** Removes [modal] and shows whatever comes next */
    private void finish(Modal modal) {
        boolean wasShowing = modals.peekFirst() == modal;
        modals.remove(modal);
        if (!wasShowing) return;
        Modal next = modals.peekFirst();
        if (next != null) NativeUIBridge.showModal(next.json);
        else NativeUIBridge.hideModal();
    }

    @Override
    public boolean showStoryCard(NativeStoryCard card, Function0<Unit> onClose) {
        if (!onGlThread(() -> showStoryCard(card, onClose))) return installed;
        if (!installed) return false;
        enqueue(new Modal(card, card.toJson(), onClose, null, null));
        return true;
    }

    @Override
    public boolean showPopup(Object owner, NativeStoryCard card, Function1<? super Integer, Unit> onButton,
                             Function2<? super Integer, ? super String, Unit> onInput) {
        if (!onGlThread(() -> showPopup(owner, card, onButton, onInput))) return installed;
        if (!installed) return false;
        enqueue(new Modal(owner, card.toJson(), null, onButton, onInput));
        return true;
    }

    @Override
    public void updatePopup(Object owner, NativeStoryCard card) {
        if (!onGlThread(() -> updatePopup(owner, card))) return;
        for (Modal modal : modals) {
            if (modal.owner != owner) continue;
            modal.json = card.toJson();
            // Same card with new content: keeps its identity, so it doesn't animate in again
            if (modals.peekFirst() == modal) NativeUIBridge.updateModal(modal.json);
        }
    }

    /** An edited text field or switch on the popup card on screen */
    private void setModalInput(int index, String value) {
        Modal modal = modals.peekFirst();
        if (modal == null || modal.onInput == null) return;
        modal.onInput.invoke(index, value);
    }

    @Override
    public void dismissPopup(Object owner) {
        if (!onGlThread(() -> dismissPopup(owner))) return;
        for (Modal modal : new java.util.ArrayList<>(modals))
            if (modal.owner == owner) finish(modal);
    }

    @Override
    public boolean isModalShowing() {
        return !modals.isEmpty();
    }

    /** Done on a story card */
    private void closeModal() {
        Modal modal = modals.peekFirst();
        if (modal == null || modal.onClose == null) return;
        resolvingModal = true;
        try {
            modal.onClose.invoke();
        } finally {
            resolvingModal = false;
        }
        finish(modal);
    }

    /** A button on a popup card; the popup closes itself through [dismissPopup] when the button closes it */
    private void pressModalButton(int index) {
        Modal modal = modals.peekFirst();
        if (modal == null || modal.onButton == null) return;
        resolvingModal = true;
        try {
            modal.onButton.invoke(index);
        } finally {
            resolvingModal = false;
        }
        // A confirmation usually changed what the screen below shows
        refreshScreen();
    }

    private void refreshScreen() {
        BaseScreen screen = UncivGame.Current.getScreen();
        if (mode == Mode.SCREEN && screen == shownScreen && screen instanceof NativeScreenHost)
            refresh(((NativeScreenHost) screen).getNativeScreen());
    }

    @Override
    public boolean showToast(String message, long durationMillis) {
        if (!onGlThread(() -> showToast(message, durationMillis))) return installed;
        if (!installed) return false;
        NativeUIBridge.showToast(message, durationMillis / 1000.0);
        return true;
    }

    @Override
    public boolean replacesCityScreen() {
        return installed;
    }

    /** Whether the switch curtain is up; it lifts once the new screen's overlay is in place */
    private boolean curtainShown;

    private static boolean isLoadingScreen(Object screen) {
        return screen instanceof LoadingScreen || screen instanceof GameStartScreen;
    }

    @Override
    public void screenChanging(BaseScreen newScreen) {
        if (!onGlThread(() -> screenChanging(newScreen))) return;
        if (!installed) return;
        curtainShown = true;
        NativeUIBridge.showCurtain(isLoadingScreen(newScreen));
    }

    @Override
    public boolean replacesScreens() {
        return installed;
    }

    @Override
    public boolean showSheet(NativeScreenPresenter presenter, Function0<Unit> onClose) {
        if (!onGlThread(() -> showSheet(presenter, onClose))) return installed;
        if (!installed) return false;
        sheet = presenter;
        sheetClose = onClose;
        NativeUIBridge.showSheet(presenter.getKind(), presenter.snapshotJson());
        return true;
    }

    @Override
    public void closeSheet() {
        if (!onGlThread(this::closeSheet)) return;
        if (sheet == null) return;
        Function0<Unit> close = sheetClose;
        sheet = null;
        sheetClose = null;
        NativeUIBridge.hideSheet();
        if (close != null) close.invoke();
    }

    @Override
    public NativeScreenPresenter currentSheet() {
        return sheet;
    }

    @Override
    public void refresh(NativeScreenPresenter presenter) {
        if (!onGlThread(() -> refresh(presenter))) return;
        if (!installed) return;
        if (presenter == sheet) {
            NativeUIBridge.showSheet(presenter.getKind(), presenter.snapshotJson());
        } else if (mode == Mode.SCREEN && shownScreen instanceof NativeScreenHost
                && ((NativeScreenHost) shownScreen).getNativeScreen() == presenter) {
            NativeUIBridge.showScreen(presenter.getKind(), presenter.snapshotJson());
        }
    }

    @Override
    public void cityScreenUpdated(CityScreen cityScreen) {
        if (mode == Mode.CITY && shownScreen == cityScreen)
            NativeUIBridge.showCityScreen(cityScreen.getNativePresenter().snapshotJson());
    }

    @Override
    public void worldScreenUpdated(WorldScreen worldScreen) {
        if (mode == Mode.WORLD && shownScreen == worldScreen) {
            NativeUIBridge.showWorldHud(worldScreen.getNativeHudPresenter().snapshotJson());
            sendMinimap(worldScreen.getNativeMinimap());
        }
    }

    private static void sendMinimap(MinimapPresenter minimap) {
        String tiles = minimap.tilesJsonIfChanged();
        if (tiles != null) NativeUIBridge.showMinimap(tiles);
        float[] camera = minimap.cameraIfChanged();
        if (camera != null) NativeUIBridge.setMinimapCamera(camera[0], camera[1], camera[2], camera[3]);
    }

    /**
     * Called after every frame. Shows the overlay that matches the current screen, and hides it
     * while a Scene2D popup is open so the popup is not covered.
     */
    void reconcile(UncivGame game) {
        if (!installed) return;
        String currentLanguage = game.getSettings().getLanguage();
        if (!currentLanguage.equals(language)) {
            language = currentLanguage;
            NativeUIBridge.resetStrings();
            mode = Mode.HIDDEN; // resend the current screen with the new strings
            shownScreen = null;
        }
        BaseScreen screen = game.getScreen();
        // A loading screen keeps the curtain with the logo; any other screen has drawn its first
        // frame by now and its overlay is sent below, so the curtain can lift
        if (curtainShown && !isLoadingScreen(screen)) {
            curtainShown = false;
            NativeUIBridge.hideCurtain();
        }
        Mode wanted = Mode.HIDDEN;
        if (screen != null && !PopupKt.hasOpenPopups(screen)) {
            if (screen instanceof MainMenuScreen) wanted = Mode.MAIN_MENU;
            else if (screen instanceof WorldScreen) wanted = Mode.WORLD;
            else if (screen instanceof CityScreen) wanted = Mode.CITY;
            else if (screen instanceof TechPickerScreen) wanted = Mode.TECH_TREE;
            else if (screen instanceof MapEditorScreen) wanted = Mode.MAP_EDITOR;
            else if (screen instanceof NativeScreenHost && ((NativeScreenHost) screen).getNativeActive()) wanted = Mode.SCREEN;
        }
        if (wanted == Mode.WORLD && wanted == mode && screen == shownScreen) {
            // The camera moves every frame while panning, without a world update
            float[] camera = ((WorldScreen) screen).getNativeMinimap().cameraIfChanged();
            if (camera != null) NativeUIBridge.setMinimapCamera(camera[0], camera[1], camera[2], camera[3]);
            return;
        }
        if (wanted == Mode.MAP_EDITOR && wanted == mode && screen == shownScreen) {
            // A map loaded or generated in the background changes the palette and title
            String editor = ((MapEditorScreen) screen).getNativePresenter().snapshotIfChanged();
            if (editor != null) NativeUIBridge.showMapEditor(editor);
            return;
        }
        if (wanted == mode && screen == shownScreen) return;

        mode = wanted;
        shownScreen = screen;
        switch (wanted) {
            case MAIN_MENU:
                NativeUIBridge.showMainMenu(((MainMenuScreen) screen).nativeMenuJson());
                break;
            case WORLD:
                WorldScreen worldScreen = (WorldScreen) screen;
                NativeUIBridge.showWorldHud(worldScreen.getNativeHudPresenter().snapshotJson());
                worldScreen.getNativeMinimap().invalidate();
                sendMinimap(worldScreen.getNativeMinimap());
                break;
            case SCREEN:
                NativeScreenPresenter presenter = ((NativeScreenHost) screen).getNativeScreen();
                NativeUIBridge.showScreen(presenter.getKind(), presenter.snapshotJson());
                break;
            case CITY:
                NativeUIBridge.showCityScreen(((CityScreen) screen).getNativePresenter().snapshotJson());
                break;
            case MAP_EDITOR:
                NativeUIBridge.showMapEditor(((MapEditorScreen) screen).getNativePresenter().snapshotJson());
                break;
            case TECH_TREE:
                NativeUIBridge.showTechTree(((TechPickerScreen) screen).getNativePresenter().snapshotJson());
                break;
            default:
                NativeUIBridge.hideAll();
        }
    }

    /** Runs a native action on the next frame, against whichever screen is current by then. */
    private void perform(final String id) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                BaseScreen screen = UncivGame.Current.getScreen();
                if (id.equals("modal:close")) {
                    closeModal();
                } else if (id.startsWith("modal:input:")) {
                    String rest = id.substring("modal:input:".length());
                    int colon = rest.indexOf(':');
                    if (colon > 0) setModalInput(Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1));
                } else if (id.startsWith("modal:button:")) {
                    pressModalButton(Integer.parseInt(id.substring("modal:button:".length())));
                } else if (id.startsWith("sheet:") && sheet != null) {
                    NativeScreenPresenter presenter = sheet;
                    presenter.perform(id.substring("sheet:".length()));
                    if (sheet == presenter) refresh(presenter);
                } else if (id.startsWith("screen:") && screen instanceof NativeScreenHost) {
                    NativeScreenPresenter presenter = ((NativeScreenHost) screen).getNativeScreen();
                    presenter.perform(id.substring("screen:".length()));
                    if (UncivGame.Current.getScreen() == screen) refresh(presenter);
                } else if (id.startsWith("menu:") && screen instanceof MainMenuScreen) {
                    ((MainMenuScreen) screen).performNativeMenuAction(id.substring("menu:".length()));
                } else if (id.startsWith("hud:") && screen instanceof WorldScreen) {
                    ((WorldScreen) screen).getNativeHudPresenter().perform(id.substring("hud:".length()));
                } else if (id.startsWith("city:") && screen instanceof CityScreen) {
                    ((CityScreen) screen).getNativePresenter().perform(id.substring("city:".length()));
                } else if (id.startsWith("editor:") && screen instanceof MapEditorScreen) {
                    MapEditorPresenter editor = ((MapEditorScreen) screen).getNativePresenter();
                    editor.perform(id.substring("editor:".length()));
                    if (UncivGame.Current.getScreen() == screen && mode == Mode.MAP_EDITOR)
                        NativeUIBridge.showMapEditor(editor.snapshotJson());
                } else if (id.startsWith("tech:") && screen instanceof TechPickerScreen) {
                    ((TechPickerScreen) screen).getNativePresenter().perform(id.substring("tech:".length()));
                    // The tech screen has no update cycle of its own; refresh unless it was closed
                    if (UncivGame.Current.getScreen() == screen && mode == Mode.TECH_TREE)
                        NativeUIBridge.showTechTree(((TechPickerScreen) screen).getNativePresenter().snapshotJson());
                }
            }
        });
        Gdx.graphics.requestRendering();
    }

    /**
     * Local-only automation for checking native screens in the simulator: steps separated by
     * {@code ;}, each {@code wait:<seconds>}, {@code do:<action id>} (as a native control would
     * send it), {@code open:<screen>} (see NativeScriptScreens) or {@code ui:<command>} (view-local state such as opening the in-game menu).
     */
    void runScript(final String script) {
        Thread runner = new Thread(new Runnable() {
            @Override
            public void run() {
                for (String step : script.split(";")) {
                    step = step.trim();
                    try {
                        if (step.startsWith("wait:")) {
                            Thread.sleep((long) (Double.parseDouble(step.substring(5)) * 1000));
                        } else if (step.startsWith("do:")) {
                            System.out.println("[UiScript] " + step);
                            perform(step.substring(3));
                        } else if (step.startsWith("open:")) {
                            System.out.println("[UiScript] " + step);
                            final String name = step.substring(5);
                            Gdx.app.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    NativeScriptScreens.INSTANCE.open(name);
                                }
                            });
                            Gdx.graphics.requestRendering();
                        } else if (step.startsWith("ui:")) {
                            System.out.println("[UiScript] " + step);
                            final String command = step.substring(3);
                            Gdx.app.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    NativeUIBridge.debugCommand(command);
                                }
                            });
                            Gdx.graphics.requestRendering();
                        }
                    } catch (Exception error) {
                        System.err.println("[UiScript] " + step + ": " + error);
                    }
                }
            }
        }, "UiScript");
        runner.setDaemon(true);
        runner.start();
    }

    /** Objective-C object the Swift overlay calls back into. */
    private static final class ActionTarget extends NSObject {
        private final IOSNativeUi owner;

        ActionTarget(IOSNativeUi owner) {
            this.owner = owner;
        }

        @Method(selector = "performNativeAction:")
        public void performNativeAction(String id) {
            owner.perform(id);
        }

        /** Swift asks for its own strings in the game's language; keys missing from the translations come back unchanged. */
        @Method(selector = "translate:")
        public String translate(String key) {
            return TranslationsKt.tr(key, true, false);
        }
    }
}
