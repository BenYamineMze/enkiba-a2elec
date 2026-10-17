package com.enkiba.app.models;

import java.io.Serializable;

public class SaleItemRow implements Serializable {

    private String id;

    // [MODIFICATION UUID] L'ID produit est maintenant une chaîne (UUID)
    private String productId;

    private String productName;
    private double unitPrice;
    // Prix catalogue au moment de la vente ou du devis (0 = inconnu : on affiche le prix appliqué)
    private double listPrice;
    private int quantity;
    private double total;
    // Quantité déjà retournée (quantity est la quantité nette, après retours)
    private int returnedQuantity;

    public SaleItemRow() {}

    // [MODIFICATION UUID] Constructeur mis à jour pour accepter String productId
    public SaleItemRow(String productId, String productName, double unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.total = unitPrice * quantity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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
    public double getListPrice() { return listPrice > 0 ? listPrice : unitPrice; }
    public void setListPrice(double listPrice) { this.listPrice = listPrice; }
    /** Vrai si l'article a été vendu (ou proposé) à un autre prix que le prix catalogue. */
    public boolean isNegotiated() { return listPrice > 0 && listPrice != unitPrice; }
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

    public int getReturnedQuantity() {
        return returnedQuantity;
    }

    public void setReturnedQuantity(int returnedQuantity) {
        this.returnedQuantity = returnedQuantity;
    }
}