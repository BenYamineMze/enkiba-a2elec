package com.enkiba.app.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enkiba.app.data.local.ShopDetails
import com.enkiba.app.models.Client
import com.enkiba.app.models.Product
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItemRow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Génère un exemple de chaque PDF sur l'appareil (Documents/Enkiba/…) pour vérifier le rendu :
 * facture longue (plusieurs pages, remises, retours), devis, rapports, étiquettes.
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.enkiba.app.pdf.PdfSamplesTest
 */
@RunWith(AndroidJUnit4::class)
class PdfSamplesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shop = ShopDetails("A2elec", "Moroni, Grande Comore", "269 410 06 07", "contact@a2elec.km")
    private val client = Client().apply { firstname = "Ali"; lastname = "Said"; address = "Itsandra"; phone = "+269 321 23 45" }

    private val names = listOf(
        "Disjoncteur 16A Legrand", "Câble 3G2,5 mm²", "Borne Wago 5 entrées", "Interrupteur simple encastré",
        "Boîte d'encastrement quadruple (pot rouge)", "Moulure 32×12", "Fil 1,5 mm² vert/jaune", "Prise RJ45",
        "Disjoncteur différentiel 63A Legrand avec un nom vraiment très long pour tester le retour à la ligne",
    )

    private fun items(count: Int) = List(count) { i ->
        val price = 1000.0 + i * 750
        SaleItemRow("p$i", names[i % names.size], if (i % 4 == 1) price * 0.9 else price, (i % 5) + 1).apply {
            listPrice = price
            if (i == 3) returnedQuantity = 1
        }
    }

    @Test
    fun genererLesExemples() {
        val lines = items(34)
        val sale = SaleHeader().apply {
            id = "a1b2c3d4-0000-4000-8000-000000000001"
            saleDate = "2026-10-17T11:32:00"
            paymentMethod = "espece"
            totalPrice = lines.sumOf { it.total }
            refundedAmount = 3500.0
        }
        val invoice = EnkibaPdf.invoice(context, sale, lines, client, shop, "Mze Ben")
        assertNotNull(invoice); assertTrue(invoice!!.length() > 0)

        val quoteLines = items(6)
        val quote = Quote().apply {
            createdAt = "2026-10-17T11:32:00"; validUntil = "2026-10-31"
            totalPrice = quoteLines.sumOf { it.total }; notes = "Livraison possible sur Moroni."
        }
        assertNotNull(EnkibaPdf.quote(context, quote, quoteLines, client, shop, "Mze Ben"))

        val sales = List(40) { i ->
            SaleHeader().apply {
                id = "0000000$i-aaaa-4000-8000-000000000000"; saleDate = "2026-10-${(i % 17 + 1).toString().padStart(2, '0')}T0${i % 9}:15:00"
                paymentMethod = if (i % 3 == 0) "virement" else "espece"; totalPrice = 5000.0 + i * 1250; username = if (i % 2 == 0) "Mze Ben" else "Vendeur A"
                if (i == 5) refundedAmount = 2000.0
            }
        }
        assertNotNull(EnkibaPdf.salesReport(context, sales, shop))

        val ops = List(25) { i ->
            ProductHistory().apply {
                productName = names[i % names.size]; operation = if (i % 3 == 0) "Vente" else if (i % 3 == 1) "Ajout de stock" else "Modification du prix"
                timestamp = "2026-10-17T0${i % 9}:00:00"; username = "Mze Ben"; oldValue = "${10 + i}"; newValue = "${8 + i}"
            }
        }
        assertNotNull(EnkibaPdf.operationsReport(context, ops, shop))

        val products = List(14) { i -> Product().apply { name = names[i % names.size]; price = 1500.0 + i * 500; qr_code = "PROD-${(1000 + i)}abc" } }
        assertNotNull(EnkibaPdf.qrSheet(context, products, shop))
    }
}
