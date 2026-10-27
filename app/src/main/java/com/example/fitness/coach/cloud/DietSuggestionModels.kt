package com.example.fitness.coach.cloud

/**
 * A diet suggestion created by a coach and sent to a trainee.
 * Stored at: trainees/{traineeId}/inboxDietSuggestions/{id}
 */
data class DietSuggestion(
    val id: String,
    val coachId: String,
    val traineeId: String = "",
    val title: String,
    val content: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

