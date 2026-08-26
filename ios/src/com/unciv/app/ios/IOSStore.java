package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.unciv.Constants;
import com.unciv.UncivGame;
import com.unciv.utils.PurchaseResult;
import com.unciv.utils.StoreProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.foundation.NSLocale;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.storekit.SKDownload;
import org.robovm.apple.storekit.SKPayment;
import org.robovm.apple.storekit.SKPaymentQueue;
import org.robovm.apple.storekit.SKPaymentTransaction;
import org.robovm.apple.storekit.SKPaymentTransactionObserver;
import org.robovm.apple.storekit.SKPaymentTransactionState;
import org.robovm.apple.storekit.SKProduct;
import org.robovm.apple.storekit.SKProductsRequest;
import org.robovm.apple.storekit.SKProductsRequestDelegate;
import org.robovm.apple.storekit.SKProductsResponse;
import org.robovm.apple.storekit.SKRequest;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIDevice;
import org.robovm.objc.block.VoidBlock1;

/** StoreKit 1 adapter for optional DeCiv support purchases.
 *
 * Purchases are intentionally client-side only: the supporter flag and tip total are stored in
 * the existing game settings, with StoreKit transactions as the source of truth available to the
 * app. No gameplay, rulesets, saves, mods, or multiplayer paths depend on these values.
 */
final class IOSStore extends NSObject implements SKPaymentTransactionObserver, SKProductsRequestDelegate {
    private final SKPaymentQueue queue = SKPaymentQueue.getDefaultQueue();
    private final Map<String, SKProduct> products = new HashMap<>();
    private final Map<String, Integer> productPricesMinorUnits = new HashMap<>();
    private final Map<String, Function1<? super PurchaseResult, Unit>> purchaseCallbacks = new HashMap<>();
    private volatile String lastAlternateIconError;

    private SKProductsRequest productsRequest;
    private Function1<? super List<StoreProduct>, Unit> productsCallback;
    private Set<String> requestedProductIds = new HashSet<>();
    private Function1<? super Set<String>, Unit> restoreCallback;
    private final Set<String> restoredProducts = new HashSet<>();

    IOSStore() {
        queue.addTransactionObserver(this);
    }

    boolean isAvailable() {
        return SKPaymentQueue.canMakePayments();
    }

    void fetchProducts(Set<String> productIds, Function1<? super List<StoreProduct>, Unit> callback) {
        if (productIds.isEmpty()) {
            callback.invoke(java.util.Collections.<StoreProduct>emptyList());
            return;
        }
        productsCallback = callback;
        requestedProductIds = new HashSet<>(productIds);
        productsRequest = new SKProductsRequest(new HashSet<>(productIds));
        productsRequest.setDelegate(this);
        productsRequest.start();
    }

    void purchase(String productId, Function1<? super PurchaseResult, Unit> callback) {
        SKProduct product = products.get(productId);
        if (!isAvailable() || product == null) {
            callback.invoke(PurchaseResult.Unavailable);
            return;
        }
        purchaseCallbacks.put(productId, callback);
        queue.addPayment(new SKPayment(product));
    }

    void restore(Function1<? super Set<String>, Unit> callback) {
        restoredProducts.clear();
        restoreCallback = callback;
        queue.restoreCompletedTransactions();
    }

    boolean setAlternateIcon(String iconName, final Function1<? super Boolean, Unit> callback) {
        UIApplication application = UIApplication.getSharedApplication();
        if (!application.supportsAlternateIcons()) {
            lastAlternateIconError = "Alternate icons are not supported on this device";
            logIconError(iconName, lastAlternateIconError);
            callback.invoke(false);
            return false;
        }
        application.setAlternateIcon(iconName, new VoidBlock1<NSError>() {
            @Override
            public void invoke(NSError error) {
                final boolean success = error == null;
                lastAlternateIconError = error == null ? null : error.getLocalizedDescription();
                if (!success) logIconError(iconName, lastAlternateIconError);
                postToGame(new Runnable() {
                    @Override
                    public void run() {
                        callback.invoke(success);
                    }
                });
            }
        });
        return true;
    }

    String getAlternateIconName() {
        return UIApplication.getSharedApplication().getAlternateIconName();
    }

    String getLastAlternateIconError() {
        return lastAlternateIconError;
    }

