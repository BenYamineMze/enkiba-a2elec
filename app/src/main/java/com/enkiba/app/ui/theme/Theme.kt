package com.enkiba.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thème de l'application (Material 3 + couleurs étendues).
 * Pas de couleurs dynamiques (Material You) : l'identité verte reste fixe.
 */
@Composable
fun EnkibaTheme(
    darkTheme: Boolean = rememberDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Icônes des barres système lisibles même quand le thème choisi diffère de celui du téléphone
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    CompositionLocalProvider(LocalEnkibaColors provides if (darkTheme) DarkEnkibaColors else LightEnkibaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = EnkibaTypography,
            shapes = EnkibaShapes,
            content = content,
        )
    }
}

/** Thème sombre selon le choix de l'utilisateur (menu de l'avatar) et le réglage du téléphone. */
@Composable
fun rememberDarkTheme(): Boolean {
    val mode by ThemePreferences.mode(LocalContext.current).collectAsState()
    return isDarkTheme(mode, isSystemInDarkTheme())
}

/** Accès aux couleurs étendues : `EnkibaTheme.colors.peach`. */
object EnkibaTheme {
    val colors: EnkibaColors
        @Composable @ReadOnlyComposable get() = LocalEnkibaColors.current
}

/** Espacements de la charte. */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    /** Marge latérale des écrans. */
    val screen: Dp = 22.dp
    /** Hauteur minimale d'une zone tactile. */
    val touch: Dp = 44.dp
}
