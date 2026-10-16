package com.unciv.ui.nativeui

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.graphics.Color
import com.unciv.ui.components.widgets.UncivTextField
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Reads a Scene2D popup that only shows text, icons and text buttons, so it can be presented as
 * a native card (see [com.unciv.ui.popups.Popup.open]): text, icons, text buttons, text fields,
 * checkboxes and small buttons holding only a label (like "+50"). Popups with select boxes, sliders,
 * tabs or other widgets return null and stay Scene2D.
 */
internal object NativePopupReader {
    /** [inputs] are the popup's text fields and checkboxes, in the order of the card's inputs */
    class Content(val card: NativeStoryCard, val buttons: List<Button>, val inputs: List<Actor>)

    /** Atlas folders worth showing as the card's emblem, most telling first */
    private val iconFolders = listOf(
        "NationIcons/", "TechIcons/", "BuildingIcons/", "UnitIcons/", "PolicyIcons/",
        "ReligionIcons/", "ResourceIcons/", "UnitPromotionIcons/", "ImprovementIcons/",
    )

    /** One piece of a Scene2D panel, in reading order */
    sealed class Entry {
        class Text(val text: String) : Entry()
        class Action(val button: TextButton, val destructive: Boolean) : Entry()
        class Field(val field: TextField) : Entry()
        class Toggle(val checkBox: CheckBox) : Entry()
        class Select(val selectBox: SelectBox<*>) : Entry()
        class Slide(val slider: Slider) : Entry()
    }

    /**
     * Reads a Scene2D panel as text and buttons in reading order, skipping what can't be shown
     * natively (bars, charts), for screens that mirror a Scene2D panel's actions.
     */
    fun readEntries(root: Actor): List<Entry> {
        val entries = ArrayList<Entry>()
        val negative = BaseScreen.skin.optional("negative", TextButtonStyle::class.java)
        fun visit(actor: Actor) {
            if (!actor.isVisible) return
            when (actor) {
                is CheckBox -> entries += Entry.Toggle(actor)
                is TextButton -> entries += Entry.Action(actor, negative != null && actor.style == negative)
                is TextField -> entries += Entry.Field(actor)
                is SelectBox<*> -> entries += Entry.Select(actor)
                is Slider -> entries += Entry.Slide(actor)
                is Button -> {}
                is Label -> clean(actor.text.toString()).takeIf { it.isNotEmpty() }?.let { entries += Entry.Text(it) }
                is ScrollPane -> actor.actor?.let(::visit)
                is Group -> actor.children.forEach(::visit)
            }
        }
        visit(root)
        // "Our relationship:" and its value are separate labels; read them as one line
        val merged = ArrayList<Entry>()
        for (entry in entries) {
            val previous = merged.lastOrNull()
            if (entry is Entry.Text && previous is Entry.Text && previous.text.endsWith(":") && '\n' !in entry.text)
                merged[merged.lastIndex] = Entry.Text(previous.text + " " + entry.text)
            else merged += entry
        }
        return merged
    }

    /** libGDX colour markup such as `[GRAY]`, `[#ff0000]` and the closing `[]` */
    private val colorMarkup = Regex("""\[(#[0-9a-fA-F]{6,8}|[A-Z_]+)?]""")

    private fun clean(text: String) = colorMarkup.replace(text, "").trim()

    fun read(vararg roots: Actor): Content? {
        val texts = ArrayList<String>()
        val buttons = ArrayList<Button>()
        val inlineButtons = HashSet<Button>()
        val icons = ArrayList<String>()
        val inputs = ArrayList<Actor>()
        val inputCards = ArrayList<NativeStoryCard.Input>()
        var error: String? = null
        // Text placed after a button explains that button, e.g. "Annex" and what annexing does
        val details = HashMap<Button, MutableList<String>>()

        fun visit(actor: Actor): Boolean {
            if (!actor.isVisible) return true
            return when (actor) {
                is CheckBox -> {
                    inputs += actor
                    inputCards += NativeStoryCard.Input("toggle", clean(actor.text.toString()), actor.isChecked.toString())
                    true
                }
                is TextButton -> { buttons += actor; true }
                is Button -> {
                    // A button holding just a label, e.g. AskNumberPopup's "+50"
                    val label = actor.children.singleOrNull() as? Label ?: return false
                    if (clean(label.text.toString()).isEmpty()) return false
                    buttons += actor
                    inlineButtons += actor
                    true
                }
                is TextField -> {
                    inputs += actor
                    val numeric = actor is UncivTextField.Integer
                    inputCards += NativeStoryCard.Input(if (numeric) "number" else "text", "", actor.text,
                        actor.messageText?.let(::clean))
                    true
                }
                is Label -> {
                    val text = clean(actor.text.toString())
                    if (text.isNotEmpty()) {
                        val owner = buttons.lastOrNull { it !in inlineButtons }
                        when {
                            actor.color == Color.RED -> error = text
                            owner == null -> texts += text
                            else -> details.getOrPut(owner) { ArrayList() } += text
                        }
                    }
                    true
                }
                is Image -> {
                    val region = (actor.drawable as? TextureRegionDrawable)?.region as? TextureAtlas.AtlasRegion
                    region?.name?.let { name -> if (iconFolders.any { name.startsWith(it) }) icons += name }
                    true
                }
                is ScrollPane -> actor.actor?.let(::visit) ?: true
                // Tables, containers, icon circles and portraits; widgets that are not groups (sliders, select boxes) don't pass
                is Group -> actor.children.all(::visit)
                else -> false
            }
        }

        for (root in roots) if (!visit(root)) return null
        if (texts.isEmpty() && inputs.isEmpty()) return null

        // A short first line reads as the title, e.g. a leader's or wonder's name
        val hasTitle = texts.size >= 2 && texts[0].length <= 60 && '\n' !in texts[0]
        val title = if (hasTitle) texts[0] else ""
        val body = if (hasTitle) texts.drop(1) else texts

        val icon = iconFolders.firstNotNullOfOrNull { folder -> icons.firstOrNull { it.startsWith(folder) } }
        val nation = icon?.takeIf { it.startsWith("NationIcons/") }
            ?.let { ImageGetter.ruleset.nations[it.removePrefix("NationIcons/")] }
        val negative = BaseScreen.skin.optional("negative", TextButtonStyle::class.java)

        val card = NativeStoryCard(
            title = title,
            pages = listOf(body.joinToString("\n\n")),
            doneLabel = "",
            icon = icon,
            iconBackground = nation?.getOuterColor(),
            iconTint = nation?.getInnerColor(),
            buttons = buttons.map {
                val label = if (it is TextButton) it.text.toString()
                    else clean((it.children.single() as Label).text.toString())
                NativeStoryCard.Button(label, destructive = negative != null && it.style == negative,
                    enabled = !it.isDisabled, detail = details[it]?.joinToString("\n"), inline = it in inlineButtons)
            },
            inputs = inputCards,
            error = error,
        )
        return Content(card, buttons, inputs)
    }
}
