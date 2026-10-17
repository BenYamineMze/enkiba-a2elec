package com.enkiba.app.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.enkiba.app.ui.components.formatKmf
import java.io.File

/**
 * Partage d'une facture ou d'un devis PDF, de préférence par WhatsApp.
 * Les règles (numéro, message) sont pures et testées ; [share] fait le lien avec Android.
 */
object WhatsAppShare {

    /** Indicatif par défaut : numéros locaux à 7 chiffres des Comores. */
    const val DEFAULT_COUNTRY_CODE = "269"

    private const val FILE_PROVIDER_AUTHORITY = "com.enkiba.app.fileprovider"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b") // WhatsApp, puis WhatsApp Business

    /**
     * Numéro au format WhatsApp (chiffres seuls, indicatif compris, sans « + »), ou null si on ne
     * peut pas le déduire sans risque : on préfère alors laisser choisir le contact dans WhatsApp
     * plutôt que d'ouvrir la conversation d'un inconnu.
     */
    fun normalizePhone(raw: String?, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val international = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.length == 7 -> defaultCountryCode + digits                       // numéro local comorien
            digits.length == 10 && digits.startsWith(defaultCountryCode) -> digits   // 269 + 7 chiffres
            else -> return null                                                     // ambigu (0612…, etc.)
        }
        return international.takeIf { it.length in 8..15 }
    }

    /** Message accompagnant une facture. */
    fun invoiceMessage(clientFirstName: String?, saleRef: String, total: Double, shopName: String): String =
        "${greeting(clientFirstName)}voici votre facture n° $saleRef de $shopName, " +
            "d'un montant de ${formatKmf(total)} KMF. Merci pour votre achat !"

    /** Message accompagnant un devis ; [validUntil] au format « yyyy-MM-dd » ou null. */
    fun quoteMessage(clientFirstName: String?, quoteRef: String, total: Double, shopName: String, validUntil: String?): String {
        val validity = validUntil?.takeIf { it.length == 10 }?.let { " Il est valable jusqu'au ${it.substring(8, 10)}/${it.substring(5, 7)}/${it.substring(0, 4)}." }.orEmpty()
        return "${greeting(clientFirstName)}voici votre devis $quoteRef de $shopName, " +
            "d'un montant de ${formatKmf(total)} KMF.$validity"
    }

    private fun greeting(firstName: String?) =
        firstName?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bonjour $it, " } ?: "Bonjour, "

    /**
     * Envoie le PDF : directement dans la conversation du client si WhatsApp est installé et le
     * numéro connu, sinon dans WhatsApp (choix du contact), sinon via le sélecteur Android.
     * @return false si aucune application ne peut recevoir le fichier
     */
    fun share(activity: Activity, pdf: File, phone: String?, message: String): Boolean {
        val uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, pdf)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val whatsapp = WHATSAPP_PACKAGES.firstOrNull { isInstalled(activity, it) }
        if (whatsapp != null) {
            val direct = Intent(base).setPackage(whatsapp)
            // « jid » : ouvre directement la conversation du numéro (sinon WhatsApp propose ses contacts)
            normalizePhone(phone)?.let { direct.putExtra("jid", "$it@s.whatsapp.net") }
            try {
                activity.startActivity(direct)
                return true
            } catch (e: ActivityNotFoundException) {
                // WhatsApp a refusé l'intention : repli sur le sélecteur
            }
        }
        return try {
            activity.startActivity(Intent.createChooser(base, "Partager le PDF"))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun isInstalled(activity: Activity, pkg: String): Boolean = try {
        activity.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
