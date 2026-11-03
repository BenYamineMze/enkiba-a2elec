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

/** Une ligne de vente de la période, nette des retours, avec son prix d'achat figé (ou null). */
data class MarginRow(
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "revenue") val revenue: Double,
    @ColumnInfo(name = "sold") val sold: Int,
    @ColumnInfo(name = "cost_price") val costPrice: Double?,
)

// ---------------------------------------------------------------- export comptable

data class ExportSaleRow(
    val id: String,
    @ColumnInfo(name = "sale_date") val saleDate: String,
    @ColumnInfo(name = "seller_name") val sellerName: String?,
    @ColumnInfo(name = "client_name") val clientName: String?,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    val refunded: Double,
    @ColumnInfo(name = "has_negotiation") val hasNegotiation: Boolean,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
)

data class ExportItemRow(
    @ColumnInfo(name = "sale_id") val saleId: String,
    @ColumnInfo(name = "sale_date") val saleDate: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val quantity: Int,
    @ColumnInfo(name = "list_price") val listPrice: Double,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    val returned: Int,
    val refunded: Double,
    @ColumnInfo(name = "cost_price") val costPrice: Double?,
)

data class ExportReturnRow(
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "sale_id") val saleId: String,
    @ColumnInfo(name = "product_name") val productName: String?,
    val quantity: Int,
    @ColumnInfo(name = "refund_amount") val refundAmount: Double,
    @ColumnInfo(name = "by_name") val byName: String?,
)

data class ExportStockRow(
    val name: String,
    val category: String?,
    val quantity: Int,
    val price: Double,
    @ColumnInfo(name = "cost_price") val costPrice: Double?,
)
