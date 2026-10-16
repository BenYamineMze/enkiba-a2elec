package com.enkiba.app.data.local

import com.enkiba.app.data.local.entity.ClientEntity
import com.enkiba.app.data.local.entity.HistoryEntity
import com.enkiba.app.data.local.entity.HistoryWithNames
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.ProductWithSold
import com.enkiba.app.data.local.entity.QuoteEntity
import com.enkiba.app.data.local.entity.QuoteItemEntity
import com.enkiba.app.data.local.entity.QuoteWithClient
import com.enkiba.app.data.local.entity.SaleEntity
import com.enkiba.app.data.local.entity.SaleItemEntity
import com.enkiba.app.data.local.entity.SaleItemWithReturned
import com.enkiba.app.data.local.entity.SaleReturnEntity
import com.enkiba.app.data.local.entity.SaleWithExtras
import com.enkiba.app.data.local.entity.StockMovementEntity
import com.enkiba.app.data.local.entity.UserEntity
import com.enkiba.app.models.Client
import com.enkiba.app.models.Product
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.QuoteItem
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItem
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.models.SaleReturn
import com.enkiba.app.models.StockMovement
import com.enkiba.app.models.User

/**
 * Conversions entre les entités Room et les modèles utilisés par les écrans et la synchro.
 * Appelées depuis le Java (DAO historiques) : Mappers.toModel(...), Mappers.toEntity(...).
 */
object Mappers {

    // --- Produits ---

    @JvmStatic
    fun toModel(e: ProductEntity, soldQuantity: Int = 0): Product = Product().apply {
        id = e.id
        shopId = e.shopId
        name = e.name
        category = e.category
        quantity = e.quantity
        price = e.price
        description = e.description
        qr_code = e.qrCode
        initial_stock = e.initialStock
        lowStockThreshold = e.lowStockThreshold
        updatedAt = e.updatedAt
        deletedAt = e.deletedAt
        syncStatus = e.syncStatus
        this.soldQuantity = soldQuantity
    }

    @JvmStatic
    fun toModel(p: ProductWithSold): Product = toModel(p.product, p.soldQuantity)

    @JvmStatic
    fun toEntity(p: Product, quantity: Int, updatedAt: String?, syncStatus: String): ProductEntity = ProductEntity(
        id = p.id,
        shopId = p.shopId,
        name = p.name ?: "",
        category = p.category ?: "",
        quantity = quantity,
        price = p.price,
        description = p.description,
        qrCode = p.qr_code,
        initialStock = p.initial_stock,
        lowStockThreshold = p.lowStockThreshold,
        updatedAt = updatedAt,
        deletedAt = p.deletedAt,
        syncStatus = syncStatus,
    )

    // --- Utilisateurs (cache local) ---

    @JvmStatic
    fun toModel(e: UserEntity): User = User().apply {
        id = e.id
        authId = e.id // v2 : profiles.id = auth.users.id
        username = e.username
        lastname = e.lastname
        telephone = e.telephone
        email = e.email
        role = e.role
        active = e.active
        updatedAt = e.updatedAt
    }

    @JvmStatic
    fun toEntity(u: User): UserEntity = UserEntity(
        id = u.id,
        username = u.username ?: "",
        lastname = u.lastname ?: "",
        telephone = u.telephone ?: "",
        email = u.email,
        role = u.role ?: "user",
        active = u.active,
        updatedAt = u.updatedAt,
    )

    // --- Clients ---

