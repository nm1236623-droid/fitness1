package com.example.fitness.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import com.example.fitness.BuildConfig
import com.example.fitness.R
import com.example.fitness.ai.GeminiTrainingPlanParser
import com.example.fitness.ai.UserTrainingContextRepository
import com.example.fitness.data.TrainingPlan
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.data.WorkoutTemplate
import com.example.fitness.data.WorkoutTemplateRepository
import com.example.fitness.data.toTrainingPlan
import com.example.fitness.data.toWorkoutTemplate
import com.example.fitness.inbody.FirebaseInBodyRepository
import com.example.fitness.network.GeminiClient
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.launch
import java.time.ZoneId

// 定義目標選項
private enum class PlanGoal(@androidx.annotation.StringRes val labelRes: Int) {
    BULK(R.string.goal_bulk),
    CUT(R.string.goal_cut),
}

// 定義程度選項
private enum class ExperienceLevel(@androidx.annotation.StringRes val labelRes: Int) {
    BEGINNER(R.string.level_beginner),
    INTERMEDIATE(R.string.level_intermediate),
    ADVANCED(R.string.level_advanced)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutCreateScreen(
    repository: TrainingPlanRepository,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val userCtxRepo = remember { UserTrainingContextRepository() }
    val userCtx by userCtxRepo.observeMyContext().collectAsState(initial = null)

    // 監聽我的模板列表
    val myTemplates by WorkoutTemplateRepository.observeMyTemplates().collectAsState(initial = emptyList())

    val profile = userCtx?.profile
    val latestInBody = userCtx?.latestInBody

    val weightKg = profile?.weightKg ?: 70f
    val heightCm = profile?.heightCm ?: 170f

    // 年齡：從 profile 自動帶入（允許空值時顯示空字串）
    var ageYearsInput by remember(profile?.age) {
        mutableStateOf(profile?.age?.takeIf { it > 0 }?.toString().orEmpty())
    }

    val muscleMassKg = latestInBody?.muscleMassKg
    val bodyFatPercent = latestInBody?.bodyFatPercent
    val inBodyWeightKg = latestInBody?.weightKg

    // State Variables
    var goal by remember { mutableStateOf(PlanGoal.BULK) }
    var daysPerWeek by remember { mutableStateOf(4) }
    var experience by remember { mutableStateOf(ExperienceLevel.BEGINNER) }

    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var previewPlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showMyTemplatesDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<WorkoutTemplate?>(null) }

    val scope = rememberCoroutineScope()

    // Get localized labels for prompt
    val goalLabel = stringResource(goal.labelRes)
    val experienceLabel = stringResource(experience.labelRes)

    fun buildPrompt(): String {
        val zoneId = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zoneId)
        val effectiveWeightKg = inBodyWeightKg ?: weightKg

