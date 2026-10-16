package com.unciv.logic.files

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.utils.Concurrency
import com.unciv.utils.Log

/**
 * Mirrors saved games to the platform's cloud storage (iCloud Drive on iOS), so a game saved on
 * one device can be loaded, or resumed, on another. Named saves and the current autosave are
 * mirrored; the numbered per-turn autosaves stay local. The newer copy of a file wins.
 *
 * All platform calls block, so they run on a background thread.
 */
object CloudSaves {
    interface Platform {
        /** Whether the cloud container is reachable: signed in, and the app has the capability */
        fun isAvailable(): Boolean
        /** False when the user has iCloud but this build has no iCloud container, so the option is pointless */
        fun isConfigured(): Boolean
        fun upload(localPath: String, name: String)
        fun delete(name: String)
        /** Copies newer cloud files into [localDir] and newer local ones up. @return files changed locally */
        fun syncInto(localDir: String): Int
    }

    var platform: Platform? = null

    @Volatile
    var available = false
        private set

    /** Whether to offer the option; see [Platform.isConfigured] */
    @Volatile
    var configured = false
        private set

    private val enabled get() = platform != null && UncivGame.isCurrentInitialized() && UncivGame.Current.settings.iCloudSaves

    /** Mirrored: everything in the save folder except the numbered "Autosave-<civ>-<turn>" copies */
    private fun isMirrored(file: FileHandle) =
        file.parent().name() == SAVE_FILES_FOLDER && !file.name().startsWith("Autosave-")

    fun saved(file: FileHandle) {
        if (!enabled || !isMirrored(file)) return
        val path = file.file().absolutePath
        val name = file.name()
        Concurrency.run("CloudSaveUpload") {
            try {
                if (available || refreshAvailability()) platform?.upload(path, name)
            } catch (ex: Exception) {
                Log.error("Could not upload $name to the cloud", ex)
            }
        }
    }

    fun deleted(file: FileHandle) {
        if (!enabled || !isMirrored(file)) return
        val name = file.name()
        Concurrency.run("CloudSaveDelete") {
            try {
                if (available) platform?.delete(name)
            } catch (ex: Exception) {
                Log.error("Could not delete $name from the cloud", ex)
            }
        }
    }

    private fun refreshAvailability(): Boolean {
        available = platform?.isAvailable() == true
        configured = available || platform?.isConfigured() == true
        return available
    }

    /** Learns in the background whether the option applies, e.g. before showing the options */
    fun checkAvailability() {
        if (platform == null) return
        Concurrency.run("CloudSaveCheck") { refreshAvailability() }
    }

    /** Brings both sides up to date; [onDone] runs on the GL thread with the number of local files that changed */
    fun sync(onDone: (Int) -> Unit = {}) {
        if (!enabled) return
        val folder = UncivGame.Current.files.getLocalFile(SAVE_FILES_FOLDER).file().absolutePath
        Concurrency.run("CloudSaveSync") {
            val changed = try {
                if (refreshAvailability()) platform?.syncInto(folder) ?: 0 else 0
            } catch (ex: Exception) {
                Log.error("Could not sync saves with the cloud", ex)
                0
            }
            Concurrency.runOnGLThread { onDone(changed) }
        }
    }
}
