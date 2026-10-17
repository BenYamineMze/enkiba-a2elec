package com.enkiba.app.modelsDao;

import android.content.Context;
import android.util.Log;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.ProductRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.ProductWithSold;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.StockMovement;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès local aux produits (ids UUID), au-dessus de Room.
 * La quantité vendue n'est plus stockée : elle est recalculée depuis les ventes non supprimées.
 *
 * Le stock (quantity) n'est jamais envoyé au serveur : il y est calculé à partir des
 * mouvements de stock. Localement, il est modifié uniquement par :
 * - createProduct / applyStockMovement (admin : un mouvement local 'pending' est créé),
 * - les ventes et les retours (le serveur génère lui-même les mouvements correspondants).
 */
public class ProductDao {

    private static final String TAG = "ProductDao";

    private final AppDatabase db;
    private final ProductRoomDao dao;
    private final SessionManager sessionManager;

    public ProductDao(Context context) {
        this.db = AppDatabase.get(context);
        this.dao = db.productDao();
        this.sessionManager = new SessionManager(context);
    }

    private static List<Product> toModels(List<ProductWithSold> rows) {
        List<Product> list = new ArrayList<>(rows.size());
        for (ProductWithSold r : rows) list.add(Mappers.toModel(r));
        return list;
    }

    // ----------------------------------------------------------------------
    // --- ÉCRITURE LOCALE (marquée 'pending') ---
    // ----------------------------------------------------------------------

    /**
     * Crée un produit avec son stock initial, en une transaction :
     * le produit et, si le stock est non nul, un mouvement 'initial' en attente d'envoi.
     *
     * @return l'UUID du produit, ou null en cas d'échec (rien n'est écrit).
     */
    public String createProduct(Product product, int initialQuantity, String userId) {
        String now = DatabaseUtils.getCurrentIsoTime();
        if (product.getShopId() == null) product.setShopId(sessionManager.getShopId());
        try {
            db.runInTransaction(() -> {
                dao.insert(Mappers.toEntity(product, initialQuantity, now, EntitiesKt.SYNC_PENDING));
                if (initialQuantity != 0) {
                    StockMovement m = new StockMovement(product.getId(), initialQuantity, StockMovement.REASON_INITIAL, userId);
                    m.setShopId(product.getShopId());
                    db.stockDao().insertMovement(Mappers.toEntity(m, now, EntitiesKt.SYNC_PENDING));
                }
            });
            product.setQuantity(initialQuantity);
            return product.getId();
        } catch (Exception e) {
            Log.e(TAG, "createProduct : " + e.getMessage(), e);
            return null;
        }
    }

    /** Met à jour les informations du produit (jamais le stock : voir applyStockMovement). */
    public boolean updateProduct(Product product) {
        // initial_stock est mis à jour aussi : il sert de base à la jauge de stock
        String shopId = product.getShopId() != null ? product.getShopId() : sessionManager.getShopId();
        return dao.updateDetails(product.getId(), shopId, product.getName(), product.getCategory(),
                product.getPrice(), product.getDescription(), product.getQr_code(), product.getInitial_stock(),
                product.getLowStockThreshold(), DatabaseUtils.getCurrentIsoTime()) > 0;
    }

    /**
     * Correction de stock par un admin : crée un mouvement local en attente d'envoi
     * et applique le delta au stock local, en une transaction.
     */
    public boolean applyStockMovement(String productId, int delta, String reason, String userId) {
        if (delta == 0) return true;
        String now = DatabaseUtils.getCurrentIsoTime();
        try {
            db.runInTransaction(() -> {
                StockMovement m = new StockMovement(productId, delta, reason, userId);
                m.setShopId(sessionManager.getShopId());
                db.stockDao().insertMovement(Mappers.toEntity(m, now, EntitiesKt.SYNC_PENDING));
                dao.addQuantity(productId, delta);
            });
            return true;
        } catch (Exception e) {
            Log.e(TAG, "applyStockMovement : " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Applique au stock local une variation dont le serveur génère lui-même le mouvement
     * (vente, retour). Le produit n'est pas marqué 'pending'.
     */
    public void addLocalQuantity(String productId, int delta) {
        dao.addQuantity(productId, delta);
    }

    /** Suppression logique : deleted_at est renseigné et sera envoyé au serveur. */
    public boolean markProductForDeletion(String id) {
        return dao.markDeleted(id, DatabaseUtils.getCurrentIsoTime()) > 0;
    }

    public void deleteProductPhysically(String id) {
        dao.deletePhysically(id);
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Produit par id, même supprimé (utile pour l'historique et les ventes passées). */
    public Product getProductById(String id) {
        ProductWithSold p = dao.getById(id);
        return p != null ? Mappers.toModel(p) : null;
    }

    public List<Product> getAllProducts() {
        return toModels(dao.getActive());
    }

    public List<Product> getAllProductsList() {
        return toModels(dao.getActiveByName());
    }

    public Product getProductByQr(String qrCode) {
        ProductWithSold p = dao.getByQr(qrCode);
        return p != null ? Mappers.toModel(p) : null;
    }

    public double getTotalStockValue() {
        return dao.totalStockValue();
    }

    public int getLowStockCount() {
        return dao.lowStockCount();
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    /** Produits modifiés localement, y compris les suppressions logiques (deleted_at renseigné). */
    public List<Product> getPendingProductsForSync() {
        return toModels(dao.getPending());
    }

    public void markProductAsSynced(String id) {
        dao.markSynced(id);
    }

    /**
     * Insère ou remplace un produit reçu du serveur (PULL), sauf modification locale
     * non envoyée. Le stock local = stock serveur + opérations locales pas encore envoyées.
     */
    public String insertOrUpdateProductFromRemote(Product p) {
        db.runInTransaction(() -> {
            if (EntitiesKt.SYNC_PENDING.equals(dao.getSyncStatus(p.getId()))) return;
            int quantity = p.getQuantity() + dao.pendingLocalDelta(p.getId());
            dao.upsert(Mappers.toEntity(p, quantity, p.getUpdatedAt(), EntitiesKt.SYNC_SYNCED));
        });
        return p.getId();
    }
}
