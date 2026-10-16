package com.unciv.ui.nativeui

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.CloudSaves
import com.unciv.logic.files.UncivFiles
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.UncivDateFormat.formatDate
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.LoadOrSaveScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import java.util.Date

/**
 * Native load and save screens: the saved games as a list, a preview of the selected one, and
 * load, save, delete and custom-location actions. Errors that need the Scene2D flow (missing
 * mods) are handed back to [LoadGameScreen].
 *
 * @param gameInfo the game to save; null for the load screen
 */
class SavesPresenter(private val screen: LoadOrSaveScreen, private val gameInfo: GameInfo?) : NativeScreenPresenter {
    override val kind = "saves"

    private val game get() = UncivGame.Current
    private val saving get() = gameInfo != null
    private var selected: FileHandle? = null
    private var previewLines: List<String> = emptyList()
    private var previewCiv: String? = null
    private var previewNation: com.unciv.models.ruleset.nation.Nation? = null
    private var previewFailed = false
    private var busy: String? = null
    private var error: String? = null

    init {
        // Saves from other devices; the list refreshes if any arrived
        val presenter = this
        CloudSaves.sync { changed -> if (changed > 0) game.getNativeUi()?.refresh(presenter) }
    }

    private fun saves() = game.files.getSaves(game.settings.showAutosaves)
        .sortedByDescending { it.lastModified() }
        .toList()

    override fun snapshotJson(): String = NativeUiJson().obj {
        put("saving", saving)
        put("title", plainText((if (saving) "Save game" else "Load game").tr()))
        put("defaultName", gameInfo?.let { plainText("[${it.currentPlayer}] - [${it.turns}] turns".tr(hideIcons = true)) } ?: "")
        put("showAutosaves", game.settings.showAutosaves)
        array("saves") {
            for (save in saves()) obj {
                put("name", save.name())
                put("date", Date(save.lastModified()).formatDate())
                put("autosave", save.name().startsWith("Autosave"))
            }
        }
        selected?.let { file ->
            obj("selected") {
                put("name", file.name())
                strings("lines", previewLines)
                put("civ", previewCiv)
                put("civIcon", previewNation?.let { "NationIcons/${it.name}" })
                put("outerColor", previewNation?.let { "#" + it.getOuterColor().toString().take(6) })
                put("innerColor", previewNation?.let { "#" + it.getInnerColor().toString().take(6) })
                put("failed", previewFailed)
            }
        }
        put("busy", busy)
        put("error", error)
        put("canPickFile", true)
        obj("labels") {
            for (key in listOf("Saved games", "Show autosaves", "No saved games", "Delete save", "Load game", "Save game",
                "Saved game name", "Overwrite", "Overwrite existing file?", "Are you sure you want to delete this save?",
                "Load from custom location", "Save to custom location"))
                put(key, plainText(key.tr()))
        }
    }.toString()

    override fun perform(id: String) {
        val argument = id.substringAfter(':', "")
        when (id.substringBefore(':')) {
            "close" -> game.popScreen()
            "toggleAutosaves" -> game.settings.showAutosaves = !game.settings.showAutosaves
            "dismissError" -> error = null
            "select" -> select(game.files.getSave(argument))
            "delete" -> delete(game.files.getSave(argument))
            "load" -> load(game.files.getSave(argument))
            "save" -> save(argument)
            "loadFromFile" -> loadFromFile()
            "saveToFile" -> saveToFile()
        }
    }

    private fun refresh() = game.getNativeUi()?.refresh(this)

