package com.unciv.ui.nativeui

import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.Counter
import com.unciv.models.ruleset.BeliefType
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.screens.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.ReligiousBeliefsPickerScreen
import com.unciv.ui.screens.worldscreen.AlertPopup
import com.unciv.ui.popups.AskNumberPopup
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.view.ForeignCivView

/**
 * Opens screens that only appear late in a game, for the local simulator script
 * (`--ui-script` step `open:<name>`). Nothing here runs in normal play.
 */
object NativeScriptScreens {
    fun open(name: String) {
        val game = UncivGame.Current
        if (name == "cleanShots") {
            // Screenshots: no tips over the game
            game.settings.showTutorials = false
            game.settings.save()
            return
        }
        if (name == "mapEditor") return game.pushScreen(com.unciv.ui.screens.mapeditorscreen.MapEditorScreen())
        val world = game.screen as? WorldScreen ?: game.worldScreen ?: return
        if (name == "doubleTap" || name == "twoFingerTap") {
            // Map gestures through the real input pipeline, at the screen center
            val input = com.badlogic.gdx.Gdx.input.inputProcessor ?: return
            val x = com.badlogic.gdx.Gdx.graphics.width / 2
            val y = com.badlogic.gdx.Gdx.graphics.height / 2
            if (name == "doubleTap") repeat(2) {
                input.touchDown(x, y, 0, 0)
                input.touchUp(x, y, 0, 0)
            } else {
                input.touchDown(x - 60, y, 0, 0)
                input.touchDown(x + 60, y, 1, 0)
                input.touchUp(x + 60, y, 1, 0)
                input.touchUp(x - 60, y, 0, 0)
            }
            return
        }
        val civ = world.selectedCiv
        when {
            name == "pantheon" -> game.pushScreen(PantheonPickerScreen(civ))
            name == "religion" -> game.pushScreen(ReligiousBeliefsPickerScreen(civ,
                Counter(mapOf(BeliefType.Founder to 1, BeliefType.Follower to 1)), pickIconAndName = true))
            name == "greatPerson" -> game.pushScreen(GreatPersonPickerScreen(world, civ))
            name == "promotion" -> {
                val unit = civ.units.getCivUnits().firstOrNull { it.baseUnit.isMilitary } ?: return
                unit.promotions.XP += 40
                game.pushScreen(PromotionPickerScreen(unit))
            }
            name == "improvements" -> {
                val unit = civ.units.getCivUnits().firstOrNull { it.cache.hasUniqueToBuildImprovements } ?: return
                game.pushScreen(ImprovementPickerScreen(unit.getTile(), unit) {})
            }
            name == "conquest" -> {
                val city = civ.gameInfo.getCities().firstOrNull { it.civ != civ && it.civ.isMajorCiv() } ?: return
                AlertPopup(world, PopupAlert(AlertType.CityConquered, city.id))
            }
            name == "askText" -> AskTextPopup(world, label = "Choose a name for your religion", defaultText = "Hello",
                validate = { it.length > 3 }).open()
            name == "askNumber" -> AskNumberPopup(world, label = "Enter the amount of gold", defaultValue = 50,
                amountButtons = listOf(50, 500), bounds = 0..1000).open()
            name == "selectCity" -> {
                val city = civ.cities.firstOrNull() ?: return
                world.bottomUnitTable.citySelected(city)
                world.shouldUpdate = true
            }
            name == "autoConstruct" -> {
                // Every idle city picks what the game's automation would build
                for (city in civ.cities)
                    if (city.cityConstructions.constructionQueue.isEmpty())
                        com.unciv.logic.automation.city.ConstructionAutomation(city.cityConstructions).chooseNextConstruction()
                world.shouldUpdate = true
            }
            name == "skipIdleUnits" -> {
                // As if the player had told every idle unit to wait this turn
                for (unit in civ.units.getDueUnits().toList()) unit.due = false
                world.shouldUpdate = true
            }
            name.startsWith("autoplay:") -> {
                // Lets the AI play our side too, to reach a developed empire for screenshots
                game.settings.autoPlay.autoPlayMaxTurns = name.removePrefix("autoplay:").toIntOrNull() ?: return
                world.autoPlay.startMultiturnAutoPlay()
                world.nextTurn()
            }
            name.startsWith("trade:") -> {
                val other = civ.gameInfo.getCivilizationOrNull(name.removePrefix("trade:")) ?: return
                game.pushScreen(DiplomacyScreen(world.selectedGameView.civView, ForeignCivView(other, civ), showTrade = true))
            }
        }
    }
}