    String getDeviceDescription() {
        UIDevice device = UIDevice.getCurrentDevice();
        return "iOS " + device.getSystemVersion() + " — " + device.getModel();
    }

    @Override
    public void didReceiveResponse(SKProductsRequest request, SKProductsResponse response) {
        products.clear();
        productPricesMinorUnits.clear();
        Set<String> returnedProductIds = new HashSet<>();
        for (SKProduct product : response.getProducts()) {
            products.put(product.getProductIdentifier(), product);
            productPricesMinorUnits.put(
                product.getProductIdentifier(),
                minorUnits(product.getPrice())
            );
        }
        for (SKProduct product : response.getProducts())
            returnedProductIds.add(product.getProductIdentifier());

        Set<String> missingProductIds = new HashSet<>(requestedProductIds);
        missingProductIds.removeAll(returnedProductIds);
        logStoreKit("requested=" + requestedProductIds + " returned=" + returnedProductIds +
            " invalid=" + response.getInvalidProductIdentifiers() + " missing=" + missingProductIds);
        if (!response.getInvalidProductIdentifiers().isEmpty())
            logStoreKit("Invalid product identifiers: " + response.getInvalidProductIdentifiers());

        final java.util.ArrayList<StoreProduct> localizedProducts = new java.util.ArrayList<>();
        for (SKProduct product : response.getProducts()) {
            localizedProducts.add(new StoreProduct(
                product.getProductIdentifier(),
                localizedPrice(product.getPrice(), product.getPriceLocale()),
                productPricesMinorUnits.get(product.getProductIdentifier())
            ));
        }
        Function1<? super List<StoreProduct>, Unit> callback = productsCallback;
        productsCallback = null;
        requestedProductIds.clear();
        productsRequest = null;
        if (callback != null) {
            postToGame(new Runnable() {
                @Override
                public void run() {
                    callback.invoke(localizedProducts);
                }
            });
        }
    }

