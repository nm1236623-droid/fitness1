package com.example.fitness.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.fitness.BuildConfig
import com.example.fitness.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// --- 機能模組引用 ---
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.activity.ActivityRecord
import com.example.fitness.auth.SessionManager
import com.example.fitness.auth.SessionRepository
import com.example.fitness.chat.ChatScreen
import com.example.fitness.coach.UserRole
import com.example.fitness.data.DietRecord
import com.example.fitness.data.FirebaseDietRecordRepository
import com.example.fitness.data.FirebaseTrainingRecordRepository
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.firebase.AuthRepository
import com.example.fitness.food.FoodRecognitionScreenOptimized
import com.example.fitness.inbody.InBodyRepository
import com.example.fitness.inbody.InBodyScreen
import com.example.fitness.running.CardioRepository
import com.example.fitness.running.CardioScreen
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.user.UserProfileRepository
import com.example.fitness.user.UserRoleProfileRepository
import com.example.fitness.ui.settings.ProfileBasicSettingsScreen
import com.example.fitness.ui.settings.WaterGoalDialog
import com.example.fitness.ui.settings.WaterIntakeCard
import com.example.fitness.nutrition.AdvancedNutritionRepository
import com.example.fitness.user.FirebaseUserProfileFirestoreRepository
import com.example.fitness.user.UserProfile

// --- Google & Firebase 引用 ---
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.firebase.auth.FirebaseAuth

// --- AI 引用 ---
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults

// ★★★ Gemini AI 服務 (用於飲食分析) ★★★
object GeminiFoodAIService {
    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY
    private const val MODEL_NAME = "gemini-2.5-flash"

    suspend fun analyze(text: String): DietRecord? {
        try {
            val generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                }
            )

