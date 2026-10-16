package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
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
 * Accès local aux ventes (tables sales et sale_items).
 * Les ids sont des UUID générés par le téléphone.
 */
public class SaleDao {

    private final DatabaseHelper dbHelper;
    private final SessionManager sessionManager;
    private static final String TABLE_SALES = DatabaseHelper.TABLE_SALES;
    private static final String TABLE_SALE_ITEMS = DatabaseHelper.TABLE_SALE_ITEMS;
    private static final String TABLE_USERS = DatabaseHelper.TABLE_USERS;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;

    public static final String COL_HAS_NEGOTIATION = "has_negotiation";

    // Ventes non supprimées (suppression logique)
    private static final String ACTIVE_SALE = "s." + DatabaseHelper.COL_DELETED_AT + " IS NULL";

    // Remboursements agrégés par vente (retours)
    private static final String REFUNDS_BY_SALE =
            "(SELECT sale_id, SUM(refund_amount) AS refunded FROM " + DatabaseHelper.TABLE_SALE_RETURNS
            + " GROUP BY sale_id) r ON r.sale_id = s.id ";

    // En-tête + nom du vendeur (table users locale, si elle le connaît) + montant remboursé
    private static final String SELECT_SALE_WITH_SELLER =
            "SELECT s.*, u.username, COALESCE(r.refunded, 0) AS refunded FROM " + TABLE_SALES + " s " +
            "LEFT JOIN " + TABLE_USERS + " u ON s.seller_id = u.id OR s.seller_id = u.auth_id " +
            "LEFT JOIN " + REFUNDS_BY_SALE;

    private static final String TAG = "SaleDao";

    public SaleDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    // ----------------------------------------------------------------------
    // --- LECTURE DU CURSEUR ---
    // ----------------------------------------------------------------------

