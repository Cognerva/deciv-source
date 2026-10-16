package com.unciv.ui.nativeui

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.MultiplayerGameNameChanged
import com.unciv.logic.multiplayer.MultiplayerGameUpdateEnded
import com.unciv.logic.multiplayer.MultiplayerGameUpdateStarted
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.ui.components.input.activate
import com.unciv.ui.screens.multiplayerscreens.MultiplayerHelpers
import com.unciv.ui.screens.multiplayerscreens.MultiplayerScreen
import com.unciv.utils.Concurrency

/**
 * Native multiplayer games list. The games, their status and the actions come from
 * [MultiplayerScreen], which keeps running hidden; its buttons are pressed on tap, so joining,
 * resigning, renaming and deleting (with their confirmations) behave as before.
 */
class MultiplayerPresenter(private val screen: MultiplayerScreen) : NativeFormPresenter() {
    private val events = EventBus.EventReceiver()
    private val refreshing = HashSet<String>()

    init {
        events.receive(MultiplayerGameUpdateStarted::class) { refreshing += it.name; refresh() }
        events.receive(MultiplayerGameUpdateEnded::class) { refreshing -= it.name; refresh() }
        events.receive(MultiplayerGameNameChanged::class) { refresh() }
    }

    private fun refresh() {
        val presenter = this
        Concurrency.runOnGLThread {
            if (UncivGame.Current.screen === screen) UncivGame.Current.getNativeUi()?.refresh(presenter)
        }
    }

    override fun NativeForm.build() {
        title = "Multiplayer"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        val selected = screen.selectedGame
        if (selected != null) section(selected.name, id = "selected") {
            text("selected.description", MultiplayerHelpers.buildDescriptionText(selected).trim())
            val negative = BaseScreen.skin.get("negative", TextButton.TextButtonStyle::class.java)
            for ((index, button) in screen.nativeGameActions.withIndex()) {
                if (!button.isVisible) continue
                button("game.$index", button.text.toString(),
                    role = when {
                        index == 0 -> "primary"
                        button.style == negative -> "destructive"
                        else -> "default"
                    },
                    enabled = !button.isDisabled) { button.activate() }
            }
        }

        val games = UncivGame.Current.onlineMultiplayer.games
            .sortedWith(compareByDescending<com.unciv.logic.multiplayer.MultiplayerGamePreview> { it.preview?.isUsersTurn() == true }
                .thenBy { it.name })
        section("Games", id = "games",
            footer = "The symbol of your nation will appear next to the game when it's your turn") {
            if (games.isEmpty()) text("games.empty", "To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.")
            for (game in games) {
                val preview = game.preview
                val ourTurn = preview?.isUsersTurn() == true
                row("game.${game.name}", game.name, translate = false,
                    detail = preview?.currentPlayer?.let { "{Current Turn:} {$it}" },
                    icon = when {
                        game.error != null -> NativeForm.Option("", "", symbol = "exclamationmark.triangle.fill", tint = "#f26154")
                        ourTurn -> NativeForm.Option("", "", symbol = "flag.fill", tint = "#e8a84d")
                        else -> NativeForm.Option("", "", symbol = "hourglass", tint = "#8b93a1")
                    },
                    tag = when {
                        game.name in refreshing -> "Refreshing"
                        game.error != null -> "Error"
                        ourTurn -> "Your turn"
                        else -> null
                    },
                    tagStyle = when {
                        game.error != null -> "bad"
                        ourTurn -> "good"
                        else -> "muted"
                    },
                    selected = game == selected) { screen.selectGameNatively(game.name) }
            }
        }

        section("Multiplayer", id = "general",
            footer = "Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.") {
            for ((index, button) in screen.nativeGeneralActions.withIndex())
                button("general.$index", button.text.toString(), enabled = !button.isDisabled) { button.activate() }
        }
    }
}
