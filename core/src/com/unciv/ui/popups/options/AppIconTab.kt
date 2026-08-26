package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.support.SupportPopup

/** The iOS app-icon picker. Alternate icons are an optional supporter extra. */
internal class AppIconTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {
    private data class AlternateIcon(
        val name: String,
        val systemName: String,
        val preview: String,
        val label: String
    )

    private val alternateIcons = listOf(
        AlternateIcon("Rosette", "rosette", "Icons/Preview-Rosette.png", "Rosette"),
        AlternateIcon("Rosette D", "rosette-d", "Icons/Preview-Rosette-D.png", "Rosette D"),
        AlternateIcon("Knockout", "knockout", "Icons/Preview-Knockout.png", "Knockout"),
        AlternateIcon("Sphinx Night", "sphinx-night", "Icons/Preview-Sphinx-Night.png", "Sphinx (night)"),
        AlternateIcon("Pyramid Night", "pyramid-night", "Icons/Preview-Pyramid-Night.png", "Pyramid (night)"),
        AlternateIcon("Forum Parchment", "forum-parchment", "Icons/Preview-Forum-Parchment.png", "Forum (parchment)"),
        AlternateIcon("Monument Parchment", "monument-parchment", "Icons/Preview-Monument-Parchment.png", "Monument (parchment)")
    )

    init {
        addHeader("App Icon")
        add("Choose the DeCiv icon shown on your Home Screen. The Monolith icon is included for everyone; alternate icons are a supporter extra.".toLabel())
            .colspan(2).fillX().row()

        addIconOption(
            label = "Monolith (default)",
            preview = "Icons/Preview-Monolith.png",
            systemName = null,
            locked = false
        )

        addHeader("Supporter icons")
        for (icon in alternateIcons) {
            addIconOption(icon.label, icon.preview, icon.systemName, locked = !settings.supporterUnlocked)
        }
    }

    private fun addIconOption(label: String, preview: String, systemName: String?, locked: Boolean) {
        val row = Table()
        row.defaults().pad(5f)
        row.add(ImageGetter.getExternalImage(preview)).size(72f)

        val selected = currentIconName() == systemName
        val caption = buildString {
            append(label)
            if (selected) append(" — selected")
            if (locked) append(" — supporter")
        }
        val button = caption.toTextButton()
        button.onActivation {
            if (locked) {
                SupportPopup(optionsPopup.baseScreen).open(force = true)
                return@onActivation
            }
            game.setAlternateAppIcon(systemName) { success ->
                if (!success) {
                    val detail = game.getLastAlternateAppIconError()
                    ToastPopup(
                        if (detail.isNullOrBlank()) "This icon could not be selected"
                        else "This icon could not be selected: $detail",
                        optionsPopup.baseScreen
                    )
                    return@setAlternateAppIcon
                }
                settings.selectedAlternateAppIcon = systemName
                settings.save()
                ToastPopup("App icon updated", optionsPopup.baseScreen)
                reopenOptions(force = true)
            }
        }
        row.add(button).growX().left()
        if (locked) row.add(ImageGetter.getImage("OtherIcons/LockSmall").apply { color = Color.LIGHT_GRAY }).size(24f)
        add(row).colspan(2).fillX().row()
    }

    private fun currentIconName(): String? = game.getAlternateAppIconName() ?: settings.selectedAlternateAppIcon
}
