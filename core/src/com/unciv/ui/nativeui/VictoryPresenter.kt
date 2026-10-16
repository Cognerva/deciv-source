package com.unciv.ui.nativeui

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.ui.screens.victoryscreen.ReplayMap
import com.unciv.logic.map.tile.TileHistory
import com.unciv.ui.components.YearTextUtil
import com.badlogic.gdx.graphics.Color
import kotlin.math.roundToInt
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Native victory status: our progress on each victory type, how every civilization stands, and
 * the rankings. When the game is over it announces the winner and offers a new game or one more
 * turn. Charts and the replay are drawn natively too; demographics and illustrations stay in the Scene2D screen.
 */
class VictoryPresenter(private val worldScreen: WorldScreen) : NativeFormPresenter() {
    private val gameInfo get() = worldScreen.gameInfo
    private val player: Civilization get() = worldScreen.selectedGameView.civView.getCiv()
    private var tab = "ours"

    /** A civilization's badge, hidden as "unknown" until we have met it */
    private fun civBadge(civ: Civilization): NativeForm.Option {
        val known = player.knows(civ) || player == civ || civ.isDefeated() || player.isDefeated() || player.isSpectator()
        return when {
            civ.isDefeated() -> NativeForm.Option(civ.civName, civ.civName, symbol = "xmark", background = "#2a2d34", tint = "#9ca3af")
            known -> NativeForm.Option(civ.civName, civ.civName, icon = "NationIcons/${civ.nation.name}",
                background = "#" + civ.nation.getOuterColor().toString().take(6),
                tint = "#" + civ.nation.getInnerColor().toString().take(6))
            else -> NativeForm.Option(Constants.unknownNationName, Constants.unknownNationName, symbol = "questionmark")
        }
    }

    private fun civName(civ: Civilization) =
        if (player.knows(civ) || player == civ || civ.isDefeated() || player.isDefeated()) civ.civName else Constants.unknownNationName

    override fun NativeForm.build() {
        title = "Victory status"
        subtitle = "{Difficulty}: {${gameInfo.difficulty}} · {Game Speed}: {${gameInfo.gameParameters.speed}}"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        gameOver()

        val visible = VictoryScreen.visibleTabs(player)
        header {
            choice("tab", "", tab, listOfNotNull(
                if ("OurStatus" in visible) NativeForm.Option("ours", "Our status") else null,
                NativeForm.Option("global", "Global status"),
                if ("Demographics" in visible) NativeForm.Option("demographics", "Demographics") else null,
                if ("Rankings" in visible) NativeForm.Option("rankings", "Rankings") else null,
                if ("Charts" in visible) NativeForm.Option("charts", "Charts") else null,
                if ("Replay" in visible) NativeForm.Option("replay", "Replay") else null,
            ), style = if (visible.size > 3) "chips" else "segmented") { tab = it }
        }

        when (tab) {
            "global" -> global()
            "rankings" -> rankings()
            "demographics" -> demographics()
            "charts" -> charts()
            "replay" -> replay()
            else -> if (player.isSpectator()) global() else ours()
        }
    }

    private fun NativeForm.gameOver() {
        val victoryData = gameInfo.victoryData
        val message = when {
            victoryData != null -> {
                val victory = gameInfo.ruleset.victories[victoryData.victoryType] ?: Victory()
                if (victoryData.winningCivObject.civID == player.civID)
                    "{You have won a [${victoryData.victoryType}] Victory!}\n{${victory.victoryString}}"
                else "{[${victoryData.winningCivObject.civName}] has won a [${victoryData.victoryType}] Victory!}\n{${victory.defeatString}}"
            }
            player.isDefeated() -> Victory().defeatString
            else -> return
        }
        section(id = "gameOver") {
            text("gameOver.message", message, symbol = "trophy.fill")
            button("gameOver.oneMore", "One more turn...!", symbol = "arrow.uturn.backward") {
                gameInfo.oneMoreTurnMode = true
                UncivGame.Current.popScreen()
            }
        }
        primary("Start new game", symbol = "plus") {
            val setup = GameSetupInfo(gameInfo)
            setup.mapParameters.reseed()
            UncivGame.Current.pushScreen(NewGameScreen(setup))
        }
    }

