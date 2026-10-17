package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

/** Boutique telle que renvoyée par le serveur (table shops). */
public class Shop {
    @SerializedName("id") private String id;
    @SerializedName("name") private String name;
    @SerializedName("address") private String address;
    @SerializedName("phone") private String phone;
    @SerializedName("email") private String email;
    @SerializedName("updated_at") private String updatedAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getUpdatedAt() { return updatedAt; }
}
