package com.example.fitness.nutrition

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Context.nutritionDataStore: DataStore<Preferences> by preferencesDataStore(name = "advanced_nutrition")

/**
 * 進階營養數據模型
 */
data class MealTimingAnalysis(
    val breakfast: NutritionSummary,
    val lunch: NutritionSummary,
    val dinner: NutritionSummary,
    val snacks: NutritionSummary
)

data class NutritionSummary(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0
)

/**
 * 微量營養素
 */
data class Micronutrients(
    val vitaminA: Double = 0.0,  // mcg
    val vitaminC: Double = 0.0,  // mg
    val vitaminD: Double = 0.0,  // mcg
    val calcium: Double = 0.0,   // mg
    val iron: Double = 0.0,      // mg
    val sodium: Double = 0.0,    // mg
    val potassium: Double = 0.0, // mg
    val zinc: Double = 0.0       // mg
)

/**
 * 水分攝取記錄
 */
data class WaterIntake(
    val id: String,
    val amount: Double,      // ml
    val timestamp: Timestamp = Timestamp.now(),
    val userId: String
)

/**
 * 營養評分
 */
data class NutritionScore(
    val overallScore: Int,        // 0-100
    val macroBalance: Int,        // 0-100
    val micronutrients: Int,      // 0-100
    val mealTiming: Int,          // 0-100
    val hydration: Int,           // 0-100
    val recommendations: List<String>
)

class AdvancedNutritionRepository(private val context: Context) {

    companion object {
        private val WATER_GOAL_KEY = doublePreferencesKey("water_goal_ml")
        private val FIBER_GOAL_KEY = doublePreferencesKey("fiber_goal_g")
        private const val DEFAULT_WATER_GOAL = 2000.0 // ml
        private const val DEFAULT_FIBER_GOAL = 25.0   // g
        private const val WATER_COLLECTION = "water_logs"
    }

    // ==================== 水分追蹤 ====================

    /**
     * 獲取今日水分攝取目標
     */
    val waterGoal: Flow<Double> = context.nutritionDataStore.data.map { prefs ->
        prefs[WATER_GOAL_KEY] ?: DEFAULT_WATER_GOAL
    }

    /**
     * 設定水分攝取目標
     */
    suspend fun setWaterGoal(goalMl: Double) {
        context.nutritionDataStore.edit { prefs ->
            prefs[WATER_GOAL_KEY] = goalMl
        }
    }