    private SaleHeader buildSaleFromCursor(Cursor cursor) {
        SaleHeader s = new SaleHeader();
        s.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        s.setShopId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        s.setSellerId(cursor.getString(cursor.getColumnIndexOrThrow("seller_id")));
        s.setClientId(cursor.getString(cursor.getColumnIndexOrThrow("client_id")));
        s.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
        s.setPaymentMethod(cursor.getString(cursor.getColumnIndexOrThrow("payment_method")));
        s.setSaleDate(cursor.getString(cursor.getColumnIndexOrThrow("sale_date")));
        s.setHasNegotiatedPrice(cursor.getInt(cursor.getColumnIndexOrThrow(COL_HAS_NEGOTIATION)) == 1);
        s.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        s.setDeletedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DELETED_AT)));
        s.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));

        int userIndex = cursor.getColumnIndex("username");
        if (userIndex != -1) s.setUsername(cursor.getString(userIndex));
        int refundedIndex = cursor.getColumnIndex("refunded");
        if (refundedIndex != -1) s.setRefundedAmount(cursor.getDouble(refundedIndex));
        return s;
    }

    private List<SaleHeader> querySales(String sql, String[] args) {
        List<SaleHeader> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(buildSaleFromCursor(c));
        } catch (Exception e) {
            Log.e(TAG, "Erreur de lecture des ventes : " + e.getMessage(), e);
        }
        db.close();
        return list;
    }

    // ----------------------------------------------------------------------
    // --- ENREGISTREMENT D'UNE VENTE ---
    // ----------------------------------------------------------------------

    /** Insère l'en-tête de vente (à appeler dans une transaction). */
    private long insertSaleLocally(SQLiteDatabase db, SaleHeader sale, String now) {
        ContentValues values = new ContentValues();
        values.put("id", sale.getId());
        values.put(DatabaseHelper.COL_SHOP_ID,
                sale.getShopId() != null ? sale.getShopId() : sessionManager.getShopId());
        values.put("seller_id", sale.getSellerId());
        values.put("client_id", sale.getClientId());
        values.put("total_price", sale.getTotalPrice());
        values.put("payment_method", sale.getPaymentMethod());
        values.put("sale_date", sale.getSaleDate() != null ? sale.getSaleDate() : now);
        values.put(COL_HAS_NEGOTIATION, sale.isHasNegotiatedPrice() ? 1 : 0);
        values.put(DatabaseHelper.COL_UPDATED_AT, now);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        return db.insert(TABLE_SALES, null, values);
    }

    /** Insère une ligne de vente (à appeler dans une transaction). */
    private long insertSaleItemLocally(SQLiteDatabase db, SaleItemRow item, String saleId,
                                       String shopId, double listPrice, String now) {
        if (item.getId() == null) item.setId(UUID.randomUUID().toString());
        ContentValues values = new ContentValues();
        values.put("id", item.getId());
        values.put(DatabaseHelper.COL_SHOP_ID, shopId);
        values.put("sale_id", saleId);
        values.put("product_id", item.getProductId());
        values.put("product_name", item.getProductName() != null ? item.getProductName() : "");
        values.put("quantity", item.getQuantity());
        values.put("list_price", listPrice);
        // unit_price contient le prix négocié le cas échéant
        values.put("unit_price", item.getUnitPrice());
        values.put("total_price", item.getTotal());
        values.put(DatabaseHelper.COL_UPDATED_AT, now);
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        return db.insert(TABLE_SALE_ITEMS, null, values);
    }

    /**
     * Enregistre une vente complète en une seule transaction :
     * en-tête, lignes, décrément du stock local et entrée d'historique par produit.
     * Le stock peut devenir négatif (vente hors ligne concurrente) : c'est voulu.
     * Le drapeau « prix négocié » est calculé ici à partir du prix catalogue.
     *
     * @return l'id (UUID) de la vente, ou null en cas d'échec (rien n'est écrit).
     */
    public String recordSale(SaleHeader header, List<SaleItemRow> rows) {
        if (rows == null || rows.isEmpty()) return null;

        String now = DatabaseUtils.getCurrentIsoTime();
        String shopId = header.getShopId() != null ? header.getShopId() : sessionManager.getShopId();
        header.setShopId(shopId);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Lecture des produits (stock avant vente, prix catalogue)
            int n = rows.size();
            int[] oldQty = new int[n];
            double[] listPrice = new double[n];
            boolean[] found = new boolean[n];
            boolean negotiated = false;
            for (int i = 0; i < n; i++) {
                SaleItemRow row = rows.get(i);
                listPrice[i] = row.getUnitPrice();
                try (Cursor c = db.rawQuery("SELECT quantity, price FROM " + TABLE_PRODUCTS + " WHERE id = ?",
                        new String[]{row.getProductId()})) {
                    if (c.moveToFirst()) {
                        found[i] = true;
                        oldQty[i] = c.getInt(0);
                        listPrice[i] = c.getDouble(1);
                    }
                }
                if (listPrice[i] != row.getUnitPrice()) negotiated = true;
            }
            header.setHasNegotiatedPrice(negotiated);

            // 2. En-tête
            if (insertSaleLocally(db, header, now) == -1) {
                throw new IllegalStateException("insertion de l'en-tête impossible");
            }

            // 3. Lignes, stock et historique
            for (int i = 0; i < n; i++) {
                SaleItemRow row = rows.get(i);
                if (insertSaleItemLocally(db, row, header.getId(), shopId, listPrice[i], now) == -1) {
                    throw new IllegalStateException("insertion de la ligne impossible : " + row.getProductName());
                }
                if (!found[i]) continue; // produit supprimé entre-temps : la ligne reste

                // Stock local seulement : le serveur décrémente le sien à la réception de la ligne
                // (trigger stock_on_sale_item). Le produit n'est donc pas marqué 'pending'.
                int newQty = oldQty[i] - row.getQuantity();
                db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET quantity = ? WHERE id = ?",
                        new Object[]{newQty, row.getProductId()});

                String op = "Vente";
                if (listPrice[i] != row.getUnitPrice()) {
                    op += String.format(Locale.US, " (Négocié: %.0f -> %.0f)", listPrice[i], row.getUnitPrice());
                }
                ProductHistory entry = new ProductHistory();
                entry.setProductId(row.getProductId());
                entry.setUserId(header.getSellerId());
                entry.setOperation(op);
                entry.setOldValue("Stock: " + oldQty[i]);
                entry.setNewValue("Stock: " + newQty);
                entry.setTimestamp(now);
                ContentValues hist = HistoryDao.toValues(entry, shopId);
                hist.put(DatabaseHelper.COL_UPDATED_AT, now);
                hist.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
                if (db.insert(DatabaseHelper.TABLE_HISTORY, null, hist) == -1) {
                    throw new IllegalStateException("insertion de l'historique impossible");
                }
            }
            db.setTransactionSuccessful();
            return header.getId();
        } catch (Exception e) {
            Log.e(TAG, "recordSale : " + e.getMessage(), e);
            return null;
        } finally {
            db.endTransaction();
        }
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
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            String saleId, productId, shopId;
            int soldQty;
            double unitPrice;
            try (Cursor c = db.rawQuery("SELECT sale_id, product_id, " + DatabaseHelper.COL_SHOP_ID
                    + ", quantity, unit_price FROM " + TABLE_SALE_ITEMS + " WHERE id = ?", new String[]{itemId})) {
                if (!c.moveToFirst()) return -1;
                saleId = c.getString(0);
                productId = c.getString(1);
                shopId = c.getString(2);
                soldQty = c.getInt(3);
                unitPrice = c.getDouble(4);
            }

            int alreadyReturned = getReturnedQuantity(db, itemId);
            if (alreadyReturned + qtyToReturn > soldQty) {
                Log.e(TAG, "Retour refusé : dépassement de la quantité vendue");
                return -1;
            }

            double refund = qtyToReturn * unitPrice;
            SaleReturn ret = new SaleReturn();
            ret.setShopId(shopId != null ? shopId : sessionManager.getShopId());
            ret.setSaleId(saleId);
            ret.setSaleItemId(itemId);
            ret.setProductId(productId);
            ret.setQuantity(qtyToReturn);
            ret.setRefundAmount(refund);
            ret.setCreatedBy(userId);
            ret.setCreatedAt(now);
            ContentValues rv = SaleReturnDao.toValues(ret);
            rv.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
            if (db.insert(DatabaseHelper.TABLE_SALE_RETURNS, null, rv) == -1) {
                throw new IllegalStateException("insertion du retour impossible");
            }

            // Stock local seulement (le serveur remet le sien par trigger)
            int oldQty = 0;
            try (Cursor c = db.rawQuery("SELECT quantity FROM " + TABLE_PRODUCTS + " WHERE id = ?", new String[]{productId})) {
                if (c.moveToFirst()) oldQty = c.getInt(0);
            }
            db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET quantity = quantity + ? WHERE id = ?",
                    new Object[]{qtyToReturn, productId});

            ProductHistory entry = new ProductHistory();
            entry.setProductId(productId);
            entry.setUserId(userId);
            entry.setOperation("RETOUR CLIENT");
            entry.setOldValue("Stock: " + oldQty);
            entry.setNewValue("Stock: " + (oldQty + qtyToReturn) + " (remboursé " + String.format(Locale.US, "%.0f", refund) + " KMF)");
            entry.setTimestamp(now);
            ContentValues hist = HistoryDao.toValues(entry, shopId);
            hist.put(DatabaseHelper.COL_UPDATED_AT, now);
            hist.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
            if (db.insert(DatabaseHelper.TABLE_HISTORY, null, hist) == -1) {
                throw new IllegalStateException("insertion de l'historique impossible");
            }

            db.setTransactionSuccessful();
            return refund;
        } catch (Exception e) {
            Log.e(TAG, "processReturn : " + e.getMessage(), e);
            return -1;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private int getReturnedQuantity(SQLiteDatabase db, String itemId) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(quantity), 0) FROM " + DatabaseHelper.TABLE_SALE_RETURNS
                + " WHERE sale_item_id = ?", new String[]{itemId})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
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
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT si.id, si.product_id, si.product_name, si.quantity, si.unit_price, " +
                "COALESCE((SELECT SUM(r.quantity) FROM " + DatabaseHelper.TABLE_SALE_RETURNS + " r WHERE r.sale_item_id = si.id), 0) " +
                "FROM " + TABLE_SALE_ITEMS + " si WHERE si.sale_id = ?";
        try (Cursor c = db.rawQuery(query, new String[]{saleId})) {
            while (c.moveToNext()) {
                SaleItemRow row = new SaleItemRow();
                row.setId(c.getString(0));
                row.setProductId(c.getString(1));
                row.setProductName(c.getString(2));
                int returned = c.getInt(5);
                int netQty = c.getInt(3) - returned;
                row.setQuantity(netQty);
                row.setReturnedQuantity(returned);
                row.setUnitPrice(c.getDouble(4));
                row.setTotal(netQty * c.getDouble(4));
                list.add(row);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur getSaleItems : " + e.getMessage(), e);
        }
        db.close();
        return list;
    }

    public SaleHeader getSaleHeaderById(String saleId) {
        List<SaleHeader> list = querySales(SELECT_SALE_WITH_SELLER + "WHERE s.id = ?", new String[]{saleId});
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SaleHeader> getSaleHeadersByUser(String userId) {
        return querySales(SELECT_SALE_WITH_SELLER + "WHERE s.seller_id = ? AND " + ACTIVE_SALE
                + " ORDER BY s.sale_date DESC", new String[]{userId});
    }

    public List<SaleHeader> getAllSaleHeaders() {
        return querySales(SELECT_SALE_WITH_SELLER + "WHERE " + ACTIVE_SALE
                + " ORDER BY s.sale_date DESC", null);
    }

    // ----------------------------------------------------------------------
    // --- STATISTIQUES ---
    // ----------------------------------------------------------------------

    private double sumTotal(String where, String[] args) {
        double total = 0.0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // Montant encaissé : total des ventes moins les remboursements
        try (Cursor c = db.rawQuery("SELECT SUM(s.total_price - COALESCE(r.refunded, 0)) FROM " + TABLE_SALES + " s "
                + "LEFT JOIN " + REFUNDS_BY_SALE + "WHERE "
                + ACTIVE_SALE + (where != null ? " AND " + where : ""), args)) {
            if (c.moveToFirst()) total = c.getDouble(0);
        } catch (Exception e) {
            Log.e(TAG, "Erreur de calcul du total : " + e.getMessage(), e);
        }
        db.close();
        return total;
    }

    public double getTotalAchatsParVirement() {
        return sumTotal("s.payment_method = ?", new String[]{"virement"});
    }

    public double getTotalAchatsEnEspeces() {
        return sumTotal("s.payment_method = ?", new String[]{"espece"});
    }

    public double getTotalSalesMoney() {
        return sumTotal(null, null);
    }

    public List<SaleHeader> getRecentSalesByPayment(String paymentMethod, int limit) {
        return querySales(SELECT_SALE_WITH_SELLER + "WHERE s.payment_method = ? AND " + ACTIVE_SALE
                + " ORDER BY s.sale_date DESC LIMIT ?", new String[]{paymentMethod, String.valueOf(limit)});
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<SaleHeader> getPendingSalesForSync() {
        return querySales("SELECT * FROM " + TABLE_SALES + " WHERE "
                + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null);
    }

    public void markSaleAsSynced(String saleId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_SALES, values, "id = ?", new String[]{saleId});
        db.close();
    }

    /** Insère ou remplace une vente reçue du serveur (PULL). */
    public void upsertSaleFromRemote(SaleHeader remoteSale) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", remoteSale.getId());
        values.put(DatabaseHelper.COL_SHOP_ID, remoteSale.getShopId());
        values.put("seller_id", remoteSale.getSellerId());
        values.put("client_id", remoteSale.getClientId());
        values.put("total_price", remoteSale.getTotalPrice());
        values.put("payment_method", remoteSale.getPaymentMethod());
        values.put("sale_date", remoteSale.getSaleDate());
        values.put(COL_HAS_NEGOTIATION, remoteSale.isHasNegotiatedPrice() ? 1 : 0);
        values.put(DatabaseHelper.COL_UPDATED_AT, remoteSale.getUpdatedAt());
        values.put(DatabaseHelper.COL_DELETED_AT, remoteSale.getDeletedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE_SALES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
