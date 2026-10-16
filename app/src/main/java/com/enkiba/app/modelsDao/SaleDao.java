package com.enkiba.app.modelsDao;

import android.content.Context;
import android.util.Log;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.SaleRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.ProductEntity;
import com.enkiba.app.data.local.entity.SaleEntity;
import com.enkiba.app.data.local.entity.SaleItemEntity;
import com.enkiba.app.data.local.entity.SaleItemWithReturned;
import com.enkiba.app.data.local.entity.SaleWithExtras;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.models.SaleReturn;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accès local aux ventes (sales, sale_items, sale_returns), au-dessus de Room.
 * Les ids sont des UUID générés par le téléphone. Une vente et ses lignes ne sont
 * jamais modifiées après création : les retours vont dans sale_returns.
 */
public class SaleDao {

    private static final String TAG = "SaleDao";

    private final AppDatabase db;
    private final SaleRoomDao dao;
    private final SessionManager sessionManager;

    public SaleDao(Context context) {
        this.db = AppDatabase.get(context);
        this.dao = db.saleDao();
        this.sessionManager = new SessionManager(context);
    }

    private static List<SaleHeader> toModels(List<SaleWithExtras> rows) {
        List<SaleHeader> list = new ArrayList<>(rows.size());
        for (SaleWithExtras r : rows) list.add(Mappers.toModel(r));
        return list;
    }

    /** Entrée d'historique 'pending' pour un produit, à insérer dans la transaction en cours. */
    private void insertHistory(String productId, String userId, String operation, String oldValue,
                               String newValue, String shopId, String now) {
        ProductHistory entry = new ProductHistory();
        entry.setProductId(productId);
        entry.setUserId(userId);
        entry.setOperation(operation);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setTimestamp(now);
        db.historyDao().insert(Mappers.toEntity(entry, shopId, now, EntitiesKt.SYNC_PENDING));
    }

    // ----------------------------------------------------------------------
    // --- ENREGISTREMENT D'UNE VENTE ---
    // ----------------------------------------------------------------------

    /**
     * Enregistre une vente complète en une seule transaction :
     * en-tête, lignes, décrément du stock local et entrée d'historique par produit.
     * Le stock peut devenir négatif (vente hors ligne concurrente) : c'est voulu.
     * Le drapeau « prix négocié » est calculé ici à partir du prix catalogue.
     *
     * @return l'id (UUID) de la vente, ou null en cas d'échec (rien n'est écrit).
     */
    public String recordSale(SaleHeader header, List<SaleItemRow> rows) {
        return recordSale(header, rows, null);
    }

