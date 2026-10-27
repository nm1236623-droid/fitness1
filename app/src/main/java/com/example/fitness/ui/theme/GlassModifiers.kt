package com.example.fitness.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * 高科技白色簡約 UI 顏色常量
 */
object TechColors {
    // 亮色/白色漸層頁面背景
    val DarkBlue = Color(0xFFFFFFFF)      // 純白背景
    val DeepPurple = Color(0xFFF8FAFC)     // 亮 Slate 50 背景
    
    // 品牌與按鈕強調色
    val NeonBlue = Color(0xFF4F46E5)       // 經典靛藍 Primary
    val NeonBlueDim = Color(0xFF4F46E5).copy(alpha = 0.6f)
    
    // 霓虹鮮豔色系
    val NeonGreen = Color(0xFF16A34A)      // 活力綠
    val NeonPurple = Color(0xFF7C3AED)     // 紫色
    val NeonOrange = Color(0xFFEA580C)     // 橙色
    val NeonYellow = Color(0xFFD97706)     // 黃色
    val NeonRed = Color(0xFFDC2626)        // 紅色
    val NeonGray = Color(0xFF64748B)       // 灰色
    
    // 亮色高對比卡片漸層
    val GlassGradient = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF8FAFC)
    )
}

/**
 * 潔白高對比 UI 卡片效果
 */
fun Modifier.glassEffect(cornerRadius: Dp = 24.dp) = this.then(
    Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(cornerRadius))
        .background(
            Color.White,
            shape = RoundedCornerShape(cornerRadius)
        )
)

/**
 * 非 Composable 版本的潔白效果
 */
fun Modifier.glassEffectStatic(cornerRadius: Dp = 24.dp) = this.then(
    Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(cornerRadius))
        .background(
            Color.White,
            shape = RoundedCornerShape(cornerRadius)
        )
)

/**
 * 主題感知的玻璃效果：自動根據深色/淺色模式調整顏色
 */
@Composable
fun Modifier.glassEffectAuto(cornerRadius: Dp = 24.dp): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.luminance() < 0.5f

    val borderColor = if (isDark) Color.White.copy(0.3f) else Color.Black.copy(0.1f)
    val gradientColors = if (isDark) {
        listOf(Color.White.copy(0.1f), Color.White.copy(0.05f))
    } else {
        listOf(Color.Black.copy(0.03f), Color.Black.copy(0.01f))
    }

    return this.then(
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(gradientColors),
                shape = RoundedCornerShape(cornerRadius)
            )
    )
}

/**
 * Theme-aware 的玻璃效果：用 MaterialTheme.colorScheme 產生更一致的結果。
 *
 * 注意：Modifier 擴充本身不需要是 @Composable。
 * 呼叫端可傳入 MaterialTheme.colorScheme 的顏色（在 composable 內取得）。
 */
fun Modifier.glassEffectThemed(
    scheme: ColorScheme,
    cornerRadius: Dp = 24.dp
) = this.then(
    Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .border(
            1.dp,
            scheme.outline.copy(alpha = 0.6f),
            RoundedCornerShape(cornerRadius)
        )
        .background(
            Brush.linearGradient(
                listOf(
                    scheme.surface.copy(alpha = 0.18f),
                    scheme.surface.copy(alpha = 0.08f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
)

/**
 * 霓虹邊框發光效果修飾符
 * 用於重要操作按鈕
 */
fun Modifier.neonGlowBorder(
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 2.dp,
    glowColor: Color = TechColors.NeonBlue
) = this.then(
    Modifier
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                listOf(
                    glowColor.copy(alpha = 0.8f),
                    glowColor.copy(alpha = 0.4f),
                    glowColor.copy(alpha = 0.8f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
)

/**
 * Theme-aware 的霓虹邊框：預設用 colorScheme.primary。
 *
 * 注意：這個函數不是 composable，呼叫端請傳入 MaterialTheme.colorScheme。
 */
fun Modifier.neonGlowBorderThemed(
    scheme: ColorScheme,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 2.dp,
    glowColor: Color = scheme.primary
) = neonGlowBorder(
    cornerRadius = cornerRadius,
    borderWidth = borderWidth,
    glowColor = glowColor
)

/**
 * 便利版本：在 Composable 中直接呼叫即可（自動讀取 MaterialTheme.colorScheme）。
 */
@Composable
fun Modifier.glassEffectThemedM3(
    cornerRadius: Dp = 24.dp
) = glassEffectThemed(
    scheme = MaterialTheme.colorScheme,
    cornerRadius = cornerRadius
)

/**
 * 便利版本：在 Composable 中直接呼叫即可（自動讀取 MaterialTheme.colorScheme）。
 */
@Composable
fun Modifier.neonGlowBorderThemedM3(
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 2.dp,
    glowColor: Color = MaterialTheme.colorScheme.primary
) = neonGlowBorderThemed(
    scheme = MaterialTheme.colorScheme,
    cornerRadius = cornerRadius,
    borderWidth = borderWidth,
    glowColor = glowColor
)

/**
 * 主題感知的顏色提供器
 * 根據深色/淺色模式返回適當的顏色
 */
object AppColors {
    /**
     * 主要文字顏色
     */
    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onBackground

    /**
     * 次要文字顏色
     */
    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    /**
     * 主要強調色（按鈕、圖標等）
     */
    val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    /**
     * 背景色
     */
    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    /**
     * 表面色（卡片等）
     */
    val surface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    /**
     * 卡片背景色（帶透明度）
     */
    val cardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            Color.White.copy(alpha = 0.06f)
        } else {
            Color.Black.copy(alpha = 0.04f)
        }

    /**
     * 邊框顏色
     */
    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    /**
     * 分隔線顏色
     */
    val divider: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
}

/**
 * 檢查當前是否為深色主題
 */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean {
    return MaterialTheme.colorScheme.background.luminance() < 0.5f
}

/**
 * 根據主題返回適當的文字顏色
 */
@Composable
@ReadOnlyComposable
fun themedTextColor(): Color = AppColors.textPrimary

/**
 * 根據主題返回適當的次要文字顏色
 */
@Composable
@ReadOnlyComposable
fun themedSecondaryTextColor(): Color = AppColors.textSecondary

// 計算 luminance 的輔助函數
private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}
