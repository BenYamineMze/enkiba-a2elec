package com.enkiba.app.ui.history

import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.localDateString
import com.enkiba.app.ui.components.parseIsoDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Onglets de l'historique (les opérations sont réservées aux admins). */
enum class HistoryTab(val label: String) { SALES("Ventes"), QUOTES("Devis"), OPERATIONS("Opérations") }

/** Filtre des devis par statut (« expiré » est calculé à partir de la validité). */
enum class QuoteFilter(val label: String) {
    ALL("Tous"), DRAFT("Brouillon"), ACCEPTED("Accepté"), CONVERTED("Converti"), EXPIRED("Expiré"), REJECTED("Refusé");

    fun matches(q: Quote): Boolean = when (this) {
        ALL -> true
        EXPIRED -> q.isExpired
        DRAFT -> !q.isExpired && q.status == Quote.STATUS_DRAFT
        ACCEPTED -> !q.isExpired && q.status == Quote.STATUS_ACCEPTED
        CONVERTED -> q.status == Quote.STATUS_CONVERTED
        REJECTED -> q.status == Quote.STATUS_REJECTED
    }
}

/** Groupe d'éléments d'un même jour (« Aujourd'hui », « Hier », « Mercredi 14 octobre »). */
data class DayGroup<T>(val label: String, val items: List<T>)

/** Règles de l'historique (sans Android : testées en unitaire). */
object HistoryFilters {

    private fun String?.has(q: String) = this?.lowercase(Locale.ROOT)?.contains(q) == true

    /** Recherche : référence (début de l'UUID), vendeur, mode de paiement. */
    fun filterSales(sales: List<SaleHeader>, query: String): List<SaleHeader> {
        val q = query.trim().lowercase(Locale.ROOT).removePrefix("#")
        if (q.isEmpty()) return sales
        return sales.filter { it.id.has(q) || it.username.has(q) || it.paymentMethod.has(q) }
    }

    /** Recherche : référence (DEV-…), client ; puis filtre de statut. */
    fun filterQuotes(quotes: List<Quote>, filter: QuoteFilter, query: String): List<Quote> {
        val q = query.trim().lowercase(Locale.ROOT).removePrefix("dev-")
        return quotes.filter { filter.matches(it) && (q.isEmpty() || it.id.has(q) || it.clientName.has(q)) }
    }

    /** Recherche : article, opération, utilisateur, valeurs. */
    fun filterOperations(ops: List<ProductHistory>, query: String): List<ProductHistory> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return ops
        return ops.filter { it.productName.has(q) || it.operation.has(q) || it.username.has(q) || it.oldValue.has(q) || it.newValue.has(q) }
    }

    /** Libellé du jour local d'une date ISO. */
    fun dayLabel(iso: String?, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String {
        val date = parseIsoDate(iso) ?: return "Date inconnue"
        val day = localDateString(date, zone)
        if (day == localDateString(now, zone)) return "Aujourd'hui"
        val yesterday = Calendar.getInstance(zone).apply { time = now; add(Calendar.DAY_OF_YEAR, -1) }.time
        if (day == localDateString(yesterday, zone)) return "Hier"
        return SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).apply { timeZone = zone }.format(date)
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }

    /** Regroupe une liste déjà triée (plus récent en premier) par jour local. */
    fun <T> groupByDay(items: List<T>, dateOf: (T) -> String?, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): List<DayGroup<T>> =
        items.groupBy { dayLabel(dateOf(it), now, zone) }.map { (label, list) -> DayGroup(label, list) }
}
