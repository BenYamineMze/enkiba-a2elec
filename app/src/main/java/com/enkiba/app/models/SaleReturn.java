package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.UUID;

/**
 * Retour d'articles sur une ligne de vente (table sale_returns).
 * La vente d'origine n'est jamais modifiée : le stock est remis par le serveur
 * (mouvement 'sale_cancel') et le montant remboursé est déduit à l'affichage.
 */
public class SaleReturn implements Serializable {

    @SerializedName("id")
    private String id;
    @SerializedName("shop_id")
    private String shopId;
    @SerializedName("sale_id")
    private String saleId;
    @SerializedName("sale_item_id")
    private String saleItemId;
    @SerializedName("product_id")
    private String productId;
    @SerializedName("quantity")
    private int quantity;
    @SerializedName("refund_amount")
    private double refundAmount;
    @SerializedName("created_by")
    private String createdBy;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;

    private transient String syncStatus;

    public SaleReturn() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getSaleId() { return saleId; }
    public void setSaleId(String saleId) { this.saleId = saleId; }

    public String getSaleItemId() { return saleItemId; }
    public void setSaleItemId(String saleItemId) { this.saleItemId = saleItemId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
