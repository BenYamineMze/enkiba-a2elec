package com.enkiba.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.abs
import kotlin.math.roundToLong

// Franc comorien : pas de centimes. Séparateur des milliers : espace fine insécable,
// pour qu'un montant ne soit jamais coupé en fin de ligne.
private const val THIN_NBSP = ' '

/** 12500.0 → « 12 500 » (arrondi à l'unité). */
fun formatKmf(amount: Double): String {
    val rounded = amount.roundToLong()
    val digits = abs(rounded).toString()
    val grouped = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(THIN_NBSP)
        grouped.append(c)
    }
    return if (rounded < 0) "-$grouped" else grouped.toString()
}

/** Format court pour les tuiles : 1 240 000 → « 1,24 M », 84 500 → « 84,5 k ». */
fun formatKmfCompact(amount: Double): String {
    val a = abs(amount)
    val sign = if (amount < 0) "-" else ""
    fun fr(v: Double, decimals: Int) = "%.${decimals}f".format(java.util.Locale.FRANCE, v).trimEnd('0').trimEnd(',')
    return when {
        a >= 1_000_000 -> "$sign${fr(a / 1_000_000, 2)}${THIN_NBSP}M"
        a >= 10_000 -> "$sign${fr(a / 1_000, 1)}${THIN_NBSP}k"
        else -> formatKmf(amount)
    }
}

/** Montant suivi de « KMF » en plus petit et en couleur secondaire. */
@Composable
fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = LocalContentColor.current,
    currencyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showCurrency: Boolean = true,
) {
    Text(
        text = buildAnnotatedString {
            append(formatKmf(amount))
            if (showCurrency) {
                withStyle(SpanStyle(fontSize = style.fontSize * 0.5f, color = currencyColor)) { append(" KMF") }
            }
        },
        modifier = modifier,
        style = style,
        color = color,
    )
}
