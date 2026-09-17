package com.personal.inout.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayBillingManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)

    private val _isProUnlocked = MutableStateFlow(
        prefs.getBoolean("is_lifetime_pro_purchased", false)
    )
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked

    // Local Test Simulation Methods
    fun simulatePurchaseSuccess() {
        prefs.edit().putBoolean("is_lifetime_pro_purchased", true).apply()
        _isProUnlocked.value = true
    }

    fun simulateRevokePro() {
        prefs.edit().putBoolean("is_lifetime_pro_purchased", false).apply()
        _isProUnlocked.value = false
    }

    // Play Billing Client initialization (Safe even when offline or without Play Console)
    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var productDetails: ProductDetails? = null

    init {
        startConnection()
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
                // Dormant until network or retry
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
        val details = productDetails
        if (details != null) {
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
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    simulatePurchaseSuccess()
                }
            }
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { _, purchases ->
            if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                simulatePurchaseSuccess()
            }
        }
    }
}
