package com.example.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.R
import com.example.fitness.data.FirebaseTrainingRecordRepository
import com.example.fitness.data.TrainingRecord
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrainingHistoryScreen(
    trainingRecordRepository: FirebaseTrainingRecordRepository,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    // 左上箭頭與系統返回鍵：一律回主頁（由呼叫端決定要切到哪個 tab）
    BackHandler(enabled = true) {
        onBack()
    }

    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    // 使用 key 來確保日期變更時重新訂閱 Flow
    val recordsFlow = remember(currentDate) {
        trainingRecordRepository.observeRecordsForDate(currentDate)
    }
    val dailyRecords by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = modifier.fillMaxSize().widthIn(max = 840.dp).padding(12.dp)) {
        ScreenHeader(title = stringResource(R.string.training_log), subtitle = stringResource(R.string.view_by_date), onBack = onBack)
        Spacer(Modifier.height(12.dp))

        // 日期選擇列（風格沿用 FoodLog 的前後日切換）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(16.dp)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentDate = currentDate.minusDays(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                text = currentDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { currentDate = LocalDate.now() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(onClick = { currentDate = currentDate.plusDays(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 當日摘要
        val totalCalories = dailyRecords.sumOf { it.caloriesBurned }.toFloat()
        val totalDurationMin = (dailyRecords.sumOf { it.durationInSeconds } / 60f)

        Box(modifier = Modifier.fillMaxWidth().glassEffect(16.dp).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                stringResource(R.string.daily_summary_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat(
                        title = stringResource(R.string.calories),
                        value = totalCalories.toInt().toString(),
                        unit = "kcal",
                        icon = Icons.Default.LocalFireDepartment,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStat(
                        title = stringResource(R.string.time),
                        value = String.format(Locale.US, "%.0f", totalDurationMin),
                        unit = stringResource(R.string.minutes),
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val noRecordTitle = stringResource(R.string.no_training_record)
        val noRecordSubtitle = stringResource(R.string.training_record_hint)
        val unnamedTraining = stringResource(R.string.unnamed_training)

        if (dailyRecords.isEmpty()) {
            EmptyState(title = noRecordTitle, subtitle = noRecordSubtitle)
        } else {
            // 依課表名稱分組，讓同天多筆更清楚
            val grouped = remember(dailyRecords, unnamedTraining) {
                dailyRecords.groupBy { it.planName.ifBlank { unnamedTraining } }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                grouped.forEach { (planName, records) ->
                    item {
                        Text(
                            planName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(records, key = { it.id }) { rec ->
                        TrainingRecordCard(rec)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingRecordCard(record: TrainingRecord) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    val unnamedTrainingText = stringResource(R.string.unnamed_training)
    val minutesText = stringResource(R.string.minutes)
    val actionsText = stringResource(R.string.actions)
    val notesText = stringResource(R.string.notes)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                record.planName.ifBlank { unnamedTrainingText },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill(label = "${record.durationInSeconds / 60} $minutesText")
                StatPill(label = "${record.caloriesBurned.toInt()} kcal")
                StatPill(label = "${record.exercises.size} $actionsText")
            }

            if (!record.notes.isNullOrBlank()) {
                Text("$notesText：${record.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }

            if (record.exercises.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)

                val list = if (expanded) record.exercises else record.exercises.take(6)
                list.forEach { ex ->
                    val exerciseText = if (ex.weight != null) {
                        stringResource(R.string.exercise_format_with_weight, ex.name, ex.sets, ex.reps, ex.weight)
                    } else {
                        stringResource(R.string.exercise_format, ex.name, ex.sets, ex.reps)
                    }
                    Text(
                        text = exerciseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!expanded && record.exercises.size > 6) {
                    Text(
                        stringResource(R.string.more_actions, record.exercises.size - 6),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (expanded && record.exercises.size > 6) {
                    Text(
                        stringResource(R.string.click_to_collapse),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
