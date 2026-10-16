package com.unciv.ui.nativeui

import com.unciv.UncivGame
import com.unciv.ui.components.input.activate
import com.unciv.models.UnitAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText
import com.unciv.ui.objectdescriptions.TileDescription
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMusicPopup
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.canUndo
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.restoreUndoCheckpoint
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import kotlin.math.ceil

/**
 * Native counterpart of the world screen's top bar, tech/policy/diplomacy buttons, next-turn
 * button and unit actions table. It reads the same state those widgets read and routes actions
 * to the same handlers, so the Scene2D widgets can be hidden without changing game behaviour.
 */
class WorldHudPresenter(private val worldScreen: WorldScreen) {
    /** Buttons of the battle preview in the last snapshot, by index */
    private var battleButtons: List<com.badlogic.gdx.scenes.scene2d.ui.TextButton> = emptyList()
    /** Notifications in the last snapshot, by index */
    private var notifications: List<com.unciv.logic.civilization.Notification> = emptyList()

    /** Unit actions offered in the last snapshot; native buttons refer to them by index */
    private var unitActions: List<UnitAction> = emptyList()
    /** Bumped whenever [unitActions] is rebuilt, so a tap on a stale button is ignored */
    private var revision = 0

    fun snapshotJson(): String {
        val civ = worldScreen.selectedCiv
        val civView = worldScreen.selectedGameView.civView
        val gameInfo = worldScreen.gameInfo
        val stats = civ.stats.statsForNextTurn
        val nextTurnButton = worldScreen.nextTurnButton
        val nextTurnAction = nextTurnButton.currentAction

        val unit = worldScreen.bottomUnitTable.selectedUnit?.getUnit()
        unitActions = if (unit == null || !worldScreen.canChangeState) emptyList()
            else UnitActions.getUnitActions(unit).sortedByDescending { it.useFrequency }.toList()
        revision++

        return NativeUiJson().obj {
            put("revision", revision)
            put("visible", worldScreen.isUiEnabled)
            put("civName", plainText(civ.civName.tr(hideIcons = true)))
            put("turn", gameInfo.turns)
            // A short game shows its limit, e.g. a skirmish's "Turn 12/50"
            val timeLimited = gameInfo.gameParameters.victoryTypes.any { gameInfo.ruleset.victories[it]?.enablesMaxTurns() == true }
            if (timeLimited && gameInfo.gameParameters.maxTurns <= 200) put("turnLimit", gameInfo.gameParameters.maxTurns)
            put("year", plainText(YearTextUtil.toYearText(gameInfo.getYear(), civ.isLongCountDisplay())))

            obj("stats") {
                put("gold", civ.gold)
                put("goldPerTurn", stats.gold.toInt())
                put("science", stats.science.toInt())
                put("happiness", civ.getHappiness())
                put("goldenAge", civ.goldenAges.isGoldenAge())
                put("culture", stats.culture.toInt())
                val cultureNeeded = civ.policies.getCultureNeededForNextPolicy() - civ.policies.storedCulture
                put("turnsToPolicy", when {
                    cultureNeeded <= 0 -> 0
                    stats.culture <= 0 -> -1
                    else -> ceil(cultureNeeded / stats.culture).toInt()
                })
                put("religionEnabled", gameInfo.isReligionEnabled())
                put("faith", civ.religionManager.storedFaith)
                put("faithPerTurn", stats.faith.toInt())
            }

            val hasResearchUi = gameInfo.ruleset.technologies.isNotEmpty() && civView.cities().isNotEmpty()
            val currentTech = civView.currentTechnologyName()
            obj("research") {
                put("available", hasResearchUi && (currentTech != null || civView.canResearchTech() || civView.hasResearchedAnyTech()))
                put("name", currentTech?.let { plainText(it.tr(true)) })
                put("icon", currentTech?.let { "TechIcons/$it" })
                put("turns", currentTech?.let { plainText(civView.turnsToTech(it)) })
                put("needsChoice", currentTech == null && civView.canResearchTech())
            }
            put("canUndo", worldScreen.canUndo())
            put("showPolicies", civView.hasAdoptedPolicies() || civView.canAdoptPolicy())
            put("showDiplomacy", !(civView.isDefeated() || civView.isSpectator() || !civView.hasMetAnyMajorCiv()))

            obj("nextTurn") {
                put("action", nextTurnAction.name)
                put("title", plainText(nextTurnAction.getText(worldScreen).tr()))
                put("subtitle", nextTurnAction.getSubText(worldScreen)?.let { plainText(it.tr()) })
                put("enabled", nextTurnButton.isEnabled)
                put("color", nextTurnAction.color)
            }

            if (unit != null) obj("unit") {
                val base = unit.baseUnit
                put("name", plainText(unit.displayName().tr(hideIcons = true)))
                put("icon", "UnitIcons/${base.name}")
                put("outerColor", unit.civ.nation.getOuterColor())
                put("innerColor", unit.civ.nation.getInnerColor())
                put("health", unit.health)
                put("movement", plainText(unit.getMovementString()))
                put("strength", if (unit.isCivilian()) 0 else base.strength)
                put("rangedStrength", base.rangedStrength)
                put("range", if (base.isRanged()) unit.getRange() else 0)
                val promotions = unit.promotions
                put("xp", if (unit.isCivilian()) -1 else promotions.XP)
                put("xpNext", promotions.xpForNextPromotion())
                put("canPromote", worldScreen.canChangeState && promotions.canBePromoted())
                array("promotions") {
                    for (promotion in promotions.getPromotions(true).take(8)) obj {
                        put("name", plainText(promotion.name.tr(hideIcons = true)))
                        put("icon", "UnitPromotionIcons/" + promotion.name.substringBefore(" (").replace(Regex(" [IV]+$"), ""))
                    }
                }
            }
            val city = if (unit == null) worldScreen.bottomUnitTable.selectedCity else null
            if (city != null) obj("city") {
                put("name", plainText(city.name.tr(hideIcons = true)))
                put("outerColor", city.getCity().civ.nation.getOuterColor())
                put("innerColor", city.getCity().civ.nation.getInnerColor())
                put("health", city.getHealth())
                put("maxHealth", city.getMaxHealth())
                put("strength", city.getDefendingStrength())
                put("bombard", city.getAttackingStrength())
                put("population", city.getCity().population.population)
                put("canEnter", city.tryGetCityView() != null)
            }
            val tutorialTask = if (worldScreen.game.settings.showTutorials && !civ.isDefeated())
                worldScreen.getCurrentTutorialTask() else null
            val tutorialLines = tutorialTask?.civilopediaText.orEmpty()
                .map { plainText(it.text.tr()) }.filter { it.isNotBlank() }
            if (tutorialTask != null && tutorialLines.isNotEmpty()) obj("tutorial") {
                put("title", tutorialLines.first())
                put("text", tutorialLines.drop(1).joinToString("\n"))
                put("collapsed", UncivGame.Current.isTutorialTaskCollapsed)
            }
            obj("menu") {
                put("showSave", !gameInfo.gameParameters.isOnlineMultiplayer)
                put("showMusic", worldScreen.game.musicController.isMusicAvailable())
                put("showDiplomacy", !(civView.isDefeated() || civView.isSpectator() || !civView.hasMetAnyMajorCiv()))
            }
            put("idleUnits", civView.idleUnitsCount(due = true))
            put("hasIdleUnits", civView.hasIdleUnits())

            val selectedTile = worldScreen.mapHolder.selectedTile
            val viewCiv = worldScreen.getGameViewConsideringForOfWar().civView
            if (selectedTile != null && viewCiv.getCiv().hasExplored(selectedTile.getTile())) obj("tile") {
                val lines = TileDescription.toMarkup(selectedTile, viewCiv)
                    .map { plainText(it.text.tr()) }.filter { it.isNotBlank() }
                put("title", lines.firstOrNull() ?: "")
                array("lines") { for (line in lines.drop(1).take(4)) obj { put("text", line) } }
                array("stats") {
                    for ((stat, value) in selectedTile.getTile().stats.getTileStats(viewCiv.getCiv())) {
                        if (value == 0f) continue
                        obj {
                            put("stat", stat.name)
                            put("value", value.toInt())
                        }
                    }
                }
            }
            array("unitActions") {
                for (action in unitActions) obj {
                    put("type", action.type.name)
                    put("title", plainText(action.title.tr()))
                    put("enabled", action.action != null)
                    put("current", action.isCurrentAction)
                }
            }

            battle()
            notificationsJson()
            put("topFraction", worldScreen.nativeHudTopFraction)
            put("bottomFraction", worldScreen.nativeHudBottomFraction)
        }.toString()
    }

