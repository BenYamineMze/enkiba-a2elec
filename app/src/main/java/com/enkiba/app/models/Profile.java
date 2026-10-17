package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

public class Profile {
    @SerializedName("id") private String id;
    @SerializedName("username") private String username;
    @SerializedName("lastname") private String lastname;
    @SerializedName("telephone") private String telephone;
    @SerializedName("email") private String email;
    @SerializedName("active") private boolean active;
    @SerializedName("updated_at") private String updatedAt;
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getLastname() { return lastname; }
    public String getTelephone() { return telephone; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public String getUpdatedAt() { return updatedAt; }
}