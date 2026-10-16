package com.unciv.ui.nativeui

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.logic.files.CloudSaves
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.options.AppIconTab
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.support.SupportContact
import com.unciv.ui.support.SupportPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.withGLContext

/**
 * Native options, shown as a sheet over the screen that opened them. It covers the settings that
 * matter on iOS; keyboard bindings, desktop window modes, the Unciv multiplayer server and the
 * debug page stay with the Scene2D popup.
 */
class OptionsPresenter : NativeFormPresenter() {
    private val game get() = UncivGame.Current
    private val settings get() = game.settings
    private var busy: String? = null

    override fun NativeForm.build() {
        title = "Options"
        onClose { close() }
        busy = this@OptionsPresenter.busy

        gameplay()
        display()
        appIcon()
        visualHints()
        automation()
        sound()
        language()
        advanced()
        about()
    }

    private fun close() {
        settings.save()
        game.musicController.onChange(null)
        game.getNativeUi()?.closeSheet()
    }

    /** Applies a setting the world screen should pick up on its next update */
    private fun updateWorld(change: () -> Unit) {
        change()
        (game.screen as? WorldScreen)?.shouldUpdate = true
    }

    /** Rebuilds the screen below for settings that change the skin, art or language */
    private fun reloadScreens() {
        busy = Constants.loading
        Concurrency.run("Reload from options") {
            withGLContext { BaseScreen.setSkin() }
            val screen = game.screen
            if (screen is WorldScreen) game.reloadWorldscreen()
            else if (screen is MainMenuScreen) withGLContext { game.replaceCurrentScreen(MainMenuScreen()) }
            withGLContext {
                busy = null
                game.getNativeUi()?.refresh(this@OptionsPresenter)
            }
        }
    }