    /**
     * The battle preview, read from the hidden Scene2D [com.unciv.ui.screens.worldscreen.bottombar.BattleTable]:
     * its attacker and defender columns line by line, and its attack button. All combat maths stay there.
     */
    private fun NativeUiJson.battle() {
        val table = worldScreen.battleTable
        val buttons = ArrayList<com.badlogic.gdx.scenes.scene2d.ui.TextButton>()
        battleButtons = buttons
        if (!worldScreen.nativeBattleShown) return
        val rows = LinkedHashMap<Int, Array<String?>>()
        for (cell in table.cells) {
            val actor = cell.actor ?: continue
            val entries = NativePopupReader.readEntries(actor)
            val text = entries.filterIsInstance<NativePopupReader.Entry.Text>().joinToString(" ") { plainText(it.text) }
            for (action in entries.filterIsInstance<NativePopupReader.Entry.Action>()) buttons += action.button
            if (text.isBlank()) continue
            val row = rows.getOrPut(cell.row) { arrayOfNulls(2) }
            val column = if ((cell.colspan ?: 1) > 1) 0 else cell.column.coerceIn(0, 1)
            row[column] = text
        }
        obj("battle") {
            array("rows") {
                for (row in rows.values) obj {
                    put("left", row[0])
                    put("right", row[1])
                }
            }
            array("buttons") {
                for (button in buttons) obj {
                    put("label", plainText(button.text.toString()))
                    put("enabled", !button.isDisabled)
                }
            }
        }
    }

