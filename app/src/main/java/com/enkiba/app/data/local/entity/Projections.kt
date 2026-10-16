package com.enkiba.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded

// Résultats de requêtes avec jointures ou agrégats (pas des tables)

/** Produit + quantité vendue nette (ventes non supprimées moins les retours). */
data class ProductWithSold(
    @Embedded val product: ProductEntity,
    @ColumnInfo(name = "sold_quantity") val soldQuantity: Int,
)

/** Vente + nom du vendeur (cache local) + montant remboursé. */
data class SaleWithExtras(
    @Embedded val sale: SaleEntity,
    @ColumnInfo(name = "seller_name") val sellerName: String?,
    @ColumnInfo(name = "refunded") val refunded: Double,
)

/** Ligne de vente avec la quantité déjà retournée. */
data class SaleItemWithReturned(
    @Embedded val item: SaleItemEntity,
    @ColumnInfo(name = "returned") val returned: Int,
)

/** Devis + nom du client. */
data class QuoteWithClient(
    @Embedded val quote: QuoteEntity,
    @ColumnInfo(name = "client_name") val clientName: String?,
)

/** Entrée d'historique + noms du produit et de l'utilisateur. */
data class HistoryWithNames(
    @Embedded val history: HistoryEntity,
    @ColumnInfo(name = "product_name") val productName: String?,
    @ColumnInfo(name = "user_name") val userName: String?,
)

/** Vente réduite à sa date, son mode de paiement et son montant net (statistiques). */
data class SaleNetRow(
    @ColumnInfo(name = "sale_date") val saleDate: String?,
    @ColumnInfo(name = "payment_method") val paymentMethod: String?,
    @ColumnInfo(name = "net") val net: Double,
)
