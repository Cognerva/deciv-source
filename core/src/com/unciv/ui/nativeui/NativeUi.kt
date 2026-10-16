package com.unciv.ui.nativeui

import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Platform-native chrome drawn over the libGDX surface.
 *
 * When a platform provides one, the matching Scene2D widgets are hidden and the platform reads
 * plain snapshots ([com.unciv.ui.screens.mainmenuscreen.MainMenuScreen.nativeMenuJson],
 * [WorldHudPresenter.snapshotJson]) and sends back string action ids. The map itself, and
 * every screen that is not replaced, keeps rendering through Scene2D.
 *
 * All calls happen on the GL thread.
 */
interface NativeUi {
    /** Whether the main menu buttons are drawn natively. The Scene2D background map still renders. */
    fun replacesMainMenu(): Boolean

    /** Whether the world screen's top bar, status buttons, tech/policy buttons and unit actions are drawn natively. */
    fun replacesWorldHud(): Boolean

    /** Called at the end of every [WorldScreen] update while [replacesWorldHud] is true. */
    fun worldScreenUpdated(worldScreen: WorldScreen) {}

    /** Whether the city screen's tables are drawn natively. Only its tile map stays in Scene2D. */
    fun replacesCityScreen(): Boolean = false

    /** Called at the end of every [CityScreen] update while [replacesCityScreen] is true. */
    fun cityScreenUpdated(cityScreen: CityScreen) {}

    /**
     * Shows [card] as a native modal and calls [onClose] on the GL thread once it is dismissed.
     * @return false when there is no native implementation; the caller then shows its Scene2D popup
     */
    fun showStoryCard(card: NativeStoryCard, onClose: () -> Unit): Boolean = false

    /** Whether a native modal is showing or queued; screens hold back new popups meanwhile. */
    fun isModalShowing(): Boolean = false

    /**
     * Shows a Scene2D popup's content as a native card, queued behind other cards.
     * [onButton] runs on the GL thread with the index of the tapped button, [onInput] with the index
     * of an edited input and its new value.
     * @param owner identifies the card for [updatePopup] and [dismissPopup], normally the popup
     * @return false to keep the Scene2D popup
     */
    fun showPopup(owner: Any, card: NativeStoryCard, onButton: (Int) -> Unit, onInput: (Int, String) -> Unit = { _, _ -> }): Boolean = false

    /** Replaces the content of [owner]'s card, e.g. after [com.unciv.ui.popups.Popup.reuseWith] */
    fun updatePopup(owner: Any, card: NativeStoryCard) {}

    /** Removes [owner]'s card, whether showing or still queued */
    fun dismissPopup(owner: Any) {}

    /** Shows a short, self-dismissing message. @return false to fall back to a Scene2D toast */
    fun showToast(message: String, durationMillis: Long): Boolean = false

    /**
     * Called just before the game switches to [newScreen], ahead of its first frame. The platform
     * covers the switch so neither the old native chrome nor the bare new screen flashes.
     */
    fun screenChanging(newScreen: com.unciv.ui.screens.basescreen.BaseScreen) {}

    /** Whether screens implementing [NativeScreenHost] are drawn entirely natively. */
    fun replacesScreens(): Boolean = false

    /**
     * Shows [presenter] as a native sheet above the current screen, e.g. the options.
     * [onClose] runs on the GL thread once the sheet is dismissed.
     * @return false when there is no native implementation; the caller then opens its Scene2D popup
     */
    fun showSheet(presenter: NativeScreenPresenter, onClose: () -> Unit = {}): Boolean = false

    /** Dismisses the sheet shown by [showSheet], if any. */
    fun closeSheet() {}

    /** The sheet shown by [showSheet], if any. */
    fun currentSheet(): NativeScreenPresenter? = null

    /**
     * Tells the platform that [presenter]'s state changed outside of [NativeScreenPresenter.perform],
     * e.g. when a background job finished, so it re-reads the snapshot.
     */
    fun refresh(presenter: NativeScreenPresenter) {}
}

/**
 * A whole screen, or a sheet, whose UI is drawn natively from JSON snapshots.
 * The platform picks its view by [kind] and sends back action ids to [perform].
 */
interface NativeScreenPresenter {
    /** Selects the native view: `form`, `saves` or `languages` */
    val kind: String
    fun snapshotJson(): String
    fun perform(id: String)
}

/** A Scene2D screen that is replaced by a native one while [NativeUi.replacesScreens]. */
interface NativeScreenHost {
    val nativeScreen: NativeScreenPresenter

    /** False while the screen shows Scene2D content the native view doesn't cover yet, e.g. a trade */
    val nativeActive: Boolean get() = true
}
