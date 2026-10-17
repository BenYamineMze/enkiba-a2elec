package com.enkiba.app.modelsDao;

import android.content.Context;
import android.util.Log;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.ClientRoomDao;
import com.enkiba.app.data.local.entity.ClientEntity;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.models.Client;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès local à la table clients (ids UUID générés par le téléphone), au-dessus de Room.
 */
public class ClientDao {

    private static final String TAG = "ClientDao";

    private final AppDatabase db;
    private final ClientRoomDao dao;
    private final SessionManager sessionManager;

    public ClientDao(Context context) {
        this.db = AppDatabase.get(context);
        this.dao = db.clientDao();
        this.sessionManager = new SessionManager(context);
    }

    private static List<Client> toModels(List<ClientEntity> rows) {
        List<Client> list = new ArrayList<>(rows.size());
        for (ClientEntity e : rows) list.add(Mappers.toModel(e));
        return list;
    }

    /**
     * Insère ou remplace un client et le marque 'pending'.
     * @return true si l'écriture a réussi.
     */
    public boolean saveClientLocally(Client client) {
        if (client.getShopId() == null) client.setShopId(sessionManager.getShopId());
        try {
            dao.upsert(Mappers.toEntity(client, DatabaseUtils.getCurrentIsoTime(), EntitiesKt.SYNC_PENDING));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Échec de l'enregistrement du client " + client.getId(), e);
            return false;
        }
    }

    /** Clients actifs (non supprimés), triés par nom. */
    public List<Client> getAllClients() {
        return toModels(dao.getActive());
    }

    public Client getClientById(String clientId) {
        if (clientId == null) return null;
        ClientEntity e = dao.getById(clientId);
        return e != null ? Mappers.toModel(e) : null;
    }

    // --- Synchronisation ---

    public List<Client> getPendingClientsForSync() {
        return toModels(dao.getPending());
    }

    public void markClientAsSynced(String clientId) {
        dao.markSynced(clientId);
    }

    /** Insère ou remplace un client reçu du serveur, sauf modification locale non envoyée. */
    public void upsertClientFromRemote(Client remoteClient) {
        db.runInTransaction(() -> {
            if (EntitiesKt.SYNC_PENDING.equals(dao.getSyncStatus(remoteClient.getId()))) return;
            dao.upsert(Mappers.toEntity(remoteClient, remoteClient.getUpdatedAt(), EntitiesKt.SYNC_SYNCED));
        });
    }
}
