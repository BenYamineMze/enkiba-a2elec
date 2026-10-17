package com.enkiba.app.ui.products

import com.enkiba.app.models.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodesTest {

    private fun product(name: String, category: String?, qr: String?) = Product().apply {
        setName(name); this.category = category; qr_code = qr
    }

    private val products = listOf(
        product("Câble 2,5 mm²", "Câblage", "QR-CAB-25"),
        product("Ampoule LED 9 W", "Éclairage", "QR-LED-9"),
        product("Disjoncteur 16 A", "Protection", null),
    )

    @Test
    fun sansCodeExclu() = assertEquals(listOf("Câble 2,5 mm²", "Ampoule LED 9 W"), qrFilter(products, "").map { it.name })

    @Test
    fun rechercheNomCategorieEtCode() {
        assertEquals(listOf("Ampoule LED 9 W"), qrFilter(products, "éclair").map { it.name })
        assertEquals(listOf("Câble 2,5 mm²"), qrFilter(products, "cab-25").map { it.name })
        assertEquals(emptyList<String>(), qrFilter(products, "disjoncteur").map { it.name })
    }
}
