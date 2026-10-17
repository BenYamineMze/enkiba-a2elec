package com.enkiba.app.ui.products

import com.enkiba.app.models.Product
import java.util.Locale

/** Articles qui ont un code QR, filtrés par nom, catégorie ou code (planche à imprimer). */
fun qrFilter(products: List<Product>, query: String): List<Product> {
    val q = query.trim().lowercase(Locale.ROOT)
    return products.filter { p ->
        !p.qr_code.isNullOrBlank() &&
            (q.isEmpty() || p.name.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                p.category.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                p.qr_code.orEmpty().lowercase(Locale.ROOT).contains(q))
    }
}
