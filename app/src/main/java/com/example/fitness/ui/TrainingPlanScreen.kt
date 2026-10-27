package com.example.fitness.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.activity.ActivityRecord
import com.example.fitness.calorie.CalorieCalculator
import com.example.fitness.coach.UserRole
import com.example.fitness.coach.local.CoachPlanSelectionRepository
import com.example.fitness.data.ExerciseEntry
import com.example.fitness.data.ExerciseLibrary
import com.example.fitness.data.ExerciseRecord
import com.example.fitness.data.PartAnalysisRepository
import com.example.fitness.data.PartExerciseCatalog
import com.example.fitness.data.TrainingPlan
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.data.TrainingRecord
import com.example.fitness.data.TrainingRecordRepository
import com.example.fitness.firestore.FirestoreTrainingRepository
import com.example.fitness.template.LocalTemplateRepository
import com.example.fitness.template.SimpleExerciseItem
import com.example.fitness.template.SimpleWorkoutTemplate
import com.example.fitness.template.TemplateLevel
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.user.UserRoleProfileRepository
import com.example.fitness.data.FirebaseTrainingRecordRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TrainingPlanScreen(
    repository: TrainingPlanRepository,
    activityRepository: ActivityLogRepository,
    trainingRecordRepository: TrainingRecordRepository,
    // 修正：移除了參數中的預設 remember 初始化，改到函式內部
    modifier: Modifier = Modifier,
    onRecordCreated: (() -> Unit)? = null,
    onCreatePlan: (() -> Unit)? = null,
    initialDialog: String? = null,
    onInitialDialogConsumed: (() -> Unit)? = null
) {
    // ★★★ 修正：在函式內部初始化 PartAnalysisRepository ★★★
    val context = LocalContext.current
    val partAnalysisRepository = remember { PartAnalysisRepository(context) }

    // 課表模板 Repository
    val localTemplateRepo = remember { LocalTemplateRepository(context) }

    // State
    val plansState by repository.plans.collectAsState(initial = emptyList())

    // Coach plan selection
    val appCtx = remember(context) { context.applicationContext }
    val coachSelectionRepo = remember(appCtx) { CoachPlanSelectionRepository(appCtx) }
    val coachSelectedIds by coachSelectionRepo.selectedPlanIds.collectAsState(initial = emptySet())

    // User role check
    val roleRepo = remember { UserRoleProfileRepository() }
    var myRole by remember { mutableStateOf<UserRole?>(null) }

    LaunchedEffect(Unit) {
        myRole = roleRepo.getMyRole().getOrNull()
    }

    // Dialog state
    var activeDialog by remember { mutableStateOf<String?>(initialDialog) }
    val selectedItems = remember { mutableStateListOf<String>() }
    val selectedSets = remember { mutableStateMapOf<String, Int>() }
    val selectedWeights = remember { mutableStateMapOf<String, Float>() }

    // 課表相關狀態
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    val defaultSets = 8
    val defaultWeight = 40f

    fun weightToSets(weight: Float, minSets: Int = 3, maxSets: Int = 12, maxWeight: Float = 100f): Int {
        val ratio = (weight.coerceIn(0f, maxWeight)) / maxWeight
        val setsFloat = maxSets - ratio * (maxSets - minSets)
        return setsFloat.roundToInt().coerceIn(minSets, maxSets)
    }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val firestoreTrainingRepository = remember { FirestoreTrainingRepository() }

    // Grouped presets logic
    val chestPresets = listOf("槓鈴臥推 (Bench Press)", "啞鈴飛鳥", "上斜槓鈴臥推 (上胸)")
    val backPresets = listOf("引體向上", "啞鈴單手划船", "坐姿划船")
    val legsPresets = listOf("深蹲", "硬舉", "腿推")

    LaunchedEffect(activeDialog) {
        selectedItems.clear()
        selectedSets.clear()

        val openedFromPartPicker = !initialDialog.isNullOrBlank()
        if (openedFromPartPicker) {
            return@LaunchedEffect
        }

        when (activeDialog) {
            "chest" -> {
                selectedItems.addAll(chestPresets.take(2))
                chestPresets.take(2).forEach { selectedSets[it] = defaultSets }
            }
            "back" -> {
                selectedItems.addAll(backPresets.take(2))
                backPresets.take(2).forEach { selectedSets[it] = defaultSets }
            }
            "legs" -> {
                selectedItems.addAll(listOf("深蹲", "硬舉"))
                listOf("深蹲", "硬舉").forEach { selectedSets[it] = defaultSets }
            }
            "arms" -> {
                selectedItems.addAll(listOf("站姿啞鈴彎舉", "臥姿啞鈴三頭肌伸展"))
                listOf("站姿啞鈴彎舉", "臥姿啞鈴三頭肌伸展").forEach { selectedSets[it] = defaultSets }
            }
            "shoulders" -> {
                selectedItems.addAll(listOf("啞鈴肩推（Dumbbell Overhead Press）", "啞鈴側平舉（Lateral Raise）"))
                listOf("啞鈴肩推（Dumbbell Overhead Press）", "啞鈴側平舉（Lateral Raise）").forEach { selectedSets[it] = defaultSets }
            }
            else -> {}
        }
    }

    LaunchedEffect(initialDialog) {
        if (!initialDialog.isNullOrBlank()) {
            activeDialog = initialDialog
            onInitialDialogConsumed?.invoke()
        }
    }

    val cs = MaterialTheme.colorScheme

    // Global Gradient Background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TechColors.DarkBlue, TechColors.DeepPurple)
                )
            )
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp).padding(16.dp)) {
            val activityLogs by activityRepository.logs.collectAsState()
            val completedPlans = remember(activityLogs) { activityLogs.mapNotNull { it.planId }.toMutableSet() }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(plansState) { plan ->
                    val isCoachPlan = coachSelectedIds.contains(plan.id)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(cornerRadius = 20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = plan.name,
                                color = cs.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "動作數: ${plan.exercises.size}",
                                color = cs.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                plan.exercises.take(5).forEach { e ->
                                    val weightText = e.weight?.let { " • ${it.roundToInt()}kg" } ?: ""
                                    val setsText = e.sets?.let { " • ${it}組" } ?: ""
                                    Text(
                                        text = "• ${e.name}$setsText$weightText",
                                        color = cs.onSurface.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                                if (plan.exercises.size > 5) {
                                    Text(
                                        text = "... 以及其他 ${plan.exercises.size - 5} 個動作",
                                        color = cs.onSurface.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "建立於: ${plan.createdAt.atZone(ZoneId.systemDefault()).format(dateFormatter)}",
                                color = cs.onSurface.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Actions Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 只有教練身分才能看到並操作「加入教練中心」的按鈕
                                if (myRole == UserRole.COACH) {
                                    OutlinedButton(
                                        onClick = {
                                            uiScope.launch {
                                                coachSelectionRepo.setSelected(plan.id, !isCoachPlan)
                                                snackbarHostState.showSnackbar(
                                                    if (!isCoachPlan) "已加入教練中心" else "已移出教練中心"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .neonGlowBorder(cornerRadius = 12.dp, borderWidth = 1.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (isCoachPlan) cs.primary else cs.onSurface.copy(alpha = 0.7f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isCoachPlan) cs.primary else cs.onSurface.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text(
                                            if (isCoachPlan) "移出教練" else "加入教練",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Complete Button
                                if (completedPlans.contains(plan.id)) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = cs.onSurface.copy(alpha = 0.1f),
                                            disabledContentColor = cs.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("已完成")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            fun estimatePlanCalories(plan: TrainingPlan, userWeightKg: Double = 70.0): Double {
                                                val minutesPerSet = 0.5
                                                val met = 6.0
                                                var totalMinutes = 0.0
                                                plan.exercises.forEach { e ->
                                                    val sets = (e.sets ?: 3).toDouble()
                                                    totalMinutes += sets * minutesPerSet
                                                }
                                                return CalorieCalculator.estimateCalories(met, userWeightKg, totalMinutes)
                                            }

                                            val estimatedCalories = estimatePlanCalories(plan)

                                            // 1. 寫入 Activity Log
                                            // 注意：為了避免「雙寫入」造成首頁今日消耗重複計算，
                                            // 這裡只保留 ActivityLogRepository（FirebaseActivityLogRepository）的寫入。
                                            val record = ActivityRecord(
                                                id = UUID.randomUUID().toString(),
                                                planId = plan.id,
                                                type = plan.name,
                                                start = Instant.now(),
                                                end = null,
                                                calories = estimatedCalories,
                                                exercises = plan.exercises
                                            )
                                            activityRepository.add(record)

                                            // 2. 寫入 Training Record
                                            // 若後續要完全 Firestore 化，建議 TrainingRecordRepository 也改成只讀寫 Firebase。
                                            val trainingRecord = TrainingRecord(
                                                planId = plan.id,
                                                planName = plan.name,
                                                date = LocalDate.now(),
                                                durationInSeconds = 3600,
                                                caloriesBurned = estimatedCalories,
                                                exercises = plan.exercises.map { e ->
                                                    ExerciseRecord(
                                                        name = e.name,
                                                        sets = e.sets ?: 0,
                                                        reps = e.reps ?: 0,
                                                        weight = e.weight?.toDouble()
                                                    )
                                                }
                                            )

                                            // 2. 寫入 Training Record（Firestore /training_records）
                                            // 注意：TrainingRecordRepository.addRecord() 已被標記為 deprecated no-op
                                            uiScope.launch {
                                                val firebaseRepo: FirebaseTrainingRecordRepository? =
                                                    trainingRecordRepository as? FirebaseTrainingRecordRepository

                                                if (firebaseRepo == null) {
                                                    Log.w(
                                                        "TrainingPlanScreen",
                                                        "trainingRecordRepository is not FirebaseTrainingRecordRepository; skip writing training_records"
                                                    )
                                                    return@launch
                                                }

                                                firebaseRepo.addRecordToFirebase(trainingRecord).fold(
                                                    onSuccess = { _: String ->
                                                        // ok
                                                    },
                                                    onFailure = { error: Throwable ->
                                                        Log.e(
                                                            "TrainingPlanScreen",
                                                            "Failed to add training record: ${error.message}",
                                                            error
                                                        )
                                                    }
                                                )
                                            }

                                            // 3. （可選）寫入 Firestore training_records：若已統一用 activity_logs / training_records，
                                            // 這段避免再寫一份不同 collection 的紀錄。
                                            // uiScope.launch {
                                            //     val uid = FirebaseAuth.getInstance().currentUser?.uid
                                            //     if (uid != null) {
                                            //         val fsRecord = com.example.fitness.firestore.TrainingRecord(
                                            //             id = UUID.randomUUID().toString(),
                                            //             date = trainingRecord.date,
                                            //             type = trainingRecord.planName,
                                            //             durationMinutes = (trainingRecord.durationInSeconds / 60).coerceAtLeast(0),
                                            //             exercises = trainingRecord.exercises.map { ex ->
                                            //                 com.example.fitness.firestore.ExerciseEntry(
                                            //                     name = ex.name,
                                            //                     sets = ex.sets,
                                            //                     reps = ex.reps,
                                            //                     weight = (ex.weight ?: 0.0).toFloat()
                                            //                 )
                                            //             }
                                            //         )
                                            //         firestoreTrainingRepository.addTrainingRecord(uid, fsRecord)
                                            //     }
                                            // }

                                            // ★★★ 4. 寫入 Part Analysis (部位分析) ★★★
                                            uiScope.launch {
                                                val maxWeights = plan.exercises
                                                    .filter { it.weight != null && it.weight > 0f }
                                                    .groupBy { it.name }
                                                    .mapValues { (_, entries) ->
                                                        entries.maxOf { it.weight!! }
                                                    }

                                                if (maxWeights.isNotEmpty()) {
                                                    val todayStr = LocalDate.now().toString()
                                                    partAnalysisRepository.addOrUpdateRecord(todayStr, maxWeights)
                                                }
                                            }

                                            completedPlans.add(plan.id)
                                            uiScope.launch {
                                                snackbarHostState.showSnackbar("已紀錄: ${plan.name} (約 ${"%.0f".format(estimatedCalories)} kcal)")
                                                onRecordCreated?.invoke()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .neonGlowBorder(cornerRadius = 12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cs.primary.copy(alpha = 0.2f),
                                            contentColor = cs.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("完成", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // --- Create Plan Dialog (Neon Style) ---
    val groupsToShow: List<Pair<String, List<String>>> = remember(activeDialog) {
        val key = activeDialog
        if (key.isNullOrBlank()) emptyList() else PartExerciseCatalog.groupsForPart(key)
    }

    val fallbackPresets = when (activeDialog) {
        "chest" -> chestPresets
        "back" -> backPresets
        "legs" -> legsPresets
        "arms" -> listOf("站姿啞鈴彎舉", "三頭下拉")
        "shoulders" -> listOf("啞鈴肩推", "啞鈴側平舉")
        "abs" -> ExerciseLibrary.AbdominalGroups.flatMap { it.exercises.map { ex -> ex.name } }.take(6)
        else -> emptyList()
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

    // 取得當前部位的已儲存課表
    val savedTemplatesForPart by localTemplateRepo.getTemplatesForPart(activeDialog ?: "").collectAsState(initial = emptyList())

    if (activeDialog != null) {
        val cs = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { activeDialog = null; selectedItems.clear(); selectedSets.clear() },
            containerColor = cs.surface,
            titleContentColor = cs.primary,
            textContentColor = cs.onSurface,
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.add_workout_template),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$partDisplayName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 已儲存的課表區域
                    if (savedTemplatesForPart.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.saved_templates),
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        savedTemplatesForPart.forEach { template ->
                            val templateLevel = template.level
                            val templateDescription = template.description
                            val levelColor = when (templateLevel) {
                                TemplateLevel.BEGINNER -> Color(0xFF4CAF50)
                                TemplateLevel.INTERMEDIATE -> Color(0xFFFF9800)
                                TemplateLevel.ADVANCED -> Color(0xFFF44336)
                            }
                            val levelName = when (templateLevel) {
                                TemplateLevel.BEGINNER -> stringResource(R.string.level_beginner)
                                TemplateLevel.INTERMEDIATE -> stringResource(R.string.level_intermediate)
                                TemplateLevel.ADVANCED -> stringResource(R.string.level_advanced)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    // 標題列
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // 課表圖示
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(cs.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.FolderOpen,
                                                    contentDescription = null,
                                                    tint = cs.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    template.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cs.onSurface
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 等級標籤
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = levelColor.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            levelName,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = levelColor,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        "${template.exercises.size} 個動作",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = cs.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                uiScope.launch {
                                                    localTemplateRepo.deleteTemplate(template.id)
                                                    snackbarHostState.showSnackbar(context.getString(R.string.template_deleted))
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete_template),
                                                tint = cs.error.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // 描述（如果有）
                                    if (templateDescription.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            templateDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    // 動作按鈕列
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 使用課表（直接建立訓練計畫）
                                        Button(
                                            onClick = {
                                                // 直接使用此課表建立訓練計畫
                                                val entries = template.exercises.map { ex ->
                                                    ExerciseEntry(ex.name, null, ex.sets, ex.weight)
                                                }
                                                repository.addPlan(TrainingPlan(name = template.name, exercises = entries))
                                                uiScope.launch {
                                                    localTemplateRepo.updateLastUsed(template.id)
                                                    snackbarHostState.showSnackbar(context.getString(R.string.template_loaded))
                                                }
                                                selectedItems.clear()
                                                selectedSets.clear()
                                                selectedWeights.clear()
                                                activeDialog = null
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 40.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = cs.primary,
                                                contentColor = cs.onPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.use_template), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        // 編輯課表（載入到選擇列表）
                                        OutlinedButton(
                                            onClick = {
                                                // 載入課表到選擇列表以供編輯
                                                selectedItems.clear()
                                                selectedSets.clear()
                                                selectedWeights.clear()
                                                template.exercises.forEach { ex ->
                                                    selectedItems.add(ex.name)
                                                    selectedSets[ex.name] = ex.sets
                                                    selectedWeights[ex.name] = ex.weight
                                                }
                                                // 設定要編輯的課表名稱
                                                templateName = template.name
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 40.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.edit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = cs.outline.copy(alpha = 0.3f)
                        )
                    }

                    // 動作選擇區域
                    Text(
                        text = stringResource(R.string.select_body_part_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    groupsToShow.forEach { (group, items) ->
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        items.forEach { item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isSelected = selectedItems.contains(item)
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedItems.add(item)
                                                if (!selectedSets.containsKey(item)) selectedSets[item] = defaultSets
                                            } else {
                                                selectedItems.remove(item)
                                                selectedSets.remove(item)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = cs.primary,
                                            uncheckedColor = cs.onSurfaceVariant,
                                            checkmarkColor = cs.onPrimary
                                        )
                                    )
                                    Text(
                                        text = item,
                                        modifier = Modifier.padding(start = 8.dp),
                                        color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                if (selectedItems.contains(item)) {
                                    val curW = selectedWeights[item] ?: defaultWeight
                                    if (!selectedWeights.containsKey(item)) selectedWeights[item] = curW
                                    if (!selectedSets.containsKey(item)) selectedSets[item] = weightToSets(curW, minSets = 3, maxSets = 12)

                                    Column(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp)) {
                                        Text(
                                            text = "重量: ${selectedWeights[item]?.roundToInt() ?: defaultWeight.toInt()} kg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.primary
                                        )
                                        Slider(
                                            value = selectedWeights[item] ?: defaultWeight,
                                            onValueChange = { v ->
                                                selectedWeights[item] = v
                                                selectedSets[item] = weightToSets(v)
                                            },
                                            valueRange = 0f..100f,
                                            steps = 99,
                                            colors = SliderDefaults.colors(
                                                thumbColor = cs.primary,
                                                activeTrackColor = cs.primary,
                                                inactiveTrackColor = cs.primary.copy(alpha = 0.3f)
                                            )
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("組數:", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Mini Buttons for Sets
                                            IconButton(
                                                onClick = { val cur = selectedSets[item] ?: defaultSets; selectedSets[item] = (cur - 1).coerceAtLeast(1) },
                                                modifier = Modifier.size(32.dp).background(cs.surfaceVariant, RoundedCornerShape(8.dp))
                                            ) { Text("-", color = cs.onSurface) }

                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = "${selectedSets[item] ?: defaultSets}",
                                                color = cs.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(Modifier.width(12.dp))

                                            IconButton(
                                                onClick = { val cur = selectedSets[item] ?: defaultSets; selectedSets[item] = (cur + 1).coerceAtMost(20) },
                                                modifier = Modifier.size(32.dp).background(cs.surfaceVariant, RoundedCornerShape(8.dp))
                                            ) { Text("+", color = cs.onSurface) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 取消按鈕
                    OutlinedButton(
                        onClick = { selectedItems.clear(); selectedSets.clear(); selectedWeights.clear(); activeDialog = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    // 儲存課表按鈕
                    Button(
                        onClick = {
                            if (selectedItems.isNotEmpty()) {
                                showSaveTemplateDialog = true
                            }
                        },
                        enabled = selectedItems.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {}
        )
    }

    // 儲存課表對話框
    if (showSaveTemplateDialog && activeDialog != null) {
        var selectedLevel by remember { mutableStateOf(TemplateLevel.BEGINNER) }
        var templateDescription by remember { mutableStateOf("") }
        val savecs = MaterialTheme.colorScheme

        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false; templateName = "" },
            containerColor = savecs.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        tint = savecs.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.save_as_template),
                        fontWeight = FontWeight.Bold,
                        color = savecs.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 課表名稱
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text(stringResource(R.string.template_name)) },
                        placeholder = { Text(stringResource(R.string.template_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, tint = savecs.primary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = savecs.primary,
                            unfocusedBorderColor = savecs.outline
                        )
                    )

                    // 課表描述
                    OutlinedTextField(
                        value = templateDescription,
                        onValueChange = { templateDescription = it },
                        label = { Text(stringResource(R.string.description)) },
                        placeholder = { Text("簡述這個課表的訓練目標...") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = savecs.primary,
                            unfocusedBorderColor = savecs.outline
                        )
                    )

                    // 等級選擇
                    Text(
                        stringResource(R.string.level),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = savecs.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TemplateLevel.entries.forEach { level ->
                            val isSelected = selectedLevel == level
                            val levelColor = when (level) {
                                TemplateLevel.BEGINNER -> Color(0xFF4CAF50)
                                TemplateLevel.INTERMEDIATE -> Color(0xFFFF9800)
                                TemplateLevel.ADVANCED -> Color(0xFFF44336)
                            }
                            val levelName = when (level) {
                                TemplateLevel.BEGINNER -> stringResource(R.string.level_beginner)
                                TemplateLevel.INTERMEDIATE -> stringResource(R.string.level_intermediate)
                                TemplateLevel.ADVANCED -> stringResource(R.string.level_advanced)
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLevel = level },
                                label = {
                                    Text(
                                        levelName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = levelColor.copy(alpha = 0.2f),
                                    selectedLabelColor = levelColor,
                                    selectedLeadingIconColor = levelColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 已選擇動作預覽
                    if (selectedItems.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = savecs.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "已選擇 ${selectedItems.size} 個動作",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = savecs.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    selectedItems.take(3).joinToString("、") + if (selectedItems.size > 3) " ..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = savecs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (templateName.isNotBlank() && selectedItems.isNotEmpty()) {
                            val template = SimpleWorkoutTemplate(
                                name = templateName,
                                bodyPart = activeDialog!!,
                                exercises = selectedItems.map { name ->
                                    SimpleExerciseItem(
                                        name = name,
                                        sets = selectedSets[name] ?: defaultSets,
                                        weight = selectedWeights[name] ?: defaultWeight
                                    )
                                },
                                level = selectedLevel,
                                description = templateDescription
                            )
                            uiScope.launch {
                                localTemplateRepo.addTemplate(template)
                                snackbarHostState.showSnackbar(context.getString(R.string.template_saved))
                            }
                            templateName = ""
                            showSaveTemplateDialog = false
                        }
                    },
                    enabled = templateName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = savecs.primary)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false; templateName = "" }) {
                    Text(stringResource(R.string.cancel), color = savecs.onSurfaceVariant)
                }
            }
        )
    }
}