package com.enkiba.app.ui.components

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Dates des ventes : deux formats coexistent en base.
// - créées sur le téléphone : « 2026-10-16T10:09Z » (UTC, à la minute)
// - reçues du serveur : « 2026-10-16T10:09:06.123456+00:00 » (UTC)
// Les deux commencent par « yyyy-MM-ddTHH:mm » en UTC : on compare et on borne sur ce préfixe.

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun utcFormat(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = UTC }

/** Lit une date ISO de l'un des deux formats ; null si illisible. */
fun parseIsoDate(iso: String?): Date? {
    if (iso.isNullOrBlank() || iso.length < 16) return null
    return try {
        // Le préfixe « yyyy-MM-ddTHH:mm » (+ secondes si présentes) suffit : tout est en UTC
        val hasSeconds = iso.length >= 19 && iso[16] == ':'
        if (hasSeconds) utcFormat("yyyy-MM-dd'T'HH:mm:ss").parse(iso.substring(0, 19))
        else utcFormat("yyyy-MM-dd'T'HH:mm").parse(iso.substring(0, 16))
    } catch (e: ParseException) {
        null
    }
}

/** Bornes UTC « yyyy-MM-ddTHH:mm » [début, fin[ d'un jour local (fuseau du téléphone). */
fun localDayRangeUtc(day: Date = Date(), zone: TimeZone = TimeZone.getDefault(), offsetDays: Int = 0): Pair<String, String> {
    val cal = Calendar.getInstance(zone).apply {
        time = day
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.time
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val fmt = utcFormat("yyyy-MM-dd'T'HH:mm")
    return fmt.format(start) to fmt.format(cal.time)
}

/** Date locale du jour au format des devis (« yyyy-MM-dd »). */
fun localDateString(day: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(day)

/** « 14:32 » si c'est aujourd'hui, « 15/10 · 14:32 » sinon (heure locale). */
fun formatSaleMoment(iso: String?, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String {
    val date = parseIsoDate(iso) ?: return iso ?: ""
    val time = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = zone }.format(date)
    return if (localDateString(date, zone) == localDateString(now, zone)) time
    else SimpleDateFormat("dd/MM", Locale.FRENCH).apply { timeZone = zone }.format(date) + " · " + time
}
