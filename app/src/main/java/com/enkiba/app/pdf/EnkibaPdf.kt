package com.enkiba.app.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import com.enkiba.app.data.local.ShopDetails
import com.enkiba.app.models.Client
import com.enkiba.app.models.Product
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.pdf.PdfBuilder.Weight
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.paymentLabel
import com.enkiba.app.ui.components.parseIsoDate
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.QrUtils
import java.io.File
import java.util.Date

/** Les documents PDF d'Enkiba, au design de l'app. Chaque fonction renvoie le fichier, ou null en cas d'échec. */
object EnkibaPdf {

    private fun kmf(amount: Double) = "${formatKmf(amount)} KMF"
    private fun eur(amount: Double) = "≈ ${PDFUtils.formatEur(PDFUtils.kmfToEur(amount))} €"
    private fun moment(iso: String?) = parseIsoDate(iso)?.let { PdfText.longDateTime(it) } ?: iso.orEmpty()

    private fun clientLines(client: Client?): List<String> {
        if (client == null) return listOf("Client de passage")
        val name = "${client.firstname.orEmpty()} ${client.lastname.orEmpty()}".trim().ifEmpty { "Client" }
        return listOf(name) + listOfNotNull(
            client.address?.takeIf { it.isNotBlank() },
            listOfNotNull(client.phone?.takeIf { it.isNotBlank() }, client.email?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
        )
    }

    private val itemColumns = listOf(
        PdfColumn("Article", 52f),
        PdfColumn("Qté", 10f, Paint.Align.CENTER),
        PdfColumn("Prix unitaire", 19f, Paint.Align.RIGHT),
        PdfColumn("Total", 19f, Paint.Align.RIGHT),
    )

    private fun itemRows(items: List<SaleItemRow>) = items.map { r ->
        val returned = r.returnedQuantity
        listOf(
            PdfCell(r.productName.orEmpty(), note = if (returned > 0) "$returned retourné${if (returned > 1) "s" else ""}" else null),
            PdfCell(r.quantity.toString()),
            PdfCell(formatKmf(r.unitPrice), struck = if (r.isNegotiated) formatKmf(r.listPrice) else null),
            PdfCell(formatKmf(r.total)),
        )
    }

    /** Facture d'une vente : montant net des retours, prix catalogue barré en cas de remise. */
    fun invoice(context: Context, sale: SaleHeader, items: List<SaleItemRow>, client: Client?, shop: ShopDetails, sellerName: String?): File? =
        runCatching {
            val b = PdfBuilder(context, shop)
            b.shopHeader(shop, "Facture", listOf("N° ${sale.shortRef}", moment(sale.saleDate)))
            val gap = 10f
            val leftW = b.contentWidth * 0.56f
            val top = b.y
            val h1 = b.textCard(b.margin, leftW, top, "Facturé à", clientLines(client))
            val h2 = b.keyValueCard(b.margin + leftW + gap, b.contentWidth - leftW - gap, top, listOf(
                "Paiement" to paymentLabel(sale.paymentMethod),
                "Vendeur" to (sellerName?.ifBlank { null } ?: "—"),
                "Boutique" to shop.name,
            ))
            b.y = top + maxOf(h1, h2) + 16f
            b.table(itemColumns, itemRows(items))
            val lines = mutableListOf("Sous-total" to kmf(sale.totalPrice))
            if (sale.refundedAmount > 0) lines += "Remboursé (retours)" to "− ${kmf(sale.refundedAmount)}"
            val notes = buildList {
                if (items.any { it.isNegotiated }) add("Prix barré : prix catalogue avant remise.")
                add("Merci pour votre achat.")
            }
            b.totals(lines, "Net à payer", kmf(sale.netTotal), eur(sale.netTotal), notes)
            val file = PdfFolder.INVOICES.file(context, PdfText.invoiceFileName(sale.shortRef, parseIsoDate(sale.saleDate) ?: Date()))
            file.takeIf { b.writeTo(it) }
        }.getOrNull()

    /** Devis : validité mise en avant et zone « Bon pour accord ». */
    fun quote(context: Context, quote: Quote, items: List<SaleItemRow>, client: Client?, shop: ShopDetails, sellerName: String?): File? =
        runCatching {
            val b = PdfBuilder(context, shop)
            val valid = quote.validUntil?.let { "Valable jusqu'au ${PdfText.shortDate(it)}" }
            b.shopHeader(shop, "Devis", listOfNotNull("N° ${quote.shortRef}", moment(quote.createdAt), valid))
            val gap = 10f
            val leftW = b.contentWidth * 0.56f
            val top = b.y
            val h1 = b.textCard(b.margin, leftW, top, "Devis pour", clientLines(client))
            val h2 = b.keyValueCard(b.margin + leftW + gap, b.contentWidth - leftW - gap, top, listOfNotNull(
                "Établi par" to (sellerName?.ifBlank { null } ?: "—"),
                quote.validUntil?.let { "Validité" to PdfText.shortDate(it) },
                "Boutique" to shop.name,
            ))
            b.y = top + maxOf(h1, h2) + 16f
            b.table(itemColumns, itemRows(items))
            val notes = buildList {
                quote.notes?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (items.any { it.isNegotiated }) add("Prix barré : prix catalogue avant remise.")
                quote.validUntil?.let { add("Ce devis est valable jusqu'au ${PdfText.shortDate(it)}. Au-delà, les prix et la disponibilité peuvent changer.") }
            }
            b.totals(emptyList(), "Total du devis", kmf(quote.totalPrice), eur(quote.totalPrice), notes)

            // Bon pour accord : date et signature du client
            b.ensure(78f)
            val boxTop = b.y
            val half = (b.contentWidth - 10f) / 2
            listOf("Date" to b.margin, "Bon pour accord · signature du client" to b.margin + half + 10f).forEach { (label, x) ->
                b.rect(x, boxTop, x + half, boxTop + 66f, PdfColors.CARD, 9f)
                b.text(label, x + 11f, boxTop + 15f, b.paint(8.5f, PdfColors.GREEN_TEXT, Weight.MEDIUM))
            }
            b.y = boxTop + 76f
            val file = PdfFolder.QUOTES.file(context, PdfText.quoteFileName(quote.shortRef, parseIsoDate(quote.createdAt) ?: Date()))
            file.takeIf { b.writeTo(it) }
        }.getOrNull()

    /** Historique des ventes affichées : période, détail, totaux par mode de paiement. */
    fun salesReport(context: Context, sales: List<SaleHeader>, shop: ShopDetails): File? = runCatching {
        val b = PdfBuilder(context, shop)
        val dates = sales.mapNotNull { parseIsoDate(it.saleDate) }
        val period = if (dates.isEmpty()) null else "Du ${PdfText.longDateTime(dates.min()).substringBefore(",")} au ${PdfText.longDateTime(dates.max()).substringBefore(",")}"
        b.shopHeader(shop, "Ventes", listOfNotNull(period, "${sales.size} vente${if (sales.size > 1) "s" else ""}", "Édité le ${PdfText.longDateTime(Date())}"))
        b.table(
            listOf(
                PdfColumn("Vente", 16f), PdfColumn("Date", 24f), PdfColumn("Paiement", 16f),
                PdfColumn("Vendeur", 24f), PdfColumn("Net", 20f, Paint.Align.RIGHT),
            ),
            sales.map { s ->
                listOf(
                    PdfCell("#${s.shortRef}"),
                    PdfCell(moment(s.saleDate)),
                    PdfCell(paymentLabel(s.paymentMethod)),
                    PdfCell(s.username?.ifBlank { null } ?: "—"),
                    PdfCell(formatKmf(s.netTotal), note = if (s.refundedAmount > 0) "retour − ${formatKmf(s.refundedAmount)}" else null),
                )
            },
            fontSize = 8.5f,
        )
        val byPayment = sales.groupBy { paymentLabel(it.paymentMethod) }.map { (k, v) -> k to kmf(v.sumOf { it.netTotal }) }.sortedBy { it.first }
        val total = sales.sumOf { it.netTotal }
        b.totals(byPayment, "Total encaissé", kmf(total), eur(total), listOf("Montants nets des retours."))
        val file = PdfFolder.REPORTS.file(context, PdfText.reportFileName("Ventes", Date()))
        file.takeIf { b.writeTo(it) }
    }.getOrNull()

    /** Historique des opérations sur les articles (ajouts, ventes, retours, modifications). */
    fun operationsReport(context: Context, ops: List<ProductHistory>, shop: ShopDetails): File? = runCatching {
        val b = PdfBuilder(context, shop)
        b.shopHeader(shop, "Opérations", listOf("${ops.size} opération${if (ops.size > 1) "s" else ""}", "Édité le ${PdfText.longDateTime(Date())}"))
        b.table(
            listOf(
                PdfColumn("Article", 26f), PdfColumn("Opération", 22f), PdfColumn("Date", 18f),
                PdfColumn("Par", 14f), PdfColumn("Avant", 10f, Paint.Align.RIGHT), PdfColumn("Après", 10f, Paint.Align.RIGHT),
            ),
            ops.map { o ->
                listOf(
                    PdfCell(o.productName?.ifBlank { null } ?: "—"),
                    PdfCell(o.operation.orEmpty()),
                    PdfCell(parseIsoDate(o.timestamp)?.let { PdfText.longDateTime(it) } ?: o.timestamp.orEmpty()),
                    PdfCell(o.username?.ifBlank { null } ?: "—"),
                    PdfCell(o.oldValue?.ifBlank { null } ?: "—"),
                    PdfCell(o.newValue?.ifBlank { null } ?: "—"),
                )
            },
            fontSize = 8f,
        )
        val file = PdfFolder.REPORTS.file(context, PdfText.reportFileName("Operations", Date()))
        file.takeIf { b.writeTo(it) }
    }.getOrNull()

    /** Planche d'étiquettes à découper : code QR, nom, prix, code en clair (3 par ligne). */
    fun qrSheet(context: Context, products: List<Product>, shop: ShopDetails): File? = runCatching {
        val b = PdfBuilder(context, shop)
        b.shopHeader(shop, "Étiquettes", listOf("${products.size} article${if (products.size > 1) "s" else ""}", "Édité le ${PdfText.longDateTime(Date())}"))
        val cols = 3
        val gap = 10f
        val cellW = (b.contentWidth - gap * (cols - 1)) / cols
        val cellH = 178f
        val qrSize = 104f
        products.chunked(cols).forEach { row ->
            b.ensure(cellH + gap)
            val top = b.y
            row.forEachIndexed { i, p ->
                val left = b.margin + i * (cellW + gap)
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = PdfColors.RULE; strokeWidth = 0.8f }
                b.draw { it.drawRoundRect(RectF(left, top, left + cellW, top + cellH), 9f, 9f, border) }
                val code = p.qr_code.orEmpty()
                QrUtils.generateQrBitmap(code)?.let { bmp ->
                    val qx = left + (cellW - qrSize) / 2
                    val crisp = Paint().apply { isFilterBitmap = false }
                    b.draw { it.drawBitmap(bmp, null, RectF(qx, top + 8f, qx + qrSize, top + 8f + qrSize), crisp) }
                }
                var ty = top + qrSize + 24f
                val np = b.paint(9f, weight = Weight.MEDIUM, align = Paint.Align.CENTER)
                b.wrap(p.name.orEmpty(), cellW - 16f, np, 2).forEach { b.text(it, left + cellW / 2, ty, np); ty += 11f }
                b.text(kmf(p.price), left + cellW / 2, top + cellH - 22f, b.paint(10f, PdfColors.GREEN_TEXT, Weight.BOLD, Paint.Align.CENTER))
                b.text(code, left + cellW / 2, top + cellH - 9f, b.paint(7f, PdfColors.FAINT, align = Paint.Align.CENTER))
            }
            b.y = top + cellH + gap
        }
        val file = PdfFolder.LABELS.file(context, PdfText.reportFileName("QR", Date()))
        file.takeIf { b.writeTo(it) }
    }.getOrNull()
}
