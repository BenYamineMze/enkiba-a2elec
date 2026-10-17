package com.enkiba.app.pdf

import android.graphics.Paint
import com.enkiba.app.data.local.ShopDetails
import com.enkiba.app.pdf.PdfBuilder.Weight

// Éléments communs aux documents : en-tête de boutique, cartes, tableau, encadré du total

/** En-tête : pastille aux initiales, nom et coordonnées à gauche ; titre et références à droite ; filet vert. */
fun PdfBuilder.shopHeader(shop: ShopDetails, title: String, rightLines: List<String>) {
    val top = y
    rect(margin, top, margin + 44f, top + 44f, PdfColors.PASTEL, 11f)
    text(PdfText.initials(shop.name), margin + 22f, top + 28f, paint(15f, PdfColors.GREEN_TEXT, Weight.BOLD, Paint.Align.CENTER))
    val nameX = margin + 56f
    text(shop.name, nameX, top + 16f, paint(15f, weight = Weight.BOLD))
    val small = paint(8.5f, PdfColors.MUTED)
    var ly = top + 29f
    listOfNotNull(shop.address.ifBlank { null }, listOf(shop.phone, shop.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null })
        .forEach { l -> wrap(l, 250f, small, 1).forEach { text(it, nameX, ly, small); ly += 11f } }

    val right = width - margin
    text(title, right, top + 20f, paint(21f, PdfColors.GREEN_TEXT, Weight.BOLD, Paint.Align.RIGHT))
    var ry = top + 33f
    rightLines.forEach { text(it, right, ry, paint(8.5f, PdfColors.MUTED, align = Paint.Align.RIGHT)); ry += 11f }

    y = maxOf(top + 50f, ly, ry) + 6f
    rect(margin, y, width - margin, y + 2.5f, PdfColors.GREEN, 1.25f)
    y += 16f
}

/** Carte pâle avec un titre vert et des lignes de texte ; renvoie sa hauteur. */
fun PdfBuilder.textCard(left: Float, width: Float, top: Float, title: String, lines: List<String>, firstLineBold: Boolean = true): Float {
    val h = 22f + lines.size * 12f + 6f
    rect(left, top, left + width, top + h, PdfColors.CARD, 9f)
    text(title, left + 11f, top + 15f, paint(8.5f, PdfColors.GREEN_TEXT, Weight.MEDIUM))
    lines.forEachIndexed { i, l ->
        val p = if (i == 0 && firstLineBold) paint(10.5f, weight = Weight.BOLD) else paint(8.5f, PdfColors.MUTED)
        text(wrap(l, width - 22f, p, 1).first(), left + 11f, top + 29f + i * 12f, p)
    }
    return h
}

/** Carte pâle « libellé … valeur » alignée à droite ; renvoie sa hauteur. */
fun PdfBuilder.keyValueCard(left: Float, width: Float, top: Float, pairs: List<Pair<String, String>>): Float {
    val h = 12f + pairs.size * 13f + 4f
    rect(left, top, left + width, top + h, PdfColors.CARD, 9f)
    pairs.forEachIndexed { i, (k, v) ->
        val by = top + 20f + i * 13f
        text(k, left + 11f, by, paint(8.5f, PdfColors.MUTED))
        text(wrap(v, width - 90f, paint(8.5f, weight = Weight.MEDIUM), 1).first(), left + width - 11f, by, paint(8.5f, weight = Weight.MEDIUM, align = Paint.Align.RIGHT))
    }
    return h
}

/** Colonne de tableau : largeur relative, alignement. */
data class PdfColumn(val title: String, val weight: Float, val align: Paint.Align = Paint.Align.LEFT)

/** Cellule : texte principal, texte barré facultatif avant (prix catalogue), petite mention orange après. */
data class PdfCell(val text: String, val struck: String? = null, val note: String? = null)

/**
 * Tableau à lignes alternées. La première colonne passe à la ligne si besoin ; l'en-tête
 * est répété en haut de chaque nouvelle page.
 */
