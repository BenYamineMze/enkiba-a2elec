package com.enkiba.app.pdf

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Règles de mise en forme des PDF, sans Android (testées en JVM)
object PdfText {

    /** Initiales de la boutique pour la pastille : « A2elec » → « A2 », « Quincaillerie du Port » → « QP ». */
    fun initials(name: String): String {
        val all = name.trim().split(Regex("[\\s\\-_'’]+")).filter { it.isNotEmpty() }
        // Les petits mots (du, de, la…) ne comptent pas : « Quincaillerie du Port » → « QP »
        val words = all.filter { it.lowercase(Locale.ROOT) !in SMALL_WORDS }.ifEmpty { all }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].filter { it.isLetterOrDigit() }.take(2).uppercase(Locale.ROOT).ifEmpty { "?" }
            else -> words.take(2).joinToString("") { it.first().uppercase(Locale.ROOT) }
        }
    }

    private val SMALL_WORDS = setOf("de", "du", "des", "la", "le", "les", "l", "d", "et", "en", "au", "aux", "a", "à")

    /**
     * Découpe un texte en lignes qui tiennent dans [maxWidth] (mesure fournie par l'appelant).
     * Un mot plus long que la largeur est coupé ; au-delà de [maxLines], la dernière ligne finit par « … ».
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float, maxLines: Int = Int.MAX_VALUE): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) lines += current
            var rest = word
            while (measure(rest) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && measure(rest.take(cut)) > maxWidth) cut--
                lines += rest.take(cut)
                rest = rest.drop(cut)
            }
            current = rest
        }
        if (current.isNotEmpty()) lines += current
        if (lines.size <= maxLines) return lines.ifEmpty { listOf("") }
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && measure("$last…") > maxWidth) last = last.dropLast(1)
        kept[kept.lastIndex] = "${last.trimEnd()}…"
        return kept
    }

    /** « 17 octobre 2026, 14:32 » (heure locale). */
    fun longDateTime(date: Date, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.FRENCH).apply { timeZone = zone }.format(date)

    /** « 31/10/2026 » à partir de « 2026-10-31 », ou le texte tel quel s'il ne suit pas ce format. */
    fun shortDate(isoDay: String?): String =
        if (isoDay != null && isoDay.length >= 10 && isoDay[4] == '-' && isoDay[7] == '-')
            "${isoDay.substring(8, 10)}/${isoDay.substring(5, 7)}/${isoDay.substring(0, 4)}"
        else isoDay.orEmpty()

    /** Nom de fichier sûr : lettres, chiffres, tirets ; le reste devient « - ». */
    fun safeFilePart(text: String): String =
        text.trim().replace(Regex("[^A-Za-z0-9_\\-]+"), "-").trim('-').ifEmpty { "document" }

    // Noms de fichiers rangés par type (voir PdfFiles)
    fun invoiceFileName(ref: String, day: Date, zone: TimeZone = TimeZone.getDefault()) = "Facture_${safeFilePart(ref)}_${dayStamp(day, zone)}.pdf"
    fun quoteFileName(ref: String, day: Date, zone: TimeZone = TimeZone.getDefault()) = "Devis_${safeFilePart(ref)}_${dayStamp(day, zone)}.pdf"
    fun reportFileName(kind: String, now: Date, zone: TimeZone = TimeZone.getDefault()) = "${safeFilePart(kind)}_${minuteStamp(now, zone)}.pdf"

    private fun dayStamp(d: Date, zone: TimeZone) = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(d)
    private fun minuteStamp(d: Date, zone: TimeZone) = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).apply { timeZone = zone }.format(d)
}
