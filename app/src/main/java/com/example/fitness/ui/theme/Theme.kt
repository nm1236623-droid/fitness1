package com.example.fitness.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.fitness.designsystem.DsProvideTokens

// ===== 統一文字顏色 Token =====
data class AppTextColors(
    val primary: Color,          // 主要文字
    val secondary: Color,        // 次要文字
    val tertiary: Color,         // 第三層文字（提示）
    val onPrimary: Color,        // 在主色按鈕上的文字
    val onSurface: Color,        // 在表面上的文字
    val onSurfaceVariant: Color, // 在變體表面上的文字
    val link: Color,             // 連結文字
    val error: Color,            // 錯誤文字
    val success: Color,          // 成功文字
    val warning: Color,          // 警告文字
)

val LocalAppTextColors = staticCompositionLocalOf {
    AppTextColors(
        primary = Color(0xFF0F172A),       // Slate 900 高對比主要文字
        secondary = Color(0xFF334155),     // Slate 700 次要文字
        tertiary = Color(0xFF64748B),      // Slate 500 第三層文字
        onPrimary = Color.White,
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF334155),
        link = Color(0xFF4F46E5),
        error = Color(0xFFDC2626),
        success = Color(0xFF16A34A),
        warning = Color(0xFFD97706),
    )
}

// 潔白頁面與超高對比配色方案
private val DarkColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),        // Indigo 600
    secondary = Color(0xFF7C3AED),      // Purple 600
    tertiary = Color(0xFF0284C7),       // Cyan 600
    background = Color(0xFFFFFFFF),     // 純白頁面背景
    surface = Color(0xFFFFFFFF),        // 純白卡片背景
    surfaceVariant = Color(0xFFF8FAFC), // Slate 50 變體表面
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),   // Slate 900 超高對比文字
    onSurface = Color(0xFF0F172A),      // Slate 900
    onSurfaceVariant = Color(0xFF334155), // Slate 700
    primaryContainer = Color(0xFFEEF2FF), // Indigo 50
    onPrimaryContainer = Color(0xFF1E1B4B), // Indigo 950
    secondaryContainer = Color(0xFFF3E8FF), // Purple 50
    onSecondaryContainer = Color(0xFF581C87), // Purple 900
    outline = Color(0xFFCBD5E1),        // Slate 300
    error = Color(0xFFDC2626),          // Red 600
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),        // Indigo 600
    secondary = Color(0xFF7C3AED),      // Purple 600
    tertiary = Color(0xFF0284C7),       // Cyan 600
    background = Color(0xFFFFFFFF),     // 純白頁面背景
    surface = Color(0xFFFFFFFF),        // 純白卡片背景
    surfaceVariant = Color(0xFFF8FAFC), // Slate 50 變體表面
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),   // Slate 900 超高對比文字
    onSurface = Color(0xFF0F172A),      // Slate 900
    onSurfaceVariant = Color(0xFF334155), // Slate 700
    primaryContainer = Color(0xFFEEF2FF), // Indigo 50
    onPrimaryContainer = Color(0xFF1E1B4B), // Indigo 950
    secondaryContainer = Color(0xFFF3E8FF), // Purple 50
    onSecondaryContainer = Color(0xFF581C87), // Purple 900
    outline = Color(0xFFCBD5E1),        // Slate 300
    error = Color(0xFFDC2626),          // Red 600
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun FitnessTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val textColors = AppTextColors(
        primary = Color(0xFF0F172A),       // 主要文字 - Slate 900
        secondary = Color(0xFF334155),     // 次要文字 - Slate 700
        tertiary = Color(0xFF64748B),      // 第三層文字 - Slate 500
        onPrimary = Color.White,
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF334155),
        link = colorScheme.primary,
        error = Color(0xFFDC2626),
        success = Color(0xFF16A34A),
        warning = Color(0xFFD97706),
    )

    CompositionLocalProvider(LocalAppTextColors provides textColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes
        ) {
            DsProvideTokens(darkTheme = false) {
                content()
            }
        }
    }
}

@Composable
fun FitnessTheme(
    themeMode: ThemeMode,
    colorScheme: ColorScheme,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val primary = remember(colorScheme) { colorScheme.toPrimaryColor().copy(alpha = 1f) }

    // 用選到的 primary 生成「一整套」配色，確保全 App 的按鈕/Tab/Icon/Progress 都一致
    val appliedScheme = remember(baseScheme, primary) {
        baseScheme.withPrimary(primary = primary)
    }

    // 統一文字顏色
    val textColors = AppTextColors(
        primary = Color(0xFF0F172A),       // 主要文字 - Slate 900 深黑高對比
        secondary = Color(0xFF334155),     // 次要文字 - Slate 700
        tertiary = Color(0xFF64748B),      // 第三層文字 - Slate 500
        onPrimary = Color.White,
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF334155),
        link = primary,
        error = Color(0xFFDC2626),
        success = Color(0xFF16A34A),
        warning = Color(0xFFD97706),
    )

    CompositionLocalProvider(LocalAppTextColors provides textColors) {
        MaterialTheme(
            colorScheme = appliedScheme,
            typography = Typography,
            shapes = Shapes
        ) {
            DsProvideTokens(darkTheme = false) {
                content()
            }
        }
    }
}

/**
 * 以 primary 為主，推導出容器色與 on* 色，避免 UI 出現低對比/看不清楚。
 * 規則：
 * - onPrimary/onPrimaryContainer 依 luminance 自動黑/白
 * - container 用 primary 的 alpha 混合 (簡單且穩定)
 */
private fun androidx.compose.material3.ColorScheme.withPrimary(
    primary: Color,
): androidx.compose.material3.ColorScheme {
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White

    val primaryContainer = primary.copy(alpha = 0.22f).compositeOver(background)
    val onPrimaryContainer = if (primaryContainer.luminance() > 0.5f) Color.Black else Color.White

    return copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        // secondary/tertiary 先沿用 base，以免整站全變同一顏色導致層級不清
        // 如果你想更強烈的「全站同色」，可再把 secondary/tertiary 也指向 primary。
    )
}

/**
 * 將設定中的 ColorScheme 映射為主色。
 * 這裡與 ThemeManager.getPrimaryColor() 保持一致，但不依賴 ThemeManager 的 mutable state。
 */
private fun ColorScheme.toPrimaryColor(): Color = when (this) {
    ColorScheme.NEON_BLUE -> TechColors.NeonBlue
    ColorScheme.PURPLE -> Color(0xFF9C27B0)
    ColorScheme.GREEN -> Color(0xFF4CAF50)
    ColorScheme.ORANGE -> Color(0xFFFF9800)
    ColorScheme.RED -> Color(0xFFF44336)
    ColorScheme.CUSTOM -> TechColors.NeonBlue
}

/**
 * 方便的擴展屬性，讓任何 @Composable 函數都能輕鬆存取統一的文字顏色
 */
object AppTheme {
    val textColors: AppTextColors
        @Composable
        get() = LocalAppTextColors.current
}

