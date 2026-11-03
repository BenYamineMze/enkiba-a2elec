package com.enkiba.app.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Partage d'un fichier de l'app (e-mail, WhatsApp, Drive…) via le FileProvider. */
object FileShare {

    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** @return false si aucune application ne peut recevoir le fichier */
    fun share(activity: Activity, file: File, mime: String, title: String, subject: String? = null): Boolean {
        val uri = FileProvider.getUriForFile(activity, WhatsAppShare.FILE_PROVIDER_AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            activity.startActivity(Intent.createChooser(send, title))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
