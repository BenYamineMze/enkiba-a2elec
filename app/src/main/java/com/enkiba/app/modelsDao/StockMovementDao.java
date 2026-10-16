package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.StockMovement;

import java.util.ArrayList;
import java.util.List;

/**
 * Mouvements de stock saisis localement, en attente d'envoi au serveur.
 * Les écritures se font dans les transactions de ProductDao (voir toValues).
 */
public class StockMovementDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE = DatabaseHelper.TABLE_STOCK_MOVEMENTS;

    public StockMovementDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    /** Valeurs d'un nouveau mouvement local (marqué 'pending'). */
    static ContentValues toValues(StockMovement m, String now) {
        ContentValues v = new ContentValues();
        v.put("id", m.getId());
        v.put(DatabaseHelper.COL_SHOP_ID, m.getShopId());
        v.put("product_id", m.getProductId());
        v.put("delta", m.getDelta());
        v.put("reason", m.getReason());
        v.put("created_by", m.getCreatedBy());
        v.put("created_at", now);
        v.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        return v;
    }

    public List<StockMovement> getPendingMovementsForSync() {
        List<StockMovement> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // Ordre de création : le mouvement 'initial' part avant les corrections
        try (Cursor c = db.rawQuery("SELECT * FROM " + TABLE + " WHERE "
                + DatabaseHelper.COL_SYNC_STATUS + " = 'pending' ORDER BY created_at", null)) {
            while (c.moveToNext()) {
                StockMovement m = new StockMovement();
                m.setId(c.getString(c.getColumnIndexOrThrow("id")));
                m.setShopId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
                m.setProductId(c.getString(c.getColumnIndexOrThrow("product_id")));
                m.setDelta(c.getInt(c.getColumnIndexOrThrow("delta")));
                m.setReason(c.getString(c.getColumnIndexOrThrow("reason")));
                m.setCreatedBy(c.getString(c.getColumnIndexOrThrow("created_by")));
                m.setCreatedAt(c.getString(c.getColumnIndexOrThrow("created_at")));
                m.setSyncStatus(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
                list.add(m);
            }
        }
        db.close();
        return list;
    }

    public void markMovementAsSynced(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE, values, "id = ?", new String[]{id});
        db.close();
    }
}
