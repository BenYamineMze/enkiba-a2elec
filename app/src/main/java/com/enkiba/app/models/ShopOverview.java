package com.enkiba.app.models;

import com.google.gson.annotations.SerializedName;

/** Une boutique et ses indicateurs (fonction shop_overview, réservée au super-admin). */
public class ShopOverview {
    @SerializedName("shop_id") private String shopId;
    @SerializedName("name") private String name;
    @SerializedName("address") private String address;
    @SerializedName("phone") private String phone;
    @SerializedName("email") private String email;
    @SerializedName("suspended_at") private String suspendedAt;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("members") private int members;
    @SerializedName("admins") private int admins;
    @SerializedName("products") private int products;
    @SerializedName("sales_month") private int salesMonth;
    @SerializedName("revenue_month") private double revenueMonth;
    @SerializedName("last_sale_at") private String lastSaleAt;

    public String getShopId() { return shopId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getSuspendedAt() { return suspendedAt; }
    public boolean isSuspended() { return suspendedAt != null; }
    public String getCreatedAt() { return createdAt; }
    public int getMembers() { return members; }
    public int getAdmins() { return admins; }
    public int getProducts() { return products; }
    public int getSalesMonth() { return salesMonth; }
    public double getRevenueMonth() { return revenueMonth; }
    public String getLastSaleAt() { return lastSaleAt; }
}
