package com.unciv.ui.nativeui

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.screens.pickerscreens.ImprovementPickerScreen
import kotlin.math.roundToInt

/**
 * Native improvement choice for a worker: who owns the tile, then every improvement the unit could
 * build with its turns, yield changes and what stands in the way. Selecting one shows its
 * description under it; the primary button starts it, queuing a terrain removal first where
 * [ImprovementPickerScreen] would.
 */
class ImprovementPresenter(private val screen: ImprovementPickerScreen) : NativeFormPresenter() {
    private val tile get() = screen.tile
    private var selected: ImprovementPickerScreen.Option? = null

    override fun NativeForm.build() {
        title = "Construct improvement"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        val owner = tile.getOwner()
        section(id = "tile") {
            when {
                owner == null -> text("owner", "Unowned tile", symbol = "flag.slash")
                owner.isCurrentPlayer() ->
                    row("owner", tile.getCity()!!.name, detail = "Tile owned by [${owner.civName}] (You)",
                        icon = NativeForm.Option(owner.civName, owner.civName, icon = "NationIcons/${owner.nation.name}",
                            background = owner.nation.getOuterColor().hex(), tint = owner.nation.getInnerColor().hex())) {
                        screen.openOwningCity()
                    }
                else -> text("owner", "Tile owned by [${owner.civName}] - [${tile.getCity()!!.name}]", symbol = "flag")
            }
        }

        section("Improvements", id = "improvements") {
            for (option in screen.options) {
                val improvement = option.improvement
                val inProgress = improvement.name == tile.improvementInProgress
                val solutions = option.report.proposedSolutions.map { it.first }
                val showTurns = option.turns > 0 && improvement.name != Constants.cancelImprovementOrder
                // Translated here so the yields keep plain names; the row would turn some into glyphs
                row("improvement.${improvement.name}",
                    (if (showTurns) "[${improvement.name}] - [${option.turns}] turns" else improvement.name)
                        .tr(hideIcons = true, hideStats = true),
                    detail = (listOfNotNull(statsText(option)) + (option.notes + solutions).map { it.tr() })
                        .joinToString("\n").ifEmpty { null },
                    icon = icon(option, inProgress),
                    tag = when {
                        inProgress -> "Current construction"
                        screen.tileMarkedForCreatesOneImprovement -> null
                        option.report.isQueueable() -> "Queueable"
                        else -> null
                    },
                    tagStyle = if (inProgress) "good" else "accent",
                    selected = selected?.improvement == improvement, translate = false) { selected = option }
                if (selected?.improvement == improvement) {
                    text("improvement.${improvement.name}.description", improvement.getDescription(screen.ruleset))
                    button("improvement.${improvement.name}.civilopedia", "Civilopedia", symbol = "book") {
                        screen.openCivilopedia(improvement.makeLink())
                    }
                }
            }
        }

        val option = selected
        val canPick = option != null && !screen.tileMarkedForCreatesOneImprovement
            && option.improvement.name != tile.improvementInProgress
            && (option.report.isEmpty() || option.report.isQueueable())
        primary(if (option == null) "Pick improvement" else "Construct [${option.improvement.name}]",
            enabled = canPick, symbol = "hammer.fill") {
            if (!canPick || option == null) return@primary
            if (option.report.isEmpty()) screen.accept(option.improvement)
            else screen.accept(option.report.removalImprovement, option.improvement)
        }
    }

    /** Yield changes, e.g. `+1 Food, -1 Gold` */
    private fun statsText(option: ImprovementPickerScreen.Option): String? =
        option.stats.mapNotNull { (stat, value) ->
            val rounded = (value * 10).roundToInt() / 10f
            if (rounded == 0f) return@mapNotNull null
            val number = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
            (if (rounded > 0) "+" else "") + number + " " + stat.name.tr(hideIcons = true, hideStats = true)
        }.joinToString(", ").ifEmpty { null }

    private fun icon(option: ImprovementPickerScreen.Option, inProgress: Boolean): NativeForm.Option {
        val name = option.improvement.name
        val available = option.report.isEmpty() || option.report.isQueueable()
        return NativeForm.Option(name, name, icon = "ImprovementIcons/$name",
            background = when { inProgress -> "#1f4d2c"; available -> "#1d3a52"; else -> "#1a1d24" },
            tint = if (available || inProgress) "#ffffff" else "#6b7280")
    }

    private fun com.badlogic.gdx.graphics.Color.hex() = "#" + toString().take(6)
}
