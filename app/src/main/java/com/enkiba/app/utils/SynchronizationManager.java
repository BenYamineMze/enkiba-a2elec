package com.enkiba.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleArchive;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleItemArchive;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.modelsDao.SyncDao;
import com.enkiba.app.modelsDao.UserDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;

public class SynchronizationManager {

    private static final String TAG = "SyncManager";
    private static final String PREFS_SYNC = "sync_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final SupabaseService supabaseService;
    private final SyncDao syncDao;
    private final UserDao userDao;
    private final ProductDao productDao;
    private final Context context;
    private final SessionManager sessionManager;

    public interface SyncCallback {
        void onFinish();
    }

    public SynchronizationManager(Context context) {
        this.context = context;
        this.supabaseService = RetrofitClient.getSupabaseService(context);
        this.syncDao = new SyncDao(context);
        this.userDao = new UserDao(context);
        this.productDao = new ProductDao(context);
        this.sessionManager = new SessionManager(context);
    }

    private String getLastSyncTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SYNC, "2000-01-01T00:00:00.000Z");
    }

    private void setLastSyncTime(String timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_SYNC, timestamp).apply();
    }

    public void startSynchronization() {
        startSynchronization(null);
    }

    public void startSynchronization(SyncCallback callback) {
        if (!NetworkUtils.isOnline(context)) {
            Log.w(TAG, "Sync annulée : Hors ligne.");
            if (callback != null) callback.onFinish();
            return;
        }

        final String lastSyncTimestamp = getLastSyncTime();
        final String currentSyncTime = DatabaseUtils.getCurrentIsoTime();

        new Thread(() -> {
            try {
                // 1. PUSH
                syncPendingUsers();
                pushLocalChangesSync();

                // 2. PULL
                pullRemoteChangesSynchronous(lastSyncTimestamp);

                setLastSyncTime(currentSyncTime);
                Log.d(TAG, "--- SYNCHRO TERMINÉE AVEC SUCCÈS ---");

            } catch (Exception e) {
                Log.e(TAG, "Erreur Critique Synchro: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onFinish);
                }
            }
        }).start();
    }

    // ----------------------------------------------------------------------------------
    // PHASE PUSH
    // ----------------------------------------------------------------------------------

    private void pushLocalChangesSync() throws Exception {
        Log.d(TAG, "--- DEBUT PUSH ---");
        String preferHeader = "resolution=merge-duplicates";

        try { pushUserTableSync(syncDao.getPendingUsers()); } catch (Exception e) {}
        try { pushClientTableSync(syncDao.getPendingClients(), preferHeader); } catch (Exception e) {}

        try {
            // Push Produits
            List<Product> pendingProducts = syncDao.getPendingProducts();
            List<Product> toUpsert = new ArrayList<>();
            List<Product> toDelete = new ArrayList<>();
            for (Product p : pendingProducts) {
                if ("deleted".equalsIgnoreCase(p.getSyncStatus())) toDelete.add(p);
                else toUpsert.add(p);
            }
            pushProductTableSync(toUpsert);
            pushDeletedProductsSync(toDelete);
        } catch (Exception e) {}

        try {
            // [CORRECTION] Utilisation du mode representation pour récupérer les IDs générés
            pushSaleHeaderTableSync(syncDao.getPendingSales(), "resolution=merge-duplicates, return=representation");
            pushSaleItemTableSync(syncDao.getPendingSaleItems(), preferHeader);
        } catch (Exception e) { Log.e(TAG, "Erreur PUSH Ventes: " + e.getMessage()); e.printStackTrace(); }

        try {
            List<SaleArchive> archives = syncDao.getPendingSaleArchives();
            if (!archives.isEmpty()) {
                pushSaleArchiveTableSync(archives, preferHeader);
                pushSaleItemArchiveTableSync(syncDao.getPendingSaleItemArchives(), preferHeader);
                deleteArchivedSalesFromServer();
            }
        } catch (Exception e) {}

        try { pushHistoryTableSync(syncDao.getPendingHistory()); } catch (Exception e) {}
    }

    // --- CORRECTION IDs NÉGATIFS + PRIX NÉGOCIÉ ---
    private void pushSaleHeaderTableSync(List<SaleHeader> sales, String prefer) throws Exception {
        for (SaleHeader s : sales) {
            Map<String, Object> map = new HashMap<>();
            map.put("total_price", s.getTotalPrice());
            map.put("updated_at", s.getUpdatedAt());
            map.put("payment_method", s.getPaymentMethod() != null ? s.getPaymentMethod() : "espece");

            // [IMPORTANT] Envoi du statut négocié
            map.put("has_negotiation", s.isHasNegotiatedPrice());

            Long clientId = s.getClientId();
            map.put("client_id", (clientId != null && clientId != 0) ? clientId : null);
            map.put("user_id", s.getUserId() != null ? s.getUserId() : sessionManager.getUserId());

            // Si l'ID est positif, on l'envoie. Sinon on laisse Supabase générer.
            if (s.getId() > 0) {
                map.put("id", s.getId());
            }

            // Upsert avec retour de l'objet créé
            Response<List<SaleHeader>> res = supabaseService.upsertSaleAsMap(prefer, map).execute();

            if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                SaleHeader createdSale = res.body().get(0);
                long serverId = createdSale.getId();

                // Si l'ID local était négatif, on le met à jour
                if (s.getId() <= 0) {
                    Log.d(TAG, "Mise à jour ID vente: " + s.getId() + " -> " + serverId);
                    syncDao.updateLocalId(s.getId(), serverId);
                } else {
                    syncDao.markSaleAsSynced(s.getId());
                }
            }
        }
    }

    private void pushSaleItemTableSync(List<SaleItem> items, String prefer) throws Exception {
        for (SaleItem i : items) {
            // [IMPORTANT] Si sale_id est négatif, on saute.
            if (i.getSaleId() <= 0) continue;

            Map<String, Object> map = new HashMap<>();
            map.put("sale_id", i.getSaleId());
            map.put("product_id", i.getProductId());
            map.put("quantity", i.getQuantity());
            int initQty = (i.getInitialQuantity() > 0) ? i.getInitialQuantity() : i.getQuantity();
            map.put("initial_quantity", initQty);
            map.put("unit_price", i.getUnitPrice());
            map.put("total_price", i.getTotalPrice());
            if (i.getId() > 0) map.put("id", i.getId());

            Response<Void> res = supabaseService.upsertSaleItemMap(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markSaleItemAsSynced(i.getId());
            }
        }
    }

    private void pushUserTableSync(List<User> users) throws Exception {
        if (users.isEmpty()) return;
        String userPrefer = "return=representation";
        for (User user : users) {
            if (user.getEmail() != null) user.setEmail(user.getEmail().trim());
            if (user.getAuthId() == null || user.getAuthId().isEmpty()) {
                try {
                    Response<List<User>> check = supabaseService.getUserProfile("eq." + user.getEmail(), "*").execute();
                    if (check.isSuccessful() && check.body() != null && !check.body().isEmpty()) {
                        user.setAuthId(check.body().get(0).getAuthId());
                        userDao.insertOrUpdateUserFromRemote(user);
                    }
                } catch (Exception e) {}
            }
            Map<String, Object> map = new HashMap<>();
            if (user.getAuthId() != null) map.put("auth_id", user.getAuthId());
            map.put("username", user.getUsername());
            map.put("lastname", user.getLastname());
            map.put("email", user.getEmail());
            map.put("role", user.getRole());
            map.put("telephone", user.getTelephone());
            map.put("active", user.getActive());
            map.put("updated_at", user.getUpdatedAt());

            Call<Void> call = (user.getAuthId() != null)
                    ? supabaseService.upsertUserMap(userPrefer, "auth_id", map)
                    : supabaseService.upsertUserMap(userPrefer, "email", map);
            Response<Void> res = call.execute();
            if (res.isSuccessful() || res.code() == 409) {
                syncDao.markUserAsSynced(user.getId());
            }
        }
    }

    private void pushClientTableSync(List<Client> clients, String prefer) throws Exception {
        for (Client c : clients) {
            Map<String, Object> map = new HashMap<>();
            map.put("firstname", c.getFirstname());
            map.put("lastname", c.getLastname());
            map.put("email", c.getEmail());
            map.put("phone", c.getPhone());
            map.put("address", c.getAddress());
            map.put("updated_at", c.getUpdatedAt());
            if (c.getId() != 0) map.put("id", c.getId());
            if (supabaseService.upsertClientMap(prefer, map).execute().isSuccessful()) syncDao.markClientAsSynced(c.getId());
        }
    }

    private void pushProductTableSync(List<Product> products) throws Exception {
        String prefer = "resolution=merge-duplicates, return=representation";
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            if (p.getId() != null) map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("category", p.getCategory());
            map.put("quantity", p.getQuantity());
            map.put("sold_quantity", p.getSoldQuantity());
            map.put("price", p.getPrice());
            map.put("description", p.getDescription());
            map.put("qr_code", p.getQr_code());
            map.put("initial_stock", p.getInitial_stock());
            map.put("low_stock_threshold", p.getLowStockThreshold());
            map.put("updated_at", p.getUpdatedAt());
            Response<List<Product>> res = supabaseService.upsertProductAsMap(prefer, map).execute();
            if (res.isSuccessful()) { syncDao.markProductAsSynced(p.getId()); }
        }
    }

    private void pushDeletedProductsSync(List<Product> products) {
        for (Product p : products) {
            Map<String, Object> body = new HashMap<>();
            body.put("product_id_to_delete", p.getId());
            try {
                Response<Void> response = supabaseService.deleteProductRemote(body).execute();
                if (response.isSuccessful()) { productDao.deleteProductPhysically(p.getId()); }
            } catch (IOException e) { Log.e(TAG, "Exception suppression produit: " + e.getMessage()); }
        }
    }

    private void pushHistoryTableSync(List<ProductHistory> histories) throws Exception {
        if (histories.isEmpty()) return;

        Log.d(TAG, "Envoi de " + histories.size() + " logs historique...");

        for (ProductHistory h : histories) {
            if (h.getProductId() == null) {
                syncDao.markHistoryAsSynced(h.getId());
                continue;
            }

            Response<Void> res = supabaseService.upsertHistory(h).execute();

            if (res.isSuccessful() || res.code() == 409) {
                syncDao.markHistoryAsSynced(h.getId());
            } else {
                // Affichage explicite de l'erreur pour débogage
                String errorBody = res.errorBody() != null ? res.errorBody().string() : "null";
                Log.e(TAG, "❌ Échec PUSH History ID " + h.getId() + " (Code " + res.code() + "): " + errorBody);
            }
        }
    }

    private void pushSaleArchiveTableSync(List<SaleArchive> archives, String prefer) throws Exception {
        for (SaleArchive ar : archives) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ar.getId());
            map.put("sale_date", ar.getSaleDate());
            map.put("user_id", ar.getUserId());
            map.put("client_id", ar.getClientId());
            map.put("total_price", ar.getTotalPrice());
            map.put("payment_method", ar.getPaymentMethod());
            map.put("updated_at", ar.getUpdatedAt());
            if (supabaseService.upsertSaleArchiveMap(prefer, map).execute().isSuccessful())
                syncDao.markSaleArchiveAsSynced(ar.getId());
        }
    }

    private void pushSaleItemArchiveTableSync(List<SaleItemArchive> items, String prefer) throws Exception {
        for (SaleItemArchive i : items) {
            if (!syncDao.isSaleArchiveSynced(i.getSaleId())) continue;
            Map<String, Object> map = new HashMap<>();
            map.put("sale_id", i.getSaleId());
            map.put("product_name", i.getProductName());
            map.put("quantity", i.getQuantity());
            int initQty = i.getQuantity();
            try {} catch (Exception e) {}
            map.put("initial_quantity", initQty);
            map.put("unit_price", i.getUnitPrice());
            map.put("total_price", i.getTotalPrice());
            map.put("updated_at", i.getUpdatedAt());
            if (supabaseService.upsertSaleItemArchiveMap(prefer, map).execute().isSuccessful())
                syncDao.markSaleItemArchiveAsSynced(i.getId());
        }
    }

    private void deleteArchivedSalesFromServer() {
        try {
            supabaseService.deleteAllSaleItemsRemote().execute();
            supabaseService.deleteAllSalesRemote().execute();
        } catch (Exception e) {}
    }

    private void syncPendingUsers() {
        if (!NetworkUtils.isOnline(context)) return;
        List<User> pendingUsers = userDao.getUsersBySyncStatus("pending_create");
        for (User user : pendingUsers) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("email", user.getEmail());
                body.put("password", user.getPassword());
                body.put("role", user.getRole());
                Response<Map<String, Object>> res = supabaseService.createAuthUser(body).execute();
                if (res.isSuccessful() && res.body() != null) {
                    user.setAuthId((String) res.body().get("id"));
                    supabaseService.upsertUser(user).execute();
                    user.setSyncStatus("synced");
                    userDao.updateUser(user);
                }
            } catch (Exception e) {}
        }
    }

    private void pullRemoteChangesSynchronous(String lastSyncTime) {
        String filterDate = "gt." + lastSyncTime;
        String selectAll = "*";
        try {
            Response<List<Product>> respProducts = supabaseService.getProductsSince("gt.1970-01-01T00:00:00.000Z", selectAll).execute();
            if (respProducts.isSuccessful() && respProducts.body() != null) {
                List<Product> remoteList = respProducts.body();
                Set<String> remoteIds = new HashSet<>();
                for (Product p : remoteList) {
                    remoteIds.add(p.getId());
                    Product localP = productDao.getProductById(p.getId());
                    if (localP != null && !"synced".equalsIgnoreCase(localP.getSyncStatus())) continue;
                    syncDao.insertOrUpdateProduct(p);
                }
                List<Product> localList = productDao.getAllProducts();
                for (Product local : localList) {
                    if (local.getId() != null && !remoteIds.contains(local.getId())) {
                        if (!"synced".equalsIgnoreCase(local.getSyncStatus())) continue;
                        productDao.deleteProductPhysically(local.getId());
                    }
                }
            }
            Response<List<User>> respUsers = supabaseService.getUsersSince(filterDate, selectAll).execute();
            if (respUsers.isSuccessful() && respUsers.body() != null) for (User u : respUsers.body()) userDao.insertOrUpdateUserFromRemote(u);
            Response<List<Client>> respClients = supabaseService.getClientsSince(filterDate, selectAll).execute();
            if (respClients.isSuccessful() && respClients.body() != null) for (Client c : respClients.body()) syncDao.insertOrUpdateClient(c);
            Response<List<SaleHeader>> respSales = supabaseService.getSalesSince(filterDate, selectAll).execute();
            if (respSales.isSuccessful() && respSales.body() != null) for (SaleHeader s : respSales.body()) syncDao.insertOrUpdateSale(s);
            Response<List<SaleItem>> respItems = supabaseService.getSaleItemsSince(filterDate, selectAll).execute();
            if (respItems.isSuccessful() && respItems.body() != null) {
                for (SaleItem i : respItems.body()) {
                    if (i.getInitialQuantity() <= 0) i.setInitialQuantity(i.getQuantity());
                    syncDao.insertOrUpdateSaleItem(i);
                }
            }
            Response<List<ProductHistory>> respHist = supabaseService.getHistorySince(filterDate, selectAll).execute();
            if (respHist.isSuccessful() && respHist.body() != null) for (ProductHistory h : respHist.body()) syncDao.insertOrUpdateHistory(h);

        } catch (IOException e) {
            Log.e(TAG, "Erreur PULL Synchrone : " + e.getMessage());
        }
    }
}