package com.unciv.ui.nativeui

import com.unciv.models.metadata.GameParameters

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Native new game setup. Short-session presets come first, then the player's civilization and
 * difficulty, the game and map, the rival civilizations, and advanced rules behind a disclosure.
 *
 * It edits the same [com.unciv.models.metadata.GameSetupInfo] as the Scene2D tables and starts
 * the game through [NewGameScreen.startGameNatively]. Online multiplayer, scenarios, custom maps
 * and extension mods stay with the classic screen for now.
 */
class NewGamePresenter(private val screen: NewGameScreen) : NativeFormPresenter() {
    private val setup get() = screen.gameSetupInfo
    private val parameters get() = setup.gameParameters
    private val map get() = setup.mapParameters
    private val ruleset get() = screen.ruleset

    private var busy: String? = null
    private var error: String? = null

    /**
     * @param era starting era, null for the ruleset's first
     * @param victories null allows every victory; a turn limit ends the game with a score victory
     */
    private class Preset(
        val id: String, val label: String, val detail: String, val symbol: String, val tint: String,
        val mapSize: String, val speed: String, val rivals: Int, val cityStates: Int,
        val era: String? = null, val victories: List<String>? = null, val turnLimit: Int? = null,
    )

    private val presets = listOf(
        // About ten minutes: one rival on a tiny map, a head start, and the best score after 50 turns wins
        Preset("skirmish", "Skirmish", "Tiny map, 1 rival, 50 turns. About 10 minutes.", "timer", "#73cc61",
            MapSize.Tiny.name, "Quick", 1, 2, era = "Medieval era", victories = listOf("Domination", "Time"), turnLimit = SKIRMISH_TURNS),
        Preset("quick", "Quick match", "Small map, 3 rivals, quick pace. About 45 minutes.", "bolt.fill", "#f2b43c",
            MapSize.Small.name, "Quick", 3, 4),
        Preset("standard", "Standard", "Medium map, 5 rivals, standard pace.", "hexagon.fill", "#6cb5ff",
            MapSize.Medium.name, "Standard", 5, 6),
        Preset("epic", "Grand campaign", "Large map, 7 rivals, epic pace.", "crown.fill", "#cc8cfa",
            MapSize.Large.name, "Epic", 7, 10),
    )

