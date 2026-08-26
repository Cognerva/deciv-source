package com.unciv.ui.support

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.options.OptionsPopupPages
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.PurchaseResult
import com.unciv.utils.StoreProduct

/** The optional, deliberately transparent iOS-only support purchase sheet. */
internal class SupportPopup(private val screen: BaseScreen) : Popup(screen) {
    private val productButtons = HashMap<String, TextButton>()
    private val fetchedProducts = HashMap<String, StoreProduct>()
    private var ownedSummaryLabel: com.badlogic.gdx.scenes.scene2d.ui.Label? = null
    private val productLabels = mapOf(
        Constants.supporterProductId to "DeCiv Supporter",
        Constants.smallTipProductId to "Small tip",
        Constants.largeTipProductId to "Large tip",
        Constants.patronTipProductId to "Patron tip"
    )
    private val productOrder = listOf(
        Constants.supporterProductId,
        Constants.smallTipProductId,
        Constants.largeTipProductId,
        Constants.patronTipProductId
    )

    companion object {
        private const val patronBadgeThresholdMinorUnits = 1200

        /** Badge names are derived from the accumulated tip total, not purchase order. */
        internal fun badgeName(settings: GameSettings): String? = when {
            settings.tipTotalMinorUnits >= patronBadgeThresholdMinorUnits -> "DeCiv Patron"
            settings.supporterUnlocked || settings.tipTotalMinorUnits > 0 -> "DeCiv Supporter"
            else -> null
        }
    }

    init {
        if (hasRecordedSupport()) {
            val badge = badgeName(screen.game.settings) ?: "DeCiv Supporter"
            addGoodSizedLabel("Thank you — you're a $badge.").row()
            addGoodSizedLabel("Your support:", size = 20).row()
            val ownedSummaryCell = addGoodSizedLabel(ownedProductsText(), hideIcons = true)
            ownedSummaryLabel = ownedSummaryCell.actor
            ownedSummaryCell.row()
            if (screen.game.settings.supporterUnlocked)
                addGoodSizedLabel("✓ Alternate app icons — choose one", hideIcons = true).row()
        } else {
            addGoodSizedLabel("DeCiv's game content is free, forever. Supporter unlocks iOS extras and helps pay for the port.").row()
            addGoodSizedLabel("What DeCiv Supporter unlocks:", size = 20).row()
            addGoodSizedLabel("• Alternate app icons\n• A supporter badge in DeCiv").row()
        }

        addProductButton(Constants.supporterProductId)
        row()

        addGoodSizedLabel("Optional tips", size = 20).padTop(10f).row()
        val tips = Table().apply { defaults().pad(4f) }
        tips.addProductButton(Constants.smallTipProductId)
        tips.addProductButton(Constants.largeTipProductId)
        tips.addProductButton(Constants.patronTipProductId)
        add(tips).colspan(2).row()

        addButton("Restore purchases") {
            screen.game.restoreStorePurchases { restored ->
                ToastPopup(
                    if (restored.isNotEmpty()) "Purchases restored" else "No purchases to restore",
                    screen
                )
                if (restored.isNotEmpty()) {
                    close()
                    SupportPopup(screen).open(force = true)
                }
            }
        }.row()
        addButton("Choose app icon") {
            close()
            screen.openOptionsPopup(OptionsPopupPages.AppIcon)
        }.row()
        addButton("Support the original game") {
            Gdx.net.openURI(Constants.uncivRepoURL)
        }.row()
        addCloseButton().row()

        screen.game.fetchStoreProducts(Constants.storeProductIds) { products ->
            fetchedProducts.clear()
            for (product in products) fetchedProducts[product.productId] = product
            val fetchedProductIds = products.mapTo(HashSet()) { it.productId }
            for ((productId, button) in productButtons) {
                val available = productId in fetchedProductIds
                val owned = productId == Constants.supporterProductId &&
                    (productId in screen.game.settings.ownedProductIds || screen.game.settings.supporterUnlocked)
                button.isDisabled = !available || owned
                when {
                    owned -> button.setText("${productLabels[productId]} — owned")
                    !available -> button.setText("${productLabels[productId] ?: productId} — unavailable")
                }
            }
            for (product in products) {
                val supporterOwned = screen.game.settings.supporterUnlocked ||
                    Constants.supporterProductId in screen.game.settings.ownedProductIds
                if (product.productId != Constants.supporterProductId || !supporterOwned)
                    productButtons[product.productId]?.setText(productButtonText(product))
            }
            ownedSummaryLabel?.setText(ownedProductsText())
            pack()
            fitOrCenterContentIntoVisibleArea()
        }
    }

    private fun Table.addProductButton(productId: String): TextButton {
        val button = (productLabels[productId] ?: productId).toTextButton()
        button.onActivation {
            screen.game.purchaseStoreProduct(productId) { result ->
                when (result) {
                    PurchaseResult.Purchased, PurchaseResult.Restored -> {
                        ToastPopup("Thank you for supporting DeCiv", screen)
                        close()
                        SupportPopup(screen).open(force = true)
                    }
                    PurchaseResult.Failed -> ToastPopup("Purchase could not be completed", screen)
                    PurchaseResult.Unavailable -> ToastPopup("Store purchases are unavailable", screen)
                }
            }
        }
        productButtons[productId] = button
        button.isDisabled = true
        add(button)
        return button
    }

    private fun hasRecordedSupport(): Boolean {
        val settings = screen.game.settings
        return settings.supporterUnlocked ||
            settings.tipTotalMinorUnits > 0 ||
            settings.ownedProductIds.isNotEmpty()
    }

    private fun ownedProductsText(): String {
        val settings = screen.game.settings
        val owned = productOrder.filter { it in settings.ownedProductIds }.toMutableList()
        // Builds from before ownedProductIds existed still have the entitlement boolean.
        if (settings.supporterUnlocked && Constants.supporterProductId !in owned)
            owned.add(0, Constants.supporterProductId)
        if (owned.isEmpty()) return "✓ DeCiv support"
        return owned.joinToString("\n") { productId ->
            val price = fetchedProducts[productId]?.localizedPrice
            "✓ ${productLabels[productId] ?: productId}" + if (price == null) "" else " — $price"
        }
    }

    private fun productButtonText(product: StoreProduct): String {
        return "${productLabels[product.productId] ?: product.productId} — ${product.localizedPrice}"
    }
}
