package com.enkiba.app.modelsDao;

import android.content.Context;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.StockRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.SaleReturnEntity;
import com.enkiba.app.models.SaleReturn;

import java.util.ArrayList;
import java.util.List;

/**
 * Retours côté synchronisation (les retours locaux sont créés par SaleDao.processReturn).
 */
public class SaleReturnDao {

    private final StockRoomDao dao;

    public SaleReturnDao(Context context) {
        this.dao = AppDatabase.get(context).stockDao();
    }

    public List<SaleReturn> getPendingReturnsForSync() {
        List<SaleReturn> list = new ArrayList<>();
        for (SaleReturnEntity e : dao.getPendingReturns()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markReturnAsSynced(String id) {
        dao.markReturnSynced(id);
    }

    /** Insère ou remplace un retour reçu du serveur (PULL). */
    public void upsertReturnFromRemote(SaleReturn r) {
        dao.upsertReturn(Mappers.toEntity(r, EntitiesKt.SYNC_SYNCED));
    }
}