    private String localizedPrice(org.robovm.apple.foundation.NSDecimalNumber price, NSLocale locale) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale.toLocale());
        return formatter.format(price.doubleValue());
    }

    private int minorUnits(org.robovm.apple.foundation.NSDecimalNumber price) {
        return BigDecimal.valueOf(price.doubleValue())
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    }

    @Override
    public void didFinish(SKRequest request) {
        // The products response callback carries the useful result.
    }

    @Override
    public void didFail(SKRequest request, NSError error) {
        if (request != productsRequest) return;
        productsRequest = null;
        Function1<? super List<StoreProduct>, Unit> callback = productsCallback;
        productsCallback = null;
        logStoreKit("product request failed: " + (error == null ? "unknown error" : error.toString()));
        requestedProductIds.clear();
        if (callback != null) {
            postToGame(new Runnable() {
                @Override
                public void run() {
                    callback.invoke(java.util.Collections.<StoreProduct>emptyList());
                }
            });
        }
    }

    private void logStoreKit(String message) {
        if (Gdx.app != null) Gdx.app.log("DeCivStore", message);
        else System.out.println("[DeCivStore] " + message);
    }

    @Override
    public void updatedTransactions(SKPaymentQueue paymentQueue, NSArray<SKPaymentTransaction> transactions) {
        for (SKPaymentTransaction transaction : transactions) {
            SKPaymentTransactionState state = transaction.getTransactionState();
            if (state == SKPaymentTransactionState.Purchased) {
                completePurchase(transaction, PurchaseResult.Purchased);
            } else if (state == SKPaymentTransactionState.Restored) {
                String productId = transaction.getOriginalTransaction() == null
                    ? transaction.getPayment().getProductIdentifier()
                    : transaction.getOriginalTransaction().getPayment().getProductIdentifier();
                restoredProducts.add(productId);
                queue.finishTransaction(transaction);
                completeRestoredTransaction(productId);
            } else if (state == SKPaymentTransactionState.Failed) {
                String productId = transaction.getPayment().getProductIdentifier();
                queue.finishTransaction(transaction);
                postToGame(new Runnable() {
                    @Override
                    public void run() {
                        completeCallback(productId, PurchaseResult.Failed);
                    }
                });
            }
        }
    }

    private void completeRestoredTransaction(final String productId) {
        postToGame(new Runnable() {
            @Override
            public void run() {
                applyRestoredEntitlement(productId);
                UncivGame.Current.getSettings().save();
                completeCallback(productId, PurchaseResult.Restored);
            }
        });
    }

    private void completePurchase(final SKPaymentTransaction transaction, final PurchaseResult result) {
        final String productId = transaction.getPayment().getProductIdentifier();
        queue.finishTransaction(transaction);
        postToGame(new Runnable() {
            @Override
            public void run() {
                applyPurchasedEntitlement(productId);
                completeCallback(productId, result);
            }
        });
    }

    private void applyPurchasedEntitlement(String productId) {
        UncivGame.Current.getSettings().getOwnedProductIds().add(productId);
        if (Constants.supporterProductId.equals(productId) ||
            Constants.largeTipProductId.equals(productId) ||
            Constants.patronTipProductId.equals(productId)) {
            UncivGame.Current.getSettings().setSupporterUnlocked(true);
        }
        Integer tipMinorUnits = productPricesMinorUnits.get(productId);
        if (isTipProduct(productId) && tipMinorUnits != null) addTipTotal(tipMinorUnits);
        UncivGame.Current.getSettings().save();
    }

    private void applyRestoredEntitlement(String productId) {
        UncivGame.Current.getSettings().getOwnedProductIds().add(productId);
        if (Constants.supporterProductId.equals(productId) ||
            Constants.largeTipProductId.equals(productId) ||
            Constants.patronTipProductId.equals(productId)) {
            UncivGame.Current.getSettings().setSupporterUnlocked(true);
        }
    }

    private boolean isTipProduct(String productId) {
        return Constants.smallTipProductId.equals(productId) ||
            Constants.largeTipProductId.equals(productId) ||
            Constants.patronTipProductId.equals(productId);
    }

    private void addTipTotal(int minorUnits) {
        if (minorUnits <= 0) return;
        UncivGame.Current.getSettings().setTipTotalMinorUnits(
            UncivGame.Current.getSettings().getTipTotalMinorUnits() + minorUnits
        );
    }

    private void logIconError(String iconName, String description) {
        String message = "Failed to select alternate icon '" + iconName + "': " +
            (description == null ? "unknown error" : description);
        if (Gdx.app != null) Gdx.app.log("DeCivAppIcon", message);
        else System.out.println("[DeCivAppIcon] " + message);
    }

    private void completeCallback(String productId, PurchaseResult result) {
        Function1<? super PurchaseResult, Unit> callback = purchaseCallbacks.remove(productId);
        if (callback != null) callback.invoke(result);
    }

    private void postToGame(Runnable runnable) {
        if (Gdx.app == null) runnable.run();
        else Gdx.app.postRunnable(runnable);
    }

    @Override
    public void removedTransactions(SKPaymentQueue paymentQueue, NSArray<SKPaymentTransaction> transactions) {}

    @Override
    public void restoreCompletedTransactionsFailed(SKPaymentQueue paymentQueue, NSError error) {
        logStoreKit("restore failed: " + (error == null ? "unknown error" : error.getLocalizedDescription()));
        Function1<? super Set<String>, Unit> callback = restoreCallback;
        restoreCallback = null;
        if (callback != null) {
            postToGame(new Runnable() {
                @Override
                public void run() {
                    callback.invoke(new HashSet<String>());
                }
            });
        }
    }

    @Override
    public void restoreCompletedTransactionsFinished(SKPaymentQueue paymentQueue) {
        Function1<? super Set<String>, Unit> callback = restoreCallback;
        restoreCallback = null;
        if (callback != null) {
            final Set<String> restored = new HashSet<>(restoredProducts);
            postToGame(new Runnable() {
                @Override
                public void run() {
                    for (String productId : restored) applyRestoredEntitlement(productId);
                    if (!restored.isEmpty()) UncivGame.Current.getSettings().save();
                    callback.invoke(restored);
                }
            });
        }
    }

    @Override
    public void updatedDownloads(SKPaymentQueue paymentQueue, NSArray<SKDownload> downloads) {}

    @Override
    public boolean shouldAddStorePayment(SKPaymentQueue paymentQueue, SKPayment payment, SKProduct product) {
        return true;
    }

    @Override
    public void paymentQueueDidChangeStorefront(SKPaymentQueue paymentQueue) {}

    @Override
    public void didRevokeEntitlements(SKPaymentQueue paymentQueue, NSArray<org.robovm.apple.foundation.NSString> productIdentifiers) {}
}