fun PdfBuilder.table(columns: List<PdfColumn>, rows: List<List<PdfCell>>, fontSize: Float = 9f) {
    val total = columns.sumOf { it.weight.toDouble() }.toFloat()
    val widths = columns.map { it.weight / total * contentWidth }
    val lefts = widths.runningFold(margin) { acc, w -> acc + w }
    val pad = 8f

    fun header() {
        rect(margin, y, width - margin, y + 20f, PdfColors.PASTEL, 7f)
        columns.forEachIndexed { i, c ->
            val p = paint(8.5f, PdfColors.ON_GREEN, Weight.MEDIUM, c.align)
            text(c.title, anchor(c.align, lefts[i], widths[i], pad), y + 13.5f, p)
        }
        y += 24f
    }
    header()
    val previous = onPageBreak
    onPageBreak = { y += 4f; header() }

    val body = paint(fontSize)
    rows.forEachIndexed { r, cells ->
        val firstLines = wrap(cells[0].text, widths[0] - 2 * pad, body, 3)
        val noteLine = if (cells[0].note != null) 1 else 0
        // Seconde ligne dans une autre colonne (prix barré, mention) : la ligne doit être plus haute
        val extra = if (cells.drop(1).any { it.struck != null || it.note != null }) 1 else 0
        val h = 9f + maxOf(firstLines.size + noteLine, 1 + extra) * (fontSize + 3f) + 4f
        ensure(h)
        if (r % 2 == 1) rect(margin, y - 2f, width - margin, y + h - 2f, PdfColors.ZEBRA)
        var ty = y + fontSize + 3f
        firstLines.forEach { text(it, lefts[0] + pad, ty, body); ty += fontSize + 3f }
        cells[0].note?.let { text(it, lefts[0] + pad, ty, paint(fontSize - 1.5f, PdfColors.ORANGE)) }
        for (i in 1 until cells.size) {
            val c = cells[i]; val col = columns[i]
            val x = anchor(col.align, lefts[i], widths[i], pad)
            val by = y + fontSize + 3f
            text(c.text, x, by, paint(fontSize, align = col.align))
            c.struck?.let { s ->
                val sp = paint(fontSize - 1.5f, PdfColors.STRIKE, align = col.align).apply { isStrikeThruText = true }
                text(s, x, by + fontSize + 3f, sp)
            }
            c.note?.let { text(it, x, by + fontSize + 3f, paint(fontSize - 1.5f, PdfColors.ORANGE, align = col.align)) }
        }
        y += h
        line(margin, y - 2f, width - margin)
    }
    onPageBreak = previous
    y += 8f
}

private fun anchor(align: Paint.Align, left: Float, width: Float, pad: Float) = when (align) {
    Paint.Align.RIGHT -> left + width - pad
    Paint.Align.CENTER -> left + width / 2
    else -> left + pad
}

/** Lignes « libellé / montant » puis encadré vert du total, alignés à droite ; texte libre à gauche. */
fun PdfBuilder.totals(lines: List<Pair<String, String>>, totalLabel: String, totalValue: String, below: String?, leftNotes: List<String>) {
    val boxW = 220f
    val left = width - margin - boxW
    val needed = lines.size * 14f + 40f + (if (below != null) 14f else 0f)
    ensure(needed)
    val top = y
    var ly = top
    lines.forEach { (k, v) ->
        ly += 13f
        text(k, left, ly, paint(9f, PdfColors.MUTED))
        text(v, width - margin, ly, paint(9f, align = Paint.Align.RIGHT))
    }
    ly += 7f
    rect(left, ly, width - margin, ly + 28f, PdfColors.GREEN, 9f)
    text(totalLabel, left + 11f, ly + 18f, paint(10f, PdfColors.ON_GREEN, Weight.BOLD))
    text(totalValue, width - margin - 11f, ly + 19f, paint(13f, PdfColors.ON_GREEN, Weight.BOLD, Paint.Align.RIGHT))
    ly += 28f
    below?.let { ly += 12f; text(it, width - margin, ly, paint(8.5f, PdfColors.MUTED, align = Paint.Align.RIGHT)) }

    var ny = top + 13f
    val np = paint(8.5f, PdfColors.MUTED)
    leftNotes.forEach { n -> wrap(n, left - margin - 16f, np).forEach { text(it, margin, ny, np); ny += 11f } }
    y = maxOf(ly, ny) + 14f
}
