package com.example.fitness.template

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * 課表等級
 */
enum class TemplateLevel {
    BEGINNER,    // 新手
    INTERMEDIATE, // 中階
    ADVANCED     // 高階
}

/**
 * 簡易訓練課表模板 - 本地儲存
 * 使用者可以儲存部位+動作組合，下次直接載入使用
 */
data class SimpleWorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // 使用者自訂名稱，如「推日」、「拉日」
    val bodyPart: String, // chest, back, legs, arms, shoulders, abs
    val exercises: List<SimpleExerciseItem>,
    val level: TemplateLevel = TemplateLevel.BEGINNER, // 課表等級
    val description: String = "", // 課表描述
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

data class SimpleExerciseItem(
    val name: String,
    val sets: Int = 8,
    val weight: Float = 40f
)

private val Context.simpleTemplateDataStore by preferencesDataStore(name = "simple_workout_templates")

class LocalTemplateRepository(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("templates_v2")

    val templates: Flow<List<SimpleWorkoutTemplate>> = context.simpleTemplateDataStore.data.map { prefs ->
        val json = prefs[key] ?: "[]"
        try {
            val type = object : TypeToken<List<SimpleWorkoutTemplate>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addTemplate(template: SimpleWorkoutTemplate) {
        context.simpleTemplateDataStore.edit { prefs ->
            val current = getCurrentList(prefs[key])
            val updated = current.toMutableList().apply { add(template) }
            prefs[key] = gson.toJson(updated)
        }
    }

    suspend fun deleteTemplate(id: String) {
        context.simpleTemplateDataStore.edit { prefs ->
            val current = getCurrentList(prefs[key])
            val updated = current.filter { it.id != id }
            prefs[key] = gson.toJson(updated)
        }
    }

    suspend fun updateLastUsed(id: String) {
        context.simpleTemplateDataStore.edit { prefs ->
            val current = getCurrentList(prefs[key])
            val updated = current.map {
                if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis())
                else it
            }
            prefs[key] = gson.toJson(updated)
        }
    }

    fun getTemplatesForPart(bodyPart: String): Flow<List<SimpleWorkoutTemplate>> {
        return templates.map { list ->
            list.filter { it.bodyPart == bodyPart }
                .sortedByDescending { it.lastUsedAt ?: it.createdAt }
        }
    }

    private fun getCurrentList(json: String?): List<SimpleWorkoutTemplate> {
        return try {
            if (json.isNullOrBlank()) emptyList()
            else {
                val type = object : TypeToken<List<SimpleWorkoutTemplate>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

