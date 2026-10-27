package com.example.fitness.coach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.coach.CoachDisplayNameRepository
import com.example.fitness.coach.cloud.CloudSyncUseCase
import com.example.fitness.coach.cloud.CoachTraineeDirectoryRepository
import com.example.fitness.data.TrainingPlanRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import com.example.fitness.coach.cloud.RemotePlanItem
import com.example.fitness.coach.cloud.DietSuggestion
import com.example.fitness.coach.cloud.CoachCloudRepository
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraineeImportScreen(
    trainingPlanRepository: TrainingPlanRepository,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val clipboardManager = remember(ctx) {
        ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authRepository = remember { CoachAuthRepository(ctx.applicationContext) }
    val cloudSync = remember { CloudSyncUseCase(authRepository, trainingPlanRepository) }

    // NEW: repositories needed to log completion into training history
    val activityRepository = remember(appCtx) { com.example.fitness.activity.ActivityLogRepository(context = appCtx) }
    val trainingRecordRepository = remember { com.example.fitness.data.FirebaseTrainingRecordRepository() }

    val displayNameRepo = remember(appCtx) { CoachDisplayNameRepository(appCtx) }
    val traineeDisplayName by displayNameRepo.traineeDisplayName.collectAsState(initial = "")

    var traineeNameInput by remember { mutableStateOf("") }

    // Remote coach-published plans (do NOT mix with local plans)
    val remoteItems by cloudSync.remoteItemsFlow().collectAsState(initial = emptyList())

    var coachIdInput by remember { mutableStateOf("") }

    var traineeId by remember { mutableStateOf<String?>(null) }
    val joinedCoaches = remember { mutableStateListOf<String>() }
    val traineeDirectoryRepo = remember { CoachTraineeDirectoryRepository() }

    // CHANGED: avoid mutableStateMapOf (may be missing import/compat)
    // and keep cache as a normal mutable state map.
    var coachNameCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Diet suggestions from coach
    val dietSuggestions = remember { mutableStateListOf<DietSuggestion>() }
    val cloudRepo = remember { CoachCloudRepository() }

    suspend fun refreshCoachNames(ids: List<String>) {
        ids.distinct().filter { it.isNotBlank() }.forEach { id ->
            cloudSync.getCoachDisplayNameById(id)
                .onSuccess { name ->
                    coachNameCache = coachNameCache + (id to (name?.takeIf { it.isNotBlank() } ?: ""))
                }
        }
    }

    suspend fun refreshMembershipsAndNames(showToast: Boolean) {
        cloudSync.getJoinedCoachIds()
            .onSuccess { ids ->
                joinedCoaches.clear(); joinedCoaches.addAll(ids.sorted())
                refreshCoachNames(ids)
                if (showToast) snackbarHostState.showSnackbar(ctx.getString(R.string.coach_list_updated))
            }
            .onFailure { e ->
                if (showToast) snackbarHostState.showSnackbar(ctx.getString(R.string.update_failed, e.message ?: ctx.getString(R.string.unknown_error)))
            }
    }

    // Auto reconnect sync: when screen opens, read memberships and start listening
    LaunchedEffect(Unit) {
        traineeId = cloudSync.getCurrentTraineeId()

        // Start listening diet suggestions for this trainee
        val tid = traineeId
        if (!tid.isNullOrBlank()) {
            cloudRepo.listenDietSuggestions(tid) { result ->
                result.onSuccess { list ->
                    dietSuggestions.clear()
                    dietSuggestions.addAll(list)
                }
            }
        }

        // Start auto reconnect (listen memberships -> listen plans)
        cloudSync.startAutoReconnectSync()

        // Also load memberships once for UI
        refreshMembershipsAndNames(showToast = false)

        // best-effort: register trainee display name into each coach's directory
        val defaultTraineeName = ctx.getString(R.string.trainee)
        val nameForDirectory = traineeDisplayName.ifBlank { traineeNameInput.trim().ifBlank { defaultTraineeName } }
        if (!tid.isNullOrBlank() && joinedCoaches.isNotEmpty()) {
            joinedCoaches.forEach { coachId ->
                traineeDirectoryRepo.upsertTrainee(
                    coachId = coachId,
                    traineeId = tid,
                    displayName = nameForDirectory,
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).padding(12.dp)) {
        com.example.fitness.ui.ScreenHeader(
            title = stringResource(R.string.trainee_center),
            onBack = onBack
        )

        Spacer(Modifier.height(10.dp))

        // 合併卡片：暱稱資訊 + 已加入教練 + 加入並同步（更小更緊湊）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // A) 狀態列：暱稱 · TraineeId + 複製 + 刷新
                val displayName = traineeDisplayName.ifBlank { traineeNameInput.trim().ifBlank { "學員" } }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tid = traineeId

                    Text(
                        text = if (tid.isNullOrBlank()) stringResource(R.string.loading) else "$displayName · $tid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = {
                            if (tid.isNullOrBlank()) return@IconButton
                            clipboardManager?.setPrimaryClip(
                                android.content.ClipData.newPlainText("traineeId", tid)
                            )
                            scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.copied_trainee_id)) }
                        },
                        enabled = !tid.isNullOrBlank()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_trainee_id))
                    }

                    IconButton(
                        onClick = { scope.launch { refreshMembershipsAndNames(showToast = true) } }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新教練清單")
                    }
                }

                // B) 暱稱列：同一行輸入 + 小按鈕（更省高度）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = traineeNameInput,
                        onValueChange = { traineeNameInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.nickname_label)) },
                        placeholder = { Text(stringResource(R.string.nickname_example)) },
                        singleLine = true
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                displayNameRepo.setTraineeDisplayName(traineeNameInput)

                                // best-effort: update directory entry in all joined coaches
                                val tid = traineeId
                                val defaultName = ctx.getString(R.string.trainee)
                                val nameForDirectory = traineeNameInput.trim().ifBlank { defaultName }
                                if (!tid.isNullOrBlank()) {
                                    joinedCoaches.forEach { coachId ->
                                        traineeDirectoryRepo.upsertTrainee(
                                            coachId = coachId,
                                            traineeId = tid,
                                            displayName = nameForDirectory,
                                        )
                                    }
                                }

                                snackbarHostState.showSnackbar(ctx.getString(R.string.saved))
                            }
                        },
                        modifier = Modifier.height(44.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.save)) }

                    TextButton(
                        onClick = { traineeNameInput = traineeDisplayName.ifBlank { ctx.getString(R.string.trainee) } },
                        modifier = Modifier.height(44.dp)
                    ) { Text(stringResource(R.string.reset_label)) }
                }

                // C) 已加入教練：下拉選單 + 顯示教練名稱（從 Firestore 反查 coachIdDirectory）
                var coachDropdownExpanded by remember { mutableStateOf(false) }
                var selectedCoachId by remember(joinedCoaches) {
                    mutableStateOf(joinedCoaches.firstOrNull() ?: "")
                }

                if (joinedCoaches.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = coachDropdownExpanded,
                        onExpandedChange = { coachDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedName = coachNameCache[selectedCoachId].orEmpty().trim()
                        val selectedLabel = selectedName.ifBlank { stringResource(R.string.no_nickname_set) }

                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
                            label = { Text(stringResource(R.string.coach), style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = coachDropdownExpanded) },
                        )

                        DropdownMenu(
                            expanded = coachDropdownExpanded,
                            onDismissRequest = { coachDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            val noNicknameText = stringResource(R.string.no_nickname_set)
                            joinedCoaches.sorted().forEach { id ->
                                val name = coachNameCache[id].orEmpty().trim()
                                val itemLabel = name.ifBlank { noNicknameText }

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            itemLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        selectedCoachId = id
                                        coachDropdownExpanded = false
                                        coachIdInput = id
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.no_coach_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // D) 加入教練並同步
                OutlinedTextField(
                    value = coachIdInput,
                    onValueChange = { coachIdInput = it },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text(stringResource(R.string.coach_id), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val joinAndSyncText = stringResource(R.string.join_and_sync)
                    Button(
                        onClick = {
                            val coachId = coachIdInput.trim()
                            if (coachId.isBlank()) return@Button
                            scope.launch {
                                val r = cloudSync.joinCoachAndStartListening(coachId)
                                if (r.isSuccess) {
                                    snackbarHostState.showSnackbar(joinAndSyncText)

                                    // refresh list + name cache from Firestore
                                    refreshMembershipsAndNames(showToast = false)

                                    // 若是第一次加入，讓下拉預設選到第一個
                                    if (selectedCoachId.isBlank() && joinedCoaches.isNotEmpty()) {
                                        selectedCoachId = joinedCoaches.first()
                                    }

                                    // best-effort: upsert trainee display name into coach directory
                                    val tid = traineeId
                                    val defaultName = ctx.getString(R.string.trainee)
                                    val nameForDirectory = traineeDisplayName.ifBlank { traineeNameInput.trim().ifBlank { defaultName } }
                                    if (!tid.isNullOrBlank()) {
                                        traineeDirectoryRepo.upsertTrainee(
                                            coachId = coachId,
                                            traineeId = tid,
                                            displayName = nameForDirectory,
                                        )
                                    }
                                } else snackbarHostState.showSnackbar(ctx.getString(R.string.join_failed, r.exceptionOrNull()?.message ?: ""))
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(joinAndSyncText, style = MaterialTheme.typography.bodySmall) }

                    OutlinedButton(
                        onClick = {
                            cloudSync.stopListening()
                            scope.launch { snackbarHostState.showSnackbar(ctx.getString(R.string.sync_stopped)) }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.stop_sync), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // --- Remote coach-published plans ---
        Spacer(Modifier.height(14.dp))
        com.example.fitness.ui.ScreenHeader(
            title = stringResource(R.string.coach_plans_title),
            subtitle = if (remoteItems.isEmpty()) stringResource(R.string.no_plans_received) else stringResource(R.string.plans_received_count, remoteItems.size),
            onBack = null
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(remoteItems.sortedBy { it.plan.name }) { item ->
                val p = item.plan
                RemotePlanExpandableCard(
                    item = item,
                    joinedCoachIds = joinedCoaches.toList(),
                    onMarkCompleted = { coachIdsToNotify ->
                        scope.launch {
                            val targets = coachIdsToNotify.distinct().filter { it.isNotBlank() }
                            if (targets.isEmpty()) {
                                snackbarHostState.showSnackbar(ctx.getString(R.string.no_coach_cannot_report))
                                return@launch
                            }

                            // Calculate estimated calories for this plan
                            fun estimatePlanCalories(plan: com.example.fitness.data.TrainingPlan, userWeightKg: Double = 70.0): Double {
                                val minutesPerSet = 0.5 // 30 seconds per set
                                val met = 6.0 // general resistance training MET
                                var totalMinutes = 0.0
                                plan.exercises.forEach { e ->
                                    val sets = (e.sets ?: 3).toDouble()
                                    totalMinutes += sets * minutesPerSet
                                }
                                return com.example.fitness.calorie.CalorieCalculator.estimateCalories(met, userWeightKg, totalMinutes)
                            }

                            val estimatedCalories = estimatePlanCalories(p)

                            // 1) Record into trainee training history (local + optional Firestore) with calories
                            cloudSync.recordCoachPlanCompletionToHistory(
                                activityRepository = activityRepository,
                                trainingRecordRepository = trainingRecordRepository,
                                plan = p,
                                estimatedCalories = estimatedCalories
                            )

                            // 2) Notify coach(es)
                            val results = targets.map { coachId ->
                                cloudSync.reportCoachPlanCompletion(coachId = coachId, plan = p)
                            }

                            val failed = results.firstOrNull { it.isFailure }?.exceptionOrNull()
                            if (failed != null) {
                                snackbarHostState.showSnackbar(ctx.getString(R.string.report_failed_msg, failed.message ?: ""))
                            } else {
                                snackbarHostState.showSnackbar(ctx.getString(R.string.reported_to_coaches, targets.size))
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            if (item.isInbox) {
                                cloudSync.markInboxPlanRead(p.id)
                                snackbarHostState.showSnackbar(ctx.getString(R.string.marked_as_read, p.name))
                            } else {
                                cloudSync.removeRemotePlan(p.id)
                                snackbarHostState.showSnackbar(ctx.getString(R.string.removed_from_list, p.name))
                            }
                        }
                    }
                )
            }

            if (remoteItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.no_plans_to_receive), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // --- Diet suggestions from coach ---
            item(key = "diet_header") {
                Spacer(Modifier.height(6.dp))
                com.example.fitness.ui.ScreenHeader(
                    title = stringResource(R.string.diet_suggestions_from_coach),
                    subtitle = if (dietSuggestions.isEmpty()) stringResource(R.string.no_diet_suggestions) else stringResource(R.string.diet_received_count, dietSuggestions.size),
                    onBack = null
                )
            }

            if (dietSuggestions.isNotEmpty()) {
                items(dietSuggestions, key = { it.id }) { suggestion ->
                    DietSuggestionExpandableCard(
                        suggestion = suggestion,
                        coachNameCache = coachNameCache,
                        onDelete = {
                            scope.launch {
                                cloudSync.deleteDietSuggestion(suggestion.id)
                                    .onSuccess {
                                        snackbarHostState.showSnackbar(ctx.getString(R.string.diet_suggestion_deleted))
                                    }
                                    .onFailure { e ->
                                        snackbarHostState.showSnackbar(ctx.getString(R.string.diet_delete_failed, e.message ?: ""))
                                    }
                            }
                        }
                    )
                }
            } else {
                item(key = "diet_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.no_diet_suggestions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun RemotePlanExpandableCard(
    item: RemotePlanItem,
    modifier: Modifier = Modifier,
    joinedCoachIds: List<String> = emptyList(),
    onMarkCompleted: ((coachIdsToNotify: List<String>) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }

    var locallyCompleted by remember(item.plan.id) { mutableStateOf(false) }

    val publishedText = remember(item.plan.publishedAt) {
        item.plan.publishedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
    }

    val sourceText = item.sourceCoachDisplayName?.takeIf { it.isNotBlank() }
        ?: item.sourceCoachId?.takeIf { it.isNotBlank() }
        ?: joinedCoachIds.firstOrNull()
        ?: "(未知)"

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_plan_title)) },
            text = { Text(stringResource(R.string.delete_plan_confirm, item.plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    }
                ) { Text(stringResource(R.string.delete_trainee)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text(stringResource(R.string.report_complete_title)) },
            text = {
                val coachText = when {
                    joinedCoachIds.isEmpty() -> stringResource(R.string.report_complete_no_coach)
                    joinedCoachIds.size == 1 -> stringResource(R.string.report_complete_confirm_single, item.plan.name, joinedCoachIds.first())
                    else -> stringResource(R.string.report_complete_confirm_multi, item.plan.name, joinedCoachIds.size)
                }
                Text(coachText)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmComplete = false
                        locallyCompleted = true
                        onMarkCompleted?.invoke(joinedCoachIds)
                    }
                ) { Text(stringResource(R.string.report_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !locallyCompleted) { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (locallyCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.plan.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (locallyCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(stringResource(R.string.exercise_count_label, item.plan.exercises.size))
                            if (publishedText != null) append("  ·  ${stringResource(R.string.published_at_label, publishedText)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.source_label, sourceText) + if (item.isInbox) "  ·  ${stringResource(R.string.direct_publish_tag)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onMarkCompleted != null) {
                    IconButton(onClick = { if (!locallyCompleted) confirmComplete = true }, enabled = !locallyCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.complete_and_report)
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(onClick = { if (!locallyCompleted) confirmDelete = true }, enabled = !locallyCompleted) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.delete_plan_title))
                    }
                }

                IconButton(onClick = { expanded = !expanded }, enabled = !locallyCompleted) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded && !locallyCompleted) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                if (item.plan.exercises.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_exercises_in_plan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.plan.exercises.forEach { e ->
                        val meta = buildList {
                            e.sets?.let { add("${it}組") }
                            e.reps?.let { add("${it}下") }
                            e.weight?.let { add("${it}kg") }
                        }.joinToString(" · ")

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = e.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (meta.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

/** Expandable card for diet suggestions received from coach */
@Composable
private fun DietSuggestionExpandableCard(
    suggestion: DietSuggestion,
    modifier: Modifier = Modifier,
    coachNameCache: Map<String, String> = emptyMap(),
    onDelete: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val timeText = remember(suggestion.createdAtEpochMs) {
        java.time.Instant.ofEpochMilli(suggestion.createdAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
    }

    val coachName = coachNameCache[suggestion.coachId].orEmpty().trim()
    val sourceDisplay = coachName.ifBlank { suggestion.coachId.take(8) + "…" }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_diet_suggestion)) },
            text = { Text(stringResource(R.string.delete_diet_confirm, suggestion.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.delete_diet_suggestion)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stringResource(R.string.from_coach)}：$sourceDisplay  ·  $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_diet_suggestion),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = suggestion.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