        return buildString {
            appendLine("你是健身教練，請依照使用者資料『產生一份訓練計畫』。")
            appendLine("目標：$goalLabel")
            appendLine("每週訓練天數：$daysPerWeek 天")
            appendLine("使用者訓練程度：$experienceLabel")
            appendLine("使用者資料：")
            // ★ 修改 2: 直接使用字串，若為空則在按鈕點擊時會被攔截，這裡不用擔心
            appendLine("- 年齡：$ageYearsInput 歲")
            appendLine("- 身高：${"%.1f".format(heightCm)} cm")
            appendLine("- 體重：${"%.1f".format(effectiveWeightKg)} kg")
            if (muscleMassKg != null) appendLine("- 肌肉量：${"%.1f".format(muscleMassKg)} kg")
            if (bodyFatPercent != null) appendLine("- 體脂：${"%.1f".format(bodyFatPercent)} %")
            appendLine("- 目前日期：${now.toLocalDate()}")
            appendLine()
            appendLine("請只回傳 JSON（不要任何額外文字、不要 code fence）。")
            appendLine()
            appendLine("JSON 格式如下（days 的長度請等於每週訓練天數）：")
            appendLine("{")
            appendLine("  \"name\": \"計畫名稱\",")
            appendLine("  \"days\": [")
            appendLine("    {")
            appendLine("      \"day\": 1,")
            appendLine("      \"focus\": \"胸+三頭\",")
            appendLine("      \"exercises\": [")
            appendLine("        { \"name\": \"動作名稱\", \"sets\": 4, \"reps\": 10, \"weightKg\": 20 }")
            appendLine("      ]")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
            appendLine()
            appendLine("規則：")
            appendLine("- days 請從 day=1 開始，依序遞增")
            appendLine("- 每一天至少 5 個動作；整份計畫總動作數至少 12")
            appendLine("- sets/reps 需為整數")
            appendLine("- weightKg 可省略或為 0（若你無法判斷重量）")
            appendLine("- 動作名稱請用繁體中文（可附英文別名但以中文為主）")
        }
    }

    // Global Background
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TechColors.DarkBlue, TechColors.DeepPurple)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            val cs = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 20.dp)
                    .padding(12.dp)
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
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ai_training_plan),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                        Text(
                            stringResource(R.string.ai_generate_plan),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    // 我的模板按鈕
                    if (myTemplates.isNotEmpty()) {
                        IconButton(
                            onClick = { showMyTemplatesDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    cs.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Badge(
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary
                            ) {
                                Text("${myTemplates.size}", style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.my_templates),
                                tint = cs.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Configuration Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
                    .padding(20.dp)
            ) {
                Column {
                    Text(stringResource(R.string.set_target), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    Spacer(Modifier.height(12.dp))

                    // Goal Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlanGoal.entries.forEach { g ->
                            NeonToggleOption(
                                text = stringResource(g.labelRes),
                                isSelected = goal == g,
                                onClick = { goal = g },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(R.string.user_data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    Spacer(Modifier.height(8.dp))

                    // Stats Row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NeonStatChip(stringResource(R.string.height), "${heightCm.toInt()}cm")
                        NeonStatChip(stringResource(R.string.weight), "${(inBodyWeightKg ?: weightKg).toInt()}kg")
                        bodyFatPercent?.let { NeonStatChip(stringResource(R.string.body_fat), "${it.toInt()}%") }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ★ 修改 3: 更新輸入框邏輯，允許空值輸入
                        NeonOutlinedTextField(
                            value = ageYearsInput,
                            onValueChange = { v ->
                                // 只允許數字，長度限制 3 位
                                if (v.all { it.isDigit() } && v.length <= 3) {
                                    ageYearsInput = v
                                }
                            },
                            label = stringResource(R.string.age),
                            modifier = Modifier.weight(1f)
                        )

                        NeonOutlinedTextField(
                            value = daysPerWeek.toString(),
                            onValueChange = { v ->
                                val n = v.filter { it.isDigit() }.toIntOrNull()
                                if (n != null) daysPerWeek = n.coerceIn(1, 6)
                            },
                            label = stringResource(R.string.days_per_week),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // 訓練程度
                    Text(stringResource(R.string.training_level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExperienceLevel.entries.forEach { level ->
                            NeonToggleOption(
                                text = stringResource(level.labelRes),
                                isSelected = experience == level,
                                onClick = { experience = level },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Error Message
                    errorText?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(20.dp))

                    val apiKeyNotSetText = stringResource(R.string.api_key_not_set)
                    val pleaseEnterAgeText = stringResource(R.string.please_enter_age)
                    val aiThinkingText = stringResource(R.string.ai_thinking)
                    val generatePlanText = stringResource(R.string.generate_plan)
                    val aiServiceFailedText = stringResource(R.string.ai_service_failed)
                    val generateFailedText = stringResource(R.string.generate_failed)

                    // Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                                    errorText = apiKeyNotSetText
                                    return@Button
                                }
                                // ★ 修改 4: 增加防呆檢查，確保年齡有輸入
                                if (ageYearsInput.isBlank()) {
                                    errorText = pleaseEnterAgeText
                                    return@Button
                                }

                                loading = true
                                errorText = null
                                previewPlan = null

                                scope.launch {
                                    try {
                                        val prompt = buildPrompt()
                                        val res = GeminiClient.ask(prompt)
                                        if (res.isSuccess) {
                                            val raw = res.getOrNull().orEmpty()
                                            val parsed = GeminiTrainingPlanParser.parseJson(raw)
                                            previewPlan = GeminiTrainingPlanParser.toTrainingPlan(parsed)
                                            // 先顯示預覽，不立即跳出儲存對話框
                                            // showSaveDialog = true  // 移除：讓用戶先看預覽
                                        } else {
                                            errorText = "$aiServiceFailedText: ${res.exceptionOrNull()?.message}"
                                        }
                                    } catch (e: Exception) {
                                        errorText = "$generateFailedText: ${e.message}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .neonGlowBorder(cornerRadius = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary.copy(alpha = 0.2f),
                                contentColor = cs.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = cs.primary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(aiThinkingText)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(generatePlanText, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { previewPlan = null; errorText = null },
                            enabled = !loading,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onBackground.copy(alpha = 0.7f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cs.onBackground.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            }


            // Preview Section
            previewPlan?.let { plan ->
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preview_plan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 24.dp)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.primary)
                        Spacer(Modifier.height(12.dp))

                        val parsedDays = remember(plan) { GeminiTrainingPlanParser.extractDaysOrNull(plan) }

                        if (!parsedDays.isNullOrEmpty()) {
                            parsedDays.forEach { day ->
                                Surface(
                                    color = cs.onBackground.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Day ${day.day} ${day.focus?.let { "• $it" } ?: ""}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onBackground
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        day.exercises.forEach { e ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text("• ", color = cs.primary)
                                                Column {
                                                    Text(e.name, color = cs.onBackground.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        "${e.sets}組 x ${e.reps}下 ${e.weight?.let { "@ ${it.toInt()}kg" } ?: ""}",
                                                        color = cs.onBackground.copy(alpha = 0.5f),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            plan.exercises.take(12).forEach { e ->
                                Text("• ${e.name}", color = cs.onBackground.copy(alpha = 0.8f))
                            }
                        }

                        // 在預覽區域底部加入儲存按鈕
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 重新生成按鈕
                            OutlinedButton(
                                onClick = { previewPlan = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = cs.onBackground.copy(alpha = 0.7f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cs.onBackground.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold)
                            }

                            // 儲存按鈕
                            Button(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .neonGlowBorder(cornerRadius = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.primary.copy(alpha = 0.3f),
                                    contentColor = cs.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.save_plan), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Save Dialog
    if (showSaveDialog && previewPlan != null) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = cs.surface.copy(alpha = 0.95f),
            titleContentColor = cs.primary,
            textContentColor = cs.onBackground,
            title = { Text(stringResource(R.string.save_training_plan)) },
            text = {
                Column {
                    Text(stringResource(R.string.save_plan_confirm, previewPlan?.name ?: ""))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.save_as_template_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onBackground.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 另存為模板
                    OutlinedButton(
                        onClick = {
                            showSaveDialog = false
                            showTemplateDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = cs.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_as_template))
                    }

                    // 直接儲存
                    Button(
                        onClick = {
                            previewPlan?.let { repository.addPlan(it) }
                            showSaveDialog = false
                            Toast.makeText(context, context.getString(R.string.plan_saved), Toast.LENGTH_SHORT).show()
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary
                        )
                    ) { Text(stringResource(R.string.save)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onBackground.copy(alpha = 0.7f))
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Save as Template Dialog
    if (showTemplateDialog && previewPlan != null) {
        var templateName by remember { mutableStateOf(previewPlan?.name ?: "") }
        var templateDescription by remember { mutableStateOf("") }
        var saveLoading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            containerColor = cs.surface.copy(alpha = 0.95f),
            titleContentColor = cs.primary,
            textContentColor = cs.onBackground,
            title = { Text(stringResource(R.string.save_as_template_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.template_save_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onBackground.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text(stringResource(R.string.template_name)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = cs.onBackground,
                            unfocusedTextColor = cs.onBackground,
                            focusedBorderColor = cs.primary,
                            unfocusedBorderColor = cs.onBackground.copy(alpha = 0.3f),
                            focusedLabelColor = cs.primary,
                            unfocusedLabelColor = cs.onBackground.copy(alpha = 0.7f),
                            cursorColor = cs.primary
                        )
                    )

                    OutlinedTextField(
                        value = templateDescription,
                        onValueChange = { templateDescription = it },
                        label = { Text(stringResource(R.string.template_description)) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = cs.onBackground,
                            unfocusedTextColor = cs.onBackground,
                            focusedBorderColor = cs.primary,
                            unfocusedBorderColor = cs.onBackground.copy(alpha = 0.3f),
                            focusedLabelColor = cs.primary,
                            unfocusedLabelColor = cs.onBackground.copy(alpha = 0.7f),
                            cursorColor = cs.primary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (templateName.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.please_enter_template_name), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        saveLoading = true
                        scope.launch {
                            try {
                                val template = previewPlan!!.toWorkoutTemplate(
                                    name = templateName,
                                    description = templateDescription,
                                    goal = goalLabel,
                                    daysPerWeek = daysPerWeek,
                                    experienceLevel = experienceLabel
                                )

                                val result = WorkoutTemplateRepository.saveTemplate(template)
                                if (result.isSuccess) {
                                    // 同時儲存為訓練計畫
                                    repository.addPlan(previewPlan!!)

                                    Toast.makeText(context, context.getString(R.string.template_saved), Toast.LENGTH_SHORT).show()
                                    showTemplateDialog = false
                                    onDone()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.save_failed) + "：${result.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.save_failed) + "：${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                saveLoading = false
                            }
                        }
                    },
                    enabled = !saveLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    )
                ) {
                    if (saveLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = cs.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.save_template))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTemplateDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onBackground.copy(alpha = 0.7f))
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // My Templates Dialog
    if (showMyTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showMyTemplatesDialog = false },
            containerColor = cs.surface.copy(alpha = 0.95f),
            titleContentColor = cs.primary,
            textContentColor = cs.onBackground,
            title = { Text(stringResource(R.string.my_templates)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    if (myTemplates.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_templates),
                                color = cs.onBackground.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(myTemplates, key = { it.id }) { template ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 套用模板
                                        scope.launch {
                                            val plan = template.toTrainingPlan()
                                            previewPlan = plan
                                            goal = when (template.goal.lowercase()) {
                                                "增肌", "bulk", "muscle gain" -> PlanGoal.BULK
                                                "減脂", "减脂", "cut", "fat loss" -> PlanGoal.CUT
                                                else -> PlanGoal.BULK
                                            }
                                            daysPerWeek = template.daysPerWeek
                                            experience = when (template.experienceLevel.lowercase()) {
                                                "新手", "beginner", "初心者", "초보" -> ExperienceLevel.BEGINNER
                                                "中階", "中阶", "intermediate", "中級者", "중급" -> ExperienceLevel.INTERMEDIATE
                                                "高階", "高阶", "advanced", "上級者", "고급" -> ExperienceLevel.ADVANCED
                                                else -> ExperienceLevel.INTERMEDIATE
                                            }
                                            showMyTemplatesDialog = false
                                            Toast.makeText(context, context.getString(R.string.template_applied), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                color = cs.onBackground.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            template.name,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onBackground
                                        )
                                        if (template.description.isNotBlank()) {
                                            Text(
                                                template.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cs.onBackground.copy(alpha = 0.6f),
                                                maxLines = 2
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                template.goal,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.primary
                                            )
                                            Text(
                                                stringResource(R.string.days_per_week_format, template.daysPerWeek),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onBackground.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                template.experienceLevel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onBackground.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { templateToDelete = template }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = cs.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMyTemplatesDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.primary)
                ) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Delete Template Confirmation
    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            containerColor = cs.surface.copy(alpha = 0.95f),
            title = { Text(stringResource(R.string.delete_template), color = cs.primary) },
            text = { Text(stringResource(R.string.delete_template_confirm, template.name), color = cs.onBackground) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = WorkoutTemplateRepository.deleteTemplate(template.id)
                            if (result.isSuccess) {
                                Toast.makeText(context, context.getString(R.string.template_deleted), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.delete_failed) + "：${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            templateToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = cs.onError
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { templateToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onBackground.copy(alpha = 0.7f))
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ... (Helper Components 保持不變) ...
@Composable
private fun NeonToggleOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) cs.primary.copy(alpha = 0.2f) else cs.onBackground.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (isSelected) cs.primary else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) cs.primary else cs.onBackground.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NeonStatChip(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = cs.primary)
        }
    }
}

@Composable
private fun NeonOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = cs.onBackground,
            unfocusedTextColor = cs.onBackground,
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.primary.copy(alpha = 0.5f),
            focusedLabelColor = cs.primary,
            unfocusedLabelColor = cs.onBackground.copy(alpha = 0.7f),
            cursorColor = cs.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}