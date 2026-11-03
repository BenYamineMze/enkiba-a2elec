package com.enkiba.app.models

import com.google.gson.annotations.SerializedName

/** Ligne de public.chat_messages. */
data class ChatMessageRow(
    @SerializedName("id") val id: String?,
    @SerializedName("shop_id") val shopId: String?,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("deleted_at") val deletedAt: String?,
)
