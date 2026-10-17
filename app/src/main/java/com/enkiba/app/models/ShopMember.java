package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

public class ShopMember {
    @SerializedName("user_id") private String userId;
    @SerializedName("role") private String role;
    @SerializedName("profiles") private Profile profile;

    public String getUserId() { return userId; }
    public String getRole() { return role; }
    public Profile getProfile() { return profile; }
}