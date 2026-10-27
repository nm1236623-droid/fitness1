package com.example.fitness.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class FirebaseTrainingRecordRepository : TrainingRecordRepository() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val collection = firestore.collection("training_records")
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override val _records = MutableStateFlow<List<TrainingRecord>>(emptyList())
    override val records = _records.asStateFlow()

    init {
        auth.addAuthStateListener {
            listenForUpdates()
        }
        listenForUpdates()
    }

    fun listenForUpdates() {
        listenerRegistration?.remove()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("FirebaseTrainingRepo", "開始監聽所有訓練紀錄: userId=${currentUser.uid}")

            listenerRegistration = collection
                .whereEqualTo("userId", currentUser.uid)
                // 只使用單一 orderBy 避免複合索引需求
                .orderBy("dateEpochDay", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseTrainingRepo", "Listen failed: ${error.message}", error)
                        // 如果 dateEpochDay 排序失敗，嘗試不排序
                        if (error.message?.contains("index") == true) {
                            Log.w("FirebaseTrainingRepo", "索引問題，嘗試不排序查詢")
                            listenForUpdatesWithoutOrder()
                        }
                        return@addSnapshotListener
                    }

                    val trainingRecords = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val data = doc.data

                            val parsedDate: LocalDate? = run {
                                val dateStr = data?.get("date") as? String
                                val byStr = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                if (byStr != null) return@run byStr

                                val epochDay = (data?.get("dateEpochDay") as? Number)?.toLong()
                                if (epochDay != null) return@run LocalDate.ofEpochDay(epochDay)

                                null
                            }

                            if (parsedDate == null) {
                                Log.w(
                                    "FirebaseTrainingRepo",
                                    "Skip training record ${doc.id} because date is missing/invalid"
                                )
                                return@mapNotNull null
                            }

                            @Suppress("UNCHECKED_CAST")
                            val exercisesRaw = data?.get("exercises") as? List<Map<String, Any>>

                            TrainingRecord(
                                id = doc.id,
                                planId = data?.get("planId") as? String ?: "",
                                planName = data?.get("planName") as? String ?: "",
                                date = parsedDate,
                                durationInSeconds = (data?.get("durationInSeconds") as? Number)?.toInt() ?: 0,
                                caloriesBurned = (data?.get("caloriesBurned") as? Number)?.toDouble() ?: 0.0,
                                exercises = exercisesRaw?.map { ex ->
                                    ExerciseRecord(
                                        name = ex["name"] as? String ?: "",
                                        sets = (ex["sets"] as? Number)?.toInt() ?: 0,
                                        reps = (ex["reps"] as? Number)?.toInt() ?: 0,
                                        weight = (ex["weight"] as? Number)?.toDouble()
                                    )
                                } ?: emptyList(),
                                notes = data?.get("notes") as? String,
                                userId = data?.get("userId") as? String
                            )
                        } catch (e: Exception) {
                            Log.e("FirebaseTrainingRepo", "Error parsing document ${doc.id}: ${e.message}")
                            null
                        }
                    } ?: emptyList()

                    Log.d("FirebaseTrainingRepo", "載入 ${trainingRecords.size} 筆訓練紀錄")
                    _records.value = trainingRecords
                }
        } else {
            Log.w("FirebaseTrainingRepo", "用戶未登入，無法監聽訓練紀錄")
        }
    }

    /**
     * 備用方法：不使用排序的查詢（當索引不存在時使用）
     */
    private fun listenForUpdatesWithoutOrder() {
        val currentUser = auth.currentUser ?: return

        collection
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseTrainingRepo", "Fallback listen failed: ${error.message}")
                    return@addSnapshotListener
                }

                val trainingRecords = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data

                        val parsedDate: LocalDate? = run {
                            val dateStr = data?.get("date") as? String
                            val byStr = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            if (byStr != null) return@run byStr

                            val epochDay = (data?.get("dateEpochDay") as? Number)?.toLong()
                            if (epochDay != null) return@run LocalDate.ofEpochDay(epochDay)

                            null
                        }

                        if (parsedDate == null) return@mapNotNull null

                        @Suppress("UNCHECKED_CAST")
                        val exercisesRaw = data?.get("exercises") as? List<Map<String, Any>>

                        TrainingRecord(
                            id = doc.id,
                            planId = data?.get("planId") as? String ?: "",
                            planName = data?.get("planName") as? String ?: "",
                            date = parsedDate,
                            durationInSeconds = (data?.get("durationInSeconds") as? Number)?.toInt() ?: 0,
                            caloriesBurned = (data?.get("caloriesBurned") as? Number)?.toDouble() ?: 0.0,
                            exercises = exercisesRaw?.map { ex ->
                                ExerciseRecord(
                                    name = ex["name"] as? String ?: "",
                                    sets = (ex["sets"] as? Number)?.toInt() ?: 0,
                                    reps = (ex["reps"] as? Number)?.toInt() ?: 0,
                                    weight = (ex["weight"] as? Number)?.toDouble()
                                )
                            } ?: emptyList(),
                            notes = data?.get("notes") as? String,
                            userId = data?.get("userId") as? String
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()

                Log.d("FirebaseTrainingRepo", "Fallback: 載入 ${trainingRecords.size} 筆訓練紀錄")
                _records.value = trainingRecords
            }
    }

    /**
     * 不要再做本地 optimistic add（那會造成隔天重新解析時用 LocalDate.now() 覆蓋）。
     * UI 請改呼叫 [addRecordToFirebase]。
     */
    @Deprecated("Use addRecordToFirebase(record) instead")
    override fun addRecord(record: TrainingRecord) {
        // no-op
        Log.w("FirebaseTrainingRepo", "addRecord() is deprecated. Use addRecordToFirebase().")
    }

    suspend fun addRecordToFirebase(record: TrainingRecord): Result<String> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            val recordWithUser = record.copy(userId = currentUser.uid)
            val data = mapOf(
                "planId" to recordWithUser.planId,
                "planName" to recordWithUser.planName,
                // 固定存當日字串（由呼叫端決定要存哪一天）
                "date" to recordWithUser.date.toString(),
                // 用於穩定排序/查詢
                "dateEpochDay" to recordWithUser.date.toEpochDay(),
                "durationInSeconds" to recordWithUser.durationInSeconds,
                "caloriesBurned" to recordWithUser.caloriesBurned,
                "exercises" to recordWithUser.exercises.map { ex ->
                    mapOf(
                        "name" to ex.name,
                        "sets" to ex.sets,
                        "reps" to ex.reps,
                        "weight" to ex.weight
                    )
                },
                "notes" to recordWithUser.notes,
                "userId" to recordWithUser.userId,
                // 僅用於排序/後台查核，不用推算 date
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val documentRef = collection.add(data).await()
            Result.success(documentRef.id)
        }
    } catch (e: Exception) {
        Log.e("FirebaseTrainingRepo", "Failed to add training record: ${e.message}", e)
        Result.failure(e)
    }

    fun observeRecordsForDate(date: LocalDate): Flow<List<TrainingRecord>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w("FirebaseTrainingRepo", "observeRecordsForDate: 用戶未登入")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val epochDay = date.toEpochDay()
        Log.d("FirebaseTrainingRepo", "開始監聽訓練紀錄: userId=$uid, date=$date, epochDay=$epochDay")

        // 注意：只使用 equality 查詢，不使用 orderBy 以避免複合索引需求
        // 排序在客戶端進行
        val registration = collection
            .whereEqualTo("userId", uid)
            .whereEqualTo("dateEpochDay", epochDay)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseTrainingRepo", "observeRecordsForDate 監聽失敗: ${error.message}", error)
                    // 嘗試檢查是否為索引錯誤
                    if (error.message?.contains("index") == true) {
                        Log.e("FirebaseTrainingRepo", "可能需要建立 Firestore 複合索引")
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: emptyList()
                Log.d("FirebaseTrainingRepo", "收到 ${documents.size} 筆訓練紀錄 for date=$date")

                val records = documents.mapNotNull { doc ->
                    try {
                        val data = doc.data
                        if (data == null) {
                            Log.w("FirebaseTrainingRepo", "文檔 ${doc.id} 資料為空")
                            return@mapNotNull null
                        }

                        val dateStr = data["date"] as? String
                        val parsedDate = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date

                        @Suppress("UNCHECKED_CAST")
                        val exercisesRaw = data["exercises"] as? List<Map<String, Any>>

                        val record = TrainingRecord(
                            id = doc.id,
                            planId = data["planId"] as? String ?: "",
                            planName = data["planName"] as? String ?: "",
                            date = parsedDate,
                            durationInSeconds = (data["durationInSeconds"] as? Number)?.toInt() ?: 0,
                            caloriesBurned = (data["caloriesBurned"] as? Number)?.toDouble() ?: 0.0,
                            exercises = exercisesRaw?.map { ex ->
                                ExerciseRecord(
                                    name = ex["name"] as? String ?: "",
                                    sets = (ex["sets"] as? Number)?.toInt() ?: 0,
                                    reps = (ex["reps"] as? Number)?.toInt() ?: 0,
                                    weight = (ex["weight"] as? Number)?.toDouble()
                                )
                            } ?: emptyList(),
                            notes = data["notes"] as? String,
                            userId = data["userId"] as? String
                        )

                        Log.d("FirebaseTrainingRepo", "解析紀錄成功: id=${doc.id}, planName=${record.planName}")
                        record
                    } catch (e: Exception) {
                        Log.e("FirebaseTrainingRepo", "解析紀錄 ${doc.id} 失敗: ${e.message}", e)
                        null
                    }
                }

                // 在客戶端排序（按時間降序）
                val sortedRecords = records.sortedByDescending { it.id }

                Log.d("FirebaseTrainingRepo", "成功解析 ${sortedRecords.size} 筆訓練紀錄")
                trySend(sortedRecords)
            }

        awaitClose {
            Log.d("FirebaseTrainingRepo", "停止監聽訓練紀錄 for date=$date")
            registration.remove()
        }
    }

    suspend fun updateRecord(record: TrainingRecord): Result<Unit> = try {
        val data = mapOf(
            "planId" to record.planId,
            "planName" to record.planName,
            "date" to record.date.toString(),
            "dateEpochDay" to record.date.toEpochDay(),
            "durationInSeconds" to record.durationInSeconds,
            "caloriesBurned" to record.caloriesBurned,
            "exercises" to record.exercises.map { ex ->
                mapOf(
                    "name" to ex.name,
                    "sets" to ex.sets,
                    "reps" to ex.reps,
                    "weight" to ex.weight
                )
            },
            "notes" to record.notes,
            "userId" to record.userId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        collection.document(record.id).set(data).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseTrainingRepo", "Failed to update training record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun deleteRecord(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseTrainingRepo", "Failed to delete training record: ${e.message}", e)
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
            docs.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()

            Result.success(Unit)
        }
    } catch (e: Exception) {
        Log.e("FirebaseTrainingRepo", "Failed to clear training records: ${e.message}", e)
        Result.failure(e)
    }

    override fun getRecordsForDate(date: LocalDate): List<TrainingRecord> {
        return _records.value.filter { it.date == date }
    }

    override fun getRecordsForPlan(planId: String): List<TrainingRecord> {
        return _records.value.filter { it.planId == planId }
    }
}
