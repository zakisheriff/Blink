package com.example.blink.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val UberBlackColorScheme =
        darkColorScheme(
                primary = PrimaryColor,
                onPrimary = OnPrimaryColor,
                primaryContainer = SurfaceColor,
                onPrimaryContainer = OnSurfaceColor,
                secondary = SurfaceVariantColor,
                onSecondary = OnSurfaceVariantColor,
                background = BackgroundColor,
                onBackground = OnBackgroundColor,
                surface = SurfaceColor,
                onSurface = OnSurfaceColor,
                surfaceVariant = SurfaceVariantColor,
                onSurfaceVariant = OnSurfaceVariantColor,
                error = ErrorRed,
                onError = PureWhite
        )

@Composable
fun BlinkTheme(
        // Force dark theme for Uber Black look
        darkTheme: Boolean = true,
        dynamicColor: Boolean = false,
        content: @Composable () -> Unit
) {
        val colorScheme = UberBlackColorScheme

        val view = LocalView.current
        if (!view.isInEditMode) {
                SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = colorScheme.background.toArgb()
                        // Status bar icons should be light (white) on the black background
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                                false
                }
        }

        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
