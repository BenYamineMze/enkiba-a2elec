package com.enkiba.app.modelsDao;

import android.content.Context;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.StockRoomDao;
import com.enkiba.app.data.local.entity.StockMovementEntity;
import com.enkiba.app.models.StockMovement;

import java.util.ArrayList;
import java.util.List;

/**
 * Mouvements de stock saisis localement, en attente d'envoi au serveur.
 * Les écritures se font dans les transactions de ProductDao.
 */
public class StockMovementDao {

    private final StockRoomDao dao;

    public StockMovementDao(Context context) {
        this.dao = AppDatabase.get(context).stockDao();
    }

    public List<StockMovement> getPendingMovementsForSync() {
        // Ordre de création : le mouvement 'initial' part avant les corrections
        List<StockMovement> list = new ArrayList<>();
        for (StockMovementEntity e : dao.getPendingMovements()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markMovementAsSynced(String id) {
        dao.markMovementSynced(id);
    }
}
