package com.unciv.ui.nativeui

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText

/**
 * A modal card of one or more text pages, shown natively in place of simple Scene2D popups
 * such as tutorials and the leader introduction. See [NativeUi.showStoryCard].
 */
class NativeStoryCard(
    val title: String,
    val pages: List<String>,
    /** Label of the button on the last page */
    val doneLabel: String,
    /** Atlas region shown next to the title, e.g. `NationIcons/Rome` */
    val icon: String? = null,
    val iconBackground: Color? = null,
    val iconTint: Color? = null,
    /** A picture shown above the text, e.g. a tutorial's ExtraImages file */
    val image: FileHandle? = null,
    val subtitle: String? = null,
    /** Choices in place of paging, for popups read by [NativePopupReader]; the platform sends their index */
    val buttons: List<Button> = emptyList(),
    /** Text fields and switches of a popup, in reading order; the platform sends edits by index */
    val inputs: List<Input> = emptyList(),
    /** A validation message, e.g. after OK with an invalid name */
    val error: String? = null,
) {
    /** @param kind `text`, `number` or `toggle` */
    class Input(val kind: String, val label: String, val value: String, val placeholder: String? = null)

    /** @param detail explains the choice, e.g. what annexing a city means */
    class Button(
        val label: String, val destructive: Boolean = false, val enabled: Boolean = true, val detail: String? = null,
        /** A small adjusting button shown in a row, e.g. "+50" next to a number field */
        val inline: Boolean = false,
    )

    fun toJson(): String = NativeUiJson().obj {
        put("title", plainText(title))
        put("subtitle", subtitle?.let { plainText(it) })
        array("pages") { for (page in pages) obj { put("text", plainText(page)) } }
        put("doneLabel", plainText(doneLabel))
        put("icon", icon)
        put("iconBackground", iconBackground?.let { "#" + it.toString().take(6) })
        put("iconTint", iconTint?.let { "#" + it.toString().take(6) })
        array("buttons") {
            for (button in buttons) obj {
                put("label", plainText(button.label))
                put("destructive", button.destructive)
                put("enabled", button.enabled)
                put("detail", button.detail?.let { plainText(it) })
                put("inline", button.inline)
            }
        }
        array("inputs") {
            for (input in inputs) obj {
                put("kind", input.kind)
                put("label", plainText(input.label))
                put("value", plainText(input.value))
                put("placeholder", input.placeholder?.let { plainText(it) })
            }
        }
        put("error", error?.let { plainText(it) })
        if (image != null) obj("image") {
            put("path", image.path())
            put("location", if (image.type() == Files.FileType.Internal) "bundle" else "local")
        }
    }.toString()
}
