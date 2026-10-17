package com.enkiba.app.modelsDao;

import android.content.Context;
import android.util.Log;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.HistoryRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.HistoryEntity;
import com.enkiba.app.data.local.entity.HistoryWithNames;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal local des opérations sur les produits (table history, ids UUID), au-dessus de Room.
 */
public class HistoryDao {

    private static final String TAG = "HistoryDao";

    private final HistoryRoomDao dao;
    private final SessionManager sessionManager;

    public HistoryDao(Context context) {
        this.dao = AppDatabase.get(context).historyDao();
        this.sessionManager = new SessionManager(context);
    }

    /** Insère une entrée d'historique locale (marquée 'pending'). */
    public boolean insertHistoryLocally(ProductHistory history) {
        String now = DatabaseUtils.getCurrentIsoTime();
        if (history.getTimestamp() == null) history.setTimestamp(now);
        try {
            dao.insert(Mappers.toEntity(history, sessionManager.getShopId(), now, EntitiesKt.SYNC_PENDING));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Échec de l'insertion de l'historique " + history.getId(), e);
            return false;
        }
    }

    public boolean insertHistory(String productId, String userId, String operation, String oldValue, String newValue) {
        ProductHistory history = new ProductHistory();
        history.setProductId(productId);
        history.setUserId(userId);
        history.setOperation(operation);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setTimestamp(DatabaseUtils.getCurrentIsoTime());
        return insertHistoryLocally(history);
    }

    /** Historique complet, du plus récent au plus ancien, avec nom du produit et de l'utilisateur. */
    public List<ProductHistory> getProductHistory() {
        List<ProductHistory> list = new ArrayList<>();
        for (HistoryWithNames h : dao.getAllWithNames()) list.add(Mappers.toModel(h));
        return list;
    }

    public void clearProductHistory() {
        dao.clear();
    }

    // --- Synchronisation ---

    public List<ProductHistory> getPendingHistoryForSync() {
        List<ProductHistory> list = new ArrayList<>();
        for (HistoryEntity e : dao.getPending()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markHistoryAsSynced(String historyId) {
        dao.markSynced(historyId);
    }

    /** Insère ou remplace une entrée reçue du serveur (PULL). */
    public void upsertHistoryFromRemote(ProductHistory remoteHistory) {
        dao.upsert(Mappers.toEntity(remoteHistory, remoteHistory.getShopId(),
                remoteHistory.getUpdatedAt(), EntitiesKt.SYNC_SYNCED));
    }
}
