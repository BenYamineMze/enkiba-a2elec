package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Devis (table quotes). Les lignes d'un devis ne changent plus après sa création :
 * seuls le statut (et la vente issue de la conversion) évoluent.
 */
public class Quote implements Serializable {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CONVERTED = "converted";

    // Validité par défaut d'un nouveau devis
    public static final int DEFAULT_VALIDITY_DAYS = 30;

    @SerializedName("id")
    private String id;
    @SerializedName("shop_id")
    private String shopId;
    @SerializedName("seller_id")
    private String sellerId;
    @SerializedName("client_id")
    private String clientId;
    @SerializedName("status")
    private String status = STATUS_DRAFT;
    @SerializedName("total_price")
    private double totalPrice;
    // Date seule, au format yyyy-MM-dd
    @SerializedName("valid_until")
    private String validUntil;
    @SerializedName("notes")
    private String notes;
    @SerializedName("converted_sale_id")
    private String convertedSaleId;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    @SerializedName("deleted_at")
    private String deletedAt;

    // --- Champs locaux (affichage / synchro) ---
    private transient String clientName;
    private transient String syncStatus;

    public Quote() {
        this.id = UUID.randomUUID().toString();
    }

    /** Référence courte pour l'affichage (« DEV-3F2A9C1B »). */
    public String getShortRef() {
        return "DEV-" + SaleHeader.shortRef(id);
    }

    /** Vrai si la date de validité est dépassée (un devis converti ou refusé n'expire plus). */
    public boolean isExpired() {
        if (validUntil == null || STATUS_CONVERTED.equals(status) || STATUS_REJECTED.equals(status)) return false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return validUntil.compareTo(today) < 0;
    }

    /** Libellé du statut en français, « expiré » compris. */
    public String getStatusLabel() {
        if (isExpired()) return "Expiré";
        if (status == null) return "Brouillon";
        switch (status) {
            case STATUS_ACCEPTED: return "Accepté";
            case STATUS_REJECTED: return "Refusé";
            case STATUS_CONVERTED: return "Converti en vente";
            default: return "Brouillon";
        }
    }

    /** Un devis converti, refusé ou supprimé ne peut plus être converti. */
    public boolean isConvertible() {
        return !STATUS_CONVERTED.equals(status) && !STATUS_REJECTED.equals(status) && deletedAt == null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getValidUntil() { return validUntil; }
    public void setValidUntil(String validUntil) { this.validUntil = validUntil; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getConvertedSaleId() { return convertedSaleId; }
    public void setConvertedSaleId(String convertedSaleId) { this.convertedSaleId = convertedSaleId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