    private fun NativeUiJson.notificationsJson() {
        val civ = worldScreen.selectedGameView.civView.getCiv()
        notifications = civ.notifications.asReversed().toList()
        array("notifications") {
            for (notification in notifications) obj {
                put("text", plainText(notification.text.tr()))
                val icon = notification.icons.firstOrNull()
                val nation = icon?.takeIf { '/' !in it }?.let { civ.gameInfo.ruleset.nations[it] }
                put("icon", when {
                    icon == null -> null
                    nation != null -> "NationIcons/$icon"
                    '/' in icon -> icon
                    else -> null
                })
                put("background", nation?.let { "#" + it.getOuterColor().toString().take(6) })
                put("tint", nation?.let { "#" + it.getInnerColor().toString().take(6) })
                put("category", notification.category.name)
                put("actionable", notification.actions.isNotEmpty())
            }
        }
    }

    /**
     * Perform a native action id. Ids are `nextTurn`, `menu`, `overview`, `research`,
     * `policies`, `diplomacy`, `undo`, `stat:<Name>`, `unit:prev|next|close|promote`,
     * `city:enter`, `minimap:*` (see [MinimapPresenter.perform]) and `action:<revision>:<index>`, plus `layout:<top>:<bottom>`, which reports
     * how much of the screen's top and bottom right the native chrome covers.
     */
    fun perform(id: String) {
        val game = worldScreen.game
        if (id.startsWith("layout:")) {
            val parts = id.removePrefix("layout:").split(':').map { it.toFloatOrNull() ?: return }
            worldScreen.nativeHudTopFraction = parts[0].coerceIn(0f, 0.5f)
            if (parts.size > 1) worldScreen.nativeHudBottomFraction = parts[1].coerceIn(0f, 0.6f)
            worldScreen.shouldUpdate = true
            return
        }
        if (id.startsWith("minimap:")) {
            worldScreen.nativeMinimap.perform(id.removePrefix("minimap:"))
            return
        }
        when {
            id.startsWith("battle:") -> battleButtons.getOrNull(id.removePrefix("battle:").toIntOrNull() ?: return)?.activate()
            id.startsWith("note:") -> notifications.getOrNull(id.removePrefix("note:").toIntOrNull() ?: return)?.let {
                it.execute(worldScreen)
            }
            id == "nextTurn" -> worldScreen.nextTurnButton.activate()
            id == "menu" -> WorldScreenMenuPopup(worldScreen)
            id == "overview" -> worldScreen.openEmpireOverview()
            id == "research" -> game.pushScreen(TechPickerScreen(worldScreen.selectedGameView.civView.getCiv()))
            id == "policies" -> game.pushScreen(PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState))
            id == "diplomacy" -> game.pushScreen(DiplomacyScreen(worldScreen.selectedGameView.civView))
            id == "undo" -> if (worldScreen.canUndo()) worldScreen.restoreUndoCheckpoint()
            id == "stat:Gold" -> worldScreen.openEmpireOverview(EmpireOverviewCategories.Stats)
            id == "stat:Science" -> game.pushScreen(TechPickerScreen(worldScreen.selectedCiv))
            id == "stat:Happiness" -> worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
            id == "stat:Culture" -> if (worldScreen.gameInfo.ruleset.policyBranches.isNotEmpty())
                game.pushScreen(PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState))
            id == "stat:Faith" -> worldScreen.openEmpireOverview(EmpireOverviewCategories.Religion)
            id == "menu:opened" -> worldScreen.autoPlay.stopAutoPlay()
            id == "menu:mainMenu" -> game.goToMainMenu()
            id == "menu:civilopedia" -> worldScreen.openCivilopedia()
            id == "menu:save" -> if (!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer) worldScreen.openSaveGameScreen()
            id == "menu:load" -> game.pushScreen(LoadGameScreen())
            id == "menu:newGame" -> worldScreen.openNewGameScreen()
            id == "menu:victory" -> game.pushScreen(VictoryScreen(worldScreen))
            id == "menu:options" -> worldScreen.openOptionsPopup()
            id == "menu:music" -> WorldScreenMusicPopup(worldScreen).open(force = true)
            id == "tutorial:off" -> {
                game.settings.showTutorials = false
                game.settings.save()
                game.getNativeUi()?.showToast("Tips are off. Turn them back on in Options.".tr(), 3500)
            }
            id == "tutorial:toggle" ->
                UncivGame.Current.isTutorialTaskCollapsed = !UncivGame.Current.isTutorialTaskCollapsed
            id == "unit:prev" -> selectIdleUnit(previous = true)
            id == "unit:next" -> selectIdleUnit(previous = false)
            id == "unit:close" -> worldScreen.bottomUnitTable.selectUnit()
            id == "unit:promote" -> worldScreen.bottomUnitTable.selectedUnit?.getUnit()
                ?.let { game.pushScreen(PromotionPickerScreen(it)) }
            id == "city:enter" -> worldScreen.bottomUnitTable.selectedCity?.tryGetCityView()
                ?.let { game.pushScreen(CityScreen(it)) }
            id.startsWith("action:") -> performUnitAction(id)
        }
        worldScreen.shouldUpdate = true
    }

    /** Same cycling as the Scene2D [com.unciv.ui.screens.worldscreen.unit.IdleUnitButton] */
    private fun selectIdleUnit(previous: Boolean) {
        val idleUnits = worldScreen.selectedGameView.civView.getCiv().units.getIdleUnits().toList()
        if (idleUnits.isEmpty()) return
        val selected = worldScreen.bottomUnitTable.selectedUnit?.getUnit()
        val index = idleUnits.indexOf(selected)
        val next = if (index < 0) idleUnits.first()
            else idleUnits[(index + (if (previous) -1 else 1) + idleUnits.size) % idleUnits.size]
        worldScreen.mapHolder.setCenterPosition(next.currentTile.position)
        worldScreen.bottomUnitTable.selectUnit(worldScreen.selectedGameView.getForeignMapUnitView(next).tryGetMapUnitView()!!)
    }

    private fun performUnitAction(id: String) {
        // action:<revision>:<index>:<type>. Snapshots are rebuilt often (layout reports, animations),
        // so the type check, not the revision, guards against acting on a different list.
        val parts = id.split(':')
        if (parts.size != 4) return
        val action = unitActions.getOrNull(parts[2].toIntOrNull() ?: return) ?: return
        if (action.type.name != parts[3]) return
        val unit = worldScreen.bottomUnitTable.selectedUnit?.getUnit() ?: return
        if (action.action == null) return
        worldScreen.activateUnitAction(action, unit)
    }
}
