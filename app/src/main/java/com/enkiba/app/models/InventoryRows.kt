package com.enkiba.app.models

import com.google.gson.annotations.SerializedName

/** Ligne de public.inventory_sessions. */
data class InventorySessionRow(
    @SerializedName("id") val id: String?,
    @SerializedName("shop_id") val shopId: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("created_by") val createdBy: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
)

/** Ligne de public.inventory_counts. */
data class InventoryCountRow(
    @SerializedName("id") val id: String?,
    @SerializedName("shop_id") val shopId: String?,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("product_id") val productId: String?,
    @SerializedName("counted") val counted: Int?,
    @SerializedName("expected") val expected: Int?,
    @SerializedName("counted_by") val countedBy: String?,
    @SerializedName("counted_at") val countedAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
)
