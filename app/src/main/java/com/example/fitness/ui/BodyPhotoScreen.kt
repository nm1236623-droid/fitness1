package com.example.fitness.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.fitness.R
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BodyPhotoScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val photos = remember { mutableStateListOf<Pair<Uri, String>>() }

    val displayFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val fileNameParseFmt = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }

    fun yearMonthFromTs(ts: String): Pair<Int, Int>? {
        return try {
            val d = displayFmt.parse(ts) ?: return null
            val cal = Calendar.getInstance()
            cal.time = d
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        } catch (_: Exception) { null }
    }

    LaunchedEffect(Unit) {
        val storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null && storageDir.exists()) {
            val files: List<File> = withContext(Dispatchers.IO) {
                storageDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
            }
            val filenameRegex = Regex("\\d{8}_\\d{6}")
            files.forEach { f ->
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                val tsText = try {
                    val match = filenameRegex.find(f.name)
                    if (match != null) {
                        val d = fileNameParseFmt.parse(match.value)
                        if (d != null) displayFmt.format(d) else displayFmt.format(Date(f.lastModified()))
                    } else displayFmt.format(Date(f.lastModified()))
                } catch (_: Exception) { displayFmt.format(Date(f.lastModified())) }
                if (!photos.any { it.first == uri }) photos.add(Pair(uri, tsText))
            }
        }
    }

    val nowCal = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH) + 1) }
    var showMonthMenu by remember { mutableStateOf(false) }

    val monthsAvailable = remember(photos) {
        photos.mapNotNull { yearMonthFromTs(it.second) }
            .distinct()
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }

    val yearStr = stringResource(R.string.year_suffix)
    val monthStr = stringResource(R.string.month_suffix)
    fun monthLabel(y: Int, m: Int) = String.format(Locale.getDefault(), "%d$yearStr %02d$monthStr", y, m)

    fun goPrevMonth() { if (selectedMonth == 1) { selectedYear--; selectedMonth = 12 } else selectedMonth-- }
    fun goNextMonth() { if (selectedMonth == 12) { selectedYear++; selectedMonth = 1 } else selectedMonth++ }

    fun createImageFile(): Pair<File?, String?> {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return Pair(null, null)
            if (!storageDir.exists()) storageDir.mkdirs()
            var file = File(storageDir, "BODY_${timeStamp}.jpg")
            var suffix = 1
            while (file.exists()) { file = File(storageDir, "BODY_${timeStamp}_$suffix.jpg"); suffix++ }
            file.createNewFile()
            val parsed = try { fileNameParseFmt.parse(timeStamp) } catch (_: Exception) { null }
            Pair(file, if (parsed != null) displayFmt.format(parsed) else displayFmt.format(Date()))
        } catch (_: IOException) { Pair(null, null) }
    }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoTimestamp by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) photos.add(0, Pair(photoUri!!, photoTimestamp ?: displayFmt.format(Date())))
    }

    // ═══════════════ UI ═══════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).padding(16.dp)) {

            // ── Header ──
            Box(
                modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 24.dp).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.1f)).clickable(onClick = onDone),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = cs.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.body_photos), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                        Text(stringResource(R.string.record_body_changes), style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(alpha = 0.5f))
                    }
                    if (photos.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = cs.primary.copy(alpha = 0.15f)) {
                            Text("${photos.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Action Buttons ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val (file, ts) = createImageFile()
                        if (file != null) {
                            photoUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                            photoTimestamp = ts
                            launcher.launch(photoUri!!)
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp).neonGlowBorder(cornerRadius = 16.dp, glowColor = cs.primary),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary.copy(alpha = 0.15f), contentColor = cs.primary),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (photos.isNotEmpty()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(photos.first().first, "image/*")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            })
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onBackground)
                ) {
                    Icon(Icons.Default.Collections, null, Modifier.size(22.dp), tint = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recent_photos), fontWeight = FontWeight.Bold, color = cs.onBackground)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Month Switcher ──
            Box(modifier = Modifier.fillMaxWidth().glassEffect(cornerRadius = 20.dp).padding(horizontal = 4.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { goPrevMonth() }) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous), tint = cs.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.08f)).clickable { showMonthMenu = true }.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(monthLabel(selectedYear, selectedMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }, containerColor = TechColors.DarkBlue) {
                            if (monthsAvailable.isEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.no_records), color = cs.onBackground.copy(0.5f)) }, onClick = { showMonthMenu = false })
                            } else {
                                monthsAvailable.forEach { (y, m) ->
                                    DropdownMenuItem(text = { Text(monthLabel(y, m), color = cs.onBackground, fontWeight = FontWeight.Medium) }, onClick = { selectedYear = y; selectedMonth = m; showMonthMenu = false })
                                }
                            }
                        }
                    }
                    IconButton(onClick = { goNextMonth() }) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next), tint = cs.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Photo List ──
            val filteredPhotos = photos.filter { pair ->
                yearMonthFromTs(pair.second)?.let { it.first == selectedYear && it.second == selectedMonth } ?: false
            }

            if (filteredPhotos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(80.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null, tint = cs.primary.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.no_photos_this_month), color = cs.onBackground.copy(0.4f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("📸", fontSize = 28.sp)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${filteredPhotos.size} ${stringResource(R.string.body_photos)}", style = MaterialTheme.typography.labelMedium, color = cs.onBackground.copy(0.5f), fontWeight = FontWeight.Medium)
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(items = filteredPhotos, key = { it.first.toString() }) { pair ->
                        PhotoCard(pair.first, pair.second, onDelete = { photos.remove(pair) }, onView = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(pair.first, "image/*"); flags = Intent.FLAG_GRANT_READ_URI_PERMISSION })
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(uri: Uri, timestamp: String, onDelete: () -> Unit, onView: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).glassEffect(cornerRadius = 20.dp)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).clickable(onClick = onView)) {
                UriImage(uri = uri, modifier = Modifier.fillMaxSize())
                // View icon
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                // Bottom gradient overlay
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(timestamp, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            onClick = onDelete, shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFF4757).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFF4757).copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4757), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.delete_photo), style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF4757), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UriImage(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var imageBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try { ctx.contentResolver.openInputStream(uri)?.use { imageBitmap = BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (_: Exception) {}
        }
    }
    if (imageBitmap != null) {
        Image(bitmap = imageBitmap!!, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(cs.surface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = cs.primary, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
        }
    }
}