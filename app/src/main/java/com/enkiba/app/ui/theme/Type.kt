package com.enkiba.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Une seule famille : Plus Jakarta Sans (maquettes v2).
// TODO : ajouter les fichiers de police dans res/font et remplacer FontFamily.Default.
val EnkibaFontFamily: FontFamily = FontFamily.Default

private fun style(size: Int, weight: FontWeight, letterSpacing: Double = 0.0, lineHeight: Int = (size * 1.3).toInt()) =
    TextStyle(
        fontFamily = EnkibaFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

/**
 * Correspondance avec la charte :
 * - displaySmall : montant clé (30, ExtraBold)
 * - headlineMedium/Small : titre d'écran (26 / 24, ExtraBold)
 * - titleLarge : montant de carte (22, ExtraBold)
 * - titleMedium : titre de section (16, ExtraBold)
 * - titleSmall / bodyLarge : nom d'article (15 Bold / 14 SemiBold)
 * - bodyMedium / bodySmall : texte secondaire (13 / 12, Medium)
 * - labelLarge : boutons (15, ExtraBold) ; labelSmall : puces de statut (11, ExtraBold)
 */
val EnkibaTypography = Typography(
    displaySmall = style(30, FontWeight.ExtraBold, -0.8),
    headlineMedium = style(26, FontWeight.ExtraBold, -0.5),
    headlineSmall = style(24, FontWeight.ExtraBold, -0.4),
    titleLarge = style(22, FontWeight.ExtraBold, -0.4),
    titleMedium = style(16, FontWeight.ExtraBold),
    titleSmall = style(15, FontWeight.Bold),
    bodyLarge = style(14, FontWeight.SemiBold),
    bodyMedium = style(13, FontWeight.Medium),
    bodySmall = style(12, FontWeight.Medium),
    labelLarge = style(15, FontWeight.ExtraBold),
    labelMedium = style(13, FontWeight.Bold),
    labelSmall = style(11, FontWeight.ExtraBold, 0.3),
)