            val prompt = """
                你是一位專業營養師。請分析這段食物描述："$text"。
                請回傳一個純 JSON 物件，必須嚴格遵守以下格式與型別：
                {
                  "food_name": "食物名稱(String)",
                  "calories": 熱量大卡(Int),
                  "protein": 蛋白質克數(Double),
                  "carbs": 碳水化合物克數(Double),
                  "fat": 脂肪克數(Double),
                  "meal_type": "早餐" 或 "午餐" 或 "晚餐" 或 "點心"
                }
                數值請依據台灣常見份量估算。如果無法辨識，回傳 null。
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return null

            val jsonString = extractJson(responseText) ?: return null
            val json = JSONObject(jsonString)

            return DietRecord(
                id = UUID.randomUUID().toString(),
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                date = LocalDate.now(),
                timestamp = System.currentTimeMillis(),
                mealType = json.optString("meal_type", "點心"),
                foodName = json.optString("food_name", text),
                calories = json.optInt("calories", 0),
                proteinG = json.optDouble("protein", 0.0),
                carbsG = json.optDouble("carbs", 0.0),
                fatG = json.optDouble("fat", 0.0)
            )
        } catch (e: Exception) {
            Log.e("GeminiAI", "Analysis failed: ${e.message}")
            return null
        }
    }

    private fun extractJson(text: String): String? {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1)
        }
        return null
    }
}


// 用於存儲首頁摘要數據的 Data Class
data class DashboardStats(
    val consumed: Double,
    val burned: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val remaining: Double
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE", "UNUSED_VARIABLE")
@Composable
fun HomeScreen(
    repository: TrainingPlanRepository,
    activityRepository: ActivityLogRepository,
    modifier: Modifier = Modifier,
    trainingRecordRepository: FirebaseTrainingRecordRepository = remember { FirebaseTrainingRecordRepository() },
    isPro: Boolean = false,
    onShowPaywall: () -> Unit = {},
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    var selectedWorkoutPart by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWorkoutPlanId by rememberSaveable { mutableStateOf<String?>(null) }

    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }

    val authRepository = remember { AuthRepository() }
    val sessionManager = remember(appCtx) { SessionManager(authRepository, SessionRepository(appCtx)) }

    val inBodyRepo = remember(appCtx) { InBodyRepository(appCtx) }
    val cardioRepo = remember { CardioRepository() }
    val userProfileRepo = remember { UserProfileRepository(appCtx) }
    val roleRepo = remember { UserRoleProfileRepository() }

    var myRole by remember { mutableStateOf(UserRole.TRAINEE) }

    val firestoreProfileRepo = remember { FirebaseUserProfileFirestoreRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val remoteProfile by remember(firestoreProfileRepo, lifecycleOwner) {
        firestoreProfileRepo.observeMyProfile()
    }.collectAsStateWithLifecycle(initialValue = UserProfile(), lifecycleOwner = lifecycleOwner)

    // ★★★ FIX: 狀態提升 (State Hoisting) - 訓練紀錄 ★★★
    // 將資料收集移至最上層，避免切換 Tab 時重新載入導致閃爍
    val activityLogsFlow = remember(activityRepository) { activityRepository.logs }
    val trainingLogs by activityLogsFlow.collectAsStateWithLifecycle(
        initialValue = emptyList(),
        lifecycleOwner = lifecycleOwner
    )

    // ★★★ FIX: 狀態提升 (State Hoisting) - 飲食紀錄 ★★★
    // 讓 Dashboard 在切換時也不會數值跳動
    // ★ 確保登入後 listener 已啟動
    LaunchedEffect(Unit) {
        FirebaseDietRecordRepository.listenForUpdates()
    }
    val dietRecordsFlow = remember { FirebaseDietRecordRepository.records }
    val dietRecords by dietRecordsFlow.collectAsStateWithLifecycle(
        initialValue = emptyList(),
        lifecycleOwner = lifecycleOwner
    )

    var myNickname by remember { mutableStateOf("Trainee") }

    LaunchedEffect(Unit) {
        myRole = roleRepo.getMyRole().getOrDefault(UserRole.TRAINEE)
        val fbUser = FirebaseAuth.getInstance().currentUser
        if (fbUser != null && !fbUser.displayName.isNullOrBlank()) {
            myNickname = fbUser.displayName!!
        }
        if (myNickname == "Trainee" && myRole == UserRole.COACH) {
            myNickname = "Coach"
        }
    }

    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val currentUser = firebaseAuth.currentUser

    val displayNickname = remember(remoteProfile.nickname, myNickname, currentUser?.displayName) {
        when {
            remoteProfile.nickname.isNotBlank() -> remoteProfile.nickname
            !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
            else -> myNickname
        }
    }

    val userWeight by userProfileRepo.weightKg.collectAsState(initial = 70f)
    val userHeight by userProfileRepo.heightCm.collectAsState(initial = 170f)
    val userTdee by userProfileRepo.tdee.collectAsState(initial = 2000f)
    val proteinGoal by userProfileRepo.proteinGoalGrams.collectAsState(initial = 120f)

    var showProfileDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Navigation Stack Logic
    var backStack by rememberSaveable { mutableStateOf(listOf("plans")) }
    val selected = backStack.lastOrNull() ?: "plans"

    val selectTab: (String) -> Unit = { tab ->
        val target = if (tab == "home") "plans" else tab
        if (target != selected) {
            val bottomNavRoutes = listOf("plans", "calendar", "food", "chat", "analytics")
            if (target in bottomNavRoutes) {
                // If it's a bottom bar route, reset stack to this tab to avoid infinite deep nesting
                backStack = listOf(target)
            } else {
                backStack = backStack + target
            }
        }
    }

    val popBackStack: () -> Unit = {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        } else {
            // Default to plans if somehow empty
            backStack = listOf("plans")
        }
    }

    // System Back Button support
    androidx.activity.compose.BackHandler(enabled = backStack.size > 1) {
        popBackStack()
    }

    val cardShape = MaterialTheme.shapes.extraLarge
    val isCompact = widthSizeClass == WindowWidthSizeClass.Compact

    // Navigation items shared between NavigationBar and NavigationRail
    val navItems = listOf(
        Triple("plans", Icons.Default.Home, stringResource(R.string.nav_home)),
        Triple("calendar", Icons.Default.DateRange, stringResource(R.string.nav_calendar)),
        Triple("food", Icons.Default.PhotoCamera, stringResource(R.string.nav_camera)),
        Triple("chat", Icons.Filled.ChatBubbleOutline, stringResource(R.string.nav_ai)),
        Triple("analytics", Icons.Filled.Analytics, stringResource(R.string.nav_analytics))
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selected == "plans") {
                CenterAlignedTopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                sessionManager.logout()
                                val intent = Intent(ctx, com.example.fitness.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                ctx.startActivity(intent)
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                        }
                    },
                    title = {
                        Text("Fitness", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.4.sp)
                    },
                    actions = {
                        IconButton(onClick = { selectTab("profile_basic_settings") }) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = "設定")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            // Only show bottom bar on compact (phone) screens
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    BannerAd(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        containerColor = Color.Transparent
                    ) {
                        navItems.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                selected = selected == route,
                                onClick = { selectTab(route) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                alwaysShowLabel = false,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // NavigationRail for Medium/Expanded screens (tablet/foldable)
                if (!isCompact) {
                    NavigationRail(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        navItems.forEach { (route, icon, label) ->
                            NavigationRailItem(
                                selected = selected == route,
                                onClick = { selectTab(route) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                alwaysShowLabel = false,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Main content area - constrain max width on very large screens
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // On expanded screens, add horizontal centering with max width
                        val contentModifier = if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                            Modifier.widthIn(max = 840.dp).align(Alignment.TopCenter)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Box(modifier = contentModifier) {
                    when (selected) {
                        "plans" -> {
                            HomeDashboardContent(
                                // ★★★ FIX: 傳入已收集的資料 List
                                trainingLogs = trainingLogs,
                                dietRecords = dietRecords,
                                userProfileRepo = userProfileRepo,
                                displayNickname = displayNickname,
                                avatarUrl = remoteProfile.avatarUri
                                    ?: currentUser?.photoUrl?.toString(),
                                myRole = myRole,
                                userTdee = userTdee,
                                proteinGoal = proteinGoal,
                                selectTab = selectTab,
                                showProfileDialog = showProfileDialog,
                                onShowProfileDialogChange = { showProfileDialog = it },
                                userWeight = userWeight,
                                userHeight = userHeight,
                                snackbarHostState = snackbarHostState,
                                scope = scope,
                                cardShape = cardShape
                            )
                        }
                        "workout" -> {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                ScreenHeader(title = stringResource(R.string.workout), subtitle = stringResource(R.string.select_body_part), onBack = popBackStack)
                                Spacer(modifier = Modifier.height(12.dp))

                                SectionCard(title = stringResource(R.string.quick_open)) {
                                    PrimaryActionRow(
                                        primaryText = stringResource(R.string.select_body_part), primaryIcon = Icons.Default.FitnessCenter,
                                        onPrimary = { selectTab("workout_select") },
                                        secondaryText = "AI 健身計畫", secondaryIcon = Icons.Default.AutoAwesome,
                                        onSecondary = { selectTab("ai_fitness_plan") }
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                SectionCard(title = stringResource(R.string.coach) + " / " + stringResource(R.string.trainee)) {
                                    val isCoach = myRole == UserRole.COACH
                                    PrimaryActionRow(
                                        primaryText = stringResource(R.string.coach_center), primaryIcon = Icons.Default.Upload,
                                        onPrimary = {
                                            if (isPro) selectTab("coach_center") else onShowPaywall()
                                        },
                                        secondaryText = stringResource(R.string.trainee_center), secondaryIcon = Icons.Default.Download,
                                        onSecondary = { selectTab("trainee_import") },
                                        primaryEnabled = isCoach,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (!isCoach) {
                                        Text(stringResource(R.string.coach_only_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text(stringResource(R.string.coach_plan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                SectionCard(title = stringResource(R.string.saved_templates)) {
                                    Button(
                                        onClick = { selectTab("templates") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.saved_templates),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        "workout_select" -> PartSelectionScreen(
                            onSelect = { part ->
                                selectedWorkoutPart = part
                                selectTab("workout_exercise_select")
                            },
                            onCancel = popBackStack
                        )
                        "workout_exercise_select" -> {
                            val part = selectedWorkoutPart
                            if (!part.isNullOrBlank()) {
                                ExerciseSelectionScreen(
                                    part = part,
                                    repository = repository,
                                    onDone = popBackStack
                                )
                            } else {
                                LaunchedEffect(Unit) { popBackStack() }
                            }
                        }
                        "bodyphotos" -> BodyPhotoScreen(onDone = popBackStack)
                        "create_workout" -> WorkoutCreateScreen(repository = repository, onDone = popBackStack)
                        "ai_fitness_plan" -> AiFitnessPlanScreen(inBodyRepo = inBodyRepo, repository = repository, onDone = popBackStack)
                        "activity" -> ActivityLogScreen(
                            // ★★★ FIX: 傳入已收集的 Logs List，移除這裡的 Repository 依賴
                            logs = trainingLogs,
                            onBack = popBackStack
                        )
                        "food" -> FoodRecognitionScreenOptimized(activityRepository = activityRepository, onBack = popBackStack)
                        "foodlog" -> FoodLogScreen(onBack = popBackStack)
                        "calendar" -> CalendarScreen(
                            repository = repository,
                            trainingRecordRepository = trainingRecordRepository,
                            onOpenPlan = {
                                selectedWorkoutPlanId = it
                                selectTab("workout_plan_detail")
                            },
                            onBack = popBackStack
                        )
                        "settings" -> SettingsScreen(onDone = popBackStack)
                        "chat" -> ChatScreen(onBack = popBackStack)
                        "inbody" -> InBodyScreen(repository = inBodyRepo, onDone = popBackStack)
                        "cardio" -> CardioScreen(repository = cardioRepo, activityRepository = activityRepository, userWeightKg = userWeight, onDone = popBackStack)
                        "analytics" -> AnalyticsScreen(repository, activityRepository, inBodyRepo, onOpenPartAnalysis = { selectTab("part_analysis") }, onOpenInBodyAnalysis = { selectTab("inbody_analysis") }, onBack = popBackStack)
                        "coach_center" -> com.example.fitness.coach.ui.CoachCenterScreen(authRepository = com.example.fitness.coach.CoachAuthRepository(LocalContext.current.applicationContext), trainingPlanRepository = repository, onBack = popBackStack, onNavigateToCreatePlan = { selectTab("coach_plan_create") })
                        "coach_plan_create" -> {
                            val appCtx = LocalContext.current.applicationContext
                            val repo = remember(appCtx) { com.example.fitness.coach.local.CoachLocalPlanRepository(appCtx) }
                            com.example.fitness.coach.ui.CoachPlanCreateScreen(onBack = popBackStack, onSave = { scope.launch { repo.add(it) } }, coachLocalRepo = repo)
                        }
                        "trainee_import" -> com.example.fitness.coach.ui.TraineeImportScreen(trainingPlanRepository = repository, onBack = popBackStack)
                        "inbody_analysis" -> com.example.fitness.inbody.InBodyAnalysisScreen(inBodyRepository = inBodyRepo, onBack = popBackStack)
                        "part_analysis" -> PartAnalysisScreen(repository = repository, activityRepository = activityRepository, inBodyRepo = inBodyRepo, onDone = popBackStack)
                        "workout_plan_detail" -> {
                            val pid = selectedWorkoutPlanId
                            if (!pid.isNullOrBlank()) {
                                TrainingPlanDetailScreen(repository = repository, planId = pid, onBack = popBackStack)
                            } else {
                                LaunchedEffect(Unit) { popBackStack() }
                            }
                        }
                        "social" -> com.example.fitness.ui.social.SocialCenterScreen(onBack = popBackStack)
                        "messages" -> com.example.fitness.ui.messaging.ChatRoomListScreen(
                            isCoach = myRole == UserRole.COACH,
                            onChatRoomSelected = { chatRoomId -> selectTab("chat_room") },
                            onBack = popBackStack
                        )
                        "templates" -> SavedPlansScreen(
                            repository = repository,
                            activityRepository = activityRepository,
                            trainingRecordRepository = trainingRecordRepository,
                            onBack = popBackStack
                        )
                        "advanced_settings" -> com.example.fitness.ui.settings.AdvancedSettingsScreen(onBack = popBackStack)
                        "training_history" -> TrainingHistoryScreen(
                            trainingRecordRepository = trainingRecordRepository,
                            modifier = Modifier.fillMaxSize(),
                            onBack = popBackStack
                        )
                        "profile_basic_settings" -> ProfileBasicSettingsScreen(
                            onBack = popBackStack
                        )
                        else -> TrainingPlanScreen(repository = repository, activityRepository = activityRepository, trainingRecordRepository = trainingRecordRepository, onRecordCreated = { selectTab("activity") })
                    }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------
// Helper Functions & UI Components
// ------------------------------------

@Composable
fun HomeDashboardContent(
    // ★★★ FIX: 接收 List 資料，而非 Repository
    trainingLogs: List<ActivityRecord>,
    dietRecords: List<DietRecord>,
    userProfileRepo: UserProfileRepository,
    displayNickname: String,
    avatarUrl: String?,
    myRole: UserRole,
    userTdee: Float,
    proteinGoal: Float,
    selectTab: (String) -> Unit,
    showProfileDialog: Boolean,
    onShowProfileDialogChange: (Boolean) -> Unit,
    userWeight: Float,
    userHeight: Float,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    cardShape: androidx.compose.ui.graphics.Shape
) {
    val today = LocalDate.now(ZoneId.systemDefault())

    // 計算邏輯包在 remember 中 (依賴傳入的 List)
    val stats = remember(dietRecords, trainingLogs, today, userTdee) {
        val todayFoodLogs = dietRecords.filter { it.date == today }

        val todayExerciseLogs = trainingLogs.filter {
            !it.type.startsWith("飲食") &&
                    // 正確處理 Instant 轉換
                    it.start.atZone(ZoneId.systemDefault()).toLocalDate() == today
        }

        val consumed = todayFoodLogs.sumOf { it.calories.toDouble() }
        val protein = todayFoodLogs.sumOf { it.proteinG ?: 0.0 }
        val carbs = todayFoodLogs.sumOf { it.carbsG ?: 0.0 }
        val fat = todayFoodLogs.sumOf { it.fatG ?: 0.0 }
        val burned = todayExerciseLogs.sumOf { it.calories ?: 0.0 }
        val net = consumed - burned
        val remaining = userTdee - net

        DashboardStats(consumed, burned, protein, carbs, fat, remaining)
    }

    val carbsGoal = (userTdee * 0.5f / 4f)
    val fatGoal = (userTdee * 0.25f / 9f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val cs = MaterialTheme.colorScheme
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).border(2.dp, cs.primary, CircleShape).clip(CircleShape).background(cs.surface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        val resolvedAvatarUrl = avatarUrl
                            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                        if (!resolvedAvatarUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(resolvedAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                error = coil.compose.rememberAsyncImagePainter(
                                    model = resolvedAvatarUrl
                                ),
                            )
                        } else {
                            Text(displayNickname.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Hi, $displayNickname", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                        Text(if (myRole == UserRole.COACH) stringResource(R.string.coach) else stringResource(R.string.trainee), style = MaterialTheme.typography.labelMedium, color = cs.onBackground.copy(alpha = 0.7f))
                    }
                }
            }
        }
        item {
            val cs = MaterialTheme.colorScheme
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)) {
                Column {
                    Text(stringResource(R.string.training_summary), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryStat(stringResource(R.string.today_intake), "%.0f".format(stats.consumed), "kcal", Icons.Default.Restaurant, modifier = Modifier.weight(1f))
                        SummaryStat(stringResource(R.string.today_burned), "%.0f".format(stats.burned), "kcal", Icons.Default.LocalFireDepartment, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            NutritionProgressCard(
                consumedCalories = stats.consumed.toFloat(),
                targetCalories = userTdee,
                consumedProtein = stats.protein.toFloat(),
                targetProtein = proteinGoal,
                consumedCarbs = stats.carbs.toFloat(),
                targetCarbs = carbsGoal,
                consumedFat = stats.fat.toFloat(),
                targetFat = fatGoal,
                remainingCalories = stats.remaining.toFloat()
            )
        }
        item {
            val shortcuts = listOf(
                ShortcutItem(stringResource(R.string.workout), Icons.Default.FitnessCenter) { selectTab("workout") },
                ShortcutItem(stringResource(R.string.training_history), Icons.Default.History) { selectTab("training_history") },
                ShortcutItem(stringResource(R.string.diet_log), Icons.Default.Restaurant) { selectTab("foodlog") },
                ShortcutItem(stringResource(R.string.social_center), Icons.Default.People) { selectTab("social") },
                ShortcutItem(stringResource(R.string.cardio), Icons.AutoMirrored.Filled.DirectionsRun) { selectTab("cardio") },
                ShortcutItem(stringResource(R.string.inbody), Icons.Default.MonitorWeight) { selectTab("inbody") },
                ShortcutItem(stringResource(R.string.body_photos), Icons.Default.PhotoLibrary) { selectTab("bodyphotos") },
                ShortcutItem(stringResource(R.string.advanced_settings), Icons.Default.Tune) { selectTab("advanced_settings") },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shortcuts.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item -> ShortcutCard(item = item, modifier = Modifier.weight(1f), shape = cardShape) }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { onShowProfileDialogChange(false) },
            confirmButton = { Button(onClick = { onShowProfileDialogChange(false) }) { Text(stringResource(R.string.confirm)) } },
            text = { Text("Profile Settings Placeholder") }
        )
    }
}

// ------------------------------------
// UI Components Definition
// ------------------------------------

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val backText = stringResource(R.string.back)
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backText, tint = cs.onBackground) } else Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.primary); if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(0.8f)) }
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp)) { Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground); Spacer(Modifier.height(10.dp)); content() } }
}

@Composable
fun PrimaryActionRow(primaryText: String, primaryIcon: ImageVector, onPrimary: () -> Unit, secondaryText: String, secondaryIcon: ImageVector, onSecondary: () -> Unit, primaryEnabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .then(if (primaryEnabled) Modifier.neonGlowBorder(16.dp, glowColor = cs.primary) else Modifier),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (primaryEnabled) cs.primary.copy(0.2f) else Color.Gray.copy(0.3f))
        ) {
            Icon(primaryIcon, contentDescription = primaryText, tint = if (primaryEnabled) cs.primary else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                primaryText,
                color = if (primaryEnabled) cs.primary else Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .neonGlowBorder(16.dp, glowColor = cs.primary),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary)
        ) {
            Icon(secondaryIcon, contentDescription = secondaryText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().height(50.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-5240507905590335/7971899831"
                adListener = object : com.google.android.gms.ads.AdListener() {
                    override fun onAdLoaded() {
                        android.util.Log.d("BannerAd", "✅ 廣告載入成功")
                    }
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        android.util.Log.e("BannerAd", "❌ 廣告載入失敗: code=${error.code}, msg=${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun SummaryStat(title: String, value: String, unit: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = modifier.glassEffect(20.dp)) { Column(modifier = Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (icon != null) Icon(icon, contentDescription = title, tint = cs.onBackground, modifier = Modifier.size(18.dp)); Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onBackground.copy(0.9f)) }; Spacer(Modifier.height(6.dp)); Row(verticalAlignment = Alignment.Bottom) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = cs.primary); Spacer(Modifier.width(4.dp)); Text(unit, style = MaterialTheme.typography.labelMedium, color = cs.onBackground.copy(0.8f)) } } }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().glassEffect(20.dp).padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(0.7f)) }
}

data class ShortcutItem(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun ShortcutCard(item: ShortcutItem, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape) {
    val quickOpenText = stringResource(R.string.quick_open)
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(80.dp)  // 固定高度確保一致
            .glassEffect(20.dp)
            .clickable(onClick = item.onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.primary.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = item.title, tint = cs.primary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    quickOpenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodLogChipsRow(content: @Composable () -> Unit) { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() } }

@Composable
fun FoodLogEntryChipsRow(p: Double?, c: Double?, f: Double?) { FoodLogChipsRow { AssistChip(onClick = {}, label = { Text("P ${p?.let { "%.1f".format(it) } ?: "-"} g") }); AssistChip(onClick = {}, label = { Text("C ${c?.let { "%.1f".format(it) } ?: "-"} g") }); AssistChip(onClick = {}, label = { Text("F ${f?.let { "%.1f".format(it) } ?: "-"} g") }) } }

@Composable
private fun DateSwitcherRow(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.glassEffect(16.dp).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前一天", tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_day), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun FoodLogScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val dietRepository = FirebaseDietRecordRepository
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cs = MaterialTheme.colorScheme

    // ★ 確保 listener 已啟動
    LaunchedEffect(Unit) { dietRepository.listenForUpdates() }

    val recordsFlow = remember { dietRepository.records }
    val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList(), lifecycleOwner = lifecycleOwner)

    val filteredRecords = remember(records, currentDate) {
        records.filter { it.date == currentDate }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    fun deleteRecord(record: DietRecord) {
        scope.launch { dietRepository.deleteRecord(record.id) }
    }

    val totalCalories = filteredRecords.sumOf { it.calories }
    val totalProtein = filteredRecords.sumOf { it.proteinG ?: 0.0 }
    val totalCarbs = filteredRecords.sumOf { it.carbsG ?: 0.0 }
    val totalFat = filteredRecords.sumOf { it.fatG ?: 0.0 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // ── Header ──
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(cs.primary.copy(alpha = 0.1f))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = cs.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.diet_log),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.primary
                        )
                        Text(
                            stringResource(R.string.ai_nutrition_analysis),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onBackground.copy(0.5f)
                        )
                    }
                    // Add button
                    Box(
                        modifier = Modifier.size(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(cs.primary.copy(alpha = 0.15f))
                            .clickable { showAddDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = cs.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Date Switcher ──
            DateSwitcherRow(date = currentDate, onDateChange = { currentDate = it }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(14.dp))

            // ── Daily Summary — 2×2 Nutrient Grid ──
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(16.dp)) {
                Column {
                    Text(
                        stringResource(R.string.daily_summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NutrientMiniCard(
                            icon = "🔥", label = "Kcal",
                            value = "$totalCalories",
                            color = TechColors.NeonOrange,
                            modifier = Modifier.weight(1f)
                        )
                        NutrientMiniCard(
                            icon = "💪", label = stringResource(R.string.protein),
                            value = "${"%.1f".format(totalProtein)}g",
                            color = TechColors.NeonBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NutrientMiniCard(
                            icon = "🥑", label = stringResource(R.string.fat),
                            value = "${"%.1f".format(totalFat)}g",
                            color = TechColors.NeonGreen,
                            modifier = Modifier.weight(1f)
                        )
                        NutrientMiniCard(
                            icon = "🌾", label = stringResource(R.string.carbs),
                            value = "${"%.1f".format(totalCarbs)}g",
                            color = TechColors.NeonPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Meal Records ──
            val breakfastStr = stringResource(R.string.breakfast)
            val lunchStr = stringResource(R.string.lunch)
            val dinnerStr = stringResource(R.string.dinner)
            val snackStr = stringResource(R.string.snack)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val grouped = filteredRecords.groupBy { it.mealType }
                val mealOrder = listOf(breakfastStr, lunchStr, dinnerStr, snackStr)
                val mealIcons = mapOf(
                    breakfastStr to "🌅",
                    lunchStr to "☀️",
                    dinnerStr to "🌙",
                    snackStr to "🍿"
                )
                mealOrder.forEach { type ->
                    val mealRecords = grouped[type]
                    if (!mealRecords.isNullOrEmpty()) {
                        item(key = "header_$type") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                            ) {
                                Text(mealIcons[type] ?: "🍽️", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    type,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = cs.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        "${mealRecords.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(mealRecords, key = { it.id }) { record ->
                            DietRecordCard(record, onDelete = { deleteRecord(record) })
                        }
                    }
                }
                if (filteredRecords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(cs.primary.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = cs.primary.copy(alpha = 0.3f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.no_food_record),
                                    color = cs.onBackground.copy(0.4f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "📝",
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            SmartFoodEntryDialog(
                currentUserId = currentUserId,
                onDismiss = { showAddDialog = false },
                onConfirm = { newRecord ->
                    scope.launch {
                        val recordToSave = newRecord.copy(date = currentDate)
                        dietRepository.addRecord(recordToSave)
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

/** 營養素迷你卡片 */
@Composable
private fun NutrientMiniCard(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun SmartFoodEntryDialog(currentUserId: String, onDismiss: () -> Unit, onConfirm: (DietRecord) -> Unit) {
    var foodDescription by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var foodName by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val breakfastStr = stringResource(R.string.breakfast)
    val lunchStr = stringResource(R.string.lunch)
    val dinnerStr = stringResource(R.string.dinner)
    val snackStr = stringResource(R.string.snack)
    var selectedMealType by remember { mutableStateOf(breakfastStr) }
    val mealTypes = listOf(breakfastStr, lunchStr, dinnerStr, snackStr)
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val aiServiceFailedStr = stringResource(R.string.ai_service_failed)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TechColors.DarkBlue,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI", tint = cs.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ai_nutrition_analysis), color = cs.primary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // AI input field
                OutlinedTextField(
                    value = foodDescription,
                    onValueChange = { foodDescription = it },
                    label = { Text(stringResource(R.string.food_name), color = cs.onBackground.copy(0.5f)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.primary.copy(alpha = 0.3f),
                        cursorColor = cs.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = cs.primary, strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = {
                                if (foodDescription.isNotBlank()) {
                                    isAnalyzing = true; errorMessage = null
                                    scope.launch {
                                        val result = GeminiFoodAIService.analyze(foodDescription)
                                        if (result != null) {
                                            foodName = result.foodName
                                            calories = result.calories.toString()
                                            protein = (result.proteinG ?: 0.0).toString()
                                            carbs = (result.carbsG ?: 0.0).toString()
                                            fat = (result.fatG ?: 0.0).toString()
                                            selectedMealType = result.mealType
                                        } else {
                                            errorMessage = aiServiceFailedStr
                                        }
                                        isAnalyzing = false
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.analyze_food), tint = cs.primary)
                            }
                        }
                    }
                )

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color(0xFFFF4757), style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(color = cs.primary.copy(0.15f))

                Text(stringResource(R.string.nutrient_analysis_hint), color = cs.onBackground.copy(0.5f), fontSize = 12.sp)

                // Food name
                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text(stringResource(R.string.food_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = cs.primary.copy(0.5f),
                        unfocusedBorderColor = cs.primary.copy(0.2f)
                    )
                )

                // Calories
                OutlinedTextField(
                    value = calories,
                    onValueChange = { if (it.all { c -> c.isDigit() }) calories = it },
                    label = { Text(stringResource(R.string.calories_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TechColors.NeonOrange,
                        unfocusedTextColor = TechColors.NeonOrange,
                        focusedBorderColor = TechColors.NeonOrange.copy(0.5f),
                        unfocusedBorderColor = TechColors.NeonOrange.copy(0.2f)
                    )
                )

                // P/C/F row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    OutlinedTextField(
                        value = protein, onValueChange = { protein = it },
                        label = { Text("P(g)", color = TechColors.NeonBlue.copy(0.7f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TechColors.NeonBlue, unfocusedTextColor = TechColors.NeonBlue, focusedBorderColor = TechColors.NeonBlue.copy(0.3f), unfocusedBorderColor = TechColors.NeonBlue.copy(0.15f)),
                        keyboardOptions = decimalKeyboard
                    )
                    OutlinedTextField(
                        value = carbs, onValueChange = { carbs = it },
                        label = { Text("C(g)", color = TechColors.NeonPurple.copy(0.7f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TechColors.NeonPurple, unfocusedTextColor = TechColors.NeonPurple, focusedBorderColor = TechColors.NeonPurple.copy(0.3f), unfocusedBorderColor = TechColors.NeonPurple.copy(0.15f)),
                        keyboardOptions = decimalKeyboard
                    )
                    OutlinedTextField(
                        value = fat, onValueChange = { fat = it },
                        label = { Text("F(g)", color = TechColors.NeonGreen.copy(0.7f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TechColors.NeonGreen, unfocusedTextColor = TechColors.NeonGreen, focusedBorderColor = TechColors.NeonGreen.copy(0.3f), unfocusedBorderColor = TechColors.NeonGreen.copy(0.15f)),
                        keyboardOptions = decimalKeyboard
                    )
                }

                // Meal type chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mealTypes.forEach { type ->
                        FilterChip(
                            selected = selectedMealType == type,
                            onClick = { selectedMealType = type },
                            label = { Text(type, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primary.copy(0.25f),
                                selectedLabelColor = cs.primary,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = cs.onBackground.copy(0.6f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (foodName.isNotBlank() && calories.isNotBlank()) {
                        val record = DietRecord(
                            id = UUID.randomUUID().toString(),
                            userId = currentUserId,
                            date = LocalDate.now(),
                            timestamp = System.currentTimeMillis(),
                            mealType = selectedMealType,
                            foodName = foodName,
                            calories = calories.toIntOrNull() ?: 0,
                            proteinG = protein.toDoubleOrNull() ?: 0.0,
                            carbsG = carbs.toDoubleOrNull() ?: 0.0,
                            fatG = fat.toDoubleOrNull() ?: 0.0
                        )
                        onConfirm(record)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary.copy(alpha = 0.2f),
                    contentColor = cs.primary
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = cs.onBackground.copy(0.5f))
            }
        }
    )
}

@Composable
fun DietRecordCard(record: DietRecord, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(16.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Food info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                // Calorie badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TechColors.NeonOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, TechColors.NeonOrange.copy(alpha = 0.2f))
                ) {
                    Text(
                        "🔥 ${record.calories} kcal",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = TechColors.NeonOrange,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Nutrient mini bars
                val maxNutrient = maxOf(
                    record.proteinG ?: 0.0,
                    record.carbsG ?: 0.0,
                    record.fatG ?: 0.0,
                    1.0
                )

                if ((record.proteinG ?: 0.0) > 0) {
                    NutrientBar("P", "${"%.0f".format(record.proteinG)}g", record.proteinG!! / maxNutrient, TechColors.NeonBlue)
                }
                if ((record.carbsG ?: 0.0) > 0) {
                    NutrientBar("C", "${"%.0f".format(record.carbsG)}g", record.carbsG!! / maxNutrient, TechColors.NeonPurple)
                }
                if ((record.fatG ?: 0.0) > 0) {
                    NutrientBar("F", "${"%.0f".format(record.fatG)}g", record.fatG!! / maxNutrient, TechColors.NeonGreen)
                }
            }

            // Delete button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFF4757).copy(alpha = 0.1f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color(0xFFFF4757).copy(0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 營養素進度條 */
@Composable
private fun NutrientBar(label: String, value: String, fraction: Double, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.6f))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
fun TrainingPlanDetailScreen(repository: TrainingPlanRepository, planId: String, onBack: () -> Unit) {
    val planFlow = remember(planId) { repository.getPlanFlow(planId) }
    val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(title = "計畫內容", subtitle = plan?.name, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        val p = plan
        if (p == null) {
            EmptyState("找不到計畫", "此計畫可能已被刪除或尚未同步完成。")
            return
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (p.exercises.isEmpty()) Text("此計畫沒有動作。", style = MaterialTheme.typography.bodyMedium)
                else p.exercises.forEach { e -> Text("• ${e.name}", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

@Composable
fun ActivityLogScreen(
    logs: List<ActivityRecord>, // ★★★ FIX: 改為接收 List，避免重新收集
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)?
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        ScreenHeader("訓練紀錄", "瀏覽歷史", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = logs,
                key = { it.id }
            ) { rec: ActivityRecord ->
                ActivityLogCard(rec)
            }
        }
    }
}

@Composable
fun ActivityLogCard(rec: ActivityRecord) {
    Card(modifier = Modifier.fillMaxWidth().glassEffect(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(rec.type, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(4.dp))

            val formattedTime = remember(rec.start) {
                try {
                    val zoneId = ZoneId.systemDefault()
                    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                    rec.start.atZone(zoneId).format(formatter)
                } catch (e: Exception) {
                    rec.start.toString()
                }
            }
            Text(formattedTime, color = MaterialTheme.colorScheme.onBackground.copy(0.6f), style = MaterialTheme.typography.bodySmall)

            if (rec.calories != null && rec.calories > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.calories_consumed, rec.calories.toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun NutritionProgressCard(
    consumedCalories: Float,
    targetCalories: Float,
    consumedProtein: Float,
    targetProtein: Float,
    consumedCarbs: Float = 0f,
    targetCarbs: Float = 0f,
    consumedFat: Float = 0f,
    targetFat: Float = 0f,
    remainingCalories: Float
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Repository 實例建立
    val nutritionRepo = remember { AdvancedNutritionRepository(context) }
    val waterGoal by nutritionRepo.waterGoal.collectAsState(initial = 2000.0)
    var showWaterGoalDialog by remember { mutableStateOf(false) }

    val todayWaterIntakeFlow = remember(nutritionRepo) { nutritionRepo.observeTodayWaterIntake() }
    val todayWaterIntake by todayWaterIntakeFlow.collectAsState(initial = 0.0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 16.dp)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.nutrition_intake),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            NutritionProgressBar(
                label = stringResource(R.string.calories),
                current = consumedCalories,
                target = targetCalories,
                unit = "kcal",
                icon = Icons.Default.LocalFireDepartment,
                color = MaterialTheme.colorScheme.primary,
                showRemaining = true
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )

            Text(
                stringResource(R.string.nutrients),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(0.8f),
                fontSize = 11.sp
            )

            NutritionProgressBar(
                label = stringResource(R.string.protein),
                current = consumedProtein,
                target = targetProtein,
                unit = "g",
                icon = Icons.Default.FitnessCenter,
                color = Color(0xFF00E676),
                showRemaining = false
            )

            if (targetCarbs > 0) {
                NutritionProgressBar(
                    label = stringResource(R.string.carbs),
                    current = consumedCarbs,
                    target = targetCarbs,
                    unit = "g",
                    icon = Icons.Default.Grain,
                    color = Color(0xFFFFD600),
                    showRemaining = false
                )
            }

            if (targetFat > 0) {
                NutritionProgressBar(
                    label = stringResource(R.string.fat),
                    current = consumedFat,
                    target = targetFat,
                    unit = "g",
                    icon = Icons.Default.WaterDrop,
                    color = Color(0xFFFF6B6B),
                    showRemaining = false
                )
            }

            // ===== 水分追蹤（從進階設定移到營養攝取） =====
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.water_tracking),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.8f),
                    fontSize = 11.sp
                )
                TextButton(onClick = { showWaterGoalDialog = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.set_goal), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }

            WaterIntakeCard(
                consumed = todayWaterIntake.toInt(),
                goal = waterGoal.toInt(),
                onAddWater = { amountMl ->
                    scope.launch {
                        Log.d("HomeScreen", "開始添加水分攝取: $amountMl ml")
                        val result = nutritionRepo.logWaterIntake(amountMl.toDouble())
                        if (result.isSuccess) {
                            Log.d("HomeScreen", "水分攝取添加成功: $amountMl ml")
                            Toast.makeText(
                                context,
                                "已記錄 $amountMl ml 水分",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Log.e("HomeScreen", "水分攝取添加失敗: ${result.exceptionOrNull()?.message}")
                            Toast.makeText(
                                context,
                                "水分紀錄失敗：${result.exceptionOrNull()?.message ?: "未知錯誤"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )

            if (remainingCalories > 0) {
                val primaryColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(primaryColor.copy(alpha = 0.1f))
                        .border(0.5.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            stringResource(R.string.can_still_intake, remainingCalories.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                val errorColor = MaterialTheme.colorScheme.error
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(errorColor.copy(alpha = 0.1f))
                        .border(0.5.dp, errorColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = errorColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            stringResource(R.string.over_intake, (-remainingCalories).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }

    if (showWaterGoalDialog) {
        WaterGoalDialog(
            currentGoal = waterGoal.toInt(),
            onGoalSet = { goal ->
                scope.launch {
                    nutritionRepo.setWaterGoal(goal.toDouble())
                    showWaterGoalDialog = false
                }
            },
            onDismiss = { showWaterGoalDialog = false }
        )
    }
}

@Composable
fun NutritionProgressBar(
    label: String,
    current: Float,
    target: Float,
    unit: String,
    icon: ImageVector,
    color: Color,
    showRemaining: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val progress = (current / target).coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt()

    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "progress_animation"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = cs.onBackground,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    current.toInt().toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 12.sp
                )
                Text(
                    "/${target.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onBackground.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cs.outline.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.7f),
                                color,
                                color.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                cs.onBackground.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}