package com.example.fitness.coach.cloud

import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.data.TrainingPlanRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cloud sync wiring:
 *  - Prefer FirebaseAuth uid as identity (cross-device)
 *  - Fallback to local CoachAuthRepository id if user isn't signed in (MVP/backward compatible)
 */
class CloudSyncUseCase(
    private val coachAuthRepository: CoachAuthRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val cloudRepository: CoachCloudRepository = CoachCloudRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val remotePlanRepository: CoachRemotePlanRepository = CoachRemotePlanRepository(),
) {
    private var membershipsRegistration: ListenerRegistration? = null
    private val coachPlanRegistrations: MutableMap<String, ListenerRegistration> = mutableMapOf()
    private var completionsRegistration: ListenerRegistration? = null
    private var inboxRegistration: ListenerRegistration? = null
    private val _coachCompletions = kotlinx.coroutines.flow.MutableStateFlow<List<CoachPlanCompletion>>(emptyList())

    private var customExercisesRegistration: ListenerRegistration? = null
    private val _customExercises = kotlinx.coroutines.flow.MutableStateFlow<List<CustomExercise>>(emptyList())

    private suspend fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: coachAuthRepository.ensureUserId()
    }

    /** Exposed for UI: shows what uid the cloud layer is using as TraineeId. */
    suspend fun getCurrentTraineeId(): String = getCurrentUserId()

    /** Observe coach-published plans (separate from user's own plans). */
    fun remotePlansFlow() = remotePlanRepository.plans

    /** Observe received plan items with source metadata. */
    fun remoteItemsFlow() = remotePlanRepository.items

    /**
     * Coach-side: observe trainee completion reports for this coach.
     * Note: you must call [startListeningCoachCompletions] at least once to receive updates.
     */
    fun coachCompletionsFlow() = _coachCompletions.asStateFlow()

    /** Convenience: start listening completions and return the flow for UI. */
    suspend fun startCoachCompletionsAndFlow() : kotlinx.coroutines.flow.StateFlow<List<CoachPlanCompletion>> {
        startListeningCoachCompletions()
        return _coachCompletions.asStateFlow()
    }

    fun stopListening() {
        membershipsRegistration?.remove()
        membershipsRegistration = null

        coachPlanRegistrations.values.forEach { it.remove() }
        coachPlanRegistrations.clear()

        inboxRegistration?.remove()
        inboxRegistration = null

        stopListeningCoachCompletions()
        stopListeningCustomExercises()

        // Clear remote cache so we don't show stale coach plans after stopping.
        remotePlanRepository.clear()
    }

    private fun handleIncomingCoachPlans(coachId: String, plans: List<com.example.fitness.data.TrainingPlan>) {
        remotePlanRepository.upsertCoachPlans(coachId, plans)
    }

    private fun handleIncomingInboxPlans(items: List<RemotePlanItem>) {
        remotePlanRepository.upsertInboxPlans(items)
    }

    private fun handleIncomingPlans(plans: List<com.example.fitness.data.TrainingPlan>) {
        // Backward-compatible path: without source metadata.
        remotePlanRepository.upsertCoachPlans(coachId = "", incomingPlans = plans)
    }

    fun startListeningCoachPlans(coachId: String) {
        // idempotent: if already listening, don't re-register
        if (coachPlanRegistrations.containsKey(coachId)) return

        val reg = cloudRepository.listenCoachPlans(coachId) { result ->
            result.onSuccess { plans ->
                handleIncomingCoachPlans(coachId, plans)
            }
        }
        coachPlanRegistrations[coachId] = reg
    }

    private fun stopListeningCoachPlans(coachId: String) {
        coachPlanRegistrations.remove(coachId)?.remove()
        // Note: we don't selectively remove from remote repository here; simplest is to keep
        // received plans cached for this session. stopListening() clears all.
    }

    /**
     * Auto reconnect sync:
     * - listen memberships
     * - for every joined coach, listen their plans
     */
    suspend fun startAutoReconnectSync(): Result<Unit> {
        return try {
            val traineeId = getCurrentUserId()

            // Listen inbox (single-target publishing)
            inboxRegistration?.remove()
            inboxRegistration = cloudRepository.listenTraineeInboxPlans(traineeId) { result ->
                result.onSuccess { items ->
                    handleIncomingInboxPlans(items)
                }
            }

            // Start with a one-time fetch so plans begin syncing immediately.
            cloudRepository.getJoinedCoachIds(traineeId)
                .getOrDefault(emptyList())
                .forEach { startListeningCoachPlans(it) }

            membershipsRegistration?.remove()
            membershipsRegistration = cloudRepository.listenJoinedCoachIds(traineeId) { result ->
                result.onSuccess { coachIds ->
                    val newSet = coachIds.toSet()
                    val oldSet = coachPlanRegistrations.keys.toSet()

                    // start new
                    (newSet - oldSet).forEach { startListeningCoachPlans(it) }
                    // stop removed
                    (oldSet - newSet).forEach { stopListeningCoachPlans(it) }
                }
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Used by UI to show currently joined coaches (one-time load). */
    suspend fun getJoinedCoachIds(): Result<List<String>> {
        val traineeId = getCurrentUserId()
        return cloudRepository.getJoinedCoachIds(traineeId)
    }

    suspend fun publishPlan(planId: String): Result<Unit> {
        val coachId = getCurrentUserId()
        val plan = trainingPlanRepository.getPlan(planId)
            ?: return Result.failure(IllegalArgumentException("Plan not found"))
        return cloudRepository.publishPlan(coachId, plan)
    }

    /**
     * Coach-side: publish a coach-local plan to Firestore.
     * This does NOT touch TrainingPlanRepository.
     */
    suspend fun publishCoachLocalPlan(coachId: String, plan: com.example.fitness.data.TrainingPlan): Result<Unit> {
        return cloudRepository.publishPlan(coachId, plan)
    }

    /**
     * Coach-side: publish a coach-local plan to a specific trainee inbox.
     */
    suspend fun publishCoachLocalPlanToTrainee(
        coachId: String,
        traineeId: String,
        plan: com.example.fitness.data.TrainingPlan,
    ): Result<Unit> {
        return cloudRepository.publishPlanToTraineeInbox(coachId = coachId, traineeId = traineeId, plan = plan)
    }

    suspend fun joinCoachAndStartListening(coachId: String): Result<Unit> {
        val traineeId = getCurrentUserId()
        val join = cloudRepository.joinCoach(traineeId = traineeId, coachId = coachId)
        if (join.isFailure) return join

        // Start listening right away; memberships listener (if enabled) will keep it consistent.
        startListeningCoachPlans(coachId)
        return Result.success(Unit)
    }

    /**
     * Remove a received coach plan from the local remote cache.
     * Note: this does NOT delete anything in Firestore; it only hides it on this device/session.
     */
    fun removeRemotePlan(planId: String) {
        remotePlanRepository.removeById(planId)
    }

    /**
     * Trainee reports completing a coach plan to the coach's Firestore area.
     * This lets the coach know the trainee executed the plan.
     */
    suspend fun reportCoachPlanCompletion(coachId: String, plan: com.example.fitness.data.TrainingPlan): Result<Unit> {
        val traineeId = getCurrentUserId()
        return cloudRepository.reportPlanCompletion(
            coachId = coachId,
            traineeId = traineeId,
            planId = plan.id,
            planName = plan.name
        )
    }

    /** Coach-side: start listening trainee completion reports for the current coach id. */
    suspend fun startListeningCoachCompletions(): Result<Unit> {
        return try {
            val coachId = getCurrentUserId()
            completionsRegistration?.remove()
            completionsRegistration = cloudRepository.listenAllCompletionsForCoach(coachId) { result ->
                result.onSuccess { _coachCompletions.value = it }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Coach-side: stop listening completion reports. */
    fun stopListeningCoachCompletions() {
        completionsRegistration?.remove()
        completionsRegistration = null
        _coachCompletions.value = emptyList()
    }

    /**
     * Join & sync by coach display name.
     *
     * This resolves the display name to a coach uid via Cloud directory.
     * If no match found, returns failure.
     */
    suspend fun joinCoachByNameAndStartListening(coachName: String): Result<Unit> {
        val name = coachName.trim()
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Coach name is blank"))

        val coachId = cloudRepository.findCoachIdByDisplayName(name)
            .getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalArgumentException("Coach not found"))

        return joinCoachAndStartListening(coachId)
    }

    /** Coach-side: publish the coach display name -> uid mapping for trainees to find. */
    suspend fun publishCoachDirectoryEntry(displayName: String): Result<Unit> {
        val coachId = getCurrentUserId()
        val name = displayName.trim()
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Display name is blank"))
        return cloudRepository.upsertCoachDirectoryEntry(coachId = coachId, displayName = name)
    }

    /**
     * Trainee: mark an inbox plan as read by deleting it from Firestore.
     * This removes it permanently (won't come back unless coach publishes again).
     */
    suspend fun markInboxPlanRead(planId: String): Result<Unit> {
        return try {
            val item = remotePlanRepository.getItem(planId)
            if (item?.isInbox != true) {
                // Not an inbox plan, just hide locally.
                remotePlanRepository.removeById(planId)
                return Result.success(Unit)
            }

            val traineeId = getCurrentUserId()
            val r = cloudRepository.deleteTraineeInboxPlan(traineeId = traineeId, planId = planId)
            if (r.isSuccess) {
                remotePlanRepository.removeById(planId)
            }
            r
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Resolve coach display name by coach uid (ID -> Name). */
    suspend fun getCoachDisplayNameById(coachId: String): Result<String?> {
        return cloudRepository.findCoachDisplayNameById(coachId)
    }

    /**
     * Trainee-side: record a completed coach-published plan into the same training history
     * pipeline used by the normal training plan completion.
     */
    suspend fun recordCoachPlanCompletionToHistory(
        activityRepository: com.example.fitness.activity.ActivityLogRepository,
        trainingRecordRepository: com.example.fitness.data.FirebaseTrainingRecordRepository,
        plan: com.example.fitness.data.TrainingPlan,
        estimatedCalories: Double? = null,
        durationSeconds: Int = 3600,
    ) {
        val calories = estimatedCalories
        val now = java.time.Instant.now()

        // 1) Local activity log (persisted)
        val activity = com.example.fitness.activity.ActivityRecord(
            id = java.util.UUID.randomUUID().toString(),
            planId = plan.id,
            type = plan.name,
            start = now,
            end = null,
            calories = calories,
            exercises = plan.exercises
        )
        activityRepository.add(activity)

        // 2) In-memory training record (used by analysis UI)
        val tr = com.example.fitness.data.TrainingRecord(
            planId = plan.id,
            planName = plan.name,
            date = java.time.LocalDate.now(),
            durationInSeconds = durationSeconds,
            caloriesBurned = calories ?: 0.0,
            exercises = plan.exercises.map { e ->
                com.example.fitness.data.ExerciseRecord(
                    name = e.name,
                    sets = e.sets ?: 0,
                    reps = e.reps ?: 0,
                    weight = e.weight?.toDouble()
                )
            }
        )
        trainingRecordRepository.addRecordToFirebase(tr).fold(
            onSuccess = { documentId ->
                android.util.Log.d("CloudSyncUseCase", "Successfully saved training record with ID: $documentId")
            },
            onFailure = { error ->
                android.util.Log.e("CloudSyncUseCase", "Failed to save training record: ${error.message}")
            }
        )
    }

    // ==================== Custom Exercises ====================

    /** Observe custom exercises created by this coach. */
    fun customExercisesFlow() = _customExercises.asStateFlow()

    /** Start listening to custom exercises. */
    suspend fun startListeningCustomExercises(): Result<Unit> {
        return try {
            val coachId = getCurrentUserId()
            customExercisesRegistration?.remove()
            customExercisesRegistration = cloudRepository.listenCustomExercises(coachId) { result ->
                result.onSuccess { _customExercises.value = it }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun stopListeningCustomExercises() {
        customExercisesRegistration?.remove()
        customExercisesRegistration = null
        _customExercises.value = emptyList()
    }

    /** Coach adds a custom exercise. */
    suspend fun addCustomExercise(exercise: CustomExercise): Result<Unit> {
        val coachId = getCurrentUserId()
        return cloudRepository.addCustomExercise(coachId, exercise)
    }

    /** Coach deletes a custom exercise. */
    suspend fun deleteCustomExercise(exerciseId: String): Result<Unit> {
        val coachId = getCurrentUserId()
        return cloudRepository.deleteCustomExercise(coachId, exerciseId)
    }

    // ==================== Diet Suggestions ====================

    /** Coach publishes a diet suggestion to a specific trainee. */
    suspend fun publishDietSuggestion(
        traineeId: String,
        suggestion: DietSuggestion,
    ): Result<Unit> {
        val coachId = getCurrentUserId()
        return cloudRepository.publishDietSuggestion(
            coachId = coachId,
            traineeId = traineeId,
            suggestion = suggestion.copy(coachId = coachId, traineeId = traineeId),
        )
    }

    /** Trainee deletes a diet suggestion from their inbox. */
    suspend fun deleteDietSuggestion(suggestionId: String): Result<Unit> {
        val traineeId = getCurrentUserId()
        return cloudRepository.deleteDietSuggestion(
            traineeId = traineeId,
            suggestionId = suggestionId,
        )
    }
}
