package com.enkiba.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfShopFooterTest {

    @Test
    fun piedDePageIgnoreLesChampsVides() {
        assertEquals("A2elec • Moroni • contact@a2elec.km", PDFUtils.shopFooter("A2elec", "Moroni", " ", "contact@a2elec.km"))
        assertEquals("Ma boutique", PDFUtils.shopFooter("Ma boutique", "", null, ""))
    }
}
