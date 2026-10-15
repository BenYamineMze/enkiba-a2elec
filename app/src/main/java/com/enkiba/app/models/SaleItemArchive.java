package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SaleItemArchive implements Serializable {

    @Expose @SerializedName("id")
    private Long id;

    // 🚨 CLÉ ÉTRANGÈRE vers sale_archive 🚨
    @Expose @SerializedName("sale_id")
    private Long saleId;

    // Le nom du produit est résolu en chaîne de caractères pendant l'archivage local
    // (Pas de lien UUID ici, c'est une archive textuelle)
    @Expose @SerializedName("product_name")
    private String productName;

    @Expose @SerializedName("quantity")
    private Integer quantity;

    @Expose @SerializedName("initial_quantity")
    private Integer initial_quantity;

    @Expose @SerializedName("unit_price")
    private Double unitPrice;

    @Expose @SerializedName("total_price")
    private Double totalPrice;

    // --- Champs de Synchronisation ---
    @Expose @SerializedName("updated_at")
    private String updatedAt;

    // Ce champ est purement LOCAL (utilisé par le DAO), il ne doit pas être envoyé.
    @Expose(serialize = false)
    private String syncStatus;

    // --- Constructeurs ---

    public SaleItemArchive() {}

    public SaleItemArchive(Long saleId, String productName, Integer quantity, Double unitPrice, Integer initial_quantity) {
        this.saleId = saleId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.initial_quantity = initial_quantity;
        if (unitPrice != null && quantity != null) {
            this.totalPrice = unitPrice * quantity;
        }
    }

    public SaleItemArchive(Long id, Long saleId, String productName, Integer quantity, Double unitPrice, Double totalPrice, String updatedAt) {
        this.id = id;
        this.saleId = saleId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.updatedAt = updatedAt;
    }

    // --- Getters et Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getInitialQuantity() {
        return initial_quantity;
    }

    public void setInitialQuantity(Integer initial_quantity) {
        this.initial_quantity = initial_quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}