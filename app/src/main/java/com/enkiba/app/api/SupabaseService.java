package com.enkiba.app.api;

import com.enkiba.app.models.AuthResponse;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleArchive;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleItemArchive;
import com.enkiba.app.models.User;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseService {

    // ----------------------------------------------------------------------
    // --- SECTION 1 : AUTHENTIFICATION ---
    // ----------------------------------------------------------------------

    @POST("auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Body Map<String, String> credentials);


    // ----------------------------------------------------------------------
    // --- SECTION 2 : SYNCHRONISATION DESCENDANTE (PULL / GET) ---
    // ----------------------------------------------------------------------

    @GET("rest/v1/users")
    Call<List<User>> getUsers(@Query("select") String select);

    @GET("rest/v1/users")
    Call<List<User>> getUserProfile(
            @Query("email") String emailFilter,
            @Query("select") String columns
    );

    @GET("rest/v1/users")
    Call<List<User>> getUsersSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );

    @GET("rest/v1/products")
    Call<List<Product>> getProductsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );

    @GET("rest/v1/clients")
    Call<List<Client>> getClientsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );

    @GET("rest/v1/sales")
    Call<List<SaleHeader>> getSalesSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );

    @GET("rest/v1/sale_items")
    Call<List<SaleItem>> getSaleItemsSince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );

    @GET("rest/v1/history")
    Call<List<ProductHistory>> getHistorySince(
            @Query("updated_at") String filterTime,
            @Query("select") String selectFields
    );


    // ----------------------------------------------------------------------
    // --- SECTION 3 : SYNCHRONISATION ASCENDANTE (PUSH / POST / UPSERT) ---
    // ----------------------------------------------------------------------

    // Méthode standard (utilisée pour les IDs existants)
    @Headers("Content-Type: application/json")
    @POST("rest/v1/products")
    Call<List<Product>> upsertProduct(@Header("Prefer") String prefer, @Body Product product);

    // 🚨 NOUVELLE MÉTHODE CRITIQUE : Permet d'envoyer une Map (sans ID) et de recevoir l'ID généré
    @Headers("Content-Type: application/json")
    @POST("rest/v1/products")
    Call<List<Product>> upsertProductAsMap(@Header("Prefer") String prefer, @Body Map<String, Object> productMap);

    @Headers({"Content-Type: application/json", "Prefer: resolution=merge-duplicates"})
    @POST("rest/v1/users")
    Call<Void> upsertUser(@Body User user);

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


    // --- UPSERT AVEC MAPS (Pour gérer les IDs partiels ou spécifiques) ---

    // 🚨 Pour gérer les conflits Users
    @Headers({
            "Content-Type: application/json",
            "Prefer: return=minimal"
    })
    @POST("rest/v1/users")
    Call<Void> upsertUserMap(
            @Header("Prefer") String prefer,
            @Query("on_conflict") String onConflict,
            @Body Map<String, Object> body
    );

    @GET("rest/v1/users")
    Call<List<User>> getUserByAuthId(
            @Query("auth_id") String authId
    );



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

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_archive")
    Call<Void> upsertSaleArchiveMap(@Header("Prefer") String prefer, @Body Map<String, Object> archiveMap);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_items_archive")
    Call<Void> upsertSaleItemArchiveMap(@Header("Prefer") String prefer, @Body Map<String, Object> itemArchiveMap);


    // ----------------------------------------------------------------------
    // --- SECTION 4 : ARCHIVAGE ET SUPPRESSION ---
    // ----------------------------------------------------------------------

    // 🚨 AJOUT : Supprimer d'abord les items pour éviter l'erreur de clé étrangère
    @DELETE("rest/v1/sale_items?id=neq.0")
    Call<Void> deleteAllSaleItemsRemote();

    @POST("rest/v1/sale_archive")
    Call<Void> upsertSaleArchive(@Body SaleArchive archive);

    @POST("rest/v1/sale_items_archive")
    Call<Void> upsertSaleItemArchive(@Body SaleItemArchive itemArchive);

    @DELETE("rest/v1/sales?id=neq.0")
    Call<Void> deleteAllSalesRemote();

    @POST("rest/v1/rpc/delete_product_by_id")
    Call<Void> deleteProductRemote(@Body Map<String, Object> body);

    @Headers("Content-Type: application/json")
    @POST("functions/v1/delete-user")
    Call<Void> deleteUser(@Body Map<String, String> body);

    @Headers("Content-Type: application/json")
    @POST("functions/v1/create-user")
    Call<Map<String, Object>> createAuthUser(@Body Map<String, Object> body);

    @Headers("Content-Type: application/json")
    @POST("functions/v1/create-user")
    Call<JsonObject> createUser(@Body JsonObject body);

    // 🚨 AJOUT CRUCIAL : La méthode manquante pour le switch Active/Inactive
    @Headers("Content-Type: application/json")
    @POST("functions/v1/toggle-user-status")
    Call<Void> toggleUserStatus(@Body Map<String, Object> body);

    // 🚨 NOUVELLE MÉTHODE : Mise à jour du mot de passe via Edge Function
    @Headers("Content-Type: application/json")
    @POST("functions/v1/update-user-password")
    Call<Void> updateUserPassword(@Body Map<String, Object> body);
}