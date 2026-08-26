package com.unciv.ui.support

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import java.net.URLEncoder

/** Builds and opens the prefilled DeCiv support report used by the menu and About page. */
object SupportContact {
    private const val subject = "DeCiv bug report"

    fun mailto(game: UncivGame, report: String? = null): String {
        val body = report ?: buildContactBody(game)
        return "mailto:${Constants.supportEmail}?subject=${encode(subject)}&body=${encode(body)}"
    }

    private fun buildContactBody(game: UncivGame): String {
        val device = game.getDeviceDescription() ?: "Unknown device"
        return buildString {
            appendLine("App version: ${UncivGame.VERSION.toNiceString()}")
            appendLine("Platform: $device")
            appendLine("Language: ${game.settings.language}")
            appendLine()
            appendLine("What happened:")
            appendLine()
            appendLine("What you expected:")
            appendLine()
            appendLine("Steps to reproduce:")
            appendLine()
            appendLine("Notes:")
        }
    }

    fun open(screen: BaseScreen, report: String? = null) {
        if (Gdx.net.openURI(mailto(screen.game, report))) return

        Popup(screen).apply {
            addGoodSizedLabel("Mail is not configured on this device.\nContact ${Constants.supportEmail} directly.")
            addButton("Copy email") {
                Gdx.app.clipboard.contents = Constants.supportEmail
                ToastPopup("Support email copied", screen)
            }
            addCloseButton()
            open()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
