package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.UncivTooltip.Companion.removeTooltips
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnAction.Default
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.delay
import yairm210.purity.annotations.Readonly

class NextTurnButton(
    private val worldScreen: WorldScreen
) : IconTextButton("", null, 30) {
    private var nextTurnAction = Default
    /** The action the button currently performs; read by the native HUD */
    val currentAction get() = nextTurnAction
    /** For [GameSettings.autoEndTurn]: the turn in which something needed the player's attention */
    private var turnWithPendingWork = -1
    private var autoEndScheduled = false
    private val unitsDueLabel = Label("", BaseScreen.skin)
    private val unitsDueCell: Cell<Label>

    init {
        pad(15f)
        onActivation { activate() }
        addContextMenu { NextTurnMenu(stage, this, worldScreen) }
        keyShortcuts.add(KeyboardBinding.NextTurn)
        keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        labelCell.row()
        unitsDueCell = add(unitsDueLabel).padTop(6f).colspan(2).center()
    }

    /** Performs the current action, as a tap on the button would */
    fun activate() {
        if (isEnabled) nextTurnAction.action(worldScreen)
    }

    fun update() {
        nextTurnAction = getNextTurnAction(worldScreen)
        updateButton(nextTurnAction)
        val autoPlay = worldScreen.autoPlay
        if (autoPlay.shouldContinueAutoPlaying() && worldScreen.isPlayersTurn
            && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning()) {
            autoPlay.runAutoPlayJobInNewThread("MultiturnAutoPlay", worldScreen, false) {
                TurnManager(worldScreen.selectedGameView.civView.getCiv()).automateTurn()
                Concurrency.runOnGLThread { worldScreen.nextTurn() }
                autoPlay.endTurnMultiturnAutoPlay()
            }
        }

        isEnabled = nextTurnAction.getText(worldScreen) == "AutoPlay"
            || ((worldScreen.isPlayersTurn || worldScreen.failedUpload) && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning())
        if (isEnabled) {
            addTooltip(KeyboardBinding.NextTurn)
        } else {
            removeTooltips()
        }

        worldScreen.smallUnitButton.update()
        checkAutoEndTurn()
    }

    /**
     * Ends the turn shortly after the last unit got its orders, see [GameSettings.autoEndTurn].
     * Only after something needed attention this turn, so a turn with nothing to do is still
     * shown to the player instead of skipping past.
     */
    private fun checkAutoEndTurn() {
        val turn = worldScreen.gameInfo.turns
        if (nextTurnAction != NextTurnAction.NextTurn || worldScreen.selectedGameView.civView.dueUnitsCount() > 0) {
            if (nextTurnAction !in passiveActions) turnWithPendingWork = turn
            return
        }
        if (autoEndScheduled || turnWithPendingWork != turn || !canAutoEndTurn()) return
        autoEndScheduled = true
        Concurrency.run("AutoEndTurn") {
            delay(1500)
            launchOnGLThread {
                autoEndScheduled = false
                // Anything may have happened meanwhile: a selected unit, a popup, another screen
                if (worldScreen.gameInfo.turns == turn && turnWithPendingWork == turn && canAutoEndTurn()
                    && getNextTurnAction(worldScreen) == NextTurnAction.NextTurn
                    && worldScreen.selectedGameView.civView.dueUnitsCount() == 0) {
                    turnWithPendingWork = -1
                    worldScreen.nextTurn()
                }
            }
        }
    }

    private fun canAutoEndTurn() = worldScreen.game.settings.autoEndTurn
        && worldScreen.game.screen === worldScreen && worldScreen.isPlayersTurn && isEnabled
        && !worldScreen.hasOpenPopups() && worldScreen.game.getNativeUi()?.isModalShowing() != true
        && !worldScreen.autoPlay.isAutoPlaying() && !worldScreen.gameInfo.gameParameters.isOnlineMultiplayer
        // Not while the player looks at a unit that could still act
        && worldScreen.bottomUnitTable.selectedUnit?.hasMovement() != true

    private val passiveActions = setOf(NextTurnAction.Default, NextTurnAction.NextTurn, NextTurnAction.Working,
        NextTurnAction.Waiting, NextTurnAction.AutoPlay, NextTurnAction.RetryUpload)

    internal fun updateButton(nextTurnAction: NextTurnAction) {
        label.setText(nextTurnAction.getText(worldScreen).tr())
        label.color = nextTurnAction.color
        if (nextTurnAction.icon != null && ImageGetter.imageExists(nextTurnAction.icon!!))
            iconCell.setActor(ImageGetter.getImage(nextTurnAction.icon).apply {
                setSize(30f)
                color = nextTurnAction.color
            })
        else
            iconCell.clearActor()

        nextTurnAction.getSubText(worldScreen)?.let {
            unitsDueLabel.setText(it.tr())
            unitsDueCell.setActor(unitsDueLabel)
        } ?: unitsDueCell.clearActor()

        pack()
    }

    private fun getNextTurnAction(worldScreen: WorldScreen) =
        // Guaranteed to return a non-null NextTurnAction because the last isChoice always returns true
        NextTurnAction.entries.first { it.isChoice(worldScreen) }

    @Readonly fun isNextUnitAction(): Boolean = nextTurnAction == NextTurnAction.NextUnit

}
