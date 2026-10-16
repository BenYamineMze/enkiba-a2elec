package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.StockMovement;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès local aux produits (ids UUID).
 * La quantité vendue n'est plus stockée : elle est recalculée depuis les ventes non supprimées.
 *
 * Le stock (quantity) n'est jamais envoyé au serveur : il y est calculé à partir des
 * mouvements de stock. Localement, il est modifié uniquement par :
 * - createProduct / applyStockMovement (admin : un mouvement local 'pending' est créé),
 * - les ventes et les retours (le serveur génère lui-même les mouvements correspondants).
 */
public class ProductDao {

    private final DatabaseHelper dbHelper;
    private final SessionManager sessionManager;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;

    // Produits actifs (suppression logique)
    private static final String ACTIVE = "p." + DatabaseHelper.COL_DELETED_AT + " IS NULL";

    // Produit + quantité vendue (lignes de ventes non supprimées), exposée sous le nom sold_quantity
    private static final String SELECT_WITH_SOLD =
            "SELECT p.*, COALESCE((SELECT SUM(si.quantity) FROM " + DatabaseHelper.TABLE_SALE_ITEMS + " si " +
            "JOIN " + DatabaseHelper.TABLE_SALES + " s ON s.id = si.sale_id " +
            "WHERE si.product_id = p.id AND s." + DatabaseHelper.COL_DELETED_AT + " IS NULL), 0) AS sold_quantity " +
            "FROM " + TABLE_PRODUCTS + " p ";

