package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Client;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès local à la table clients (ids UUID générés par le téléphone).
 */
public class ClientDao {

    private final DatabaseHelper dbHelper;
    private final SessionManager sessionManager;
    private static final String TABLE_CLIENTS = DatabaseHelper.TABLE_CLIENTS;
    private static final String TAG = "ClientDao";

    public ClientDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    // ----------------------------------------------------------------------
    // --- LECTURE DU CURSEUR ---
    // ----------------------------------------------------------------------

    private Client buildClientFromCursor(Cursor cursor) {
        Client c = new Client();
        c.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        c.setShopId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        c.setFirstname(cursor.getString(cursor.getColumnIndexOrThrow("firstname")));
        c.setLastname(cursor.getString(cursor.getColumnIndexOrThrow("lastname")));
        c.setAddress(cursor.getString(cursor.getColumnIndexOrThrow("address")));
        c.setPhone(cursor.getString(cursor.getColumnIndexOrThrow("phone")));
        c.setEmail(cursor.getString(cursor.getColumnIndexOrThrow("email")));
        c.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        c.setDeletedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DELETED_AT)));
        c.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        return c;
    }

    private ContentValues toValues(Client client) {
        ContentValues values = new ContentValues();
        values.put("id", client.getId());
        values.put(DatabaseHelper.COL_SHOP_ID,
                client.getShopId() != null ? client.getShopId() : sessionManager.getShopId());
        values.put("firstname", client.getFirstname());
        values.put("lastname", client.getLastname());
        values.put("address", client.getAddress());
        values.put("phone", client.getPhone());
        values.put("email", client.getEmail());
        values.put(DatabaseHelper.COL_DELETED_AT, client.getDeletedAt());
        return values;
    }

    // ----------------------------------------------------------------------
    // --- ÉCRITURE LOCALE (marquée 'pending') ---
    // ----------------------------------------------------------------------

    /**
     * Insère ou remplace un client et le marque 'pending'.
     * @return true si l'écriture a réussi.
     */
    public boolean saveClientLocally(Client client) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(client);
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        long result = db.insertWithOnConflict(TABLE_CLIENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        if (result == -1) Log.e(TAG, "Échec de l'enregistrement du client " + client.getId());
        return result != -1;
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Clients actifs (non supprimés), triés par nom. */
    public List<Client> getAllClients() {
        List<Client> clients = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CLIENTS
                + " WHERE " + DatabaseHelper.COL_DELETED_AT + " IS NULL ORDER BY lastname ASC", null)) {
            while (cursor.moveToNext()) clients.add(buildClientFromCursor(cursor));
        }
        db.close();
        return clients;
    }

    public Client getClientById(String clientId) {
        if (clientId == null) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Client client = null;
        try (Cursor c = db.query(TABLE_CLIENTS, null, "id = ?", new String[]{clientId}, null, null, null)) {
            if (c.moveToFirst()) client = buildClientFromCursor(c);
        }
        db.close();
        return client;
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<Client> getPendingClientsForSync() {
        List<Client> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CLIENTS
                + " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null)) {
            while (cursor.moveToNext()) pendingList.add(buildClientFromCursor(cursor));
        }
        db.close();
        return pendingList;
    }

    public void markClientAsSynced(String clientId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_CLIENTS, values, "id = ?", new String[]{clientId});
        db.close();
    }

    /** Insère ou remplace un client reçu du serveur (PULL). */
    public void upsertClientFromRemote(Client remoteClient) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toValues(remoteClient);
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteClient.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE_CLIENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
