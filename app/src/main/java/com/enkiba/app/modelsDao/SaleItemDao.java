package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class SaleItemDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_SALE_ITEMS = DatabaseHelper.TABLE_SALE_ITEMS; // Utilisation de la constante du Helper
    private static final String TAG = "SaleItemDao";

    public SaleItemDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // ----------------------------------------------------------------------
    // --- MÉTHODE DE SUPPORT (LECTURE) ---
    // ----------------------------------------------------------------------

    /**
     * Construit un objet SaleItem à partir de la ligne actuelle du Cursor.
     */
    private SaleItem buildSaleItemFromCursor(Cursor cursor) {
        SaleItem item = new SaleItem();

        // L'ID principal est toujours long
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));

        // [CORRECTION] product_id est maintenant un String (UUID)
        int productIdIndex = cursor.getColumnIndexOrThrow("product_id");
        if (!cursor.isNull(productIdIndex)) {
            // C'était getLong(), c'est maintenant getString()
            item.setProductId(cursor.getString(productIdIndex));
        }

        // 🚨 CORRECTION CRITIQUE : Lecture du sale_id manquant 🚨
        int saleIdIndex = cursor.getColumnIndexOrThrow("sale_id");
        if (!cursor.isNull(saleIdIndex)) {
            item.setSaleId(cursor.getLong(saleIdIndex));
        }

        // Champs de données
        item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
        item.setUnitPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
        item.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));

        // Champs de Synchronisation
        item.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        item.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));

        return item;
    }


    // ----------------------------------------------------------------------
    // --- OPÉRATIONS D'ÉCRITURE LOCALE (avec marquage 'pending') ---
    // ----------------------------------------------------------------------

    /**
     * Insère un détail de vente. Doit être appelé dans la transaction de SaleDao
     * car il dépend de l'ID temporaire de la vente (saleId).
     *
     * @param db Instance de la base de données (dans une transaction active).
     */
    public long insertSaleItemLocally(SQLiteDatabase db, SaleItem item) {
        ContentValues values = new ContentValues();

        long itemId;
        // 1. Détermination de l'ID (temporaire ou connu)
        if (item.getId() == null || item.getId() <= 0) {
            itemId = DatabaseUtils.generateTempId(); // ID négatif
        } else {
            itemId = item.getId();
        }

        values.put("id", itemId);

        // 🚨 CORRECTION CRITIQUE : AJOUT DU SALE_ID 🚨
        values.put("sale_id", item.getSaleId());

        // [CORRECTION] product_id est un String (UUID), values.put gère String automatiquement
        values.put("product_id", item.getProductId());
        values.put("quantity", item.getQuantity());
        values.put("unit_price", item.getUnitPrice());
        values.put("total_price", item.getTotalPrice());

        // 2. Champs de Synchronisation
        String currentTimestamp = DatabaseUtils.getCurrentIsoTime();
        values.put(DatabaseHelper.COL_UPDATED_AT, currentTimestamp);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        long result = db.insert(TABLE_SALE_ITEMS, null, values);

        if (result == -1) {
            Log.e(TAG, "Erreur lors de l'insertion du détail de vente.");
            // Pas de db.close() car on est dans la transaction de l'appelant
            return -1;
        }

        return itemId;
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION (PUSH/PULL) ---
    // ----------------------------------------------------------------------

    /**
     * Récupère tous les détails de vente marqués comme 'pending' ou 'deleted'.
     */
    public List<SaleItem> getPendingSaleItemsForSync() {
        List<SaleItem> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Les détails de vente n'ont pas de Soft Delete ('deleted') s'ils font partie
        // d'une vente active. Ils sont supprimés avec la vente.
        // On ne cherche que les 'pending' créés localement.
        String query = "SELECT * FROM " + TABLE_SALE_ITEMS +
                " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                pendingList.add(buildSaleItemFromCursor(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return pendingList;
    }

    /**
     * Marque un détail de vente comme synchronisé.
     */
    public void markSaleItemAsSynced(long itemId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        db.update(
                TABLE_SALE_ITEMS,
                values,
                "id = ?",
                new String[]{String.valueOf(itemId)}
        );
        db.close();
    }

    /**
     * Insère ou met à jour un détail de vente provenant du serveur (PULL).
     */
    public long insertOrUpdateSaleItemFromRemote(SaleItem remoteItem) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("id", remoteItem.getId());
        values.put("sale_id", remoteItem.getSaleId()); // 🚨 AJOUT DE LA FK sale_id
        values.put("product_id", remoteItem.getProductId()); // [CORRECTION] String UUID
        values.put("quantity", remoteItem.getQuantity());


        // 🚨 CORRECTION CRITIQUE : Il manquait l'enregistrement de 'initial_quantity' !!
        // Cela provoquait parfois des crashs d'insertion ou des bugs sur les retours.
        int initQty = remoteItem.getInitialQuantity() > 0 ? remoteItem.getInitialQuantity() : remoteItem.getQuantity();
        values.put("initial_quantity", initQty);

        values.put("unit_price", remoteItem.getUnitPrice());
        values.put("total_price", remoteItem.getTotalPrice());

        // Vient du serveur, donc 'synced'
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteItem.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        // Tente de mettre à jour
        int rowsUpdated = db.update(
                TABLE_SALE_ITEMS,
                values,
                "id = ?",
                new String[]{String.valueOf(remoteItem.getId())}
        );

        long result;
        if (rowsUpdated > 0) {
            result = remoteItem.getId(); // Mise à jour réussie
        } else {
            // Insérer la nouvelle ligne
            result = db.insert(TABLE_SALE_ITEMS, null, values);
        }
        db.close();
        return result;
    }

    /**
     * Met à jour l'ID local (négatif) du détail de vente avec l'ID réel du serveur.
     * Cette méthode est appelée dans la transaction du PUSH si l'ID a été résolu.
     */
    public void updateLocalId(long oldId, long newId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Il n'y a pas de tables qui pointent vers sale_items, donc seule la mise à jour de l'ID est nécessaire.
        ContentValues itemValues = new ContentValues();
        itemValues.put("id", newId);
        itemValues.put(DatabaseHelper.COL_SYNC_STATUS, "synced"); // Marquer comme synchronisé

        db.update(
                TABLE_SALE_ITEMS,
                itemValues,
                "id = ?",
                new String[]{String.valueOf(oldId)}
        );
        db.close();
        Log.i(TAG, "ID Détail Vente local " + oldId + " mis à jour vers " + newId + ".");
    }

    /**
     * Met à jour les clés étrangères (product_id ou sale_id) après la résolution de l'ID parent.
     *
     * @param db Instance de la base de données (dans une transaction active).
     * @param foreignKeyName Nom de la colonne FK à mettre à jour ('product_id' ou 'sale_id').
     * @param oldParentId ID temporaire du parent.
     * @param newParentId ID réel du parent résolu par le serveur.
     */
    public void updateForeignKeyId(SQLiteDatabase db, String foreignKeyName, long oldParentId, long newParentId) {
        ContentValues values = new ContentValues();
        values.put(foreignKeyName, newParentId);

        // Mettre à jour tous les items qui pointaient vers l'ancien ID (temporaire)
        db.update(
                TABLE_SALE_ITEMS,
                values,
                foreignKeyName + " = ?",
                new String[]{String.valueOf(oldParentId)}
        );
        // Pas de db.close() car c'est géré par la classe appelante (ProductDao ou SaleDao)
        Log.d(TAG, "FK " + foreignKeyName + " mis à jour: " + oldParentId + " -> " + newParentId);
    }
}