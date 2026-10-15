package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class HistoryDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_HISTORY = DatabaseHelper.TABLE_HISTORY;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;
    private static final String TABLE_USERS = DatabaseHelper.TABLE_USERS;
    private static final String TAG = "HistoryDao";

    public HistoryDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    private ProductHistory buildHistoryFromCursor(Cursor cursor) {
        ProductHistory h = new ProductHistory();
        h.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
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

    public long insertHistoryLocally(ProductHistory history) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        long historyId = (history.getId() <= 0) ? DatabaseUtils.generateTempId() : history.getId();

        values.put("id", historyId);
        values.put("product_id", history.getProductId());
        values.put("user_id", history.getUserId());
        values.put("operation", history.getOperation());
        values.put("old_value", history.getOldValue());
        values.put("new_value", history.getNewValue());
        values.put("timestamp", history.getTimestamp());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        long result = db.insert(TABLE_HISTORY, null, values);
        db.close();
        return (result == -1) ? -1 : historyId;
    }

    public long insertHistory(String productId, String userId, String operation, String oldValue, String newValue) {
        ProductHistory history = new ProductHistory();
        history.setProductId(productId);
        history.setUserId(userId);
        history.setOperation(operation);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setTimestamp(DatabaseUtils.getCurrentIsoTime());
        return insertHistoryLocally(history);
    }

    public List<ProductHistory> getProductHistory() {
        List<ProductHistory> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 🚨 CORRECTION CRITIQUE :
        // J'ai ajouté 'h.product_id' à la fin de la requête (index 8)
        // Sans cela, l'ID du produit est NULL dans l'objet, ce qui fait planter l'hydratation
        String query = "SELECT h.id, p.name AS product_name, h.operation, h.old_value, h.new_value, h.timestamp, u.username, h.user_id, h.product_id " +
                "FROM " + TABLE_HISTORY + " h " +
                "LEFT JOIN " + TABLE_PRODUCTS + " p ON h.product_id = p.id " +
                "LEFT JOIN " + TABLE_USERS + " u ON h.user_id = u.id " +
                "ORDER BY h.timestamp DESC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                ProductHistory h = new ProductHistory();
                h.setId(cursor.getLong(0));
                h.setProductName(cursor.getString(1));
                h.setOperation(cursor.getString(2));
                h.setOldValue(cursor.getString(3));
                h.setNewValue(cursor.getString(4));
                h.setTimestamp(cursor.getString(5));
                h.setUsername(cursor.getString(6));
                h.setUserId(cursor.getString(7));

                // ✅ RECUPERATION DE L'ID PRODUIT (Index 8)
                h.setProductId(cursor.getString(8));

                list.add(h);
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return list;
    }

    public void clearProductHistory() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
        db.close();
    }

    public List<ProductHistory> getPendingHistoryForSync() {
        List<ProductHistory> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HISTORY + " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null);
        if (cursor != null && cursor.moveToFirst()) {
            do { pendingList.add(buildHistoryFromCursor(cursor)); } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return pendingList;
    }

    public void markHistoryAsSynced(long historyId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_HISTORY, values, "id = ?", new String[]{String.valueOf(historyId)});
        db.close();
    }

    public long insertOrUpdateHistoryFromRemote(ProductHistory remoteHistory) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", remoteHistory.getId());
        values.put("product_id", remoteHistory.getProductId());
        values.put("user_id", remoteHistory.getUserId());
        values.put("operation", remoteHistory.getOperation());
        values.put("old_value", remoteHistory.getOldValue());
        values.put("new_value", remoteHistory.getNewValue());
        values.put("timestamp", remoteHistory.getTimestamp());
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteHistory.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        int rowsUpdated = db.update(TABLE_HISTORY, values, "id = ?", new String[]{String.valueOf(remoteHistory.getId())});
        long result = (rowsUpdated > 0) ? remoteHistory.getId() : db.insert(TABLE_HISTORY, null, values);
        db.close();
        return result;
    }

    public void updateLocalId(long oldId, long newId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", newId);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_HISTORY, values, "id = ?", new String[]{String.valueOf(oldId)});
        db.close();
    }
}