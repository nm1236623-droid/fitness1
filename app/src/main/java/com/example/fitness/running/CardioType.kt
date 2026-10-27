package com.example.fitness.running

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.fitness.R

/**
 * Cardio activity types shown in the Cardio screen.
 */
enum class CardioType(
    @StringRes val displayNameRes: Int,
    val icon: ImageVector,
    val displayName: String, // Fallback name for non-Compose usage (e.g., GeminiClient)
) {
    SPIN_BIKE(R.string.cardio_spin_bike, Icons.Filled.PedalBike, "Spin Bike"),
    ELLIPTICAL(R.string.cardio_elliptical, Icons.Filled.FitnessCenter, "Elliptical"),
    ROWING_MACHINE(R.string.cardio_rowing, Icons.Filled.Rowing, "Rowing Machine"),
    STAIR_CLIMBER(R.string.cardio_stair, Icons.Filled.Stairs, "Stair Climber"),
    CYCLING(R.string.cardio_cycling, Icons.AutoMirrored.Filled.DirectionsBike, "Cycling"),
    WALK_OR_JOG(R.string.cardio_walk_jog, Icons.AutoMirrored.Filled.DirectionsRun, "Walk/Jog"),
}
