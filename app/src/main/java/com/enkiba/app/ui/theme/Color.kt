package com.enkiba.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Charte v2 (maquettes validées le 2026-10-16) : vert menthe léger, fonds très pâles,
// cartes blanches, tuiles pastel. Texte sur le vert en vert très foncé (contraste AA).

// --- Thème clair ---
private val Green = Color(0xFF34C98B)          // vert principal : boutons, anneaux, onglet actif
private val OnGreen = Color(0xFF0B3B27)        // texte et icônes posés sur le vert
private val GreenText = Color(0xFF0E7A4F)      // vert lisible sur fond clair : liens, navigation active
private val GreenPastel = Color(0xFFDDF5EA)    // tuiles, sélection
private val Background = Color(0xFFF4F8F6)
private val Surface = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF17241F)
private val TextSecondary = Color(0xFF5E6E68)
private val Outline = Color(0xFFD5E3DD)
private val Orange = Color(0xFFF0904A)         // alerte : stock faible, prix négocié
private val Danger = Color(0xFFB42F2F)

// --- Thème sombre ---
private val DarkBackground = Color(0xFF0F1714)
private val DarkSurface = Color(0xFF18221E)
private val DarkTextPrimary = Color(0xFFE6EFEB)
private val DarkTextSecondary = Color(0xFF9DB0A8)
private val DarkOutline = Color(0xFF2C3A35)

internal val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = OnGreen,
    primaryContainer = GreenPastel,
    onPrimaryContainer = OnGreen,
    secondary = GreenText,
    onSecondary = Color.White,
    tertiary = Orange,
    onTertiary = Color(0xFF3A1D08),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEEF2F0),
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = Color(0xFFE6EEEA),
    error = Danger,
    onError = Color.White,
    inverseSurface = TextPrimary,          // barre du panier, puces sélectionnées
    inverseOnSurface = Color.White,
)

internal val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = OnGreen,
    primaryContainer = Color(0xFF183A2C),
    onPrimaryContainer = Color(0xFFBDEFD7),
    secondary = Color(0xFF6FDDAE),
    onSecondary = OnGreen,
    tertiary = Orange,
    onTertiary = Color(0xFF3A1D08),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF222E29),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF24302B),
    error = Color(0xFFF2A3A3),
    onError = Color(0xFF3B0A0A),
    inverseSurface = Color(0xFFE6EFEB),
    inverseOnSurface = TextPrimary,
)

/** Une tuile pastel : fond, contenu (texte, icône) et fond de la pastille d'icône. */
@Immutable
data class Tile(val container: Color, val content: Color, val iconBackground: Color)

/** Couleurs propres à l'application, absentes du jeu Material 3. */
@Immutable
data class EnkibaColors(
    val mint: Tile,       // ventes, succès, converti
    val peach: Tile,      // alertes, stock faible, expiré
    val sky: Tile,        // devis, accepté, virement
    val lavender: Tile,   // statistiques
    val sand: Tile,       // catégories (éclairage…)
    val neutral: Tile,    // brouillon
    val danger: Tile,     // rupture, refusé
    val warning: Color,   // orange des anneaux et barres en alerte
    val link: Color,      // liens et élément de navigation actif
)

internal val LightEnkibaColors = EnkibaColors(
    mint = Tile(GreenPastel, Color(0xFF2B5E49), Color.White),
    peach = Tile(Color(0xFFFDE9D9), Color(0xFF8A4417), Color.White),
    sky = Tile(Color(0xFFE2EEFB), Color(0xFF1F4E86), Color.White),
    lavender = Tile(Color(0xFFECE7FB), Color(0xFF4A3393), Color.White),
    sand = Tile(Color(0xFFFFF4D6), Color(0xFF6E5100), Color.White),
    neutral = Tile(Color(0xFFEEF2F0), Color(0xFF3E4D48), Color.White),
    danger = Tile(Color(0xFFFBE3E3), Danger, Color.White),
    warning = Orange,
    link = GreenText,
)

internal val DarkEnkibaColors = EnkibaColors(
    mint = Tile(Color(0xFF183A2C), Color(0xFFBDEFD7), Color(0xFF214A39)),
    peach = Tile(Color(0xFF3D2A1C), Color(0xFFFFC9A3), Color(0xFF4E3624)),
    sky = Tile(Color(0xFF1C2E44), Color(0xFFB9D6F7), Color(0xFF253B56)),
    lavender = Tile(Color(0xFF2A2442), Color(0xFFD3C8F7), Color(0xFF362E55)),
    sand = Tile(Color(0xFF3A3218), Color(0xFFF2DD9A), Color(0xFF4A4020)),
    neutral = Tile(Color(0xFF222E29), Color(0xFFC4D2CC), Color(0xFF2C3A35)),
    danger = Tile(Color(0xFF3E1C1C), Color(0xFFF2A3A3), Color(0xFF4E2424)),
    warning = Orange,
    link = Color(0xFF6FDDAE),
)

val LocalEnkibaColors = staticCompositionLocalOf { LightEnkibaColors }
