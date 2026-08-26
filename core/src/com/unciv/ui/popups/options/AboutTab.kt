package com.unciv.ui.popups.options

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.support.SupportContact
import com.unciv.ui.support.SupportPopup

internal class AboutTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    init {
        pad(20f)

        val sourceTag = "${Constants.decivSourceURL}tree/v${UncivGame.VERSION.text}-build${UncivGame.VERSION.number}"
        val lines = sequence {
            yield(FormattedLine(extraImage = "Icons/Preview-Monolith.png", imageSize = 128f, centered = true))
            yield(FormattedLine())
            yield(FormattedLine("{Version}: ${UncivGame.VERSION.toNiceString()}", link = sourceTag))
            yield(FormattedLine("Open source code and licenses", link = sourceTag))
            yield(FormattedLine("DeCiv is an independent iOS port of Unciv. It is not affiliated with, endorsed by, or supported by the Unciv project or its authors."))
            yield(FormattedLine("Unciv is © yairm210 and contributors, licensed under the Mozilla Public License 2.0. DeCiv's modified source is published at github.com/Cognerva/deciv-source."))
            yield(FormattedLine("Civilization is a trademark of Take-Two Interactive. DeCiv is not associated with or endorsed by Take-Two Interactive."))
            yield(FormattedLine("Under MPL-2.0 §3.2(a), the corresponding source for this build is available at the published DeCiv source tag.", link = sourceTag))
            yield(FormattedLine())
            yield(FormattedLine("Credits", header = 3))
            yield(FormattedLine("Hexagon — kareemovic, Noun Project (CC BY)"))
            yield(FormattedLine("Civilization / Monument — Eucalyp, Noun Project (CC BY)"))
            yield(FormattedLine("Civilization / Forum — Eucalyp, Noun Project (CC BY)"))
            yield(FormattedLine("Mayan Pyramid — WR Graphic Garage, Noun Project (CC BY)"))
            yield(FormattedLine("Sphinx — 1516, Noun Project (CC BY)"))
            yield(FormattedLine("Third-party libraries include libGDX, Kotlin, kotlinx.coroutines, kotlinx.serialization, Ktor, ThreeTen-Backport, and RoboVM/MobiVM. Complete license notices are included in the published source."))
            yield(FormattedLine())
            yield(FormattedLine("Original project (Unciv)", link = Constants.uncivRepoURL))
            yield(FormattedLine("Unciv wiki", link = Constants.wikiURL))
        }
        MarkupRenderer.renderTo(this, lines.asIterable())

        val badge = SupportPopup.badgeName(game.settings)
        add("${badge?.let { "$it — owned" } ?: "DeCiv Supporter — optional"}".toTextButton().onActivation {
            SupportPopup(optionsPopup.baseScreen).open(force = true)
        }).colspan(2).row()
        add("Contact support".toTextButton().onActivation {
            SupportContact.open(optionsPopup.baseScreen)
        }).colspan(2).row()
        if (game.isStoreAvailable()) {
            add("Restore purchases".toTextButton().onActivation {
                game.restoreStorePurchases { restored ->
                    ToastPopup(
                        if (restored.isNotEmpty()) "Purchases restored" else "No purchases to restore",
                        optionsPopup.baseScreen
                    )
                    if (restored.isNotEmpty()) optionsPopup.reopenOptions(force = true)
                }
            }).colspan(2).row()
        }
    }
}
