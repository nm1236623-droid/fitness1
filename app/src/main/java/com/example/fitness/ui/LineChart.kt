package com.example.fitness.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.ui.theme.TechColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LineChart(
    dataPoints: List<Pair<LocalDate, Float>>,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
) {
    if (dataPoints.isEmpty()) return

    val xValues = remember(dataPoints) { dataPoints.map { it.first.toEpochDay().toFloat() } }
    val yValues = remember(dataPoints) { dataPoints.map { it.second } }

    val minX = xValues.minOrNull() ?: 0f
    val maxX = xValues.maxOrNull() ?: 0f
    val minY = (yValues.minOrNull() ?: 0f) * 0.9f // Add some buffer
    val maxY = (yValues.maxOrNull() ?: 0f) * 1.1f

    val rangeX = if (maxX - minX == 0f) 1f else maxX - minX
    val rangeY = if (maxY - minY == 0f) 1f else maxY - minY

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 30.dp, bottom = 20.dp, top = 10.dp, end = 10.dp)) {
            val width = size.width
            val height = size.height

            // 1. Draw Grid Lines (Horizontal)
            val gridSteps = 4
            for (i in 0..gridSteps) {
                val yRatio = i.toFloat() / gridSteps
                val y = height - (yRatio * height)

                // Draw Line
                drawLine(
                    color = labelColor.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )

                // Draw Y-Axis Labels
                val labelValue = minY + (rangeY * yRatio)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(labelValue),
                    -10.dp.toPx(), // Offset to left
                    y + 10f, // Center vertically roughly
                    Paint().apply {
                        color = labelColor.toArgb()
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.RIGHT
                    }
                )
            }

            // 2. Build Chart Path
            val path = Path()
            var firstPoint = Offset.Unspecified

            dataPoints.forEachIndexed { index, point ->
                val xRatio = (point.first.toEpochDay().toFloat() - minX) / rangeX
                val yRatio = (point.second - minY) / rangeY

                val x = xRatio * width
                val y = height - (yRatio * height)

                if (index == 0) {
                    path.moveTo(x, y)
                    firstPoint = Offset(x, y)
                } else {
                    // Smooth curve logic could go here, using lineTo for neon sharp look
                    path.lineTo(x, y)
                }
            }

            // 3. Draw Path (Neon Glow Effect)
            // Outer glow (blurry thick line)
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.4f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            // Core line (sharp bright line)
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(lineColor, lineColor.copy(alpha = 0.6f))
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Draw Points & X-Axis Labels
            // Only draw a subset of labels to avoid overlapping
            val labelStep = maxOf(1, dataPoints.size / 5)

            dataPoints.forEachIndexed { index, point ->
                val xRatio = (point.first.toEpochDay().toFloat() - minX) / rangeX
                val yRatio = (point.second - minY) / rangeY

                val x = xRatio * width
                val y = height - (yRatio * height)

                // Draw Point
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )

                // Draw Date Label
                if (index % labelStep == 0 || index == dataPoints.lastIndex) {
                    drawContext.canvas.nativeCanvas.drawText(
                        point.first.format(dateFormatter),
                        x,
                        height + 20.dp.toPx(),
                        Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 10.sp.toPx()
                            textAlign = Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}