    @JvmStatic
    fun toModel(e: ClientEntity): Client = Client().apply {
        id = e.id
        shopId = e.shopId
        firstname = e.firstname
        lastname = e.lastname
        address = e.address
        phone = e.phone
        email = e.email
        updatedAt = e.updatedAt
        deletedAt = e.deletedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toEntity(c: Client, updatedAt: String?, syncStatus: String): ClientEntity = ClientEntity(
        id = c.id,
        shopId = c.shopId,
        firstname = c.firstname,
        lastname = c.lastname,
        address = c.address,
        phone = c.phone,
        email = c.email,
        updatedAt = updatedAt,
        deletedAt = c.deletedAt,
        syncStatus = syncStatus,
    )

    // --- Ventes ---

    @JvmStatic
    fun toModel(e: SaleEntity): SaleHeader = SaleHeader().apply {
        id = e.id
        shopId = e.shopId
        sellerId = e.sellerId
        clientId = e.clientId
        totalPrice = e.totalPrice
        paymentMethod = e.paymentMethod
        isHasNegotiatedPrice = e.hasNegotiation
        saleDate = e.saleDate
        sourceQuoteId = e.sourceQuoteId
        updatedAt = e.updatedAt
        deletedAt = e.deletedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toModel(s: SaleWithExtras): SaleHeader = toModel(s.sale).apply {
        username = s.sellerName?.takeIf { it.isNotBlank() }
        refundedAmount = s.refunded
    }

    @JvmStatic
    fun toEntity(s: SaleHeader, updatedAt: String?, syncStatus: String): SaleEntity = SaleEntity(
        id = s.id,
        shopId = s.shopId,
        sellerId = s.sellerId ?: "",
        clientId = s.clientId,
        totalPrice = s.totalPrice,
        paymentMethod = s.paymentMethod ?: "espece",
        hasNegotiation = s.isHasNegotiatedPrice,
        saleDate = s.saleDate ?: (updatedAt ?: ""),
        sourceQuoteId = s.sourceQuoteId,
        updatedAt = updatedAt,
        deletedAt = s.deletedAt,
        syncStatus = syncStatus,
    )

    @JvmStatic
    fun toModel(e: SaleItemEntity): SaleItem = SaleItem().apply {
        id = e.id
        shopId = e.shopId
        saleId = e.saleId
        productId = e.productId
        productName = e.productName
        listPrice = e.listPrice
        setUnitPrice(e.unitPrice)
        setQuantity(e.quantity)
        totalPrice = e.totalPrice
        updatedAt = e.updatedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toEntity(i: SaleItem, syncStatus: String): SaleItemEntity = SaleItemEntity(
        id = i.id,
        shopId = i.shopId,
        saleId = i.saleId,
        productId = i.productId,
        productName = i.productName ?: "",
        quantity = i.quantity,
        listPrice = i.listPrice,
        unitPrice = i.unitPrice,
        totalPrice = i.totalPrice,
        updatedAt = i.updatedAt,
        syncStatus = syncStatus,
    )

    /** Ligne de vente nette des retours (quantité et total après retours). */
    @JvmStatic
    fun toRow(r: SaleItemWithReturned): SaleItemRow = SaleItemRow().apply {
        val net = r.item.quantity - r.returned
        id = r.item.id
        productId = r.item.productId
        productName = r.item.productName
        quantity = net
        returnedQuantity = r.returned
        unitPrice = r.item.unitPrice
        total = net * r.item.unitPrice
    }

    // --- Historique ---

    @JvmStatic
    fun toModel(e: HistoryEntity): ProductHistory = ProductHistory().apply {
        id = e.id
        shopId = e.shopId
        productId = e.productId
        userId = e.userId
        operation = e.operation
        oldValue = e.oldValue
        newValue = e.newValue
        timestamp = e.timestamp
        updatedAt = e.updatedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toModel(h: HistoryWithNames): ProductHistory = toModel(h.history).apply {
        productName = h.productName
        username = h.userName
    }

    @JvmStatic
    fun toEntity(h: ProductHistory, shopId: String?, updatedAt: String?, syncStatus: String): HistoryEntity = HistoryEntity(
        id = h.id,
        shopId = h.shopId ?: shopId,
        productId = h.productId,
        userId = h.userId,
        operation = h.operation,
        oldValue = h.oldValue,
        newValue = h.newValue,
        timestamp = h.timestamp ?: (updatedAt ?: ""),
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    // --- Mouvements de stock et retours ---

    @JvmStatic
    fun toModel(e: StockMovementEntity): StockMovement = StockMovement().apply {
        id = e.id
        shopId = e.shopId
        productId = e.productId
        delta = e.delta
        reason = e.reason
        createdBy = e.createdBy
        createdAt = e.createdAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toEntity(m: StockMovement, createdAt: String, syncStatus: String): StockMovementEntity = StockMovementEntity(
        id = m.id,
        shopId = m.shopId,
        productId = m.productId,
        delta = m.delta,
        reason = m.reason,
        createdBy = m.createdBy,
        createdAt = createdAt,
        syncStatus = syncStatus,
    )

    @JvmStatic
    fun toModel(e: SaleReturnEntity): SaleReturn = SaleReturn().apply {
        id = e.id
        shopId = e.shopId
        saleId = e.saleId
        saleItemId = e.saleItemId
        productId = e.productId
        quantity = e.quantity
        refundAmount = e.refundAmount
        createdBy = e.createdBy
        createdAt = e.createdAt
        updatedAt = e.updatedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toEntity(r: SaleReturn, syncStatus: String): SaleReturnEntity = SaleReturnEntity(
        id = r.id,
        shopId = r.shopId,
        saleId = r.saleId,
        saleItemId = r.saleItemId,
        productId = r.productId,
        quantity = r.quantity,
        refundAmount = r.refundAmount,
        createdBy = r.createdBy,
        createdAt = r.createdAt,
        updatedAt = r.updatedAt,
        syncStatus = syncStatus,
    )

    // --- Devis ---

    @JvmStatic
    fun toModel(e: QuoteEntity): Quote = Quote().apply {
        id = e.id
        shopId = e.shopId
        sellerId = e.sellerId
        clientId = e.clientId
        status = e.status
        totalPrice = e.totalPrice
        validUntil = e.validUntil
        notes = e.notes
        convertedSaleId = e.convertedSaleId
        createdAt = e.createdAt
        updatedAt = e.updatedAt
        deletedAt = e.deletedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toModel(q: QuoteWithClient): Quote = toModel(q.quote).apply {
        clientName = q.clientName
    }

    @JvmStatic
    fun toEntity(q: Quote, updatedAt: String?, syncStatus: String): QuoteEntity = QuoteEntity(
        id = q.id,
        shopId = q.shopId,
        sellerId = q.sellerId ?: "",
        clientId = q.clientId,
        status = q.status ?: Quote.STATUS_DRAFT,
        totalPrice = q.totalPrice,
        validUntil = q.validUntil,
        notes = q.notes,
        convertedSaleId = q.convertedSaleId,
        createdAt = q.createdAt,
        updatedAt = updatedAt,
        deletedAt = q.deletedAt,
        syncStatus = syncStatus,
    )

    @JvmStatic
    fun toModel(e: QuoteItemEntity): QuoteItem = QuoteItem().apply {
        id = e.id
        shopId = e.shopId
        quoteId = e.quoteId
        productId = e.productId
        productName = e.productName
        quantity = e.quantity
        listPrice = e.listPrice
        unitPrice = e.unitPrice
        totalPrice = e.totalPrice
        updatedAt = e.updatedAt
        syncStatus = e.syncStatus
    }

    @JvmStatic
    fun toEntity(i: QuoteItem, syncStatus: String): QuoteItemEntity = QuoteItemEntity(
        id = i.id,
        shopId = i.shopId,
        quoteId = i.quoteId,
        productId = i.productId,
        productName = i.productName ?: "",
        quantity = i.quantity,
        listPrice = i.listPrice,
        unitPrice = i.unitPrice,
        totalPrice = i.totalPrice,
        updatedAt = i.updatedAt,
        syncStatus = syncStatus,
    )

    /** Ligne de devis au format des lignes de panier (PDF, conversion en vente). */
    @JvmStatic
    fun toRow(e: QuoteItemEntity): SaleItemRow =
        SaleItemRow(e.productId, e.productName, e.unitPrice, e.quantity)
}
