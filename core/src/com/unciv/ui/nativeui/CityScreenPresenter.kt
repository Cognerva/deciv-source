package com.unciv.ui.nativeui

import com.unciv.logic.city.CityFocus
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText
import com.unciv.ui.objectdescriptions.TileDescription
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.cityscreen.BuyButtonFactory
import com.unciv.ui.screens.cityscreen.CityScreen
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Native counterpart of the city screen's Scene2D tables: header and city picker, stats,
 * construction queue and picker, buy buttons, built buildings, citizen focus and the selected
 * tile. The city's tile map stays in Scene2D underneath. Actions call the same [CityView]
 * methods the Scene2D tables call, followed by [CityScreen.update].
 */
class CityScreenPresenter(private val screen: CityScreen) {
    private val cityView get() = screen.cityView

    fun snapshotJson(): String {
        val city = cityView
        val constructions = city.constructions
        val civColors = city.owningCiv()
        val canChange = screen.canCityBeChanged()

        return NativeUiJson().obj {
            put("name", plainText(city.name.tr(hideIcons = true)))
            put("population", city.getPopulationCount())
            put("freePopulation", city.getFreePopulation())
            put("isCapital", city.isCapital())
            put("isPuppet", city.isPuppet())
            put("isRazing", city.isBeingRazed())
            put("inResistance", city.isInResistance())
            put("canPage", screen.viewableCities.size > 1)
            put("canChange", canChange)
            put("isSpying", screen.isSpying)
            put("outerColor", civColors.getOuterColor())
            put("innerColor", civColors.getInnerColor())

            obj("growth") { putGrowth() }
            val culture = city.getCurrentCityStats().culture
            put("turnsToExpand", if (!city.hasChoosableTiles() || culture <= 0) -1
                else ceil((city.getCultureToNextTile() - city.getCultureStored()) / culture).toInt().coerceAtLeast(1))

            array("stats") {
                for (stat in Stat.entries) {
                    if (stat == Stat.Faith && !city.viewingCiv().isReligionEnabled()) continue
                    val value = if (stat == Stat.Happiness) city.getHappinessList().values.sum()
                        else city.getCurrentCityStats()[stat]
                    obj {
                        put("stat", stat.name)
                        put("value", value.roundToInt())
                    }
                }
            }

            put("focus", city.getCityFocus().name)
            put("avoidGrowth", city.avoidGrowth)
            array("focusOptions") {
                for (focus in CityFocus.entries) {
                    if (!focus.tableEnabled) continue
                    if (focus == CityFocus.FaithFocus && !city.viewingCiv().isReligionEnabled()) continue
                    obj {
                        put("id", focus.name)
                        put("label", if (focus.stat == null && focus.name in setOf("NoFocus", "Manual")) focus.label.tr() else "")
                        array("stats") { for (stat in focusStats(focus)) obj { put("stat", stat.name) } }
                    }
                }
            }

            array("queue") {
                val queue = constructions.constructionQueue
                for ((index, name) in queue.withIndex()) {
                    val construction = constructions.getConstruction(name)
                    obj {
                        put("name", plainText(name.tr(hideIcons = true)))
                        put("id", name)
                        put("icon", iconName(construction))
                        val perpetual = name in PerpetualConstruction.perpetualConstructionsMap
                        put("perpetual", perpetual)
                        put("turns", if (perpetual) -1 else turnsTo(name, constructions.isFirstConstructionOfItsKind(index, name)))
                        put("progress", progress(construction))
                        put("canRaise", canChange && index > 0)
                        put("canLower", canChange && index != queue.lastIndex)
                        put("canRemove", canChange)
                    }
                }
            }

            array("categories") { putAvailableConstructions() }
            array("buildings") { putBuildings() }

            val selected = screen.selectedConstruction
            if (selected != null) obj("selectedConstruction") {
                put("id", selected.name)
                put("name", plainText(selected.name.tr(hideIcons = true)))
                put("icon", iconName(selected))
                put("description", plainText(description(selected)))
                val building = selected as? Building
                val sellable = building != null && constructions.isBuilt(building.name) && building.isSellable()
                put("sellGold", if (sellable) city.getGoldForSellingBuilding(building!!.name) else 0)
                put("canSell", sellable && !city.hasFreeBuilding(building!!) && !city.isPuppet() && screen.canChangeState
                    && (!city.hasSoldBuildingThisTurn() || city.isGodModeEnabled()))
            }

            val tile = screen.selectedTile
            if (tile != null) obj("selectedTile") {
                val lines = TileDescription.toMarkup(tile, city.viewingCiv())
                    .map { plainText(it.text.tr()) }.filter { it.isNotBlank() }
                put("title", lines.firstOrNull() ?: "")
                array("lines") { for (line in lines.drop(1).take(5)) obj { put("text", line) } }
                putStats(tile.getTileStats(city.viewingCiv(), city))
                put("isWorked", city.isWorked(tile))
                put("isLocked", tile.isLocked())
                val canBuy = screen.canChangeState && city.canBuyTile(tile)
                put("canBuy", canBuy)
                val cost = if (canBuy) city.getGoldCostOfTile(tile) else 0
                put("buyCost", cost)
                put("canAffordBuy", canBuy && city.viewingCiv().hasStatToBuy(Stat.Gold, cost))
                put("canLock", canChange && city.isWorked(tile))
            }
        }.toString()
    }

