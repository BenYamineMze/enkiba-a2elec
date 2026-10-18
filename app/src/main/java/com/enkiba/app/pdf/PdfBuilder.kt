package com.enkiba.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.enkiba.app.R
import com.enkiba.app.data.local.ShopDetails
import java.io.File
import java.io.FileOutputStream

/** Couleurs des PDF : celles de l'app (vert menthe), sur fond blanc pour l'impression. */
object PdfColors {
    const val GREEN = 0xFF34C98B.toInt()
    const val GREEN_TEXT = 0xFF0E7A4F.toInt()
    const val ON_GREEN = 0xFF0B3B27.toInt()
    const val PASTEL = 0xFFDDF5EA.toInt()
    const val CARD = 0xFFF4F8F6.toInt()
    const val ZEBRA = 0xFFFAFCFB.toInt()
    const val RULE = 0xFFE3ECE8.toInt()
    const val TEXT = 0xFF17241F.toInt()
    const val MUTED = 0xFF5E6E68.toInt()
    const val FAINT = 0xFF8A9893.toInt()
    const val STRIKE = 0xFF9AA8A2.toInt()
    const val ORANGE = 0xFFB4561C.toInt()
}

/**
 * Mise en page A4 (595 × 842 pt). Le contenu est enregistré page par page, puis écrit
 * d'un coup : le pied de page connaît ainsi le nombre total de pages (« Page 2 / 3 »).
 */
class PdfBuilder(private val context: Context, private val shop: ShopDetails) {

    val width = 595f
    val height = 842f
    val margin = 36f
    val contentWidth get() = width - 2 * margin
    private val bottomLimit get() = height - 58f

    private val regular: Typeface = font(R.font.plus_jakarta_sans_regular, Typeface.NORMAL)
    private val medium: Typeface = font(R.font.plus_jakarta_sans_medium, Typeface.NORMAL)
    private val bold: Typeface = font(R.font.plus_jakarta_sans_bold, Typeface.BOLD)

    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    private lateinit var current: MutableList<(Canvas) -> Unit>

    /** Position verticale courante (base de la prochaine ligne). */
    var y = margin

    /** Appelé à chaque nouvelle page (hors la première) : en-tête de tableau répété, etc. */
    var onPageBreak: (() -> Unit)? = null

    init { addPage() }

    private fun font(id: Int, fallback: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.create(Typeface.DEFAULT, fallback)

    fun paint(size: Float, color: Int = PdfColors.TEXT, weight: Weight = Weight.REGULAR, align: Paint.Align = Paint.Align.LEFT) =
        // LINEAR + SUBPIXEL : sans eux, les espaces disparaissent dans les petits textes du PDF
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when (weight) { Weight.REGULAR -> regular; Weight.MEDIUM -> medium; Weight.BOLD -> bold }
            textAlign = align
        }

    enum class Weight { REGULAR, MEDIUM, BOLD }

    private fun addPage() {
        current = mutableListOf()
        pages += current
        y = margin
    }

    /** Nouvelle page si [needed] points ne tiennent plus sur la page courante. */
    fun ensure(needed: Float) {
        if (y + needed > bottomLimit) {
            addPage()
            onPageBreak?.invoke()
        }
    }

    fun draw(op: (Canvas) -> Unit) { current += op }

    fun text(s: String, x: Float, baseline: Float, p: Paint) = draw { it.drawText(s, x, baseline, p) }

    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        draw { if (radius > 0) it.drawRoundRect(RectF(l, t, r, b), radius, radius, p) else it.drawRect(l, t, r, b, p) }
    }

    fun line(x1: Float, yy: Float, x2: Float, color: Int = PdfColors.RULE, stroke: Float = 0.6f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = stroke }
        draw { it.drawLine(x1, yy, x2, yy, p) }
    }

    fun wrap(s: String, maxWidth: Float, p: Paint, maxLines: Int = Int.MAX_VALUE) =
        PdfText.wrap(s, maxWidth, { p.measureText(it) }, maxLines)

    /** Petit logo Enkiba (vectoriel : il reste net à l'impression), dessiné dans le pied de page. */
    private val logo = ContextCompat.getDrawable(context, R.drawable.enkiba_logo)

    /** Écrit le PDF dans [file] avec le pied de page sur chaque page. */
    fun writeTo(file: File): Boolean {
        val doc = PdfDocument()
        return try {
            val footer = PdfText.wrap(shop.footerLine, contentWidth - 200, { paint(8f).measureText(it) }, maxLines = 1).first()
            val faint = paint(8f, PdfColors.FAINT)
            pages.forEachIndexed { index, ops ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt(), index + 1).create())
                val c = page.canvas
                ops.forEach { it(c) }
                val fy = height - 30f
                c.drawLine(margin, fy - 14f, width - margin, fy - 14f, Paint().apply { color = PdfColors.RULE; strokeWidth = 0.6f })
                c.drawText(footer, margin, fy, faint)
                drawSignature(c, fy, "Page ${index + 1} / ${pages.size}", faint)
                doc.finishPage(page)
            }
            FileOutputStream(file).use { doc.writeTo(it) }
            true
        } finally {
            doc.close()
        }
    }

    /** À droite du pied de page : « Page x / n  ·  [logo] Édité avec Enkiba ». L'en-tête reste à la boutique. */
    private fun drawSignature(c: Canvas, baseline: Float, pageLabel: String, p: Paint) {
        val signature = "Édité avec Enkiba"
        val logoSize = 11f
        var x = width - margin - p.measureText(signature)
        c.drawText(signature, x, baseline, p)
        logo?.let {
            x -= logoSize + 4f
            val top = (baseline - 8.5f).toInt()
            it.setBounds(x.toInt(), top, (x + logoSize).toInt(), top + logoSize.toInt())
            it.draw(c)
        }
        c.drawText("$pageLabel  ·  ", x - 3f, baseline, Paint(p).apply { textAlign = Paint.Align.RIGHT })
    }
}