    private fun select(file: FileHandle) {
        if (!file.exists()) return
        selected = file
        previewLines = emptyList()
        previewCiv = null
        previewNation = null
        previewFailed = false
        Concurrency.run("LoadMetaData") {
            val lines = ArrayList<String>()
            var civ: String? = null
            var nation: com.unciv.models.ruleset.nation.Nation? = null
            var failed = false
            try {
                val preview = game.files.loadGamePreviewFromFile(file)
                val player = preview.civilizations.firstOrNull { it.isPlayerCivilization() }
                civ = player?.civName?.tr(hideIcons = true)
                nation = player?.let { RulesetCache[preview.gameParameters.baseRuleset]?.nations?.get(it.civName) }
                lines += plainText("Turn".tr() + " ${preview.turns} · " + preview.difficulty.tr())
                lines += plainText("Saved at".tr() + ": " + Date(file.lastModified()).formatDate())
                if (preview.gameParameters.mods.isNotEmpty())
                    lines += plainText("Mods:".tr() + " " + preview.gameParameters.mods.joinToString())
            } catch (_: Exception) {
                failed = true
                lines += "Could not load game!".tr()
            }
            launchOnGLThread {
                if (selected != file) return@launchOnGLThread
                previewLines = lines
                previewCiv = civ
                previewNation = nation
                previewFailed = failed
                refresh()
            }
        }
    }

    private fun delete(file: FileHandle) {
        val name = file.name()
        error = try {
            if (game.files.deleteSave(file)) null else "Failed to delete [$name].".tr()
        } catch (_: SecurityException) {
            "Insufficient permissions to delete [$name].".tr()
        } catch (_: Throwable) {
            "Failed to delete [$name].".tr()
        }
        if (selected == file) selected = null
    }

    private fun load(file: FileHandle) {
        if (busy != null || !file.exists()) return
        busy = "Loading..."
        Concurrency.run("Load game") {
            try {
                val loadedGame = game.files.loadGameFromFile(file)
                game.loadGame(loadedGame, callFromLoadScreen = true)
            } catch (ex: Exception) {
                launchOnGLThread { onLoadFailed(ex) }
            }
        }
    }

    private fun onLoadFailed(ex: Exception) {
        busy = null
        val (message, isUserFixable) = LoadOrSaveScreen.getLoadExceptionMessage(ex)
        if (!isUserFixable) Log.error("Could not load game", ex)
        error = plainText(message)
        // Missing mods are downloaded by the Scene2D screen's own flow
        (screen as? LoadGameScreen)?.handleNativeLoadFailure(ex)
        refresh()
    }

    private fun save(name: String) {
        val gameInfo = gameInfo ?: return
        if (busy != null) return
        if (!UncivFiles.isValidFileName(name)) {
            error = "Invalid file name".tr()
            return
        }
        busy = "Saving..."
        val file = game.files.getSave(name)
        Concurrency.runOnNonDaemonThreadPool("SaveGame") {
            game.files.saveGame(gameInfo, file) { exception ->
                launchOnGLThread {
                    busy = null
                    if (exception == null) {
                        game.popScreen()
                        return@launchOnGLThread
                    }
                    val (message, _) = LoadOrSaveScreen.getLoadExceptionMessage(exception, "Could not save game!", file)
                    error = plainText(message)
                    refresh()
                }
            }
        }
    }

    private fun loadFromFile() {
        if (busy != null) return
        game.files.loadGameFromCustomLocation(
            { loaded ->
                busy = "Loading..."
                refresh()
                Concurrency.run("Load from custom location") {
                    try {
                        game.loadGame(loaded, callFromLoadScreen = true)
                    } catch (ex: Exception) {
                        launchOnGLThread { onLoadFailed(ex) }
                    }
                }
            },
            { ex ->
                if (ex is PlatformSaverLoader.Cancelled) return@loadGameFromCustomLocation
                val (message, _) = LoadOrSaveScreen.getLoadExceptionMessage(ex, "Could not load game from custom location!")
                error = plainText(message)
                refresh()
            }
        )
    }

    private fun saveToFile() {
        val gameInfo = gameInfo ?: return
        if (busy != null) return
        val name = "[${gameInfo.currentPlayer}] - [${gameInfo.turns}] turns".tr(hideIcons = true)
        game.files.saveGameToCustomLocation(gameInfo, name,
            { game.popScreen() },
            { ex ->
                if (ex is PlatformSaverLoader.Cancelled) return@saveGameToCustomLocation
                val (message, _) = LoadOrSaveScreen.getLoadExceptionMessage(ex, "Could not save game to custom location!")
                error = plainText(message)
                refresh()
            }
        )
    }
}
