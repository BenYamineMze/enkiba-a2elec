package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class User implements Serializable {

    @SerializedName("id")
    private String id; // L'ID local ou serveur de la table public.users

    @SerializedName("auth_id") // Le lien vers la table d'authentification Supabase (UUID)
    private String authId;

    @SerializedName("username")
    private String username;

    @SerializedName("lastname")
    private String lastname;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("role")
    private String role;

    @SerializedName("telephone")
    private String telephone;

    @SerializedName("active")
    private int active;

    @SerializedName("updated_at")
    private String updatedAt;

    // Champ local uniquement (non envoyé au serveur via Gson sauf si configuré autrement,
    // mais ici on utilise des Maps pour l'upload donc ça ne gêne pas)
    private transient String syncStatus;

    public User() {}

    // --- GETTERS ---

    public String getId() { return id; }

    // ✅ La méthode manquante qui corrige l'erreur
    public String getAuthId() { return authId; }

    public String getUsername() { return username; }
    public String getLastname() { return lastname; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public String getTelephone() { return telephone; }
    public int getActive() { return active; }
    public String getUpdatedAt() { return updatedAt; }
    public String getSyncStatus() { return syncStatus; }

    // --- SETTERS ---

    public void setId(String id) { this.id = id; }
    public void setAuthId(String authId) { this.authId = authId; }
    public void setUsername(String username) { this.username = username; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(String role) { this.role = role; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public void setActive(int active) { this.active = active; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}