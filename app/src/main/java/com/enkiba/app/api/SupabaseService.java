package com.enkiba.app.api;

import com.enkiba.app.models.AuditLogEntry;
import com.enkiba.app.models.AuthResponse;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Membership;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.Shop;
import com.enkiba.app.models.ShopMemberOverview;
import com.enkiba.app.models.ShopOverview;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.QuoteItem;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.models.ShopMember;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
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

    /** Boutique de l'utilisateur : nom et coordonnées (factures, devis, « Ma boutique »). */
    @GET("rest/v1/shops")
    Call<List<Shop>> getShop(@Query("id") String idFilter, @Query("select") String select);

    /** « Ma boutique » : la policy shops_update limite la modification à l'admin de la boutique. */
    @Headers({"Content-Type: application/json", "Prefer: return=representation"})
    @PATCH("rest/v1/shops")
    Call<List<Shop>> updateShop(@Query("id") String idFilter, @Body Map<String, Object> fields);

    /** « Mon profil » : prénom, nom, téléphone (la policy limite à son propre profil). */
    @Headers({"Content-Type: application/json", "Prefer: return=representation"})
    @PATCH("rest/v1/profiles")
    Call<List<Profile>> updateMyProfile(@Query("id") String idFilter, @Body Map<String, Object> fields);

    // ======================================================================
    // 2. GESTION DES UTILISATEURS (Edge Functions)
    // ======================================================================

    @GET("rest/v1/shop_members")
    Call<List<ShopMember>> getShopMembers(
            @Query("shop_id") String shopIdFilter,
            @Query("select") String select);

    /** Super-administrateur : renvoie sa propre ligne, ou rien. */
    @GET("rest/v1/platform_admins")
    Call<List<JsonObject>> getPlatformAdmin(@Query("user_id") String userIdFilter, @Query("select") String select);

    /** Super-admin : toutes les boutiques avec leurs indicateurs. */
    @POST("rest/v1/rpc/shop_overview")
    Call<List<ShopOverview>> shopOverview(@Body JsonObject empty);

    /** Super-admin : membres d'une boutique ({"p_shop": "<uuid>"}). */
    @POST("rest/v1/rpc/shop_members_overview")
    Call<List<ShopMemberOverview>> shopMembersOverview(@Body JsonObject body);

    /** Super-admin : modification d'une boutique, dont suspended_at (null pour réactiver). */
    @Headers({"Content-Type: application/json", "Prefer: return=representation"})
    @PATCH("rest/v1/shops")
    Call<List<Shop>> patchShop(@Query("id") String idFilter, @Body JsonObject fields);

    /** Appartenances avec le nom de la boutique (vide si la boutique est suspendue). */
    @GET("rest/v1/shop_members")
    Call<List<JsonObject>> getMembershipsDetailed(@Query("user_id") String userIdFilter, @Query("select") String select);

    /** Super-admin : se rattacher à une boutique comme admin ({"p_shop": "<uuid>"}). */
    @POST("rest/v1/rpc/join_shop_as_admin")
    Call<Void> joinShopAsAdmin(@Body JsonObject body);

    /** Super-admin : création d'une boutique et de son administrateur. */
    @POST("functions/v1/create-shop")
    Call<JsonObject> createShop(@Body JsonObject body);

    @POST("functions/v1/create-seller")
    Call<JsonObject> createSeller(@Body JsonObject body);

    @POST("functions/v1/set-seller-status")
    Call<JsonObject> setSellerStatus(@Body JsonObject body);

    @POST("functions/v1/update-seller")
    Call<JsonObject> updateSeller(@Body JsonObject body);

    // ======================================================================
    // 3. SYNCHRONISATION DESCENDANTE (PULL)
    // Incrémental : updated_at (ou created_at pour audit_log) >= curseur, trié, par pages.
    // since = "gte.<horodatage serveur>", order = "updated_at.asc,id.asc"
    // ======================================================================

    @GET("rest/v1/products")
    Call<List<Product>> pullProducts(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                     @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/clients")
    Call<List<Client>> pullClients(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                   @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/sales")
    Call<List<SaleHeader>> pullSales(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                     @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/sale_items")
    Call<List<SaleItem>> pullSaleItems(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                       @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/sale_returns")
    Call<List<SaleReturn>> pullSaleReturns(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                           @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/quotes")
    Call<List<Quote>> pullQuotes(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                 @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/quote_items")
    Call<List<QuoteItem>> pullQuoteItems(@Query("shop_id") String shopFilter, @Query("updated_at") String since,
                                         @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/audit_log?entity=eq.product")
    Call<List<AuditLogEntry>> pullProductAuditLog(@Query("shop_id") String shopFilter, @Query("created_at") String since,
                                                  @Query("order") String order, @Query("limit") int limit);

    // ======================================================================
    // 4. SYNCHRONISATION ASCENDANTE (PUSH)
    // Le header Prefer est choisi par le SynchronizationManager :
    // - resolution=ignore-duplicates pour ce qui n'est jamais modifié après création
    //   (ventes, lignes, retours, mouvements de stock, journal) : un renvoi est sans effet ;
    // - resolution=merge-duplicates pour les clients (modifiables par tous les membres).
    // ======================================================================

    @Headers("Content-Type: application/json")
    @POST("rest/v1/clients")
    Call<Void> upsertClient(@Header("Prefer") String prefer, @Body Map<String, Object> client);

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
    @POST("rest/v1/stock_movements")
    Call<Void> insertStockMovement(@Header("Prefer") String prefer, @Body Map<String, Object> movement);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sales")
    Call<Void> insertSale(@Header("Prefer") String prefer, @Body Map<String, Object> sale);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_items")
    Call<Void> insertSaleItem(@Header("Prefer") String prefer, @Body Map<String, Object> item);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/sale_returns")
    Call<Void> insertSaleReturn(@Header("Prefer") String prefer, @Body Map<String, Object> saleReturn);

    // Devis : upsert (le statut change) ; lignes : jamais modifiées après création
    @Headers("Content-Type: application/json")
    @POST("rest/v1/quotes")
    Call<Void> upsertQuote(@Header("Prefer") String prefer, @Body Map<String, Object> quote);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/quote_items")
    Call<Void> insertQuoteItem(@Header("Prefer") String prefer, @Body Map<String, Object> item);

    @Headers("Content-Type: application/json")
    @POST("rest/v1/audit_log")
    Call<Void> insertAuditLog(@Header("Prefer") String prefer, @Body Map<String, Object> entry);

    // ======================================================================
    // 5. DIVERS
    // ======================================================================

    @GET("rest/v1/shop_members")
    Call<List<ShopMember>> getShopMember(
            @Query("shop_id") String shopIdFilter,
            @Query("user_id") String userIdFilter,
            @Query("select") String select);
}