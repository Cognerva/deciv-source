package com.unciv.ui.nativeui

import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText
import com.unciv.ui.objectdescriptions.TechnologyDescriptions
import com.unciv.ui.screens.pickerscreens.TechPickerScreen

/**
 * Native counterpart of [TechPickerScreen]. The screen stays the navigation entry and keeps its
 * selection and confirmation logic (research path, queueing, free techs); this presenter only
 * reads that state and forwards taps to it.
 */
class TechTreePresenter(private val screen: TechPickerScreen) {

    fun snapshotJson(): String {
        val civ = screen.civInfo
        val techManager = civ.tech
        val ruleset = civ.gameInfo.ruleset
        val queue = screen.tempTechsToResearch
        val techs = ruleset.technologies.values.filter { it.column != null }

        return NativeUiJson().obj {
            put("freeTechPick", screen.freeTechPick)
            put("science", civ.stats.statsForNextTurn.science.toInt())

            array("eras") {
                val columnsByEra = techs.groupBy { it.era() }
                    .mapValues { (_, list) -> list.map { it.column!!.columnNumber } }
                for ((era, columns) in columnsByEra.entries.sortedBy { it.value.min() }) obj {
                    put("name", plainText(era.tr()))
                    put("firstColumn", columns.min())
                    put("lastColumn", columns.max())
                }
            }

            array("techs") {
                for (tech in techs) obj {
                    val name = tech.name
                    val researched = techManager.isResearched(name) && name != Constants.futureTech
                    put("id", name)
                    put("name", plainText(name.tr(hideIcons = true)))
                    put("icon", "TechIcons/$name")
                    put("column", tech.column!!.columnNumber)
                    put("row", tech.row)
                    put("state", when {
                        researched -> "researched"
                        queue.firstOrNull() == name && !screen.freeTechPick -> "current"
                        name in screen.researchableTechs -> "available"
                        name in queue -> "queued"
                        else -> "locked"
                    })
                    put("turns", if (researched) -1 else screen.turnsToTech[name]?.toIntOrNull() ?: -1)
                    put("queueIndex", queue.indexOf(name) + 1)
                    val cost = techManager.costOfTech(name)
                    put("progress", if (cost > 0) techManager.researchOfTech(name) / cost.toFloat() else 0f)
                    array("prerequisites") { for (prerequisite in tech.prerequisites) obj { put("id", prerequisite) } }
                    array("unlocks") { for (icon in unlockIcons(name).take(5)) obj { put("icon", icon) } }
                }
            }

            val selected = screen.selectedTech
            if (selected != null) obj("selected") {
                put("id", selected.name)
                put("name", plainText(selected.name.tr(hideIcons = true)))
                put("icon", "TechIcons/${selected.name}")
                put("description", plainText(selected.getDescription(civ)))
                array("unlocks") { for (icon in unlockIcons(selected.name)) obj { put("icon", icon) } }
            }

            obj("pick") {
                put("label", plainText(screen.rightSideButton.text.toString()))
                put("enabled", screen.rightSideButton.isEnabled)
            }
        }.toString()
    }

    private fun unlockIcons(techName: String): List<String> {
        val civ = screen.civInfo
        val ruleset = civ.gameInfo.ruleset
        return TechnologyDescriptions.getEnabledUnits(techName, ruleset, civ).map { "UnitIcons/${it.name}" }.toList() +
            TechnologyDescriptions.getEnabledBuildings(techName, ruleset, civ).map { "BuildingIcons/${it.name}" }.toList() +
            ruleset.tileResources.values.filter { it.revealedBy == techName }.map { "ResourceIcons/${it.name}" } +
            ruleset.tileImprovements.values.filter { it.techRequired == techName && (it.uniqueTo == null || civ.matchesFilter(it.uniqueTo!!)) }
                .map { "ImprovementIcons/${it.name}" }
    }

    /** Ids: `select:<tech>`, `queue:<tech>`, `confirm`, `close`, `civilopedia:<tech>` */
    fun perform(id: String) {
        val ruleset = screen.civInfo.gameInfo.ruleset
        val argument = id.substringAfter(':', "")
        when (id.substringBefore(':')) {
            "select" -> screen.selectTechnology(ruleset.technologies[argument] ?: return)
            "queue" -> screen.selectTechnology(ruleset.technologies[argument] ?: return, queue = true)
            "confirm" -> if (screen.rightSideButton.isEnabled) screen.tryExit()
            "close" -> screen.game.popScreen()
            "civilopedia" -> screen.openCivilopedia(ruleset.technologies[argument]?.makeLink() ?: return)
        }
    }
}
