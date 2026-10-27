package com.example.fitness.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 課表模板 Repository
 * 管理使用者保存的訓練課表模板
 */
object WorkoutTemplateRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private const val COLLECTION = "workout_templates"

    /**
     * 保存課表模板
     */
    suspend fun saveTemplate(template: WorkoutTemplate): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return Result.failure(Exception("用戶未登入"))
            }

            val templateWithUser = template.copy(userId = userId)
            val data = mapOf(
                "id" to templateWithUser.id,
                "name" to templateWithUser.name,
                "description" to templateWithUser.description,
                "goal" to templateWithUser.goal,
                "daysPerWeek" to templateWithUser.daysPerWeek,
                "experienceLevel" to templateWithUser.experienceLevel,
                "days" to templateWithUser.days.map { day ->
                    mapOf(
                        "day" to day.day,
                        "focus" to day.focus,
                        "exercises" to day.exercises.map { ex ->
                            mapOf(
                                "name" to ex.name,
                                "sets" to ex.sets,
                                "reps" to ex.reps,
                                "weightKg" to ex.weightKg,
                                "restSeconds" to ex.restSeconds
                            )
                        }
                    )
                },
                "userId" to templateWithUser.userId,
                "createdAt" to templateWithUser.createdAt,
                "isPublic" to templateWithUser.isPublic
            )

            firestore.collection(COLLECTION)
                .document(templateWithUser.id)
                .set(data)
                .await()

            Log.d("WorkoutTemplate", "模板保存成功: ${templateWithUser.name}")
            Result.success(templateWithUser.id)
        } catch (e: Exception) {
            Log.e("WorkoutTemplate", "保存模板失敗", e)
            Result.failure(e)
        }
    }

    /**
     * 獲取我的課表模板列表
     */
    fun observeMyTemplates(): Flow<List<WorkoutTemplate>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkoutTemplate", "監聽模板失敗", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val templates = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseTemplate(doc.data ?: return@mapNotNull null)
                    } catch (e: Exception) {
                        Log.e("WorkoutTemplate", "解析模板失敗: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(templates)
            }

        awaitClose { registration.remove() }
    }

    /**
     * 獲取公開的課表模板
     */
    fun observePublicTemplates(): Flow<List<WorkoutTemplate>> = callbackFlow {
        val registration = firestore.collection(COLLECTION)
            .whereEqualTo("isPublic", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkoutTemplate", "監聽公開模板失敗", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val templates = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseTemplate(doc.data ?: return@mapNotNull null)
                    } catch (e: Exception) {
                        Log.e("WorkoutTemplate", "解析公開模板失敗: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(templates)
            }

        awaitClose { registration.remove() }
    }

    /**
     * 刪除課表模板
     */
    suspend fun deleteTemplate(templateId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION)
                .document(templateId)
                .delete()
                .await()

            Log.d("WorkoutTemplate", "模板刪除成功: $templateId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkoutTemplate", "刪除模板失敗", e)
            Result.failure(e)
        }
    }

    /**
     * 獲取單個模板
     */
    suspend fun getTemplate(templateId: String): Result<WorkoutTemplate> {
        return try {
            val doc = firestore.collection(COLLECTION)
                .document(templateId)
                .get()
                .await()

            val data = doc.data
            if (data == null) {
                return Result.failure(Exception("模板不存在"))
            }

            val template = parseTemplate(data)
            Result.success(template)
        } catch (e: Exception) {
            Log.e("WorkoutTemplate", "獲取模板失敗", e)
            Result.failure(e)
        }
    }

    private fun parseTemplate(data: Map<String, Any>): WorkoutTemplate {
        @Suppress("UNCHECKED_CAST")
        val daysData = data["days"] as? List<Map<String, Any>> ?: emptyList()

        return WorkoutTemplate(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            goal = data["goal"] as? String ?: "增肌",
            daysPerWeek = (data["daysPerWeek"] as? Number)?.toInt() ?: 4,
            experienceLevel = data["experienceLevel"] as? String ?: "中階",
            days = daysData.map { dayData ->
                @Suppress("UNCHECKED_CAST")
                val exercisesData = dayData["exercises"] as? List<Map<String, Any>> ?: emptyList()

                TemplateDay(
                    day = (dayData["day"] as? Number)?.toInt() ?: 1,
                    focus = dayData["focus"] as? String ?: "",
                    exercises = exercisesData.map { exData ->
                        TemplateExercise(
                            name = exData["name"] as? String ?: "",
                            sets = (exData["sets"] as? Number)?.toInt() ?: 0,
                            reps = (exData["reps"] as? Number)?.toInt() ?: 0,
                            weightKg = (exData["weightKg"] as? Number)?.toFloat(),
                            restSeconds = (exData["restSeconds"] as? Number)?.toInt()
                        )
                    }
                )
            },
            userId = data["userId"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            isPublic = data["isPublic"] as? Boolean ?: false
        )
    }
}

