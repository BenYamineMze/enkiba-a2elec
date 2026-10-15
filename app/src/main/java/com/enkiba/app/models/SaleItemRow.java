package com.enkiba.app.models;

import java.io.Serializable;

public class SaleItemRow implements Serializable {

    private long id;

    // [MODIFICATION UUID] L'ID produit est maintenant une chaîne (UUID)
    private String productId;

    private String productName;
    private double unitPrice;
    private int quantity;
    private double total;
    private int initial_quantity;

    public SaleItemRow() {}

    // [MODIFICATION UUID] Constructeur mis à jour pour accepter String productId
    public SaleItemRow(String productId, String productName, double unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.total = unitPrice * quantity;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    // [MODIFICATION UUID] Getter mis à jour
    public String getProductId() { return productId; }

    // [MODIFICATION UUID] Setter mis à jour
    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() { return productName; }
    public double getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public double getTotal() { return total; }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public int getInitialQuantity() {
        return initial_quantity;
    }

    public void setInitialQuantity(int initial_quantity) {
        this.initial_quantity = initial_quantity;
    }
}