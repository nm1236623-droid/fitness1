package com.example.fitness.coach.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AirlineSeatReclineExtra
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.coach.cloud.CloudSyncUseCase
import com.example.fitness.coach.cloud.CoachTrainee
import com.example.fitness.coach.cloud.CoachTraineeDirectoryRepository
import com.example.fitness.coach.local.CoachLocalPlanRepository
import com.example.fitness.data.ExerciseEntry
import com.example.fitness.data.PartExerciseCatalog
import com.example.fitness.data.TrainingPlan
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class CoachPartUi(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
)

/**
 * Coach-only plan creator.
 * UX mirrors the app's "選擇訓練部位" flow:
 * 1) pick a body part
 * 2) pick exercises + adjust weight/sets
 * 3) save as a coach-local plan (does not mix with personal plans)
 */
@Composable
fun CoachPlanCreateScreen(
    onBack: () -> Unit,
    onSave: suspend (TrainingPlan) -> Unit,
    coachLocalRepo: CoachLocalPlanRepository,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Cloud publish wiring (uses same logic as CoachCenterScreen)
    // TrainingPlanRepository in this project is a concrete class, so we create a dummy instance.
    val appCtx: Context = remember { coachLocalRepo.appContext }
    val authRepository = remember(appCtx) { CoachAuthRepository(appCtx) }
    val cloudSync = remember(authRepository) {
        CloudSyncUseCase(
            authRepository,
            com.example.fitness.data.TrainingPlanRepository()
        )
    }

    // Trainees for single-target publish
    val traineeDirectoryRepo = remember { CoachTraineeDirectoryRepository() }
    val trainees = remember { mutableStateListOf<CoachTrainee>() }
    var publishExpanded by remember { mutableStateOf(false) }
    var publishTarget by remember { mutableStateOf<CoachTrainee?>(null) }
    var confirmPublishToTrainee by remember { mutableStateOf(false) }

    // Resolve coachId once for publish
    var coachId by remember { mutableStateOf("") }

    // Custom exercises from coach
    val customExercises by cloudSync.customExercisesFlow()
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        coachId = FirebaseAuth.getInstance().currentUser?.uid ?: authRepository.ensureUserId()
        cloudSync.startListeningCustomExercises()

        // Load trainees for publish dialog
        if (coachId.isNotBlank()) {
            traineeDirectoryRepo.listTrainees(coachId)
                .onSuccess { list ->
                    trainees.clear(); trainees.addAll(list)
                    publishTarget = trainees.firstOrNull()
                }
        }
    }

    var planName by remember { mutableStateOf("") }

    // dialog state
    var activeDialog by remember { mutableStateOf<String?>(null) }

    // selections for current dialog
    val selectedItems = remember { mutableStateListOf<String>() }
    val selectedSets = remember { mutableStateMapOf<String, Int>() }
    val selectedWeights = remember { mutableStateMapOf<String, Float>() }

    val defaultSets = 5
    val defaultWeight = 40f

    fun weightToSets(weight: Float, minSets: Int = 3, maxSets: Int = 12): Int {
        // heavier weight -> fewer sets, lighter -> more sets
        val w = weight.coerceIn(0f, 100f)
        val normalized = (w / 100f)
        val sets = (maxSets - (normalized * (maxSets - minSets))).roundToInt()
        return sets.coerceIn(minSets, maxSets)
    }

    // Coach plan being built (can combine multiple parts)
    // 每次建立計畫都是獨立的，不會自動載入之前的動作，確保每個計畫都是全新的
    val planExercises = remember { mutableStateListOf<ExerciseEntry>() }

    // Auto name: if user doesn't type a name, generate a reasonable default.
    // Uses selected body parts if we can infer them from current exercises.
    fun resolvedPlanName(): String {
        val trimmed = planName.trim()
        if (trimmed.isNotBlank()) return trimmed

        // Best-effort infer parts from exercise names (uses catalog)
        val exerciseNames = planExercises.map { it.name }.toSet()
        val partLabels = listOf(
            "chest" to "胸",
            "back" to "背",
            "legs" to "腿",
            "arms" to "手臂",
            "shoulders" to "肩",
            "abs" to "腹",
        ).filter { (key, _) ->
            PartExerciseCatalog.groupsForPart(key)
                .flatMap { it.second }
                .any { it in exerciseNames }
        }.map { it.second }

        val base = when {
            partLabels.isNotEmpty() -> partLabels.joinToString("+")
            activeDialog != null -> when (activeDialog) {
                "chest" -> "胸"
                "back" -> "背"
                "legs" -> "腿"
                "arms" -> "手臂"
                "shoulders" -> "肩"
                "abs" -> "腹"
                else -> "訓練"
            }

            else -> "訓練"
        }

        return "$base 計畫"
    }

    val parts = remember {
        listOf(
            CoachPartUi("chest", "胸", Icons.Default.Favorite, contentDescription = "胸部"),
            CoachPartUi(
                "back",
                "背",
                Icons.Default.AirlineSeatReclineExtra,
                contentDescription = "背部"
            ),
            CoachPartUi(
                "legs",
                "腿",
                Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = "腿部"
            ),
            CoachPartUi("arms", "手臂", Icons.Default.FitnessCenter, contentDescription = "手臂"),
            CoachPartUi(
                "shoulders",
                "肩",
                Icons.Default.AccessibilityNew,
                contentDescription = "肩膀"
            ),
            CoachPartUi("abs", "腹", Icons.Default.SportsMartialArts, contentDescription = "腹部"),
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${stringResource(R.string.coach_center)} - ${stringResource(R.string.create_plan)}", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = planName,
                onValueChange = { planName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.plan_name_optional)) },
                placeholder = { Text(stringResource(R.string.plan_name_example)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = stringResource(R.string.select_body_part), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val columns = when {
                    maxWidth < 420.dp -> 2
                    maxWidth < 720.dp -> 3
                    else -> 4
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(parts, key = { it.key }) { part ->
                        ElevatedCard(
                            modifier = Modifier
                                .height(104.dp)
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    // open dialog
                                    activeDialog = part.key
                                    // reset only the dialog temporary selections
                                    selectedItems.clear()
                                    selectedSets.clear()
                                    selectedWeights.clear()
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = part.icon,
                                    contentDescription = part.contentDescription,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = part.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // whole card click
                                Spacer(Modifier.height(0.dp))
                            }
                        }
                        // overlay clickable via separate item layer
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plan preview + actions
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val previewTitle = if (planName.trim()
                            .isBlank()
                    ) stringResource(R.string.plan_auto_generate) else stringResource(R.string.plan_name_display, planName.trim())
                    Text(previewTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.current_exercises_count, planExercises.size),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (planExercises.isEmpty()) {
                        Text(
                            stringResource(R.string.no_exercises_added),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Scrollable preview (滑動展示)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(planExercises, key = { it.name }) { e ->
                                val line = buildString {
                                    append(e.name)
                                    val sets = e.sets
                                    val reps = e.reps
                                    val w = e.weight
                                    if (sets != null) append("  ${sets}${appCtx.getString(R.string.sets)}")
                                    if (reps != null) append("x${reps}")
                                    if (w != null) append("  ${w.roundToInt()}kg")
                                }
                                Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.height(56.dp).width(110.dp)
                        ) { Text(stringResource(R.string.back)) }
                        TextButton(
                            enabled = planExercises.isNotEmpty(),
                            onClick = { planExercises.clear() },
                            modifier = Modifier.height(56.dp).width(110.dp)
                        ) { Text(stringResource(R.string.clear_btn)) }

                        // Save locally only (name no longer required)
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
                                    onSave(plan)
                                    snackbarHostState.showSnackbar(appCtx.getString(R.string.saved_plan, plan.name))
                                    onBack()
                                }
                            },
                            modifier = Modifier.height(56.dp).weight(1f)
                        ) { Text(stringResource(R.string.save)) }

                        // Publish: open trainee picker dialog (single target)
                        Button(
                            enabled = coachId.isNotBlank() && planExercises.isNotEmpty(),
                            onClick = {
                                if (trainees.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar(appCtx.getString(R.string.no_trainee_hint)) }
                                } else {
                                    confirmPublishToTrainee = true
                                }
                            },
                            modifier = Modifier.height(56.dp).width(150.dp)
                        ) { Text(stringResource(R.string.publish)) }

                        // Optional: keep cloud publish as separate action
                        OutlinedButton(
                            enabled = coachId.isNotBlank() && planExercises.isNotEmpty(),
                            onClick = {
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
                                    onSave(plan)
                                    val r = cloudSync.publishCoachLocalPlan(
                                        coachId = coachId,
                                        plan = plan
                                    )
                                    if (r.isSuccess) {
                                        snackbarHostState.showSnackbar(appCtx.getString(R.string.published_cloud, plan.name))
                                        onBack()
                                    } else {
                                        snackbarHostState.showSnackbar(appCtx.getString(R.string.publish_failed, r.exceptionOrNull()?.message ?: ""))
                                    }
                                }
                            },
                            modifier = Modifier.height(56.dp).width(150.dp)
                        ) { Text(stringResource(R.string.publish_cloud)) }
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }

        // --- Dialog mapping (mirror TrainingPlanScreen) ---

        // Dialog content groups (shared with TrainingPlanScreen)
        val groupsToShow = remember(activeDialog, customExercises) {
            val key = activeDialog
            if (key.isNullOrBlank()) emptyList()
            else {
                val catalogGroups = PartExerciseCatalog.groupsForPart(key)
                // Append custom exercises that match this body part
                val customForPart = customExercises
                    .filter { it.bodyPart == key }
                    .map { it.name }
                if (customForPart.isEmpty()) catalogGroups
                else catalogGroups + listOf("自訂動作" to customForPart)
            }
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

        if (activeDialog != null) {
            AlertDialog(
                onDismissRequest = {
                    activeDialog = null
                    selectedItems.clear(); selectedSets.clear(); selectedWeights.clear()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Add selected items into planExercises (merge; don't overwrite)
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
                            scope.launch { snackbarHostState.showSnackbar(appCtx.getString(R.string.added_to_plan, newEntries.size)) }

                            activeDialog = null
                            selectedItems.clear(); selectedSets.clear(); selectedWeights.clear()
                        },
                        modifier = Modifier.height(56.dp).width(160.dp)
                    ) { Text(stringResource(R.string.confirm_add)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            activeDialog = null
                            selectedItems.clear(); selectedSets.clear(); selectedWeights.clear()
                        },
                        modifier = Modifier.height(56.dp).width(120.dp)
                    ) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(text = stringResource(R.string.part_training_options, partDisplayName)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        groupsToShow.forEach { (group, items) ->
                            Text(
                                text = group,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            items.forEach { item ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isSelected = selectedItems.contains(item)
                                        androidx.compose.material3.Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked: Boolean ->
                                                if (checked) {
                                                    selectedItems.add(item)
                                                    if (!selectedSets.containsKey(item)) selectedSets[item] =
                                                        defaultSets
                                                } else {
                                                    selectedItems.remove(item)
                                                    selectedSets.remove(item)
                                                    selectedWeights.remove(item)
                                                }
                                            }
                                        )
                                        Text(
                                            text = item,
                                            modifier = Modifier.padding(start = 8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (selectedItems.contains(item)) {
                                        val curW = selectedWeights[item] ?: defaultWeight
                                        if (!selectedWeights.containsKey(item)) selectedWeights[item] =
                                            curW
                                        if (!selectedSets.containsKey(item)) selectedSets[item] =
                                            weightToSets(curW, minSets = 3, maxSets = 12)

                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(start = 40.dp, top = 6.dp)
                                        ) {
                                            Text(text = stringResource(R.string.weight_label, selectedWeights[item]?.roundToInt() ?: defaultWeight.toInt()))
                                            Slider(
                                                value = selectedWeights[item] ?: defaultWeight,
                                                onValueChange = { v ->
                                                    selectedWeights[item] = v
                                                    selectedSets[item] = weightToSets(v)
                                                },
                                                valueRange = 0f..100f,
                                                steps = 99
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = stringResource(R.string.sets_label))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(onClick = {
                                                    val cur = selectedSets[item] ?: defaultSets
                                                    selectedSets[item] = (cur - 1).coerceAtLeast(1)
                                                }) { Text("-") }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = " ${selectedSets[item] ?: defaultSets} ")
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(onClick = {
                                                    val cur = selectedSets[item] ?: defaultSets
                                                    selectedSets[item] = (cur + 1).coerceAtMost(20)
                                                }) { Text("+") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // ===== Publish-to-trainee confirm dialog (single select) =====
        if (confirmPublishToTrainee) {
            AlertDialog(
                onDismissRequest = { confirmPublishToTrainee = false },
                title = { Text(stringResource(R.string.select_trainee)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.publish_to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
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
                                onSave(plan)

                                val r = cloudSync.publishCoachLocalPlanToTrainee(
                                    coachId = coachId,
                                    traineeId = target.traineeId,
                                    plan = plan,
                                )
                                if (r.isSuccess) {
                                    confirmPublishToTrainee = false
                                    snackbarHostState.showSnackbar(
                                        appCtx.getString(R.string.published_to_trainee, target.displayName.ifBlank { target.traineeId })
                                    )
                                    onBack()
                                } else {
                                    snackbarHostState.showSnackbar(appCtx.getString(R.string.publish_failed, r.exceptionOrNull()?.message ?: ""))
                                }
                            }
                        },
                        enabled = publishTarget != null && coachId.isNotBlank() && planExercises.isNotEmpty()
                    ) { Text(stringResource(R.string.confirm_publish)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmPublishToTrainee = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
