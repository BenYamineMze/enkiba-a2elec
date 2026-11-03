package com.enkiba.app.export

import com.enkiba.app.data.local.entity.ExportItemRow
import com.enkiba.app.data.local.entity.ExportReturnRow
import com.enkiba.app.data.local.entity.ExportSaleRow
import com.enkiba.app.data.local.entity.ExportStockRow
import com.enkiba.app.data.local.entity.MarginRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class AccountingExportTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private val data = ExportData(
        sales = listOf(
            ExportSaleRow("3f2a9c1b-0000", "2026-10-16T10:00:00+00:00", "Awa Saïd", "Client & Fils", "espece", 4500.0, 1500.0, false, null),
            ExportSaleRow("aa11bb22-0000", "2026-10-17T09:30:00+00:00", "Awa Saïd", null, "virement", 2000.0, 0.0, true, null),
            ExportSaleRow("cc33dd44-0000", "2026-10-18T09:30:00+00:00", "Awa Saïd", null, "espece", 900.0, 0.0, false, "2026-10-18T10:00:00+00:00"),
        ),
        items = listOf(
            ExportItemRow("3f2a9c1b-0000", "2026-10-16T10:00:00+00:00", "Câble <3G2,5>", 3, 1500.0, 1500.0, 4500.0, 1, 1500.0, 1000.0),
            ExportItemRow("aa11bb22-0000", "2026-10-17T09:30:00+00:00", "Prise", 1, 2500.0, 2000.0, 2000.0, 0, 0.0, null),
        ),
        returns = listOf(ExportReturnRow("2026-10-16T11:00:00+00:00", "3f2a9c1b-0000", "Câble <3G2,5>", 1, 1500.0, "Admin")),
        stock = listOf(ExportStockRow("Câble <3G2,5>", "Câbles", 7, 1500.0, 1000.0), ExportStockRow("Prise", "Appareillage", 0, 2500.0, null)),
        margins = listOf(MarginRow("p1", "Câble <3G2,5>", 3000.0, 2, 1000.0), MarginRow("p2", "Prise", 2000.0, 1, null)),
    )

    private fun xlsx(): ByteArray {
        val sheets = AccountingExport.sheets(data, "Ma boutique", "octobre 2026", Date(0), utc)
        return ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()
    }

    private fun entries(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return out
    }

    @Test
    fun classeurValideAvecSixOnglets() {
        val files = entries(xlsx())
        assertTrue(files.containsKey("[Content_Types].xml"))
        assertEquals(6, files.keys.count { it.startsWith("xl/worksheets/sheet") })
        // Chaque partie est un XML bien formé (caractères spéciaux échappés)
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.forEach { (name, xml) -> parser.parse(ByteArrayInputStream(xml.toByteArray())).also { assertTrue(name, it.documentElement != null) } }
        assertTrue(files["xl/workbook.xml"]!!.contains("name=\"Synthèse\""))
        assertTrue(files["xl/worksheets/sheet2.xml"]!!.contains("Client &amp; Fils"))
    }

    @Test
    fun syntheseSansLesVentesAnnulees() {
        val summary = AccountingExport.sheets(data, "Ma boutique", "octobre 2026", Date(0), utc).first()
        fun value(label: String) = summary.rows.first { (it[0] as Cell.Text).value == label }[1]
        assertEquals(Cell.Number(2.0, NumberFormat.INTEGER), value("Ventes"))
        assertEquals(Cell.Number(1.0, NumberFormat.INTEGER), value("Ventes annulées"))
        assertEquals(Cell.Number(6500.0), value("Chiffre d'affaires brut (KMF)"))
        assertEquals(Cell.Number(5000.0), value("Chiffre d'affaires net (KMF)"))
        assertEquals(Cell.Number(3000.0), value("  dont espèces (KMF)"))
        assertEquals(Cell.Number(1000.0), value("Marge brute (KMF)"))
    }

    @Test
    fun outils() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals(25569.0, XlsxWriter.excelSerial(Date(0), utc), 0.0)
        assertEquals("Ventes 2026 10", XlsxWriter.sheetName("Ventes 2026/10"))
    }

    /** Classeur d'exemple pour l'ouvrir à la main (build/exemple-export.xlsx). */
    @Test
    fun exempleSurDisque() {
        File("build/exemple-export.xlsx").writeBytes(xlsx())
    }
}
