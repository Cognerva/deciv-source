package com.unciv.ui.nativeui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.ui.components.input.activate
import com.unciv.ui.screens.pickerscreens.PickerScreen

/**
 * A native form read from a Scene2D panel on every snapshot: labels, text fields, checkboxes,
 * select boxes, sliders and text buttons, in reading order. Edits go into the Scene2D widgets
 * and taps press the original buttons, so the panel's own logic runs unchanged.
 */
abstract class MirroredFormPresenter : NativeFormPresenter() {
    protected abstract val screenTitle: String
    protected open val help: String? = null
    protected abstract fun root(): Actor
    protected abstract fun close()
    /** The prominent action at the bottom, if any */
    protected open fun primaryButton(): TextButton? = null

    override fun NativeForm.build() {
        title = screenTitle
        closeSymbol = "chevron.left"
        onClose { close() }

        val entries = NativePopupReader.readEntries(root())
        section(id = "content", footer = help) {
            // A label right before a control names the control
            var pendingLabel: String? = null
            fun label(fallback: String) = (pendingLabel ?: fallback).also { pendingLabel = null }
            for ((index, entry) in entries.withIndex()) when (entry) {
                is NativePopupReader.Entry.Text -> {
                    val next = entries.getOrNull(index + 1)
                    if (next is NativePopupReader.Entry.Field || next is NativePopupReader.Entry.Select || next is NativePopupReader.Entry.Slide)
                        pendingLabel = entry.text
                    else row("text.$index", entry.text, translate = false)
                }
                is NativePopupReader.Entry.Field -> {
                    val field = entry.field
                    field(id = "field.$index", label = label(field.messageText ?: ""), value = field.text,
                        placeholder = field.messageText) { value ->
                        field.text = value
                        field.fire(ChangeListener.ChangeEvent())
                    }
                }
                is NativePopupReader.Entry.Toggle ->
                    toggle("toggle.$index", entry.checkBox.text.toString(), entry.checkBox.isChecked) {
                        entry.checkBox.isChecked = it
                    }
                is NativePopupReader.Entry.Select -> {
                    val box = entry.selectBox
                    val items = box.items.map { it.toString() }
                    choice("select.$index", label(""), box.selectedIndex.toString(),
                        items.mapIndexed { i, text -> NativeForm.Option(i.toString(), text) }) { picked ->
                        picked.toIntOrNull()?.let { box.selectedIndex = it }
                    }
                }
                is NativePopupReader.Entry.Slide -> {
                    val slider = entry.slider
                    slider("slider.$index", label(""), slider.value, slider.minValue, slider.maxValue, slider.stepSize,
                        format = if (slider.stepSize < 1f) "decimal" else "int") { slider.value = it }
                }
                is NativePopupReader.Entry.Action ->
                    button("button.$index", entry.button.text.toString(),
                        role = if (entry.destructive) "destructive" else "default",
                        enabled = !entry.button.isDisabled) { entry.button.activate() }
            }
        }

        val action = primaryButton()
        if (action != null && action.isVisible && action.stage != null)
            primary(action.text.toString(), enabled = !action.isDisabled) { action.activate() }
    }
}

/**
 * A simple [PickerScreen] as a native form, e.g. adding a multiplayer game by its ID or editing a
 * friend: its top table, its Close button and its bottom action button.
 */
class MirroredPickerPresenter(
    private val screen: PickerScreen,
    override val screenTitle: String,
    override val help: String? = null,
) : MirroredFormPresenter() {
    override fun root(): Actor = screen.topTable
    override fun close() { screen.closeButton.activate() }
    override fun primaryButton() = screen.rightSideButton
}
