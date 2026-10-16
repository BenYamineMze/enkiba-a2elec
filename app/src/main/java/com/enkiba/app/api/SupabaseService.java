package com.enkiba.app.api;

import com.enkiba.app.models.AuthResponse;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Membership;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.models.ShopMember;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseService {

    // ======================================================================
    // 1. AUTHENTIFICATION ET PROFIL
    // ======================================================================

    @POST("auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Body Map<String, String> credentials);

    @GET("rest/v1/profiles")
    Call<List<Profile>> getProfile(@Query("id") String idFilter, @Query("select") String select);

    @GET("rest/v1/shop_members")
    Call<List<Membership>> getMemberships(@Query("user_id") String userIdFilter, @Query("select") String select);

    // ======================================================================
    // 2. GESTION DES UTILISATEURS (Edge Functions)
    // ======================================================================

    @GET("rest/v1/shop_members")
    Call<List<ShopMember>> getShopMembers(
            @Query("shop_id") String shopIdFilter,
            @Query("select") String select);

    @POST("functions/v1/create-seller")
    Call<JsonObject> createSeller(@Body JsonObject body);

    @POST("functions/v1/set-seller-status")
    Call<JsonObject> setSellerStatus(@Body JsonObject body);

    @POST("functions/v1/update-seller")
    Call<JsonObject> updateSeller(@Body JsonObject body);

    // ======================================================================
    // 3. SYNCHRONISATION DESCENDANTE (PULL) : à réécrire à l'étape 4
    // ======================================================================

    @GET("rest/v1/products")
    Call<List<Product>> getProductsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    @GET("rest/v1/clients")
    Call<List<Client>> getClientsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    @GET("rest/v1/sales")
    Call<List<SaleHeader>> getSalesSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    @GET("rest/v1/sale_items")
    Call<List<SaleItem>> getSaleItemsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    @GET("rest/v1/sale_returns")
    Call<List<SaleReturn>> getSaleReturnsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    @GET("rest/v1/history")
    Call<List<ProductHistory>> getHistorySince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields);

    // ======================================================================
    // 4. SYNCHRONISATION ASCENDANTE (PUSH) : à réécrire à l'étape 4
    // ======================================================================

    @Headers("Content-Type: application/json")
    @POST("rest/v1/products")
    Call<List<Product>> upsertProduct(@Header("Prefer") String prefer, @Body Product product);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/products")
    Call<List<Product>> upsertProductAsMap(@Header("Prefer") String prefer, @Body Map<String, Object> productMap);

    @Headers({"Content-Type: application/json", "Prefer: resolution=merge-duplicates"})
    @POST("rest/v1/clients")
    Call<Void> upsertClient(@Body Client client);

    @Headers({"Content-Type: application/json", "Prefer: resolution=merge-duplicates"})
    @POST("rest/v1/sales")
    Call<Void> upsertSale(@Body SaleHeader sale);

    @Headers({"Content-Type: application/json", "Prefer: resolution=merge-duplicates"})
    @POST("rest/v1/sale_items")
    Call<Void> upsertSaleItem(@Body SaleItem saleItem);

    @Headers({"Content-Type: application/json", "Prefer: resolution=merge-duplicates"})
    @POST("rest/v1/history")
    Call<Void> upsertHistory(@Body ProductHistory history);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/clients")
    Call<Void> upsertClientMap(@Header("Prefer") String prefer, @Body Map<String, Object> clientMap);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sales")
    Call<Void> upsertSaleMap(@Header("Prefer") String prefer, @Body Map<String, Object> saleMap);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sales")
    Call<List<SaleHeader>> upsertSaleAsMap(@Header("Prefer") String prefer, @Body Map<String, Object> saleMap);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_items")
    Call<Void> upsertSaleItemMap(@Header("Prefer") String prefer, @Body Map<String, Object> itemMap);

    // Produit : modification (PATCH) puis création si absent. Un upsert réécrirait id et
    // shop_id, que le rôle authenticated n'a pas le droit de modifier.
    @Headers("Content-Type: application/json")
    @PATCH("rest/v1/products")
    Call<List<Product>> patchProduct(@Header("Prefer") String prefer, @Query("id") String idFilter,
                                     @Body Map<String, Object> product);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/products")
    Call<Void> insertProduct(@Header("Prefer") String prefer, @Body Map<String, Object> product);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/audit_log")
    Call<Void> insertAuditLog(@Header("Prefer") String prefer, @Body Map<String, Object> entry);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_returns")
    Call<Void> insertSaleReturn(@Header("Prefer") String prefer, @Body Map<String, Object> saleReturn);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/stock_movements")
    Call<Void> insertStockMovement(@Header("Prefer") String prefer, @Body Map<String, Object> movement);

    @GET("rest/v1/shop_members")
    Call<List<ShopMember>> getShopMember(
            @Query("shop_id") String shopIdFilter,
            @Query("user_id") String userIdFilter,
            @Query("select") String select);
}