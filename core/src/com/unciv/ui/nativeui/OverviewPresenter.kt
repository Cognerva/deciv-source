package com.unciv.ui.nativeui

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.view.CivView
import com.unciv.view.ForeignCivView
import kotlin.math.roundToInt

/**
 * Native empire overview: stats breakdowns, cities, units, resources, relations, wonders and
 * notifications, each a tab. Rows lead where the Scene2D tabs lead: a city opens its screen, a
 * unit or wonder centres the map on it, a civilization opens diplomacy.
 */
class OverviewPresenter(private val viewingPlayer: CivView, initial: EmpireOverviewCategories?) : NativeFormPresenter() {
    private val civ: Civilization get() = viewingPlayer.getCiv()
    private var tab = when (initial) {
        EmpireOverviewCategories.Cities -> "cities"
        EmpireOverviewCategories.Units -> "units"
        EmpireOverviewCategories.Resources -> "resources"
        EmpireOverviewCategories.Politics -> "politics"
        EmpireOverviewCategories.Wonders -> "wonders"
        EmpireOverviewCategories.Notifications -> "notifications"
        else -> "stats"
    }

    private fun civBadge(other: Civilization) = NativeForm.Option(
        other.civName, other.civName, icon = if (other.isCityState) "CityStateIcons/${other.cityStateType.name}" else "NationIcons/${other.nation.name}",
        background = "#" + other.nation.getOuterColor().toString().take(6),
        tint = "#" + other.nation.getInnerColor().toString().take(6),
    )

    override fun NativeForm.build() {
        title = "Empire Overview"
        subtitle = civ.civName
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        header {
            choice("tab", "", tab, listOfNotNull(
                NativeForm.Option("stats", "Stats", symbol = "chart.bar.fill"),
                if (civ.cities.isNotEmpty()) NativeForm.Option("cities", "Cities", symbol = "building.2.fill") else null,
                NativeForm.Option("units", "Units", symbol = "shield.fill"),
                NativeForm.Option("resources", "Resources", symbol = "diamond.fill"),
                if (civ.diplomacy.isNotEmpty()) NativeForm.Option("politics", "Politics", symbol = "globe.europe.africa.fill") else null,
                NativeForm.Option("wonders", "Wonders", symbol = "building.columns.fill"),
                NativeForm.Option("notifications", "Notifications", symbol = "bell.fill"),
            ), style = "chips") { tab = it }
        }

        when (tab) {
            "cities" -> cities()
            "units" -> units()
            "resources" -> resources()
            "politics" -> politics()
            "wonders" -> wonders()
            "notifications" -> notifications()
            else -> stats()
        }
    }

    // region Stats

    private fun NativeForm.stats() {
        val happiness = viewingPlayer.getHappinessBreakdown()
        section("Happiness", id = "happiness", icon = "StatIcons/Happiness") {
            for ((source, value) in happiness) if (value.roundToInt() != 0) text("happiness.$source", source, signed(value))
            text("happiness.total", "Total", signed(happiness.values.sum()))
        }
        val statMap = viewingPlayer.getStatMapForNextTurn()
        val stats = listOfNotNull(Stat.Gold, Stat.Science, Stat.Culture, if (civ.gameInfo.isReligionEnabled()) Stat.Faith else null)
        for (stat in stats) {
            section(stat.name, id = "stat.${stat.name}", icon = "StatIcons/${stat.name}") {
                var total = 0f
                for ((source, values) in statMap) {
                    total += values[stat]
                    if (values[stat].roundToInt() != 0) text("stat.${stat.name}.$source", source, signed(values[stat]))
                }
                text("stat.${stat.name}.total", "Total", signed(total))
            }
        }
        val points = viewingPlayer.getGreatPersonPointsCounter()
        if (!points.isEmpty()) section("Great person points", id = "greatPeople", expanded = true) {
            val perTurn = viewingPlayer.getGreatPersonPointsForNextTurn()
            for ((person, current) in points) {
                val needed = viewingPlayer.getPointsRequiredForGreatPerson(person)
                meter("gp.$person", person, current / needed.toFloat().coerceAtLeast(1f), "$current/$needed",
                    detail = "+${perTurn[person]} {per turn}".tr(), color = "#6b8cff")
            }
        }
        val score = viewingPlayer.calculateScoreBreakdown()
        section("Score", id = "score", expanded = false) {
            for ((label, value) in score) if (value.roundToInt() != 0) text("score.$label", label, value.roundToInt().toString())
            text("score.total", "Total", score.values.sum().roundToInt().toString())
        }
    }