    /**
     * 記錄水分攝取
     */
    suspend fun logWaterIntake(amountMl: Double): Result<Unit> {
        return try {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId.isNullOrBlank()) {
                Log.e("WaterIntake", "用戶未登入")
                return Result.failure(Exception("未登入"))
            }

            val id = java.util.UUID.randomUUID().toString()
            val nowTs = Timestamp.now()

            Log.d("WaterIntake", "開始記錄水分攝取: userId=$userId, amount=$amountMl ml")

            // rules 需要 amount:number，這裡統一寫 amount，並保留 amountMl 方便舊版查詢相容
            val data = hashMapOf(
                "id" to id,
                "amount" to amountMl,
                "amountMl" to amountMl,
                "userId" to userId,
                "timestamp" to nowTs,
                "timestampEpochMillis" to nowTs.toDate().time
            )

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(WATER_COLLECTION)
                .document(id)
                .set(data)
                .await()

            Log.d("WaterIntake", "水分攝取記錄成功: id=$id, amount=$amountMl ml")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WaterIntake", "記錄水分攝取失敗: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 獲取今日水分攝取總量
     */
    suspend fun getTodayWaterIntake(): Double {
        return try {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: return 0.0

            val today = LocalDate.now(ZoneId.systemDefault())
            val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(WATER_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", Timestamp(startOfDay.epochSecond, startOfDay.nano))
                .whereLessThan("timestamp", Timestamp(endOfDay.epochSecond, endOfDay.nano))
                .get()
                .await()

            snapshot.documents.sumOf {
                // 先讀 amount，再 fallback 到 amountMl
                it.getDouble("amount") ?: it.getDouble("amountMl") ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * 監聽「今日」水分攝取總量（ml）。
     *
     * - 以 Firestore snapshot listener 當單一資料來源（SSOT），
     *   寫入成功後會自動推送最新總量。
     * - 若未登入，會持續 emit 0.0。
     */
    fun observeTodayWaterIntake(): Flow<Double> = callbackFlow {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrBlank()) {
            Log.w("WaterIntake", "用戶未登入，返回 0.0")
            trySend(0.0)
            close()
            return@callbackFlow
        }

        val today = LocalDate.now(ZoneId.systemDefault())
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val startMillis = startOfDay.toEpochMilli()
        val endMillis = endOfDay.toEpochMilli()

        Log.d("WaterIntake", "開始監聽今日水分攝取: userId=$userId, date=$today, startMillis=$startMillis, endMillis=$endMillis")

        // 只使用 userId 查詢，避免複合索引問題，在客戶端過濾日期
        val registration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection(WATER_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("WaterIntake", "Snapshot listener error: ${e.message}", e)
                    // 即使有錯誤也嘗試發送 0，不要讓 UI 卡住
                    trySend(0.0)
                    return@addSnapshotListener
                }

                val allRecords = snapshot?.documents ?: emptyList()
                Log.d("WaterIntake", "收到 ${allRecords.size} 筆總水分記錄，開始過濾今日...")

                // 在客戶端過濾今日的記錄
                val todayRecords = allRecords.filter { doc ->
                    val epochMillis = doc.getLong("timestampEpochMillis")
                    if (epochMillis != null) {
                        epochMillis in startMillis until endMillis
                    } else {
                        // 如果沒有 timestampEpochMillis，嘗試從 timestamp 欄位取得
                        val ts = doc.getTimestamp("timestamp")
                        if (ts != null) {
                            val millis = ts.toDate().time
                            millis in startMillis until endMillis
                        } else {
                            false
                        }
                    }
                }

                Log.d("WaterIntake", "過濾後今日有 ${todayRecords.size} 筆水分記錄")

                val total = todayRecords.sumOf { doc ->
                    val amount = doc.getDouble("amount") ?: doc.getDouble("amountMl") ?: 0.0
                    Log.d("WaterIntake", "記錄 ${doc.id}: amount=$amount ml")
                    amount
                }

                Log.d("WaterIntake", "今日總水分攝取: $total ml")
                trySend(total)
            }

        awaitClose {
            Log.d("WaterIntake", "停止監聽水分攝取")
            registration.remove()
        }
    }

    // ==================== 餐食時間分析 ====================

    /**
     * 分析今日餐食時間分佈
     */
    fun analyzeMealTiming(foodLogs: List<com.example.fitness.activity.ActivityRecord>): MealTimingAnalysis {
        val breakfast = mutableListOf<com.example.fitness.activity.ActivityRecord>()
        val lunch = mutableListOf<com.example.fitness.activity.ActivityRecord>()
        val dinner = mutableListOf<com.example.fitness.activity.ActivityRecord>()
        val snacks = mutableListOf<com.example.fitness.activity.ActivityRecord>()

        foodLogs.forEach { log ->
            val hour = log.start.atZone(ZoneId.systemDefault()).hour
            when {
                hour in 5..10 -> breakfast.add(log)
                hour in 11..14 -> lunch.add(log)
                hour in 17..21 -> dinner.add(log)
                else -> snacks.add(log)
            }
        }

        return MealTimingAnalysis(
            breakfast = calculateNutritionSummary(breakfast),
            lunch = calculateNutritionSummary(lunch),
            dinner = calculateNutritionSummary(dinner),
            snacks = calculateNutritionSummary(snacks)
        )
    }

    private fun calculateNutritionSummary(logs: List<com.example.fitness.activity.ActivityRecord>): NutritionSummary {
        return NutritionSummary(
            calories = logs.sumOf { it.calories ?: 0.0 },
            protein = logs.sumOf { it.proteinGrams ?: 0.0 },
            carbs = logs.sumOf { it.carbsGrams ?: 0.0 },
            fat = logs.sumOf { it.fatGrams ?: 0.0 }
        )
    }

    // ==================== 營養評分系統 ====================

    /**
     * 計算營養評分
     */
    fun calculateNutritionScore(
        consumedCalories: Double,
        targetCalories: Double,
        consumedProtein: Double,
        targetProtein: Double,
        consumedCarbs: Double,
        targetCarbs: Double,
        consumedFat: Double,
        targetFat: Double,
        waterIntake: Double,
        waterGoal: Double
    ): NutritionScore {
        val recommendations = mutableListOf<String>()

        // 計算宏量營養素平衡分數
        val proteinRatio = (consumedProtein / targetProtein).coerceIn(0.0, 1.5)
        val carbsRatio = (consumedCarbs / targetCarbs).coerceIn(0.0, 1.5)
        val fatRatio = (consumedFat / targetFat).coerceIn(0.0, 1.5)

        val macroScore = ((proteinRatio + carbsRatio + fatRatio) / 3.0 * 100).toInt()
            .coerceIn(0, 100)

        if (consumedProtein < targetProtein * 0.8) {
            recommendations.add("蛋白質攝取不足，建議增加優質蛋白質來源")
        }
        if (consumedCarbs > targetCarbs * 1.2) {
            recommendations.add("碳水化合物攝取過多，注意控制精製糖類")
        }

        // 計算水分攝取分數
        val hydrationScore = ((waterIntake / waterGoal) * 100).toInt().coerceIn(0, 100)

        if (waterIntake < waterGoal * 0.7) {
            recommendations.add("水分攝取不足，記得多喝水")
        }

        // 計算總分
        val overallScore = ((macroScore + hydrationScore) / 2).coerceIn(0, 100)

        if (recommendations.isEmpty()) {
            recommendations.add("營養攝取均衡，繼續保持！")
        }

        return NutritionScore(
            overallScore = overallScore,
            macroBalance = macroScore,
            micronutrients = 70, // 暫時固定值，需要實際數據
            mealTiming = 80,
            hydration = hydrationScore,
            recommendations = recommendations
        )
    }
}
