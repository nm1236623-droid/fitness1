package com.example.fitness.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.data.ExerciseEntry
import com.example.fitness.data.PartExerciseCatalog
import com.example.fitness.data.TrainingPlan
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.template.LocalTemplateRepository
import com.example.fitness.template.SimpleExerciseItem
import com.example.fitness.template.SimpleWorkoutTemplate
import com.example.fitness.template.TemplateLevel
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 全頁式動作選擇畫面 — 取代原本的 AlertDialog。
 *
 * @param part         部位 key，例如 "chest", "back", "legs" 等
 * @param repository   訓練計畫 Repository（用來新增計畫）
 * @param onDone       完成 / 返回時的 callback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    part: String,
    repository: TrainingPlanRepository,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Repos ──
    val localTemplateRepo = remember { LocalTemplateRepository(context) }

    // ── State ──
    val selectedItems = remember { mutableStateListOf<String>() }
    val selectedSets = remember { mutableStateMapOf<String, Int>() }
    val selectedWeights = remember { mutableStateMapOf<String, Float>() }

    val defaultSets = 8
    val defaultWeight = 40f

    fun weightToSets(weight: Float): Int {
        val ratio = (weight.coerceIn(0f, 100f)) / 100f
        val s = 12 - ratio * (12 - 3)
        return s.roundToInt().coerceIn(3, 12)
    }

    // ── 資料 ──
    val groupsToShow = remember(part) {
        PartExerciseCatalog.groupsForPart(part)
    }

    val partDisplayName = when (part) {
        "chest"     -> stringResource(R.string.chest_part)
        "back"      -> stringResource(R.string.back_part_full)
        "legs"      -> stringResource(R.string.legs_part)
        "arms"      -> stringResource(R.string.arms_part)
        "shoulders" -> stringResource(R.string.shoulders_part)
        "abs"       -> stringResource(R.string.abs_part)
        else        -> ""
    }

    val savedTemplates by localTemplateRepo
        .getTemplatesForPart(part)
        .collectAsState(initial = emptyList())

    // ── 課表命名 Dialog ──
    var showNameDialog by remember { mutableStateOf(false) }
    var planNameInput by remember { mutableStateOf("") }

    // ── UI ──
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
                // ── 頂部標題列 ──
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
                        IconButton(onClick = onDone) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = cs.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                text = partDisplayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.primary
                            )
                            Text(
                                text = stringResource(R.string.select_body_part_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        // 已選數量 badge
                        if (selectedItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cs.primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "${selectedItems.size}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // ── 底部操作列 ──
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 0.dp,
                    color = cs.surface.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 取消
                        OutlinedButton(
                            onClick = onDone,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // 儲存課表（彈出命名 Dialog）
                        Button(
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    // 預設名稱：部位名稱 + 序號
                                    val existingCount = savedTemplates.size
                                    planNameInput = if (existingCount == 0) partDisplayName
                                        else "$partDisplayName ${existingCount + 1}"
                                    showNameDialog = true
                                }
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .then(
                                    if (selectedItems.isNotEmpty())
                                        Modifier.neonGlowBorder(16.dp, glowColor = cs.primary)
                                    else Modifier
                                ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary,
                                disabledContainerColor = cs.onSurface.copy(alpha = 0.12f)
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        ) { innerPadding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 已儲存的課表 ──
                if (savedTemplates.isNotEmpty()) {
                    item(key = "saved_header") {
                        Text(
                            text = stringResource(R.string.saved_templates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                    items(savedTemplates, key = { it.id }) { template ->
                        SavedTemplateCard(
                            template = template,
                            onUse = {
                                val entries = template.exercises.map { ex ->
                                    ExerciseEntry(ex.name, null, ex.sets, ex.weight)
                                }
                                repository.addPlan(TrainingPlan(name = template.name, exercises = entries))
                                scope.launch {
                                    localTemplateRepo.updateLastUsed(template.id)
                                    snackbarHostState.showSnackbar(context.getString(R.string.template_loaded))
                                }
                                onDone()
                            },
                            onLoad = {
                                selectedItems.clear(); selectedSets.clear(); selectedWeights.clear()
                                template.exercises.forEach { ex ->
                                    selectedItems.add(ex.name)
                                    selectedSets[ex.name] = ex.sets
                                    selectedWeights[ex.name] = ex.weight
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    localTemplateRepo.deleteTemplate(template.id)
                                    snackbarHostState.showSnackbar(context.getString(R.string.template_deleted))
                                }
                            }
                        )
                    }
                    item(key = "divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = cs.outline.copy(alpha = 0.2f)
                        )
                    }
                }

                // ── 動作選擇 ──
                item(key = "exercise_header") {
                    Text(
                        text = stringResource(R.string.select_body_part_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                }

                groupsToShow.forEach { (group, exercises) ->
                    item(key = "group_$group") {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(exercises, key = { "${group}_$it" }) { exerciseName ->
                        val isSelected = selectedItems.contains(exerciseName)
                        ExerciseItemCard(
                            name = exerciseName,
                            isSelected = isSelected,
                            sets = selectedSets[exerciseName] ?: defaultSets,
                            weight = selectedWeights[exerciseName] ?: defaultWeight,
                            onToggle = { checked ->
                                if (checked) {
                                    selectedItems.add(exerciseName)
                                    if (!selectedSets.containsKey(exerciseName)) selectedSets[exerciseName] = defaultSets
                                    if (!selectedWeights.containsKey(exerciseName)) selectedWeights[exerciseName] = defaultWeight
                                } else {
                                    selectedItems.remove(exerciseName)
                                    selectedSets.remove(exerciseName)
                                    selectedWeights.remove(exerciseName)
                                }
                            },
                            onWeightChange = { w ->
                                selectedWeights[exerciseName] = w
                                selectedSets[exerciseName] = weightToSets(w)
                            },
                            onSetsChange = { s -> selectedSets[exerciseName] = s }
                        )
                    }
                }

                // 底部留白
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // ── 課表命名 Dialog ──
        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                containerColor = cs.surface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        "課表名稱",
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                },
                text = {
                    Column {
                        Text(
                            "為這份課表取個名稱：",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = planNameInput,
                            onValueChange = { planNameInput = it },
                            singleLine = true,
                            placeholder = { Text(partDisplayName, color = cs.onSurfaceVariant) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = cs.onSurface,
                                unfocusedTextColor = cs.onSurface,
                                cursorColor = cs.primary,
                                focusedBorderColor = cs.primary,
                                unfocusedBorderColor = cs.outline.copy(alpha = 0.5f),
                                focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.2f),
                                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val finalName = planNameInput.ifBlank { partDisplayName }
                            showNameDialog = false

                            val exercises = selectedItems.map { name ->
                                SimpleExerciseItem(
                                    name = name,
                                    sets = selectedSets[name] ?: defaultSets,
                                    weight = selectedWeights[name] ?: defaultWeight
                                )
                            }
                            val tpl = SimpleWorkoutTemplate(
                                name = finalName,
                                bodyPart = part,
                                exercises = exercises,
                                level = TemplateLevel.BEGINNER,
                                description = ""
                            )
                            // 存入 TrainingPlanRepository
                            val entries = exercises.map { ex ->
                                ExerciseEntry(name = ex.name, sets = ex.sets, weight = ex.weight)
                            }
                            repository.addPlan(
                                TrainingPlan(name = finalName, exercises = entries)
                            )
                            // 非同步存入本地模板
                            scope.launch {
                                try {
                                    localTemplateRepo.addTemplate(tpl)
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.template_saved)
                                    )
                                } catch (_: Exception) { }
                                onDone()
                            }
                        }
                    ) {
                        Text("儲存", color = cs.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────
// 子元件
// ─────────────────────────────────────────────

/** 單一動作卡片（可展開調整重量 / 組數） */
@Composable
private fun ExerciseItemCard(
    name: String,
    isSelected: Boolean,
    sets: Int,
    weight: Float,
    onToggle: (Boolean) -> Unit,
    onWeightChange: (Float) -> Unit,
    onSetsChange: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) cs.primary else cs.outline.copy(alpha = 0.15f),
        label = "border"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        label = "elev"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(!isSelected) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) cs.primary.copy(alpha = 0.06f) else cs.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = cs.primary,
                        uncheckedColor = cs.onSurfaceVariant,
                        checkmarkColor = cs.onPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cs.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${sets}組",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                }
            }

            // 展開：重量 & 組數調整
            if (isSelected) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.outline.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))

                // 重量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${weight.roundToInt()} kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = weight,
                        onValueChange = onWeightChange,
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = cs.primary,
                            activeTrackColor = cs.primary,
                            inactiveTrackColor = cs.primary.copy(alpha = 0.2f)
                        )
                    )
                }

                // 組數
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "組數",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))

                    FilledIconButton(
                        onClick = { onSetsChange((sets - 1).coerceAtLeast(1)) },
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = cs.surfaceVariant,
                            contentColor = cs.onSurface
                        )
                    ) { Text("−", fontWeight = FontWeight.Bold) }

                    Text(
                        text = "$sets",
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )

                    FilledIconButton(
                        onClick = { onSetsChange((sets + 1).coerceAtMost(20)) },
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = cs.surfaceVariant,
                            contentColor = cs.onSurface
                        )
                    ) { Text("+", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** 已儲存課表卡片 */
@Composable
private fun SavedTemplateCard(
    template: SimpleWorkoutTemplate,
    onUse: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val levelColor = when (template.level) {
        TemplateLevel.BEGINNER     -> Color(0xFF4CAF50)
        TemplateLevel.INTERMEDIATE -> Color(0xFFFF9800)
        TemplateLevel.ADVANCED     -> Color(0xFFF44336)
    }
    val levelName = when (template.level) {
        TemplateLevel.BEGINNER     -> stringResource(R.string.level_beginner)
        TemplateLevel.INTERMEDIATE -> stringResource(R.string.level_intermediate)
        TemplateLevel.ADVANCED     -> stringResource(R.string.level_advanced)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(cs.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = levelColor.copy(alpha = 0.12f)) {
                            Text(
                                levelName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = levelColor
                            )
                        }
                        Text(
                            "${template.exercises.size} 個動作",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = cs.error.copy(alpha = 0.7f))
                }
            }

            if (template.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onUse,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.use_template), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.edit), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 儲存課表的 Dialog */
@Composable
private fun SaveTemplateDialog(
    templateName: String,
    onTemplateNameChange: (String) -> Unit,
    selectedItems: List<String>,
    selectedSets: Map<String, Int>,
    selectedWeights: Map<String, Float>,
    defaultSets: Int,
    defaultWeight: Float,
    part: String,
    localTemplateRepo: LocalTemplateRepository,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    var selectedLevel by remember { mutableStateOf(TemplateLevel.BEGINNER) }
    var description by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Save, null, tint = cs.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.save_as_template),
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = onTemplateNameChange,
                    label = { Text(stringResource(R.string.template_name)) },
                    placeholder = { Text(stringResource(R.string.template_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = cs.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    placeholder = { Text("簡述這個課表的訓練目標...") },
                    minLines = 2, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )

                Text(stringResource(R.string.level), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateLevel.entries.forEach { level ->
                        val isSel = selectedLevel == level
                        val lc = when (level) {
                            TemplateLevel.BEGINNER     -> Color(0xFF4CAF50)
                            TemplateLevel.INTERMEDIATE -> Color(0xFFFF9800)
                            TemplateLevel.ADVANCED     -> Color(0xFFF44336)
                        }
                        val ln = when (level) {
                            TemplateLevel.BEGINNER     -> stringResource(R.string.level_beginner)
                            TemplateLevel.INTERMEDIATE -> stringResource(R.string.level_intermediate)
                            TemplateLevel.ADVANCED     -> stringResource(R.string.level_advanced)
                        }
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedLevel = level },
                            label = { Text(ln, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSel) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = lc.copy(alpha = 0.2f),
                                selectedLabelColor = lc,
                                selectedLeadingIconColor = lc
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedItems.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("已選擇 ${selectedItems.size} 個動作", style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedItems.take(3).joinToString("、") + if (selectedItems.size > 3) " ..." else "",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
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
                        val tpl = SimpleWorkoutTemplate(
                            name = templateName,
                            bodyPart = part,
                            exercises = selectedItems.map { name ->
                                SimpleExerciseItem(
                                    name = name,
                                    sets = selectedSets[name] ?: defaultSets,
                                    weight = selectedWeights[name] ?: defaultWeight
                                )
                            },
                            level = selectedLevel,
                            description = description
                        )
                        scope.launch {
                            localTemplateRepo.addTemplate(tpl)
                            snackbarHostState.showSnackbar(context.getString(R.string.template_saved))
                        }
                        onDismiss()
                    }
                },
                enabled = templateName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant)
            }
        }
    )
}





