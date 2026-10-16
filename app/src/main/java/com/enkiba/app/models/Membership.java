package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

public class Membership {
    @SerializedName("shop_id") private String shopId;
    @SerializedName("role") private String role;
    public String getShopId() { return shopId; }
    public String getRole() { return role; }
}