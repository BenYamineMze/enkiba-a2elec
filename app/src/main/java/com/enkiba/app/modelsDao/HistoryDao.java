package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal local des opérations sur les produits (table history, ids UUID).
 */
public class HistoryDao {

    private final DatabaseHelper dbHelper;
    private final SessionManager sessionManager;
    private static final String TABLE_HISTORY = DatabaseHelper.TABLE_HISTORY;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;
    private static final String TABLE_USERS = DatabaseHelper.TABLE_USERS;
    private static final String TAG = "HistoryDao";

    public HistoryDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    private ProductHistory buildHistoryFromCursor(Cursor cursor) {
        ProductHistory h = new ProductHistory();
        h.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        h.setShopId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        h.setProductId(cursor.getString(cursor.getColumnIndexOrThrow("product_id")));
        h.setUserId(cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
        h.setOperation(cursor.getString(cursor.getColumnIndexOrThrow("operation")));
        h.setOldValue(cursor.getString(cursor.getColumnIndexOrThrow("old_value")));
        h.setNewValue(cursor.getString(cursor.getColumnIndexOrThrow("new_value")));
        h.setTimestamp(cursor.getString(cursor.getColumnIndexOrThrow("timestamp")));
        h.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        h.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        return h;
    }

    /**
     * Valeurs d'une entrée d'historique, prêtes à insérer.
     * Exposé pour pouvoir écrire l'historique dans la transaction d'un autre DAO (vente).
     */
    public static ContentValues toValues(ProductHistory history, String shopId) {
        ContentValues values = new ContentValues();
        values.put("id", history.getId());
        values.put(DatabaseHelper.COL_SHOP_ID, history.getShopId() != null ? history.getShopId() : shopId);
        values.put("product_id", history.getProductId());
        values.put("user_id", history.getUserId());
        values.put("operation", history.getOperation());
        values.put("old_value", history.getOldValue());
        values.put("new_value", history.getNewValue());
        values.put("timestamp", history.getTimestamp() != null ? history.getTimestamp() : DatabaseUtils.getCurrentIsoTime());
        return values;
    }

    /** Insère une entrée d'historique locale (marquée 'pending'). */
    public boolean insertHistoryLocally(ProductHistory history) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(history, sessionManager.getShopId());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        long result = db.insert(TABLE_HISTORY, null, values);
        db.close();
        if (result == -1) Log.e(TAG, "Échec de l'insertion de l'historique " + history.getId());
        return result != -1;
    }

    public boolean insertHistory(String productId, String userId, String operation, String oldValue, String newValue) {
        ProductHistory history = new ProductHistory();
        history.setProductId(productId);
        history.setUserId(userId);
        history.setOperation(operation);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setTimestamp(DatabaseUtils.getCurrentIsoTime());
        return insertHistoryLocally(history);
    }

    /** Historique complet, du plus récent au plus ancien, avec nom du produit et de l'utilisateur. */
    public List<ProductHistory> getProductHistory() {
        List<ProductHistory> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT h.id, p.name AS product_name, h.operation, h.old_value, h.new_value, h.timestamp, u.username, h.user_id, h.product_id " +
                "FROM " + TABLE_HISTORY + " h " +
                "LEFT JOIN " + TABLE_PRODUCTS + " p ON h.product_id = p.id " +
                "LEFT JOIN " + TABLE_USERS + " u ON h.user_id = u.id " +
                "ORDER BY h.timestamp DESC";
        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                ProductHistory h = new ProductHistory();
                h.setId(cursor.getString(0));
                h.setProductName(cursor.getString(1));
                h.setOperation(cursor.getString(2));
                h.setOldValue(cursor.getString(3));
                h.setNewValue(cursor.getString(4));
                h.setTimestamp(cursor.getString(5));
                h.setUsername(cursor.getString(6));
                h.setUserId(cursor.getString(7));
                h.setProductId(cursor.getString(8));
                list.add(h);
            }
        }
        db.close();
        return list;
    }

    public void clearProductHistory() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
        db.close();
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<ProductHistory> getPendingHistoryForSync() {
        List<ProductHistory> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HISTORY
                + " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null)) {
            while (cursor.moveToNext()) pendingList.add(buildHistoryFromCursor(cursor));
        }
        db.close();
        return pendingList;
    }

    public void markHistoryAsSynced(String historyId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_HISTORY, values, "id = ?", new String[]{historyId});
        db.close();
    }

    /** Insère ou remplace une entrée reçue du serveur (PULL). */
    public void upsertHistoryFromRemote(ProductHistory remoteHistory) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(remoteHistory, remoteHistory.getShopId());
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteHistory.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
