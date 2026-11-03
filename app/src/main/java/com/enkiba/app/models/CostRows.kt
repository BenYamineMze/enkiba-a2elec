package com.enkiba.app.models

import com.google.gson.annotations.SerializedName

/** Ligne de public.product_costs (admins seulement). */
data class ProductCostRow(
    @SerializedName("product_id") val productId: String?,
    @SerializedName("shop_id") val shopId: String?,
    @SerializedName("cost_price") val costPrice: Double?,
    @SerializedName("updated_at") val updatedAt: String?,
)

/** Ligne de public.sale_item_costs : prix d'achat figé par le serveur (admins seulement). */
data class SaleItemCostRow(
    @SerializedName("sale_item_id") val saleItemId: String?,
    @SerializedName("shop_id") val shopId: String?,
    @SerializedName("cost_price") val costPrice: Double?,
    @SerializedName("updated_at") val updatedAt: String?,
)
