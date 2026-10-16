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
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.modelsDao.SyncDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Response;

/**
 * Synchronise la base locale (SQLite) avec Supabase.
 *
 * Fonctionnement : PUSH (envoi des modifications locales "pending")
 * puis PULL (récupération des changements du serveur).
 *
 * NOTE : les utilisateurs ne sont plus synchronisés ici. Ils sont gérés
 * directement par le serveur (écrans de gestion des utilisateurs + Edge Functions).
 *
 * TODO étape 4 : réécriture complète (UUID, stock_movements, pull incrémental
 * basé sur le updated_at serveur).
 */
public class SynchronizationManager {

    private static final String TAG = "SyncManager";
    private static final String PREFS_SYNC = "sync_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final SupabaseService supabaseService;
    private final SyncDao syncDao;
    private final ProductDao productDao;
    private final Context context;
    private final SessionManager sessionManager;

    /** Appelé à la fin de la synchronisation, sur le thread principal. */
    public interface SyncCallback {
        void onFinish();
    }

    public SynchronizationManager(Context context) {
        this.context = context;
        this.supabaseService = RetrofitClient.getSupabaseService(context);
        this.syncDao = new SyncDao(context);
        this.productDao = new ProductDao(context);
        this.sessionManager = new SessionManager(context);
    }

    // ----------------------------------------------------------------------------------
    // DATE DE DERNIÈRE SYNCHRO
    // ----------------------------------------------------------------------------------

