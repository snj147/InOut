package com.personal.inout.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayBillingManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)

    private val _isProUnlocked = MutableStateFlow(
        checkInitialTrialOrPro()
    )
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var productDetails: ProductDetails? = null

    init {
        startConnection()
    }

    private fun checkInitialTrialOrPro(): Boolean {
        // 1. Is permanently purchased?
        if (prefs.getBoolean("is_lifetime_pro_purchased", false)) return true

        // 2. 7-Day Free Trial check
        val installTime = prefs.getLong("first_install_timestamp", 0L)
        val now = System.currentTimeMillis()
        if (installTime == 0L) {
            prefs.edit().putLong("first_install_timestamp", now).apply()
            return true
        }

        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        return (now - installTime) < sevenDaysMillis
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection on next launch
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("inout_lifetime_pro_21")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { _, list ->
            productDetails = list.firstOrNull()
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val details = productDetails ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        grantLifetimePro()
                    }
                }
            } else {
                grantLifetimePro()
            }
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { _, purchases ->
            if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                grantLifetimePro()
            }
        }
    }

    private fun grantLifetimePro() {
        prefs.edit().putBoolean("is_lifetime_pro_purchased", true).apply()
        _isProUnlocked.value = true
    }
}
