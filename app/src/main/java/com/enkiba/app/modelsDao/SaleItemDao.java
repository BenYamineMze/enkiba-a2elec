package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.SaleItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Lignes de vente côté synchronisation (les écritures locales passent par SaleDao.recordSale).
 */
public class SaleItemDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_SALE_ITEMS = DatabaseHelper.TABLE_SALE_ITEMS;

    public SaleItemDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    private SaleItem buildSaleItemFromCursor(Cursor cursor) {
        SaleItem item = new SaleItem();
        item.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        item.setShopId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        item.setSaleId(cursor.getString(cursor.getColumnIndexOrThrow("sale_id")));
        item.setProductId(cursor.getString(cursor.getColumnIndexOrThrow("product_id")));
        item.setProductName(cursor.getString(cursor.getColumnIndexOrThrow("product_name")));
        item.setListPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("list_price")));
        item.setUnitPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
        item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
        item.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
        item.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        item.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        return item;
    }

    public List<SaleItem> getPendingSaleItemsForSync() {
        List<SaleItem> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SALE_ITEMS
                + " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null)) {
            while (cursor.moveToNext()) pendingList.add(buildSaleItemFromCursor(cursor));
        }
        db.close();
        return pendingList;
    }

    public void markSaleItemAsSynced(String itemId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_SALE_ITEMS, values, "id = ?", new String[]{itemId});
        db.close();
    }

    /** Insère ou remplace une ligne de vente reçue du serveur (PULL). */
    public void upsertSaleItemFromRemote(SaleItem remoteItem) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", remoteItem.getId());
        values.put(DatabaseHelper.COL_SHOP_ID, remoteItem.getShopId());
        values.put("sale_id", remoteItem.getSaleId());
        values.put("product_id", remoteItem.getProductId());
        values.put("product_name", remoteItem.getProductName() != null ? remoteItem.getProductName() : "");
        values.put("quantity", remoteItem.getQuantity());
        values.put("list_price", remoteItem.getListPrice());
        values.put("unit_price", remoteItem.getUnitPrice());
        values.put("total_price", remoteItem.getTotalPrice());
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteItem.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE_SALE_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
