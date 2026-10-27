package com.example.fitness.running

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.activity.ActivityRecord
import com.example.fitness.network.GeminiClient
import com.example.fitness.timer.StopwatchService
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** 每個 CardioType 對應的霓虹色 */
private fun CardioType.neonColor(): Color = when (this) {
    CardioType.WALK_OR_JOG   -> TechColors.NeonGreen
    CardioType.CYCLING        -> TechColors.NeonBlue
    CardioType.SPIN_BIKE      -> TechColors.NeonPurple
    CardioType.ELLIPTICAL     -> TechColors.NeonOrange
    CardioType.ROWING_MACHINE -> TechColors.NeonBlue
    CardioType.STAIR_CLIMBER  -> TechColors.NeonYellow
}

@Composable
fun CardioScreen(
    repository: CardioRepository,
    activityRepository: ActivityLogRepository,
    userWeightKg: Float = 70f,
    onDone: () -> Unit = {}
) {
    var minutes by remember { mutableIntStateOf(30) }
    var seconds by remember { mutableIntStateOf(0) }
    var speed by remember { mutableIntStateOf(5) }
    var incline by remember { mutableIntStateOf(1) }

    var cardioType by remember { mutableStateOf(CardioType.WALK_OR_JOG) }

    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val records by repository.records.collectAsState()

    val accentColor by animateColorAsState(
        targetValue = cardioType.neonColor(),
        animationSpec = tween(400),
        label = "accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            // ═══ 1. Header ═══
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 20.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = accentColor, modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.cardio_tracker),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.cardio),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Save button
                    Button(
                        onClick = {
                            val dur = minutes * 60 + seconds
                            if (dur <= 0) return@Button
                            val spd = speed.toFloat()
                            val inc = incline.toFloat()
                            val now = Instant.now()
                            isSaving = true; saveError = null
                            scope.launch {
                                try {
                                    val kcalFloat = GeminiClient.estimateCardioCaloriesWithGemini(
                                        cardioType = cardioType, durationSec = dur, weightKg = userWeightKg, avgSpeedKph = spd
                                    )
                                    val kcal = kcalFloat.toDouble()
                                    repository.add(CardioRecord(timestamp = now, durationSeconds = dur, averageSpeedKph = spd, inclinePercent = inc, calories = kcal, cardioType = cardioType))
                                    val actType = "cardio_" + cardioType.name.lowercase(Locale.US)
                                    activityRepository.add(ActivityRecord(id = UUID.randomUUID().toString(), planId = null, type = actType, start = now, end = now.plusSeconds(dur.toLong()), calories = kcal))
                                    minutes = 30; seconds = 0
                                } catch (e: Exception) {
                                    saveError = e.message
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.copy(0.2f),
                            contentColor = accentColor
                        ),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.save), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ═══ 2. Cardio Type Selector (horizontal scroll, icon chips) ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardioType.entries.forEach { t ->
                    val selected = cardioType == t
                    val color = t.neonColor()
                    val cs = MaterialTheme.colorScheme
                    val bgColor = if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.3f)
                    val borderColor = if (selected) cs.primary else cs.outline.copy(alpha = 0.2f)

                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable { cardioType = t }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            t.icon, null,
                            tint = if (selected) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(t.displayNameRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ═══ 3. Parameter Input Grid ═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonParamItem(
                    label = stringResource(R.string.min_short),
                    value = minutes.toString(),
                    color = accentColor,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f),
                    onValueChange = { minutes = it },
                    range = 0..600
                )
                NeonParamItem(
                    label = stringResource(R.string.sec_short),
                    value = seconds.toString(),
                    color = accentColor,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f),
                    onValueChange = { seconds = it },
                    range = 0..59
                )
                NeonParamItem(
                    label = stringResource(R.string.speed),
                    value = speed.toString(),
                    color = TechColors.NeonBlue,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f),
                    onValueChange = { speed = it },
                    range = 1..50
                )
                NeonParamItem(
                    label = stringResource(R.string.incline),
                    value = incline.toString(),
                    color = TechColors.NeonOrange,
                    icon = Icons.Default.Landscape,
                    modifier = Modifier.weight(1f),
                    onValueChange = { incline = it },
                    range = 0..20
                )
            }

            if (saveError != null) {
                Text(
                    text = saveError!!,
                    color = TechColors.NeonRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ═══ 4. History Records ═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cs = MaterialTheme.colorScheme
                Icon(
                    Icons.Default.History, null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.history_record),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (records.isNotEmpty()) {
                    Text(
                        "${records.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (records.isEmpty()) {
                val cs = MaterialTheme.colorScheme
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 20.dp)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition(label = "empty")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f, targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                            label = "emptyAlpha"
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsRun, null,
                            modifier = Modifier.size(56.dp),
                            tint = accentColor.copy(alpha)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.no_record_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(records, key = { it.id }) { rec ->
                        HistoryRecordCard(rec)
                    }
                }
            }
        }

        // ═══ 5. Floating Timer Bar ═══
        NeonTimerBar(
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            onFillTime = { m, s -> minutes = m; seconds = s }
        )
    }
}