    override fun NativeForm.build() {
        screen.useGeneratedMap()
        title = "Start new game"
        closeSymbol = "chevron.left"
        onClose { screen.closeNatively() }
        busy = this@NewGamePresenter.busy
        error?.let { message -> alert("Could not start the game", message) { error = null } }

        header {
            tiles("preset", currentPreset()?.id ?: "", presets.filter { it.speed in ruleset.speeds }.map {
                NativeForm.Option(it.id, it.label, it.detail, symbol = it.symbol, tint = it.tint)
            }) { id -> presets.firstOrNull { it.id == id }?.let(::applyPreset) }
        }

        catalog("nations", nationOptions())

        section("Your civilization") {
            val human = parameters.players.firstOrNull { it.playerType == PlayerType.Human }
            if (human != null) {
                choice("civ", "Civilization", human.chosenCiv, style = "list", catalog = "nations",
                    icon = nationOption(human.chosenCiv).icon) { chooseNation(human, it) }
            }
            choice("difficulty", "Difficulty", parameters.difficulty,
                ruleset.difficulties.keys.map { NativeForm.Option(it, it) }) { parameters.difficulty = it }
        }

        section("Game") {
            choice("speed", "Game Speed", parameters.speed, ruleset.speeds.keys.map { NativeForm.Option(it, it) }) {
                parameters.speed = it
            }
            if (ruleset.eras.isNotEmpty() && !ruleset.modOptions.hasUnique(UniqueType.CanOnlyStartFromStartingEra)) {
                choice("era", "Starting Era", parameters.startingEra, ruleset.eras.keys.map { NativeForm.Option(it, it) }) {
                    parameters.startingEra = it
                }
            }
            multi("victory", "Victory Conditions", parameters.victoryTypes,
                ruleset.victories.keys.map { NativeForm.Option(it, it) }) { victory ->
                if (!parameters.victoryTypes.remove(victory)) parameters.victoryTypes.add(victory)
            }
            if (parameters.victoryTypes.any { ruleset.victories[it]?.enablesMaxTurns() == true }) {
                slider("maxTurns", "Max Turns", parameters.maxTurns.toFloat(), 30f, 1500f, 5f) {
                    parameters.maxTurns = it.toInt()
                }
            }
        }

        section("Map") {
            choice("mapType", "Map Generation Type", map.type, MapType.allValues.map { NativeForm.Option(it, it) }, style = "list") {
                map.type = it
            }
            choice("mapSize", "World Size", map.mapSize.name, MapSize.names().map { NativeForm.Option(it, it) }) {
                map.mapSize = MapSize(it)
            }
            choice("mapShape", "Map Shape", map.shape, MapShape.allValues.map { NativeForm.Option(it, it) }) {
                map.shape = it
            }
            choice("resources", "Resource Setting", map.mapResources,
                MapResourceSetting.activeLabels().map { NativeForm.Option(it, it) }) { map.mapResources = it }
            toggle("worldWrap", "World Wrap", map.worldWrap) { map.worldWrap = it }
        }

        section("Civilizations", footer = "Random rivals are drawn from the nations no one picked.") {
            val humans = parameters.players.count { it.playerType == PlayerType.Human }
            for ((index, player) in parameters.players.withIndex()) {
                val isHuman = player.playerType == PlayerType.Human
                player("player.$index", nationOption(player.chosenCiv), player.playerType.name, isHuman, "nations",
                    canChangeType = !isHuman || humans > 1 || player.chosenCiv != Constants.spectator,
                    canRemove = parameters.players.size > 2 && !(isHuman && humans == 1),
                    onNation = { chooseNation(player, it) },
                    onType = {
                        if (isHuman && humans == 1) return@player
                        player.playerType = if (isHuman) PlayerType.AI else PlayerType.Human
                    },
                    onRemove = { parameters.players.remove(player) })
            }
            if (parameters.players.size < playableNations().size) {
                button("addPlayer", "Add a rival", symbol = "plus") { parameters.players.add(Player()) }
            }
            val cityStatesAvailable = cityStateCount()
            if (cityStatesAvailable > 0 && !parameters.randomNumberOfCityStates) {
                stepper("cityStates", "City-States", parameters.numberOfCityStates.coerceAtMost(cityStatesAvailable),
                    0, cityStatesAvailable) { parameters.numberOfCityStates = it }
            }
        }

        section("Advanced Settings", expanded = false, id = "advanced") {
            val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
            if (sortedBaseRulesets.size > 1) {
                choice("baseRuleset", "Base Ruleset", parameters.baseRuleset,
                    sortedBaseRulesets.map { NativeForm.Option(it, it) }) { changeBaseRuleset(it) }
            }
            toggle("noBarbarians", "No Barbarians", parameters.noBarbarians) { parameters.noBarbarians = it }
            toggle("ragingBarbarians", "Raging Barbarians", parameters.ragingBarbarians) { parameters.ragingBarbarians = it }
            toggle("noCityRazing", "No City Razing", parameters.noCityRazing) { parameters.noCityRazing = it }
            toggle("oneCityChallenge", "One City Challenge", parameters.oneCityChallenge) { parameters.oneCityChallenge = it }
            toggle("nuclearWeapons", "Enable Nuclear Weapons", parameters.nuclearWeaponsEnabled) { parameters.nuclearWeaponsEnabled = it }
            toggle("espionage", "Enable Espionage", parameters.espionageEnabled) { parameters.espionageEnabled = it }
            toggle("noStartBias", "Disable starting bias", parameters.noStartBias) { parameters.noStartBias = it }
            toggle("noRuins", "No Ancient Ruins", map.noRuins) { map.noRuins = it }
            toggle("noNaturalWonders", "No Natural Wonders", map.noNaturalWonders) { map.noNaturalWonders = it }
            toggle("strategicBalance", "Strategic Balance", map.strategicBalance) { map.strategicBalance = it }
            toggle("legendaryStart", "Legendary Start", map.legendaryStart) { map.legendaryStart = it }
            if (UncivGame.Current.settings.lastGameSetup != null) {
                button("reset", "Reset to defaults", symbol = "arrow.counterclockwise", role = "destructive",
                    confirm = "Are you sure you want to reset all game options to defaults?") { screen.resetToDefaults() }
            }
        }

        val humans = parameters.players.count { it.playerType == PlayerType.Human }
        primary("Start game!", enabled = humans >= 1 && parameters.players.size >= 2 && parameters.victoryTypes.isNotEmpty(),
            symbol = "play.fill") { start() }
    }

    // region Nations

    private fun playableNations() = ruleset.nations.values.filter {
        it.isMajorCiv && !it.hasUnique(UniqueType.WillNotBeChosenForNewGames)
    }

