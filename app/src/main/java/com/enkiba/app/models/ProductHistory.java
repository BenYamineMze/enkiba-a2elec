package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ProductHistory implements Serializable {

    @Expose @SerializedName("id")
    private long id;

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

    public ProductHistory() {
        // Constructeur vide nécessaire pour la désérialisation Retrofit/Gson et les DAOs
    }

    public ProductHistory(long id, String productName, String operation, String oldValue, String newValue, String timestamp, String username) {
        this.id = id;
        this.productName = productName;
        this.operation = operation;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = timestamp;
        this.username = username;
    }

    // -----------------------------------------------------------
    // GETTERS & SETTERS
    // -----------------------------------------------------------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

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