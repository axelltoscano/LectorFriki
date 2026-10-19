package com.panels.reader

import android.os.Build
import android.view.Window
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val DayColors = lightColorScheme(
    primary = Color(0xFF171717), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAEA), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF525252), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0), onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF525252), onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF3F3F3), onSurfaceVariant = Color(0xFF626262),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF858585), outlineVariant = Color(0xFFE2E2E2),
    inverseSurface = Color(0xFF171717), inverseOnSurface = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEAEAEA)
)

private val NightColors = darkColorScheme(
    primary = Color(0xFFF5F5F5), onPrimary = Color.Black,
    primaryContainer = Color(0xFF272727), onPrimaryContainer = Color.White,
    secondary = Color(0xFFBDBDBD), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF202020), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD), onTertiary = Color.Black,
    background = Color.Black, onBackground = Color(0xFFF5F5F5),
    surface = Color.Black, onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF171717), onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF888888), outlineVariant = Color(0xFF303030),
    inverseSurface = Color(0xFFF5F5F5), inverseOnSurface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010), surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222)
)

@Composable
fun LectorTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) NightColors else DayColors, content = content)
}

/** A bottom sheet owns a separate window; it must also use the reader's immersive style. */
@Composable
fun ImmersiveSheetWindow(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        val sheetWindow = (view.parent as? DialogWindowProvider)?.window
        sheetWindow?.let { configureSystemBars(it, reading = true, dark = dark) }
        onDispose { }
    }
}

/** Fullscreen belongs to the reader; the library restores system navigation. */
@Suppress("DEPRECATION")
fun configureSystemBars(window: Window, reading: Boolean, dark: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (reading) controller.hide(WindowInsetsCompat.Type.systemBars())
    else controller.show(WindowInsetsCompat.Type.systemBars())
}
