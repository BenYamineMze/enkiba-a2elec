package com.enkiba.app.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class AuthUser {
    @SerializedName("id") // C'est bien "id" dans la réponse Auth
    @Expose
    private String id;

    @SerializedName("email")
    @Expose
    private String email;

    @SerializedName("user_metadata")
    @Expose
    private Map<String, Object> userMetadata;

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, Object> getUserMetadata() {
        return userMetadata;
    }
}