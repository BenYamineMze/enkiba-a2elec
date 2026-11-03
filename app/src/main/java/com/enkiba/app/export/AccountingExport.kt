package com.enkiba.app.export

import com.enkiba.app.data.local.entity.ExportItemRow
import com.enkiba.app.data.local.entity.ExportReturnRow
import com.enkiba.app.data.local.entity.ExportSaleRow
import com.enkiba.app.data.local.entity.ExportStockRow
import com.enkiba.app.data.local.entity.MarginRow
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.paymentLabel
import com.enkiba.app.ui.components.parseIsoDate
import com.enkiba.app.ui.stats.marginStats
import java.util.Date
import java.util.TimeZone

/** Données brutes d'un mois, lues dans la base locale. */
data class ExportData(
    val sales: List<ExportSaleRow>,
    val items: List<ExportItemRow>,
    val returns: List<ExportReturnRow>,
    val stock: List<ExportStockRow>,
    val margins: List<MarginRow>,
)

/**
 * Classeur de l'export comptable : Synthèse, Ventes, Lignes, Retours, Stock, Marge.
 * Sans Android : testé en unitaire. Montants en KMF, dates à l'heure locale.
 */
object AccountingExport {

    fun sheets(
        data: ExportData,
        shopName: String,
        periodLabel: String,
        exportedAt: Date = Date(),
        zone: TimeZone = TimeZone.getDefault(),
    ): List<Sheet> = listOf(
        summary(data, shopName, periodLabel, exportedAt, zone),
        sales(data.sales, zone),
        items(data.items, zone),
        returns(data.returns, zone),
        stock(data.stock),
        margin(data.margins),
    )

    private fun text(s: String?) = s?.takeIf { it.isNotBlank() }?.let { Cell.Text(it) }
    private fun money(v: Double) = Cell.Number(v, NumberFormat.MONEY)
    private fun int(v: Int) = Cell.Number(v.toDouble(), NumberFormat.INTEGER)
    private fun date(iso: String?, zone: TimeZone): Cell? = parseIsoDate(iso)?.let { Cell.DateTime(it, zone) } ?: text(iso)
    private fun ref(id: String) = Cell.Text(SaleHeader.shortRef(id))

    private fun summary(d: ExportData, shopName: String, period: String, exportedAt: Date, zone: TimeZone): Sheet {
        val active = d.sales.filter { it.deletedAt == null }
        val gross = active.sumOf { it.totalPrice }
        val refunds = active.sumOf { it.refunded }
        val m = marginStats(d.margins)
        val rows = mutableListOf<List<Cell?>>(
            listOf(Cell.Text("Boutique"), Cell.Text(shopName)),
            listOf(Cell.Text("Période"), Cell.Text(period)),
            listOf(Cell.Text("Exporté le"), Cell.DateTime(exportedAt, zone)),
            listOf(Cell.Text("Ventes"), int(active.size)),
            listOf(Cell.Text("Ventes annulées"), int(d.sales.size - active.size)),
            listOf(Cell.Text("Chiffre d'affaires brut (KMF)"), money(gross)),
            listOf(Cell.Text("Remboursements (KMF)"), money(refunds)),
            listOf(Cell.Text("Chiffre d'affaires net (KMF)"), money(gross - refunds)),
        )
        // Encaissé net par mode de paiement
        active.groupBy { it.paymentMethod }.toSortedMap().forEach { (method, list) ->
            rows += listOf(Cell.Text("  dont ${paymentLabel(method).lowercase()} (KMF)"), money(list.sumOf { it.totalPrice - it.refunded }))
        }
        rows += listOf(Cell.Text("Marge brute (KMF)"), money(m.margin))
        m.rate?.let { rows += listOf(Cell.Text("Taux de marge"), Cell.Number(it, NumberFormat.PERCENT)) }
        if (m.uncoveredLines > 0) {
            rows += listOf(Cell.Text("Lignes sans prix d'achat (exclues de la marge)"), int(m.uncoveredLines))
        }
        return Sheet("Synthèse", listOf(Column("Indicateur", 44.0), Column("Valeur", 22.0)), rows)
    }

