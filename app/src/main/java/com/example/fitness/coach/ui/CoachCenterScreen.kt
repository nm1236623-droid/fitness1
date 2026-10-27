package com.example.fitness.coach.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.example.fitness.R
import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.coach.UserRole
import com.example.fitness.coach.cloud.CloudSyncUseCase
import com.example.fitness.coach.cloud.CoachTrainee
import com.example.fitness.coach.cloud.CoachTraineeDirectoryRepository
import com.example.fitness.coach.cloud.CustomExercise
import com.example.fitness.coach.cloud.DietSuggestion
import com.example.fitness.coach.local.CoachLocalPlanRepository
import com.example.fitness.data.TrainingPlan
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.user.UserRoleProfileRepository
import com.google.firebase.auth.FirebaseAuth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun CoachCenterScreen(
    authRepository: CoachAuthRepository,
    trainingPlanRepository: TrainingPlanRepository,
    onBack: () -> Unit,
    onNavigateToCreatePlan: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val roleRepo = remember { UserRoleProfileRepository() }
    var myRole by remember { mutableStateOf<UserRole?>(null) }
    var coachNickname by rememberSaveable { mutableStateOf("") }
    var isEditingNickname by rememberSaveable { mutableStateOf(false) }
    var nicknameDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        myRole = roleRepo.getMyRole().getOrDefault(UserRole.TRAINEE)
        coachNickname = roleRepo.getMyNickname().getOrNull().orEmpty()
        nicknameDraft = coachNickname
    }

    // Loading
    if (myRole == null) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple))), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = cs.primary)
        }
        return
    }

    // Non-coach
    if (myRole != UserRole.COACH) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple))).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(cs.primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.People, null, tint = cs.primary.copy(0.4f), modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.trainee_account_hint), color = cs.onBackground.copy(0.6f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = cs.primary.copy(0.15f), contentColor = cs.primary), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                    Text(stringResource(R.string.back), fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // ════════════ Coach data ════════════
    val appCtx = LocalContext.current.applicationContext
    val coachLocalRepo = remember(appCtx) { CoachLocalPlanRepository(appCtx) }
    val plans by coachLocalRepo.plans.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cloudSync = remember { CloudSyncUseCase(authRepository, trainingPlanRepository) }

    val traineeDirectoryRepo = remember { CoachTraineeDirectoryRepository() }
    val trainees = remember { mutableStateListOf<CoachTrainee>() }
    var traineeIdInput by remember { mutableStateOf("") }
    var traineeNameInput by remember { mutableStateOf("") }
    var traineeToDelete by remember { mutableStateOf<CoachTrainee?>(null) }

    val customExercises by cloudSync.customExercisesFlow().collectAsState(initial = emptyList())
    var customExName by remember { mutableStateOf("") }
    var customExDesc by remember { mutableStateOf("") }
    var customExBodyPart by remember { mutableStateOf("chest") }
    var customExEquipment by remember { mutableStateOf("") }
    var customExBodyPartExpanded by remember { mutableStateOf(false) }
    val bodyPartOptions = remember { listOf("chest" to "胸", "back" to "背", "legs" to "腿", "arms" to "手臂", "shoulders" to "肩", "abs" to "腹") }

    var dietTitle by remember { mutableStateOf("") }
    var dietContent by remember { mutableStateOf("") }
    var dietTraineeExpanded by remember { mutableStateOf(false) }
    var dietTraineeTarget by remember { mutableStateOf<CoachTrainee?>(null) }

    val traineeNicknameCache = remember { mutableStateMapOf<String, String>() }
    val traineeNameById = remember(trainees) { trainees.associate { it.traineeId to it.displayName.trim() } }

    // Publish plan to trainee
    var showPublishDialog by remember { mutableStateOf(false) }
    var publishPlanTarget by remember { mutableStateOf<TrainingPlan?>(null) }
    var publishTraineeExpanded by remember { mutableStateOf(false) }
    var publishTraineeTarget by remember { mutableStateOf<CoachTrainee?>(null) }

    val completions by cloudSync.coachCompletionsFlow().collectAsState(initial = emptyList<com.example.fitness.coach.cloud.CoachPlanCompletion>())
    val selectedTraineeId: String? = null
    val filteredCompletions = remember(completions) { completions }

    LaunchedEffect(filteredCompletions) {
        filteredCompletions.asSequence().sortedByDescending { it.completedAt }.take(50).map { it.traineeId }
            .filter { it.isNotBlank() }.distinct().toList().forEach { uid ->
                if (!traineeNicknameCache.containsKey(uid)) traineeNicknameCache[uid] = roleRepo.getNicknameByUid(uid).getOrNull().orEmpty()
            }
    }

    var coachId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        cloudSync.startListeningCoachCompletions()
        cloudSync.startListeningCustomExercises()
        coachId = FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId()
        traineeDirectoryRepo.listTrainees(coachId).onSuccess { list -> trainees.clear(); trainees.addAll(list) }
    }

    val dtf = remember { DateTimeFormatter.ofPattern("MM/dd HH:mm") }

    fun refreshTrainees() {
        scope.launch {
            val cid = coachId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId() }
            traineeDirectoryRepo.listTrainees(cid)
                .onSuccess { list -> trainees.clear(); trainees.addAll(list) }
                .onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.read_trainee_failed, e.message ?: "")) }
        }
    }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = cs.onBackground,
        unfocusedTextColor = cs.onBackground,
        focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.25f),
        unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.12f),
        cursorColor = cs.primary,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.primary.copy(alpha = 0.45f),
        focusedLabelColor = cs.primary,
        unfocusedLabelColor = cs.onBackground.copy(alpha = 0.8f),
    )
    val tfShape = RoundedCornerShape(14.dp)

    // ═══ Delete trainee dialog ═══
    traineeToDelete?.let { trainee ->
        AlertDialog(
            onDismissRequest = { traineeToDelete = null }, containerColor = TechColors.DarkBlue, shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.delete_trainee), color = Color(0xFFFF4757), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_trainee_confirm, trainee.displayName.ifBlank { trainee.traineeId }), color = cs.onBackground) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val cid = coachId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId() }
                        traineeDirectoryRepo.deleteTrainee(cid, trainee.traineeId)
                            .onSuccess { traineeToDelete = null; refreshTrainees(); snackbarHostState.showSnackbar(ctx.getString(R.string.trainee_deleted)) }
                            .onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.delete_trainee_failed, e.message ?: "")) }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757).copy(0.2f), contentColor = Color(0xFFFF4757)), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                    Text(stringResource(R.string.delete_trainee), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { traineeToDelete = null }) { Text(stringResource(R.string.cancel), color = cs.onBackground.copy(0.5f)) } }
        )
    }

    // ═══ Publish plan dialog ═══
    if (showPublishDialog && publishPlanTarget != null) {
        AlertDialog(
            onDismissRequest = { showPublishDialog = false }, containerColor = TechColors.DarkBlue, shape = RoundedCornerShape(24.dp),
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("📤", fontSize = 22.sp); Spacer(Modifier.width(10.dp)); Text("發送課表給學員", color = cs.primary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(0.08f)).padding(12.dp)) {
                        Column { Text("📋 ${publishPlanTarget!!.name}", color = cs.onBackground, fontWeight = FontWeight.Bold); Text("${publishPlanTarget!!.exercises.size} 個動作", color = cs.onBackground.copy(0.5f), style = MaterialTheme.typography.bodySmall) }
                    }
                    if (trainees.isNotEmpty()) {
                        ExposedDropdownMenuBox(publishTraineeExpanded, { publishTraineeExpanded = it }) {
                            OutlinedTextField(publishTraineeTarget?.let { it.displayName.ifBlank { it.traineeId } } ?: "選擇學員", {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true), label = { Text("學員") }, shape = tfShape, colors = tfColors, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(publishTraineeExpanded) })
                            ExposedDropdownMenu(publishTraineeExpanded, { publishTraineeExpanded = false }, containerColor = TechColors.DarkBlue) {
                                trainees.forEach { t -> DropdownMenuItem(text = { Text(t.displayName.ifBlank { t.traineeId }, color = cs.onBackground) }, onClick = { publishTraineeTarget = t; publishTraineeExpanded = false }) }
                            }
                        }
                    } else { Text("尚未加入學員", color = cs.onBackground.copy(0.5f)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = publishTraineeTarget ?: return@Button; val plan = publishPlanTarget ?: return@Button
                    scope.launch {
                        cloudSync.publishCoachLocalPlanToTrainee(coachId, target.traineeId, plan)
                            .onSuccess { showPublishDialog = false; publishTraineeTarget = null; snackbarHostState.showSnackbar("已發送「${plan.name}」給 ${target.displayName.ifBlank { target.traineeId }}") }
                            .onFailure { e -> snackbarHostState.showSnackbar("發送失敗：${e.message}") }
                    }
                }, enabled = publishTraineeTarget != null, colors = ButtonDefaults.buttonColors(containerColor = cs.primary.copy(0.2f), contentColor = cs.primary), elevation = ButtonDefaults.buttonElevation(0.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("發送", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showPublishDialog = false }) { Text("取消", color = cs.onBackground.copy(0.5f)) } }
        )
    }

    // ═══════════════ Main UI ═══════════════
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple))).padding(innerPadding).widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(0.1f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = cs.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.coach_center), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                            if (coachNickname.isNotBlank()) Text(coachNickname, style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(0.5f))
                        }
                    }
                }
            }

            // Coach ID
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CoachSectionHeader("🆔", "教練 ID")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(coachId, style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primary.copy(0.1f)).clickable {
                                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("coachId", coachId))
                                scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.copied_coach_id)) }
                            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ContentCopy, null, tint = cs.primary, modifier = Modifier.size(16.dp)) }
                        }
                        if (isEditingNickname) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(nicknameDraft, { nicknameDraft = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.coach_nickname)) }, singleLine = true, colors = tfColors, shape = tfShape)
                                TextButton(onClick = { scope.launch { roleRepo.updateMyNickname(nicknameDraft.trim()).onSuccess { coachNickname = nicknameDraft.trim(); isEditingNickname = false; snackbarHostState.showSnackbar(ctx.getString(R.string.nickname_saved)) }.onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.save_failed_msg_simple, e.message ?: "")) } } }, enabled = nicknameDraft.trim().isNotEmpty()) { Text(stringResource(R.string.save), color = cs.primary) }
                                TextButton(onClick = { nicknameDraft = coachNickname; isEditingNickname = false }) { Text(stringResource(R.string.cancel), color = cs.onBackground.copy(0.5f)) }
                            }
                        } else {
                            TextButton(onClick = { nicknameDraft = coachNickname; isEditingNickname = true }) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = cs.primary); Spacer(Modifier.width(6.dp)); Text(if (coachNickname.isBlank()) stringResource(R.string.set_coach_nickname) else stringResource(R.string.edit_coach_nickname), color = cs.primary) }
                        }
                    }
                }
            }

            // Trainee Management
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoachSectionHeader("👥", stringResource(R.string.trainee_list)); Spacer(Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primary.copy(0.1f)).clickable { refreshTrainees() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Refresh, null, tint = cs.primary, modifier = Modifier.size(16.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(traineeIdInput, { traineeIdInput = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.id_label)) }, singleLine = true, colors = tfColors, shape = tfShape, textStyle = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(traineeNameInput, { traineeNameInput = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.nickname_label)) }, singleLine = true, colors = tfColors, shape = tfShape, textStyle = MaterialTheme.typography.bodySmall)
                            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(0.15f)).clickable {
                                val tid = traineeIdInput.trim(); if (tid.isBlank()) return@clickable
                                scope.launch {
                                    val cid = coachId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId() }
                                    traineeDirectoryRepo.upsertTrainee(cid, tid, traineeNameInput.trim())
                                        .onSuccess { traineeIdInput = ""; traineeNameInput = ""; refreshTrainees(); snackbarHostState.showSnackbar(ctx.getString(R.string.trainee_added)) }
                                        .onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.add_trainee_failed, e.message ?: "")) }
                                }
                            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.PersonAdd, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
                        }
                        if (trainees.isEmpty()) { Text(stringResource(R.string.no_trainees_yet), color = cs.onBackground.copy(0.4f), style = MaterialTheme.typography.bodySmall) }
                        else {
                            trainees.forEach { t ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(0.05f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primary.copy(0.12f)), contentAlignment = Alignment.Center) { Text(t.displayName.take(1).uppercase().ifBlank { "?" }, color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) { Text(t.displayName.ifBlank { "—" }, color = cs.onBackground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.traineeId, color = cs.onBackground.copy(0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFF4757).copy(0.1f)).clickable { traineeToDelete = t }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4757).copy(0.7f), modifier = Modifier.size(14.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // Coach Plans
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoachSectionHeader("📋", stringResource(R.string.coach_schedule))
                        if (plans.isNotEmpty()) {
                            plans.take(15).forEach { plan ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(0.05f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(plan.name, color = cs.onBackground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(stringResource(R.string.exercises_count_format, plan.exercises.size), color = cs.onBackground.copy(0.5f), style = MaterialTheme.typography.labelSmall) }
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primary.copy(0.12f)).clickable { publishPlanTarget = plan; publishTraineeTarget = null; showPublishDialog = true }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = cs.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else { Text(stringResource(R.string.no_plans_yet), color = cs.onBackground.copy(0.4f), style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = onNavigateToCreatePlan, modifier = Modifier.fillMaxWidth().height(48.dp).neonGlowBorder(cornerRadius = 14.dp, glowColor = cs.primary), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = cs.primary.copy(0.15f), contentColor = cs.primary), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.create_plan), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Custom Exercises
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoachSectionHeader("💪", stringResource(R.string.custom_exercises))
                        OutlinedTextField(customExName, { customExName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_exercise_name)) }, singleLine = true, colors = tfColors, shape = tfShape)
                        OutlinedTextField(customExDesc, { customExDesc = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_exercise_desc)) }, singleLine = true, colors = tfColors, shape = tfShape)
                        ExposedDropdownMenuBox(customExBodyPartExpanded, { customExBodyPartExpanded = it }) {
                            OutlinedTextField(bodyPartOptions.firstOrNull { it.first == customExBodyPart }?.second ?: "胸", {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true), label = { Text(stringResource(R.string.custom_exercise_bodypart)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(customExBodyPartExpanded) }, colors = tfColors, shape = tfShape)
                            ExposedDropdownMenu(customExBodyPartExpanded, { customExBodyPartExpanded = false }, containerColor = TechColors.DarkBlue) {
                                bodyPartOptions.forEach { (key, label) -> DropdownMenuItem(text = { Text(label, color = cs.onBackground) }, onClick = { customExBodyPart = key; customExBodyPartExpanded = false }) }
                            }
                        }
                        OutlinedTextField(customExEquipment, { customExEquipment = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_exercise_equipment)) }, singleLine = true, colors = tfColors, shape = tfShape)
                        Button(onClick = {
                            val name = customExName.trim(); if (name.isBlank()) { scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.custom_exercise_name_required)) }; return@Button }
                            scope.launch { cloudSync.addCustomExercise(CustomExercise(UUID.randomUUID().toString(), name, customExDesc.trim(), customExBodyPart, customExEquipment.trim()))
                                .onSuccess { customExName = ""; customExDesc = ""; customExEquipment = ""; snackbarHostState.showSnackbar(ctx.getString(R.string.custom_exercise_added)) }
                                .onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.custom_exercise_add_failed, e.message ?: "")) } }
                        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = cs.primary.copy(0.15f), contentColor = cs.primary), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_custom_exercise), fontWeight = FontWeight.Bold)
                        }
                        if (customExercises.isNotEmpty()) {
                            HorizontalDivider(color = cs.primary.copy(0.1f))
                            customExercises.forEach { ex ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(0.05f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(ex.name, color = cs.onBackground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(buildString { append(bodyPartOptions.firstOrNull { it.first == ex.bodyPart }?.second ?: ex.bodyPart); if (ex.equipment.isNotBlank()) append(" · ${ex.equipment}") }, color = cs.onBackground.copy(0.4f), style = MaterialTheme.typography.labelSmall) }
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFF4757).copy(0.1f)).clickable { scope.launch { cloudSync.deleteCustomExercise(ex.id).onSuccess { snackbarHostState.showSnackbar(ctx.getString(R.string.custom_exercise_deleted)) } } }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4757).copy(0.6f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        } else { Text(stringResource(R.string.no_custom_exercises), color = cs.onBackground.copy(0.4f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // Diet Suggestions
            item {
                Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoachSectionHeader("🥗", stringResource(R.string.diet_suggestion))
                        OutlinedTextField(dietTitle, { dietTitle = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.diet_suggestion_title)) }, singleLine = true, colors = tfColors, shape = tfShape)
                        OutlinedTextField(dietContent, { dietContent = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text(stringResource(R.string.diet_suggestion_content)) }, colors = tfColors, shape = tfShape, maxLines = 8)
                        if (trainees.isNotEmpty()) {
                            ExposedDropdownMenuBox(dietTraineeExpanded, { dietTraineeExpanded = it }) {
                                OutlinedTextField(dietTraineeTarget?.let { it.displayName.ifBlank { it.traineeId } } ?: stringResource(R.string.select_trainee_for_diet), {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true), label = { Text(stringResource(R.string.trainee)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dietTraineeExpanded) }, colors = tfColors, shape = tfShape)
                                ExposedDropdownMenu(dietTraineeExpanded, { dietTraineeExpanded = false }, containerColor = TechColors.DarkBlue) {
                                    trainees.forEach { t -> DropdownMenuItem(text = { Text(t.displayName.ifBlank { t.traineeId }, color = cs.onBackground) }, onClick = { dietTraineeTarget = t; dietTraineeExpanded = false }) }
                                }
                            }
                        }
                        Button(onClick = {
                            val title = dietTitle.trim(); val content = dietContent.trim(); val target = dietTraineeTarget
                            when {
                                title.isBlank() -> scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.diet_title_required)) }
                                content.isBlank() -> scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.diet_content_required)) }
                                target == null -> scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.select_trainee_for_diet)) }
                                else -> scope.launch { cloudSync.publishDietSuggestion(target.traineeId, DietSuggestion(UUID.randomUUID().toString(), coachId, target.traineeId, title, content)).onSuccess { dietTitle = ""; dietContent = ""; snackbarHostState.showSnackbar(ctx.getString(R.string.diet_suggestion_sent, target.displayName.ifBlank { target.traineeId })) }.onFailure { e -> snackbarHostState.showSnackbar(ctx.getString(R.string.diet_suggestion_send_failed, e.message ?: "")) } }
                            }
                        }, enabled = trainees.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = TechColors.NeonGreen.copy(0.15f), contentColor = TechColors.NeonGreen), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.send_diet_suggestion), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Completion Reports
            if (filteredCompletions.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CoachSectionHeader("✅", stringResource(R.string.trainee_completion_report))
                            val sorted = remember(filteredCompletions) { filteredCompletions.sortedByDescending { it.completedAt }.take(50) }
                            sorted.forEachIndexed { idx, c ->
                                if (idx > 0) HorizontalDivider(color = cs.primary.copy(0.08f))
                                val timeText = c.completedAt.atZone(ZoneId.systemDefault()).format(dtf)
                                val serverNick = traineeNicknameCache[c.traineeId].orEmpty().trim()
                                val localNick = traineeNameById[c.traineeId].orEmpty().trim()
                                val displayName = serverNick.ifBlank { localNick.ifBlank { stringResource(R.string.unnamed) } }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(TechColors.NeonGreen.copy(0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.TaskAlt, null, tint = TechColors.NeonGreen, modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.trainee_label, displayName), color = cs.onBackground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${c.planName} · $timeText", color = cs.onBackground.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CoachSectionHeader(emoji: String, title: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onBackground,
            fontSize = 17.sp
        )
    }
}
