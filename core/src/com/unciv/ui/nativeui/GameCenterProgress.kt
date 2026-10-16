package com.unciv.ui.nativeui

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.ui.screens.victoryscreen.RankingType

/**
 * What a single-player game reports to Game Center: achievements as they are earned, and the
 * leaderboard scores once the human player has won. Games with mods, several human players or a
 * spectator don't count, so the leaderboards compare like with like. The ids match the
 * leaderboards and achievements set up in App Store Connect.
 */
object GameCenterProgress {
    interface Platform {
        fun authenticate()
        fun submitScore(score: Int, leaderboard: String)
        fun unlock(achievement: String)
        fun showDashboard()
    }

    var platform: Platform? = null

    const val BEST_SCORE = "deciv.leaderboard.score"
    const val SKIRMISH_SCORE = "deciv.leaderboard.skirmish"
    const val FASTEST_VICTORY = "deciv.leaderboard.fastest_victory"

    /** Victory type name to achievement id */
    private val victoryAchievements = mapOf(
        "Domination" to "deciv.achievement.victory_domination",
        "Scientific" to "deciv.achievement.victory_science",
        "Cultural" to "deciv.achievement.victory_culture",
        "Diplomatic" to "deciv.achievement.victory_diplomatic",
        "Time" to "deciv.achievement.victory_time",
    )
    private val hardDifficulties = setOf("Immortal", "Deity")

    /** Games whose victory scores were already submitted, so a reloaded game doesn't submit twice */
    private val submittedGames = HashSet<String>()

    fun authenticate() = platform?.authenticate()

    private fun counts(gameInfo: GameInfo): Boolean {
        val parameters = gameInfo.gameParameters
        return parameters.mods.isEmpty() && !parameters.isOnlineMultiplayer &&
            gameInfo.civilizations.count { it.playerType == PlayerType.Human } == 1 &&
            gameInfo.getCurrentPlayerCivilization().isHuman() && !gameInfo.getCurrentPlayerCivilization().isSpectator()
    }

    /** Called after every world screen update; cheap enough for that */
    fun update(gameInfo: GameInfo) {
        val platform = platform ?: return
        if (!counts(gameInfo)) return
        val civ = gameInfo.getCurrentPlayerCivilization()

        if (civ.cities.isNotEmpty()) platform.unlock("deciv.achievement.first_city")
        if (civ.goldenAges.isGoldenAge()) platform.unlock("deciv.achievement.golden_age")
        if (civ.religionManager.religionState >= ReligionState.Religion) platform.unlock("deciv.achievement.religion")
        val wonders = civ.cities.sumOf { city -> city.cityConstructions.getBuiltBuildings().count { it.isWonder } }
        if (wonders >= 5) platform.unlock("deciv.achievement.wonders")

        val victory = gameInfo.victoryData ?: return
        if (victory.winningCiv != civ.civName || !submittedGames.add(gameInfo.gameId)) return
        platform.unlock("deciv.achievement.first_victory")
        victoryAchievements[victory.victoryType]?.let(platform::unlock)
        if (gameInfo.difficulty in hardDifficulties) platform.unlock("deciv.achievement.hard_victory")

        val score = civ.getStatForRanking(RankingType.Score)
        val skirmish = gameInfo.gameParameters.maxTurns == NewGamePresenter.SKIRMISH_TURNS
        if (skirmish) {
            platform.unlock("deciv.achievement.skirmish_victory")
            platform.submitScore(score, SKIRMISH_SCORE)
        } else {
            platform.submitScore(score, BEST_SCORE)
            platform.submitScore(victory.victoryTurn, FASTEST_VICTORY)
        }
    }
}
