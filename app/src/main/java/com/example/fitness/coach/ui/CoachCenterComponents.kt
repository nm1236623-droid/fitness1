package com.example.fitness.coach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitness.coach.cloud.CoachTrainee
import com.example.fitness.data.TrainingPlan

@Composable
fun PlanShareCard(
    plan: TrainingPlan,
    onPublishCloud: () -> Unit,
    trainees: List<CoachTrainee> = emptyList(),
    onPublishToTrainee: ((CoachTrainee) -> Unit)? = null,
    onDeleteLocal: (() -> Unit)? = null,
) {
    val isPublished = plan.publishedAt != null

    var confirmPublishToTrainee by remember { mutableStateOf(false) }

    // NEW: pick trainee only when user taps Publish
    var pickingTrainee by remember { mutableStateOf(false) }
    var selectedTrainee by remember(plan.id, trainees) {
        mutableStateOf<CoachTrainee?>(trainees.firstOrNull())
    }

    if (pickingTrainee && onPublishToTrainee != null) {
        AlertDialog(
            onDismissRequest = { pickingTrainee = false },
            title = { Text("選擇學員") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (trainees.isEmpty()) {
                        Text("尚無學員", style = MaterialTheme.typography.bodySmall)
                    } else {
                        trainees.forEach { t ->
                            val label = t.displayName.ifBlank { t.traineeId }
                            OutlinedButton(
                                onClick = {
                                    selectedTrainee = t
                                    pickingTrainee = false
                                    confirmPublishToTrainee = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingTrainee = false }) { Text("關閉") }
            }
        )
    }

    if (confirmPublishToTrainee && selectedTrainee != null && onPublishToTrainee != null) {
        AlertDialog(
            onDismissRequest = { confirmPublishToTrainee = false },
            title = { Text("發布給學員") },
            text = {
                val t = selectedTrainee!!
                Text("確定要把『${plan.name}』發布給\n${t.displayName.ifBlank { t.traineeId }}\n(${t.traineeId}) 嗎？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPublishToTrainee = false
                        onPublishToTrainee(selectedTrainee!!)
                    }
                ) { Text("發布") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPublishToTrainee = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPublished) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPublished)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (isPublished) {
                    com.example.fitness.ui.theme.AppBadge(
                        text = "已發布",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (onPublishToTrainee != null) {
                Button(
                    onClick = { pickingTrainee = true },
                    enabled = trainees.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (trainees.isEmpty()) "尚未加入學員" else "發布給特定學員",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !isPublished,
                    onClick = onPublishCloud,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isPublished) "已同步雲端" else "發布至雲端")
                }

                if (onDeleteLocal != null) {
                    OutlinedButton(
                        onClick = onDeleteLocal,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("刪除")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleTraineePicker(
    trainees: List<CoachTrainee>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selected: CoachTrainee?,
    onSelected: (CoachTrainee) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        val label = selected?.displayName?.ifBlank { selected.traineeId } ?: "選擇學員"

        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("學員", style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            trainees.forEach { t ->
                val text = t.displayName.ifBlank { t.traineeId }
                DropdownMenuItem(
                    text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelected(t)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
