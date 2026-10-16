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

    // En-tête + nom du vendeur (table users locale, si elle le connaît)
    private static final String SELECT_SALE_WITH_SELLER =
            "SELECT s.*, u.username FROM " + TABLE_SALES + " s " +
            "LEFT JOIN " + TABLE_USERS + " u ON s.seller_id = u.id OR s.seller_id = u.auth_id ";

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
        // Quantité vendue à l'origine (fixe), pour les retours
        values.put("initial_quantity", item.getQuantity());
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
    // --- RETOURS (gérés localement pour l'instant) ---
    // ----------------------------------------------------------------------

    /** Met à jour le montant total d'une vente (suite à un retour). */
    public boolean updateSaleHeader(SaleHeader sale) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("total_price", sale.getTotalPrice());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_SALES, values, "id = ?", new String[]{sale.getId()});
        db.close();
        return rows > 0;
    }

    /** Met à jour la quantité restante d'une ligne de vente. */
    private boolean updateSaleItem(String itemId, int newQuantity, double newTotal) {
        if (newQuantity < 0) {
            Log.e(TAG, "Quantité négative interdite pour la ligne " + itemId);
            return false;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("quantity", newQuantity);
        values.put("total_price", newTotal);
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_SALE_ITEMS, values, "id = ?", new String[]{itemId});
        db.close();
        return rows > 0;
    }

    /**
     * Valide et applique un retour sur une ligne de vente.
     * Autorisé si : déjà retourné + quantité à retourner <= quantité vendue à l'origine.
     */
    public boolean processReturn(String itemId, int qtyToReturn) {
        int currentQty, initialQty;
        double unitPrice;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT quantity, initial_quantity, unit_price FROM " + TABLE_SALE_ITEMS + " WHERE id = ?",
                new String[]{itemId})) {
            if (!c.moveToFirst()) return false;
            currentQty = c.getInt(0);
            initialQty = c.getInt(1);
            unitPrice = c.getDouble(2);
        } finally {
            db.close();
        }

        int alreadyReturned = initialQty - currentQty;
        if (qtyToReturn <= 0 || alreadyReturned + qtyToReturn > initialQty) {
            Log.e(TAG, "Retour refusé : dépassement de la quantité vendue");
            return false;
        }
        int newQty = currentQty - qtyToReturn;
        return updateSaleItem(itemId, newQty, newQty * unitPrice);
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Lignes d'une vente (le nom du produit est celui enregistré au moment de la vente). */
    public List<SaleItemRow> getSaleItems(String saleId) {
        List<SaleItemRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT id, product_id, product_name, quantity, unit_price, total_price, initial_quantity " +
                "FROM " + TABLE_SALE_ITEMS + " WHERE sale_id = ?";
        try (Cursor c = db.rawQuery(query, new String[]{saleId})) {
            while (c.moveToNext()) {
                SaleItemRow row = new SaleItemRow();
                row.setId(c.getString(0));
                row.setProductId(c.getString(1));
                row.setProductName(c.getString(2));
                row.setQuantity(c.getInt(3));
                row.setUnitPrice(c.getDouble(4));
                row.setTotal(c.getDouble(5));
                row.setInitialQuantity(c.getInt(6));
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
        try (Cursor c = db.rawQuery("SELECT SUM(total_price) FROM " + TABLE_SALES + " s WHERE "
                + ACTIVE_SALE + (where != null ? " AND " + where : ""), args)) {
            if (c.moveToFirst()) total = c.getDouble(0);
        } catch (Exception e) {
            Log.e(TAG, "Erreur de calcul du total : " + e.getMessage(), e);
        }
        db.close();
        return total;
    }

    public double getTotalAchatsParVirement() {
        return sumTotal("payment_method = ?", new String[]{"virement"});
    }

    public double getTotalAchatsEnEspeces() {
        return sumTotal("payment_method = ?", new String[]{"espece"});
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
