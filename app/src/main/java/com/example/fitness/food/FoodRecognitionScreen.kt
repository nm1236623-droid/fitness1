package com.example.fitness.food

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.data.DietRecord
import com.example.fitness.data.FirebaseDietRecordRepository
import com.example.fitness.ui.SecurePrefs
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// Data model
data class FoodAnalysis(
    val name: String,
    val calories: Double?,
    val nutrients: Map<String, Double>,
    val items: List<String>? = null,
    val rawText: String? = null
)

@Composable
fun FoodRecognitionScreenOptimized(activityRepository: ActivityLogRepository, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val dietRepository = remember { FirebaseDietRecordRepository }

    // 初始化與模型檢查
    LaunchedEffect(Unit) {
        initGlobalContext(context)
        FoodRecognitionService.debugListModels(context)
        // ★ 確保飲食紀錄 listener 已啟動
        FirebaseDietRecordRepository.listenForUpdates()
    }

    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var bitmapAvailable by remember { mutableStateOf(false) }

    var analyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<FoodAnalysis?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savedToLog by remember { mutableStateOf(false) }

    // portion controls
    var portionQuantity by remember { mutableStateOf(1f) }
    val portionUnit by remember { mutableStateOf("serving") }

    // time and meal type controls
    val selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val selectedTime by remember { mutableStateOf(LocalTime.now()) }

    // Localized meal types
    val breakfastStr = stringResource(R.string.breakfast)
    val lunchStr = stringResource(R.string.lunch)
    val dinnerStr = stringResource(R.string.dinner)
    val snackStr = stringResource(R.string.snack)
    val mealTypes = remember(breakfastStr, lunchStr, dinnerStr, snackStr) {
        listOf(breakfastStr, lunchStr, dinnerStr, snackStr)
    }
    var mealType by remember { mutableStateOf(lunchStr) }

    // permission
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    // CameraX imageCapture holder
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Localized error messages
    val recognitionFailedStr = stringResource(R.string.recognition_failed)
    val readPhotoFailedStr = stringResource(R.string.read_photo_failed)
    val recognitionErrorStr = stringResource(R.string.recognition_error)

    // gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            analysisResult = null
            errorMsg = null
            savedToLog = false
            try {
                context.contentResolver.openInputStream(it)?.use { ins ->
                    val bmp = BitmapFactory.decodeStream(ins)
                    previewBitmap = bmp
                    bitmapAvailable = bmp != null
                }
                // analyze in coroutine
                coroutineScope.launch {
                    analyzing = true
                    try {
                        context.contentResolver.openInputStream(it)?.use { ins ->
                            var res = FoodRecognitionService.analyzeImageWithGemini(ins, context, SecurePrefs.readGeminiKey(context))
                                if (res != null) {
                                    if (res.calories == null) {
                                        val kcal = FoodRecognitionService.fetchCaloriesFromEdamam(res.name, context)
                                        if (kcal != null) res = res.copy(calories = kcal)
                                    }
                                    analysisResult = res
                                    // 5秒後自動跳出儲存對話框
                                    kotlinx.coroutines.delay(5000L)
                                    if (analysisResult != null && !savedToLog) {
                                        showSaveDialog = true
                                    }
                                } else errorMsg = recognitionFailedStr
                        } ?: run { errorMsg = readPhotoFailedStr }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMsg = "$recognitionErrorStr：${e.message}"
                    }
                    finally { analyzing = false }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                errorMsg = readPhotoFailedStr
            }
        }
    }

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
            modifier = modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🎯 頂部標題框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.food_recognition),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.ai_nutrition_analysis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }

                    if (savedToLog) {
                        Box(
                            modifier = Modifier
                                .background(
                                    TechColors.NeonGreen.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.saved_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = TechColors.NeonGreen
                            )
                        }
                    }
                }
            }

            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .glassEffect(cornerRadius = 16.dp)
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.need_camera_permission),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.camera_permission_reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .neonGlowBorder(cornerRadius = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechColors.NeonBlue.copy(alpha = 0.2f),
                                contentColor = TechColors.NeonBlue
                            )
                        ) {
                            Text(stringResource(R.string.grant_camera_permission), color = Color.White)
                        }
                    }
                }
                return@Column
            }

            // 📸 相機預覽
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .glassEffect(cornerRadius = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val previewView = remember { PreviewView(context) }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 相機中央指引框
                    if (!analyzing && analysisResult == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .drawBehind {
                                        val strokeWidth = 2.dp.toPx()
                                        drawRoundRect(
                                            color = TechColors.NeonBlue,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                        )
                                    }
                            )

                            Box(
                                modifier = Modifier
                                    .offset(y = 80.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.place_food_in_frame),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 分析中指示器
                    if (analyzing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = TechColors.NeonBlue,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    stringResource(R.string.ai_analyzing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                DisposableEffect(previewView, lifecycleOwner) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                    val listener = Runnable {
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val previewUseCase = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val ic = ImageCapture.Builder()
                                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                                .build()
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase,
                                ic
                            )
                            imageCapture = ic
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                            errorMsg = context.getString(R.string.camera_start_failed) + ": ${exc.message}"
                        }
                    }
                    cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(previewView.context))
                    onDispose {
                        try {
                            if (cameraProviderFuture.isDone) {
                                val cp = cameraProviderFuture.get()
                                cp?.unbindAll()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // 📸 拍照/相簿按鈕 (中間)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !analyzing,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gallery), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val ic = imageCapture
                        if (ic == null) {
                            errorMsg = context.getString(R.string.camera_not_ready)
                            return@Button
                        }
                        savedToLog = false
                        analysisResult = null
                        errorMsg = null

                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        val executor = ContextCompat.getMainExecutor(context)

                        ic.takePicture(outputOptions, executor, object: ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                try {
                                    file.inputStream().use { ins ->
                                        val bmp = BitmapFactory.decodeStream(ins)
                                        previewBitmap = bmp
                                        bitmapAvailable = bmp != null
                                    }

                                    coroutineScope.launch {
                                        analyzing = true
                                        try {
                                            file.inputStream().use { ins ->
                                                var res = FoodRecognitionService.analyzeImageWithGemini(
                                                    ins,
                                                    context,
                                                    SecurePrefs.readGeminiKey(context)
                                                )
                                                if (res != null) {
                                                    if (res.calories == null) {
                                                        val kcal = FoodRecognitionService.fetchCaloriesFromEdamam(
                                                            res.name,
                                                            context
                                                        )
                                                        if (kcal != null) res = res.copy(calories = kcal)
                                                    }
                                                    analysisResult = res
                                                    // 5秒後自動跳出儲存對話框
                                                    kotlinx.coroutines.delay(5000L)
                                                    if (analysisResult != null && !savedToLog) {
                                                        showSaveDialog = true
                                                    }
                                                } else {
                                                    errorMsg = context.getString(R.string.recognition_failed)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            errorMsg = context.getString(R.string.recognition_error) + "：${e.message}"
                                        } finally {
                                            analyzing = false
                                        }
                                    }
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    errorMsg = context.getString(R.string.read_photo_failed)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                                errorMsg = context.getString(R.string.photo_failed) + ": ${exception.message}"
                            }
                        })
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .neonGlowBorder(cornerRadius = 16.dp, glowColor = MaterialTheme.colorScheme.primary),
                    enabled = !analyzing,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }
            }

            // 🖼️ 拍照圖片顯示區域
            if (bitmapAvailable && previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .glassEffect(cornerRadius = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 📊 分析結果顯示 (底部Overlay)
            analysisResult?.let { r ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .glassEffect(cornerRadius = 20.dp)
                        .border(
                            width = 1.dp,
                            color = TechColors.NeonBlue.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    val cs = MaterialTheme.colorScheme
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 食物名稱
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.recognition_result),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = r.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = cs.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 營養資訊卡片 (動態顯示：數值 * portionQuantity)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NutritionCardHighContrast(
                                icon = "🔥",
                                label = stringResource(R.string.calorie),
                                value = r.calories?.let { String.format("%.0f", it * portionQuantity) } ?: "?",
                                unit = "kcal",
                                modifier = Modifier.weight(1f),
                                bgColor = Color(0xFFFF6B35).copy(alpha = 0.2f),
                                borderColor = Color(0xFFFF6B35)
                            )

                            val protein = r.nutrients["protein"]
                            NutritionCardHighContrast(
                                icon = "💪",
                                label = stringResource(R.string.protein_label),
                                value = protein?.let { String.format("%.1f", it * portionQuantity) } ?: "?",
                                unit = "g",
                                modifier = Modifier.weight(1f),
                                bgColor = Color(0xFF4ECDC4).copy(alpha = 0.2f),
                                borderColor = Color(0xFF4ECDC4)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fat = r.nutrients["fat"]
                            NutritionCardHighContrast(
                                icon = "🥑",
                                label = stringResource(R.string.fat_label),
                                value = fat?.let { String.format("%.1f", it * portionQuantity) } ?: "?",
                                unit = "g",
                                modifier = Modifier.weight(1f),
                                bgColor = Color(0xFF95E77E).copy(alpha = 0.2f),
                                borderColor = Color(0xFF95E77E)
                            )

                            val carbs = r.nutrients["carbs"]
                            NutritionCardHighContrast(
                                icon = "🌾",
                                label = stringResource(R.string.carbs_label),
                                value = carbs?.let { String.format("%.1f", it * portionQuantity) } ?: "?",
                                unit = "g",
                                modifier = Modifier.weight(1f),
                                bgColor = Color(0xFFA78BFA).copy(alpha = 0.2f),
                                borderColor = Color(0xFFA78BFA)
                            )
                        }

                        // 份量調整滑桿
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    cs.primary.copy(alpha = 0.06f),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    cs.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.portion_adjust),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onBackground,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${(portionQuantity * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Slider(
                                    value = portionQuantity,
                                    onValueChange = { portionQuantity = it },
                                    valueRange = 0.1f..3f,
                                    steps = 29,
                                    colors = SliderDefaults.colors(
                                        thumbColor = cs.primary,
                                        activeTrackColor = cs.primary,
                                        inactiveTrackColor = cs.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ★★★★★ 自適應寬度的底部按鈕 (返回 & 儲存) ★★★★★
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. 左側：返回/取消
                            OutlinedButton(
                                onClick = {
                                    analysisResult = null
                                    savedToLog = false
                                    previewBitmap = null
                                    bitmapAvailable = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = cs.onBackground
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Return",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.back),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 2. 右側：儲存
                            Button(
                                onClick = {
                                    if (!savedToLog) {
                                        showSaveDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                // 已儲存後鎖定
                                enabled = !savedToLog,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (savedToLog) TechColors.NeonGreen.copy(alpha = 0.9f) else TechColors.NeonBlue,
                                    disabledContainerColor = TechColors.NeonGreen.copy(alpha = 0.6f),
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (savedToLog) Icons.Default.Check else Icons.Default.Save,
                                    contentDescription = "Save",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (savedToLog) stringResource(R.string.saved) else stringResource(R.string.save),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 錯誤訊息
            errorMsg?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Color(0xFFFF4757).copy(alpha = 0.15f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFF4757).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = "⚠️ $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }

    // 儲存對話框 (確認視窗，會顯示乘上倍率後的數值)
    if (showSaveDialog && analysisResult != null) {
        val dialogCs = MaterialTheme.colorScheme
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = {
                    Text(
                        stringResource(R.string.save_to_log),
                        color = dialogCs.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.food_label, analysisResult!!.name),
                            color = dialogCs.onBackground.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )

                        analysisResult!!.calories?.let { calories ->
                            Text(
                                stringResource(R.string.calorie_label, String.format("%.0f", calories * portionQuantity)),
                                color = dialogCs.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 顯示主要營養成分
                        val protein = analysisResult!!.nutrients["protein"]
                        val fat = analysisResult!!.nutrients["fat"]
                        val carbs = analysisResult!!.nutrients["carbs"]

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            protein?.let {
                                Text(
                                    stringResource(R.string.protein_value, String.format("%.1f", it * portionQuantity)),
                                    color = Color(0xFF4ECDC4),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            fat?.let {
                                Text(
                                    stringResource(R.string.fat_value, String.format("%.1f", it * portionQuantity)),
                                    color = Color(0xFF95E77E),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            carbs?.let {
                                Text(
                                    stringResource(R.string.carbs_value, String.format("%.1f", it * portionQuantity)),
                                    color = Color(0xFFA78BFA),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (portionQuantity != 1f) {
                            Text(
                                stringResource(R.string.portion_label, "${String.format("%.1f", portionQuantity)} $portionUnit"),
                                color = dialogCs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 時間和餐食類型選擇
                        Text(
                            stringResource(R.string.record_time_setting),
                            color = dialogCs.onBackground.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )

                        // 餐食類型選擇
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mealTypes.forEach { type ->
                                FilterChip(
                                    onClick = { mealType = type },
                                    label = {
                                        Text(
                                            type,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    },
                                    selected = mealType == type,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = dialogCs.primary.copy(alpha = 0.3f),
                                        selectedLabelColor = dialogCs.onBackground,
                                        containerColor = dialogCs.surfaceVariant.copy(alpha = 0.3f),
                                        labelColor = dialogCs.onBackground.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 日期和時間顯示
                        Text(
                            stringResource(R.string.date_time_label, selectedDate.toString(), selectedTime.toString()),
                            color = dialogCs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            stringResource(R.string.confirm_save_to_log),
                            color = dialogCs.onBackground.copy(alpha = 0.8f)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val recordEpochMillis = selectedDate
                                        .atTime(selectedTime)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()

                                    // ★ 關鍵：建立資料時乘上 portionQuantity
                                    val dietRecord = DietRecord(
                                        id = UUID.randomUUID().toString(),
                                        foodName = analysisResult!!.name,
                                        calories = (analysisResult!!.calories?.times(portionQuantity))?.toInt()
                                            ?: 0,
                                        date = selectedDate,
                                        mealType = mealType,
                                        proteinG = analysisResult!!.nutrients["protein"]?.times(
                                            portionQuantity
                                        ),
                                        carbsG = analysisResult!!.nutrients["carbs"]?.times(
                                            portionQuantity
                                        ),
                                        fatG = analysisResult!!.nutrients["fat"]?.times(
                                            portionQuantity
                                        ),
                                        timestamp = recordEpochMillis,
                                        userId = null,
                                        items = analysisResult!!.items,
                                        rawAnalysisText = analysisResult!!.rawText
                                    )
                                    try {
                                        val result =
                                            com.example.fitness.data.FirebaseDietRecordRepository.addRecord(
                                                dietRecord
                                            )
                                        result.fold(
                                            onSuccess = {
                                                savedToLog = true
                                                showSaveDialog = false
                                                errorMsg = null
                                                Log.d("FoodRecognition", "Saved diet record: $it")
                                            },
                                            onFailure = { err ->
                                                errorMsg = context.getString(R.string.save_error) + ": ${err.message}"
                                                Log.e("FoodRecognition", "Save failed", err)
                                            }
                                        )
                                    } catch (e: Exception) {
                                        errorMsg = context.getString(R.string.save_error) + ": ${e.message}"
                                    }
                                } catch (e: Exception) {
                                    errorMsg = context.getString(R.string.save_error) + ": ${e.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dialogCs.primary,
                            contentColor = dialogCs.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.confirm_save))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSaveDialog = false },
                        border = BorderStroke(
                            1.dp,
                            dialogCs.primary.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = dialogCs.primary
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = TechColors.DarkBlue,
                titleContentColor = dialogCs.onBackground,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun NutritionCardHighContrast(
    icon: String,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    bgColor: Color,
    borderColor: Color
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = icon,
                fontSize = 22.sp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onBackground.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = borderColor,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}

// Service Logic
object FoodRecognitionService {
    private const val GEMINI_API_KEY = ""
    private const val MODEL = "gemini-2.5-flash"

    suspend fun analyzeImageWithGemini(input: InputStream, context: Context, apiKeyOverride: String? = null): FoodAnalysis? = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = BitmapFactory.decodeStream(input)
            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val imgBytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)

            val apiKey = when {
                !apiKeyOverride.isNullOrBlank() -> apiKeyOverride
                else -> SecurePrefs.readGeminiKey(context).takeIf { it.isNotBlank() }
                    ?: readBuildConfigGeminiKey().takeIf { it.isNotBlank() }
                    ?: GEMINI_API_KEY
            }

            val payloadJson = JSONObject()
            val contentsArray = org.json.JSONArray()
            val contentObject = JSONObject()
            val partsArray = org.json.JSONArray()

            val textPart = JSONObject()
            textPart.put("text", "分析這張照片中的食物，請嚴格只回傳純JSON格式，不要有Markdown標記。必須包含以下字段：\n1. name: 食物名稱（中文）\n2. calories: 熱量（數字，單位kcal）\n3. nutrients: 營養成分對象，包含：\n   - protein: 蛋白質（克）\n   - fat: 脂肪（克）\n   - carbs: 碳水化合物（克）\n\n範例格式：{\"name\": \"雞肉飯\", \"calories\": 650, \"nutrients\": {\"protein\": 25.5, \"fat\": 12.3, \"carbs\": 78.9}}")
            partsArray.put(textPart)

            val imagePart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mime_type", "image/jpeg")
            inlineData.put("data", base64)
            imagePart.put("inline_data", inlineData)
            partsArray.put(imagePart)

            contentObject.put("parts", partsArray)
            contentsArray.put(contentObject)
            payloadJson.put("contents", contentsArray)

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respText = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                android.util.Log.e("GeminiAPI", "Error Code: ${response.code}, Body: $respText")
                response.close()
                return@withContext null
            }
            response.close()

            val root = JSONObject(respText)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) return@withContext null

            val content = candidates.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textRaw = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanJson = textRaw.replace("```json", "").replace("```", "").trim()
            if (cleanJson.isEmpty()) return@withContext null

            val json = try {
                JSONObject(cleanJson)
            } catch (e: Exception) {
                val start = cleanJson.indexOf('{')
                val end = cleanJson.lastIndexOf('}')
                if (start >= 0 && end > start) JSONObject(cleanJson.substring(start, end + 1)) else null
            } ?: return@withContext null

            val name = json.optString("name", "未知食物")
            val calories = json.optDouble("calories", Double.NaN).takeIf { !it.isNaN() }

            val nutrients = json.optJSONObject("nutrients")?.let { nut ->
                mutableMapOf<String, Double>().also { map ->
                    nut.keys().forEach { key ->
                        val v = nut.optDouble(key)
                        if (!v.isNaN()) {
                            val standardKey = when (key.lowercase()) {
                                "protein", "蛋白質", "蛋白质" -> "protein"
                                "fat", "脂肪" -> "fat"
                                "carbs", "carbohydrates", "碳水化合物", "碳水" -> "carbs"
                                else -> key.lowercase()
                            }
                            map[standardKey] = v
                        }
                    }
                }
            } ?: mutableMapOf() // simplified fallback

            // 如果營養成分為空，提供默認估算
            val finalNutrients = if (nutrients.isEmpty()) {
                calories?.let { cal ->
                    mapOf(
                        "protein" to cal * 0.15 / 4,
                        "fat" to cal * 0.25 / 9,
                        "carbs" to cal * 0.60 / 4
                    )
                } ?: emptyMap()
            } else {
                nutrients
            }

            return@withContext FoodAnalysis(name, calories, finalNutrients)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try { input.close() } catch (_: Exception) {}
        }
    }

    suspend fun debugListModels(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val apiKey = SecurePrefs.readGeminiKey(context).takeIf { it.isNotBlank() }
                    ?: readBuildConfigGeminiKey().takeIf { it.isNotBlank() }
                    ?: GEMINI_API_KEY

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                    .get()
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                response.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun fetchCaloriesFromEdamam(foodName: String, context: Context): Double? = withContext(Dispatchers.IO) {
        try {
            val appId = SecurePrefs.readEdamamId(context)
            val appKey = SecurePrefs.readEdamamKey(context)
            if (appId.isBlank() || appKey.isBlank()) return@withContext null
            val ingr = URLEncoder.encode(foodName, "UTF-8")
            val url = "https://api.edamam.com/api/food-database/v2/parser?app_id=$appId&app_key=$appKey&ingr=$ingr"
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: run { resp.close(); return@withContext null }
            resp.close()
            val root = JSONObject(body)
            val hints = root.optJSONArray("hints") ?: return@withContext null
            if (hints.length() == 0) return@withContext null
            val first = hints.optJSONObject(0) ?: return@withContext null
            val food = first.optJSONObject("food") ?: return@withContext null
            val nutrients = food.optJSONObject("nutrients") ?: return@withContext null
            val kcal = nutrients.optDouble("ENERC_KCAL", Double.NaN)
            return@withContext if (!kcal.isNaN()) kcal else null
        } catch (_: Exception) {
            return@withContext null
        }
    }
}

// 輔助函數
private var globalContext: Context? = null
fun initGlobalContext(context: Context) {
    globalContext = context.applicationContext
}
private fun readBuildConfigGeminiKey(): String {
    return com.example.fitness.BuildConfig.GEMINI_API_KEY
}