    private fun NativeForm.ours() {
        for ((name, victory) in gameInfo.getEnabledVictories()) {
            section("[$name] Victory", id = "ours.$name", footer = victory.victoryScreenHeader) {
                var firstIncomplete = true
                for ((index, milestone) in victory.milestoneObjects.withIndex()) {
                    val status = when {
                        milestone.hasBeenCompletedBy(player) -> Victory.CompletionStatus.Completed
                        firstIncomplete -> { firstIncomplete = false; Victory.CompletionStatus.Partially }
                        else -> Victory.CompletionStatus.Incomplete
                    }
                    val done = status == Victory.CompletionStatus.Completed
                    // Milestone buttons carry the step texts; the first is the milestone itself
                    val steps = milestone.getVictoryScreenButtons(status, player).map { it.text.toString() }
                    row("ours.$name.$index", milestone.getVictoryScreenButtonHeaderText(done, player),
                        icon = NativeForm.Option("", "", symbol = if (done) "checkmark.circle.fill" else if (status == Victory.CompletionStatus.Partially) "circle.lefthalf.filled" else "circle",
                            tint = if (done) "#73cc61" else if (status == Victory.CompletionStatus.Partially) "#e8a84d" else "#6b7280"),
                        tag = if (done) "Completed" else if (status == Victory.CompletionStatus.Partially) "In progress" else null,
                        tagStyle = if (done) "good" else "accent")
                    for ((stepIndex, step) in steps.drop(1).withIndex())
                        row("ours.$name.$index.$stepIndex", step, translate = false,
                            icon = NativeForm.Option("", "", symbol = "arrow.turn.down.right", tint = "#8b93a1"))
                }
            }
        }
    }

