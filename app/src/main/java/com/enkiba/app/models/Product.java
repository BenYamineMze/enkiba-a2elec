package com.enkiba.app.models;

import java.io.Serializable;
import java.util.UUID; // [MODIFICATION] Import nécessaire pour générer les IDs uniques

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Expose;

public class Product implements Serializable {
    @Expose @SerializedName("id")
    private String id;
    @Expose @SerializedName("name")
    private String name;
    @Expose @SerializedName("category")
    private String category;
    @Expose @SerializedName("quantity")
    private int quantity; // Stock actuel
    @Expose
    private int initial_stock; // Stock initial (base pour les pourcentages)
    @Expose @SerializedName("price")
    private double price;
    @Expose @SerializedName("description")
    private String description;
    @Expose @SerializedName("qr_code")
    private String qr_code;
    @Expose @SerializedName("sold_quantity")
    private int soldQuantity;
    @Expose @SerializedName("low_stock_threshold")
    private int lowStockThreshold;

    // --- Champs de Synchronisation ---
    // Correspondance avec le champ 'updated_at' de Supabase
    @Expose @SerializedName("updated_at")
    private String updatedAt;

    // Ce champ est purement LOCAL, le serveur n'a pas besoin de le voir
    @Expose(serialize = false)
    private transient String syncStatus;

    // Constructeurs et Getters/Setters mis à jour

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    // Pour la gestion SQLite locale
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    // [MODIFICATION] Constructeur vide : On génère un ID par sécurité
    public Product() {
        this.id = UUID.randomUUID().toString();
    }

    public Product(String id, String name, String category, int quantity, double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        // On initialise initial_stock avec la quantité si non spécifié, à revoir lors de l'insertion DB
        this.initial_stock = quantity;
        this.price = price;
    }

    // ✅ Constructeur de copie (clone)
    public Product(Product other) {
        this.id = other.id;
        this.name = other.name;
        this.category = other.category;
        this.quantity = other.quantity;
        this.initial_stock = other.initial_stock; // Inclure le stock initial
        this.price = other.price;
        this.description = other.description;
        this.qr_code = other.qr_code;
        this.soldQuantity = other.soldQuantity;
        this.lowStockThreshold = other.lowStockThreshold;
    }

    // [IMPORTANT] C'est le constructeur utilisé pour CRÉER un nouveau produit
    // AVANT : l'id n'était pas défini (null)
    // APRÈS : On génère l'UUID immédiatement. C'est ça qui empêche les doublons.
    public Product(String name, String category, int quantity, double price, String description, int soldQuantity, int lowStockThreshold, int initialStock) {
        this.id = UUID.randomUUID().toString(); // <--- LA CLÉ DU SUCCÈS EST ICI
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.initial_stock = initialStock; // Ajout du stock initial
        this.price = price;
        this.description = description;
        this.soldQuantity = soldQuantity;
        this.lowStockThreshold = lowStockThreshold;
    }

    public Product(String id, String name, String category, int quantity) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.initial_stock = quantity;
    }

    public Product(String id, String name, String category, int quantity, double price, String description, int initialStock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.initial_stock = initialStock; // Ajout du stock initial
        this.price = price;
        this.description = description;
    }


    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; } // Stock actuel
    public double getPrice() { return price; }

    public int getSoldQuantity() { return soldQuantity; }
    public void setSoldQuantity(int soldQuantity) { this.soldQuantity = soldQuantity; }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getQr_code() { return qr_code; }
    public void setQr_code(String qr_code) { this.qr_code = qr_code; }

    // Remplacement de String par int pour le stock initial
    public int getInitial_stock() {
        return initial_stock;
    }

    // Remplacement de String par int pour le stock initial
    public void setInitial_stock(int initial_stock) {
        this.initial_stock = initial_stock;
    }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }

    /**
     * Calcule le pourcentage de stock restant par rapport au stock initial.
     * @return Le pourcentage (0 à 100), ou 0 si le stock initial est 0.
     */
    public int getRemainingStockPercentage() {
        if (initial_stock <= 0) {
            return 0; // Évite la division par zéro
        }
        // Conversion en double pour la division, puis retour en int après * 100
        return (int) (((double) quantity / initial_stock) * 100);
    }
}