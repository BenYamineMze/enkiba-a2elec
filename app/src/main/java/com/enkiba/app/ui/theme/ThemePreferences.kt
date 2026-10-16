package com.enkiba.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Choix du thème par l'utilisateur ; SYSTEM suit le réglage du téléphone. */
enum class ThemeMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

/** Thème sombre à appliquer selon le choix et le réglage du téléphone. */
fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Choix du thème, mémorisé sur le téléphone (propre à l'appareil, pas au compte).
 * Exposé en StateFlow : tous les écrans ouverts changent de thème immédiatement.
 */
object ThemePreferences {

    private const val PREFS = "enkiba_ui"
    private const val KEY_MODE = "theme_mode"

    private val state = MutableStateFlow(ThemeMode.SYSTEM)
    @Volatile private var loaded = false

    fun mode(context: Context): StateFlow<ThemeMode> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val saved = prefs(context).getString(KEY_MODE, null)
                    state.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
                    loaded = true
                }
            }
        }
        return state.asStateFlow()
    }

    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
        loaded = true
        state.value = mode
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