    public ProductDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    private Product buildProductFromCursor(Cursor cursor) {
        Product p = new Product();
        p.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        p.setShopId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        p.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        p.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
        p.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
        p.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("price")));
        p.setDescription(cursor.getString(cursor.getColumnIndexOrThrow("description")));
        p.setQr_code(cursor.getString(cursor.getColumnIndexOrThrow("qr_code")));
        p.setInitial_stock(cursor.getInt(cursor.getColumnIndexOrThrow("initial_stock")));
        p.setLowStockThreshold(cursor.getInt(cursor.getColumnIndexOrThrow("low_stock_threshold")));
        p.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        p.setDeletedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DELETED_AT)));
        p.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        int soldIndex = cursor.getColumnIndex("sold_quantity");
        if (soldIndex != -1) p.setSoldQuantity(cursor.getInt(soldIndex));
        return p;
    }

    private ContentValues toValues(Product product) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SHOP_ID,
                product.getShopId() != null ? product.getShopId() : sessionManager.getShopId());
        values.put("name", product.getName());
        values.put("category", product.getCategory());
        values.put("price", product.getPrice());
        values.put("description", product.getDescription());
        values.put("qr_code", product.getQr_code());
        values.put("initial_stock", product.getInitial_stock());
        values.put("low_stock_threshold", product.getLowStockThreshold());
        return values;
    }

    private List<Product> queryProducts(String where, String orderBy, String[] args) {
        List<Product> products = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = SELECT_WITH_SOLD + "WHERE " + where + (orderBy != null ? " ORDER BY " + orderBy : "");
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) products.add(buildProductFromCursor(cursor));
        }
        db.close();
        return products;
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
        String shopId = product.getShopId() != null ? product.getShopId() : sessionManager.getShopId();
        product.setShopId(shopId);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = toValues(product);
            values.put("id", product.getId());
            values.put("quantity", initialQuantity);
            values.put(DatabaseHelper.COL_UPDATED_AT, now);
            values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
            if (db.insert(TABLE_PRODUCTS, null, values) == -1) return null;

            if (initialQuantity != 0) {
                StockMovement m = new StockMovement(product.getId(), initialQuantity, StockMovement.REASON_INITIAL, userId);
                m.setShopId(shopId);
                if (db.insert(DatabaseHelper.TABLE_STOCK_MOVEMENTS, null, StockMovementDao.toValues(m, now)) == -1) return null;
            }
            db.setTransactionSuccessful();
            product.setQuantity(initialQuantity);
            return product.getId();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** Met à jour les informations du produit (jamais le stock : voir applyStockMovement). */
    public boolean updateProduct(Product product) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // initial_stock est mis à jour aussi : il sert de base à la jauge de stock
        ContentValues values = toValues(product);
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{product.getId()});
        db.close();
        return rows > 0;
    }

    /**
     * Correction de stock par un admin : crée un mouvement local en attente d'envoi
     * et applique le delta au stock local, en une transaction.
     */
    public boolean applyStockMovement(String productId, int delta, String reason, String userId) {
        if (delta == 0) return true;
        String now = DatabaseUtils.getCurrentIsoTime();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            StockMovement m = new StockMovement(productId, delta, reason, userId);
            m.setShopId(sessionManager.getShopId());
            if (db.insert(DatabaseHelper.TABLE_STOCK_MOVEMENTS, null, StockMovementDao.toValues(m, now)) == -1) return false;
            db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET quantity = quantity + ? WHERE id = ?",
                    new Object[]{delta, productId});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * Applique au stock local une variation dont le serveur génère lui-même le mouvement
     * (vente, retour). Le produit n'est pas marqué 'pending'.
     */
    public void addLocalQuantity(String productId, int delta) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET quantity = quantity + ? WHERE id = ?",
                new Object[]{delta, productId});
        db.close();
    }

    /** Suppression logique : deleted_at est renseigné et sera envoyé au serveur. */
    public boolean markProductForDeletion(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = DatabaseUtils.getCurrentIsoTime();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_DELETED_AT, now);
        values.put(DatabaseHelper.COL_UPDATED_AT, now);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{id});
        db.close();
        return rows > 0;
    }

    public void deleteProductPhysically(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TABLE_PRODUCTS, "id = ?", new String[]{id});
        db.close();
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Produit par id, même supprimé (utile pour l'historique et les ventes passées). */
    public Product getProductById(String id) {
        List<Product> list = queryProducts("p.id = ?", null, new String[]{id});
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Product> getAllProducts() {
        return queryProducts(ACTIVE, null, null);
    }

    public List<Product> getAllProductsList() {
        return queryProducts(ACTIVE, "p.name ASC", null);
    }

    /** Curseur des produits actifs (avec la colonne calculée sold_quantity). À fermer par l'appelant. */
    public Cursor getAllProductsCursor() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery(SELECT_WITH_SOLD + "WHERE " + ACTIVE, null);
    }

    public Product getProductByQr(String qrCode) {
        List<Product> list = queryProducts("p.qr_code = ? AND " + ACTIVE, null, new String[]{qrCode});
        return list.isEmpty() ? null : list.get(0);
    }

    public double getTotalStockValue() {
        double total = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT SUM(price * quantity) FROM " + TABLE_PRODUCTS + " p WHERE " + ACTIVE, null)) {
            if (cursor.moveToFirst()) total = cursor.getDouble(0);
        }
        db.close();
        return total;
    }

    public int getLowStockCount() {
        int count = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRODUCTS
                + " p WHERE quantity <= low_stock_threshold AND " + ACTIVE, null)) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
        }
        db.close();
        return count;
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    /** Produits modifiés localement, y compris les suppressions logiques (deleted_at renseigné). */
    public List<Product> getPendingProductsForSync() {
        return queryProducts("p." + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null, null);
    }

    public void markProductAsSynced(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{id});
        db.close();
    }

    /**
     * Écart entre le stock local et le stock serveur dû aux opérations locales
     * pas encore envoyées : mouvements en attente, ventes en attente.
     */
    private int pendingLocalDelta(SQLiteDatabase db, String productId) {
        int delta = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(delta), 0) FROM " + DatabaseHelper.TABLE_STOCK_MOVEMENTS
                + " WHERE product_id = ? AND " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", new String[]{productId})) {
            if (c.moveToFirst()) delta += c.getInt(0);
        }
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(quantity), 0) FROM " + DatabaseHelper.TABLE_SALE_ITEMS
                + " WHERE product_id = ? AND " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", new String[]{productId})) {
            if (c.moveToFirst()) delta -= c.getInt(0);
        }
        return delta;
    }

    /**
     * Insère ou remplace un produit reçu du serveur (PULL).
     * Le stock local = stock serveur + opérations locales pas encore envoyées.
     */
    public String insertOrUpdateProductFromRemote(Product p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(p);
        values.put("id", p.getId());
        values.put("quantity", p.getQuantity() + pendingLocalDelta(db, p.getId()));
        values.put(DatabaseHelper.COL_UPDATED_AT, p.getUpdatedAt());
        values.put(DatabaseHelper.COL_DELETED_AT, p.getDeletedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        long result = db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return (result != -1) ? p.getId() : null;
    }
}
