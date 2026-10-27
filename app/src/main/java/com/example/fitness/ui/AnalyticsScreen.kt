package com.example.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.activity.ActivityLogRepository
import com.example.fitness.data.TrainingPlanRepository
import com.example.fitness.inbody.InBodyRepository
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * A lightweight entry screen for analysis.
 * HomeScreen routes here and then can navigate to InBodyAnalysisScreen / PartAnalysisScreen.
 */
@Composable
fun AnalyticsScreen(
    repository: TrainingPlanRepository,
    activityRepository: ActivityLogRepository,
    inBodyRepo: InBodyRepository,
    onOpenPartAnalysis: () -> Unit,
    onOpenInBodyAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
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
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = cs.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.data_analysis),
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.track_progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }

        // Analysis Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // InBody Analysis Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
                    .clickable(onClick = onOpenInBodyAnalysis)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Monitor,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = stringResource(R.string.inbody_analysis_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = cs.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.inbody_analysis_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Part Analysis Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 24.dp)
                    .clickable(onClick = onOpenPartAnalysis)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = stringResource(R.string.part_analysis_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = cs.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.part_analysis_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Tip Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(cornerRadius = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.tip),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.analytics_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
        }
    }
}
