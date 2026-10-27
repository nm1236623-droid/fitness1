package com.example.fitness.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.R
import kotlin.math.roundToInt

/**
 * 簡單的水分追蹤卡。
 * - consumed / goal 單位：ml
 */
@Composable
fun WaterIntakeCard(
    consumed: Int,
    goal: Int,
    onAddWater: (amountMl: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val waterColor = Color(0xFF4FC3F7)
    val safeGoal = goal.coerceAtLeast(1)
    val targetProgress = (consumed.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val percent = (targetProgress * 100).roundToInt()

    // 使用動畫讓進度條平滑更新
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 500,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "water_progress_animation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surface.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .border(0.5.dp, cs.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocalDrink,
                contentDescription = null,
                tint = waterColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.water)}：${consumed} / ${goal} ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onBackground,
                    fontSize = 12.sp
                )
                Text(
                    text = "${percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onBackground.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }

        // 進度條 - 使用動畫進度
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = waterColor,
            trackColor = cs.outline.copy(alpha = 0.2f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAddWater(200) },
                colors = ButtonDefaults.buttonColors(containerColor = waterColor.copy(alpha = 0.22f))
            ) { Text("+200", color = cs.onBackground, fontSize = 12.sp) }

            Button(
                onClick = { onAddWater(500) },
                colors = ButtonDefaults.buttonColors(containerColor = waterColor.copy(alpha = 0.22f))
            ) { Text("+500", color = cs.onBackground, fontSize = 12.sp) }
        }
    }
}

@Composable
fun WaterGoalDialog(
    currentGoal: Int,
    onGoalSet: (newGoal: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(currentGoal.takeIf { it > 0 }?.toString().orEmpty()) }

    // 避免 currentGoal 變更時 input 沒同步
    LaunchedEffect(currentGoal) {
        if (input.isBlank()) input = currentGoal.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_water_goal), color = Color.White) },
        text = {
            Column {
                Text(stringResource(R.string.enter_goal_ml), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(6)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.example_2000)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedLabelColor = Color.White.copy(alpha = 0.9f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        cursorColor = Color(0xFF4FC3F7)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newGoal = input.toIntOrNull()?.coerceAtLeast(0) ?: currentGoal
                    onGoalSet(newGoal)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Color(0xFF1B1F2A)
    )
}
