package com.example.fitness.localization

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.localeDataStore: DataStore<Preferences> by preferencesDataStore(name = "locale_prefs")

/**
 * 支援的語言
 */
enum class SupportedLanguage(val code: String, val displayName: String) {
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어")
}

/**
 * 多語言管理器
 */
class LocaleManager(private val context: Context) {

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("selected_language")

        @Volatile
        private var instance: LocaleManager? = null

        fun getInstance(context: Context): LocaleManager {
            return instance ?: synchronized(this) {
                instance ?: LocaleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 獲取當前語言
     */
    val currentLanguage: Flow<SupportedLanguage> = context.localeDataStore.data.map { prefs ->
        val langCode = prefs[LANGUAGE_KEY] ?: SupportedLanguage.CHINESE_TRADITIONAL.code
        SupportedLanguage.values().find { it.code == langCode } 
            ?: SupportedLanguage.CHINESE_TRADITIONAL
    }

    /**
     * 設置語言
     */
    suspend fun setLanguage(language: SupportedLanguage) {
        context.localeDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language.code
        }
    }

    /**
     * 同步獲取當前語言（用於 attachBaseContext）
     */
    fun getCurrentLanguageSync(): SupportedLanguage {
        return try {
            val prefs = context.getSharedPreferences("locale_prefs_sync", Context.MODE_PRIVATE)
            val langCode = prefs.getString("selected_language", SupportedLanguage.CHINESE_TRADITIONAL.code)
            SupportedLanguage.values().find { it.code == langCode }
                ?: SupportedLanguage.CHINESE_TRADITIONAL
        } catch (e: Exception) {
            SupportedLanguage.CHINESE_TRADITIONAL
        }
    }

    /**
     * 同步保存語言（配合 DataStore 使用）
     */
    suspend fun setLanguageSync(language: SupportedLanguage) {
        // 保存到 DataStore
        context.localeDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language.code
        }
        // 同時保存到 SharedPreferences 以便同步讀取
        context.getSharedPreferences("locale_prefs_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_language", language.code)
            .apply()
    }

    /**
     * 獲取本地化 Context
     */
    fun getLocalizedContext(language: SupportedLanguage): Context {
        val locale = when (language) {
            SupportedLanguage.CHINESE_TRADITIONAL -> Locale.TRADITIONAL_CHINESE
            SupportedLanguage.CHINESE_SIMPLIFIED -> Locale.SIMPLIFIED_CHINESE
            SupportedLanguage.ENGLISH -> Locale.ENGLISH
            SupportedLanguage.JAPANESE -> Locale.JAPANESE
            SupportedLanguage.KOREAN -> Locale.KOREAN
        }

        val configuration = context.resources.configuration
        configuration.setLocale(locale)

        return context.createConfigurationContext(configuration)
    }

    /**
     * 將語言應用到指定的 Context
     */
    fun applyLanguage(baseContext: Context, language: SupportedLanguage): Context {
        val locale = when (language) {
            SupportedLanguage.CHINESE_TRADITIONAL -> Locale.TRADITIONAL_CHINESE
            SupportedLanguage.CHINESE_SIMPLIFIED -> Locale.SIMPLIFIED_CHINESE
            SupportedLanguage.ENGLISH -> Locale.ENGLISH
            SupportedLanguage.JAPANESE -> Locale.JAPANESE
            SupportedLanguage.KOREAN -> Locale.KOREAN
        }

        Locale.setDefault(locale)
        val configuration = baseContext.resources.configuration
        configuration.setLocale(locale)

        return baseContext.createConfigurationContext(configuration)
    }
}

/**
 * 本地化字串 Composable Helper
 */
object Strings {
    // 常用字串鍵值
    const val APP_NAME = "app_name"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val EMAIL = "email"
    const val PASSWORD = "password"
    const val FORGOT_PASSWORD = "forgot_password"

    // 訓練相關
    const val WORKOUT = "workout"
    const val EXERCISES = "exercises"
    const val SETS = "sets"
    const val REPS = "reps"
    const val WEIGHT = "weight"
    const val REST = "rest"
    const val START_WORKOUT = "start_workout"
    const val FINISH_WORKOUT = "finish_workout"

    // 營養相關
    const val NUTRITION = "nutrition"
    const val CALORIES = "calories"
    const val PROTEIN = "protein"
    const val CARBS = "carbs"
    const val FAT = "fat"
    const val WATER = "water"

    // 社交相關
    const val FRIENDS = "friends"
    const val CHALLENGES = "challenges"
    const val LEADERBOARD = "leaderboard"
    const val ACHIEVEMENTS = "achievements"

    // 教練相關
    const val COACH = "coach"
    const val TRAINEE = "trainee"
    const val MESSAGES = "messages"
    const val BOOKINGS = "bookings"
    const val SCHEDULE = "schedule"

    // 設定相關
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val THEME = "theme"
    const val LANGUAGE = "language"
    const val NOTIFICATIONS = "notifications"
    const val PRIVACY = "privacy"
    const val LOGOUT = "logout"

    // 通用
    const val SAVE = "save"
    const val CANCEL = "cancel"
    const val DELETE = "delete"
    const val EDIT = "edit"
    const val CONFIRM = "confirm"
    const val BACK = "back"
    const val NEXT = "next"
    const val DONE = "done"
    const val LOADING = "loading"
    const val ERROR = "error"
    const val SUCCESS = "success"
}

/**
 * 字串資源映射（實際專案中應使用 strings.xml）
 */
object StringResources {
    private val translations = mapOf(
        SupportedLanguage.CHINESE_TRADITIONAL to mapOf(
            Strings.APP_NAME to "健身助手",
            Strings.WELCOME to "歡迎",
            Strings.LOGIN to "登入",
            Strings.SIGN_UP to "註冊",
            Strings.WORKOUT to "訓練",
            Strings.NUTRITION to "營養",
            Strings.FRIENDS to "好友",
            Strings.COACH to "教練",
            Strings.TRAINEE to "學員",
            Strings.SETTINGS to "設定",
            Strings.SAVE to "儲存",
            Strings.CANCEL to "取消"
        ),
        SupportedLanguage.ENGLISH to mapOf(
            Strings.APP_NAME to "Fitness Trainer",
            Strings.WELCOME to "Welcome",
            Strings.LOGIN to "Login",
            Strings.SIGN_UP to "Sign Up",
            Strings.WORKOUT to "Workout",
            Strings.NUTRITION to "Nutrition",
            Strings.FRIENDS to "Friends",
            Strings.COACH to "Coach",
            Strings.TRAINEE to "Trainee",
            Strings.SETTINGS to "Settings",
            Strings.SAVE to "Save",
            Strings.CANCEL to "Cancel"
        )
    )

    fun getString(language: SupportedLanguage, key: String): String {
        return translations[language]?.get(key) 
            ?: translations[SupportedLanguage.CHINESE_TRADITIONAL]?.get(key)
            ?: key
    }
}
