package com.example.fitness.activity

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.*

object FirebaseActivityLogRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val collection = firestore.collection("activity_logs")

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private val _records = MutableStateFlow<List<ActivityRecord>>(emptyList())
    val records = _records.asStateFlow()

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
            listenerRegistration = collection
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("start", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseActivityLogRepo", "Listen failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    val activityRecords = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null

                            // 關鍵修正：start 是用來判斷「今日」的欄位，缺值時不能用 Instant.now() 頂替，
                            // 否則任何壞資料都會被算進今天，造成「今日消耗」莫名固定數字。
                            val startTs = data["start"] as? Timestamp ?: run {
                                Log.w("FirebaseActivityLogRepo", "Skip document ${doc.id} because missing start")
                                return@mapNotNull null
                            }

                            val calories = (data["calories"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }

                            ActivityRecord(
                                id = doc.id,
                                planId = data["planId"] as? String,
                                type = data["type"] as? String ?: "",
                                start = startTs.toDate().toInstant(),
                                end = (data["end"] as? Timestamp)?.toDate()?.toInstant(),
                                calories = calories,
                                proteinGrams = (data["proteinGrams"] as? Number)?.toDouble(),
                                carbsGrams = (data["carbsGrams"] as? Number)?.toDouble(),
                                fatGrams = (data["fatGrams"] as? Number)?.toDouble(),
                                exercises = (data["exercises"] as? List<Map<String, Any>>)?.map { ex ->
                                    com.example.fitness.data.ExerciseEntry(
                                        name = ex["name"] as? String ?: "",
                                        sets = (ex["sets"] as? Number)?.toInt() ?: 0,
                                        reps = (ex["reps"] as? Number)?.toInt() ?: 0,
                                        weight = (ex["weight"] as? Number)?.toFloat()
                                    )
                                } ?: emptyList(),
                                userId = data["userId"] as? String
                            )
                        } catch (e: Exception) {
                            Log.e("FirebaseActivityLogRepo", "Error parsing document ${doc.id}: ${e.message}")
                            null
                        }
                    } ?: emptyList()

                    _records.value = activityRecords
                    Log.d("FirebaseActivityLogRepo", "Loaded ${activityRecords.size} activity records from Firestore")
                }
        }
    }

    suspend fun addRecord(record: ActivityRecord): Result<String> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            val recordWithUser = record.copy(userId = currentUser.uid)
            val data = mapOf(
                "planId" to recordWithUser.planId,
                "type" to recordWithUser.type,
                "start" to Timestamp(recordWithUser.start.epochSecond, recordWithUser.start.nano),
                "end" to recordWithUser.end?.let { Timestamp(it.epochSecond, it.nano) },
                "calories" to recordWithUser.calories,
                "proteinGrams" to recordWithUser.proteinGrams,
                "carbsGrams" to recordWithUser.carbsGrams,
                "fatGrams" to recordWithUser.fatGrams,
                "exercises" to recordWithUser.exercises?.map { ex ->
                    mapOf(
                        "name" to ex.name,
                        "sets" to ex.sets,
                        "reps" to ex.reps,
                        "weight" to ex.weight
                    )
                },
                "userId" to recordWithUser.userId
            )

            val documentRef = collection.add(data).await()
            Log.d("FirebaseActivityLogRepo", "Successfully added activity record with ID: ${documentRef.id}")
            Result.success(documentRef.id)
        }
    } catch (e: Exception) {
        Log.e("FirebaseActivityLogRepo", "Failed to add activity record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun updateRecord(record: ActivityRecord): Result<Unit> = try {
        val data = mapOf(
            "planId" to record.planId,
            "type" to record.type,
            "start" to Timestamp(record.start.epochSecond, record.start.nano),
            "end" to record.end?.let { Timestamp(it.epochSecond, it.nano) },
            "calories" to record.calories,
            "proteinGrams" to record.proteinGrams,
            "carbsGrams" to record.carbsGrams,
            "fatGrams" to record.fatGrams,
            "exercises" to record.exercises?.map { ex ->
                mapOf(
                    "name" to ex.name,
                    "sets" to ex.sets,
                    "reps" to ex.reps,
                    "weight" to ex.weight
                )
            },
            "userId" to record.userId
        )

        collection.document(record.id).set(data).await()
        Log.d("FirebaseActivityLogRepo", "Successfully updated activity record with ID: ${record.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseActivityLogRepo", "Failed to update activity record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun deleteRecord(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Log.d("FirebaseActivityLogRepo", "Successfully deleted activity record with ID: $id")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseActivityLogRepo", "Failed to delete activity record: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun getRecordsForDateRange(startDate: Instant, endDate: Instant): Result<List<ActivityRecord>> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Result.failure(Exception("User not authenticated"))
        } else {
            val docs = collection
                .whereEqualTo("userId", currentUser.uid)
                .whereGreaterThanOrEqualTo("start", startDate)
                .whereLessThanOrEqualTo("start", endDate)
                .orderBy("start", Query.Direction.DESCENDING)
                .get()
                .await()

            val activityRecords = docs.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    ActivityRecord(
                        id = doc.id,
                        planId = data?.get("planId") as? String,
                        type = data?.get("type") as? String ?: "",
                        start = (data?.get("start") as? Timestamp)?.toInstant() ?: Instant.now(),
                        end = (data?.get("end") as? Timestamp)?.toInstant(),
                        calories = (data?.get("calories") as? Number)?.toDouble(),
                        proteinGrams = (data?.get("proteinGrams") as? Number)?.toDouble(),
                        carbsGrams = (data?.get("carbsGrams") as? Number)?.toDouble(),
                        fatGrams = (data?.get("fatGrams") as? Number)?.toDouble(),
                        exercises = (data?.get("exercises") as? List<Map<String, Any>>)?.map { ex ->
                            com.example.fitness.data.ExerciseEntry(
                                name = ex["name"] as? String ?: "",
                                sets = (ex["sets"] as? Number)?.toInt() ?: 0,
                                reps = (ex["reps"] as? Number)?.toInt() ?: 0,
                                weight = (ex["weight"] as? Number)?.toFloat()
                            )
                        } ?: emptyList(),
                        userId = data?.get("userId") as? String
                    )
                } catch (e: Exception) {
                    Log.e("FirebaseActivityLogRepo", "Error parsing document ${doc.id}: ${e.message}")
                    null
                }
            }

            Result.success(activityRecords)
        }
    } catch (e: Exception) {
        Log.e("FirebaseActivityLogRepo", "Failed to get records for date range: ${e.message}", e)
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
            Log.d("FirebaseActivityLogRepo", "Successfully cleared all activity records for user")
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Log.e("FirebaseActivityLogRepo", "Failed to clear activity records: ${e.message}", e)
        Result.failure(e)
    }
}