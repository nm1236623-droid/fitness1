package com.example.fitness.coach.cloud

import com.example.fitness.data.ExerciseEntry
import com.example.fitness.data.TrainingPlan
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.Instant
import kotlinx.coroutines.tasks.await

/**
 * Cloud sync for coach/trainee plans.
 *
 * Collections:
 *  - coaches/{coachId}/plans/{planId}
 *  - trainees/{traineeId}/memberships/{coachId}
 */
class CoachCloudRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
) {

    suspend fun publishPlan(coachId: String, plan: TrainingPlan): Result<Unit> {
        return try {
            val doc = db.collection("coaches")
                .document(coachId)
                .collection("plans")
                .document(plan.id)

            val now = System.currentTimeMillis()
            val createdAtMs = plan.createdAt.toEpochMilli()
            val publishedAtMs = (plan.publishedAt ?: Instant.ofEpochMilli(now)).toEpochMilli()

            val payload = mapOf(
                "id" to plan.id,
                "coachId" to coachId,
                "name" to plan.name,
                "createdAtEpochMs" to createdAtMs,
                "publishedAtEpochMs" to publishedAtMs,
                "updatedAtEpochMs" to now,
                "exercises" to plan.exercises.map {
                    mapOf(
                        "name" to it.name,
                        "sets" to it.sets,
                        "reps" to it.reps,
                        "weight" to it.weight
                    )
                }
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun joinCoach(traineeId: String, coachId: String): Result<Unit> {
        return try {
            val doc = db.collection("trainees")
                .document(traineeId)
                .collection("memberships")
                .document(coachId)

            val payload = mapOf(
                "coachId" to coachId,
                "joinedAtEpochMs" to System.currentTimeMillis(),
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Read current memberships once.
     * Collection: trainees/{traineeId}/memberships/{coachId}
     */
    suspend fun getJoinedCoachIds(traineeId: String): Result<List<String>> {
        return try {
            val snap = db.collection("trainees")
                .document(traineeId)
                .collection("memberships")
                .get()
                .await()

            val coachIds = snap.documents.mapNotNull { it.id.ifBlank { null } }
            Result.success(coachIds)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Listen to memberships in real-time.
     * Useful for auto reconnect sync and UI showing joined coaches.
     */
    fun listenJoinedCoachIds(
        traineeId: String,
        onChange: (Result<List<String>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("trainees")
            .document(traineeId)
            .collection("memberships")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val coachIds = snap.documents.mapNotNull { it.id.ifBlank { null } }
                onChange(Result.success(coachIds))
            }
    }

    fun listenCoachPlans(
        coachId: String,
        onChange: (Result<List<TrainingPlan>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("coaches")
            .document(coachId)
            .collection("plans")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val plans = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null

                    val createdAtMs = (doc.get("createdAtEpochMs") as? Number)?.toLong()
                    val publishedAtMs = (doc.get("publishedAtEpochMs") as? Number)?.toLong()

                    @Suppress("UNCHECKED_CAST")
                    val exercisesRaw = doc.get("exercises") as? List<Map<String, Any?>> ?: emptyList()
                    val exercises = exercisesRaw.mapNotNull { m ->
                        val n = m["name"] as? String ?: return@mapNotNull null
                        val sets = (m["sets"] as? Number)?.toInt()
                        val reps = (m["reps"] as? Number)?.toInt()
                        val weight = (m["weight"] as? Number)?.toFloat()
                        ExerciseEntry(name = n, sets = sets, reps = reps, weight = weight)
                    }

                    TrainingPlan(
                        id = id,
                        name = name,
                        exercises = exercises,
                        createdAt = createdAtMs?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
                        publishedAt = publishedAtMs?.let { Instant.ofEpochMilli(it) },
                    )
                }

                onChange(Result.success(plans))
            }
    }

    /**
     * Trainee reports completion of a coach plan so the coach can view it.
     *
     * Path:
     *  coaches/{coachId}/trainees/{traineeId}/completions/{completionId}
     */
    suspend fun reportPlanCompletion(
        coachId: String,
        traineeId: String,
        planId: String,
        planName: String,
    ): Result<Unit> {
        return try {
            val completionId = db.collection("_tmp").document().id // generate random id

            val doc = db.collection("coaches")
                .document(coachId)
                .collection("trainees")
                .document(traineeId)
                .collection("completions")
                .document(completionId)

            val payload = mapOf(
                "coachId" to coachId,
                "traineeId" to traineeId,
                "planId" to planId,
                "planName" to planName,
                "completedAtEpochMs" to System.currentTimeMillis(),
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Coach listens to all trainees' completion events.
     *
     * Uses a collectionGroup query on "completions" below:
     *  coaches/{coachId}/trainees/{traineeId}/completions/{completionId}
     */
    fun listenAllCompletionsForCoach(
        coachId: String,
        onChange: (Result<List<CoachPlanCompletion>>) -> Unit,
    ): ListenerRegistration {
        return db.collectionGroup("completions")
            .whereEqualTo("coachId", coachId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { doc ->
                    val cId = doc.getString("coachId") ?: return@mapNotNull null
                    val tId = doc.getString("traineeId") ?: return@mapNotNull null
                    val planId = doc.getString("planId") ?: return@mapNotNull null
                    val planName = doc.getString("planName") ?: ""
                    val completedAtMs = (doc.get("completedAtEpochMs") as? Number)?.toLong() ?: 0L

                    CoachPlanCompletion(
                        id = doc.id,
                        coachId = cId,
                        traineeId = tId,
                        planId = planId,
                        planName = planName,
                        completedAt = java.time.Instant.ofEpochMilli(completedAtMs),
                    )
                }.sortedByDescending { it.completedAt }

                onChange(Result.success(list))
            }
    }

    /**
     * Directory collection so trainees can resolve a coach id by display name.
     *
     * (Name -> ID)
     * Collection: coachDirectory/{displayNameLower}
     */
    suspend fun upsertCoachDirectoryEntry(coachId: String, displayName: String): Result<Unit> {
        return try {
            val name = displayName.trim()
            val key = name.lowercase()
            if (key.isBlank()) return Result.failure(IllegalArgumentException("Display name is blank"))

            // Name -> ID
            val doc = db.collection("coachDirectory").document(key)
            val payload = mapOf(
                "displayName" to name,
                "displayNameLower" to key,
                "coachId" to coachId,
                "updatedAtEpochMs" to System.currentTimeMillis(),
            )
            doc.set(payload, SetOptions.merge()).await()

            // ID -> Name (reverse index)
            val reverseDoc = db.collection("coachIdDirectory").document(coachId)
            val reversePayload = mapOf(
                "coachId" to coachId,
                "displayName" to name,
                "displayNameLower" to key,
                "updatedAtEpochMs" to System.currentTimeMillis(),
            )
            reverseDoc.set(reversePayload, SetOptions.merge()).await()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Resolve coach uid by display name. Returns null if not found. */
    suspend fun findCoachIdByDisplayName(displayName: String): Result<String?> {
        return try {
            val key = displayName.trim().lowercase()
            if (key.isBlank()) return Result.success(null)

            val doc = db.collection("coachDirectory").document(key).get().await()
            if (!doc.exists()) return Result.success(null)
            Result.success(doc.getString("coachId"))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Resolve coach display name by coach uid. Returns null if not found. */
    suspend fun findCoachDisplayNameById(coachId: String): Result<String?> {
        return try {
            val id = coachId.trim()
            if (id.isBlank()) return Result.success(null)

            val doc = db.collection("coachIdDirectory").document(id).get().await()
            if (!doc.exists()) return Result.success(null)
            Result.success(doc.getString("displayName"))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Coach pushes a plan directly to a specific trainee's inbox.
     *
     * Path:
     *  trainees/{traineeId}/inboxPlans/{planId}
     */
    suspend fun publishPlanToTraineeInbox(
        coachId: String,
        traineeId: String,
        plan: TrainingPlan,
    ): Result<Unit> {
        return try {
            val doc = db.collection("trainees")
                .document(traineeId)
                .collection("inboxPlans")
                .document(plan.id)

            val now = System.currentTimeMillis()
            val createdAtMs = plan.createdAt.toEpochMilli()
            val publishedAtMs = (plan.publishedAt ?: Instant.ofEpochMilli(now)).toEpochMilli()

            val payload = mapOf(
                "id" to plan.id,
                "coachId" to coachId,
                // optional: if coach has a directory entry, UI can show a friendly name
                "coachDisplayName" to null,
                "traineeId" to traineeId,
                "name" to plan.name,
                "createdAtEpochMs" to createdAtMs,
                "publishedAtEpochMs" to publishedAtMs,
                "updatedAtEpochMs" to now,
                "exercises" to plan.exercises.map {
                    mapOf(
                        "name" to it.name,
                        "sets" to it.sets,
                        "reps" to it.reps,
                        "weight" to it.weight
                    )
                }
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Listen to plans pushed directly to a trainee inbox.
     * Path: trainees/{traineeId}/inboxPlans/{planId}
     */
    fun listenTraineeInboxPlans(
        traineeId: String,
        onChange: (Result<List<RemotePlanItem>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("trainees")
            .document(traineeId)
            .collection("inboxPlans")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val items = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null

                    val coachId = doc.getString("coachId")
                    val coachDisplayName = doc.getString("coachDisplayName")

                    val createdAtMs = (doc.get("createdAtEpochMs") as? Number)?.toLong()
                    val publishedAtMs = (doc.get("publishedAtEpochMs") as? Number)?.toLong()

                    @Suppress("UNCHECKED_CAST")
                    val exercisesRaw = doc.get("exercises") as? List<Map<String, Any?>> ?: emptyList()
                    val exercises = exercisesRaw.mapNotNull { m ->
                        val n = m["name"] as? String ?: return@mapNotNull null
                        val sets = (m["sets"] as? Number)?.toInt()
                        val reps = (m["reps"] as? Number)?.toInt()
                        val weight = (m["weight"] as? Number)?.toFloat()
                        ExerciseEntry(name = n, sets = sets, reps = reps, weight = weight)
                    }

                    val plan = TrainingPlan(
                        id = id,
                        name = name,
                        exercises = exercises,
                        createdAt = createdAtMs?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
                        publishedAt = publishedAtMs?.let { Instant.ofEpochMilli(it) },
                    )

                    RemotePlanItem(
                        plan = plan,
                        sourceCoachId = coachId,
                        sourceCoachDisplayName = coachDisplayName,
                        isInbox = true,
                    )
                }

                onChange(Result.success(items))
            }
    }

    /**
     * Trainee marks an inbox plan as read by deleting it from Firestore.
     * Path: trainees/{traineeId}/inboxPlans/{planId}
     */
    suspend fun deleteTraineeInboxPlan(
        traineeId: String,
        planId: String,
    ): Result<Unit> {
        return try {
            db.collection("trainees")
                .document(traineeId)
                .collection("inboxPlans")
                .document(planId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ==================== Custom Exercises ====================

    /**
     * Coach creates a custom exercise.
     * Path: coaches/{coachId}/customExercises/{exerciseId}
     */
    suspend fun addCustomExercise(coachId: String, exercise: CustomExercise): Result<Unit> {
        return try {
            val doc = db.collection("coaches")
                .document(coachId)
                .collection("customExercises")
                .document(exercise.id)

            val payload = mapOf(
                "id" to exercise.id,
                "name" to exercise.name,
                "description" to exercise.description,
                "bodyPart" to exercise.bodyPart,
                "equipment" to exercise.equipment,
                "createdAtEpochMs" to exercise.createdAtEpochMs,
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Coach deletes a custom exercise.
     * Path: coaches/{coachId}/customExercises/{exerciseId}
     */
    suspend fun deleteCustomExercise(coachId: String, exerciseId: String): Result<Unit> {
        return try {
            db.collection("coaches")
                .document(coachId)
                .collection("customExercises")
                .document(exerciseId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Listen to custom exercises in real-time.
     * Path: coaches/{coachId}/customExercises
     */
    fun listenCustomExercises(
        coachId: String,
        onChange: (Result<List<CustomExercise>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("coaches")
            .document(coachId)
            .collection("customExercises")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val description = doc.getString("description") ?: ""
                    val bodyPart = doc.getString("bodyPart") ?: ""
                    val equipment = doc.getString("equipment") ?: ""
                    val createdAt = (doc.get("createdAtEpochMs") as? Number)?.toLong() ?: 0L
                    CustomExercise(
                        id = id,
                        name = name,
                        description = description,
                        bodyPart = bodyPart,
                        equipment = equipment,
                        createdAtEpochMs = createdAt,
                    )
                }.sortedByDescending { it.createdAtEpochMs }

                onChange(Result.success(list))
            }
    }

    // ==================== Diet Suggestions ====================

    /**
     * Coach publishes a diet suggestion to a specific trainee's inbox.
     * Path: trainees/{traineeId}/inboxDietSuggestions/{suggestionId}
     */
    suspend fun publishDietSuggestion(
        coachId: String,
        traineeId: String,
        suggestion: DietSuggestion,
    ): Result<Unit> {
        return try {
            val doc = db.collection("trainees")
                .document(traineeId)
                .collection("inboxDietSuggestions")
                .document(suggestion.id)

            val payload = mapOf(
                "id" to suggestion.id,
                "coachId" to coachId,
                "traineeId" to traineeId,
                "title" to suggestion.title,
                "content" to suggestion.content,
                "createdAtEpochMs" to suggestion.createdAtEpochMs,
            )

            doc.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Listen to diet suggestions sent to a trainee (trainee-side).
     * Path: trainees/{traineeId}/inboxDietSuggestions
     */
    fun listenDietSuggestions(
        traineeId: String,
        onChange: (Result<List<DietSuggestion>>) -> Unit,
    ): ListenerRegistration {
        return db.collection("trainees")
            .document(traineeId)
            .collection("inboxDietSuggestions")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onChange(Result.failure(err))
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val cId = doc.getString("coachId") ?: return@mapNotNull null
                    val tId = doc.getString("traineeId") ?: ""
                    val title = doc.getString("title") ?: ""
                    val content = doc.getString("content") ?: ""
                    val createdAt = (doc.get("createdAtEpochMs") as? Number)?.toLong() ?: 0L
                    DietSuggestion(
                        id = id,
                        coachId = cId,
                        traineeId = tId,
                        title = title,
                        content = content,
                        createdAtEpochMs = createdAt,
                    )
                }.sortedByDescending { it.createdAtEpochMs }

                onChange(Result.success(list))
            }
    }

    /**
     * Trainee deletes a diet suggestion from their inbox.
     * Path: trainees/{traineeId}/inboxDietSuggestions/{suggestionId}
     */
    suspend fun deleteDietSuggestion(
        traineeId: String,
        suggestionId: String,
    ): Result<Unit> {
        return try {
            db.collection("trainees")
                .document(traineeId)
                .collection("inboxDietSuggestions")
                .document(suggestionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
