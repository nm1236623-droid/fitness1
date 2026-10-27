@file:Suppress("unused")

package com.example.fitness.coach.cloud

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Coach-managed list of trainees so the coach can target publishing.
 *
 * Path:
 *  coaches/{coachId}/trainees/{traineeId}
 * Payload:
 *  { traineeId: String, displayName: String, createdAtEpochMs: Number }
 */
class CoachTraineeDirectoryRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
) {
    suspend fun upsertTrainee(coachId: String, traineeId: String, displayName: String): Result<Unit> {
        return try {
            val doc = db.collection("coaches")
                .document(coachId)
                .collection("trainees")
                .document(traineeId)

            val payload = mapOf(
                "traineeId" to traineeId,
                "displayName" to displayName.trim(),
                "updatedAtEpochMs" to System.currentTimeMillis(),
            )
            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun listTrainees(coachId: String): Result<List<CoachTrainee>> {
        return try {
            val snap = db.collection("coaches")
                .document(coachId)
                .collection("trainees")
                .get()
                .await()

            val list = snap.documents.mapNotNull { d ->
                val traineeId = d.getString("traineeId") ?: d.id
                val name = d.getString("displayName") ?: ""
                CoachTrainee(traineeId = traineeId, displayName = name)
            }.sortedBy { it.displayName.ifBlank { it.traineeId } }

            Result.success(list)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun deleteTrainee(coachId: String, traineeId: String): Result<Unit> {
        return try {
            db.collection("coaches")
                .document(coachId)
                .collection("trainees")
                .document(traineeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun listenTrainees(
        coachId: String,
        onChange: (Result<List<CoachTrainee>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("coaches")
            .document(coachId)
            .collection("trainees")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { d ->
                    val traineeId = d.getString("traineeId") ?: d.id
                    val name = d.getString("displayName") ?: ""
                    CoachTrainee(traineeId = traineeId, displayName = name)
                }.sortedBy { it.displayName.ifBlank { it.traineeId } }
                onChange(Result.success(list))
            }
    }
}

data class CoachTrainee(
    val traineeId: String,
    val displayName: String,
)