    private fun NativeUiJson.putGrowth() {
        val city = cityView
        val turns = when {
            city.isStarving() -> city.getNumTurnsToStarvation()
            city.isGrowing() -> city.getNumTurnsToNewPopulation()
            else -> null
        }
        put("starving", city.isStarving())
        put("growing", city.isGrowing())
        put("turns", turns ?: -1)
        put("foodStored", city.getFoodStored())
        put("foodNeeded", city.getFoodToNextPopulation())
    }

    private fun NativeUiJson.putAvailableConstructions() {
        val city = cityView
        val constructions = city.constructions
        city.updateTileStats()
        val ruleset = city.getRuleset()
        val all: List<IConstruction> = (ruleset.units.values + ruleset.buildings.values)
            .filter { constructions.shouldBeDisplayed(it) } +
            PerpetualConstruction.perpetualConstructionsMap.values.filter { constructions.shouldBeDisplayed(it) }
        val disabled = city.getDisabledConstructions()

        val groups = linkedMapOf<String, MutableList<IConstruction>>(
            "Units" to mutableListOf(), "Buildings" to mutableListOf(), "Wonders" to mutableListOf(),
            "National Wonders" to mutableListOf(), "Other" to mutableListOf(), "Disabled" to mutableListOf()
        )
        for (construction in all) {
            val group = when {
                construction.name in disabled -> "Disabled"
                construction is BaseUnit -> "Units"
                construction is Building && construction.isWonder -> "Wonders"
                construction is Building && construction.isNationalWonder -> "National Wonders"
                construction is Building -> "Buildings"
                else -> "Other"
            }
            groups.getValue(group).add(construction)
        }

        val generalAddAllowed = screen.canChangeState && !city.isPuppet() && !constructions.isQueueFull()
        for ((title, items) in groups) {
            if (items.isEmpty()) continue
            obj {
                put("title", title.tr())
                put("id", title)
                array("items") {
                    for (construction in items) obj {
                        put("id", construction.name)
                        put("name", plainText(construction.name.tr(hideIcons = true)))
                        put("icon", iconName(construction))
                        val perpetual = construction is PerpetualConstruction
                        put("perpetual", perpetual)
                        if (perpetual) {
                            put("turns", -1)
                            put("cost", 0)
                            put("rejection", null)
                        } else {
                            val nonPerpetual = construction as INonPerpetualConstruction
                            val useStored = construction is Building || !constructions.isBeingConstructedOrEnqueued(construction.name)
                            put("turns", turnsTo(construction.name, useStored))
                            put("cost", city.getConstructionProductionCost(nonPerpetual))
                            val rejection = constructions.getRejectionReasons(nonPerpetual)
                                .filter { it.isImportantRejection() }
                                .minByOrNull { it.getRejectionPrecedence() }
                            put("rejection", rejection?.takeIf { it.type != RejectionReasonType.ConsumesResources }
                                ?.let { plainText(it.errorMessage.tr()) })
                        }
                        if (construction is Building) {
                            array("statHints") {
                                for (stat in Stat.entries.filter { city.isStatRelated(it, construction) })
                                    obj { put("stat", stat.name) }
                            }
                        }
                        val canAdd = generalAddAllowed && constructions.isBuildable(construction)
                            && !(perpetual && constructions.isBeingConstructedOrEnqueued(construction.name))
                        put("canAdd", canAdd)
                        array("buy") { putBuyOptions(construction) }
                    }
                }
            }
        }
    }

