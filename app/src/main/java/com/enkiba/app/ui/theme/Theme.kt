package com.enkiba.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thème de l'application (Material 3 + couleurs étendues).
 * Pas de couleurs dynamiques (Material You) : l'identité verte reste fixe.
 */
@Composable
fun EnkibaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalEnkibaColors provides if (darkTheme) DarkEnkibaColors else LightEnkibaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = EnkibaTypography,
            shapes = EnkibaShapes,
            content = content,
        )
    }
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
