package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SaleHeader implements Serializable {
    @Expose @SerializedName("id")
    private long id;

    // 🚨 CORRECTION : userId doit être un String pour les UUID Supabase
    @Expose @SerializedName("user_id")
    private String userId;

    @Expose @SerializedName("client_id")
    private Long clientId; // nullable

    @Expose @SerializedName("total_price")
    private double totalPrice;

    @Expose @SerializedName("payment_method")
    private String paymentMethod;

    @Expose @SerializedName("sale_date")
    private String saleDate;

    // --- NOUVEAU CHAMP ---
    // Indique si au moins un article de cette vente a été négocié
    @Expose @SerializedName("has_negotiation")
    private boolean hasNegotiatedPrice;

    @Expose
    private String username;

    @Expose
    private String authId;

    private List<SaleItem> items = new ArrayList<>();

    // --- Champs de Synchronisation ---
    // Correspondance avec le champ 'updated_at' de Supabase
    @Expose @SerializedName("updated_at")
    private String updatedAt;

    // Ce champ est purement LOCAL, le serveur n'a pas besoin de le voir
    private String syncStatus;

    public SaleHeader() {}

    // --- Getters & Setters ---

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getSaleDate() { return saleDate; }
    public void setSaleDate(String saleDate) { this.saleDate = saleDate; }

    // --- NOUVEAUX GETTERS/SETTERS POUR LA NÉGOCIATION ---
    public boolean isHasNegotiatedPrice() { return hasNegotiatedPrice; }
    public void setHasNegotiatedPrice(boolean hasNegotiatedPrice) { this.hasNegotiatedPrice = hasNegotiatedPrice; }
    // ----------------------------------------------------

    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
    public void addItem(SaleItem item) { this.items.add(item); }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAuthId() { return authId; }
    public void setAuthId(String authId) { this.authId = authId; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}