    private fun NativeForm.global() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        for ((name, victory) in gameInfo.getEnabledVictories()) {
            section("[$name] Victory", id = "global.$name") {
                val sorted = majorCivs.sortedWith(
                    compareBy<Civilization> { it.isDefeated() }
                        .thenByDescending { it.victoryManager.amountMilestonesCompleted(victory) })
                for (civ in sorted) {
                    val next = civ.victoryManager.getNextMilestone(victory)
                        ?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
                    val completed = civ.victoryManager.amountMilestonesCompleted(victory)
                    row("global.$name.${civ.civName}", civName(civ), detail = next, icon = civBadge(civ),
                        value = "$completed/${victory.milestoneObjects.size}", selected = civ == player)
                }
            }
        }
    }

    private var chartType = RankingType.Score
    private var chartCiv: Civilization? = null

    /** The same lines as [com.unciv.ui.screens.victoryscreen.VictoryScreenCharts], one per civilization */
    private fun NativeForm.charts() {
        val civs = gameInfo.civilizations.filter { it.isMajorCiv() }
            .filter { VictoryScreen.canViewCivStats(gameInfo, player, it) }
        val highlighted = chartCiv ?: player
        val sorted = civs.map { VictoryScreen.CivWithStat(it, chartType) }
            .sortedWith(compareByDescending<VictoryScreen.CivWithStat> { if (it.civ.isDefeated()) Int.MIN_VALUE else it.value }
                .thenBy { it.civ.civName })

        section(id = "chart") {
            choice("chart.type", "Category", chartType.name,
                RankingType.filteredEntries(gameInfo.gameParameters).map { NativeForm.Option(it.name, it.label) }) { picked ->
                chartType = RankingType.entries.firstOrNull { it.name == picked } ?: RankingType.Score
            }
            chart("chart.lines", sorted.map { entry ->
                val civ = entry.civ
                val points = civ.statsHistory.entries
                    .filter { it.value.containsKey(chartType) }
                    .map { it.key to it.value.getValue(chartType) }
                    .sortedBy { it.first }
                    .toMutableList()
                // History is recorded at the end of each turn; add the live value for this one
                if (!civ.isDefeated()) {
                    points.removeAll { it.first == gameInfo.turns }
                    points += gameInfo.turns to civ.getStatForRanking(chartType)
                }
                NativeForm.ChartSeries(civName(civ),
                    if (knowsColor(civ)) "#" + civ.nation.getOuterColor().toString().take(6) else "#8b93a1",
                    points, civ == highlighted)
            })
        }
        section("Civilizations", id = "chart.civs") {
            for ((place, entry) in sorted.withIndex()) {
                row("chart.civ.${entry.civ.civName}", civName(entry.civ), icon = civBadge(entry.civ),
                    detail = "#${place + 1}", value = if (entry.civ.isDefeated()) "" else entry.value.tr(),
                    selected = entry.civ == highlighted) { chartCiv = entry.civ }
            }
        }
    }

    private fun knowsColor(civ: Civilization) = player.isSpectator() || player.isDefeated() ||
        player.victoryManager.hasWon() || player == civ || player.knows(civ) || civ.isDefeated()

    /** Built once: walking every tile's history is too slow to repeat for each snapshot */
    private val replayData by lazy { buildReplay() }

    private class ReplayData(
        val width: Float, val height: Float, val tileSize: Float, val palette: Collection<String>,
        val tiles: FloatArray, val owners: IntArray, val changes: IntArray, val firstTurn: Int, val years: List<String>,
    )

    private fun buildReplay(): ReplayData {
        // The Scene2D replay map lays out the explored tiles; only their positions are used
        val map = ReplayMap(gameInfo.tileMap, player, 1000f, 1000f)
        val minimapTiles = map.tilesForNative
        val palette = LinkedHashMap<String, Int>()
        fun colorIndex(color: Color) = palette.getOrPut("#" + color.toString().take(6)) { palette.size }
        val ownerIndex = LinkedHashMap<String, Int>()
        val owners = ArrayList<Int>()
        fun owner(civName: String?): Int {
            val civ = civName?.let { gameInfo.getCivilizationOrNull(it) } ?: return -1
            return ownerIndex.getOrPut(civ.civID) {
                owners += colorIndex(civ.nation.getOuterColor())
                owners += colorIndex(civ.nation.getInnerColor())
                ownerIndex.size
            }
        }

        val tiles = FloatArray(minimapTiles.size * 3)
        val changes = ArrayList<Int>()
        for ((index, minimapTile) in minimapTiles.withIndex()) {
            val tile = minimapTile.tile
            val image = minimapTile.image
            tiles[index * 3] = image.x + image.width / 2
            tiles[index * 3 + 1] = image.y + image.height / 2
            tiles[index * 3 + 2] = colorIndex(tile.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)).toFloat()
            if (tile.isWater) continue
            for ((turn, state) in tile.history) {
                changes += turn
                changes += index
                changes += owner(state.owningCivName)
                changes += when (state.cityCenterType) {
                    TileHistory.TileHistoryState.CityCenterType.None -> 0
                    TileHistory.TileHistoryState.CityCenterType.Regular -> 1
                    TileHistory.TileHistoryState.CityCenterType.Capital -> 2
                }
            }
        }
        val firstTurn = gameInfo.historyStartTurn
        val years = (firstTurn..gameInfo.turns).map { turn ->
            YearTextUtil.toYearText(gameInfo.getYear(turn - gameInfo.turns), gameInfo.currentPlayerCiv.isLongCountDisplay())
        }
        return ReplayData(map.width, map.height, minimapTiles.firstOrNull()?.image?.width ?: 0f,
            palette.keys, tiles, owners.toIntArray(), changes.toIntArray(), firstTurn, years)
    }

    private fun NativeForm.replay() {
        val data = replayData
        section(id = "replay") {
            replay("replay.map", data.width, data.height, data.tileSize, data.palette,
                data.tiles, data.owners, data.changes, data.firstTurn, data.years)
        }
    }

    /** Like [com.unciv.ui.screens.victoryscreen.VictoryScreenDemographics]: our rank and value against the best, average and worst */
    private fun NativeForm.demographics() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() && (it.isAlive() || it == player) }
        val ranked = player.isMajorCiv() && !player.isDefeated()
        for (category in RankingType.filteredEntries(gameInfo.gameParameters)) {
            // Turn-start snapshots, so mid-turn changes can't be used to probe other civilizations
            fun value(civ: Civilization): Int {
                val snapshot = civ.statsHistory[gameInfo.turns] ?: civ.statsHistory.maxByOrNull { it.key }?.value
                return snapshot?.get(category) ?: civ.getStatForRanking(category)
            }
            val sorted = majorCivs.map { it to value(it) }.sortedByDescending { it.second }
            if (sorted.isEmpty()) continue
            val (best, bestValue) = sorted.first()
            val (worst, worstValue) = sorted.last()
            val average = (sorted.sumOf { it.second }.toFloat() / sorted.size).roundToInt()
            section(category.label, id = "demo.${category.name}") {
                if (ranked) {
                    val place = sorted.indexOfFirst { it.first == player } + 1
                    row("demo.${category.name}.ours", civName(player), icon = civBadge(player),
                        detail = "#$place", value = value(player).tr(), selected = true)
                }
                row("demo.${category.name}.best", "Best", detail = civName(best), icon = civBadge(best), value = bestValue.tr())
                text("demo.${category.name}.average", "Average", average.tr())
                row("demo.${category.name}.worst", "Worst", detail = civName(worst), icon = civBadge(worst), value = worstValue.tr())
            }
        }
    }

    private fun NativeForm.rankings() {
        val civs = gameInfo.civilizations.filter { it.isMajorCiv() }
            .filter { VictoryScreen.canViewCivStats(gameInfo, player, it) }
        for (category in RankingType.filteredEntries(gameInfo.gameParameters)) {
            section(category.label, id = "rank.${category.name}", expanded = category == RankingType.Score) {
                val entries = civs.map { VictoryScreen.CivWithStat(it, category) }
                    .sortedBy { it.civ.civName }
                    .sortedByDescending { if (it.civ.isDefeated()) Int.MIN_VALUE else it.value }
                for ((place, entry) in entries.withIndex()) {
                    row("rank.${category.name}.${entry.civ.civName}", civName(entry.civ), icon = civBadge(entry.civ),
                        detail = "#${place + 1}", value = if (entry.civ.isDefeated()) "" else entry.value.tr(),
                        selected = entry.civ == player)
                }
            }
        }
    }
}
