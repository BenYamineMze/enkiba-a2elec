package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.export.AccountingExport
import com.enkiba.app.export.ExportData
import com.enkiba.app.export.XlsxWriter
import com.enkiba.app.pdf.PdfFolder
import com.enkiba.app.pdf.PdfText
import com.enkiba.app.ui.stats.MonthKey
import com.enkiba.app.ui.stats.monthRangeUtc
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Export comptable d'un mois (admin) : classeur Excel dans Documents/Enkiba/Exports. */
class ExportRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val session = SessionManager(appContext)

    suspend fun export(month: MonthKey): File = withContext(Dispatchers.IO) {
        val (start, end) = monthRangeUtc(month)
        val dao = db.exportDao()
        val data = ExportData(
            sales = dao.sales(start, end),
            items = dao.items(start, end),
            returns = dao.returns(start, end),
            stock = dao.stock(),
            margins = db.costDao().marginRowsBetween(start, end),
        )
        val shop = ShopStore.get(appContext, session.shopId)
        val period = "${month.longLabel} ${month.year}"
        val stamp = "%04d-%02d".format(month.year, month.month + 1)
        val file = PdfFolder.EXPORTS.file(appContext, "Export_${PdfText.safeFilePart(shop.name)}_$stamp.xlsx")
        FileOutputStream(file).use { XlsxWriter.write(AccountingExport.sheets(data, shop.name, period), it) }
        file
    }
}
