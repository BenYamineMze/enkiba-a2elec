package com.enkiba.app.export

import java.io.OutputStream
import java.util.Date
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Format d'affichage d'une cellule numérique dans Excel. */
enum class NumberFormat(internal val styleIndex: Int) { INTEGER(2), MONEY(3), PERCENT(4), DATE_TIME(5) }

/** Cellule d'une feuille : texte, nombre ou date (vide = null dans la ligne). */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: Double, val format: NumberFormat = NumberFormat.MONEY) : Cell
    /** Date affichée dans le fuseau [zone] (Excel n'a pas de notion de fuseau). */
    data class DateTime(val value: Date, val zone: TimeZone = TimeZone.getDefault()) : Cell
}

data class Column(val header: String, val width: Double = 14.0)

data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Cell?>>)

/**
 * Écrit un classeur Excel (.xlsx, Office Open XML) sans bibliothèque externe : chaque feuille
 * a une ligne d'en-tête figée et filtrable, les montants restent de vrais nombres.
 * Une bibliothèque complète (Apache POI) ajouterait plusieurs Mo à l'application.
 */
object XlsxWriter {

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "Au moins une feuille" }
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    /** Nombre de jours depuis le 30/12/1899 (date série Excel), à l'heure locale de [zone]. */
    fun excelSerial(date: Date, zone: TimeZone): Double {
        val localMillis = date.time + zone.getOffset(date.time)
        return localMillis / 86_400_000.0 + 25_569.0 // 01/01/1970 = 25569 en date série
    }

    /** Lettres de colonne : 0 → A, 25 → Z, 26 → AA. */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, 'A' + r)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** Nom d'onglet valide : 31 caractères, sans : \ / ? * [ ] */
    fun sheetName(name: String): String = name.replace(Regex("[:\\\\/?*\\[\\]]"), " ").take(31).ifBlank { "Feuille" }

    private fun esc(s: String) = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            // Caractères de contrôle interdits en XML 1.0 (hors tabulation et retours à la ligne)
            c < ' ' && c != '\t' && c != '\n' && c != '\r' -> append(' ')
            else -> append(c)
        }
    }

    private fun sheetXml(s: Sheet): String = buildString {
        val lastCol = columnName((s.columns.size - 1).coerceAtLeast(0))
        append(XML_HEAD)
        append("<worksheet xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        append("<cols>")
        s.columns.forEachIndexed { i, c -> append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${c.width}\" customWidth=\"1\"/>") }
        append("</cols><sheetData>")
        append("<row r=\"1\">")
        s.columns.forEachIndexed { i, c -> append(textCell("${columnName(i)}1", c.header, style = 1)) }
        append("</row>")
        s.rows.forEachIndexed { r, row ->
            val rowNum = r + 2
            append("<row r=\"$rowNum\">")
            row.forEachIndexed { i, cell ->
                val ref = "${columnName(i)}$rowNum"
                when (cell) {
                    null -> Unit
                    is Cell.Text -> append(textCell(ref, cell.value))
                    is Cell.Number -> if (cell.value.isFinite()) {
                        append("<c r=\"$ref\" s=\"${cell.format.styleIndex}\"><v>${cell.value}</v></c>")
                    }
                    is Cell.DateTime -> append(
                        "<c r=\"$ref\" s=\"${NumberFormat.DATE_TIME.styleIndex}\"><v>${excelSerial(cell.value, cell.zone)}</v></c>"
                    )
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.rows.isNotEmpty()) append("<autoFilter ref=\"A1:$lastCol${s.rows.size + 1}\"/>")
        append("</worksheet>")
    }

    private fun textCell(ref: String, value: String, style: Int = 0) =
        "<c r=\"$ref\" t=\"inlineStr\"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"

    private fun contentTypes(count: Int) = buildString {
        append(XML_HEAD)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..count) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(XML_HEAD)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        val used = mutableSetOf<String>()
        sheets.forEachIndexed { i, s ->
            // Deux onglets ne peuvent pas porter le même nom
            var name = sheetName(s.name)
            var n = 2
            while (!used.add(name.lowercase())) name = sheetName(s.name).take(28) + " " + n++
            append("<sheet name=\"${esc(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append(XML_HEAD)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..count) {
            append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${count + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    // Styles : 0 normal, 1 en-tête (gras, fond vert pâle), 2 entier, 3 montant, 4 pourcentage, 5 date et heure
    private const val STYLES = XML_HEAD +
        "<styleSheet xmlns=\"$NS_MAIN\">" +
        "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy hh:mm\"/><numFmt numFmtId=\"165\" formatCode=\"#,##0\"/></numFmts>" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDF5EA\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"6\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
        "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"9\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
