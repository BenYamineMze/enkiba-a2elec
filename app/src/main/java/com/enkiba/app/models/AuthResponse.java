package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Modèle pour la réponse d'authentification de Supabase.
 * Utilise la classe UserData globale pour éviter les conflits de types et
 * permettre l'accès aux métadonnées (username, role, etc.).
 */
public class AuthResponse {

    @Expose
    @SerializedName("access_token")
    private String accessToken;

    @Expose
    @SerializedName("token_type")
    private String tokenType;

    @Expose
    @SerializedName("user")
    private UserData user; // Utilise maintenant com.enkiba.app.models.UserData (global)

    // Getters
    public String getAccessToken() {
        return accessToken;
    }

    public UserData getUser() {
        return user;
    }

    public String getTokenType() {
        return tokenType;
    }

    // Setters
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public void setUser(UserData user) {
        this.user = user;
    }
}