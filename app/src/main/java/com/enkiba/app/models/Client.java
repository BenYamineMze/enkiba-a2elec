package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.UUID;

/**
 * Client d'une boutique (table clients).
 * L'id est un UUID généré par le téléphone : le même id est envoyé au serveur (push idempotent).
 */
public class Client implements Serializable {
    @Expose @SerializedName("id")
    private String id;
    @Expose @SerializedName("shop_id")
    private String shopId;
    @Expose
    private String firstname;
    @Expose
    private String lastname;
    @Expose
    private String address;
    @Expose
    private String phone;
    @Expose
    private String email;

    // --- Champs de synchronisation ---
    @Expose @SerializedName("updated_at")
    private String updatedAt;
    @Expose @SerializedName("deleted_at")
    private String deletedAt;

    // Purement local : le serveur n'a pas besoin de le voir
    private transient String syncStatus;

    /** Nouveau client : l'UUID est généré immédiatement. */
    public Client() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
