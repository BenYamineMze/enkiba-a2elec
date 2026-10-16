package com.enkiba.app.ui.stats

import com.enkiba.app.data.local.entity.SaleNetRow
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.localDateString
import com.enkiba.app.ui.components.parseIsoDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Calculs des statistiques, sans Android : testables en JVM simple.

/** Mois civil ; [month] de 0 (janvier) à 11, comme Calendar. */
data class MonthKey(val year: Int, val month: Int) {
    val shortLabel: String get() = SHORT_MONTHS[month]
    val longLabel: String get() = LONG_MONTHS[month]

    private companion object {
        val SHORT_MONTHS = listOf("Janv.", "Févr.", "Mars", "Avr.", "Mai", "Juin", "Juil.", "Août", "Sept.", "Oct.", "Nov.", "Déc.")
        val LONG_MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    }
}

/** Les [count] derniers mois, du plus ancien au mois en cours. */
fun lastMonths(count: Int, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): List<MonthKey> {
    val cal = Calendar.getInstance(zone).apply { time = now; set(Calendar.DAY_OF_MONTH, 1) }
    cal.add(Calendar.MONTH, -(count - 1))
    return List(count) {
        MonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)).also { cal.add(Calendar.MONTH, 1) }
    }
}

/** Début et fin ([start, end[) d'un mois local, en horodatage UTC « yyyy-MM-ddTHH:mm ». */
fun monthRangeUtc(m: MonthKey, zone: TimeZone = TimeZone.getDefault()): Pair<String, String> {
    val cal = Calendar.getInstance(zone).apply {
        clear(); set(m.year, m.month, 1, 0, 0, 0)
    }
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val start = fmt.format(cal.time)
    cal.add(Calendar.MONTH, 1)
    return start to fmt.format(cal.time)
}

/** Encaissé d'un jour local ; [label] : « Auj. », « Hier » ou « Jeu. 15 ». */
data class DayTotal(val date: String, val label: String, val total: Double, val count: Int)

data class MonthStats(
    val month: MonthKey,
    val net: Double,
    val cash: Double,
    val transfer: Double,
    val salesCount: Int,
    /** Jours avec au moins une vente, le plus récent d'abord. */
    val days: List<DayTotal>,
) {
    /** Part des virements dans la jauge (0 à 1). */
    val transferShare: Float get() = if (net <= 0.0) 0f else (transfer / net).toFloat().coerceIn(0f, 1f)
    val cashShare: Float get() = if (net <= 0.0) 0f else (cash / net).toFloat().coerceIn(0f, 1f)
    val averageBasket: Double get() = if (salesCount == 0) 0.0 else net / salesCount
    val bestDay: DayTotal? get() = days.maxByOrNull { it.total }?.takeIf { it.total > 0 }
    /** Plus gros jour, base des barres (au moins 1 pour éviter la division par zéro). */
    val maxDay: Double get() = days.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0
}

private val DAY_NAMES = listOf("Dim.", "Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.")

fun monthStats(month: MonthKey, rows: List<SaleNetRow>, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): MonthStats {
    val today = localDateString(now, zone)
    val yesterday = localDateString(Date(now.time - 24 * 3600 * 1000L), zone)
    val cal = Calendar.getInstance(zone)
    val days = rows.mapNotNull { r -> parseIsoDate(r.saleDate)?.let { it to r } }
        .groupBy { (date, _) -> localDateString(date, zone) }
        .map { (day, list) ->
            cal.time = list.first().first
            val label = when (day) {
                today -> "Auj."
                yesterday -> "Hier"
                else -> "${DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)}"
            }
            DayTotal(day, label, list.sumOf { it.second.net }, list.size)
        }
        .sortedByDescending { it.date }
    return MonthStats(
        month = month,
        net = rows.sumOf { it.net },
        cash = rows.filter { it.paymentMethod == "espece" }.sumOf { it.net },
        transfer = rows.filter { it.paymentMethod == "virement" }.sumOf { it.net },
        salesCount = rows.size,
        days = days,
    )
}

/** État du stock d'un article : quantité restante par rapport au stock de départ. */
data class StockLine(val product: Product, val total: Int, val percent: Int) {
    val low: Boolean get() = product.quantity <= product.lowStockThreshold
}

/** Articles triés du plus entamé au moins entamé (règle de l'ancien écran). */
fun stockLines(products: List<Product>): List<StockLine> = products.map { p ->
    val total = if (p.initial_stock > 0) p.initial_stock else p.quantity
    val percent = if (total > 0) (p.quantity * 100 / total).coerceIn(0, 100) else 0
    StockLine(p, total, percent)
}.sortedWith(compareBy<StockLine> { it.percent }.thenBy { it.product.name.orEmpty() })