    private fun sales(rows: List<ExportSaleRow>, zone: TimeZone) = Sheet(
        "Ventes",
        listOf(
            Column("Date", 17.0), Column("N°", 11.0), Column("Vendeur", 20.0), Column("Client", 20.0),
            Column("Paiement", 14.0), Column("Montant", 12.0), Column("Remboursé", 12.0), Column("Net", 12.0),
            Column("Prix négocié", 12.0), Column("Statut", 11.0),
        ),
        rows.map { s ->
            val cancelled = s.deletedAt != null
            listOf(
                date(s.saleDate, zone), ref(s.id), text(s.sellerName), text(s.clientName),
                Cell.Text(paymentLabel(s.paymentMethod)), money(s.totalPrice), money(s.refunded),
                money(if (cancelled) 0.0 else s.totalPrice - s.refunded),
                Cell.Text(if (s.hasNegotiation) "Oui" else "Non"), Cell.Text(if (cancelled) "Annulée" else "Validée"),
            )
        },
    )

    private fun items(rows: List<ExportItemRow>, zone: TimeZone) = Sheet(
        "Lignes",
        listOf(
            Column("Date", 17.0), Column("Vente", 11.0), Column("Produit", 30.0), Column("Quantité", 10.0),
            Column("Prix catalogue", 14.0), Column("Prix appliqué", 14.0), Column("Total", 12.0),
            Column("Qté retournée", 13.0), Column("Remboursé", 12.0), Column("Net", 12.0),
            Column("Prix d'achat", 12.0), Column("Marge", 12.0),
        ),
        rows.map { i ->
            val net = i.totalPrice - i.refunded
            listOf(
                date(i.saleDate, zone), ref(i.saleId), Cell.Text(i.productName), int(i.quantity),
                money(i.listPrice), money(i.unitPrice), money(i.totalPrice), int(i.returned), money(i.refunded), money(net),
                i.costPrice?.let(::money), i.costPrice?.let { money(net - it * (i.quantity - i.returned)) },
            )
        },
    )

    private fun returns(rows: List<ExportReturnRow>, zone: TimeZone) = Sheet(
        "Retours",
        listOf(
            Column("Date", 17.0), Column("Vente", 11.0), Column("Produit", 30.0), Column("Quantité", 10.0),
            Column("Remboursé", 12.0), Column("Fait par", 20.0),
        ),
        rows.map { r ->
            listOf(date(r.createdAt, zone), ref(r.saleId), text(r.productName), int(r.quantity), money(r.refundAmount), text(r.byName))
        },
    )

    private fun stock(rows: List<ExportStockRow>) = Sheet(
        "Stock",
        listOf(
            Column("Produit", 30.0), Column("Catégorie", 20.0), Column("Quantité", 10.0), Column("Prix de vente", 13.0),
            Column("Prix d'achat", 12.0), Column("Valeur (vente)", 14.0), Column("Valeur (achat)", 14.0),
        ),
        rows.map { p ->
            val qty = p.quantity.coerceAtLeast(0)
            listOf(
                Cell.Text(p.name), text(p.category), int(p.quantity), money(p.price), p.costPrice?.let(::money),
                money(p.price * qty), p.costPrice?.let { money(it * qty) },
            )
        },
    )

    private fun margin(rows: List<MarginRow>): Sheet {
        val byProduct = rows.filter { it.sold > 0 }.groupBy { it.productId }.map { (_, list) ->
            val covered = list.filter { it.costPrice != null }
            val revenue = list.sumOf { it.revenue }
            val coveredRevenue = covered.sumOf { it.revenue }
            val margin = covered.sumOf { it.revenue - it.costPrice!! * it.sold }
            listOf(
                Cell.Text(list.first().productName), int(list.sumOf { it.sold }), money(revenue),
                covered.takeIf { it.isNotEmpty() }?.let { money(margin) },
                if (coveredRevenue > 0) Cell.Number(margin / coveredRevenue, NumberFormat.PERCENT) else null,
                if (covered.size < list.size) Cell.Text("Prix d'achat manquant sur ${list.size - covered.size} ligne(s)") else null,
            ) to margin
        }.sortedByDescending { it.second }.map { it.first }
        return Sheet(
            "Marge",
            listOf(Column("Produit", 30.0), Column("Vendus", 10.0), Column("CA net", 12.0), Column("Marge", 12.0), Column("Taux", 9.0), Column("Remarque", 34.0)),
            byProduct,
        )
    }
}
