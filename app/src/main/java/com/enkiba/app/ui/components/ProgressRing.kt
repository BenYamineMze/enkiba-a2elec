package com.enkiba.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anneau de progression (objectif du jour, stock restant). `progress` entre 0 et 1 ;
 * le contenu (pourcentage, montant) se place au centre.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (p > 0f) {
                drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Barre horizontale arrondie (statistiques par jour, stock). */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(this.size.height / 2)
        drawRoundRect(trackColor, cornerRadius = r)
        if (p > 0f) {
            drawRoundRect(color, size = this.size.copy(width = this.size.width * p), cornerRadius = r)
        }
    }
}
