package com.example.fitness.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.R
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.user.FirebaseUserProfileFirestoreRepository
import com.example.fitness.user.FirebaseUserProfileRepository
import com.example.fitness.user.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBasicSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repo: FirebaseUserProfileFirestoreRepository = remember { FirebaseUserProfileFirestoreRepository() },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cs = MaterialTheme.colorScheme

    val profileFlow = remember(repo) {
        repo.observeMyProfile().catch { emit(UserProfile()) }
    }
    val remoteProfile by profileFlow.collectAsStateWithLifecycle(initialValue = UserProfile())

    // ── 頭像編輯 ──
    val context = LocalContext.current
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarChooserOpen by remember { mutableStateOf(false) }
    var currentAvatarUri by remember(remoteProfile.avatarUri) { mutableStateOf(remoteProfile.avatarUri) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) avatarBitmap = bmp
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                @Suppress("DEPRECATION")
                avatarBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } catch (_: Exception) { }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    /** 上傳頭像到 Firebase Storage 並回傳 downloadUrl */
    suspend fun uploadAvatar(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()
            val ref = FirebaseStorage.getInstance().reference.child("user_avatars/$uid/avatar.jpg")
            ref.putBytes(data).await()
            ref.downloadUrl.await().toString()
        } catch (_: Throwable) {
            null
        }
    }

    // 表單狀態
    var nickname by remember(remoteProfile.nickname) { mutableStateOf(remoteProfile.nickname) }
    var ageText by remember(remoteProfile.age) { mutableStateOf(remoteProfile.age.takeIf { it > 0 }?.toString().orEmpty()) }
    var weightText by remember(remoteProfile.weightKg) { mutableStateOf(remoteProfile.weightKg.toString()) }
    var heightText by remember(remoteProfile.heightCm) { mutableStateOf(remoteProfile.heightCm.toString()) }
    var tdeeText by remember(remoteProfile.tdee) { mutableStateOf(remoteProfile.tdee.toString()) }
    var proteinGoalText by remember(remoteProfile.proteinGoalGrams) { mutableStateOf(remoteProfile.proteinGoalGrams.toString()) }

    fun validateAndBuild(): UserProfile? {
        val age = ageText.trim().toIntOrNull() ?: 0
        val weight = weightText.trim().toFloatOrNull() ?: return null
        val height = heightText.trim().toFloatOrNull() ?: return null
        val tdee = tdeeText.trim().toIntOrNull() ?: return null
        val protein = proteinGoalText.trim().toFloatOrNull() ?: return null

        if (weight <= 0f || height <= 0f || tdee <= 0 || protein < 0f) return null

        return UserProfile(
            nickname = nickname.trim(),
            age = age,
            weightKg = weight,
            heightCm = height,
            tdee = tdee,
            proteinGoalGrams = protein,
            avatarUri = currentAvatarUri,
        )
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = cs.onSurface,
        unfocusedTextColor = cs.onSurface,
        disabledTextColor = cs.onSurface.copy(alpha = 0.5f),
        focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.2f),
        unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.1f),
        cursorColor = cs.primary,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.outline.copy(alpha = 0.5f),
        focusedLabelColor = cs.primary,
        unfocusedLabelColor = cs.onSurfaceVariant,
        focusedLeadingIconColor = cs.primary,
        unfocusedLeadingIconColor = cs.onSurfaceVariant.copy(alpha = 0.8f),
    )
    val tfShape = RoundedCornerShape(14.dp)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .background(cs.background)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════ Header ═══════
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.1f)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = cs.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.profile_basic_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSurface
                        )
                        Text(
                            stringResource(R.string.basic_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            // ═══════ Avatar Section ═══════
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(vertical = 28.dp, horizontal = 16.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar with neon glow
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .neonGlowBorder(cornerRadius = 52.dp, glowColor = cs.primary)
                                .clip(CircleShape)
                                .background(cs.primary.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                avatarBitmap != null -> {
                                    Image(
                                        bitmap = avatarBitmap!!.asImageBitmap(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                !currentAvatarUri.isNullOrBlank() -> {
                                    coil.compose.AsyncImage(
                                        model = currentAvatarUri,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.Person, "Placeholder",
                                        tint = cs.primary.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            if (isUploadingAvatar) {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = cs.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        // Edit button
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cs.primary)
                                .clickable { avatarChooserOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, "Edit avatar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (nickname.isNotBlank()) nickname else stringResource(R.string.nickname),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground
                    )
                    Text(
                        FirebaseAuth.getInstance().currentUser?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onBackground.copy(alpha = 0.4f)
                    )
                }
            }

            // ═══════ Basic Info Section ═══════
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) { Text("👤", fontSize = 16.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.basic_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    }

                    OutlinedTextField(
                        value = nickname, onValueChange = { nickname = it },
                        label = { Text(stringResource(R.string.nickname)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                    OutlinedTextField(
                        value = ageText, onValueChange = { ageText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.age)) },
                        leadingIcon = { Icon(Icons.Default.Cake, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                }
            }

            // ═══════ Body Data Section ═══════
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) { Text("📏", fontSize = 16.sp) }
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.body_data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        }

                        // ★ Sync from Health Connect Button
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val hcManager = com.example.fitness.health.HealthConnectManager(context)
                                    if (!hcManager.isAvailable()) {
                                        snackbarHostState.showSnackbar("此設備不支援 Health Connect，或尚未安裝。")
                                        return@launch
                                    }
                                    if (!hcManager.hasAllPermissions()) {
                                        snackbarHostState.showSnackbar("請先授予存取體重之權限。")
                                        return@launch
                                    }
                                    val weight = hcManager.readLatestWeight()
                                    if (weight != null) {
                                        weightText = "%.1f".format(weight)
                                        snackbarHostState.showSnackbar("✅ 已成功從 Health Connect 同步體重！")
                                    } else {
                                        snackbarHostState.showSnackbar("找不到近期的體重紀錄，請確認 Health Connect 內有資料。")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("健康同步", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedTextField(
                        value = weightText, onValueChange = { weightText = it },
                        label = { Text(stringResource(R.string.weight_kg_label)) },
                        leadingIcon = { Icon(Icons.Default.MonitorWeight, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                    OutlinedTextField(
                        value = heightText, onValueChange = { heightText = it },
                        label = { Text(stringResource(R.string.height_cm_label)) },
                        leadingIcon = { Icon(Icons.Default.Height, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                }
            }

            // ═══════ Nutrition Goal Section ═══════
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(TechColors.NeonOrange.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🔥", fontSize = 16.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.nutrition_goal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
                    }

                    OutlinedTextField(
                        value = tdeeText, onValueChange = { tdeeText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.daily_calorie_goal)) },
                        leadingIcon = { Icon(Icons.Default.LocalFireDepartment, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                    OutlinedTextField(
                        value = proteinGoalText, onValueChange = { proteinGoalText = it },
                        label = { Text(stringResource(R.string.protein_goal_label)) },
                        leadingIcon = { Icon(Icons.Default.FitnessCenter, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors, shape = tfShape,
                    )
                }
            }

            // ═══════ Save Button ═══════
            val checkInputMsg = stringResource(R.string.check_input_format)
            val savedMsg = stringResource(R.string.saved)
            val saveFailedMsg = stringResource(R.string.save_failed_msg, "")
            var isSaving by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    val newProfile = validateAndBuild()
                    if (newProfile == null) {
                        scope.launch { snackbarHostState.showSnackbar(checkInputMsg) }
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        try {
                            var profileToSave = newProfile
                            val bmpToUpload = avatarBitmap
                            if (bmpToUpload != null) {
                                isUploadingAvatar = true
                                try {
                                    val downloadUrl = uploadAvatar(bmpToUpload)
                                    if (downloadUrl != null) {
                                        profileToSave = newProfile.copy(avatarUri = downloadUrl)
                                        currentAvatarUri = downloadUrl
                                        try {
                                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                .setDisplayName(profileToSave.nickname)
                                                .setPhotoUri(android.net.Uri.parse(downloadUrl))
                                                .build()
                                            FirebaseAuth.getInstance().currentUser?.updateProfile(profileUpdates)?.await()
                                        } catch (_: Throwable) {}
                                    }
                                } catch (_: Throwable) {}
                                isUploadingAvatar = false
                                avatarBitmap = null
                            }
                            val result = repo.saveMyProfile(profileToSave)
                            if (result.isSuccess) {
                                FirebaseUserProfileRepository.saveProfile(profileToSave)
                                snackbarHostState.showSnackbar(savedMsg)
                            } else {
                                snackbarHostState.showSnackbar(saveFailedMsg.replace(": ", ": ${result.exceptionOrNull()?.localizedMessage ?: ""}"))
                            }
                        } catch (e: Throwable) {
                            snackbarHostState.showSnackbar(saveFailedMsg.replace(": ", ": ${e.localizedMessage ?: ""}"))
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .neonGlowBorder(cornerRadius = 16.dp, glowColor = cs.primary),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary.copy(alpha = 0.15f),
                    contentColor = cs.primary,
                    disabledContainerColor = cs.onBackground.copy(alpha = 0.05f)
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = cs.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Text(
                text = stringResource(R.string.profile_hint),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Avatar chooser dialog ──
    if (avatarChooserOpen) {
        AlertDialog(
            onDismissRequest = { avatarChooserOpen = false },
            containerColor = cs.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📷", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("更換頭像", fontWeight = FontWeight.ExtraBold, color = cs.primary, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Camera
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                avatarChooserOpen = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            .background(cs.primary.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                                .background(cs.primary.copy(alpha = 0.12f))
                                .border(1.dp, cs.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = cs.primary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("拍照", color = cs.onBackground, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    // Gallery
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                avatarChooserOpen = false
                                galleryLauncher.launch("image/*")
                            }
                            .background(TechColors.NeonGreen.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                                .background(TechColors.NeonGreen.copy(alpha = 0.12f))
                                .border(1.dp, TechColors.NeonGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = TechColors.NeonGreen, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("相簿", color = cs.onBackground, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { avatarChooserOpen = false }) {
                    Text("取消", color = cs.onBackground.copy(0.5f))
                }
            }
        )
    }
}
