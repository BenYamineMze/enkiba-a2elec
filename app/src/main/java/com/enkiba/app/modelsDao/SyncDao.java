package com.enkiba.app.modelsDao;

import android.content.Context;

import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.models.StockMovement;

import java.util.List;

/**
 * Façade de synchronisation : regroupe les accès locaux utilisés par le SynchronizationManager.
 * Tous les ids sont des UUID générés par le téléphone : plus de résolution d'ids temporaires.
 */
public class SyncDao {

    private final ProductDao productDao;
    private final ClientDao clientDao;
    private final SaleDao saleDao;
    private final SaleItemDao saleItemDao;
    private final HistoryDao historyDao;
    private final StockMovementDao stockMovementDao;
    private final SaleReturnDao saleReturnDao;

    public SyncDao(Context context) {
        this.productDao = new ProductDao(context);
        this.clientDao = new ClientDao(context);
        this.saleDao = new SaleDao(context);
        this.saleItemDao = new SaleItemDao(context);
        this.historyDao = new HistoryDao(context);
        this.stockMovementDao = new StockMovementDao(context);
        this.saleReturnDao = new SaleReturnDao(context);
    }

    // ----------------------------------------------------------------------------------
    // A. PUSH : ÉLÉMENTS EN ATTENTE
    // ----------------------------------------------------------------------------------

    public List<Product> getPendingProducts() { return productDao.getPendingProductsForSync(); }
    public List<Client> getPendingClients() { return clientDao.getPendingClientsForSync(); }
    public List<SaleHeader> getPendingSales() { return saleDao.getPendingSalesForSync(); }
    public List<SaleItem> getPendingSaleItems() { return saleItemDao.getPendingSaleItemsForSync(); }
    public List<ProductHistory> getPendingHistory() { return historyDao.getPendingHistoryForSync(); }
    public List<StockMovement> getPendingStockMovements() { return stockMovementDao.getPendingMovementsForSync(); }
    public List<SaleReturn> getPendingSaleReturns() { return saleReturnDao.getPendingReturnsForSync(); }

    // ----------------------------------------------------------------------------------
    // B. PULL : INSERTION/MISE À JOUR DEPUIS LE SERVEUR
    // ----------------------------------------------------------------------------------

    public String insertOrUpdateProduct(Product p) { return productDao.insertOrUpdateProductFromRemote(p); }
    public void upsertClient(Client c) { clientDao.upsertClientFromRemote(c); }
    public void upsertSale(SaleHeader s) { saleDao.upsertSaleFromRemote(s); }
    public void upsertSaleItem(SaleItem si) { saleItemDao.upsertSaleItemFromRemote(si); }
    public void upsertHistory(ProductHistory h) { historyDao.upsertHistoryFromRemote(h); }
    public void upsertSaleReturn(SaleReturn r) { saleReturnDao.upsertReturnFromRemote(r); }

    // ----------------------------------------------------------------------------------
    // C. MARQUER COMME SYNCHRONISÉ
    // ----------------------------------------------------------------------------------

    public void markProductAsSynced(String id) { productDao.markProductAsSynced(id); }
    public void markClientAsSynced(String id) { clientDao.markClientAsSynced(id); }
    public void markSaleAsSynced(String id) { saleDao.markSaleAsSynced(id); }
    public void markSaleItemAsSynced(String id) { saleItemDao.markSaleItemAsSynced(id); }
    public void markHistoryAsSynced(String id) { historyDao.markHistoryAsSynced(id); }
    public void markStockMovementAsSynced(String id) { stockMovementDao.markMovementAsSynced(id); }
    public void markSaleReturnAsSynced(String id) { saleReturnDao.markReturnAsSynced(id); }
}
