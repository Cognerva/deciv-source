package com.unciv.utils

import java.util.Locale

data class StoreProduct(
    val productId: String,
    val localizedPrice: String,
    /** Price in the storefront's minor-unit scale, used for stacked tip totals. */
    val priceMinorUnits: Int = 0
)

enum class PurchaseResult {
    Purchased,
    Restored,
    Failed,
    Unavailable
}

/** Which landscape edge should receive a sensor-housing safe-area inset. */
enum class SafeAreaInsetSide {
    Left,
    Right,
    Both
}

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null

    fun getGcCount(): Int

    /** Get system locale, on Android 13+ app-specific locale */
    fun getDefaultLocale(): Locale = Locale.getDefault()

    /** Whether this platform has the optional DeCiv StoreKit storefront. */
    fun isStoreAvailable(): Boolean = false

    /** Fetch localized StoreKit prices. Desktop and Android return an empty list. */
    fun fetchStoreProducts(productIds: Set<String>, onResult: (List<StoreProduct>) -> Unit) {
        onResult(emptyList())
    }

    /** Start a purchase for an optional DeCiv-only product. */
    fun purchaseStoreProduct(productId: String, onResult: (PurchaseResult) -> Unit) {
        onResult(PurchaseResult.Unavailable)
    }

    /** Restore non-consumable purchases. */
    fun restoreStorePurchases(onResult: (Set<String>) -> Unit) {
        onResult(emptySet())
    }

    /** Select an iOS alternate icon, or null to restore the default icon. */
    fun setAlternateAppIcon(iconName: String?, onResult: (Boolean) -> Unit) {
        onResult(false)
    }

    /** Read the icon name currently selected by the operating system. */
    fun getAlternateAppIconName(): String? = null

    /** Last platform error returned while selecting an alternate icon, if any. */
    fun getLastAlternateAppIconError(): String? = null

    /** Whether the platform reports libGDX safe-area insets. */
    fun supportsSafeAreaInsets(): Boolean = false

    /** Which edge should receive the landscape sensor-housing inset, if any. */
    fun getSafeAreaInsetSide(): SafeAreaInsetSide = SafeAreaInsetSide.Both

    /** Short device/OS description for prefilled support reports. */
    fun getDeviceDescription(): String? = null
}
