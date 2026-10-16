package com.enkiba.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Entités Room : le schéma de la base locale. Tous les ids sont des UUID (TEXT)
// générés par le téléphone, sauf ceux reçus du serveur. sync_status vaut
// 'pending' (à envoyer) ou 'synced'. deleted_at non nul = suppression logique.

const val SYNC_PENDING = "pending"
const val SYNC_SYNCED = "synced"

@Entity(tableName = "products", indices = [Index("qr_code")])
data class ProductEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    val name: String,
    val category: String,
    // Stock local : stock serveur + opérations locales non encore envoyées
    val quantity: Int,
    val price: Double,
    val description: String?,
    @ColumnInfo(name = "qr_code") val qrCode: String?,
    @ColumnInfo(name = "initial_stock") val initialStock: Int,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

// Cache local des noms d'utilisateurs (affichage des vendeurs) ; jamais synchronisé
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val lastname: String,
    val telephone: String,
    val email: String?,
    val role: String,
    val active: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
)

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    val firstname: String?,
    val lastname: String?,
    val address: String?,
    val phone: String?,
    val email: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

@Entity(tableName = "sales", indices = [Index("sale_date"), Index("seller_id")])
data class SaleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "seller_id") val sellerId: String,
    @ColumnInfo(name = "client_id") val clientId: String?,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "has_negotiation") val hasNegotiation: Boolean,
    @ColumnInfo(name = "sale_date") val saleDate: String,
    @ColumnInfo(name = "source_quote_id") val sourceQuoteId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

// Ligne de vente : jamais modifiée (les retours vont dans sale_returns)
@Entity(tableName = "sale_items", indices = [Index("sale_id"), Index("product_id")])
data class SaleItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "sale_id") val saleId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val quantity: Int,
    @ColumnInfo(name = "list_price") val listPrice: Double,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

// Journal des opérations sur les produits (audit_log côté serveur)
@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    val operation: String?,
    @ColumnInfo(name = "old_value") val oldValue: String?,
    @ColumnInfo(name = "new_value") val newValue: String?,
    val timestamp: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

// Mouvements saisis sur ce téléphone ('initial', 'restock', 'adjustment')
@Entity(tableName = "stock_movements", indices = [Index("product_id")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "product_id") val productId: String,
    val delta: Int,
    val reason: String,
    @ColumnInfo(name = "created_by") val createdBy: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

@Entity(tableName = "sale_returns", indices = [Index("sale_item_id"), Index("sale_id"), Index("product_id")])
data class SaleReturnEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "sale_id") val saleId: String,
    @ColumnInfo(name = "sale_item_id") val saleItemId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    val quantity: Int,
    @ColumnInfo(name = "refund_amount") val refundAmount: Double,
    @ColumnInfo(name = "created_by") val createdBy: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

@Entity(tableName = "quotes", indices = [Index("created_at")])
data class QuoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "seller_id") val sellerId: String,
    @ColumnInfo(name = "client_id") val clientId: String?,
    val status: String,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    @ColumnInfo(name = "valid_until") val validUntil: String?,
    val notes: String?,
    @ColumnInfo(name = "converted_sale_id") val convertedSaleId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

// Ligne de devis : écrite une seule fois, à la création du devis
@Entity(tableName = "quote_items", indices = [Index("quote_id")])
data class QuoteItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "quote_id") val quoteId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val quantity: Int,
    @ColumnInfo(name = "list_price") val listPrice: Double,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)
