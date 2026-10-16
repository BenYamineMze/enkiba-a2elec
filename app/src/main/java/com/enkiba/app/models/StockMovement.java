package com.enkiba.app.models;

import java.io.Serializable;
import java.util.UUID;

/**
 * Mouvement de stock saisi par un admin (table stock_movements).
 * Seuls les mouvements 'initial', 'restock' et 'adjustment' sont créés par le téléphone :
 * les mouvements 'sale' et 'sale_cancel' sont générés par les triggers du serveur.
 */
public class StockMovement implements Serializable {

    public static final String REASON_INITIAL = "initial";
    public static final String REASON_RESTOCK = "restock";
    public static final String REASON_ADJUSTMENT = "adjustment";

    private String id;
    private String shopId;
    private String productId;
    private int delta;
    private String reason;
    private String createdBy;
    private String createdAt;
    private transient String syncStatus;

    public StockMovement() {
        this.id = UUID.randomUUID().toString();
    }

    public StockMovement(String productId, int delta, String reason, String createdBy) {
        this();
        this.productId = productId;
        this.delta = delta;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
