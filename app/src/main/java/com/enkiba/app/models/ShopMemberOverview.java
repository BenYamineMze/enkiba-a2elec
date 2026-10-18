package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

/** Membre d'une boutique vu par le super-admin (fonction shop_members_overview). */
public class ShopMemberOverview {
    @SerializedName("user_id") private String userId;
    @SerializedName("username") private String username;
    @SerializedName("lastname") private String lastname;
    @SerializedName("email") private String email;
    @SerializedName("telephone") private String telephone;
    @SerializedName("role") private String role;
    @SerializedName("active") private boolean active;

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getLastname() { return lastname; }
    public String getEmail() { return email; }
    public String getTelephone() { return telephone; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
}
