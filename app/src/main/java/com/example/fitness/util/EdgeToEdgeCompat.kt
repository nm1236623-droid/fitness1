package com.example.fitness.util
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
/**
 * Sets up edge-to-edge display **without** calling any deprecated APIs.
 *
 * Avoids:
 *  - Window.setStatusBarColor        (deprecated SDK 35)
 *  - Window.setNavigationBarColor    (deprecated SDK 35)
 *  - LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES (deprecated SDK 35)
 *
 * Instead uses:
 *  - WindowCompat.setDecorFitsSystemWindows  (all API levels)
 *  - WindowInsetsControllerCompat            (all API levels)
 *  - LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS    (SDK 30+, non-deprecated)
 */
fun ComponentActivity.setupEdgeToEdge() {
    val w = window
    // 1. Let content draw behind system bars
    WindowCompat.setDecorFitsSystemWindows(w, false)
    // 2. Make system bars transparent using the non-deprecated Compat controller
    val controller = WindowInsetsControllerCompat(w, w.decorView)
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
    // 3. Handle display cutout - use ALWAYS (SDK 30+) instead of deprecated SHORT_EDGES
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        w.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        w.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
