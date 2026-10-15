package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SaleItem implements Serializable {

    // ID principal de l'article de vente (reste Long car la table sale_items n'a pas changé de PK)
    @Expose @SerializedName("id")
    private Long id;

    // --- CLÉS ÉTRANGÈRES ---

    // Clé vers la table parente 'sales' (reste Long)
    @Expose @SerializedName("sale_id")
    private Long saleId;

    // [MODIFICATION UUID] Clé vers la table 'products' qui est maintenant un UUID (String)
    @Expose @SerializedName("product_id")
    private String productId;

    @Expose @SerializedName("auth_id")
    private String authId;

    // --- CHAMPS DE DONNÉES ---

    @Expose @SerializedName("quantity")
    private int quantity;

    @Expose @SerializedName("initial_quantity")
    private int initial_quantity;

    @Expose @SerializedName("unit_price")
    private double unitPrice;

    @Expose @SerializedName("total_price")
    private double totalPrice;

    // --- CHAMPS LOCAUX/SYNCHRO ---

    @Expose(serialize = false)
    private String productName;

    @Expose @SerializedName("updated_at")
    private String updatedAt;

    @Expose(serialize = false)
    private String syncStatus;

    // -----------------------------------------------------------
    // CONSTRUCTEURS
    // -----------------------------------------------------------

    public SaleItem() {}

    public SaleItem(Long saleId, String productId, int quantity, double unitPrice) {
        this.saleId = saleId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice * quantity;
    }

    // -----------------------------------------------------------
    // LOGIQUE MÉTIER
    // -----------------------------------------------------------

    // Mise à jour de la quantité doit impacter le total
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.totalPrice = this.unitPrice * quantity;
    }

    // Mise à jour du prix unitaire doit impacter le total
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        this.totalPrice = this.unitPrice * this.quantity;
    }

    // -----------------------------------------------------------
    // GETTERS / SETTERS
    // -----------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    // [MODIFICATION UUID] Getter/Setter adaptés pour String
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }

    public double getUnitPrice() { return unitPrice; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getAuthId() { return authId; }
    public void setAuthId(String authId) { this.authId = authId; }

    public int getInitialQuantity() { return initial_quantity; }
    public void setInitialQuantity(int initial_quantity) { this.initial_quantity = initial_quantity; }
}