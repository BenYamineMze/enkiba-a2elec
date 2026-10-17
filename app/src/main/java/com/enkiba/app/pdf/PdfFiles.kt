package com.enkiba.app.pdf

import android.content.Context
import android.os.Environment
import java.io.File

/** Rangement des PDF dans Documents/Enkiba de l'app (partagés via le FileProvider). */
enum class PdfFolder(val dirName: String) {
    INVOICES("Factures"),
    QUOTES("Devis"),
    REPORTS("Rapports"),
    LABELS("Etiquettes"),
    ;

    fun file(context: Context, fileName: String): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Enkiba/$dirName")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }
}
