package com.enkiba.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.AuditLogEntry;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.QuoteItem;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.models.StockMovement;
import com.enkiba.app.modelsDao.SyncDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * Synchronise la base locale (SQLite) avec Supabase.
 *
 * Fonctionnement : PUSH (envoi des modifications locales "pending") puis PULL
 * (récupération des changements du serveur). Une seule synchronisation à la fois.
 *
 * - Les ids sont des UUID générés par le téléphone : un renvoi est idempotent.
 * - Le stock n'est jamais envoyé : le serveur le calcule à partir des mouvements de stock
 *   (saisis par un admin, ou générés par trigger pour les ventes et les retours).
 * - Le pull est incrémental, table par table, sur l'horodatage du serveur.
 * - Les suppressions passent par deleted_at, jamais par un DELETE.
 *
 * Les utilisateurs ne sont pas synchronisés ici : ils sont gérés directement par le
 * serveur (écrans de gestion des utilisateurs + Edge Functions).
 */
public class SynchronizationManager {

    private static final String TAG = "SyncManager";

    // Insertion idempotente : une ligne déjà présente (même id) est ignorée sans erreur.
    // Pour les données qu'on ne modifie jamais après création (ventes, mouvements, journal).
    private static final String PREFER_IGNORE_DUPLICATES = "resolution=ignore-duplicates, return=minimal";
    // Upsert : la ligne existante est mise à jour (clients).
    private static final String PREFER_MERGE_DUPLICATES = "resolution=merge-duplicates, return=minimal";
    private static final String PREFS_SYNC = "sync_prefs";

