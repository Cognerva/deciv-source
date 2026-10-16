package com.unciv.ui.nativeui

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.ActorAttachments
import com.unciv.ui.components.input.activate
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen

/**
 * Native chrome for the map editor. The map stays in Scene2D and is painted by tapping it, as
 * before; this replaces the tools drawer with a top bar and a brush palette. The palette items
 * are the edit tab's own clickable brush lines, so tapping one selects that brush exactly as the
 * drawer would. The drawer's other tabs (new map, load, save, options, mods, map info) open as
 * native sheets read from their Scene2D tables, see [MirroredFormPresenter].
 *
 * Ids: `back`, `category:<i>`, `item:<category>:<i>`, `brush:<size>` (-1 floods), `sheet:<page>`.
 */
class MapEditorPresenter(private val screen: MapEditorScreen) {
    private var category = 0
    private var selected: Pair<Int, Int>? = null
    private var sentRevision = -1

    private class Item(val label: String, val icon: String?, val actor: Actor)

    /** The clickable lines of a brush page: each has activation actions, a label and usually an icon */
    private fun items(page: Actor): List<Item> {
        val result = ArrayList<Item>()
        fun firstLabel(actor: Actor): String? = when (actor) {
            is Label -> actor.text.toString().trim().takeIf { it.isNotEmpty() }
            is Group -> actor.children.firstNotNullOfOrNull(::firstLabel)
            else -> null
        }
        fun firstIcon(actor: Actor): String? = when (actor) {
            is Image -> ((actor.drawable as? TextureRegionDrawable)?.region as? TextureAtlas.AtlasRegion)?.name
            is Group -> actor.children.firstNotNullOfOrNull(::firstIcon)
            else -> null
        }
        fun visit(actor: Actor) {
            if (!actor.isVisible) return
            if (ActorAttachments.getOrNull(actor)?.activationActions != null) {
                val label = firstLabel(actor) ?: return
                result += Item(label, firstIcon(actor), actor)
                return
            }
            when (actor) {
                is ScrollPane -> actor.actor?.let(::visit)
                is Group -> actor.children.forEach(::visit)
            }
        }
        visit(page)
        return result
    }

    private fun pages() = screen.tabs.edit.nativeSubTabs.entries.toList()

    /** @return a snapshot when the map or ruleset changed since the last one sent */
    fun snapshotIfChanged(): String? {
        if (sentRevision == screen.nativeRevision) return null
        return snapshotJson()
    }

    fun snapshotJson(): String {
        sentRevision = screen.nativeRevision
        val pages = pages()
        if (category !in pages.indices) category = 0
        val map = screen.tileMap
        return NativeUiJson().obj {
            put("title", plainText(map.mapParameters.name.ifEmpty { "Map editor".tr() }))
            put("subtitle", plainText("{${map.mapParameters.mapSize.name}} · {${map.mapParameters.baseRuleset}}".tr()))
            put("category", category)
            put("brushSize", screen.tabs.edit.brushSize)
            selected?.let { put("selected", "${it.first}:${it.second}") }
            array("categories") {
                for ((index, entry) in pages.withIndex()) obj {
                    val (caption, page) = entry.key to entry.value
                    put("name", plainText(caption.tr()))
                    put("disabled", page.second)
                    if (index == category) array("items") {
                        for (item in items(page.first)) obj {
                            put("label", plainText(item.label))
                            put("icon", item.icon)
                        }
                    }
                }
            }
        }.toString()
    }

    fun perform(id: String) {
        val parts = id.split(':')
        when (parts[0]) {
            "back" -> screen.closeEditor()
            "category" -> {
                category = parts.getOrNull(1)?.toIntOrNull() ?: return
            }
            "item" -> {
                val pageIndex = parts.getOrNull(1)?.toIntOrNull() ?: return
                val itemIndex = parts.getOrNull(2)?.toIntOrNull() ?: return
                val page = pages().getOrNull(pageIndex)?.value?.first ?: return
                val item = items(page).getOrNull(itemIndex) ?: return
                // The edit page must be active for taps on the map to paint
                if (screen.tabs.activePage != 2) screen.tabs.selectPage(2)
                item.actor.activate()
                selected = pageIndex to itemIndex
            }
            "brush" -> screen.tabs.edit.brushSize = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(-1, 5) ?: return
            "sheet" -> openSheet(parts.getOrNull(1) ?: return)
        }
        sentRevision = -1
    }

    private fun openSheet(page: String) {
        val tabs = screen.tabs
        val (index, title, root) = when (page) {
            "view" -> Triple(0, "View", tabs.view)
            "generate" -> Triple(1, "Generate", tabs.generate)
            "load" -> Triple(3, "Load", tabs.load)
            "save" -> Triple(4, "Save", tabs.save)
            "mods" -> Triple(5, "Mods", tabs.mods)
            "options" -> Triple(6, "Options", tabs.options)
            else -> return
        }
        tabs.selectPage(index)
        val nativeUi = UncivGame.Current.getNativeUi() ?: return
        val sheet = object : MirroredFormPresenter() {
            override val screenTitle = title
            override fun root(): Actor = root
            override fun close() { nativeUi.closeSheet() }
        }
        nativeUi.showSheet(sheet) {
            // Back to painting
            if (tabs.activePage != 2) tabs.selectPage(2)
            sentRevision = -1
        }
    }
}
