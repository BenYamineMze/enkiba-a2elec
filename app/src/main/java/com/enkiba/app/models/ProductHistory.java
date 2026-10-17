package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.UUID;

public class ProductHistory implements Serializable {

    // UUID généré par le téléphone
    @Expose @SerializedName("id")
    private String id;

    @Expose @SerializedName("shop_id")
    private String shopId;

    // --- CLÉS ÉTRANGÈRES ---

    // [MODIFICATION UUID] L'ID produit est maintenant une chaîne (UUID)
    // Cela permet de lier l'historique au bon produit même après la migration vers UUID
    @Expose @SerializedName("product_id")
    private String productId;

    // ID Utilisateur en String (UUID Supabase)
    @Expose @SerializedName("user_id")
    private String userId;

    // --- CHAMPS D'AFFICHAGE ---
    // (non directement liés aux FKs de la table 'history' en base, mais utiles pour l'UI)
    @Expose
    private String productName;
    @Expose
    private String username;

    @Expose @SerializedName("operation")
    private String operation;

    @Expose @SerializedName("old_value")
    private String oldValue;

    @Expose @SerializedName("new_value")
    private String newValue;

    @Expose @SerializedName("created_at") // Souvent 'created_at' ou 'timestamp' en base
    private String timestamp;

    // --- CHAMPS DE SYNCHRONISATION ---
    @SerializedName("updated_at")
    private String updatedAt;

    @Expose(serialize = false)
    private transient String syncStatus;

    // -----------------------------------------------------------
    // CONSTRUCTEURS
    // -----------------------------------------------------------

    /** Nouvelle entrée : l'UUID est généré immédiatement (écrasé par Gson ou le DAO à la lecture). */
    public ProductHistory() {
        this.id = UUID.randomUUID().toString();
    }

    // -----------------------------------------------------------
    // GETTERS & SETTERS
    // -----------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    // [MODIFICATION UUID] Getter mis à jour pour String
    public String getProductId() { return productId; }

    // [MODIFICATION UUID] Setter mis à jour pour String
    public void setProductId(String productId) { this.productId = productId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    // --- Synchronisation ---
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    // Champs UI
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}