    /**
     * Comme recordSale, et si quoteId est fourni : la vente est issue de ce devis
     * (source_quote_id), et le devis passe en 'converted' avec converted_sale_id,
     * dans la même transaction. Refusé si le devis est déjà converti, refusé ou supprimé.
     */
    public String recordSale(SaleHeader header, List<SaleItemRow> rows, String quoteId) {
        if (rows == null || rows.isEmpty()) return null;

        String now = DatabaseUtils.getCurrentIsoTime();
        String shopId = header.getShopId() != null ? header.getShopId() : sessionManager.getShopId();
        header.setShopId(shopId);
        header.setSourceQuoteId(quoteId);
        if (header.getSaleDate() == null) header.setSaleDate(now);

        try {
            db.runInTransaction(() -> {
                // 1. Lecture des produits (stock avant vente, prix catalogue)
                int n = rows.size();
                ProductEntity[] products = new ProductEntity[n];
                double[] listPrice = new double[n];
                boolean negotiated = false;
                for (int i = 0; i < n; i++) {
                    SaleItemRow row = rows.get(i);
                    products[i] = db.productDao().getEntity(row.getProductId());
                    listPrice[i] = products[i] != null ? products[i].getPrice() : row.getUnitPrice();
                    if (listPrice[i] != row.getUnitPrice()) negotiated = true;
                }
                header.setHasNegotiatedPrice(negotiated);

                // 2. En-tête
                dao.insertSale(Mappers.toEntity(header, now, EntitiesKt.SYNC_PENDING));

                // 3. Lignes, stock local et historique
                for (int i = 0; i < n; i++) {
                    SaleItemRow row = rows.get(i);
                    if (row.getId() == null) row.setId(UUID.randomUUID().toString());
                    dao.insertItem(new SaleItemEntity(row.getId(), shopId, header.getId(), row.getProductId(),
                            row.getProductName() != null ? row.getProductName() : "", row.getQuantity(),
                            listPrice[i], row.getUnitPrice(), row.getTotal(), now, EntitiesKt.SYNC_PENDING));
                    if (products[i] == null) continue; // produit supprimé entre-temps : la ligne reste

                    // Stock local seulement : le serveur décrémente le sien à la réception de la ligne
                    // (trigger stock_on_sale_item). Le produit n'est donc pas marqué 'pending'.
                    int oldQty = products[i].getQuantity();
                    int newQty = oldQty - row.getQuantity();
                    db.productDao().setQuantity(row.getProductId(), newQty);

                    String op = "Vente";
                    if (listPrice[i] != row.getUnitPrice()) {
                        op += String.format(Locale.US, " (Négocié: %.0f -> %.0f)", listPrice[i], row.getUnitPrice());
                    }
                    insertHistory(row.getProductId(), header.getSellerId(), op,
                            "Stock: " + oldQty, "Stock: " + newQty, shopId, now);
                }

                // 4. Devis converti (échoue s'il n'est plus convertible : tout est annulé)
                if (quoteId != null && db.quoteDao().markConverted(quoteId, header.getId(), now) != 1) {
                    throw new IllegalStateException("devis non convertible : " + quoteId);
                }
            });
            return header.getId();
        } catch (Exception e) {
            Log.e(TAG, "recordSale : " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Annule localement une vente issue d'un devis que le serveur a refusée parce que
     * ce devis a déjà été converti sur un autre téléphone. La vente n'existe pas sur le
     * serveur (refusée avant ses lignes) : elle est retirée du téléphone, le stock local
     * est rétabli, et le devis sera rafraîchi par le prochain pull.
     */
    public void discardRejectedQuoteSale(String saleId, String quoteId) {
        String now = DatabaseUtils.getCurrentIsoTime();
        String shopId = sessionManager.getShopId();
        db.runInTransaction(() -> {
            for (SaleItemEntity item : dao.getItems(saleId)) {
                db.productDao().addQuantity(item.getProductId(), item.getQuantity());
                insertHistory(item.getProductId(), sessionManager.getUserId(),
                        "Vente annulée (devis déjà converti ailleurs)",
                        "Vente #" + SaleHeader.shortRef(saleId), "+" + item.getQuantity() + " en stock", shopId, now);
            }
            db.stockDao().deleteReturnsOfSale(saleId);
            dao.deleteItems(saleId);
            dao.deleteSale(saleId);
            // Le devis local n'est plus « à envoyer » : le pull apportera la version du serveur
            if (quoteId != null) db.quoteDao().markQuoteSynced(quoteId);
        });
        Log.w(TAG, "Vente " + saleId + " annulée : devis " + quoteId + " déjà converti sur le serveur");
    }

    // ----------------------------------------------------------------------
    // --- RETOURS ---
    // ----------------------------------------------------------------------

    /**
     * Enregistre un retour sur une ligne de vente, en une transaction :
     * retour (en attente d'envoi), remise en stock locale et entrée d'historique.
     * La vente et sa ligne ne sont pas modifiées. Le serveur refait le contrôle
     * de quantité et génère lui-même le mouvement de stock 'sale_cancel'.
     *
     * @return le montant remboursé, ou -1 si le retour est refusé ou a échoué.
     */
    public double processReturn(String itemId, int qtyToReturn, String userId) {
        if (qtyToReturn <= 0) return -1;
        String now = DatabaseUtils.getCurrentIsoTime();
        try {
            return db.runInTransaction(() -> {
                SaleItemEntity item = dao.getItem(itemId);
                if (item == null) return -1.0;
                int alreadyReturned = db.stockDao().returnedQuantity(itemId);
                if (alreadyReturned + qtyToReturn > item.getQuantity()) {
                    Log.e(TAG, "Retour refusé : dépassement de la quantité vendue");
                    return -1.0;
                }

                double refund = qtyToReturn * item.getUnitPrice();
                String shopId = item.getShopId() != null ? item.getShopId() : sessionManager.getShopId();
                SaleReturn ret = new SaleReturn();
                ret.setShopId(shopId);
                ret.setSaleId(item.getSaleId());
                ret.setSaleItemId(itemId);
                ret.setProductId(item.getProductId());
                ret.setQuantity(qtyToReturn);
                ret.setRefundAmount(refund);
                ret.setCreatedBy(userId);
                ret.setCreatedAt(now);
                db.stockDao().insertReturn(Mappers.toEntity(ret, EntitiesKt.SYNC_PENDING));

                // Stock local seulement (le serveur remet le sien par trigger)
                ProductEntity product = db.productDao().getEntity(item.getProductId());
                int oldQty = product != null ? product.getQuantity() : 0;
                db.productDao().addQuantity(item.getProductId(), qtyToReturn);
                insertHistory(item.getProductId(), userId, "RETOUR CLIENT", "Stock: " + oldQty,
                        "Stock: " + (oldQty + qtyToReturn) + " (remboursé "
                                + String.format(Locale.US, "%.0f", refund) + " KMF)", shopId, now);
                return refund;
            });
        } catch (Exception e) {
            Log.e(TAG, "processReturn : " + e.getMessage(), e);
            return -1;
        }
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /**
     * Lignes d'une vente, nettes des retours : quantity et total tiennent compte des
     * articles retournés, returnedQuantity donne ce qui a été retourné.
     * Le nom du produit est celui enregistré au moment de la vente.
     */
    public List<SaleItemRow> getSaleItems(String saleId) {
        List<SaleItemRow> list = new ArrayList<>();
        for (SaleItemWithReturned r : dao.getItemsWithReturned(saleId)) list.add(Mappers.toRow(r));
        return list;
    }

    public SaleHeader getSaleHeaderById(String saleId) {
        SaleWithExtras s = dao.getById(saleId);
        return s != null ? Mappers.toModel(s) : null;
    }

    public List<SaleHeader> getSaleHeadersByUser(String userId) {
        return toModels(dao.getActiveBySeller(userId));
    }

    public List<SaleHeader> getAllSaleHeaders() {
        return toModels(dao.getActive());
    }

    // ----------------------------------------------------------------------
    // --- STATISTIQUES (montants encaissés : total moins remboursements) ---
    // ----------------------------------------------------------------------

    public double getTotalAchatsParVirement() {
        return dao.netTotal("virement");
    }

    public double getTotalAchatsEnEspeces() {
        return dao.netTotal("espece");
    }

    public double getTotalSalesMoney() {
        return dao.netTotal(null);
    }

    public List<SaleHeader> getRecentSalesByPayment(String paymentMethod, int limit) {
        return toModels(dao.getRecentByPayment(paymentMethod, limit));
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<SaleHeader> getPendingSalesForSync() {
        List<SaleHeader> list = new ArrayList<>();
        for (SaleEntity e : dao.getPendingSales()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markSaleAsSynced(String saleId) {
        dao.markSaleSynced(saleId);
    }

    /** Insère ou remplace une vente reçue du serveur (PULL). */
    public void upsertSaleFromRemote(SaleHeader remoteSale) {
        dao.upsertSale(Mappers.toEntity(remoteSale, remoteSale.getUpdatedAt(), EntitiesKt.SYNC_SYNCED));
    }
}
