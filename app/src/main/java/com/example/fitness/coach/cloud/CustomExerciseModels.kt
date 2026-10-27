package com.example.fitness.coach.cloud

/**
 * A custom exercise created by a coach.
 * Stored at: coaches/{coachId}/customExercises/{id}
 */
data class CustomExercise(
    val id: String,
    val name: String,
    val description: String = "",
    val bodyPart: String = "",       // e.g. "chest", "back", "legs", "arms", "shoulders", "abs"
    val equipment: String = "",      // e.g. "啞鈴", "槓鈴", "Cable", "" for bodyweight
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

