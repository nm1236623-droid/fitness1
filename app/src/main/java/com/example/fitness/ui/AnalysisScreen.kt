package com.example.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.data.TrainingRecordRepository
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalysisScreen(
    trainingRecordRepository: TrainingRecordRepository
) {
    val trainingRecords by trainingRecordRepository.records.collectAsState(initial = emptyList())

    // Debug: Log records count
    LaunchedEffect(trainingRecords) {
        println("AnalysisScreen: Found ${trainingRecords.size} training records")
        trainingRecords.forEach { record ->
            println("Record: ${record.planName}, Date: ${record.date}, Exercises: ${record.exercises.size}")
            record.exercises.forEach { ex ->
                println("  - ${ex.name}: ${ex.sets}x${ex.reps} @ ${ex.weight}kg")
            }
        }
    }

    // Group exercises by name and track weight progression
    val exerciseProgressions = remember(trainingRecords) {
        val allExercises = trainingRecords
            .flatMap { record ->
                record.exercises.map { exercise ->
                    println("AnalysisScreen: Processing exercise: ${exercise.name}, weight: ${exercise.weight}, sets: ${exercise.sets}, reps: ${exercise.reps}")
                    Triple(exercise.name, record.date, exercise.weight ?: 0.0)
                }
            }

        println("AnalysisScreen: Total exercises found: ${allExercises.size}")

        val withWeight = allExercises.filter { it.third > 0.0 }
        println("AnalysisScreen: Exercises with weight > 0: ${withWeight.size}")
        withWeight.forEach {
            println("  - ${it.first}: ${it.third}kg on ${it.second}")
        }

        val grouped = withWeight
            .groupBy { it.first } // Group by exercise name
            .mapValues { (exerciseName, records) ->
                println("AnalysisScreen: Exercise '$exerciseName' has ${records.size} records")
                records
                    .sortedBy { it.second } // Sort by date
                    .map { Pair(it.second, it.third) } // Keep date and weight
            }
            .filter { it.value.isNotEmpty() }

        println("AnalysisScreen: Final exercise groups: ${grouped.keys}")
        grouped
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "重量變化追蹤",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (exerciseProgressions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚無訓練記錄\n完成訓練後將顯示重量變化",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(exerciseProgressions.toList()) { (exerciseName, progressions) ->
                    ExerciseWeightCard(
                        exerciseName = exerciseName,
                        progressions = progressions
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseWeightCard(
    exerciseName: String,
    progressions: List<Pair<java.time.LocalDate, Double>>
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MM/dd") }
    val maxWeight = progressions.maxOfOrNull { it.second } ?: 0.0
    val latestWeight = progressions.lastOrNull()?.second ?: 0.0
    val firstWeight = progressions.firstOrNull()?.second ?: 0.0
    val weightChange = latestWeight - firstWeight

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Exercise name
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Statistics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("最新", "${latestWeight}kg")
                StatItem("最高", "${maxWeight}kg")
                StatItem(
                    "變化",
                    "${if (weightChange >= 0) "+" else ""}${String.format(Locale.getDefault(), "%.1f", weightChange)}kg",
                    color = if (weightChange >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Weight progression list
            Text(
                text = "訓練記錄",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                progressions.takeLast(5).reversed().forEach { (date, weight) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${weight}kg",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (progressions.size > 5) {
                Text(
                    text = "... 共 ${progressions.size} 筆記錄",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

