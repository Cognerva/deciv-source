package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.unciv.Constants;
import com.unciv.UncivGame;
import com.unciv.logic.GameInfo;
import com.unciv.logic.GameStarter;
import com.unciv.logic.map.MapSize;
import com.unciv.models.metadata.GameSetupInfo;
import com.unciv.models.ruleset.Ruleset;
import com.unciv.models.ruleset.RulesetCache;
import com.unciv.ui.screens.basescreen.BaseScreen;
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen;
import com.unciv.ui.screens.LanguagePickerScreen;
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen;
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen;
import com.unciv.ui.screens.modmanager.ModManagementScreen;
import com.unciv.ui.screens.newgamescreen.NewGameScreen;
import com.unciv.ui.screens.worldscreen.WorldScreen;
import com.unciv.ui.screens.worldscreen.unit.AutoPlay;
import com.unciv.ui.popups.options.OptionsPopupPages;
import com.unciv.utils.PurchaseResult;
import com.unciv.utils.SafeAreaInsetSide;
import com.unciv.utils.StoreProduct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.Unit;

/** Shared Unciv game with the small amount of iOS-specific lifecycle behavior needed by the core. */
public final class IOSGame extends UncivGame {

    private boolean screenshotSceneStarted;

    public IOSGame() {
        super(false);
    }

    @Override
    public void create() {
        super.create();
        if (IOSLauncher.getScreenshotScene() != null) waitForScreenshotScene();
    }

    /**
     * A local-only launch harness for reproducible App Store screenshots. It uses the existing
     * game screens and game-start path; normal launches never enter this branch.
     */
    private void waitForScreenshotScene() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (screenshotSceneStarted) return;
                if (getScreen() instanceof LanguagePickerScreen) {
                    // Make a fresh simulator install usable without a manual tap. This is only
                    // reached when the local screenshot harness is explicitly requested.
                    getSettings().setLanguage(Constants.english);
                    getSettings().updateLocaleFromLanguage();
                    getSettings().setFreshlyCreated(false);
                    getSettings().save();
                    getTranslations().tryReadTranslationForCurrentLanguage();
                    replaceCurrentScreen(new MainMenuScreen());
                    waitForScreenshotScene();
                    return;
                }
                if (!(getScreen() instanceof MainMenuScreen)) {
                    waitForScreenshotScene();
                    return;
                }

