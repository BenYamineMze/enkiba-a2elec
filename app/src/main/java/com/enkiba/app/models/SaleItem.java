package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.UUID;

/**
 * Ligne de vente (table sale_items), telle qu'échangée avec le serveur.
 * Garde une copie du nom et du prix catalogue au moment de la vente.
 */
public class SaleItem implements Serializable {

    @Expose @SerializedName("id")
    private String id;

    @Expose @SerializedName("shop_id")
    private String shopId;

    @Expose @SerializedName("sale_id")
    private String saleId;

    @Expose @SerializedName("product_id")
    private String productId;

    @Expose @SerializedName("product_name")
    private String productName;

    @Expose @SerializedName("quantity")
    private int quantity;

    // Prix catalogue au moment de la vente
    @Expose @SerializedName("list_price")
    private double listPrice;

    // Prix réellement appliqué (négocié ou non)
    @Expose @SerializedName("unit_price")
    private double unitPrice;

    @Expose @SerializedName("total_price")
    private double totalPrice;

    @Expose @SerializedName("updated_at")
    private String updatedAt;

    // --- Champs locaux ---
    // Quantité vendue à l'origine : les retours sont gérés localement pour l'instant
    private transient int initialQuantity;
    private transient String syncStatus;

    public SaleItem() {
        this.id = UUID.randomUUID().toString();
    }

    // -----------------------------------------------------------
    // LOGIQUE MÉTIER
    // -----------------------------------------------------------

    // La mise à jour de la quantité recalcule le total
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.totalPrice = this.unitPrice * quantity;
    }

    // La mise à jour du prix unitaire recalcule le total
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        this.totalPrice = this.unitPrice * this.quantity;
    }

    // -----------------------------------------------------------
    // GETTERS / SETTERS
    // -----------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getSaleId() { return saleId; }
    public void setSaleId(String saleId) { this.saleId = saleId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }

    public double getListPrice() { return listPrice; }
    public void setListPrice(double listPrice) { this.listPrice = listPrice; }

    public double getUnitPrice() { return unitPrice; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public int getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(int initialQuantity) { this.initialQuantity = initialQuantity; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
