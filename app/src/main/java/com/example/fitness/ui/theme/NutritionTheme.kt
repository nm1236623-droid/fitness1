package com.example.fitness.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 營養記錄專用主題擴展
 * 提供營養相關的顏色、樣式和組件
 */

// 營養相關顏色擴展
object NutritionColors {
    // 主要營養顏色
    val CaloriesPrimary = Color(0xFF4CAF50)      // 綠色 - 熱量
    val ProteinPrimary = Color(0xFFFF9800)        // 橙色 - 蛋白質
    val CarbsPrimary = Color(0xFF2196F3)          // 藍色 - 碳水化合物
    val FatPrimary = Color(0xFF9C27B0)            // 紫色 - 脂肪
    
    // 次要營養顏色
    val FiberPrimary = Color(0xFF795548)          // 棕色 - 纖維
    val SugarPrimary = Color(0xFFE91E63)          // 粉紅 - 糖分
    val SodiumPrimary = Color(0xFF607D8B)         // 藍灰 - 鈉
    val VitaminPrimary = Color(0xFF8BC34A)        // 黃綠 - 維生素
    
    // 狀態顏色
    val Excellent = Color(0xFF4CAF50)             // 優秀 - 綠色
    val Good = Color(0xFF8BC34A)                  // 良好 - 黃綠
    val Average = Color(0xFFFFC107)               // 一般 - 黃色
    val Poor = Color(0xFFFF9800)                  // 較差 - 橙色
    val Critical = Color(0xFFF44336)              // 危險 - 紅色
}

// 營養相關漸變
object NutritionGradients {
    val CaloriesGradient = Brush.horizontalGradient(
        colors = listOf(NutritionColors.CaloriesPrimary, NutritionColors.CaloriesPrimary.copy(alpha = 0.3f))
    )
    
    val ProteinGradient = Brush.horizontalGradient(
        colors = listOf(NutritionColors.ProteinPrimary, NutritionColors.ProteinPrimary.copy(alpha = 0.3f))
    )
    
    val CarbsGradient = Brush.horizontalGradient(
        colors = listOf(NutritionColors.CarbsPrimary, NutritionColors.CarbsPrimary.copy(alpha = 0.3f))
    )
    
    val FatGradient = Brush.horizontalGradient(
        colors = listOf(NutritionColors.FatPrimary, NutritionColors.FatPrimary.copy(alpha = 0.3f))
    )
    
    val RainbowGradient = Brush.horizontalGradient(
        colors = listOf(
            NutritionColors.CaloriesPrimary,
            NutritionColors.ProteinPrimary,
            NutritionColors.CarbsPrimary,
            NutritionColors.FatPrimary
        )
    )
}

// 營養相關形狀
object NutritionShapes {
    val CardShape = RoundedCornerShape(16.dp)
    val SmallCardShape = RoundedCornerShape(12.dp)
    val ChipShape = RoundedCornerShape(20.dp)
    val ProgressShape = RoundedCornerShape(8.dp)
    val PillShape = RoundedCornerShape(50.dp)
}

// 營養相關文字樣式
object NutritionTextStyles {
    val Title = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    
    val Subtitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.9f)
    )
    
    val Body = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.8f)
    )
    
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.6f)
    )
    
    val Metric = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    
    val MetricUnit = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.7f)
    )
}

// 營養相關修飾器擴展
fun Modifier.nutritionCard(): Modifier = this
    .background(Color.White.copy(alpha = 0.1f), NutritionShapes.CardShape)
    .border(1.dp, TechColors.NeonBlue.copy(alpha = 0.3f), NutritionShapes.CardShape)

fun Modifier.nutritionProgress(
    color: Color = TechColors.NeonBlue,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f)
): Modifier = this
    .background(backgroundColor, NutritionShapes.ProgressShape)
    .height(8.dp)

