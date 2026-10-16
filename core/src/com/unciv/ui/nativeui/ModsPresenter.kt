package com.unciv.ui.nativeui

import com.badlogic.gdx.Gdx
import com.unciv.logic.github.GithubAPI
import com.unciv.ui.screens.modmanager.ModManagementScreen
import com.unciv.ui.screens.modmanager.ModManagementScreen.Companion.cleanModName
import com.unciv.ui.screens.modmanager.ModUIData
import com.unciv.utils.Concurrency

/**
 * Native mod manager: the installed mods with their actions (audiovisual, delete), and adding a
 * mod from a link. DeCiv does not browse or list mods itself: players find mods on the web
 * (a GitHub topic page is offered) and paste the link here. Downloading and deleting are
 * [ModManagementScreen]'s own, which keeps running hidden.
 */
class ModsPresenter(private val screen: ModManagementScreen) : NativeFormPresenter() {
    private var query = ""
    private var selected: String? = null
    private var urlText = ""
    private var urlStatus: String? = null

    companion object {
        /** Where the community tags its mods; DeCiv only links to it */
        const val COMMUNITY_MODS_URL = "https://github.com/topics/unciv-mod"
    }

    override fun NativeForm.build() {
        title = "Mods"
        closeSymbol = "chevron.left"
        onClose { screen.closeNatively() }

        addFromLink()
        installed()
    }

    private fun matches(mod: ModUIData) = query.isBlank() ||
        mod.name.contains(query, true) || mod.author().contains(query, true)

    private fun NativeForm.addFromLink() = section("Add a mod",
        footer = "Mods are made and shared by their authors. They are not made, reviewed or endorsed by DeCiv, and DeCiv is not responsible for their content.") {
        button("browse", "Find mods on GitHub", symbol = "safari",
            detail = "Opens the community's list of mods in your browser. Copy the link of the one you like.") {
            Gdx.net.openURI(COMMUNITY_MODS_URL)
        }
        field("url.text", "Mod link", urlText, placeholder = "https://github.com/…", detail = urlStatus) { urlText = it.trim() }
        button("url.paste", "Paste from clipboard", symbol = "doc.on.clipboard") {
            urlText = Gdx.app.clipboard.contents?.trim() ?: ""
        }
        button("url.download", "Download", symbol = "arrow.down.circle", role = "primary", enabled = urlText.isNotEmpty()) {
            val url = urlText
            urlStatus = "Downloading..."
            // Parsing a link can query GitHub
            Concurrency.run("ParseModUrl") {
                val repo = GithubAPI.Repo.parseUrl(url)
                Concurrency.runOnGLThread {
                    if (repo == null) {
                        urlStatus = "Invalid link!"
                        refresh()
                    } else screen.downloadNatively(repo, { urlStatus = it; refresh() }) {
                        urlStatus = null
                        urlText = ""
                        refresh()
                    }
                }
            }
        }
    }

    private fun NativeForm.installed() {
        val all = screen.installedMods.values.filter { it.ruleset?.folderLocation != null }
        val mods = all.filter(::matches).sortedBy { it.name.lowercase() }
        val current = selected?.let { screen.installedMods[it] }
        if (current != null) selectedInstalled(current)

        section("Installed", id = "installed") {
            if (all.size > 8) search("search", "Search mods", query) { query = it }
            if (mods.isEmpty()) text("installed.empty", if (query.isBlank()) "No mods installed yet" else "No matching mods")
            for (mod in mods) {
                row("installed.${mod.name}", cleanModName(mod.name), detail = mod.author().takeIf { it.isNotEmpty() },
                    icon = NativeForm.Option("", "", symbol = if (mod.isVisual) "paintpalette.fill" else "puzzlepiece.extension.fill",
                        tint = "#e8a84d"),
                    tag = if (mod.isVisual) "Audiovisual" else null,
                    selected = mod.name == selected, translate = false) { selected = mod.name }
            }
        }
    }

    private fun NativeForm.selectedInstalled(mod: ModUIData) {
        val ruleset = mod.ruleset ?: return
        section(cleanModName(mod.name), id = "selected") {
            text("selected.description", mod.description)
            if (mod.author().isNotEmpty()) text("selected.author", "Author: [${mod.author()}]")
            toggle("selected.visual", "Permanent audiovisual mod", mod.isVisual) {
                screen.setVisualNatively(ruleset, it)
            }
            button("selected.delete", "Delete [${cleanModName(mod.name)}]", symbol = "trash", role = "destructive",
                confirm = "Are you SURE you want to delete this mod?") {
                screen.deleteNatively(ruleset)
                selected = null
            }
        }
    }

    private fun refresh() {
        val presenter = this
        Concurrency.runOnGLThread { com.unciv.UncivGame.Current.getNativeUi()?.refresh(presenter) }
    }
}
