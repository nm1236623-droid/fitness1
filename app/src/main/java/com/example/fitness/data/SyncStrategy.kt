package com.example.fitness.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 統一的本地/雲端數據同步策略
 * 
 * 提供統一的配置管理，讓用戶可以選擇使用本地存儲或 Firebase 雲端存儲
 */
object SyncStrategy {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_settings")
    
    private val USE_FIREBASE_KEY = booleanPreferencesKey("use_firebase")
    private val USE_OFFLINE_CACHE_KEY = booleanPreferencesKey("use_offline_cache")
    
    /**
     * 獲取是否使用 Firebase 的設定
     */
    fun getUseFirebase(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[USE_FIREBASE_KEY] ?: true  // 預設改為 true
        }
    }
    
    /**
     * 設置是否使用 Firebase
     */
    suspend fun setUseFirebase(context: Context, useFirebase: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_FIREBASE_KEY] = useFirebase
        }
    }
    
    /**
     * 獲取是否使用離線緩存的設定
     */
    fun getUseOfflineCache(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[USE_OFFLINE_CACHE_KEY] ?: true
        }
    }
    
    /**
     * 設置是否使用離線緩存
     */
    suspend fun setUseOfflineCache(context: Context, useOfflineCache: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_OFFLINE_CACHE_KEY] = useOfflineCache
        }
    }
    
    /**
     * 同步策略枚舉
     */
    enum class Strategy {
        LOCAL_ONLY,          // 僅使用本地存儲
        FIREBASE_ONLY,       // 僅使用 Firebase 雲端
        FIREFIRST_LOCAL_FALLBACK,  // 優先 Firebase，本地作為備份
        LOCAL_FIREBASE_SYNC  // 本地和 Firebase 同步
    }
    
    /**
     * 獲取當前同步策略
     */
    fun getCurrentStrategy(context: Context): Flow<Strategy> {
        return context.dataStore.data.map { preferences ->
            val useFirebase = preferences[USE_FIREBASE_KEY] ?: true  // 預設改為 true
            val useOfflineCache = preferences[USE_OFFLINE_CACHE_KEY] ?: true
            
            when {
                !useFirebase -> Strategy.LOCAL_ONLY
                useOfflineCache -> Strategy.FIREFIRST_LOCAL_FALLBACK
                else -> Strategy.FIREBASE_ONLY
            }
        }
    }
    
    /**
     * 設置同步策略
     */
    suspend fun setStrategy(context: Context, strategy: Strategy) {
        context.dataStore.edit { preferences ->
            when (strategy) {
                Strategy.LOCAL_ONLY -> {
                    preferences[USE_FIREBASE_KEY] = false
                    preferences[USE_OFFLINE_CACHE_KEY] = true
                }
                Strategy.FIREBASE_ONLY -> {
                    preferences[USE_FIREBASE_KEY] = true
                    preferences[USE_OFFLINE_CACHE_KEY] = false
                }
                Strategy.FIREFIRST_LOCAL_FALLBACK -> {
                    preferences[USE_FIREBASE_KEY] = true
                    preferences[USE_OFFLINE_CACHE_KEY] = true
                }
                Strategy.LOCAL_FIREBASE_SYNC -> {
                    preferences[USE_FIREBASE_KEY] = true
                    preferences[USE_OFFLINE_CACHE_KEY] = true
                }
            }
        }
    }
}

/**
 * 數據同步管理器
 * 
 * 提供統一的數據同步接口，處理本地和雲端數據的一致性
 */
class DataSyncManager(private val context: Context) {
    
    /**
     * 檢查網路連接狀態
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 決定使用哪個數據源
     */
    suspend fun decideDataSource(): Boolean {
        var result = false
        SyncStrategy.getCurrentStrategy(context).collect { strategy ->
            result = when (strategy) {
                SyncStrategy.Strategy.LOCAL_ONLY -> false
                SyncStrategy.Strategy.FIREBASE_ONLY -> true
                SyncStrategy.Strategy.FIREFIRST_LOCAL_FALLBACK -> isNetworkAvailable()
                SyncStrategy.Strategy.LOCAL_FIREBASE_SYNC -> true // 雙向同步，優先 Firebase
            }
        }
        return result
    }
    
    /**
     * 同步數據到雲端
     */
    suspend fun syncToCloud() {
        if (!isNetworkAvailable()) return
        
        // 這裡可以實現具體的同步邏輯
        // 例如：將本地未同步的數據上傳到 Firebase
    }
    
    /**
     * 從雲端同步數據
     */
    suspend fun syncFromCloud() {
        if (!isNetworkAvailable()) return
        
        // 這裡可以實現具體的同步邏輯
        // 例如：從 Firebase 下載最新數據並合併到本地
    }
}