fun Modifier.nutritionChip(
    selected: Boolean = false,
    color: Color = TechColors.NeonBlue
): Modifier = this
    .background(
        if (selected) color.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
        NutritionShapes.ChipShape
    )
    .border(
        1.dp,
        if (selected) color else Color.White.copy(alpha = 0.3f),
        NutritionShapes.ChipShape
    )

fun Modifier.nutritionMetricCard(
    color: Color = TechColors.NeonBlue
): Modifier = this
    .background(color.copy(alpha = 0.1f), NutritionShapes.SmallCardShape)
    .border(1.dp, color.copy(alpha = 0.5f), NutritionShapes.SmallCardShape)

// 營養相關組件
@Composable
fun NutritionMetricCard(
    title: String,
    value: String,
    unit: String,
    color: Color = TechColors.NeonBlue,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .nutritionMetricCard(color)
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = value,
                style = NutritionTextStyles.Metric,
                color = color
            )
            
            Text(
                text = unit,
                style = NutritionTextStyles.MetricUnit
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = title,
                style = NutritionTextStyles.Caption
            )
        }
    }
}

@Composable
fun NutritionProgressBar(
    progress: Float,
    color: Color = TechColors.NeonBlue,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(backgroundColor, NutritionShapes.ProgressShape)
            .height(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .background(color, NutritionShapes.ProgressShape)
                .height(8.dp)
        )
    }
}

@Composable
fun NutritionStatusChip(
    status: String,
    color: Color = TechColors.NeonBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .nutritionChip(true, color),
        color = Color.Transparent
    ) {
        Text(
            text = status,
            style = NutritionTextStyles.Caption,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun NutritionLegend(
    items: List<Pair<String, Color>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = label,
                    style = NutritionTextStyles.Caption
                )
            }
        }
    }
}

// 營養狀態評估
fun getNutritionStatus(
    actual: Double,
    target: Double,
    tolerance: Double = 0.1
): Pair<String, Color> {
    val ratio = actual / target
    
    return when {
        ratio >= 1.0 - tolerance && ratio <= 1.0 + tolerance -> "目標達成" to NutritionColors.Excellent
        ratio >= 0.9 - tolerance && ratio <= 1.1 + tolerance -> "接近目標" to NutritionColors.Good
        ratio >= 0.8 - tolerance && ratio <= 1.2 + tolerance -> "需要改善" to NutritionColors.Average
        ratio >= 0.7 - tolerance && ratio <= 1.3 + tolerance -> "需要注意" to NutritionColors.Poor
        else -> "嚴重偏差" to NutritionColors.Critical
    }
}

// 營養建議生成
fun generateNutritionAdvice(
    calories: Double,
    protein: Double,
    targetCalories: Double,
    targetProtein: Double
): List<String> {
    val advice = mutableListOf<String>()
    
    val calorieStatus = getNutritionStatus(calories, targetCalories)
    val proteinStatus = getNutritionStatus(protein, targetProtein)
    
    when (calorieStatus.first) {
        "目標達成" -> advice.add("熱量攝取很完美，繼續保持！")
        "接近目標" -> advice.add("熱量攝取接近目標，微調即可達成。")
        "需要改善" -> advice.add("熱量攝取需要調整，建議控制份量。")
        "需要注意" -> advice.add("熱量攝取偏差較大，建議重新規劃飲食。")
        "嚴重偏差" -> advice.add("熱量攝取嚴重偏差，建議諮詢營養師。")
    }
    
    when (proteinStatus.first) {
        "目標達成" -> advice.add("蛋白質攝取充足，有助於肌肉維持！")
        "接近目標" -> advice.add("蛋白質攝取接近目標，增加少量即可。")
        "需要改善" -> advice.add("蛋白質攝取不足，建議增加高蛋白食物。")
        "需要注意" -> advice.add("蛋白質攝取明顯不足，影響身體恢復。")
        "嚴重偏差" -> advice.add("蛋白質攝取嚴重不足，建議立即調整。")
    }
    
    return advice
}