    private String getLastSyncTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SYNC, "2000-01-01T00:00:00.000Z");
    }

    private void setLastSyncTime(String timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_SYNC, timestamp).apply();
    }

    // ----------------------------------------------------------------------------------
    // POINT D'ENTRÉE
    // ----------------------------------------------------------------------------------

    public void startSynchronization() {
        startSynchronization(null);
    }

    /**
     * Lance la synchronisation dans un thread séparé.
     * Ne fait rien si l'appareil est hors ligne ou si aucun utilisateur n'est connecté.
     */
    public void startSynchronization(SyncCallback callback) {
        if (!NetworkUtils.isOnline(context)) {
            Log.w(TAG, "Sync annulée : hors ligne.");
            if (callback != null) callback.onFinish();
            return;
        }
        // Sans session, toute requête serait refusée par le serveur
        if (!sessionManager.isLoggedIn()) {
            Log.w(TAG, "Sync annulée : aucune session.");
            if (callback != null) callback.onFinish();
            return;
        }

        final String lastSyncTimestamp = getLastSyncTime();
        final String currentSyncTime = DatabaseUtils.getCurrentIsoTime();

        new Thread(() -> {
            try {
                // 1. PUSH : envoi des modifications locales
                pushLocalChangesSync();

                // 2. PULL : récupération des changements du serveur
                pullRemoteChangesSynchronous(lastSyncTimestamp);

                setLastSyncTime(currentSyncTime);
                Log.d(TAG, "--- SYNCHRO TERMINÉE ---");

            } catch (Exception e) {
                Log.e(TAG, "Erreur critique synchro : " + e.getMessage(), e);
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

    /** Envoie au serveur tout ce qui est en attente ("pending") dans la base locale. */
    private void pushLocalChangesSync() {
        Log.d(TAG, "--- DÉBUT PUSH ---");
        String preferHeader = "resolution=merge-duplicates";

        // Clients
        try {
            pushClientTableSync(syncDao.getPendingClients(), preferHeader);
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH clients : " + e.getMessage(), e);
        }

        // Produits : on sépare les créations/modifications des suppressions
        try {
            List<Product> pendingProducts = syncDao.getPendingProducts();
            List<Product> toUpsert = new ArrayList<>();
            List<Product> toDelete = new ArrayList<>();
            for (Product p : pendingProducts) {
                if ("deleted".equalsIgnoreCase(p.getSyncStatus())) toDelete.add(p);
                else toUpsert.add(p);
            }
            pushProductTableSync(toUpsert);
            pushDeletedProductsSync(toDelete);
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH produits : " + e.getMessage(), e);
        }

        // Ventes : l'en-tête d'abord (pour récupérer l'ID serveur), puis les lignes
        try {
            pushSaleHeaderTableSync(syncDao.getPendingSales(), "resolution=merge-duplicates, return=representation");
            pushSaleItemTableSync(syncDao.getPendingSaleItems(), preferHeader);
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH ventes : " + e.getMessage(), e);
        }

        // Archives (à supprimer à l'étape 4 : les tables n'existent plus en v2)
        try {
            List<SaleArchive> archives = syncDao.getPendingSaleArchives();
            if (!archives.isEmpty()) {
                pushSaleArchiveTableSync(archives, preferHeader);
                pushSaleItemArchiveTableSync(syncDao.getPendingSaleItemArchives(), preferHeader);
                deleteArchivedSalesFromServer();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH archives : " + e.getMessage(), e);
        }

        // Historique des produits
        try {
            pushHistoryTableSync(syncDao.getPendingHistory());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH historique : " + e.getMessage(), e);
        }
    }

    /**
     * Envoie les en-têtes de vente.
     * Une vente créée hors ligne a un ID local négatif : le serveur en génère un
     * positif, qu'on reporte ensuite dans la base locale (updateLocalId).
     */
    private void pushSaleHeaderTableSync(List<SaleHeader> sales, String prefer) throws Exception {
        for (SaleHeader s : sales) {
            Map<String, Object> map = new HashMap<>();
            map.put("total_price", s.getTotalPrice());
            map.put("updated_at", s.getUpdatedAt());
            map.put("payment_method", s.getPaymentMethod() != null ? s.getPaymentMethod() : "espece");
            map.put("has_negotiation", s.isHasNegotiatedPrice()); // vente à prix négocié

            Long clientId = s.getClientId();
            map.put("client_id", (clientId != null && clientId != 0) ? clientId : null);
            map.put("user_id", s.getUserId() != null ? s.getUserId() : sessionManager.getUserId());

            // ID positif = déjà connu du serveur ; sinon on le laisse en générer un
            if (s.getId() > 0) {
                map.put("id", s.getId());
            }

            Response<List<SaleHeader>> res = supabaseService.upsertSaleAsMap(prefer, map).execute();

            if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                long serverId = res.body().get(0).getId();
                if (s.getId() <= 0) {
                    Log.d(TAG, "ID vente : " + s.getId() + " -> " + serverId);
                    syncDao.updateLocalId(s.getId(), serverId);
                } else {
                    syncDao.markSaleAsSynced(s.getId());
                }
            } else {
                logFailure("vente " + s.getId(), res);
            }
        }
    }

    /** Envoie les lignes de vente (seulement si leur vente a déjà un ID serveur). */
    private void pushSaleItemTableSync(List<SaleItem> items, String prefer) throws Exception {
        for (SaleItem i : items) {
            // Vente pas encore envoyée (ID négatif) : on attend la prochaine synchro
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
            } else {
                logFailure("ligne de vente " + i.getId(), res);
            }
        }
    }

    /** Envoie les clients créés ou modifiés localement. */
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

            Response<Void> res = supabaseService.upsertClientMap(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markClientAsSynced(c.getId());
            } else {
                logFailure("client " + c.getId(), res);
            }
        }
    }

    /** Envoie les produits créés ou modifiés localement (identifiés par un UUID). */
    private void pushProductTableSync(List<Product> products) throws Exception {
        String prefer = "resolution=merge-duplicates, return=representation";
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            if (p.getId() != null) map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("category", p.getCategory());
            map.put("quantity", p.getQuantity());
            map.put("sold_quantity", p.getSoldQuantity()); // n'existe plus en v2 (étape 4)
            map.put("price", p.getPrice());
            map.put("description", p.getDescription());
            map.put("qr_code", p.getQr_code());
            map.put("initial_stock", p.getInitial_stock());
            map.put("low_stock_threshold", p.getLowStockThreshold());
            map.put("updated_at", p.getUpdatedAt());

            Response<List<Product>> res = supabaseService.upsertProductAsMap(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markProductAsSynced(p.getId());
            } else {
                logFailure("produit " + p.getId(), res);
            }
        }
    }

    /** Supprime côté serveur les produits supprimés localement, puis les efface du téléphone. */
    private void pushDeletedProductsSync(List<Product> products) {
        for (Product p : products) {
            Map<String, Object> body = new HashMap<>();
            body.put("product_id_to_delete", p.getId());
            try {
                Response<Void> response = supabaseService.deleteProductRemote(body).execute();
                if (response.isSuccessful()) {
                    productDao.deleteProductPhysically(p.getId());
                } else {
                    logFailure("suppression produit " + p.getId(), response);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception suppression produit : " + e.getMessage());
            }
        }
    }

    /** Envoie le journal des opérations sur les produits. */
    private void pushHistoryTableSync(List<ProductHistory> histories) throws Exception {
        if (histories.isEmpty()) return;
        Log.d(TAG, "Envoi de " + histories.size() + " logs historique...");

        for (ProductHistory h : histories) {
            // Entrée sans produit : inutilisable, on la considère comme traitée
            if (h.getProductId() == null) {
                syncDao.markHistoryAsSynced(h.getId());
                continue;
            }

            Response<Void> res = supabaseService.upsertHistory(h).execute();

            // 409 = déjà présent sur le serveur : on le marque comme envoyé
            if (res.isSuccessful() || res.code() == 409) {
                syncDao.markHistoryAsSynced(h.getId());
            } else {
                logFailure("historique " + h.getId(), res);
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // ARCHIVES (à supprimer à l'étape 4)
    // ----------------------------------------------------------------------------------

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
            if (supabaseService.upsertSaleArchiveMap(prefer, map).execute().isSuccessful()) {
                syncDao.markSaleArchiveAsSynced(ar.getId());
            }
        }
    }

    private void pushSaleItemArchiveTableSync(List<SaleItemArchive> items, String prefer) throws Exception {
        for (SaleItemArchive i : items) {
            // L'archive parente doit être déjà envoyée
            if (!syncDao.isSaleArchiveSynced(i.getSaleId())) continue;

            Map<String, Object> map = new HashMap<>();
            map.put("sale_id", i.getSaleId());
            map.put("product_name", i.getProductName());
            map.put("quantity", i.getQuantity());
            map.put("initial_quantity", i.getQuantity());
            map.put("unit_price", i.getUnitPrice());
            map.put("total_price", i.getTotalPrice());
            map.put("updated_at", i.getUpdatedAt());
            if (supabaseService.upsertSaleItemArchiveMap(prefer, map).execute().isSuccessful()) {
                syncDao.markSaleItemArchiveAsSynced(i.getId());
            }
        }
    }

    /** DANGEREUX : supprime TOUTES les ventes du serveur. À retirer à l'étape 4. */
    private void deleteArchivedSalesFromServer() {
        try {
            supabaseService.deleteAllSaleItemsRemote().execute();
            supabaseService.deleteAllSalesRemote().execute();
        } catch (Exception e) {
            Log.e(TAG, "Erreur suppression des ventes archivées : " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------
    // PHASE PULL
    // ----------------------------------------------------------------------------------

    /** Récupère les données du serveur et met à jour la base locale. */
    private void pullRemoteChangesSynchronous(String lastSyncTime) {
        String filterDate = "gt." + lastSyncTime;
        String selectAll = "*";
        try {
            // Produits : récupération complète, puis suppression locale de ceux disparus du serveur
            Response<List<Product>> respProducts =
                    supabaseService.getProductsSince("gt.1970-01-01T00:00:00.000Z", selectAll).execute();
            if (respProducts.isSuccessful() && respProducts.body() != null) {
                Set<String> remoteIds = new HashSet<>();
                for (Product p : respProducts.body()) {
                    remoteIds.add(p.getId());
                    // On ne remplace jamais une modification locale non encore envoyée
                    Product localP = productDao.getProductById(p.getId());
                    if (localP != null && !"synced".equalsIgnoreCase(localP.getSyncStatus())) continue;
                    syncDao.insertOrUpdateProduct(p);
                }
                for (Product local : productDao.getAllProducts()) {
                    if (local.getId() != null && !remoteIds.contains(local.getId())) {
                        if (!"synced".equalsIgnoreCase(local.getSyncStatus())) continue;
                        productDao.deleteProductPhysically(local.getId());
                    }
                }
            } else {
                logFailure("pull produits", respProducts);
            }

            // Clients
            Response<List<Client>> respClients = supabaseService.getClientsSince(filterDate, selectAll).execute();
            if (respClients.isSuccessful() && respClients.body() != null) {
                for (Client c : respClients.body()) syncDao.insertOrUpdateClient(c);
            }

            // Ventes
            Response<List<SaleHeader>> respSales = supabaseService.getSalesSince(filterDate, selectAll).execute();
            if (respSales.isSuccessful() && respSales.body() != null) {
                for (SaleHeader s : respSales.body()) syncDao.insertOrUpdateSale(s);
            }

            // Lignes de vente
            Response<List<SaleItem>> respItems = supabaseService.getSaleItemsSince(filterDate, selectAll).execute();
            if (respItems.isSuccessful() && respItems.body() != null) {
                for (SaleItem i : respItems.body()) {
                    if (i.getInitialQuantity() <= 0) i.setInitialQuantity(i.getQuantity());
                    syncDao.insertOrUpdateSaleItem(i);
                }
            }

            // Historique (la table "history" n'existe plus en v2 : réponse 404 attendue jusqu'à l'étape 4)
            Response<List<ProductHistory>> respHist = supabaseService.getHistorySince(filterDate, selectAll).execute();
            if (respHist.isSuccessful() && respHist.body() != null) {
                for (ProductHistory h : respHist.body()) syncDao.insertOrUpdateHistory(h);
            }

        } catch (IOException e) {
            Log.e(TAG, "Erreur PULL synchrone : " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------
    // OUTILS
    // ----------------------------------------------------------------------------------

    /** Journalise un échec HTTP avec le code et le message d'erreur renvoyé par le serveur. */
    private void logFailure(String what, Response<?> res) {
        String body = "";
        try {
            if (res.errorBody() != null) body = res.errorBody().string();
        } catch (IOException ignored) { }
        Log.e(TAG, "Échec " + what + " (code " + res.code() + ") " + body);
    }
}