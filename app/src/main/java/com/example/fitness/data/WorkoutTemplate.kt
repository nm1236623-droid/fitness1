package com.example.fitness.data

import java.util.UUID

/**
 * 訓練課表模板
 * 可以保存使用者常用的訓練計畫配置，方便重複使用
 */
data class WorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val goal: String, // "增肌" or "減脂"
    val daysPerWeek: Int,
    val experienceLevel: String, // "新手", "中階", "高階"
    val days: List<TemplateDay>,
    val userId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isPublic: Boolean = false // 是否為公開模板（供其他用戶使用）
)

data class TemplateDay(
    val day: Int,
    val focus: String,
    val exercises: List<TemplateExercise>
)

data class TemplateExercise(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Float? = null,
    val restSeconds: Int? = null
)

/**
 * 將 TrainingPlan 轉換為 WorkoutTemplate
 */
fun TrainingPlan.toWorkoutTemplate(
    name: String,
    description: String = "",
    goal: String = "增肌",
    daysPerWeek: Int = 4,
    experienceLevel: String = "中階"
): WorkoutTemplate {
    val days = com.example.fitness.ai.GeminiTrainingPlanParser.extractDaysOrNull(this)

    return WorkoutTemplate(
        name = name,
        description = description,
        goal = goal,
        daysPerWeek = daysPerWeek,
        experienceLevel = experienceLevel,
        days = days?.map { day ->
            TemplateDay(
                day = day.day,
                focus = day.focus ?: "",
                exercises = day.exercises.map { ex ->
                    TemplateExercise(
                        name = ex.name,
                        sets = ex.sets ?: 3,  // 預設 3 組
                        reps = ex.reps ?: 10, // 預設 10 次
                        weightKg = ex.weight
                    )
                }
            )
        } ?: emptyList()
    )
}

/**
 * 將 WorkoutTemplate 轉換為 TrainingPlan
 */
fun WorkoutTemplate.toTrainingPlan(): TrainingPlan {
    val exercises = days.flatMap { day ->
        day.exercises.map { ex ->
            ExerciseEntry(
                name = "${ex.name} (Day ${day.day})",
                sets = ex.sets,
                reps = ex.reps,
                weight = ex.weightKg
            )
        }
    }

    return TrainingPlan(
        name = this.name,
        exercises = exercises
    )
}