    private final SupabaseService supabaseService;
    private final SyncDao syncDao;
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
        this.sessionManager = new SessionManager(context);
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
        try {
            // 1. PUSH : envoi des modifications locales
            pushLocalChangesSync();

            // 2. PULL : récupération des changements du serveur
            pullRemoteChangesSynchronous();

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
        // Ordre : chaque table après celles qu'elle référence (clés étrangères)

        // Clients (modifiables par tous les membres : upsert)
        try {
            pushClientTableSync(syncDao.getPendingClients(), PREFER_MERGE_DUPLICATES);
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
            // Une vente n'est jamais modifiée par un vendeur : doublon ignoré
            pushSaleHeaderTableSync(syncDao.getPendingSales(), PREFER_IGNORE_DUPLICATES);
            pushSaleItemTableSync(syncDao.getPendingSaleItems(), PREFER_IGNORE_DUPLICATES);
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH ventes : " + e.getMessage(), e);
        }

        // Retours (après les lignes de vente qu'ils référencent)
        try {
            pushSaleReturnsSync(syncDao.getPendingSaleReturns());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH retours : " + e.getMessage(), e);
        }

        // Devis (après les ventes : un devis converti référence sa vente), puis leurs lignes
        try {
            pushQuotesSync(syncDao.getPendingQuotes());
            pushQuoteItemsSync(syncDao.getPendingQuoteItems());
        } catch (Exception e) {
            Log.e(TAG, "Erreur PUSH devis : " + e.getMessage(), e);
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
     * le serveur garde le même ; un renvoi (réponse perdue, synchro interrompue) est ignoré.
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
            map.put("source_quote_id", s.getSourceQuoteId());

            Response<Void> res = supabaseService.insertSale(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markSaleAsSynced(s.getId());
                continue;
            }
            String error = readError(res);
            if (res.code() == 409 && s.getSourceQuoteId() != null && error.contains("sales_source_quote_uniq")) {
                // Devis déjà converti sur un autre téléphone : cette vente est annulée localement
                syncDao.discardRejectedQuoteSale(s.getId(), s.getSourceQuoteId());
            } else {
                Log.e(TAG, "Échec vente " + s.getId() + " (code " + res.code() + ") " + error);
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

            Response<Void> res = supabaseService.insertSaleItem(prefer, map).execute();
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

            Response<Void> res = supabaseService.upsertClient(prefer, map).execute();
            if (res.isSuccessful()) {
                syncDao.markClientAsSynced(c.getId());
            } else {
                logFailure("client " + c.getId(), res);
            }
        }
    }

    /**
     * Envoie les produits créés ou modifiés localement (admin).
     * PATCH d'abord ; si aucune ligne n'est modifiée, le produit n'existe pas encore : INSERT.
     * Le stock (quantity) n'est jamais envoyé : il vient des mouvements de stock.
     */
    private void pushProductTableSync(List<Product> products) throws Exception {
        for (Product p : products) {
            Map<String, Object> fields = new HashMap<>();
            fields.put("name", p.getName());
            fields.put("category", p.getCategory());
            fields.put("price", p.getPrice());
            fields.put("description", p.getDescription());
            fields.put("qr_code", p.getQr_code());
            fields.put("initial_stock", p.getInitial_stock());
            fields.put("low_stock_threshold", p.getLowStockThreshold());
            fields.put("deleted_at", p.getDeletedAt());

            Response<List<Product>> patch = supabaseService
                    .patchProduct("return=representation", "eq." + p.getId(), fields).execute();
            if (!patch.isSuccessful()) {
                logFailure("modification produit " + p.getId(), patch);
                continue;
            }
            if (patch.body() == null || patch.body().isEmpty()) {
                Map<String, Object> row = new HashMap<>(fields);
                row.put("id", p.getId());
                row.put("shop_id", p.getShopId() != null ? p.getShopId() : sessionManager.getShopId());
                Response<Void> insert = supabaseService.insertProduct(PREFER_IGNORE_DUPLICATES, row).execute();
                if (!insert.isSuccessful()) {
                    logFailure("création produit " + p.getId(), insert);
                    continue;
                }
            }
            syncDao.markProductAsSynced(p.getId());
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

    /** Envoie les devis créés ou modifiés (statut, conversion, suppression) : upsert. */
    private void pushQuotesSync(List<Quote> quotes) throws Exception {
        for (Quote q : quotes) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", q.getId());
            map.put("shop_id", q.getShopId() != null ? q.getShopId() : sessionManager.getShopId());
            map.put("seller_id", q.getSellerId());
            map.put("client_id", q.getClientId());
            map.put("status", q.getStatus());
            map.put("total_price", q.getTotalPrice());
            map.put("valid_until", q.getValidUntil());
            map.put("notes", q.getNotes());
            map.put("converted_sale_id", q.getConvertedSaleId());
            map.put("deleted_at", q.getDeletedAt());

            Response<Void> res = supabaseService.upsertQuote(PREFER_MERGE_DUPLICATES, map).execute();
            if (res.isSuccessful()) {
                syncDao.markQuoteAsSynced(q.getId());
            } else {
                logFailure("devis " + q.getId(), res);
            }
        }
    }

    /** Envoie les lignes de devis (jamais modifiées : doublon ignoré). */
    private void pushQuoteItemsSync(List<QuoteItem> items) throws Exception {
        for (QuoteItem i : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", i.getId());
            map.put("shop_id", i.getShopId() != null ? i.getShopId() : sessionManager.getShopId());
            map.put("quote_id", i.getQuoteId());
            map.put("product_id", i.getProductId());
            map.put("product_name", i.getProductName());
            map.put("quantity", i.getQuantity());
            map.put("list_price", i.getListPrice());
            map.put("unit_price", i.getUnitPrice());
            map.put("total_price", i.getTotalPrice());

            Response<Void> res = supabaseService.insertQuoteItem(PREFER_IGNORE_DUPLICATES, map).execute();
            if (res.isSuccessful()) {
                syncDao.markQuoteItemAsSynced(i.getId());
            } else {
                logFailure("ligne de devis " + i.getId(), res);
            }
        }
    }

    /** Envoie le journal des opérations sur les produits. */
    private void pushHistoryTableSync(List<ProductHistory> histories) throws Exception {
        String currentUser = sessionManager.getUserId();
        for (ProductHistory h : histories) {
            // Entrée sans produit : inutilisable, on la considère comme traitée
            if (h.getProductId() == null) {
                syncDao.markHistoryAsSynced(h.getId());
                continue;
            }
            // Le serveur n'accepte que les entrées de l'utilisateur connecté : celles d'un autre
            // utilisateur de ce téléphone partiront à sa prochaine connexion
            if (h.getUserId() != null && !h.getUserId().equals(currentUser)) continue;

            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("shop_id", h.getShopId() != null ? h.getShopId() : sessionManager.getShopId());
            map.put("user_id", currentUser);
            map.put("entity", "product");
            map.put("entity_id", h.getProductId());
            map.put("operation", h.getOperation() != null ? h.getOperation() : "");
            // old_value / new_value sont en jsonb : une chaîne devient une chaîne JSON
            map.put("old_value", h.getOldValue());
            map.put("new_value", h.getNewValue());
            if (h.getTimestamp() != null) map.put("created_at", h.getTimestamp());

            Response<Void> res = supabaseService.insertAuditLog(PREFER_IGNORE_DUPLICATES, map).execute();
            if (res.isSuccessful()) {
                syncDao.markHistoryAsSynced(h.getId());
            } else {
                logFailure("historique " + h.getId(), res);
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // PHASE PULL
    // ----------------------------------------------------------------------------------

    private static final int PULL_PAGE_SIZE = 500;
    private static final String PULL_ORDER = "updated_at.asc,id.asc";
    private static final String EPOCH = "1970-01-01T00:00:00+00:00";

    /** Requête d'une page à partir d'un curseur (filtre PostgREST "gte.<horodatage>"). */
    private interface PageFetcher<T> {
        retrofit2.Call<List<T>> fetch(String since);
    }

    /**
     * Récupère les changements du serveur, table par table, dans l'ordre des dépendances.
     * Chaque table a son curseur : le plus grand horodatage SERVEUR déjà reçu (jamais
     * l'horloge du téléphone). Les suppressions arrivent comme des lignes avec deleted_at.
     */
    private void pullRemoteChangesSynchronous() {
        String shop = sessionManager.getShopId();
        if (shop == null) {
            Log.w(TAG, "Pull annulé : aucune boutique dans la session.");
            return;
        }
        String shopFilter = "eq." + shop;

        pullTable(shop, "products",
                since -> supabaseService.pullProducts(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                Product::getUpdatedAt, syncDao::insertOrUpdateProduct);
        pullTable(shop, "clients",
                since -> supabaseService.pullClients(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                Client::getUpdatedAt, syncDao::upsertClient);
        pullTable(shop, "sales",
                since -> supabaseService.pullSales(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                SaleHeader::getUpdatedAt, syncDao::upsertSale);
        pullTable(shop, "sale_items",
                since -> supabaseService.pullSaleItems(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                SaleItem::getUpdatedAt, syncDao::upsertSaleItem);
        pullTable(shop, "sale_returns",
                since -> supabaseService.pullSaleReturns(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                SaleReturn::getUpdatedAt, syncDao::upsertSaleReturn);
        pullTable(shop, "quotes",
                since -> supabaseService.pullQuotes(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                Quote::getUpdatedAt, syncDao::upsertQuote);
        pullTable(shop, "quote_items",
                since -> supabaseService.pullQuoteItems(shopFilter, since, PULL_ORDER, PULL_PAGE_SIZE),
                QuoteItem::getUpdatedAt, syncDao::upsertQuoteItem);
        // audit_log n'a pas de updated_at (lignes jamais modifiées) : curseur sur created_at
        pullTable(shop, "audit_log",
                since -> supabaseService.pullProductAuditLog(shopFilter, since, "created_at.asc,id.asc", PULL_PAGE_SIZE),
                AuditLogEntry::getCreatedAt, e -> syncDao.upsertHistory(e.toProductHistory()));
    }

    /**
     * Pull paginé d'une table. Le curseur est enregistré après chaque page appliquée :
     * une synchro interrompue reprend là où elle s'était arrêtée. Le filtre est "gte"
     * (pas "gt") pour ne rien perdre entre deux pages au même horodatage ; les lignes
     * déjà reçues sont simplement réécrites.
     */
    private <T> void pullTable(String shop, String table, PageFetcher<T> fetcher,
                               java.util.function.Function<T, String> timestampOf,
                               java.util.function.Consumer<T> apply) {
        String cursor = getPullCursor(shop, table);
        try {
            while (true) {
                Response<List<T>> res = fetcher.fetch("gte." + cursor).execute();
                if (!res.isSuccessful() || res.body() == null) {
                    logFailure("pull " + table, res);
                    return;
                }
                List<T> rows = res.body();
                String max = cursor;
                for (T row : rows) {
                    apply.accept(row);
                    String ts = timestampOf.apply(row);
                    if (ts != null && compareTimestamps(ts, max) > 0) max = ts;
                }
                setPullCursor(shop, table, max);
                if (rows.size() < PULL_PAGE_SIZE) return;
                if (compareTimestamps(max, cursor) == 0) {
                    // Page pleine au même horodatage : on ne peut pas avancer sans boucler
                    Log.w(TAG, "Pull " + table + " : page pleine au même horodatage " + max);
                    return;
                }
                cursor = max;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur pull " + table + " : " + e.getMessage(), e);
        }
    }

    // --- Curseurs de pull (par boutique et par table) ---

    /** Oublie tous les curseurs : le prochain pull retélécharge tout (base locale recréée). */
    public static void resetPullCursors(Context context) {
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private String getPullCursor(String shop, String table) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        return prefs.getString("cursor_" + shop + "_" + table, EPOCH);
    }

    private void setPullCursor(String shop, String table, String value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        prefs.edit().putString("cursor_" + shop + "_" + table, value).apply();
    }

    /**
     * Compare deux horodatages PostgreSQL au format ISO ("2026-10-16T10:09:06.12+00:00").
     * PostgreSQL supprime les zéros finaux des microsecondes : la partie décimale est
     * complétée à 6 chiffres avant la comparaison (les deux sont en UTC).
     */
    static int compareTimestamps(String a, String b) {
        return normalizeTimestamp(a).compareTo(normalizeTimestamp(b));
    }

    private static String normalizeTimestamp(String ts) {
        int tPos = ts.indexOf('T');
        int dot = ts.indexOf('.', tPos);
        int zoneStart = ts.length();
        for (int i = (dot >= 0 ? dot + 1 : tPos + 1); i < ts.length(); i++) {
            char c = ts.charAt(i);
            if (c == '+' || c == '-' || c == 'Z') { zoneStart = i; break; }
        }
        String base = dot >= 0 ? ts.substring(0, dot) : ts.substring(0, zoneStart);
        StringBuilder frac = new StringBuilder(dot >= 0 ? ts.substring(dot + 1, zoneStart) : "");
        while (frac.length() < 6) frac.append('0');
        return base + "." + frac;
    }

    // ----------------------------------------------------------------------------------
    // OUTILS
    // ----------------------------------------------------------------------------------

    /** Journalise un échec HTTP avec le code et le message d'erreur renvoyé par le serveur. */
    private void logFailure(String what, Response<?> res) {
        Log.e(TAG, "Échec " + what + " (code " + res.code() + ") " + readError(res));
    }

    /** Corps de l'erreur renvoyée par le serveur (lisible une seule fois). */
    private static String readError(Response<?> res) {
        try {
            if (res.errorBody() != null) return res.errorBody().string();
        } catch (IOException ignored) { }
        return "";
    }
}