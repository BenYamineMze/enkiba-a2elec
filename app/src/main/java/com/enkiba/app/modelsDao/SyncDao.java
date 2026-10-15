package com.enkiba.app.modelsDao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleArchive;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleItemArchive;
import com.enkiba.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Façade de synchronisation (SyncDao).
 * Unifie l'interface pour le SynchronizationManager.
 * ADAPTATION : Gestion des IDs User ET Product en String (UUID) + Négociation Prix.
 */
public class SyncDao {

    private final ProductDao productDao;
    private final UserDao userDao;
    private final ClientDao clientDao;
    private final SaleDao saleDao;
    private final SaleItemDao saleItemDao;
    private final HistoryDao historyDao;
    private final SaleArchiveDao saleArchiveDao;
    private final DatabaseHelper dbHelper;

    public SyncDao(Context context) {
        this.productDao = new ProductDao(context);
        this.userDao = new UserDao(context);
        this.clientDao = new ClientDao(context);
        this.saleDao = new SaleDao(context);
        this.saleItemDao = new SaleItemDao(context);
        this.historyDao = new HistoryDao(context);
        this.saleArchiveDao = new SaleArchiveDao(context);
        this.dbHelper = new DatabaseHelper(context);
    }

    // ----------------------------------------------------------------------------------
    // A. PUSH : RÉCUPÉRATION DES ÉLÉMENTS EN ATTENTE
    // ----------------------------------------------------------------------------------

    public List<Product> getPendingProducts() { return productDao.getPendingProductsForSync(); }
    public List<Client> getPendingClients() { return clientDao.getPendingClientsForSync(); }
    public List<User> getPendingUsers() { return userDao.getPendingUsersForSync(); }

