package com.enkiba.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.models.StockMovement;
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

    // Insertion idempotente : une ligne déjà présente (même id) est ignorée sans erreur.
    // Pour les données qu'on ne modifie jamais après création (ventes, mouvements, journal).
    private static final String PREFER_IGNORE_DUPLICATES = "resolution=ignore-duplicates, return=minimal";
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

    // Une seule synchronisation à la fois pour tout le processus (plusieurs écrans en créent).
    // Une demande reçue pendant une synchro en cours relance un tour juste après,
    // pour que la dernière modification locale soit toujours envoyée.
    private static final Object LOCK = new Object();
    private static boolean running = false;
    private static boolean rerunRequested = false;
    private static final List<SyncCallback> waitingCallbacks = new ArrayList<>();

    /**
     * Lance la synchronisation dans un thread séparé.
     * Ne fait rien si l'appareil est hors ligne ou si aucun utilisateur n'est connecté.
     * Le callback est appelé sur le thread principal à la fin du tour qui inclut cette demande.
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

        synchronized (LOCK) {
            if (callback != null) waitingCallbacks.add(callback);
            if (running) {
                rerunRequested = true;
                Log.d(TAG, "Sync déjà en cours : un nouveau tour suivra.");
                return;
            }
            running = true;
        }

        new Thread(() -> {
            while (true) {
                synchronized (LOCK) {
                    rerunRequested = false;
                }
                runOnce();
                List<SyncCallback> toNotify;
                synchronized (LOCK) {
                    if (rerunRequested) continue;
                    running = false;
                    toNotify = new ArrayList<>(waitingCallbacks);
                    waitingCallbacks.clear();
                }
                if (!toNotify.isEmpty()) {
                    android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                    for (SyncCallback cb : toNotify) main.post(cb::onFinish);
                }
                break;
            }
        }, "sync").start();
    }

    /** Un tour complet : PUSH puis PULL. Ne lève jamais d'exception. */
    private void runOnce() {
        final String lastSyncTimestamp = getLastSyncTime();
        final String currentSyncTime = DatabaseUtils.getCurrentIsoTime();
        try {
            // 1. PUSH : envoi des modifications locales
            pushLocalChangesSync();

            // 2. PULL : récupération des changements du serveur
            pullRemoteChangesSynchronous(lastSyncTimestamp);

            setLastSyncTime(currentSyncTime);
            Log.d(TAG, "--- SYNCHRO TERMINÉE ---");
        } catch (Exception e) {
            Log.e(TAG, "Erreur critique synchro : " + e.getMessage(), e);
        }
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

        // Produits (les suppressions sont logiques : deleted_at est envoyé avec le reste)
        try {
            pushProductTableSync(syncDao.getPendingProducts());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH produits : " + e.getMessage(), e);
        }

        // Mouvements de stock saisis localement (après les produits : clé étrangère)
        try {
            pushStockMovementsSync(syncDao.getPendingStockMovements());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH mouvements de stock : " + e.getMessage(), e);
        }

        // Ventes : l'en-tête d'abord, puis les lignes
        try {
            pushSaleHeaderTableSync(syncDao.getPendingSales(), preferHeader);
            pushSaleItemTableSync(syncDao.getPendingSaleItems(), preferHeader);
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH ventes : " + e.getMessage(), e);
        }

        // Retours (après les lignes de vente qu'ils référencent)
        try {
            pushSaleReturnsSync(syncDao.getPendingSaleReturns());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH retours : " + e.getMessage(), e);
        }

        // Historique des produits
        try {
            pushHistoryTableSync(syncDao.getPendingHistory());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH historique : " + e.getMessage(), e);
        }
    }

    /**
     * Envoie les en-têtes de vente. L'id (UUID) est généré par le téléphone :
     * le serveur garde le même, il n'y a plus d'id à reporter localement.
     * TODO étape 4 : resolution=ignore-duplicates (un vendeur ne modifie pas une vente).
     */
    private void pushSaleHeaderTableSync(List<SaleHeader> sales, String prefer) throws Exception {
        for (SaleHeader s : sales) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("shop_id", s.getShopId() != null ? s.getShopId() : sessionManager.getShopId());
            map.put("seller_id", s.getSellerId() != null ? s.getSellerId() : sessionManager.getUserId());
            map.put("client_id", s.getClientId());
            map.put("total_price", s.getTotalPrice());
            map.put("payment_method", s.getPaymentMethod() != null ? s.getPaymentMethod() : "espece");
            map.put("has_negotiation", s.isHasNegotiatedPrice()); // vente à prix négocié
            map.put("sale_date", s.getSaleDate());

            Response<Void> res = supabaseService.upsertSaleMap(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markSaleAsSynced(s.getId());
            } else {
                logFailure("vente " + s.getId(), res);
            }
        }
    }

    /** Envoie les lignes de vente (la vente parente est envoyée juste avant). */
    private void pushSaleItemTableSync(List<SaleItem> items, String prefer) throws Exception {
        for (SaleItem i : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", i.getId());
            map.put("shop_id", i.getShopId() != null ? i.getShopId() : sessionManager.getShopId());
            map.put("sale_id", i.getSaleId());
            map.put("product_id", i.getProductId());
            map.put("product_name", i.getProductName());
            map.put("quantity", i.getQuantity());
            map.put("list_price", i.getListPrice());
            map.put("unit_price", i.getUnitPrice());
            map.put("total_price", i.getTotalPrice());

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
            map.put("id", c.getId());
            map.put("shop_id", c.getShopId() != null ? c.getShopId() : sessionManager.getShopId());

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
            map.put("id", p.getId());
            map.put("shop_id", p.getShopId() != null ? p.getShopId() : sessionManager.getShopId());
            map.put("name", p.getName());
            map.put("category", p.getCategory());
            // Pas de quantity : le serveur la calcule à partir des mouvements de stock
            map.put("price", p.getPrice());
            map.put("description", p.getDescription());
            map.put("qr_code", p.getQr_code());
            map.put("initial_stock", p.getInitial_stock());
            map.put("low_stock_threshold", p.getLowStockThreshold());
            map.put("deleted_at", p.getDeletedAt());

            Response<List<Product>> res = supabaseService.upsertProductAsMap(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markProductAsSynced(p.getId());
            } else {
                logFailure("produit " + p.getId(), res);
            }
        }
    }

    /**
     * Envoie les mouvements de stock saisis par un admin.
     * Rejouer un envoi est sans effet (même id, doublon ignoré).
     */
    private void pushStockMovementsSync(List<StockMovement> movements) throws Exception {
        for (StockMovement m : movements) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("shop_id", m.getShopId() != null ? m.getShopId() : sessionManager.getShopId());
            map.put("product_id", m.getProductId());
            map.put("delta", m.getDelta());
            map.put("reason", m.getReason());
            map.put("created_by", m.getCreatedBy() != null ? m.getCreatedBy() : sessionManager.getUserId());

            Response<Void> res = supabaseService.insertStockMovement(PREFER_IGNORE_DUPLICATES, map).execute();
            if (res.isSuccessful()) {
                syncDao.markStockMovementAsSynced(m.getId());
            } else {
                logFailure("mouvement de stock " + m.getId(), res);
            }
        }
    }

    /**
     * Envoie les retours. Le serveur revérifie la quantité retournable et remet le stock
     * (mouvement 'sale_cancel'). Rejouer un envoi est sans effet (même id, doublon ignoré).
     */
    private void pushSaleReturnsSync(List<SaleReturn> returns) throws Exception {
        for (SaleReturn r : returns) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("shop_id", r.getShopId() != null ? r.getShopId() : sessionManager.getShopId());
            map.put("sale_id", r.getSaleId());
            map.put("sale_item_id", r.getSaleItemId());
            map.put("product_id", r.getProductId());
            map.put("quantity", r.getQuantity());
            map.put("refund_amount", r.getRefundAmount());
            map.put("created_by", r.getCreatedBy() != null ? r.getCreatedBy() : sessionManager.getUserId());

            Response<Void> res = supabaseService.insertSaleReturn(PREFER_IGNORE_DUPLICATES, map).execute();
            if (res.isSuccessful()) {
                syncDao.markSaleReturnAsSynced(r.getId());
            } else {
                logFailure("retour " + r.getId(), res);
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
                for (Client c : respClients.body()) syncDao.upsertClient(c);
            }

            // Ventes
            Response<List<SaleHeader>> respSales = supabaseService.getSalesSince(filterDate, selectAll).execute();
            if (respSales.isSuccessful() && respSales.body() != null) {
                for (SaleHeader s : respSales.body()) syncDao.upsertSale(s);
            }

            // Lignes de vente
            Response<List<SaleItem>> respItems = supabaseService.getSaleItemsSince(filterDate, selectAll).execute();
            if (respItems.isSuccessful() && respItems.body() != null) {
                for (SaleItem i : respItems.body()) syncDao.upsertSaleItem(i);
            }

            // Retours
            Response<List<SaleReturn>> respReturns = supabaseService.getSaleReturnsSince(filterDate, selectAll).execute();
            if (respReturns.isSuccessful() && respReturns.body() != null) {
                for (SaleReturn r : respReturns.body()) syncDao.upsertSaleReturn(r);
            } else {
                logFailure("pull retours", respReturns);
            }

            // Historique (la table "history" n'existe plus en v2 : réponse 404 attendue jusqu'à l'étape 4)
            Response<List<ProductHistory>> respHist = supabaseService.getHistorySince(filterDate, selectAll).execute();
            if (respHist.isSuccessful() && respHist.body() != null) {
                for (ProductHistory h : respHist.body()) syncDao.upsertHistory(h);
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