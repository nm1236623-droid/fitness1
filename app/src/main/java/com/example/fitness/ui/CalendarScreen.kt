package com.example.fitness.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.coach.cloud.CloudSyncUseCase
import com.example.fitness.coach.cloud.CoachTrainee
import com.example.fitness.coach.cloud.CoachTraineeDirectoryRepository
import com.example.fitness.coach.local.CoachLocalPlanRepository
import com.example.fitness.coach.ui.SingleTraineePicker
import com.example.fitness.data.DietRecord
import com.example.fitness.data.ExerciseEntry
import com.example.fitness.data.FirebaseDietRecordRepository
import com.example.fitness.data.FirebaseTrainingRecordRepository
import com.example.fitness.data.PartExerciseCatalog
import com.example.fitness.data.TrainingPlan
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.data.TrainingRecord
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirlineSeatReclineExtra
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrunchDining
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.fitness.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 輔助資�?類別
private data class CoachPartUi(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
)

@Composable
fun CalendarScreen(
    repository: TrainingPlanRepository,
    trainingRecordRepository: FirebaseTrainingRecordRepository? = null,
    modifier: Modifier = Modifier,
    onOpenPlan: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val cs = MaterialTheme.colorScheme

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val plansState by repository.plans.collectAsState(initial = emptyList())

    // observe scheduled plans for selected date
    val scheduledForDate by repository.getPlansForDateFlow(selectedDate).collectAsState(initial = emptyList())

    // ── 當日訓練紀錄 ──
    val trainingRecordsForDate by remember(trainingRecordRepository, selectedDate) {
        trainingRecordRepository?.observeRecordsForDate(selectedDate)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // ── 當日飲食紀錄 ──
    LaunchedEffect(Unit) { FirebaseDietRecordRepository.listenForUpdates() }
    val allDietRecords by FirebaseDietRecordRepository.records.collectAsState()
    val dietRecordsForDate = remember(allDietRecords, selectedDate) {
        allDietRecords.filter { it.date == selectedDate }
    }

    var addDialogOpen by remember { mutableStateOf(false) }
    var createNewPlanDialogOpen by remember { mutableStateOf(false) }

    // Coach functionality
    val scope = rememberCoroutineScope()
    val coachLocalRepo = remember(appCtx) { CoachLocalPlanRepository(appCtx) }
    val authRepository = remember(appCtx) { CoachAuthRepository(appCtx) }
    val cloudSync = remember(authRepository) {
        CloudSyncUseCase(authRepository, TrainingPlanRepository()) // Note: creates new repo instance here, might want to share
    }
    val traineeDirectoryRepo = remember { CoachTraineeDirectoryRepository() }
    val trainees = remember { mutableStateListOf<CoachTrainee>() }
    var coachId by remember { mutableStateOf("") }

    // Load coach ID and trainees
    LaunchedEffect(Unit) {
        coachId = FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId()
        if (coachId.isNotBlank()) {
            traineeDirectoryRepo.listTrainees(coachId).onSuccess { list ->
                trainees.clear()
                trainees.addAll(list)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = modifier.widthIn(max = 840.dp).padding(16.dp)) {
            // Header: month navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = cs.primary
                            )
                        }
                        IconButton(
                            onClick = { currentMonth = currentMonth.minusMonths(1); selectedDate = currentMonth.atDay(1) }
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.previous),
                                tint = cs.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.year_month_format, currentMonth.year, currentMonth.month.value),
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { currentMonth = currentMonth.plusMonths(1); selectedDate = currentMonth.atDay(1) }
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.next),
                            tint = cs.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day of week headers
            Row(modifier = Modifier.fillMaxWidth()) {
                val dayNames = listOf(
                    stringResource(R.string.day_sun),
                    stringResource(R.string.day_mon),
                    stringResource(R.string.day_tue),
                    stringResource(R.string.day_wed),
                    stringResource(R.string.day_thu),
                    stringResource(R.string.day_fri),
                    stringResource(R.string.day_sat)
                )
                dayNames.forEach { dn ->
                    Text(
                        text = dn,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = cs.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            val firstOfMonth = currentMonth.atDay(1)
            val daysInMonth = currentMonth.lengthOfMonth()
            val firstDow = firstOfMonth.dayOfWeek.value % 7 // Java time: Mon=1...Sun=7. We want Sun=0
            val leadingEmpty = firstDow
            val totalCells = leadingEmpty + daysInMonth
            val rows = (totalCells + 6) / 7

            Column(modifier = Modifier.fillMaxWidth()) {
                var day = 1
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val cellIndex = r * 7 + c
                            if (cellIndex < leadingEmpty || day > daysInMonth) {
                                Box(modifier = Modifier.weight(1f).height(48.dp))
                            } else {
                                val thisDate = currentMonth.atDay(day)
                                val isSelected = thisDate == selectedDate
                                val hasPlan = repository.getPlansForDate(thisDate).isNotEmpty()
                                val isToday = thisDate == LocalDate.now()

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) cs.primary.copy(alpha = 0.3f)
                                            else if (isToday) cs.onSurface.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) cs.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedDate = thisDate },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day.toString(),
                                            textAlign = TextAlign.Center,
                                            color = if (isSelected) cs.primary else if (isToday) cs.onSurface else cs.onSurface.copy(alpha = 0.8f),
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (hasPlan) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .background(cs.tertiary, CircleShape)
                                            )
                                        }
                                    }
                                }
                                day++
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ═══ 選中日期的所有資訊（可捲動） ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 日期 + 按鈕 ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 20.dp)
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { createNewPlanDialogOpen = true },
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.primary.copy(alpha = 0.2f),
                                    contentColor = cs.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.create_plan_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { addDialogOpen = true },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary)
                            ) {
                                Text(stringResource(R.string.add_existing), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ── 排程計畫 ──
                if (scheduledForDate.isNotEmpty()) {
                    CalendarSectionHeader(
                        icon = Icons.Default.FitnessCenter,
                        title = "排程計畫",
                        count = scheduledForDate.size,
                        color = cs.primary
                    )
                    scheduledForDate.forEach { plan ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassEffect(cornerRadius = 14.dp)
                                .clickable(enabled = onOpenPlan != null) { onOpenPlan?.invoke(plan.id) }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cs.primary.copy(0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FitnessCenter, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plan.name, color = cs.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(R.string.exercises_count, plan.exercises.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { repository.removeScheduledPlan(selectedDate, plan.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = cs.error.copy(0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // ── 訓練紀錄 ──
                if (trainingRecordsForDate.isNotEmpty()) {
                    CalendarSectionHeader(
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        title = "訓練紀錄",
                        count = trainingRecordsForDate.size,
                        color = Color(0xFF16A34A) // NeonGreen equivalent
                    )
                    trainingRecordsForDate.forEach { record ->
                        CalendarTrainingRecordCard(record)
                    }
                }

                // ── 飲食紀錄 ──
                if (dietRecordsForDate.isNotEmpty()) {
                    CalendarSectionHeader(
                        icon = Icons.Default.Restaurant,
                        title = "飲食紀錄",
                        count = dietRecordsForDate.size,
                        color = Color(0xFFEA580C) // NeonOrange equivalent
                    )
                    // 按餐別分組
                    val groupedByMeal = dietRecordsForDate.groupBy { it.mealType }
                    val mealOrder = listOf("早餐", "午餐", "晚餐", "點心")
                    mealOrder.forEach { mealType ->
                        val records = groupedByMeal[mealType]
                        if (!records.isNullOrEmpty()) {
                            CalendarMealGroupCard(mealType = mealType, records = records)
                        }
                    }
                    // 未分類
                    groupedByMeal.filterKeys { it !in mealOrder }.forEach { (mealType, records) ->
                        CalendarMealGroupCard(mealType = mealType, records = records)
                    }

                    // 當日飲食總計
                    val totalCal = dietRecordsForDate.sumOf { it.calories }
                    val totalP = dietRecordsForDate.sumOf { it.proteinG ?: 0.0 }
                    val totalC = dietRecordsForDate.sumOf { it.carbsG ?: 0.0 }
                    val totalF = dietRecordsForDate.sumOf { it.fatG ?: 0.0 }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFEA580C).copy(0.2f), RoundedCornerShape(14.dp))
                            .background(Color(0xFFEA580C).copy(0.06f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CalendarNutrientStat("熱量", "${totalCal} kcal", Color(0xFFEA580C))
                            CalendarNutrientStat("蛋白質", "${"%.1f".format(totalP)}g", cs.primary)
                            CalendarNutrientStat("碳水", "${"%.1f".format(totalC)}g", Color(0xFF16A34A))
                            CalendarNutrientStat("脂肪", "${"%.1f".format(totalF)}g", Color(0xFF7C3AED))
                        }
                    }
                }

                // ── 空狀態 ──
                if (scheduledForDate.isEmpty() && trainingRecordsForDate.isEmpty() && dietRecordsForDate.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EventBusy, null,
                                tint = Color.White.copy(0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.no_plan_for_day),
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        // --- Dialogs ---

        // Add existing plan dialog
        if (addDialogOpen) {
            AlertDialog(
                onDismissRequest = { addDialogOpen = false },
                containerColor = cs.surface.copy(alpha = 0.95f),
                titleContentColor = cs.primary,
                textContentColor = cs.onSurface,
                title = { Text(stringResource(R.string.select_plan_to_add), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (plansState.isEmpty()) {
                            Text(stringResource(R.string.no_plans_create_first), color = cs.onSurface.copy(alpha = 0.7f))
                        } else {
                            plansState.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(cs.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = p.name,
                                        modifier = Modifier.weight(1f),
                                        color = cs.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Button(
                                        onClick = {
                                            repository.addScheduledPlan(selectedDate, p.id)
                                            addDialogOpen = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(stringResource(R.string.add_btn))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { addDialogOpen = false }) {
                        Text(stringResource(R.string.cancel), color = cs.onSurface.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Create new plan dialog (Coach style)
        if (createNewPlanDialogOpen) {
            CalendarPlanCreateDialog(
                selectedDate = selectedDate,
                repository = repository,
                cloudSync = cloudSync,
                trainees = trainees,
                coachId = coachId,
                onDismiss = { createNewPlanDialogOpen = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarPlanCreateDialog(
    selectedDate: LocalDate,
    repository: TrainingPlanRepository,
    cloudSync: CloudSyncUseCase,
    trainees: List<CoachTrainee>,
    coachId: String,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var planName by remember { mutableStateOf("") }
    var activeDialog by remember { mutableStateOf<String?>(null) }

    val selectedItems = remember { mutableStateListOf<String>() }
    val selectedSets = remember { mutableStateMapOf<String, Int>() }
    val selectedWeights = remember { mutableStateMapOf<String, Float>() }

    val planExercises = remember { mutableStateListOf<ExerciseEntry>() }

    var publishTarget by remember { mutableStateOf<CoachTrainee?>(trainees.firstOrNull()) }
    var publishExpanded by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }

    val defaultSets = 5
    val defaultWeight = 40f

    // Localized labels for plan name generation
    val partLabelsMap = mapOf(
        "chest" to stringResource(R.string.chest),
        "back" to stringResource(R.string.back_part),
        "legs" to stringResource(R.string.legs),
        "arms" to stringResource(R.string.arms),
        "shoulders" to stringResource(R.string.shoulders),
        "abs" to stringResource(R.string.abs)
    )
    val trainingLabel = stringResource(R.string.training_label)

    fun weightToSets(weight: Float, minSets: Int = 3, maxSets: Int = 12): Int {
        val w = weight.coerceIn(0f, 100f)
        val normalized = (w / 100f)
        val sets = (maxSets - (normalized * (maxSets - minSets))).roundToInt()
        return sets.coerceIn(minSets, maxSets)
    }

    fun resolvedPlanName(): String {
        val trimmed = planName.trim()
        if (trimmed.isNotBlank()) return trimmed

        val base = partLabelsMap[activeDialog] ?: trainingLabel
        return "$base ${selectedDate.format(DateTimeFormatter.ofPattern("M/d"))}"
    }

    val parts = listOf(
        CoachPartUi("chest", stringResource(R.string.chest), Icons.Default.Favorite),
        CoachPartUi("back", stringResource(R.string.back_part), Icons.Default.AirlineSeatReclineExtra),
        CoachPartUi("legs", stringResource(R.string.legs), Icons.AutoMirrored.Filled.DirectionsRun),
        CoachPartUi("arms", stringResource(R.string.arms), Icons.Default.FitnessCenter),
        CoachPartUi("shoulders", stringResource(R.string.shoulders), Icons.Default.AccessibilityNew),
        CoachPartUi("abs", stringResource(R.string.abs), Icons.Default.SportsMartialArts),
    )


    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("M/d"))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface.copy(alpha = 0.95f),
        titleContentColor = cs.primary,
        textContentColor = cs.onSurface,
        title = { Text(stringResource(R.string.create_plan_for_date, dateStr)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plan_name_optional)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.onSurface.copy(alpha = 0.3f),
                        focusedLabelColor = cs.primary,
                        unfocusedLabelColor = cs.onSurface.copy(alpha = 0.7f),
                        focusedTextColor = cs.onSurface,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.select_body_part_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Part selection chips (Custom Neon Style)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parts.forEach { part ->
                        val isSelected = activeDialog == part.key
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) cs.primary else cs.onSurface.copy(alpha = 0.1f)
                                )
                                .clickable {
                                    activeDialog = part.key
                                    selectedItems.clear()
                                    selectedSets.clear()
                                    selectedWeights.clear()
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    part.icon,
                                    contentDescription = part.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) cs.onPrimary else cs.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    part.label,
                                    color = if (isSelected) cs.onPrimary else cs.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Plan preview
                if (planExercises.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.added_exercises, planExercises.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        planExercises.take(8).forEach { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(cs.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = e.name,
                                    color = cs.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.sets_x_weight, e.sets ?: 0, e.weight?.roundToInt() ?: 0),
                                    color = cs.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (planExercises.size > 8) {
                        Text(
                            text = stringResource(R.string.more_exercises, planExercises.size - 8),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { planExercises.clear() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Text(stringResource(R.string.clear_all_exercises))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Publish to trainee (if applicable)
                if (trainees.isNotEmpty()) {
                    OutlinedButton(
                        enabled = planExercises.isNotEmpty() && coachId.isNotBlank(),
                        onClick = { confirmPublish = true },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cs.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.publish))
                    }
                }

                // Save locally only
                Button(
                    enabled = planExercises.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            val now = Instant.now()
                            val plan = TrainingPlan(
                                id = UUID.randomUUID().toString(),
                                name = resolvedPlanName(),
                                exercises = planExercises.toList(),
                                createdAt = now,
                                updatedAt = now,
                            )
                            repository.addPlan(plan)
                            repository.addScheduledPlan(selectedDate, plan.id)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            // Moved Cancel to the top right 'X' or handled by tapping outside,
            // but for AlertDialog standard, let's keep a text button if needed,
            // or rely on onDismissRequest. Here we just put a spacer to push buttons right if standard logic applies,
            // but since we used a Row in confirmButton, we can omit this or keep it simple.
        }
    )

    // Exercise selection dialog
    if (activeDialog != null) {
        val context = LocalContext.current
        val groupsToShow = remember(activeDialog, context) {
            val key = activeDialog
            if (key.isNullOrBlank()) emptyList() else PartExerciseCatalog.getLocalizedGroupsForPart(key, context)
        }

        val partDisplayName = when (activeDialog) {
            "chest" -> stringResource(R.string.chest_part)
            "back" -> stringResource(R.string.back_part_full)
            "legs" -> stringResource(R.string.legs_part)
            "arms" -> stringResource(R.string.arms_part)
            "shoulders" -> stringResource(R.string.shoulders_part)
            "abs" -> stringResource(R.string.abs_part)
            else -> ""
        }

        AlertDialog(
            onDismissRequest = {
                activeDialog = null
                selectedItems.clear()
                selectedSets.clear()
                selectedWeights.clear()
            },
            containerColor = cs.surface,
            title = {
                Text(
                    stringResource(R.string.part_training_options, partDisplayName),
                    color = cs.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    groupsToShow.forEach { (group, items) ->
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        items.forEach { item ->
                            val isSelected = selectedItems.contains(item)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (isSelected) cs.primary.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if(isSelected) 1.dp else 0.dp,
                                        color = if(isSelected) cs.primary.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedItems.remove(item)
                                                selectedSets.remove(item)
                                                selectedWeights.remove(item)
                                            } else {
                                                selectedItems.add(item)
                                                if (!selectedSets.containsKey(item)) selectedSets[item] = defaultSets
                                                if (!selectedWeights.containsKey(item)) selectedWeights[item] = defaultWeight
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null, // Handled by Row click
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = cs.primary,
                                            checkmarkColor = cs.surface,
                                            uncheckedColor = cs.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                    Text(
                                        text = item,
                                        color = if (isSelected) cs.primary else cs.onSurface.copy(alpha = 0.8f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }

                                // Controls visible only when selected
                                if (isSelected) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp, bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.weight_label, selectedWeights[item]?.roundToInt() ?: defaultWeight.toInt()),
                                            color = cs.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Slider(
                                            value = selectedWeights[item] ?: defaultWeight,
                                            onValueChange = { v ->
                                                selectedWeights[item] = v
                                                selectedSets[item] = weightToSets(v)
                                            },
                                            valueRange = 0f..100f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = cs.primary,
                                                activeTrackColor = cs.primary,
                                                inactiveTrackColor = cs.primary.copy(alpha = 0.3f)
                                            )
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.sets_label), color = cs.onSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Custom - Button
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cs.onSurface.copy(alpha = 0.1f))
                                                    .clickable {
                                                        val cur = selectedSets[item] ?: defaultSets
                                                        selectedSets[item] = (cur - 1).coerceAtLeast(1)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("-", color = cs.onSurface, fontWeight = FontWeight.Bold)
                                            }

                                            Text(
                                                text = " ${selectedSets[item] ?: defaultSets} ",
                                                color = cs.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )

                                            // Custom + Button
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cs.onSurface.copy(alpha = 0.1f))
                                                    .clickable {
                                                        val cur = selectedSets[item] ?: defaultSets
                                                        selectedSets[item] = (cur + 1).coerceAtMost(20)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+", color = cs.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newEntries = selectedItems.map { item ->
                            val sets = selectedSets[item] ?: defaultSets
                            val weight = selectedWeights[item] ?: defaultWeight
                            ExerciseEntry(
                                name = item,
                                reps = null,
                                sets = sets,
                                weight = weight
                            )
                        }
                        planExercises.addAll(newEntries)
                        activeDialog = null
                        selectedItems.clear()
                        selectedSets.clear()
                        selectedWeights.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) { Text(stringResource(R.string.confirm_add)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        activeDialog = null
                        selectedItems.clear()
                        selectedSets.clear()
                        selectedWeights.clear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.6f))
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Publish confirmation dialog
    if (confirmPublish) {
        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            containerColor = cs.surface.copy(alpha = 0.95f),
            titleContentColor = cs.primary,
            title = { Text(stringResource(R.string.select_trainee)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.publish_to),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleTraineePicker(
                        trainees = trainees,
                        expanded = publishExpanded,
                        onExpandedChange = { publishExpanded = it },
                        selected = publishTarget,
                        onSelected = { publishTarget = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = publishTarget ?: return@Button
                        scope.launch {
                            val now = Instant.now()
                            val plan = TrainingPlan(
                                id = UUID.randomUUID().toString(),
                                name = resolvedPlanName(),
                                exercises = planExercises.toList(),
                                createdAt = now,
                                updatedAt = now,
                                publishedAt = now,
                            )

                            // Save locally
                            repository.addPlan(plan)
                            repository.addScheduledPlan(selectedDate, plan.id)

                            // Publish to trainee with scheduled date
                            val result = cloudSync.publishCoachLocalPlanToTrainee(
                                coachId = coachId,
                                traineeId = target.traineeId,
                                plan = plan
                            )

                            confirmPublish = false
                            onDismiss()
                        }
                    },
                    enabled = publishTarget != null && coachId.isNotBlank() && planExercises.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) { Text(stringResource(R.string.confirm_publish)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmPublish = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.6f))
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ════════════════════════════════════════════
//  日曆子元件
// ════════════════════════════════════════════

/** 區段標題 */
@Composable
private fun CalendarSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(0.15f)
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/** 訓練紀錄卡片 */
@Composable
private fun CalendarTrainingRecordCard(record: TrainingRecord) {
    val cs = MaterialTheme.colorScheme
    val color = Color(0xFF16A34A) // NeonGreen
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(if (expanded) 0.3f else 0.1f), RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(0.3f))
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = tween(300))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsRun, null,
                    tint = color, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.planName.ifBlank { "訓練" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${record.exercises.size} 個動作",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        "${record.durationInSeconds / 60} 分鐘",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(0.12f)
            ) {
                Text(
                    "${"%.0f".format(record.caloriesBurned)} kcal",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        // 展開顯示動作列表
        if (expanded && record.exercises.isNotEmpty()) {
            HorizontalDivider(
                color = cs.outline.copy(0.2f),
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                record.exercises.forEachIndexed { i, ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            ex.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (ex.sets > 0) {
                            Text(
                                "${ex.sets}組",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary
                            )
                        }
                        if (ex.weight != null && ex.weight > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${"%.0f".format(ex.weight)}kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }
                }
            }
        }

        // 備註
        if (expanded && !record.notes.isNullOrBlank()) {
            Text(
                "📝 ${record.notes}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            )
        }
    }
}

/** 飲食餐別分組卡片 */
@Composable
private fun CalendarMealGroupCard(
    mealType: String,
    records: List<DietRecord>
) {
    val cs = MaterialTheme.colorScheme
    val color = Color(0xFFEA580C) // NeonOrange equivalent
    val mealIcon = when (mealType) {
        "早餐" -> Icons.Default.WbSunny
        "午餐" -> Icons.Default.LightMode
        "晚餐" -> Icons.Default.NightsStay
        "點心" -> Icons.Default.BrunchDining
        else -> Icons.Default.Restaurant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.1f), RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(0.3f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(mealIcon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                mealType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${records.sumOf { it.calories }} kcal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        records.forEach { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.onSurface.copy(0.03f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.foodName,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${record.calories} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(0.7f)
                )
                if (record.proteinG != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "P${"%.0f".format(record.proteinG)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary
                    )
                }
            }
        }
    }
}

/** 營養素統計項 */
@Composable
private fun CalendarNutrientStat(
    label: String,
    value: String,
    color: Color
) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}
