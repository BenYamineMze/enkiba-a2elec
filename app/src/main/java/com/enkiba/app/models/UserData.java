package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Modèle représentant l'utilisateur tel qu'envoyé par Supabase Auth (GoTrue).
 * Utilisé uniquement pour l'authentification et l'importation initiale.
 */
public class UserData {

    @Expose
    @SerializedName("id")
    private String id; // UUID Supabase (String)

    @Expose
    @SerializedName("email")
    private String email;

    @Expose
    @SerializedName("phone")
    private String phone; // Ajouté car présent dans les logs Supabase

    @Expose
    @SerializedName("user_metadata")
    private Map<String, Object> userMetadata;

    // Getters et Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Map<String, Object> getUserMetadata() {
        return userMetadata;
    }

    public void setUserMetadata(Map<String, Object> userMetadata) {
        this.userMetadata = userMetadata;
    }
}