    private fun signed(value: Float): String {
        val rounded = value.roundToInt()
        return if (rounded > 0) "+$rounded" else rounded.toString()
    }

    // endregion
    // region Cities

    private fun NativeForm.cities() = section(id = "cities") {
        for (city in civ.cities.sortedWith(compareByDescending<com.unciv.logic.city.City> { it.isCapital() }.thenBy { it.name })) {
            val stats = city.cityStats.currentCityStats
            val construction = city.cityConstructions.currentConstructionName()
            val producing = if (construction.isEmpty()) "Nothing in production".tr()
                else "${construction.tr(hideIcons = true)} · ${"[${city.cityConstructions.turnsToConstruction(construction)}] turns".tr()}"
            row("city.${city.id}", city.name, translate = false,
                detail = "$producing\n" + listOf(Stat.Food, Stat.Production, Stat.Gold, Stat.Science, Stat.Culture)
                    .joinToString("  ") { "${it.name.tr()} ${stats[it].roundToInt()}" },
                value = city.population.population.toString(),
                icon = NativeForm.Option(city.name, city.name, symbol = if (city.isCapital()) "star.fill" else "building.2.fill",
                    background = "#" + civ.nation.getOuterColor().toString().take(6),
                    tint = "#" + civ.nation.getInnerColor().toString().take(6)),
                tag = when {
                    city.isPuppet -> "Puppet"
                    city.isBeingRazed -> "Razing"
                    city.isInResistance() -> "In resistance"
                    else -> null
                }, tagStyle = "bad") {
                UncivGame.Current.pushScreen(CityScreen(GUI.getWorldScreen().selectedGameView.getCityView(city)))
            }
        }
    }

    // endregion
    // region Units

    private fun NativeForm.units() {
        val units = civ.units.getCivUnits().toList()
        if (units.isEmpty()) {
            section(id = "units") { text("units.none", "No units") }
            return
        }
        for ((type, group) in units.groupBy { it.baseUnit.unitType }.toSortedMap()) {
            section(type, id = "units.$type", expanded = true) {
                for (unit in group.sortedBy { it.displayName() }) {
                    val status = when {
                        unit.isFortified() -> "Fortified"
                        unit.isSleeping() -> "Sleeping"
                        unit.isAutomated() -> "Automated"
                        unit.isExploring() -> "Exploring"
                        unit.isMoving() -> "Moving"
                        unit.due && unit.isIdle() -> "Idle"
                        else -> null
                    }
                    row("unit.${unit.id}", unit.displayName(),
                        detail = "${unit.currentMovement.roundToInt()}/${unit.getMaxMovement()} · ${unit.health} HP",
                        icon = NativeForm.Option(unit.name, unit.name, icon = "UnitIcons/${unit.baseUnit.name}",
                            background = "#" + civ.nation.getOuterColor().toString().take(6),
                            tint = "#" + civ.nation.getInnerColor().toString().take(6)),
                        tag = status, tagStyle = if (status == "Idle") "accent" else "muted") {
                        GUI.resetToWorldScreen()
                        GUI.getMap().setCenterPosition(unit.currentTile.position, forceSelectUnit = unit)
                    }
                }
            }
        }
    }

    // endregion
    // region Resources

    private fun NativeForm.resources() {
        val amounts = civ.getCivResourcesByName()
        val ruleset = civ.gameInfo.ruleset
        for (type in listOf(ResourceType.Luxury, ResourceType.Strategic)) {
            val owned = ruleset.tileResources.values.filter { it.resourceType == type && (amounts[it.name] ?: 0) != 0 }
            section(type.name, id = "resources.${type.name}") {
                if (owned.isEmpty()) text("resources.${type.name}.none", "None yet")
                for (resource in owned.sortedBy { it.name.tr(hideIcons = true) }) {
                    val amount = amounts[resource.name] ?: 0
                    row("resource.${resource.name}", resource.name, value = amount.toString(),
                        icon = NativeForm.Option(resource.name, resource.name, icon = "ResourceIcons/${resource.name}"),
                        tag = if (amount < 0) "Shortage" else null, tagStyle = "bad")
                }
            }
        }
    }