    private fun NativeUiJson.putBuyOptions(construction: IConstruction) {
        val nonPerpetual = construction as? INonPerpetualConstruction ?: return
        val city = cityView
        for (stat in Stat.statsUsableToBuy) {
            if (!city.canBePurchasedWithStat(nonPerpetual, stat)) continue
            val cost = city.constructions.getStatBuyCost(nonPerpetual, stat) ?: continue
            obj {
                put("stat", stat.name)
                put("cost", cost)
                put("enabled", screen.canChangeState && city.constructions.isConstructionPurchaseAllowed(nonPerpetual, stat, cost))
            }
        }
    }

    private fun NativeUiJson.putBuildings() {
        val city = cityView
        val groups = city.getBuiltBuildings().groupBy {
            when {
                it.isAnyWonder() -> "Wonders"
                it.newSpecialists().isNotEmpty() -> "Specialist Buildings"
                else -> "Other"
            }
        }
        for (title in listOf("Wonders", "Specialist Buildings", "Other")) {
            val buildings = groups[title] ?: continue
            obj {
                put("title", title.tr())
                array("items") {
                    for (building in buildings.sortedBy { it.name.tr(hideIcons = true) }) obj {
                        put("id", building.name)
                        put("name", plainText(building.name.tr(hideIcons = true)))
                        put("icon", "BuildingIcons/${building.name}")
                        put("free", city.hasFreeBuilding(building))
                        putStats(city.getBuildingStats(building))
                    }
                }
            }
        }
    }

    private fun NativeUiJson.putStats(stats: Stats) {
        array("stats") {
            for ((stat, value) in stats) {
                if (value == 0f) continue
                obj {
                    put("stat", stat.name)
                    put("value", value.roundToInt())
                }
            }
        }
    }

    private fun focusStats(focus: CityFocus): List<Stat> = when (focus) {
        CityFocus.GoldGrowthFocus -> listOf(Stat.Gold, Stat.Food)
        CityFocus.ProductionGrowthFocus -> listOf(Stat.Production, Stat.Food)
        else -> listOfNotNull(focus.stat)
    }

    private fun turnsTo(name: String, useStoredProduction: Boolean): Int = try {
        cityView.getCity().cityConstructions.turnsToConstruction(name, useStoredProduction)
    } catch (_: Exception) {
        -1
    }

    private fun progress(construction: IConstruction): Float {
        if (construction !is INonPerpetualConstruction) return 0f
        val done = cityView.constructions.getWorkDone(construction.name)
        if (done == 0) return 0f
        return done / cityView.getConstructionProductionCost(construction).toFloat()
    }

    private fun description(construction: IConstruction): String = when (construction) {
        is BaseUnit -> cityView.getUnitDescription(construction)
        is Building -> cityView.getBuildingDescription(construction)
        is PerpetualConstruction.StatConversion -> construction.description.replace("[rate]", "[${cityView.getConversionRate(construction)}]").tr()
        is PerpetualConstruction -> construction.description.tr()
        else -> ""
    }

