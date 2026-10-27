package com.example.fitness.inbody

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.ui.LineChart
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.TechColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

private enum class InBodyMetric(
    val labelResId: Int,
    val icon: String,
    val unit: String,
    val color: @Composable () -> androidx.compose.ui.graphics.Color
) {
    WEIGHT(R.string.weight, "⚖️", "kg", { MaterialTheme.colorScheme.primary }),
    BODY_FAT(R.string.body_fat, "📉", "%", { MaterialTheme.colorScheme.tertiary }),
    MUSCLE(R.string.muscle_mass, "💪", "kg", { MaterialTheme.colorScheme.secondary }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InBodyAnalysisScreen(
    inBodyRepository: InBodyRepository,
    onBack: () -> Unit,
) {
    var selectedMetric by remember { mutableStateOf(InBodyMetric.WEIGHT) }
    val records by inBodyRepository.records.collectAsState(initial = emptyList())
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.inbody_analysis),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = cs.primary,
                navigationIconContentColor = cs.onBackground
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(cs.background, cs.background)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Metric Selector Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InBodyMetric.entries.forEach { metric ->
                    val isSelected = selectedMetric == metric
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .glassEffect(cornerRadius = 20.dp)
                            .then(
                                if (isSelected) {
                                    Modifier
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(onClick = { selectedMetric = metric })
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = metric.icon,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(metric.labelResId),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected)
                                    TechColors.NeonBlue
                                else
                                    Color.White.copy(alpha = 0.7f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Calculate stats
            val points = remember(records, selectedMetric) {
                val byDay = records
                    .sortedBy { it.timestamp }
                    .groupBy { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
                    .mapValues { (_, list) -> list.last() }

                when (selectedMetric) {
                    InBodyMetric.WEIGHT -> byDay
                        .map { (date, rec) -> date to rec.weightKg }
                        .sortedBy { it.first }
                    InBodyMetric.BODY_FAT -> byDay
                        .mapNotNull { (date, rec) -> rec.bodyFatPercent?.let { date to it } }
                        .sortedBy { it.first }
                    InBodyMetric.MUSCLE -> byDay
                        .mapNotNull { (date, rec) -> rec.muscleMassKg?.let { date to it } }
                        .sortedBy { it.first }
                }
            }

            // Statistics Summary Card
            if (points.isNotEmpty()) {
                val values = points.map { it.second }
                val latest = values.lastOrNull() ?: 0f
                val earliest = values.firstOrNull() ?: 0f
                val change = latest - earliest
                val average = values.average().toFloat()
                val min = values.minOrNull() ?: 0f
                val max = values.maxOrNull() ?: 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.data_statistics),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Latest Value
                            StatCard(
                                label = stringResource(R.string.latest),
                                value = String.format(Locale.getDefault(), "%.1f", latest),
                                unit = selectedMetric.unit,
                                modifier = Modifier.weight(1f)
                            )

                            // Change
                            StatCard(
                                label = stringResource(R.string.change),
                                value = String.format(
                                    Locale.getDefault(),
                                    "%+.1f",
                                    change
                                ),
                                unit = selectedMetric.unit,
                                modifier = Modifier.weight(1f),
                                valueColor = when {
                                    change > 0 && selectedMetric == InBodyMetric.MUSCLE ->
                                        MaterialTheme.colorScheme.tertiary
                                    change < 0 && selectedMetric == InBodyMetric.BODY_FAT ->
                                        MaterialTheme.colorScheme.tertiary
                                    change < 0 && selectedMetric == InBodyMetric.WEIGHT ->
                                        MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Average
                            StatCard(
                                label = stringResource(R.string.average),
                                value = String.format(Locale.getDefault(), "%.1f", average),
                                unit = selectedMetric.unit,
                                modifier = Modifier.weight(1f)
                            )

                            // Range
                            StatCard(
                                label = stringResource(R.string.range),
                                value = String.format(
                                    Locale.getDefault(),
                                    "%.1f-%.1f",
                                    min,
                                    max
                                ),
                                unit = selectedMetric.unit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Chart Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.trend_chart),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${stringResource(selectedMetric.labelResId)} (${selectedMetric.unit})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = selectedMetric.icon,
                            style = MaterialTheme.typography.displaySmall
                        )
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    if (points.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassEffect(cornerRadius = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    stringResource(R.string.no_records),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFF6B6B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.please_add_inbody_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LineChart(
                            dataPoints = points,
                            dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
                    }
                }
            }

            // Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TechColors.NeonBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.operation_guide),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.chart_tips),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = TechColors.NeonBlue
) {
    Box(
        modifier = modifier.glassEffect(cornerRadius = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
