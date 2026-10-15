package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SaleArchive implements Serializable {

    @Expose @SerializedName("id")
    private Long id;

    @Expose @SerializedName("sale_date")
    private String saleDate; // Format TEXT ISO 8601

    @Expose @SerializedName("user_id")
    private Long userId; // Clé étrangère vers la table users

    // Le nom du client est résolu en chaînes de caractères pendant l'archivage local,
    // le serveur Supabase doit accepter une colonne TEXT (client_name) si vous ne voulez pas la FK.
    @Expose @SerializedName("client_id")
    private long clientId;

    @Expose @SerializedName("total_price")
    private Double totalPrice; // Double pour les valeurs monétaires

    @Expose @SerializedName("payment_method")
    private String paymentMethod;

    // --- Champs de Synchronisation ---
    @Expose @SerializedName("updated_at")
    private String updatedAt;

    // Ce champ est purement LOCAL (utilisé par le DAO), il ne doit pas être envoyé.
    @Expose(serialize = false)
    private String syncStatus;

    // --- Constructeurs, Getters et Setters ---

    public SaleArchive() {}

    // Vous devez ajouter tous les constructeurs, getters et setters pour tous les champs ci-dessus (Long id, String saleDate, etc.)
    // ...

    // Exemple d'un getter/setter critique :
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(String saleDate) {
        this.saleDate = saleDate;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    // etc.
}