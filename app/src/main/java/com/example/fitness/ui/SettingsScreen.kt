package com.example.fitness.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.fitness.data.SyncStrategy
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.user.UserProfileRepository
import kotlinx.coroutines.launch

object SecurePrefs {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_GEMINI = "gemini_api_key"
    private const val KEY_EDAMAM_ID = "edamam_app_id"
    private const val KEY_EDAMAM_KEY = "edamam_app_key"

    fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun readGeminiKey(context: Context): String = getEncryptedPrefs(context).getString(KEY_GEMINI, "") ?: ""
    fun writeGeminiKey(context: Context, value: String) = getEncryptedPrefs(context).edit().putString(KEY_GEMINI, value).apply()

    fun readEdamamId(context: Context): String = getEncryptedPrefs(context).getString(KEY_EDAMAM_ID, "") ?: ""
    fun writeEdamamId(context: Context, value: String) = getEncryptedPrefs(context).edit().putString(KEY_EDAMAM_ID, value).apply()

    fun readEdamamKey(context: Context): String = getEncryptedPrefs(context).getString(KEY_EDAMAM_KEY, "") ?: ""
    fun writeEdamamKey(context: Context, value: String) = getEncryptedPrefs(context).edit().putString(KEY_EDAMAM_KEY, value).apply()
}

@Composable
fun SettingsScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Repository Init
    val userRepo = remember { UserProfileRepository(ctx) }

    // States
    var geminiKey by remember { mutableStateOf(SecurePrefs.readGeminiKey(ctx)) }
    var edaId by remember { mutableStateOf(SecurePrefs.readEdamamId(ctx)) }
    var edaKey by remember { mutableStateOf(SecurePrefs.readEdamamKey(ctx)) }

    var currentStrategy by remember { mutableStateOf(SyncStrategy.Strategy.LOCAL_ONLY) }
    var useOfflineCache by remember { mutableStateOf(true) }
    var useFirebase by remember { mutableStateOf(false) }

    var nickname by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }

    // Load Data
    LaunchedEffect(ctx) {
        launch { SyncStrategy.getCurrentStrategy(ctx).collect { currentStrategy = it } }
        launch { SyncStrategy.getUseOfflineCache(ctx).collect { useOfflineCache = it } }
        launch { SyncStrategy.getUseFirebase(ctx).collect { useFirebase = it } }

        launch { userRepo.nickname.collect { nickname = it } }
        launch { userRepo.age.collect { ageVal -> age = if (ageVal > 0) ageVal.toString() else "" } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsScreenHeader(title = "系統設定", subtitle = "個人資料與系統偏好", onBack = onDone)
            Spacer(modifier = Modifier.height(20.dp))

            // Profile Section
            SectionCard(title = "個人資料設定") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeonOutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = "暱稱",
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    NeonOutlinedTextField(
                        value = age,
                        onValueChange = { if (it.all { char -> char.isDigit() }) age = it },
                        label = "年齡",
                        leadingIcon = { Icon(Icons.Default.Cake, null, tint = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sync Strategy Section
            SectionCard(title = "數據同步策略") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("選擇您的數據存儲和同步方式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Firebase 雲端同步", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                            Text("啟用數據雲端備份與多設備同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = useFirebase,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SyncStrategy.setUseFirebase(ctx, enabled)
                                    useFirebase = enabled
                                    val newStrategy = if (enabled) {
                                        if (useOfflineCache) SyncStrategy.Strategy.FIREFIRST_LOCAL_FALLBACK
                                        else SyncStrategy.Strategy.FIREBASE_ONLY
                                    } else {
                                        SyncStrategy.Strategy.LOCAL_ONLY
                                    }
                                    SyncStrategy.setStrategy(ctx, newStrategy)
                                    currentStrategy = newStrategy
                                    snackbarHostState.showSnackbar("Firebase 同步已${if (enabled) "啟用" else "停用"}")
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }

                    if (useFirebase) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SyncStrategy.Strategy.entries.forEach { strategy ->
                            if (strategy != SyncStrategy.Strategy.LOCAL_ONLY) {
                                SyncStrategyOption(strategy = strategy, isSelected = currentStrategy == strategy) {
                                    scope.launch {
                                        SyncStrategy.setStrategy(ctx, strategy)
                                        currentStrategy = strategy
                                        snackbarHostState.showSnackbar("同步策略已更新")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("離線緩存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                            Text("在無網路時使用本地數據", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = useOfflineCache,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SyncStrategy.setUseOfflineCache(ctx, enabled)
                                    useOfflineCache = enabled
                                    snackbarHostState.showSnackbar("離線緩存設定已更新")
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // API Keys Section
            SectionCard(title = "API 金鑰設定") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeonOutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = "Gemini API Key", leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary) })
                    NeonOutlinedTextField(value = edaId, onValueChange = { edaId = it }, label = "Edamam App ID", leadingIcon = { Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary) })
                    NeonOutlinedTextField(value = edaKey, onValueChange = { edaKey = it }, label = "Edamam App Key", leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Text("取消", color = MaterialTheme.colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        scope.launch {
                            SecurePrefs.writeGeminiKey(ctx, geminiKey)
                            SecurePrefs.writeEdamamId(ctx, edaId)
                            SecurePrefs.writeEdamamKey(ctx, edaKey)

                            userRepo.setNickname(nickname)
                            userRepo.setAge(age.toIntOrNull() ?: 0)

                            snackbarHostState.showSnackbar("設定已安全儲存")
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp).neonGlowBorder(cornerRadius = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("儲存設定", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().glassEffect(12.dp).padding(16.dp)) {
                Text("說明: 暱稱與年齡將用於計算個人化健康建議。API Keys 均經過加密儲存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
            Snackbar(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(12.dp)) {
                Text(data.visuals.message, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Components ---

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(20.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SyncStrategyOption(strategy: SyncStrategy.Strategy, isSelected: Boolean, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, title, description) = when (strategy) {
        SyncStrategy.Strategy.LOCAL_ONLY -> Triple(Icons.Default.CloudOff, "僅本地存儲", "數據僅儲存在本機")
        SyncStrategy.Strategy.FIREBASE_ONLY -> Triple(Icons.Default.Cloud, "僅雲端存儲", "數據僅儲存在 Firebase")
        SyncStrategy.Strategy.FIREFIRST_LOCAL_FALLBACK -> Triple(Icons.Default.CloudSync, "雲端優先", "優先使用雲端，離線使用本地")
        SyncStrategy.Strategy.LOCAL_FIREBASE_SYNC -> Triple(Icons.Default.Sync, "雙向同步", "本地和雲端保持同步")
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().let { if (isSelected) it.neonGlowBorder(cornerRadius = 12.dp) else it },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) cs.primary.copy(alpha = 0.2f) else cs.surfaceVariant),
        border = BorderStroke(1.dp, if (isSelected) cs.primary else cs.outline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSelected) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) cs.primary else cs.onBackground, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (isSelected) Icon(Icons.Default.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = cs.primary) }
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onBackground)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NeonOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = cs.onBackground,
            unfocusedTextColor = cs.onBackground,
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.primary.copy(alpha = 0.5f),
            focusedLabelColor = cs.primary,
            unfocusedLabelColor = cs.onSurfaceVariant,
            cursorColor = cs.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}