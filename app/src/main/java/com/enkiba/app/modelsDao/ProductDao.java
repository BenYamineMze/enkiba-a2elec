package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Product;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;
    private static final String TAG = "ProductDao";

    public ProductDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    private Product buildProductFromCursor(Cursor cursor) {
        Product p = new Product();
        // [MODIF UUID] Lecture de l'ID en tant que String
        p.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        p.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        p.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
        p.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
        p.setSoldQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("sold_quantity")));
        p.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("price")));
        p.setDescription(cursor.getString(cursor.getColumnIndexOrThrow("description")));
        p.setQr_code(cursor.getString(cursor.getColumnIndexOrThrow("qr_code")));
        p.setInitial_stock(cursor.getInt(cursor.getColumnIndexOrThrow("initial_stock")));
        p.setLowStockThreshold(cursor.getInt(cursor.getColumnIndexOrThrow("low_stock_threshold")));
        p.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        p.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        return p;
    }

    // [MODIF UUID] Retourne l'UUID (String)
    public String insertProduct(Product product) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("id", product.getId());
        values.put("name", product.getName());
        values.put("category", product.getCategory());
        values.put("quantity", product.getQuantity());
        values.put("sold_quantity", product.getSoldQuantity());
        values.put("price", product.getPrice());
        values.put("description", product.getDescription());
        values.put("qr_code", product.getQr_code());
        values.put("initial_stock", product.getInitial_stock());
        values.put("low_stock_threshold", product.getLowStockThreshold());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        long result = db.insert(TABLE_PRODUCTS, null, values);
        db.close();

        return (result == -1) ? null : product.getId();
    }

    public boolean updateProduct(Product product) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", product.getName());
        values.put("category", product.getCategory());
        values.put("quantity", product.getQuantity());

        // 🚨 CORRECTION : Mise à jour du stock initial
        // Indispensable pour réinitialiser la jauge (barre de progression) à 100%
        values.put("initial_stock", product.getInitial_stock());

        values.put("price", product.getPrice());
        values.put("description", product.getDescription());
        values.put("qr_code", product.getQr_code());
        values.put("low_stock_threshold", product.getLowStockThreshold());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        int rows = db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{product.getId()});
        db.close();
        return rows > 0;
    }

    public boolean markProductForDeletion(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "deleted"); // Marqué pour suppression

        int rows = db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{id});
        db.close();
        return rows > 0;
    }

    public void deleteProductPhysically(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TABLE_PRODUCTS, "id = ?", new String[]{id});
        db.close();
    }

    public Product getProductById(String id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Product product = null;
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCTS + " WHERE id = ?", new String[]{id});
        if (cursor.moveToFirst()) {
            product = buildProductFromCursor(cursor);
        }
        cursor.close();
        db.close();
        return product;
    }

    // 🚨 CORRECTION MAJEURE : Ne jamais afficher les produits marqués 'deleted'
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // AJOUT DU FILTRE WHERE sync_status != 'deleted'
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCTS + " WHERE sync_status != 'deleted'", null);
        if (cursor.moveToFirst()) {
            do { products.add(buildProductFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return products;
    }

    // 🚨 CORRECTION MAJEURE : Idem pour la liste triée
    public List<Product> getAllProductsList() {
        List<Product> products = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // AJOUT DU FILTRE
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCTS + " WHERE sync_status != 'deleted' ORDER BY name ASC", null);
        if (cursor != null && cursor.moveToFirst()) {
            do { products.add(buildProductFromCursor(cursor)); } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return products;
    }

    public Cursor getAllProductsCursor() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_PRODUCTS + " WHERE sync_status != 'deleted'", null);
    }

    public Product getProductByQr(String qrCode) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Product p = null;
        // On vérifie aussi que le produit scanné n'est pas supprimé
        Cursor c = db.query(TABLE_PRODUCTS, null, "qr_code = ? AND sync_status != 'deleted'", new String[]{ qrCode }, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) { p = buildProductFromCursor(c); }
            c.close();
        }
        db.close();
        return p;
    }

    public double getTotalStockValue() {
        double total = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // On ne compte pas la valeur des produits supprimés
        Cursor cursor = db.rawQuery("SELECT SUM(price * quantity) FROM " + TABLE_PRODUCTS + " WHERE sync_status != 'deleted'", null);
        if (cursor.moveToFirst()) { total = cursor.getDouble(0); }
        cursor.close();
        db.close();
        return total;
    }

    public int getLowStockCount() {
        int count = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRODUCTS + " WHERE quantity <= low_stock_threshold AND sync_status != 'deleted'", null);
        if (cursor.moveToFirst()) { count = cursor.getInt(0); }
        cursor.close();
        db.close();
        return count;
    }

    // Cette méthode doit TOUT renvoyer pour que le SyncManager puisse trouver les éléments à supprimer sur le serveur
    public List<Product> getPendingProductsForSync() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCTS + " WHERE sync_status IN ('pending', 'deleted')", null);
        if (cursor.moveToFirst()) {
            do { list.add(buildProductFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    public void markProductAsSynced(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{id});
        db.close();
    }

    public String insertOrUpdateProductFromRemote(Product p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", p.getId());
        values.put("name", p.getName());
        values.put("category", p.getCategory());
        values.put("quantity", p.getQuantity());
        values.put("sold_quantity", p.getSoldQuantity());
        values.put("price", p.getPrice());
        values.put("description", p.getDescription());
        values.put("qr_code", p.getQr_code());
        values.put("initial_stock", p.getInitial_stock());
        values.put("low_stock_threshold", p.getLowStockThreshold());
        values.put(DatabaseHelper.COL_UPDATED_AT, p.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        int rows = db.update(TABLE_PRODUCTS, values, "id = ?", new String[]{p.getId()});

        if (rows > 0) {
            db.close();
            return p.getId();
        } else {
            long result = db.insert(TABLE_PRODUCTS, null, values);
            db.close();
            return (result != -1) ? p.getId() : null;
        }
    }

    // Méthodes obsolètes conservées pour compatibilité
    public void updateLocalId(long oldTempId, long newServerId) {}
    public void resetStuckProducts() {}
}