    // endregion
    // region Politics

    private fun NativeForm.politics() {
        val known = civ.getKnownCivs().filter { it != civ && !it.isBarbarian && !it.isSpectator() }.toList()
        for ((title, civs) in listOf("Civilizations" to known.filter { it.isMajorCiv() }, "City-States" to known.filter { it.isCityState })) {
            if (civs.isEmpty()) continue
            section(title, id = "politics.$title") {
                for (other in civs.sortedBy { it.civName }) {
                    val diplomacy = civ.getDiplomacyManager(other) ?: continue
                    val status = diplomacy.diplomaticStatus
                    val level = diplomacy.relationshipLevel()
                    row("civ.${other.civName}", other.civName, icon = civBadge(other),
                        detail = if (other.isDefeated()) "Defeated" else level.name,
                        tag = if (other.isDefeated()) null else status.name,
                        tagStyle = when {
                            status == DiplomaticStatus.War -> "bad"
                            level >= RelationshipLevel.Friend -> "good"
                            else -> "muted"
                        }) {
                        UncivGame.Current.pushScreen(DiplomacyScreen(viewingPlayer, ForeignCivView(other, civ)))
                    }
                }
            }
        }
    }

    // endregion
    // region Wonders

    private fun NativeForm.wonders() {
        val built = civ.cities.flatMap { city ->
            city.cityConstructions.getBuiltBuildings().filter { it.isAnyWonder() }.map { it to city }
        }
        section("Wonders", id = "wonders.built") {
            if (built.isEmpty()) text("wonders.none", "No wonders built yet")
            for ((wonder, city) in built.sortedBy { it.first.name.tr(hideIcons = true) }) {
                row("wonder.${wonder.name}", wonder.name, detail = city.name,
                    icon = NativeForm.Option(wonder.name, wonder.name, icon = "BuildingIcons/${wonder.name}")) {
                    val worldScreen = UncivGame.Current.resetToWorldScreen()
                    worldScreen.mapHolder.setCenterPosition(city.location)
                }
            }
        }
        if (civ.naturalWonders.isNotEmpty()) section("Natural Wonders", id = "wonders.natural") {
            for (name in civ.naturalWonders.sortedBy { it.tr(hideIcons = true) })
                row("natural.$name", name, icon = NativeForm.Option(name, name, icon = "TerrainIcons/$name"))
        }
    }

    // endregion
    // region Notifications

    private fun NativeForm.notifications() {
        if (civ.notifications.isEmpty() && civ.notificationsLog.isEmpty()) {
            section(id = "notifications") { text("notifications.none", "No notifications") }
            return
        }
        if (civ.notifications.isNotEmpty())
            section("Current turn", id = "notifications.current") { notificationRows("current", civ.notifications) }
        for (log in civ.notificationsLog.sortedByDescending { it.turn }) {
            if (log.notifications.isEmpty()) continue
            section("{Turn} ${log.turn}",
                id = "notifications.${log.turn}", expanded = false) { notificationRows("log${log.turn}", log.notifications) }
        }
    }

    private fun NativeForm.Section.notificationRows(prefix: String, notifications: List<Notification>) {
        for ((index, notification) in notifications.asReversed().withIndex()) {
            val iconName = notification.icons.firstOrNull()
            val icon = when {
                iconName == null -> NativeForm.Option("", "", symbol = "bell.fill")
                '/' in iconName -> NativeForm.Option(iconName, iconName, icon = iconName)
                else -> civ.gameInfo.ruleset.nations[iconName]?.let { nation ->
                    NativeForm.Option(iconName, iconName, icon = "NationIcons/$iconName",
                        background = "#" + nation.getOuterColor().toString().take(6),
                        tint = "#" + nation.getInnerColor().toString().take(6))
                } ?: NativeForm.Option("", "", symbol = "bell.fill")
            }
            row("note.$prefix.$index", notification.text, icon = icon,
                action = if (notification.actions.isEmpty()) null else {
                    {
                        val worldScreen = UncivGame.Current.resetToWorldScreen()
                        notification.resetExecuteRoundRobin()
                        notification.execute(worldScreen)
                    }
                })
        }
    }

    // endregion
}
