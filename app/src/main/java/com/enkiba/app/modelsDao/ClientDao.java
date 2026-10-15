package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Client;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class ClientDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_CLIENTS = "clients";
    private static final String TAG = "ClientDao";

    public ClientDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // ----------------------------------------------------------------------
    // --- MÉTHODE DE SUPPORT (LECTURE) ---
    // ----------------------------------------------------------------------

    /**
     * Construit un objet Client à partir de la ligne actuelle du Cursor.
     */
    private Client buildClientFromCursor(Cursor cursor) {
        Client c = new Client();

        // 🚨 IMPORTANT : Utiliser getLong pour l'ID pour la cohérence avec les IDs temporaires.
        c.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        c.setFirstname(cursor.getString(cursor.getColumnIndexOrThrow("firstname")));
        c.setLastname(cursor.getString(cursor.getColumnIndexOrThrow("lastname")));
        c.setAddress(cursor.getString(cursor.getColumnIndexOrThrow("address")));
        c.setPhone(cursor.getString(cursor.getColumnIndexOrThrow("phone")));
        c.setEmail(cursor.getString(cursor.getColumnIndexOrThrow("email")));

        // Champs de Synchronisation
        c.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        c.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));

        return c;
    }


    // ----------------------------------------------------------------------
    // --- OPÉRATIONS D'ÉCRITURE LOCALE (avec marquage 'pending') ---
    // ----------------------------------------------------------------------

    /**
     * Insère ou met à jour un client (si l'ID est connu) et le marque 'pending'.
     */
    public long insertOrUpdateClientLocally(Client client) {
        // ... (Logique inchangée, elle est correcte pour UPSERT local) ...
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        long clientId;
        boolean isNew = client.getId() <= 0;

        if (isNew) {
            clientId = DatabaseUtils.generateTempId();
        } else {
            clientId = client.getId();
        }

        values.put("id", clientId);
        values.put("firstname", client.getFirstname());
        values.put("lastname", client.getLastname());
        values.put("address", client.getAddress());
        values.put("phone", client.getPhone());
        values.put("email", client.getEmail());

        String currentTimestamp = DatabaseUtils.getCurrentIsoTime();
        values.put(DatabaseHelper.COL_UPDATED_AT, currentTimestamp);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        long result;
        if (isNew) {
            result = db.insert(TABLE_CLIENTS, null, values);
            if (result > 0) result = clientId;
        } else {
            result = db.update(
                    TABLE_CLIENTS,
                    values,
                    "id = ?",
                    new String[]{ String.valueOf(clientId) }
            );
        }

        db.close();
        return result;
    }


    // ----------------------------------------------------------------------
    // --- AJOUTS POUR LA SUPPRESSION (SOFT DELETE) ---
    // ----------------------------------------------------------------------

    /**
     * Marque un client pour la suppression (Soft Delete).
     * @param clientId ID du client à marquer.
     * @return true si la ligne a été mise à jour.
     */
    public boolean markClientForDeletion(long clientId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "deleted"); // 🚨 Statut pour le PUSH DELETE 🚨

        int rows = db.update(
                TABLE_CLIENTS,
                values,
                "id = ?",
                new String[]{String.valueOf(clientId)}
        );
        db.close();
        return rows > 0;
    }

    /**
     * Supprime physiquement un client de la base de données locale.
     * Appelée UNIQUEMENT après confirmation de suppression du serveur (RPC).
     */
    public void deleteClientPhysically(long clientId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Suppression dans la table CLIENTS
            db.delete(
                    TABLE_CLIENTS,
                    "id = ?",
                    new String[]{String.valueOf(clientId)}
            );

            // 2. Supprimer les références (sales) si ON DELETE n'est pas CASCADE
            // (La FK sale.client_id DOIT être gérée ici ou dans le schéma)
            // Laissez cette ligne si vous voulez être sûr :
            // db.delete("sales", "client_id = ?", new String[]{String.valueOf(clientId)});

            db.setTransactionSuccessful();
            Log.i(TAG, "Client ID " + clientId + " supprimé physiquement après confirmation du serveur.");

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la suppression physique du client : " + e.getMessage());
        } finally {
            db.endTransaction();
            db.close();
        }
    }


    // ----------------------------------------------------------------------
    // --- LECTURE SIMPLE (Récupération des méthodes retirées de DatabaseHelper) ---
    // ----------------------------------------------------------------------

    /**
     * Récupère tous les clients de la base de données.
     */
    public List<Client> getAllClients() {
        List<Client> clients = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_CLIENTS + " ORDER BY lastname ASC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                clients.add(buildClientFromCursor(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return clients;
    }

    /**
     * Récupère un client par son ID.
     */
    public Client getClientById(long clientId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Client client = null;

        Cursor c = db.query(
                TABLE_CLIENTS,
                null,
                "id = ?",
                new String[]{String.valueOf(clientId)},
                null, null, null
        );

        if (c != null) {
            if (c.moveToFirst()) {
                client = buildClientFromCursor(c);
            }
            c.close();
        }
        db.close();
        return client;
    }


    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION (pour SyncDao / Manager) ---
    // ----------------------------------------------------------------------

    /**
     * Récupère tous les clients marqués comme 'pending' ou 'deleted'.
     */
    public List<Client> getPendingClientsForSync() {
        // ... (Logique inchangée, elle est correcte) ...
        List<Client> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_CLIENTS +
                " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " IN ('pending', 'deleted')";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                pendingList.add(buildClientFromCursor(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return pendingList;
    }

    /**
     * Marque un client comme synchronisé.
     */
    public void markClientAsSynced(long clientId) {
        // ... (Logique inchangée, elle est correcte) ...
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        db.update(
                TABLE_CLIENTS,
                values,
                "id = ?",
                new String[]{String.valueOf(clientId)}
        );
        db.close();
    }

    /**
     * Insère ou met à jour un client provenant du serveur (PULL).
     */
    public long insertOrUpdateClientFromRemote(Client remoteClient) {
        // ... (Logique inchangée, elle est correcte) ...
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("id", remoteClient.getId());
        values.put("firstname", remoteClient.getFirstname());
        values.put("lastname", remoteClient.getLastname());
        values.put("address", remoteClient.getAddress());
        values.put("phone", remoteClient.getPhone());
        values.put("email", remoteClient.getEmail());

        values.put(DatabaseHelper.COL_UPDATED_AT, remoteClient.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        int rowsUpdated = db.update(
                TABLE_CLIENTS,
                values,
                "id = ?",
                new String[]{String.valueOf(remoteClient.getId())}
        );

        long result;
        if (rowsUpdated > 0) {
            result = remoteClient.getId();
        } else {
            result = db.insert(TABLE_CLIENTS, null, values);
        }
        db.close();
        return result;
    }

    /**
     * Met à jour l'ID local (négatif) avec l'ID réel du serveur.
     */
    public void updateLocalId(long oldId, long newId) {
        // ... (Logique inchangée, elle est correcte pour la gestion des FK) ...
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Mise à jour de l'ID dans la table CLIENTS
            ContentValues clientValues = new ContentValues();
            clientValues.put("id", newId);
            db.update(TABLE_CLIENTS, clientValues, "id = ?", new String[]{String.valueOf(oldId)});

            // 2. Mise à jour de la table SALES (la seule table qui pointe vers clients)
            ContentValues saleValues = new ContentValues();
            saleValues.put("client_id", newId);
            db.update(DatabaseHelper.TABLE_SALES, saleValues, "client_id = ?", new String[]{String.valueOf(oldId)}); // 🚨 Utilisation de la constante TABLE_SALES 🚨

            // 3. Marquer le client comme 'synced'
            ContentValues syncValues = new ContentValues();
            syncValues.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
            db.update(TABLE_CLIENTS, syncValues, "id = ?", new String[]{String.valueOf(newId)});

            db.setTransactionSuccessful();
            Log.i(TAG, "ID Client local " + oldId + " mis à jour vers " + newId + " et clés étrangères mises à jour.");

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la mise à jour des IDs Client : " + e.getMessage());
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}