package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.SaleReturn;

import java.util.ArrayList;
import java.util.List;

/**
 * Retours côté synchronisation (les retours locaux sont créés par SaleDao.processReturn).
 */
public class SaleReturnDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE = DatabaseHelper.TABLE_SALE_RETURNS;

    public SaleReturnDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    /** Valeurs d'un retour (sans le statut de synchro). */
    static ContentValues toValues(SaleReturn r) {
        ContentValues v = new ContentValues();
        v.put("id", r.getId());
        v.put(DatabaseHelper.COL_SHOP_ID, r.getShopId());
        v.put("sale_id", r.getSaleId());
        v.put("sale_item_id", r.getSaleItemId());
        v.put("product_id", r.getProductId());
        v.put("quantity", r.getQuantity());
        v.put("refund_amount", r.getRefundAmount());
        v.put("created_by", r.getCreatedBy());
        v.put("created_at", r.getCreatedAt());
        v.put(DatabaseHelper.COL_UPDATED_AT, r.getUpdatedAt());
        return v;
    }

    public List<SaleReturn> getPendingReturnsForSync() {
        List<SaleReturn> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM " + TABLE + " WHERE "
                + DatabaseHelper.COL_SYNC_STATUS + " = 'pending' ORDER BY created_at", null)) {
            while (c.moveToNext()) {
                SaleReturn r = new SaleReturn();
                r.setId(c.getString(c.getColumnIndexOrThrow("id")));
                r.setShopId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
                r.setSaleId(c.getString(c.getColumnIndexOrThrow("sale_id")));
                r.setSaleItemId(c.getString(c.getColumnIndexOrThrow("sale_item_id")));
                r.setProductId(c.getString(c.getColumnIndexOrThrow("product_id")));
                r.setQuantity(c.getInt(c.getColumnIndexOrThrow("quantity")));
                r.setRefundAmount(c.getDouble(c.getColumnIndexOrThrow("refund_amount")));
                r.setCreatedBy(c.getString(c.getColumnIndexOrThrow("created_by")));
                r.setCreatedAt(c.getString(c.getColumnIndexOrThrow("created_at")));
                r.setSyncStatus(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
                list.add(r);
            }
        }
        db.close();
        return list;
    }

    public void markReturnAsSynced(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE, values, "id = ?", new String[]{id});
        db.close();
    }

    /** Insère ou remplace un retour reçu du serveur (PULL). */
    public void upsertReturnFromRemote(SaleReturn r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(r);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