                screenshotSceneStarted = true;
                String scene = IOSLauncher.getScreenshotScene();
                BaseScreen mainMenu = getScreen();
                if ("main-menu".equals(scene)) return;
                if ("new-game".equals(scene)) {
                    pushScreen(new NewGameScreen());
                } else if ("new-game-start".equals(scene)) {
                    pushScreen(new NewGameScreen());
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (getScreen() instanceof NewGameScreen)
                                ((NewGameScreen) getScreen()).startGameForScreenshotHarness();
                        }
                    });
                } else if ("civilopedia".equals(scene)) {
                    mainMenu.openCivilopedia("");
                } else if ("map-editor".equals(scene)) {
                    pushScreen(new MapEditorScreen());
                } else if ("mods".equals(scene)) {
                    pushScreen(new ModManagementScreen());
                } else if ("options-display".equals(scene)) {
                    mainMenu.openOptionsPopup(
                        OptionsPopupPages.Display,
                        false,
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                return Unit.INSTANCE;
                            }
                        }
                    );
                } else if ("world".equals(scene)) {
                    startScreenshotGame();
                } else {
                    // Keep an unknown scene useful during iteration instead of crashing the app.
                    screenshotSceneStarted = false;
                }
            }
        });
    }

    private void startScreenshotGame() {
        Thread starter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[Screenshot] loading Autosave");
                    if (getFiles().getSave("Autosave").exists()) {
                        GameInfo savedGame = getFiles().loadGameByName("Autosave");
                        if (savedGame != null) {
                            loadGameForScreenshot(savedGame);
                            return;
                        }
                    }

                    System.out.println("[Screenshot] Autosave unavailable; generating minimal fixture");
                    GameSetupInfo setup = new GameSetupInfo();
                    // Do not inherit a user's saved game or map settings into marketing captures.
                    setup.getGameParameters().setDifficulty("Chieftain");
                    setup.getGameParameters().setMinNumberOfPlayers(1);
                    setup.getGameParameters().setMaxNumberOfPlayers(1);
                    setup.getGameParameters().getPlayers().subList(
                        1, setup.getGameParameters().getPlayers().size()
                    ).clear();
                    setup.getGameParameters().setMinNumberOfCityStates(0);
                    setup.getGameParameters().setMaxNumberOfCityStates(0);
                    setup.getGameParameters().setNumberOfCityStates(0);
                    setup.getMapParameters().setMapSize(MapSize.Companion.getTiny());
                    setup.getMapParameters().setShape("Rectangular");
                    setup.getMapParameters().setWorldWrap(false);
                    setup.getMapParameters().setNoNaturalWonders(true);
                    setup.getMapParameters().setSeed(20260823L);
                    if (setup.getGameParameters().getVictoryTypes().isEmpty()) {
                        Ruleset ruleset = RulesetCache.INSTANCE.getComplexRuleset(setup.getGameParameters());
                        setup.getGameParameters().getVictoryTypes().addAll(
                            new ArrayList<String>(ruleset.getVictories().keySet())
                        );
                    }
                    GameInfo gameInfo = GameStarter.Companion.startNewGame(setup);
                    loadGameForScreenshot(gameInfo);
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        }, "Unciv iOS screenshot game");
        starter.setDaemon(true);
        starter.start();
    }

    private void loadGameForScreenshot(GameInfo gameInfo) {
        System.out.println("[Screenshot] calling loadGame");
        Object result = loadGame(
            gameInfo,
            new AutoPlay(getSettings().getAutoPlay()),
            false,
            new Continuation<WorldScreen>() {
                @Override
                public CoroutineContext getContext() {
                    return EmptyCoroutineContext.INSTANCE;
                }

                @Override
                public void resumeWith(Object result) {
                    // The game has installed WorldScreen before this continuation resumes.
                    if (result instanceof Throwable) ((Throwable) result).printStackTrace();
                }
            }
        );
        System.out.println("[Screenshot] loadGame returned " + result);
    }

    @Override
    public int getGcCount() {
        // java.lang.management is not available in the iOS runtime.
        return 0;
    }

    @Override
    public Locale getDefaultLocale() {
        return Locale.getDefault();
    }

    @Override
    public boolean isStoreAvailable() {
        return IOSPlatform.isStoreAvailable();
    }

    @Override
    public void fetchStoreProducts(Set<String> productIds, Function1<? super List<StoreProduct>, Unit> onResult) {
        IOSPlatform.fetchStoreProducts(productIds, onResult);
    }

    @Override
    public void purchaseStoreProduct(String productId, Function1<? super PurchaseResult, Unit> onResult) {
        IOSPlatform.purchaseStoreProduct(productId, onResult);
    }

    @Override
    public void restoreStorePurchases(Function1<? super Set<String>, Unit> onResult) {
        IOSPlatform.restoreStorePurchases(onResult);
    }

    @Override
    public void setAlternateAppIcon(String iconName, Function1<? super Boolean, Unit> onResult) {
        if (!IOSPlatform.setAlternateAppIcon(iconName, onResult)) onResult.invoke(false);
    }

    @Override
    public String getAlternateAppIconName() {
        return IOSPlatform.getAlternateAppIconName();
    }

    @Override
    public String getLastAlternateAppIconError() {
        return IOSPlatform.getLastAlternateAppIconError();
    }

    @Override
    public boolean supportsSafeAreaInsets() {
        return true;
    }

    @Override
    public SafeAreaInsetSide getSafeAreaInsetSide() {
        return IOSPlatform.getSafeAreaInsetSide();
    }

    @Override
    public void resize(int width, int height) {
        // UIKit updates its scene orientation and safe-area values with the resize callback.
        // Refresh once here so render() can use the cached housing edge.
        IOSPlatform.refreshSafeAreaInsetSide();
        super.resize(width, height);
    }

    @Override
    public String getDeviceDescription() {
        return IOSPlatform.getDeviceDescription();
    }
}
