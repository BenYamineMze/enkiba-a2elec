package com.enkiba.app.models;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Ligne du journal serveur (table audit_log), reçue au pull.
 * Convertie en ProductHistory pour l'affichage local de l'historique des produits.
 */
public class AuditLogEntry {

    @SerializedName("id")
    private String id;
    @SerializedName("shop_id")
    private String shopId;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("entity")
    private String entity;
    @SerializedName("entity_id")
    private String entityId;
    @SerializedName("operation")
    private String operation;
    @SerializedName("old_value")
    private JsonElement oldValue;
    @SerializedName("new_value")
    private JsonElement newValue;
    @SerializedName("created_at")
    private String createdAt;

    public String getCreatedAt() { return createdAt; }

    /** Une valeur jsonb : la chaîne elle-même si c'est une chaîne JSON, sinon son texte JSON. */
    private static String asText(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return e.getAsString();
        return e.toString();
    }

    public ProductHistory toProductHistory() {
        ProductHistory h = new ProductHistory();
        h.setId(id);
        h.setShopId(shopId);
        h.setProductId(entityId);
        h.setUserId(userId);
        h.setOperation(operation);
        h.setOldValue(asText(oldValue));
        h.setNewValue(asText(newValue));
        h.setTimestamp(createdAt);
        h.setUpdatedAt(createdAt);
        return h;
    }
}