    // Lecture directe des Ventes
    public List<SaleHeader> getPendingSales() {
        List<SaleHeader> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_SALES + " WHERE sync_status = 'pending'", null);
        if (cursor.moveToFirst()) {
            do { list.add(cursorToSaleHeader(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // Lecture directe des Items Ventes
    public List<SaleItem> getPendingSaleItems() {
        List<SaleItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_SALE_ITEMS + " WHERE sync_status = 'pending'", null);
        if (cursor.moveToFirst()) {
            do { list.add(cursorToSaleItem(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<ProductHistory> getPendingHistory() { return historyDao.getPendingHistoryForSync(); }

    public List<SaleArchive> getPendingSaleArchives() {
        List<SaleArchive> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_SALE_ARCHIVE + " WHERE sync_status = 'pending'", null);
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToSaleArchive(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<SaleItemArchive> getPendingSaleItemArchives() {
        List<SaleItemArchive> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_SALE_ITEMS_ARCHIVE + " WHERE sync_status = 'pending'", null);
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToSaleItemArchive(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // ----------------------------------------------------------------------------------
    // B. PULL : INSERTION/MISE À JOUR DEPUIS LE SERVEUR
    // ----------------------------------------------------------------------------------

    public String insertOrUpdateUser(User u) { return userDao.insertOrUpdateUserFromRemote(u); }
    public String insertOrUpdateProduct(Product p) { return productDao.insertOrUpdateProductFromRemote(p); }
    public long insertOrUpdateClient(Client c) { return clientDao.insertOrUpdateClientFromRemote(c); }
    public long insertOrUpdateSale(SaleHeader s) { return saleDao.insertOrUpdateSaleFromRemote(s); }
    public long insertOrUpdateSaleItem(SaleItem si) { return saleItemDao.insertOrUpdateSaleItemFromRemote(si); }
    public long insertOrUpdateHistory(ProductHistory h) { return historyDao.insertOrUpdateHistoryFromRemote(h); }


    // ----------------------------------------------------------------------------------
    // C. ID RESOLUTION : MISE À JOUR DES IDs TEMPORAIRES ET DES FKs
    // ----------------------------------------------------------------------------------

    public void updateLocalUserId(String oldId, String newId) { userDao.updateLocalId(oldId, newId); }
    public void updateLocalClientId(long oldId, long newId) { clientDao.updateLocalId(oldId, newId); }

    public void updateLocalSaleId(long oldId, long newId) { saleDao.updateLocalId(oldId, newId); }

    // [AJOUT] Alias pour le SynchronizationManager qui appelle updateLocalId pour les ventes
    public void updateLocalId(long oldId, long newId) {
        saleDao.updateLocalId(oldId, newId);
    }

    public void updateLocalSaleItemId(long oldId, long newId) { saleItemDao.updateLocalId(oldId, newId); }
    public void updateLocalHistoryId(long oldId, long newId) { historyDao.updateLocalId(oldId, newId); }

    // ----------------------------------------------------------------------------------
    // D. MARQUER COMME SYNCHRONISÉ
    // ----------------------------------------------------------------------------------

    public void markUserAsSynced(String id) { userDao.markUserAsSynced(id); }
    public void markProductAsSynced(String id) { productDao.markProductAsSynced(id); }
    public void markClientAsSynced(long id) { clientDao.markClientAsSynced(id); }
    public void markSaleAsSynced(long id) { saleDao.markSaleAsSynced(id); }
    public void markSaleItemAsSynced(long id) { saleItemDao.markSaleItemAsSynced(id); }
    public void markHistoryAsSynced(long id) { historyDao.markHistoryAsSynced(id); }
    public void markSaleArchiveAsSynced(long localId) { saleArchiveDao.markSaleArchiveAsSynced(localId); }
    public void markSaleItemArchiveAsSynced(long localId) { saleArchiveDao.markSaleItemArchiveAsSynced(localId); }

    // ----------------------------------------------------------------------------------
    // E. AUTRES UTILITAIRES
    // ----------------------------------------------------------------------------------

    public User getUserById(String id) { return userDao.getUserById(id); }

    public boolean isSaleArchiveSynced(long saleId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM sale_archive WHERE id = ? AND sync_status = 'synced'",
                new String[]{String.valueOf(saleId)}
        );
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public SaleHeader getSaleById(long saleId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        SaleHeader sale = null;
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_SALES + " WHERE id = ?", new String[]{String.valueOf(saleId)});
        if (cursor.moveToFirst()) {
            sale = cursorToSaleHeader(cursor);
        }
        cursor.close();
        return sale;
    }

    public void deleteSaleItem(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_SALE_ITEMS, "id = ?", new String[]{String.valueOf(id)});
    }

    // --- Helpers privés pour la lecture du curseur ---

    @SuppressLint("Range")
    private SaleHeader cursorToSaleHeader(Cursor c) {
        SaleHeader s = new SaleHeader();
        s.setId(c.getInt(c.getColumnIndex("id")));
        s.setUserId(c.getString(c.getColumnIndex("user_id")));

        int clientIdx = c.getColumnIndex("client_id");
        if (clientIdx != -1 && !c.isNull(clientIdx)) {
            s.setClientId(c.getLong(clientIdx));
        } else {
            s.setClientId(0L);
        }

        s.setTotalPrice(c.getDouble(c.getColumnIndex("total_price")));
        s.setPaymentMethod(c.getString(c.getColumnIndex("payment_method")));

        // [AJOUT IMPORTANT] Lecture du flag de négociation
        // Sans cela, l'objet SaleHeader est incomplet et le SyncManager envoie "false"
        int negIndex = c.getColumnIndex("has_negotiation");
        if (negIndex != -1) {
            s.setHasNegotiatedPrice(c.getInt(negIndex) == 1);
        }

        s.setUpdatedAt(c.getString(c.getColumnIndex(DatabaseHelper.COL_UPDATED_AT)));
        s.setSyncStatus(c.getString(c.getColumnIndex(DatabaseHelper.COL_SYNC_STATUS)));
        return s;
    }

    @SuppressLint("Range")
    private SaleItem cursorToSaleItem(Cursor c) {
        SaleItem i = new SaleItem();
        i.setId(c.getLong(c.getColumnIndex("id")));
        i.setSaleId(c.getLong(c.getColumnIndex("sale_id")));
        i.setProductId(c.getString(c.getColumnIndex("product_id"))); // UUID String
        i.setQuantity(c.getInt(c.getColumnIndex("quantity")));

        int idxInit = c.getColumnIndex("initial_quantity");
        if (idxInit != -1 && !c.isNull(idxInit)) {
            i.setInitialQuantity(c.getInt(idxInit));
        } else {
            i.setInitialQuantity(i.getQuantity());
        }

        i.setUnitPrice(c.getDouble(c.getColumnIndex("unit_price")));
        i.setTotalPrice(c.getDouble(c.getColumnIndex("total_price")));
        i.setUpdatedAt(c.getString(c.getColumnIndex(DatabaseHelper.COL_UPDATED_AT)));
        i.setSyncStatus(c.getString(c.getColumnIndex(DatabaseHelper.COL_SYNC_STATUS)));
        return i;
    }

    @SuppressLint("Range")
    private SaleArchive cursorToSaleArchive(Cursor c) {
        SaleArchive s = new SaleArchive();
        s.setId(c.getLong(c.getColumnIndex("id")));
        s.setSaleDate(c.getString(c.getColumnIndex("sale_date")));
        s.setUserId(c.getLong(c.getColumnIndex("user_id")));

        int clientIdx = c.getColumnIndex("client_id");
        if (clientIdx != -1 && !c.isNull(clientIdx)) {
            s.setClientId(c.getInt(clientIdx));
        } else {
            s.setClientId(0);
        }

        s.setTotalPrice(c.getDouble(c.getColumnIndex("total_price")));
        s.setPaymentMethod(c.getString(c.getColumnIndex("payment_method")));
        s.setUpdatedAt(c.getString(c.getColumnIndex(DatabaseHelper.COL_UPDATED_AT)));
        s.setSyncStatus(c.getString(c.getColumnIndex(DatabaseHelper.COL_SYNC_STATUS)));
        return s;
    }

    @SuppressLint("Range")
    private SaleItemArchive cursorToSaleItemArchive(Cursor c) {
        SaleItemArchive i = new SaleItemArchive();
        i.setId(c.getLong(c.getColumnIndex("id")));
        i.setSaleId(c.getLong(c.getColumnIndex("sale_id")));
        i.setProductName(c.getString(c.getColumnIndex("product_name")));
        i.setQuantity(c.getInt(c.getColumnIndex("quantity")));

        int idxInit = c.getColumnIndex("initial_quantity");
        if (idxInit != -1 && !c.isNull(idxInit)) {
            i.setInitialQuantity(c.getInt(idxInit));
        } else {
            i.setInitialQuantity(i.getQuantity());
        }

        i.setUnitPrice(c.getDouble(c.getColumnIndex("unit_price")));
        i.setTotalPrice(c.getDouble(c.getColumnIndex("total_price")));
        i.setUpdatedAt(c.getString(c.getColumnIndex(DatabaseHelper.COL_UPDATED_AT)));
        return i;
    }
}