package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Product;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès local aux produits (ids UUID).
 * La quantité vendue n'est plus stockée : elle est recalculée depuis les ventes non supprimées.
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
        values.put("quantity", product.getQuantity());
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

    /** @return l'UUID du produit, ou null en cas d'échec. */
    public String insertProduct(Product product) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(product);
        values.put("id", product.getId());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        long result = db.insert(TABLE_PRODUCTS, null, values);
        db.close();
        return (result == -1) ? null : product.getId();
    }

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

    /** Insère ou remplace un produit reçu du serveur (PULL). */
    public String insertOrUpdateProductFromRemote(Product p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(p);
        values.put("id", p.getId());
        values.put(DatabaseHelper.COL_UPDATED_AT, p.getUpdatedAt());
        values.put(DatabaseHelper.COL_DELETED_AT, p.getDeletedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        long result = db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return (result != -1) ? p.getId() : null;
    }
}
