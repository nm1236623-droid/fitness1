package com.example.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.data.PartAnalysisRepository
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.inbody.InBodyRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.neonGlowBorder

// ★★★ 動作分類器 ★★★
object ExerciseClassifier {
    fun getPartForExercise(exerciseName: String): String {
        val name = exerciseName.lowercase()
        return when {
            // 胸部
            name.contains("臥推") || name.contains("飛鳥") || name.contains("胸") ||
                    name.contains("push up") || name.contains("bench") || name.contains("chest") ||
                    name.contains("pec") || name.contains("dip") -> "chest"

            // 背部
            name.contains("划船") || name.contains("引體") || name.contains("背") ||
                    name.contains("拉") || name.contains("row") || name.contains("pull") ||
                    name.contains("lat") || name.contains("back") -> "back"

            // 腿部
            name.contains("蹲") || name.contains("腿") || name.contains("硬舉") ||
                    name.contains("leg") || name.contains("squat") || name.contains("deadlift") ||
                    name.contains("lunge") || name.contains("calf") -> "legs"

            // 肩部
            name.contains("肩") || name.contains("推舉") || name.contains("平舉") ||
                    name.contains("shoulder") || name.contains("press") || name.contains("raise") ||
                    name.contains("deltoid") || name.contains("military") -> "shoulders"

            // 手臂
            name.contains("彎舉") || name.contains("三頭") || name.contains("二頭") ||
                    name.contains("臂") || name.contains("curl") || name.contains("extension") ||
                    name.contains("tricep") || name.contains("bicep") || name.contains("skull") -> "arms"

            // 腹部
            name.contains("腹") || name.contains("捲腹") || name.contains("核心") ||
                    name.contains("abs") || name.contains("crunch") || name.contains("plank") ||
                    name.contains("sit up") -> "abs"

            // 其他
            else -> "other"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartAnalysisScreen(
    repository: TrainingPlanRepository,
    activityRepository: ActivityLogRepository,
    inBodyRepo: InBodyRepository,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val partRepo = remember { PartAnalysisRepository(ctx) }
    val entries by partRepo.data.collectAsState(initial = emptyList())

    val partOptions: List<Pair<String, String>> = listOf(
        stringResource(R.string.chest) to "chest",
        stringResource(R.string.back_part) to "back",
        stringResource(R.string.legs) to "legs",
        stringResource(R.string.shoulders) to "shoulders",
        stringResource(R.string.arms) to "arms",
        stringResource(R.string.abs) to "abs",
        stringResource(R.string.other_part) to "other"
    )

    var selectedPartKey by rememberSaveable { mutableStateOf("chest") }
    var selectedExercise by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseDropdownExpanded by remember { mutableStateOf(false) }

    val byDate: Map<LocalDate, Map<String, Float>> = remember(entries) {
        entries.associate { LocalDate.parse(it.date) to it.exerciseWeights }
    }

    val filteredExerciseNames: List<String> = remember(byDate, selectedPartKey) {
        byDate
            .values
            .flatMap { it.keys }
            .toSet()
            .filter { exerciseName ->
                ExerciseClassifier.getPartForExercise(exerciseName) == selectedPartKey
            }
            .sorted()
    }

    LaunchedEffect(filteredExerciseNames) {
        if (filteredExerciseNames.isNotEmpty()) {
            if (selectedExercise == null || !filteredExerciseNames.contains(selectedExercise)) {
                selectedExercise = filteredExerciseNames.first()
            }
        } else {
            selectedExercise = null
        }
    }

    val chartPoints: List<Pair<LocalDate, Float>> = remember(selectedExercise, byDate) {
        val ex = selectedExercise ?: return@remember emptyList()
        byDate
            .mapNotNull { (date, map) -> map[ex]?.let { date to it } }
            .sortedBy { it.first }
    }

    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TechColors.DarkBlue, TechColors.DeepPurple)
                )
            )
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 20.dp)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = cs.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.part_analysis),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                        Text(
                            stringResource(R.string.track_weight_trend),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Part Selector - Renamed component usage
                AnalysisSectionCard(title = stringResource(R.string.select_training_part)) {
                    val rows = partOptions.chunked(4)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { (label, key) ->
                                    val isSelected = selectedPartKey == key
                                    val modifier = if (isSelected) {
                                        Modifier
                                            .weight(1f)
                                            .neonGlowBorder(cornerRadius = 12.dp)
                                            .background(
                                                cs.primary.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    } else {
                                        Modifier
                                            .weight(1f)
                                            .glassEffect(cornerRadius = 12.dp)
                                    }

                                    Box(
                                        modifier = modifier
                                            .height(45.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedPartKey = key },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) cs.primary else cs.onBackground.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // 2. Exercise Selector - Renamed component usage
                AnalysisSectionCard(title = stringResource(R.string.select_exercise)) {
                    if (filteredExerciseNames.isEmpty()) {
                        EmptyStateCard(stringResource(R.string.no_exercise_in_part))
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = exerciseDropdownExpanded,
                            onExpandedChange = { exerciseDropdownExpanded = !exerciseDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NeonOutlinedTextField(
                                value = selectedExercise ?: stringResource(R.string.please_select_exercise),
                                onValueChange = {},
                                readOnly = true,
                                label = stringResource(R.string.exercise_name),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = exerciseDropdownExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = exerciseDropdownExpanded,
                                onDismissRequest = { exerciseDropdownExpanded = false },
                                containerColor = cs.surface,
                                modifier = Modifier.background(cs.surface)
                            ) {
                                filteredExerciseNames.forEach { ex ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = ex,
                                                color = if (selectedExercise == ex) cs.primary else cs.onSurface,
                                                fontWeight = if (selectedExercise == ex) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedExercise = ex
                                            exerciseDropdownExpanded = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider(color = cs.outline.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }

                // 3. Statistics Grid
                if (chartPoints.isNotEmpty()) {
                    val values = chartPoints.map { it.second }
                    val latest = values.lastOrNull() ?: 0f
                    val earliest = values.firstOrNull() ?: 0f
                    val change = latest - earliest
                    val max = values.maxOrNull() ?: 0f
                    val average = values.average().toFloat()

                    // Renamed component usage
                    AnalysisSectionCard(title = stringResource(R.string.weight_statistics)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NeonStatCard(
                                    label = stringResource(R.string.latest_weight),
                                    value = "%.1f".format(latest),
                                    unit = "kg",
                                    modifier = Modifier.weight(1f)
                                )
                                NeonStatCard(
                                    label = stringResource(R.string.total_progress),
                                    value = "%+.1f".format(change),
                                    unit = "kg",
                                    valueColor = if (change >= 0) cs.primary else Color(0xFFFF6B6B),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NeonStatCard(
                                    label = stringResource(R.string.historical_max),
                                    value = "%.1f".format(max),
                                    unit = "kg",
                                    modifier = Modifier.weight(1f)
                                )
                                NeonStatCard(
                                    label = stringResource(R.string.average_weight),
                                    value = "%.1f".format(average),
                                    unit = "kg",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 4. Chart Section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(cornerRadius = 24.dp)
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.weight_trend),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onBackground
                                    )
                                    Text(
                                        text = selectedExercise ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            LineChart(
                                dataPoints = chartPoints,
                                dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault()),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            )
                        }
                    }

                } else if (selectedExercise != null) {
                    EmptyStateCard(stringResource(R.string.no_data_for_chart))
                } else if (filteredExerciseNames.isNotEmpty()) {
                    EmptyStateCard(stringResource(R.string.select_exercise_to_view))
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// --- Helper Components ---

// ★★★ Renamed to AnalysisSectionCard to prevent ambiguity with other files ★★★
@Composable
private fun AnalysisSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 24.dp)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun NeonStatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .glassEffect(cornerRadius = 16.dp)
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun NeonOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = cs.onSurface,
            unfocusedTextColor = cs.onSurface,
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.outline.copy(alpha = 0.5f),
            focusedLabelColor = cs.primary,
            unfocusedLabelColor = cs.onSurfaceVariant,
            cursorColor = cs.primary,
            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.2f),
            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.1f)
        )
    )
}

@Composable
private fun EmptyStateCard(message: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 20.dp)
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = cs.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onBackground.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}