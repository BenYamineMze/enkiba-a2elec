package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.SaleArchive;
import com.enkiba.app.models.SaleItemArchive;

import java.util.ArrayList;
import java.util.List;

public class SaleArchiveDao {

    private final DatabaseHelper dbHelper;
    private static final String TAG = "SaleArchiveDao";

    private static final String TABLE_SALE_ARCHIVE = DatabaseHelper.TABLE_SALE_ARCHIVE; // Utilisation des constantes publiques est encore plus propre
    private static final String TABLE_SALE_ITEMS_ARCHIVE = DatabaseHelper.TABLE_SALE_ITEMS_ARCHIVE;


    public SaleArchiveDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // --- RÉCUPÉRATION DES ARCHIVES EN ATTENTE (PUSH) ---

    public List<SaleArchive> getPendingSaleArchivesForSync() {
        List<SaleArchive> archives = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SALE_ARCHIVE,
                null,
                DatabaseHelper.COL_SYNC_STATUS + " = ?",
                new String[]{"pending"},
                null, null, null
        );

        try {
            if (cursor.moveToFirst()) {
                do {
                    SaleArchive archive = new SaleArchive();
                    archive.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    archive.setSaleDate(cursor.getString(cursor.getColumnIndexOrThrow("sale_date")));
                    archive.setUserId(cursor.getLong(cursor.getColumnIndexOrThrow("user_id")));
                    archive.setClientId(cursor.getLong(cursor.getColumnIndexOrThrow("client_id")));
                    archive.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
                    // 🚨 CORRECTION DE LA FAUTE DE FRAPPE : "paryment_method" -> "payment_method"
                    archive.setPaymentMethod(cursor.getString(cursor.getColumnIndexOrThrow("payment_method")));
                    archive.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
                    archives.add(archive);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur de récupération SaleArchives: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return archives;
    }

    public List<SaleItemArchive> getPendingSaleItemArchivesForSync() {
        List<SaleItemArchive> items = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SALE_ITEMS_ARCHIVE,
                null,
                DatabaseHelper.COL_SYNC_STATUS + " = ?",
                new String[]{"pending"},
                null, null, null
        );

        try {
            if (cursor.moveToFirst()) {
                do {
                    SaleItemArchive item = new SaleItemArchive();
                    item.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    item.setSaleId(cursor.getLong(cursor.getColumnIndexOrThrow("sale_id")));
                    item.setProductName(cursor.getString(cursor.getColumnIndexOrThrow("product_name")));
                    item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
                    item.setUnitPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
                    item.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
                    item.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
                    items.add(item);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur de récupération SaleItemArchives: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return items;
    }

    // --- MARQUAGE COMME SYNCHRONISÉ (POST-PUSH) ---

    public void markSaleArchiveAsSynced(long localId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        db.update(
                TABLE_SALE_ARCHIVE,
                values,
                "id = ?",
                new String[]{String.valueOf(localId)}
        );
        db.close();
    }

    public void markSaleItemArchiveAsSynced(long localId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        db.update(
                TABLE_SALE_ITEMS_ARCHIVE,
                values,
                "id = ?",
                new String[]{String.valueOf(localId)}
        );
        db.close();
    }
}