// ════════════════════════════════════════════
//  Components
// ════════════════════════════════════════════

/** 帶霓虹色彩的參數選擇方塊 */
@Composable
private fun NeonParamItem(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.4f), RoundedCornerShape(14.dp))
            .background(color.copy(0.12f))
            .clickable { showDialog = true }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color.copy(0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showDialog) {
        WheelPickerDialog(
            title = label,
            initialValue = value.toIntOrNull() ?: range.first,
            range = range,
            accentColor = color,
            onDismiss = { showDialog = false },
            onConfirm = { onValueChange(it); showDialog = false }
        )
    }
}

/** 歷史紀錄卡片 — 帶左邊顏色指示條 */
@Composable
private fun HistoryRecordCard(rec: CardioRecord) {
    val color = rec.cardioType.neonColor()

    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.15f), RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(0.3f))
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左側色條
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp)
                .background(color)
        )

        Spacer(Modifier.width(12.dp))

        // Icon
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(rec.cardioType.icon, null, tint = color, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(rec.cardioType.displayNameRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Text(
                DateTimeFormatter.ofPattern("MM/dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(rec.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 14.dp)
        ) {
            Text(
                "${"%.0f".format(rec.calories)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    "${rec.durationSeconds / 60} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                if (rec.averageSpeedKph != null && rec.averageSpeedKph > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Speed, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "${rec.averageSpeedKph.toInt()} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 底部浮動計時器 — 帶脈衝動畫 */
@Composable
fun NeonTimerBar(
    accentColor: Color,
    modifier: Modifier = Modifier,
    onFillTime: (Int, Int) -> Unit
) {
    val elapsedMs by StopwatchService.elapsedMs.collectAsState(initial = 0L)
    val isRunning = StopwatchService.isRunning
    val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
    val mm = totalSec / 60
    val ss = totalSec % 60

    // Pulse animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "timer")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRunning) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    val borderAlpha = if (isRunning) pulseAlpha else 0.25f

    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .border(1.5f.dp, accentColor.copy(borderAlpha), RoundedCornerShape(31.dp))
            .background(cs.surface)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Time Display
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) accentColor.copy(pulseAlpha + 0.3f)
                            else cs.onSurfaceVariant.copy(0.2f)
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) accentColor else cs.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset
                IconButton(
                    onClick = { StopwatchService.stopAndReset() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cs.onSurface.copy(0.06f))
                ) {
                    Icon(Icons.Default.Refresh, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                // Play / Pause
                IconButton(
                    onClick = { if (isRunning) StopwatchService.pause() else StopwatchService.start() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(0.2f))
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Fill
                IconButton(
                    onClick = {
                        if (isRunning) StopwatchService.pause()
                        onFillTime(mm.toInt(), ss.toInt())
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.06f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Input, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Wheel picker dialog with neon accent color */
@Composable
fun WheelPickerDialog(
    title: String,
    initialValue: Int,
    range: IntRange,
    accentColor: Color = TechColors.NeonBlue,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TechColors.DarkBlue,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            val listState = rememberLazyListState()
            val itemHeight = 44.dp
            LaunchedEffect(Unit) {
                val initialIndex = (initialValue - range.first).coerceIn(0, (range.last - range.first))
                listState.scrollToItem(initialIndex)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Highlight band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(0.12f))
                        .border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(12.dp))
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(range.toList()) { num ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable { onConfirm(num) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                num.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (num == initialValue) accentColor else Color.White.copy(0.35f),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(0.5f))
            }
        }
    )
}