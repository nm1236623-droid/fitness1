package com.example.fitness.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

object FirebaseDietRecordRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 統一飲食日誌 collection：所有畫面/AI 儲存/首頁進度條都看同一個
    private val collection = firestore.collection("food_logs")

    private val _records = MutableStateFlow<List<DietRecord>>(emptyList())
    val records = _records.asStateFlow()

    // 追蹤目前 listener 綁定的 uid，避免重複註冊
    private var listenerUid: String? = null
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        listenForUpdates()
    }

    /**
     * 啟動/重新啟動 Firestore 即時監聽。
     * 可從外部呼叫（如登入後），會自動移除舊 listener。
     */
    fun listenForUpdates() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("FirebaseDietRecordRepo", "listenForUpdates: user is null, skipping")
            return
        }
        // 如果已經在監聽同一個 uid，不需要重複註冊
        if (listenerUid == currentUser.uid && listenerRegistration != null) return

        // 移除舊 listener
        listenerRegistration?.remove()
        listenerUid = currentUser.uid

        Log.d("FirebaseDietRecordRepo", "Starting listener for uid=${currentUser.uid}")
        listenerRegistration = collection
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseDietRecordRepo", "Listen failed: ${error.message}")
                    return@addSnapshotListener
                }

                val dietRecords = snapshot?.documents?.mapNotNull { doc ->
                    parseDietRecord(doc)
                } ?: emptyList()

                _records.value = dietRecords
                Log.d("FirebaseDietRecordRepo", "Loaded ${dietRecords.size} diet records from Firestore")
            }
    }

    private fun parseDietRecord(doc: com.google.firebase.firestore.DocumentSnapshot): DietRecord? {
        return try {
            val data = doc.data ?: return null

            val dateIso = data["dateIso"] as? String
            // 不要用 LocalDate.now() 當 fallback，避免「昨天的紀錄隔天變今天」的視覺錯誤
            val parsedDate = runCatching {
                dateIso?.let { LocalDate.parse(it) }
            }.getOrNull() ?: return null

            // timestamp 統一用 epochMillis(Long)
            val epochMillis = (data["timestamp"] as? Number)?.toLong() ?: 0L

            @Suppress("UNCHECKED_CAST")
            val items = data["items"] as? List<String>

            DietRecord(
                id = doc.id,
                foodName = data["foodName"] as? String ?: "",
                calories = (data["calories"] as? Number)?.toInt() ?: 0,
                date = parsedDate,
                mealType = data["mealType"] as? String ?: "",
                proteinG = (data["proteinG"] as? Number)?.toDouble(),
                carbsG = (data["carbsG"] as? Number)?.toDouble(),
                fatG = (data["fatG"] as? Number)?.toDouble(),
                timestamp = epochMillis,
                userId = data["userId"] as? String,
                items = items,
                rawAnalysisText = data["rawAnalysisText"] as? String
            )
        } catch (e: Exception) {
            Log.e("FirebaseDietRecordRepo", "Error parsing document ${doc.id}: ${e.message}")
            null
        }
    }

    suspend fun addRecord(record: DietRecord): Result<String> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            // 確保 listener 在運行
            listenForUpdates()

            val recordWithUser = record.copy(
                userId = currentUser.uid,
                timestamp = if (record.timestamp > 0L) record.timestamp else System.currentTimeMillis()
            )
            val data = hashMapOf(
                "foodName" to recordWithUser.foodName,
                "calories" to recordWithUser.calories,
                "dateIso" to recordWithUser.date.toString(),
                "mealType" to recordWithUser.mealType,
                "proteinG" to recordWithUser.proteinG,
                "carbsG" to recordWithUser.carbsG,
                "fatG" to recordWithUser.fatG,
                // 統一：epoch millis
                "timestamp" to recordWithUser.timestamp,
                "userId" to recordWithUser.userId,
                "items" to recordWithUser.items,
                "rawAnalysisText" to recordWithUser.rawAnalysisText
            )

            val documentRef = collection.add(data).await()
            Log.d("FirebaseDietRecordRepo", "Successfully added diet record with ID: ${documentRef.id}")

            // ★ 立即更新本地 _records，不等待 Firestore listener 回傳
            val savedRecord = recordWithUser.copy(id = documentRef.id)
            val current = _records.value.toMutableList()
            current.add(0, savedRecord)
            _records.value = current
            Log.d("FirebaseDietRecordRepo", "Immediately added to local records, total: ${current.size}")

            Result.success(documentRef.id)
        }
    } catch (e: Exception) {
        Log.e("FirebaseDietRecordRepo", "Failed to add diet record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun updateRecord(record: DietRecord): Result<Unit> = try {
        val data = hashMapOf(
            "foodName" to record.foodName,
            "calories" to record.calories,
            "dateIso" to record.date.toString(),
            "mealType" to record.mealType,
            "proteinG" to record.proteinG,
            "carbsG" to record.carbsG,
            "fatG" to record.fatG,
            "timestamp" to record.timestamp,
            "userId" to record.userId,
            "items" to record.items,
            "rawAnalysisText" to record.rawAnalysisText
        )

        collection.document(record.id).set(data).await()
        Log.d("FirebaseDietRecordRepo", "Successfully updated diet record with ID: ${record.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseDietRecordRepo", "Failed to update diet record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun deleteRecord(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Log.d("FirebaseDietRecordRepo", "Successfully deleted diet record with ID: $id")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseDietRecordRepo", "Failed to delete diet record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun getRecordsForDateRange(startDate: LocalDate, endDate: LocalDate): Result<List<DietRecord>> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            val docs = collection
                .whereEqualTo("userId", currentUser.uid)
                .whereGreaterThanOrEqualTo("dateIso", startDate.toString())
                .whereLessThanOrEqualTo("dateIso", endDate.toString())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val dietRecords = docs.documents.mapNotNull { doc ->
                parseDietRecord(doc)
            }

            Result.success(dietRecords)
        }
    } catch (e: Exception) {
        Log.e("FirebaseDietRecordRepo", "Failed to get records for date range: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun clear(): Result<Unit> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            val docs = collection
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()
            Log.d("FirebaseDietRecordRepo", "Successfully cleared all diet records for user")
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Log.e("FirebaseDietRecordRepo", "Failed to clear diet records: ${e.message}", e)
        Result.failure(e)
    }
}
