package com.enkiba.app.modelsDao;

import android.content.Context;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.SaleRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.SaleItemEntity;
import com.enkiba.app.models.SaleItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Lignes de vente côté synchronisation (les écritures locales passent par SaleDao.recordSale).
 */
public class SaleItemDao {

    private final SaleRoomDao dao;

    public SaleItemDao(Context context) {
        this.dao = AppDatabase.get(context).saleDao();
    }

    public List<SaleItem> getPendingSaleItemsForSync() {
        List<SaleItem> list = new ArrayList<>();
        for (SaleItemEntity e : dao.getPendingItems()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markSaleItemAsSynced(String itemId) {
        dao.markItemSynced(itemId);
    }

    /** Insère ou remplace une ligne de vente reçue du serveur (PULL). */
    public void upsertSaleItemFromRemote(SaleItem remoteItem) {
        dao.upsertItem(Mappers.toEntity(remoteItem, EntitiesKt.SYNC_SYNCED));
    }
}
