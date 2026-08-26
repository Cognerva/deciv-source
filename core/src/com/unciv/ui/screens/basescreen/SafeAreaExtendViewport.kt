package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.UncivGame
import com.unciv.utils.SafeAreaInsetSide
import kotlin.math.max

/**
 * An [ExtendViewport] whose usable screen rectangle excludes platform safe-area insets.
 *
 * The full framebuffer is still cleared by [BaseScreen]; only the stage is constrained to
 * the inset rectangle. Keeping the inset calculation here also means libGDX's normal project /
 * unproject touch mapping continues to use the same viewport that draws the stage.
 */
class SafeAreaExtendViewport(
    minWorldWidth: Float,
    minWorldHeight: Float,
    private val useSafeArea: () -> Boolean
) : ExtendViewport(minWorldWidth, minWorldHeight) {
    var lastFullWidth: Int = -1
        private set
    var lastFullHeight: Int = -1
        private set

    private var lastInsets = intArrayOf(-1, -1, -1, -1) // left, top, bottom, right

    override fun update(width: Int, height: Int, centerCamera: Boolean) {
        lastFullWidth = width
        lastFullHeight = height
        val insets = currentInsets()
        lastInsets = insets

        val availableWidth = max(1, width - insets[0] - insets[3])
        val availableHeight = max(1, height - insets[1] - insets[2])
        super.update(availableWidth, availableHeight, centerCamera)

        // ExtendViewport calculated the world size from the available rectangle. Move the
        // resulting viewport into that rectangle after the calculation.
        setScreenBounds(
            insets[0],
            insets[2],
            screenWidth,
            screenHeight
        )
        apply(centerCamera)
    }

    fun refreshIfNeeded() {
        val width = Gdx.graphics.width
        val height = Gdx.graphics.height
        val insets = currentInsets()
        if (width != lastFullWidth || height != lastFullHeight || !insets.contentEquals(lastInsets))
            update(width, height, true)
    }

    fun hasSafeAreaInsets(): Boolean = currentInsets().any { it > 0 }

    private fun currentInsets(): IntArray {
        if (!useSafeArea()) return intArrayOf(0, 0, 0, 0)
        val reportedLeft = max(0, Gdx.graphics.getSafeInsetLeft())
        val reportedRight = max(0, Gdx.graphics.getSafeInsetRight())
        val housingInset = max(reportedLeft, reportedRight)

        // In landscape, iOS may report the sensor housing symmetrically so content remains
        // centred. The iOS adapter chooses the physical housing edge; keep a small inset on the
        // opposite edge for the rounded display corners. Unknown/platform-neutral cases retain
        // the reported values. Top and bottom insets are deliberately ignored: the status bar
        // is hidden and the home indicator is auto-hidden on iOS.
        return when (UncivGame.Current.getSafeAreaInsetSide()) {
            SafeAreaInsetSide.Left -> intArrayOf(housingInset, 0, 0, if (housingInset > 0) 16 else 0)
            SafeAreaInsetSide.Right -> intArrayOf(if (housingInset > 0) 16 else 0, 0, 0, housingInset)
            SafeAreaInsetSide.Both -> intArrayOf(reportedLeft, 0, 0, reportedRight)
        }
    }
}