    /**
     * Ids: `exit`, `prev`, `next`, `add:<name>`, `addFirst:<name>`, `remove:<index>`,
     * `raise:<index>`, `lower:<index>`, `buy:<name>:<Stat>`, `select:<name>`, `deselect`,
     * `sell:<name>`, `focus:<CityFocus>`, `reset`, `avoidGrowth`, `tile:buy`, `tile:lock`,
     * `tile:unlock`.
     */
    fun perform(id: String) {
        val city = cityView
        val constructions = city.constructions
        val argument = id.substringAfter(':', "")
        when (id.substringBefore(':')) {
            "exit" -> { screen.exit(); return }
            "prev" -> { screen.page(-1); return }
            "next" -> { screen.page(1); return }
            "add", "addFirst" -> {
                val construction = constructions.getConstruction(argument)
                if (!screen.canChangeState || city.isPuppet() || constructions.isQueueFull()
                    || !constructions.isBuildable(construction)) return
                if (construction is Building && construction.hasCreateOneImprovementUnique()) {
                    screen.startPickTileForCreatesOneImprovement(construction, Stat.Gold, false)
                    return
                }
                if (id.startsWith("addFirst")) city.tryAddToQueueConstruction(construction, addToTop = true)
                else city.tryAddToQueue(construction.name)
                city.tryReassignPopulation()
            }
            "remove" -> {
                val index = argument.toIntOrNull() ?: return
                city.tryRemoveFromQueue(index, false)
                city.tryReassignPopulation()
            }
            "raise" -> city.tryRaisePriority(argument.toIntOrNull() ?: return)?.let { city.tryReassignPopulation() }
            "lower" -> city.tryLowerPriority(argument.toIntOrNull() ?: return)?.let { city.tryReassignPopulation() }
            "buy" -> {
                val construction = constructions.getConstruction(argument.substringBeforeLast(':'))
                    as? INonPerpetualConstruction ?: return
                val stat = Stat.safeValueOf(argument.substringAfterLast(':')) ?: return
                if (construction is Building && construction.hasCreateOneImprovementUnique()) {
                    screen.startPickTileForCreatesOneImprovement(construction, stat, true)
                    return
                }
                BuyButtonFactory(screen).askToBuyConstruction(construction, stat)
                return
            }
            "select" -> screen.selectConstruction(argument)
            "deselect" -> screen.clearSelection()
            "sell" -> {
                val building = constructions.getConstruction(argument) as? Building ?: return
                ConfirmPopup(screen, "Are you sure you want to sell this [${building.name}]?".tr(), "Sell") {
                    city.trySellBuilding(building)
                    screen.clearSelection()
                    screen.update()
                }.open()
                return
            }
            "focus" -> {
                val focus = CityFocus.entries.firstOrNull { it.name == argument } ?: return
                city.trySetCityFocus(if (focus == city.getCityFocus()) CityFocus.NoFocus else focus)
            }
            "reset" -> city.tryReassignPopulation(resetLocked = true)
            "avoidGrowth" -> city.tryToggleAvoidGrowth()
            "tile" -> {
                val tile = screen.selectedTile ?: return
                when (argument) {
                    "buy" -> { screen.askToBuyTile(tile); return }
                    "lock" -> city.tryLockTile(tile)
                    "unlock" -> city.tryUnlockTile(tile)
                }
            }
            else -> return
        }
        screen.update()
    }

    companion object {
        fun iconName(construction: IConstruction): String = when (construction) {
            is BaseUnit -> "UnitIcons/${construction.name}"
            is Building -> "BuildingIcons/${construction.name}"
            is PerpetualConstruction -> "OtherIcons/Convert${construction.name}"
            else -> "StatIcons/${construction.name}"
        }
    }
}
