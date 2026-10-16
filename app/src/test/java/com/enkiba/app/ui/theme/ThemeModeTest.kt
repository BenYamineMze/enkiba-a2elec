package com.enkiba.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun systemeSuitLeTelephone() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun choixForceIgnoreLeTelephone() {
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
    }
}
