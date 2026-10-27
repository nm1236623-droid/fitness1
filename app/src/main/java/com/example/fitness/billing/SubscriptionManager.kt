package com.example.fitness.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.fitness.coach.UserRole
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration // ★ 新版設定類別
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchaseParams // ★ 新版購買參數
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SubscriptionManager {
    // ★ 權限 ID：教練專屬功能需要此訂閱
    private const val ENTITLEMENT_ID = "Fitness Pro"

    // ★ 學員免費功能權限
    private const val TRAINEE_BASIC_FEATURES = "trainee_basic"

    // ★ 正式模式：教練功能需要訂閱
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _isTraineeBasic = MutableStateFlow(true) // 學員預設可使用基礎功能
    val isTraineeBasic: StateFlow<Boolean> = _isTraineeBasic.asStateFlow()

    fun initialize(context: Context, apiKey: String, userId: String?) {
        try {
            Purchases.logLevel = LogLevel.DEBUG
            Log.d("Subscription", "╔══════════════════════════════════════")
            Log.d("Subscription", "║ Initializing RevenueCat...")
            Log.d("Subscription", "║ API Key: ${apiKey.take(10)}...")
            Log.d("Subscription", "║ User ID: $userId")
            Log.d("Subscription", "╚══════════════════════════════════════")

            val builder = PurchasesConfiguration.Builder(context, apiKey)
            if (userId != null) {
                builder.appUserID(userId)
            }
            Purchases.configure(builder.build())
            Log.d("Subscription", "✅ RevenueCat configured successfully")

            checkSubscriptionStatus()
        } catch (e: Exception) {
            Log.e("Subscription", "❌ initialize failed: ${e.message}", e)
        }
    }

    fun checkSubscriptionStatus() {
        try {
            Log.d("Subscription", "🔍 Checking subscription status...")
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val isActive = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    _isPro.value = isActive

                    val hasTraineeBasic = customerInfo.entitlements[TRAINEE_BASIC_FEATURES]?.isActive == true
                    _isTraineeBasic.value = hasTraineeBasic

                    Log.d("Subscription", "╔══════════════════════════════════════")
                    Log.d("Subscription", "║ 📋 Customer Info Received")
                    Log.d("Subscription", "║ isPro (entitlement '$ENTITLEMENT_ID'): $isActive")
                    Log.d("Subscription", "║ isTraineeBasic (entitlement '$TRAINEE_BASIC_FEATURES'): $hasTraineeBasic")
                    Log.d("Subscription", "║ All entitlements: ${customerInfo.entitlements.all.keys}")
                    Log.d("Subscription", "║ Active subscriptions: ${customerInfo.activeSubscriptions}")
                    Log.d("Subscription", "╚══════════════════════════════════════")
                }

                override fun onError(error: PurchasesError) {
                    Log.e("Subscription", "❌ Check status failed: code=${error.code}, msg=${error.message}, underlyingMsg=${error.underlyingErrorMessage}")
                    _isTraineeBasic.value = true
                }
            })
        } catch (e: Exception) {
            Log.e("Subscription", "❌ checkSubscriptionStatus crashed: ${e.message}", e)
            _isTraineeBasic.value = true
        }
    }

    /**
     * 檢查使用者是否有權限使用特定功能
     * @param userRole 使用者角色（學員/教練）
     * @param isCoachFeature 是否為教練專屬功能
     * @return true 表示有權限使用
     */
    fun hasAccessToFeature(userRole: UserRole, isCoachFeature: Boolean): Boolean {
        return when {
            // 教練功能需要訂閱
            isCoachFeature && userRole == UserRole.COACH -> _isPro.value
            // 學員基礎功能免費
            !isCoachFeature && userRole == UserRole.TRAINEE -> _isTraineeBasic.value
            // 其他情況不允許
            else -> false
        }
    }

    /**
     * 檢查教練是否需要訂閱才能使用進階功能
     */
    fun shouldShowPaywall(userRole: UserRole): Boolean {
        return userRole == UserRole.COACH && !_isPro.value
    }

    // 取得商品列表
    fun getOfferings(onResult: (List<Package>) -> Unit) {
        try {
            Log.d("Subscription", "🔍 Fetching offerings...")
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    val packages = offerings.current?.availablePackages ?: emptyList()
                    Log.d("Subscription", "╔══════════════════════════════════════")
                    Log.d("Subscription", "║ 📦 Offerings Received")
                    Log.d("Subscription", "║ Current offering: ${offerings.current?.identifier ?: "NULL (未設定!)"}")
                    Log.d("Subscription", "║ All offerings: ${offerings.all.keys}")
                    Log.d("Subscription", "║ Available packages: ${packages.size}")
                    packages.forEach { pkg ->
                        Log.d("Subscription", "║   - ${pkg.identifier}: ${pkg.product.title} (${pkg.product.price.formatted})")
                    }
                    if (packages.isEmpty()) {
                        Log.w("Subscription", "║ ⚠️ No packages! Check RevenueCat dashboard:")
                        Log.w("Subscription", "║   1. 是否有建立 Offering (需設為 Current)?")
                        Log.w("Subscription", "║   2. Offering 內是否有 Package?")
                        Log.w("Subscription", "║   3. Package 是否連結到 Google Play 商品?")
                    }
                    Log.d("Subscription", "╚══════════════════════════════════════")
                    onResult(packages)
                }

                override fun onError(error: PurchasesError) {
                    Log.e("Subscription", "❌ Get offerings failed: code=${error.code}, msg=${error.message}, underlyingMsg=${error.underlyingErrorMessage}")
                    onResult(emptyList())
                }
            })
        } catch (e: Exception) {
            Log.e("Subscription", "❌ getOfferings crashed (RevenueCat not initialized?): ${e.message}", e)
            onResult(emptyList())
        }
    }

    // ★★★ 修正 2: 購買邏輯與 Callback ★★★
    fun purchase(activity: Activity, packageToBuy: Package, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // 建立購買參數
        val params = PurchaseParams.Builder(activity, packageToBuy).build()

        Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
            override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                if (customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true) {
                    _isPro.value = true
                    onSuccess()
                }
            }

            override fun onError(error: PurchasesError, userCancelled: Boolean) {
                if (!userCancelled) {
                    onError(error.message)
                }
            }
        })
    }
}