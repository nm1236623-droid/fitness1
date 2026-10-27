package com.example.fitness

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.fitness.util.setupEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.auth.SessionManager
import com.example.fitness.auth.SessionRepository
import com.example.fitness.billing.SubscriptionManager
import com.example.fitness.coach.CoachAuthRepository
import com.example.fitness.coach.cloud.CloudSyncUseCase
import com.example.fitness.data.FirebaseTrainingRecordRepository
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.firebase.AuthRepository
import com.example.fitness.localization.LocaleManager
import com.example.fitness.ui.HomeScreen
import com.example.fitness.ui.PaywallScreen
import com.example.fitness.ui.SignInScreen
import com.example.fitness.ui.theme.ColorScheme
import com.example.fitness.ui.theme.FitnessTheme
import com.example.fitness.ui.theme.ThemeManager
import com.example.fitness.ui.theme.ThemeMode
import com.example.fitness.user.UserRoleProfileRepository
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

// ★ UMP 新增的 Imports
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val repository = TrainingPlanRepository()
    private val trainingRecordRepository = FirebaseTrainingRecordRepository()
    private val activityRepo by lazy { ActivityLogRepository(context = applicationContext, useFirebase = true) }
    private val authRepository = AuthRepository()
    private val sessionRepository by lazy { SessionRepository(this.applicationContext) }
    private val sessionManager by lazy { SessionManager(authRepository, sessionRepository) }

    private val revenueCatApiKey = "goog_VamwiUCRZkIZdyEueLrLfMmiPMq"

    // ★ UMP 新增：同意聲明資訊與確保廣告只初始化一次的變數
    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitializeCalled = AtomicBoolean(false)

    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager.getInstance(newBase)
        val currentLanguage = localeManager.getCurrentLanguageSync()
        val context = localeManager.applyLanguage(newBase, currentLanguage)
        super.attachBaseContext(context)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setupEdgeToEdge()
        super.onCreate(savedInstanceState)


        // ★ UMP 修改：移除原本直接呼叫的 MobileAds.initialize，改為先請求 UMP 同意聲明

        // 1. 設定測試裝置 (模擬在歐洲地區，確保視窗會彈出)
        val debugSettings = ConsentDebugSettings.Builder(this)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            // TODO: 請在執行後查看 Logcat，搜尋 "UserMessagingPlatform"，並將您的模擬器 Hash ID 貼在這裡替換
            .addTestDeviceHashedId("TEST-DEVICE-HASHED-ID")
            .build()

        val params = ConsentRequestParameters.Builder()
            .setConsentDebugSettings(debugSettings) // 實際上線到 Google Play 時，請務必將這行移除
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        // 2. 請求同意聲明狀態
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                // 狀態更新成功，載入並顯示表單 (如果需要的話)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    if (loadAndShowError != null) {
                        Log.w("UMP_AdMob", "${loadAndShowError.errorCode}: ${loadAndShowError.message}")
                    }

                    // 3. 表單關閉後 (無論同意或拒絕)，檢查是否可以載入廣告
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk()
                    }
                }
            },
            { requestConsentError ->
                Log.w("UMP_AdMob", "${requestConsentError.errorCode}: ${requestConsentError.message}")
            }
        )

        // 如果使用者在之前的 Session 已經同意過，我們可以提早初始化廣告
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk()
        }
        // ★ UMP 設定結束

        SubscriptionManager.initialize(
            context = this,
            apiKey = revenueCatApiKey,
            userId = authRepository.currentUser()?.uid
        )

        setContent {
            // ... (下方的 Jetpack Compose UI 程式碼完全保持原樣，不需更動)
            val windowSizeClass = calculateWindowSizeClass(this)
            val widthSizeClass = windowSizeClass.widthSizeClass
            val context = LocalContext.current
            val themeManager = remember { ThemeManager.getInstance(context) }

            val themeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.AUTO)
            val colorScheme by themeManager.colorScheme.collectAsState(initial = ColorScheme.NEON_BLUE)

            FitnessTheme(
                themeMode = themeMode,
                colorScheme = colorScheme,
                dynamicColor = false
            ) {
                val scope = rememberCoroutineScope()
                val appCtx = context.applicationContext

                val credentialManager = remember { CredentialManager.create(context) }
                val firebaseUser = remember { FirebaseAuth.getInstance().currentUser }
                var isSignedIn by rememberSaveable { mutableStateOf(firebaseUser != null) }

                val isPro by SubscriptionManager.isPro.collectAsState()
                var showPaywall by rememberSaveable { mutableStateOf(false) }

                val cloudSync = remember { CloudSyncUseCase(CoachAuthRepository(appCtx), repository) }

                LaunchedEffect(firebaseUser) {
                    if (firebaseUser != null) {
                        isSignedIn = true
                        sessionManager.touchIfSignedIn()
                        SubscriptionManager.initialize(appCtx, revenueCatApiKey, firebaseUser.uid)
                    }
                }

                val auth = FirebaseAuth.getInstance()
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        scope.launch {
                            val currentUser = firebaseAuth.currentUser
                            isSignedIn = currentUser != null
                            if (isSignedIn) {
                                sessionManager.touchIfSignedIn()
                            }
                        }
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                LaunchedEffect(isSignedIn) {
                    if (isSignedIn) {
                        sessionManager.touchIfSignedIn()
                        cloudSync.startAutoReconnectSync()
                    } else {
                        cloudSync.stopListening()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    if (!isSignedIn) {
                        SignInScreen(
                            modifier = Modifier.padding(innerPadding),
                            authRepository = authRepository,
                            onSignedIn = {
                                scope.launch {
                                    sessionManager.touchIfSignedIn()
                                    isSignedIn = true
                                    authRepository.currentUser()?.uid?.let { uid ->
                                        SubscriptionManager.initialize(context, revenueCatApiKey, uid)
                                    }
                                }
                            },
                            onGoogleSignIn = { selectedRole ->
                                lifecycleScope.launch {
                                    handleGoogleSignIn(context, credentialManager, auth, selectedRole ?: com.example.fitness.coach.UserRole.TRAINEE) {
                                        scope.launch {
                                            sessionManager.touchIfSignedIn()
                                            isSignedIn = true
                                        }
                                    }
                                }
                            },
                            onFacebookSignIn = { selectedRole ->
                                lifecycleScope.launch {
                                    handleFacebookSignIn(this@MainActivity, authRepository, selectedRole ?: com.example.fitness.coach.UserRole.TRAINEE) {
                                        scope.launch {
                                            sessionManager.touchIfSignedIn()
                                            isSignedIn = true
                                        }
                                    }
                                }
                            },
                            onPhoneSignIn = {
                                val intent = Intent(context, PhoneSignInActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                    } else {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            HomeScreen(
                                repository = repository,
                                activityRepository = activityRepo,
                                trainingRecordRepository = trainingRecordRepository,
                                modifier = Modifier.fillMaxSize(),
                                isPro = isPro,
                                onShowPaywall = { showPaywall = true },
                                widthSizeClass = widthSizeClass
                            )

                            if (showPaywall) {
                                PaywallScreen(
                                    onDismiss = { showPaywall = false },
                                    onPurchaseSuccess = {
                                        showPaywall = false
                                        Toast.makeText(context, "Welcome to Pro!", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (authRepository.currentUser() != null) {
                sessionManager.touchIfSignedIn()
            }
        }
    }

    // ★ UMP 新增：將原本直接呼叫的 AdMob 初始化邏輯移到獨立的方法中
    private fun initializeMobileAdsSdk() {
        // 確保 SDK 只會被初始化一次
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        MobileAds.initialize(this) { }

        // 保留您原本設定 Emulator 為測試裝置的邏輯
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
            .build()
        MobileAds.setRequestConfiguration(configuration)

        Log.d("UMP_AdMob", "AdMob SDK Initialized Successfully")
    }

    private suspend fun handleFacebookSignIn(
        activity: android.app.Activity,
        authRepository: AuthRepository,
        selectedRole: com.example.fitness.coach.UserRole,
        onSuccess: () -> Unit
    ) {
        val result = authRepository.firebaseSignInWithFacebook(activity)
        if (result.isSuccess && result.getOrNull() != null) {
            val user = result.getOrNull()!!
            val roleRepo = UserRoleProfileRepository()
            roleRepo.upsertMyRole(role = selectedRole, nickname = user.displayName ?: "")
            onSuccess()
        } else {
            val err = result.exceptionOrNull()?.localizedMessage
            if (err != null && !err.contains("canceled", ignoreCase = true) && !err.contains("cancel", ignoreCase = true)) {
                Toast.makeText(activity, "Facebook 登入失敗: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun handleGoogleSignIn(
        context: android.content.Context,
        credentialManager: CredentialManager,
        auth: FirebaseAuth,
        selectedRole: com.example.fitness.coach.UserRole,
        onSuccess: () -> Unit
    ) {
        // ... (下方的 Google 登入邏輯保持原樣，不需更動)
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                val authResult = auth.signInWithCredential(firebaseCredential).await()

                if (authResult.user != null) {
                    val user = authResult.user!!
                    val roleRepo = UserRoleProfileRepository()

                    val uid = user.uid
                    val docSnapshot = com.google.firebase.firestore.FirebaseFirestore
                        .getInstance()
                        .collection("users")
                        .document(uid)
                        .get()
                        .await()
                    val isNewUser = !docSnapshot.exists()

                    if (isNewUser) {
                        val saveResult = roleRepo.upsertMyRole(
                            role = selectedRole,
                            nickname = user.displayName
                        )

                        if (saveResult.isSuccess) {
                            Log.d("Auth", "New user role saved: $selectedRole")
                        } else {
                            Log.e("Auth", "Failed to save user role", saveResult.exceptionOrNull())
                        }
                    } else {
                        Log.d("Auth", "Existing user, keeping current role")
                    }

                    SubscriptionManager.initialize(context, revenueCatApiKey, user.uid)
                    SubscriptionManager.checkSubscriptionStatus()

                    sessionManager.touchIfSignedIn()

                    onSuccess()

                    val welcomeMsg = if (isNewUser) {
                        "歡迎加入！您的角色：${if (selectedRole == com.example.fitness.coach.UserRole.COACH) "教練" else "學員"}"
                    } else {
                        "歡迎回來，${user.displayName ?: "用戶"}！"
                    }
                    Toast.makeText(context, welcomeMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Firebase 驗證失敗", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d("Auth", "User cancelled Google Sign-In")
            Toast.makeText(context, "取消登入", Toast.LENGTH_SHORT).show()
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Credential error", e)
            Toast.makeText(context, "憑證錯誤：${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In Error", e)
            Toast.makeText(context, "登入錯誤：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}