    private fun NativeForm.gameplay() = section("Gameplay") {
        if (CloudSaves.configured)
            toggle("iCloudSaves", "Sync saves with iCloud", settings.iCloudSaves,
                detail = if (CloudSaves.available || !settings.iCloudSaves) "Your saved games and the latest autosave are kept in iCloud Drive, so you can continue on another device."
                    else "iCloud is not available. Sign in to iCloud in the Settings app.") {
                settings.iCloudSaves = it
                if (it) CloudSaves.sync { game.getNativeUi()?.refresh(this@OptionsPresenter) }
            }
        toggle("checkForDueUnits", "Check for idle units", settings.checkForDueUnits) { updateWorld { settings.checkForDueUnits = it } }
        toggle("checkForDueUnitsCycles", "'Next unit' button cycles idle units", settings.checkForDueUnitsCycles) {
            updateWorld { settings.checkForDueUnitsCycles = it }
        }
        toggle("autoUnitCycle", "Auto Unit Cycle", settings.autoUnitCycle) { updateWorld { settings.autoUnitCycle = it } }
        toggle("alternateUnitCycleOrder", "Alternate Unit cycle order", settings.alternateUnitCycleOrder) {
            updateWorld { settings.alternateUnitCycleOrder = it }
        }
        toggle("singleTapMove", "Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
        toggle("longTapMove", "Move units with a long tap", settings.longTapMove) { settings.longTapMove = it }
        toggle("confirmNextTurn", "Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }
        toggle("autoEndTurn", "Automatically end turn", settings.autoEndTurn,
            detail = "Once your last unit has orders and nothing else needs a decision") { settings.autoEndTurn = it }
        toggle("orderTradeOffers", "Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
        slider("notificationsLog", "Notifications log max turns", settings.notificationsLogMaxTurns.toFloat(), 3f, 15f, 1f) {
            settings.notificationsLogMaxTurns = it.toInt()
        }
    }

    private fun NativeForm.display() = section("Display") {
        choice("screenSize", "UI Scale", settings.screenSize.name,
            GameSettings.ScreenSize.entries.map { NativeForm.Option(it.name, it.name) }) { value ->
            val size = GameSettings.ScreenSize.entries.firstOrNull { it.name == value } ?: return@choice
            if (size == settings.screenSize) return@choice
            settings.screenSize = size
            reloadScreens()
        }
        val tilesets = ImageGetter.getAvailableTilesets().toList()
        if (tilesets.size > 1) choice("tileset", "Tileset", settings.tileSet, tilesets.map { NativeForm.Option(it, it) }) { value ->
            if (value == settings.tileSet) return@choice
            val unitsets = ImageGetter.getAvailableUnitsets().toList()
            if (settings.tileSet == settings.unitSet && value in unitsets) settings.unitSet = value
            settings.tileSet = value
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            reloadScreens()
        }
        val unitsets = ImageGetter.getAvailableUnitsets().toList()
        if (unitsets.isNotEmpty()) {
            val none = "None"
            choice("unitset", "Unitset", settings.unitSet ?: none,
                (listOf(none) + unitsets).map { NativeForm.Option(it, it) }) { value ->
                settings.unitSet = value.takeUnless { it == none }
                TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
                reloadScreens()
            }
        }
        val skins = ImageGetter.getAvailableSkins().toList()
        if (skins.size > 1) choice("skin", "UI Skin", settings.skin, skins.map { NativeForm.Option(it, it) }) { value ->
            if (value == settings.skin) return@choice
            settings.skin = value
            SkinCache.assembleSkinConfigs(ImageGetter.ruleset.mods)
            reloadScreens()
        }
        choice("notificationScroll", "Notifications on world screen", settings.notificationScroll,
            NotificationsScroll.UserSetting.entries.map { NativeForm.Option(it.name, it.name) }) {
            settings.notificationScroll = it
            GUI.setUpdateWorldOnNextRender()
        }
        toggle("showMinimap", "Show minimap", settings.showMinimap) { updateWorld { settings.showMinimap = it } }
        toggle("showTutorials", "Show tutorials", settings.showTutorials) { updateWorld { settings.showTutorials = it } }
        button("resetTutorials", "Reset tutorials", symbol = "arrow.counterclockwise",
            confirm = "Do you want to reset completed tutorials?") {
            settings.tutorialsShown.clear()
            settings.tutorialTasksCompleted.clear()
            game.getNativeUi()?.showToast("Done!".tr(), 2000)
        }
        toggle("longPressIndicators", "Show long-press indicators", settings.showLongPressIndicators.toBoolean()) {
            settings.showLongPressIndicators = GameSettings.LongPressIndicatorSetting.of(it)
        }
        toggle("movableCircles", "Use circles to indicate movable tiles", settings.useCirclesToIndicateMovableTiles) {
            updateWorld { settings.useCirclesToIndicateMovableTiles = it }
        }
        toggle("continuousRendering", "Continuous rendering", settings.continuousRendering,
            detail = "When disabled, saves battery life but certain animations will be suspended") {
            settings.continuousRendering = it
            Gdx.graphics.isContinuousRendering = it
        }
    }

    /** The Home Screen icon; the Monolith is free, the alternates are a supporter extra */
    private fun NativeForm.appIcon() = section("App Icon",
        footer = "The Monolith icon is included for everyone; alternate icons are a supporter extra.") {
        val locked = !settings.supporterUnlocked
        val current = game.getAlternateAppIconName() ?: settings.selectedAlternateAppIcon ?: "default"
        val options = listOf(NativeForm.Option("default", "Monolith (default)", icon = "ExtraImages/Icons/Preview-Monolith.png")) +
            AppIconTab.alternateIcons.map {
                NativeForm.Option(it.systemName, it.label, icon = "ExtraImages/${it.preview}",
                    symbol = if (locked) "lock.fill" else null)
            }
        tiles("appIcon", current, options, style = "icons") { picked ->
            val systemName = picked.takeUnless { it == "default" }
            if (systemName != null && locked) {
                withSheetClosed { SupportPopup(it).open(force = true) }
                return@tiles
            }
            game.setAlternateAppIcon(systemName) { success ->
                if (success) {
                    settings.selectedAlternateAppIcon = systemName
                    settings.save()
                } else {
                    val detail = game.getLastAlternateAppIconError()
                    game.screen?.let { ToastPopup(if (detail.isNullOrBlank()) "This icon could not be selected"
                        else "This icon could not be selected: $detail", it) }
                }
                game.getNativeUi()?.refresh(this@OptionsPresenter)
            }
        }
    }

    private fun NativeForm.visualHints() = section("Visual Hints") {
        toggle("showUnitMovements", "Show unit movement arrows", settings.showUnitMovements) { updateWorld { settings.showUnitMovements = it } }
        toggle("settlerHints", "Show suggested city locations for units that can found cities", settings.showSettlersSuggestedCityLocations) {
            updateWorld { settings.showSettlersSuggestedCityLocations = it }
        }
        toggle("showTileYields", "Show tile yields", settings.showTileYields) { updateWorld { settings.showTileYields = it } }
        toggle("showWorkedTiles", "Show worked tiles", settings.showWorkedTiles) { updateWorld { settings.showWorkedTiles = it } }
        toggle("showResources", "Show resources and improvements", settings.showResourcesAndImprovements) {
            updateWorld { settings.showResourcesAndImprovements = it }
        }
        toggle("showPixelImprovements", "Show pixel improvements", settings.showPixelImprovements) {
            updateWorld { settings.showPixelImprovements = it }
        }
        slider("unitIconOpacity", "Unit icon opacity", settings.unitIconOpacity, 0f, 1f, 0.1f, format = "percent") {
            settings.unitIconOpacity = it
            GUI.setUpdateWorldOnNextRender()
        }
    }

    private fun NativeForm.automation() = section("Automation", expanded = false) {
        toggle("autoAssignProduction", "Auto-assign city production", settings.autoAssignCityProduction) {
            updateWorld { settings.autoAssignCityProduction = it }
        }
        toggle("autoBuildRoads", "Auto-build roads", settings.autoBuildingRoads) { settings.autoBuildingRoads = it }
        toggle("replaceImprovements", "Automated workers replace improvements", settings.automatedWorkersReplaceImprovements) {
            settings.automatedWorkersReplaceImprovements = it
        }
        toggle("keepVegetation", "Stop automated workers from removing vegetation terrain", settings.stopAutomatedWorkersRemoveVegetation) {
            settings.stopAutomatedWorkersRemoveVegetation = it
        }
        toggle("moveOnTurnStart", "Automated units move on turn start", settings.automatedUnitsMoveOnTurnStart) {
            updateWorld { settings.automatedUnitsMoveOnTurnStart = it }
        }
        toggle("unitsUpgrade", "Automated units can upgrade", settings.automatedUnitsCanUpgrade) { settings.automatedUnitsCanUpgrade = it }
        toggle("unitsPromote", "Automated units choose promotions", settings.automatedUnitsChoosePromotions) {
            settings.automatedUnitsChoosePromotions = it
        }
        toggle("autoBombard", "Cities auto-bombard at end of turn", settings.citiesAutoBombardAtEndOfTurn) {
            settings.citiesAutoBombardAtEndOfTurn = it
        }
    }

    private fun NativeForm.sound() = section("Sound") {
        val music = game.musicController
        slider("soundEffects", "Sound effects volume", settings.soundEffectsVolume, 0f, 1f, 0.05f, format = "percent") {
            settings.soundEffectsVolume = it
        }
        slider("citySounds", "City ambient sound volume", settings.citySoundsVolume, 0f, 1f, 0.05f, format = "percent") {
            settings.citySoundsVolume = it
        }
        if (music.isVoicesAvailable()) slider("voices", "Leader voices volume", settings.voicesVolume, 0f, 1f, 0.05f, format = "percent") {
            settings.voicesVolume = it
            music.setOverlayVolume(it)
        }
        if (music.isMusicAvailable()) slider("music", "Music volume", settings.musicVolume, 0f, 1f, 0.05f, format = "percent") {
            settings.musicVolume = it
            music.setVolume(it)
            if (!music.isPlaying()) music.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
        }
    }

    private fun NativeForm.language() = section("Language") {
        val completion = game.translations.percentCompleteOfLanguages
        val languages = completion.keys.sortedWith(
            compareBy<String> { it != Constants.english }.thenByDescending { completion[it] ?: 0 }
        )
        choice("language", "Language", settings.language, languages.map {
            val percent = if (it == Constants.english) 100 else completion[it] ?: 0
            NativeForm.Option(it, it.replace('_', ' '), "$percent%", icon = "FlagIcons/$it")
        }, style = "list", icon = "FlagIcons/${settings.language}") { value ->
            if (value == settings.language) return@choice
            settings.language = value
            settings.updateLocaleFromLanguage()
            game.translations.tryReadTranslationForCurrentLanguage()
            reloadScreens()
        }
    }

    private fun NativeForm.advanced() = section("Advanced", expanded = false) {
        choice("autosaveTurns", "Turns between autosaves", settings.turnsBetweenAutosaves.toString(),
            listOf(1, 2, 5, 10, 20, 50, 100, 1000).map { NativeForm.Option(it.toString(), it.toString()) }) {
            settings.turnsBetweenAutosaves = it.toIntOrNull() ?: return@choice
        }
        slider("maxZoom", "Max zoom out", settings.maxWorldZoomOut, 2f, 6f, 1f) {
            settings.maxWorldZoomOut = it
            if (GUI.isWorldLoaded()) GUI.getMap().reloadMaxZoom()
        }
        slider("longPressDelay", "Long press delay", settings.longPressDelay, 0.2f, 4f, 0.1f, format = "decimal") {
            settings.longPressDelay = it
        }
        toggle("enlargeNotifications", "Enlarge selected notifications", settings.enlargeSelectedNotification) {
            settings.enlargeSelectedNotification = it
        }
        toggle("astar", "Enable experimental A* pathing", settings.useAStarPathfinding) { settings.useAStarPathfinding = it }
        toggle("easterEggs", "Enable Easter Eggs", settings.enableEasterEggs) { settings.enableEasterEggs = it }
    }

    private fun NativeForm.about() = section("About",
        footer = "DeCiv is an independent game derived from the open-source Unciv project. It is not affiliated with, endorsed by, or supported by the Unciv project or its authors. " +
            "Unciv is © yairm210 and contributors, licensed under the Mozilla Public License 2.0."
    ) {
        val sourceTag = "${Constants.decivSourceURL}tree/${UncivGame.sourceTag}"
        text("version", "Version", UncivGame.appVersionString())
        button("source", "Open source code and licenses", symbol = "chevron.left.forwardslash.chevron.right") {
            Gdx.net.openURI(sourceTag)
        }
        if (game.isStoreAvailable()) {
            val badge = SupportPopup.badgeName(settings)
            button("support", badge?.let { "$it — owned" } ?: "DeCiv Supporter — optional", symbol = "heart.fill") {
                withSheetClosed { SupportPopup(it).open(force = true) }
            }
            button("restore", "Restore purchases", symbol = "arrow.clockwise") {
                game.restoreStorePurchases { restored ->
                    ToastPopup(if (restored.isNotEmpty()) "Purchases restored" else "No purchases to restore", game.screen!!)
                    game.getNativeUi()?.refresh(this@OptionsPresenter)
                }
            }
        }
        button("contact", "Contact support", symbol = "envelope.fill") {
            withSheetClosed { SupportContact.open(it) }
        }
    }

    /** Scene2D popups open below the sheet, so it steps aside for them */
    private fun withSheetClosed(action: (BaseScreen) -> Unit) {
        close()
        game.screen?.let(action)
    }
}
