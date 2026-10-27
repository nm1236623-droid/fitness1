package com.example.fitness.inbody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InBodyScreen(
    repository: InBodyRepository,
    onDone: () -> Unit = {}
) {
    var weightText by remember { mutableStateOf("") }
    var bodyFatText by remember { mutableStateOf("") }
    var muscleText by remember { mutableStateOf("") }

    val records by repository.records.collectAsState()

    // Global Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(16.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 20.dp)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.inbody_tracker),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            stringResource(R.string.track_body_metrics),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input Section
            SectionCard(title = stringResource(R.string.add_measurement)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonOutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = stringResource(R.string.weight_kg),
                        icon = Icons.Default.MonitorWeight
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonOutlinedTextField(
                            value = bodyFatText,
                            onValueChange = { bodyFatText = it },
                            label = stringResource(R.string.body_fat_percent),
                            icon = Icons.Default.Percent,
                            modifier = Modifier.weight(1f)
                        )
                        NeonOutlinedTextField(
                            value = muscleText,
                            onValueChange = { muscleText = it },
                            label = stringResource(R.string.muscle_mass_kg),
                            icon = Icons.Default.FitnessCenter,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val w = weightText.toFloatOrNull() ?: return@Button
                                val bf = bodyFatText.toFloatOrNull()
                                val mm = muscleText.toFloatOrNull()
                                val now = Instant.now()
                                repository.add(
                                    InBodyRecord(
                                        timestamp = now,
                                        weightKg = w,
                                        bodyFatPercent = bf,
                                        muscleMassKg = mm
                                    )
                                )
                                weightText = ""
                                bodyFatText = ""
                                muscleText = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .neonGlowBorder(cornerRadius = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(stringResource(R.string.save_record), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                weightText = ""
                                bodyFatText = ""
                                muscleText = ""
                            },
                            modifier = Modifier.height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History Section
            Text(
                stringResource(R.string.history_records),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 20.dp)
                        .padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.no_inbody_records),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.please_add_inbody_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val formatter = remember {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(items = records, key = { it.id }) { rec ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassEffect(cornerRadius = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatter.format(rec.timestamp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        MetricMiniNeon(label = stringResource(R.string.weight), value = "${rec.weightKg}", unit = "kg")
                                        rec.bodyFatPercent?.let {
                                            MetricMiniNeon(label = stringResource(R.string.body_fat), value = "$it", unit = "%")
                                        }
                                        rec.muscleMassKg?.let {
                                            MetricMiniNeon(label = stringResource(R.string.muscle_mass), value = "$it", unit = "kg")
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { repository.remove(rec.id) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Reusable Components (Consistent with CardioScreen/HomeScreen) ---

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 20.dp)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NeonOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = cs.primary) }
        } else null,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun MetricMiniNeon(label: String, value: String, unit: String) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}