package com.example.fitness.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.activity.ActivityRecord
import com.example.fitness.data.*
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 已儲存課表頁面 — 點擊可展開查看動作清單，含「完成」按鈕同步至訓練紀錄。
 */
@Composable
fun SavedPlansScreen(
    repository: TrainingPlanRepository,
    activityRepository: ActivityLogRepository? = null,
    trainingRecordRepository: FirebaseTrainingRecordRepository? = null,
    onBack: () -> Unit,
) {
    val plans by repository.plans.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val partAnalysisRepo = remember { com.example.fitness.data.PartAnalysisRepository(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple))),
        contentAlignment = Alignment.TopCenter
    ) {
        Scaffold(
            modifier = Modifier.widthIn(max = 840.dp),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassEffect(cornerRadius = 20.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = TechColors.NeonBlue
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                text = stringResource(R.string.saved_templates),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "${plans.size} 個課表",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (plans.isEmpty()) {
                // 空狀態
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_saved_templates),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(plans, key = { it.id }) { plan ->
                        ExpandablePlanCard(
                            plan = plan,
                            dateFormatter = dateFormatter,
                            onDelete = {
                                repository.removePlan(plan.id)
                                scope.launch { snackbarHostState.showSnackbar("已刪除: ${plan.name}") }
                            },
                            onComplete = {
                                scope.launch {
                                    completePlan(
                                        plan = plan,
                                        activityRepository = activityRepository,
                                        trainingRecordRepository = trainingRecordRepository,
                                        partAnalysisRepository = partAnalysisRepo,
                                        snackbarHostState = snackbarHostState
                                    )
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * 將課表標記為完成 → 寫入 TrainingRecord (Firebase) + ActivityRecord + PartAnalysis
 */
private suspend fun completePlan(
    plan: TrainingPlan,
    activityRepository: ActivityLogRepository?,
    trainingRecordRepository: FirebaseTrainingRecordRepository?,
    partAnalysisRepository: com.example.fitness.data.PartAnalysisRepository,
    snackbarHostState: SnackbarHostState
) {
    val now = Instant.now()
    val today = LocalDate.now()

    // 估算訓練時間 (每個動作約 3~5 分鐘)
    val estimatedDuration = plan.exercises.size * 4 * 60 // 每動作 4 分鐘
    // 估算卡路里 (每組約 8 kcal)
    val estimatedCalories = plan.exercises.sumOf { (it.sets ?: 3) * 8.0 }

    // 1. 寫入 Firebase TrainingRecord
    trainingRecordRepository?.let { repo ->
        val record = TrainingRecord(
            planId = plan.id,
            planName = plan.name,
            date = today,
            durationInSeconds = estimatedDuration,
            caloriesBurned = estimatedCalories,
            exercises = plan.exercises.map { e ->
                ExerciseRecord(
                    name = e.name,
                    sets = e.sets ?: 0,
                    reps = e.reps ?: 0,
                    weight = e.weight?.toDouble()
                )
            },
            notes = "透過課表「${plan.name}」完成"
        )
        val result = repo.addRecordToFirebase(record)
        if (result.isFailure) {
            snackbarHostState.showSnackbar("❌ 儲存訓練紀錄失敗")
            return
        }
    }

    // 2. 寫入 ActivityRecord
    activityRepository?.let { repo ->
        repo.add(
            ActivityRecord(
                id = UUID.randomUUID().toString(),
                planId = plan.id,
                type = "workout_${plan.name}",
                start = now.minusSeconds(estimatedDuration.toLong()),
                end = now,
                calories = estimatedCalories,
                exercises = plan.exercises
            )
        )
    }

    // 3. 寫入 PartAnalysisRepository 以顯示部位分析圖表
    val maxWeights = plan.exercises
        .filter { it.weight != null && it.weight > 0f }
        .groupBy { it.name }
        .mapValues { (_, entries) ->
            entries.maxOf { it.weight!! }
        }

    if (maxWeights.isNotEmpty()) {
        try {
            partAnalysisRepository.addOrUpdateRecord(today.toString(), maxWeights)
        } catch (_: Exception) {}
    }

    snackbarHostState.showSnackbar("✅ 已完成「${plan.name}」並同步至訓練紀錄")
}

// ════════════════════════════════════════════
//  可展開的課表卡片
// ════════════════════════════════════════════

@Composable
private fun ExpandablePlanCard(
    plan: TrainingPlan,
    dateFormatter: DateTimeFormatter,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrow"
    )

    val accentColor = TechColors.NeonBlue
    val borderColor = if (expanded) accentColor.copy(0.4f) else Color.White.copy(0.12f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .animateContentSize(animationSpec = tween(300))
    ) {
        // ── 標題列（可點擊展開） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 動作數量 badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(0.15f)
                    ) {
                        Text(
                            text = "${plan.exercises.size} 個動作",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = plan.createdAt.atZone(ZoneId.systemDefault()).format(dateFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 展開箭頭
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        // ── 展開內容：動作清單 + 操作按鈕 ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                // 分隔線
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                // 動作清單
                if (plan.exercises.isNotEmpty()) {
                    plan.exercises.forEachIndexed { index, exercise ->
                        ExerciseRow(
                            index = index + 1,
                            exercise = exercise,
                            accentColor = accentColor
                        )
                        if (index < plan.exercises.size - 1) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                } else {
                    Text(
                        "尚無動作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 統計摘要
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniStat(
                        label = "動作",
                        value = "${plan.exercises.size}",
                        color = accentColor
                    )
                    MiniStat(
                        label = "總組數",
                        value = "${plan.exercises.sumOf { it.sets ?: 0 }}",
                        color = TechColors.NeonGreen
                    )
                    MiniStat(
                        label = "預估時間",
                        value = "${plan.exercises.size * 4} min",
                        color = TechColors.NeonOrange
                    )
                    MiniStat(
                        label = "預估 kcal",
                        value = "${plan.exercises.sumOf { (it.sets ?: 3) * 8 }}",
                        color = TechColors.NeonPurple
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 操作按鈕列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 刪除
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TechColors.NeonRed.copy(0.8f)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(
                                listOf(TechColors.NeonRed.copy(0.3f), TechColors.NeonRed.copy(0.1f))
                            )
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刪除", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 完成
                    Button(
                        onClick = { showCompleteConfirm = true },
                        enabled = !isCompleted,
                        modifier = Modifier.weight(2f).heightIn(min = 42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechColors.NeonGreen.copy(0.2f),
                            contentColor = TechColors.NeonGreen,
                            disabledContainerColor = TechColors.NeonGreen.copy(0.08f),
                            disabledContentColor = TechColors.NeonGreen.copy(0.4f)
                        )
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("已完成", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("完成訓練", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // ── 刪除確認 Dialog ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = TechColors.DarkBlue,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("刪除課表", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "確定要刪除「${plan.name}」嗎？此操作無法復原。",
                    color = Color.White.copy(0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("刪除", color = TechColors.NeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(0.5f))
                }
            }
        )
    }

    // ── 完成確認 Dialog ──
    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            containerColor = TechColors.DarkBlue,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    Icons.Default.EmojiEvents,
                    null,
                    tint = TechColors.NeonYellow,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("完成訓練", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column {
                    Text(
                        "確認完成「${plan.name}」？",
                        color = Color.White.copy(0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "此操作將同步至訓練紀錄與活動數據。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.5f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteConfirm = false
                    isCompleted = true
                    onComplete()
                }) {
                    Text("確認完成", color = TechColors.NeonGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(0.5f))
                }
            }
        )
    }
}

// ════════════════════════════════════════════
//  子元件
// ════════════════════════════════════════════

/** 單一動作行 */
@Composable
private fun ExerciseRow(
    index: Int,
    exercise: ExerciseEntry,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序號圓圈
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.width(10.dp))

        // 動作名稱
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 組數
        exercise.sets?.let { sets ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TechColors.NeonGreen.copy(0.12f)
            ) {
                Text(
                    "${sets}組",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TechColors.NeonGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // 重量
        exercise.weight?.let { weight ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TechColors.NeonOrange.copy(0.12f)
            ) {
                Text(
                    "${weight.roundToInt()}kg",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TechColors.NeonOrange,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 迷你統計項 */
@Composable
private fun MiniStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.4f)
        )
    }
}
