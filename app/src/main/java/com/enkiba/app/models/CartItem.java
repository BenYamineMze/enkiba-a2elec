package com.enkiba.app.models;

import java.io.Serializable;

public class CartItem implements Serializable {
    // [MODIFICATION UUID] L'ID produit est maintenant une chaîne (UUID)
    private String productId;

    private String name;
    private double unitPrice;
    private int quantity;

    // [MODIFICATION UUID] Constructeur mis à jour pour accepter String productId
    public CartItem(String productId, String name, double unitPrice, int quantity) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    // [MODIFICATION UUID] Getter mis à jour
    public String getProductId() { return productId; }

    public String getName() { return name; }
    public double getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int q) { this.quantity = q; }

    public double getTotal() {
        return unitPrice * quantity;
    }
}