package com.example.fitness.firestore

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore-based repository for user training records.
 *
 * Data model:
 * users/{uid}/trainingRecords/{recordId}
 *  - date: "yyyy-MM-dd"
 *  - type: String
 *  - durationMinutes: Number
 *  - exercises: Array<Map> (name, sets, reps, weight)
 */
class FirestoreTrainingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun addTrainingRecord(userId: String, record: TrainingRecord): Result<String> {
        return try {
            val doc = db.collection("users")
                .document(userId)
                .collection("trainingRecords")
                .document()

            val payload = mapOf(
                "date" to record.date.toString(),
                "type" to record.type,
                "durationMinutes" to record.durationMinutes,
                "exercises" to record.exercises.map {
                    mapOf(
                        "name" to it.name,
                        "sets" to it.sets,
                        "reps" to it.reps,
                        "weight" to it.weight
                    )
                }
            )

            doc.set(payload).await()
            Result.success(doc.id)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun getAllTrainingRecords(userId: String): Result<List<TrainingRecord>> {
        return try {
            val snap = db.collection("users")
                .document(userId)
                .collection("trainingRecords")
                .orderBy("date")
                .get()
                .await()

            val records = snap.documents.mapNotNull { doc ->
                val dateStr = doc.getString("date") ?: return@mapNotNull null
                val type = doc.getString("type") ?: ""
                val duration = (doc.getLong("durationMinutes") ?: 0L).toInt()

                @Suppress("UNCHECKED_CAST")
                val exercisesRaw = doc.get("exercises") as? List<Map<String, Any?>> ?: emptyList()

                val exercises = exercisesRaw.mapNotNull { m ->
                    val name = m["name"] as? String ?: return@mapNotNull null
                    val sets = (m["sets"] as? Number)?.toInt() ?: 0
                    val reps = (m["reps"] as? Number)?.toInt() ?: 0
                    val weight = (m["weight"] as? Number)?.toFloat() ?: 0f
                    ExerciseEntry(name = name, sets = sets, reps = reps, weight = weight)
                }

                TrainingRecord(
                    id = doc.id,
                    date = LocalDate.parse(dateStr),
                    type = type,
                    durationMinutes = duration,
                    exercises = exercises
                )
            }

            Result.success(records)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

