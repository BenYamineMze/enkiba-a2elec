package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Locale;
import java.util.UUID;

/**
 * En-tête de vente (table sales).
 * L'id est un UUID généré par le téléphone : le même id est envoyé au serveur (push idempotent).
 */
public class SaleHeader implements Serializable {
    @Expose @SerializedName("id")
    private String id;

    @Expose @SerializedName("shop_id")
    private String shopId;

    // Vendeur : profiles.id (= auth.users.id)
    @Expose @SerializedName("seller_id")
    private String sellerId;

    @Expose @SerializedName("client_id")
    private String clientId; // facultatif

    @Expose @SerializedName("total_price")
    private double totalPrice;

    @Expose @SerializedName("payment_method")
    private String paymentMethod;

    @Expose @SerializedName("sale_date")
    private String saleDate;

    // Indique si au moins un article de cette vente a été vendu à un prix négocié
    @Expose @SerializedName("has_negotiation")
    private boolean hasNegotiatedPrice;

    // --- Champs de synchronisation ---
    @Expose @SerializedName("updated_at")
    private String updatedAt;
    @Expose @SerializedName("deleted_at")
    private String deletedAt;

    // --- Champs locaux (affichage / synchro) ---
    private transient String username;
    private transient String syncStatus;

    /** Nouvelle vente : l'UUID est généré immédiatement. */
    public SaleHeader() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Référence courte pour l'affichage (« Vente #3F2A9C1B ») :
     * les 8 premiers caractères de l'UUID, en majuscules.
     */
    public String getShortRef() {
        return shortRef(id);
    }

    public static String shortRef(String uuid) {
        if (uuid == null) return "";
        String s = uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        return s.toUpperCase(Locale.ROOT);
    }

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getSaleDate() { return saleDate; }
    public void setSaleDate(String saleDate) { this.saleDate = saleDate; }

    public boolean isHasNegotiatedPrice() { return hasNegotiatedPrice; }
    public void setHasNegotiatedPrice(boolean hasNegotiatedPrice) { this.hasNegotiatedPrice = hasNegotiatedPrice; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