    private fun cityStateCount() = ruleset.nations.values.count {
        it.isCityState && !it.hasUnique(UniqueType.WillNotBeChosenForNewGames)
    }

    private fun Nation.option() = NativeForm.Option(
        name, name, getLeaderDisplayName().takeIf { it != name },
        icon = "NationIcons/$name",
        background = "#" + getOuterColor().toString().take(6),
        tint = "#" + getInnerColor().toString().take(6),
    )

    private fun randomOption() = NativeForm.Option(Constants.random, Constants.random, symbol = "questionmark")

    private fun nationOption(name: String) = ruleset.nations[name]?.option() ?: randomOption()

    private fun nationOptions() = listOf(randomOption()) + playableNations()
        .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr(hideIcons = true) })
        .map { it.option() }

    /** Picking a nation another player has swaps that player to Random */
    private fun chooseNation(player: Player, nation: String) {
        if (nation != Constants.random)
            parameters.players.filter { it !== player && it.chosenCiv == nation }.forEach { it.chosenCiv = Constants.random }
        player.chosenCiv = nation
    }

    // endregion
    // region Presets

    private fun aiCount() = parameters.players.count { it.playerType == PlayerType.AI }

    private fun currentPreset() = presets.firstOrNull {
        map.mapSize.name == it.mapSize && parameters.speed == it.speed && aiCount() == it.rivals &&
            parameters.numberOfCityStates == it.cityStates.coerceAtMost(cityStateCount()) &&
            (it.turnLimit == null || parameters.maxTurns == it.turnLimit)
    }

    private fun applyPreset(preset: Preset) {
        map.mapSize = MapSize(preset.mapSize)
        val firstEra = ruleset.eras.keys.firstOrNull()
        parameters.startingEra = preset.era?.takeIf { it in ruleset.eras } ?: firstEra ?: parameters.startingEra
        parameters.victoryTypes.clear()
        parameters.victoryTypes.addAll(preset.victories?.filter { it in ruleset.victories } ?: ruleset.victories.keys)
        parameters.maxTurns = preset.turnLimit ?: GameParameters().maxTurns
        if (preset.speed in ruleset.speeds) parameters.speed = preset.speed
        parameters.randomNumberOfPlayers = false
        parameters.randomNumberOfCityStates = false
        parameters.numberOfCityStates = preset.cityStates.coerceAtMost(cityStateCount())

        val rivals = preset.rivals.coerceAtMost(playableNations().size - 1)
        val players = parameters.players
        // Drop surplus AIs, random ones first, then fill up with random AIs
        while (aiCount() > rivals) {
            val surplus = players.lastOrNull { it.playerType == PlayerType.AI && it.chosenCiv == Constants.random }
                ?: players.last { it.playerType == PlayerType.AI }
            players.remove(surplus)
        }
        while (aiCount() < rivals) players.add(Player())
    }

    // endregion

    private fun changeBaseRuleset(name: String) {
        if (name == parameters.baseRuleset) return
        if (RulesetCache[name]?.getErrorList()?.isError() == true) {
            error = "The mod you selected is incorrectly defined!".tr()
            return
        }
        parameters.baseRuleset = name
        parameters.mods.clear()
        if (!screen.tryUpdateRuleset(updateUI = true)) error = "Your previous options needed to be reset to defaults.".tr()
        parameters.victoryTypes.removeAll { it !in ruleset.victories.keys }
        if (parameters.victoryTypes.isEmpty()) parameters.victoryTypes.addAll(ruleset.victories.keys)
        for (player in parameters.players)
            if (player.chosenCiv != Constants.random && player.chosenCiv !in ruleset.nations) player.chosenCiv = Constants.random
    }

    private fun start() {
        if (busy != null) return
        busy = Constants.working
        // This form has no multiplayer controls, but a saved setup can carry the flag over
        parameters.isOnlineMultiplayer = false
        Concurrency.run("NativeNewGame") {
            val message = screen.getErrorMessage()
            if (message != null) {
                launchOnGLThread {
                    busy = null
                    error = message.tr()
                    refresh()
                }
                return@run
            }
            launchOnGLThread {
                screen.startGameNatively { failure ->
                    busy = null
                    error = failure
                    refresh()
                }
            }
        }
    }

    private fun refresh() = UncivGame.Current.getNativeUi()?.refresh(this)

    companion object {
        /** Turn limit of a skirmish; also how Game Center tells a skirmish from other games */
        const val SKIRMISH_TURNS = 50
    }
}
