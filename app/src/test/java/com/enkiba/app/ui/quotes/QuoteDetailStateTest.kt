package com.enkiba.app.ui.quotes

import com.enkiba.app.data.repository.QuoteDetail
import com.enkiba.app.models.Quote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteDetailStateTest {

    private fun state(status: String?, canEdit: Boolean) = QuoteDetailUiState(
        loading = false,
        detail = QuoteDetail(Quote().apply { this.status = status }, emptyList(), null, null, canEdit),
    )

    @Test
    fun brouillonConvertibleParSonAuteur() {
        val s = state(Quote.STATUS_DRAFT, canEdit = true)
        assertTrue(s.canConvert)
        assertTrue(s.canChangeStatus)
    }

    @Test
    fun autreVendeurNePeutRienModifier() {
        val s = state(Quote.STATUS_DRAFT, canEdit = false)
        assertFalse(s.canConvert)
        assertFalse(s.canChangeStatus)
    }

    @Test
    fun devisConvertiFige() {
        val s = state(Quote.STATUS_CONVERTED, canEdit = true)
        assertFalse(s.canConvert)
        assertFalse(s.canChangeStatus)
    }

    @Test
    fun devisRefuseNonConvertibleMaisStatutModifiable() {
        val s = state(Quote.STATUS_REJECTED, canEdit = true)
        assertFalse(s.canConvert)
        assertTrue(s.canChangeStatus)
    }
}
