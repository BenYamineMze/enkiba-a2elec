package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItem;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class SaleDao {

    private final DatabaseHelper dbHelper;
    private static final String TABLE_SALES = DatabaseHelper.TABLE_SALES;
    private static final String TABLE_SALE_ITEMS = DatabaseHelper.TABLE_SALE_ITEMS;
    private static final String TABLE_USERS = DatabaseHelper.TABLE_USERS;
    private static final String TABLE_PRODUCTS = DatabaseHelper.TABLE_PRODUCTS;
    private static final String TABLE_CLIENTS = DatabaseHelper.TABLE_CLIENTS;

    // Constantes des ventes
    public static final String COL_IS_ARCHIVED = "is_archived";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_SYNC_STATUS = "sync_status";
    // [NOUVEAU] Nom de la colonne pour la négociation
    public static final String COL_HAS_NEGOTIATION = "has_negotiation";

    private static final String TAG = "SaleDao";

    public SaleDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // ----------------------------------------------------------------------
    // --- MÉTHODE DE SUPPORT (LECTURE) ---
    // ----------------------------------------------------------------------

    private SaleHeader buildSaleFromCursor(Cursor cursor) {
        SaleHeader s = new SaleHeader();
        s.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        s.setUserId(cursor.getString(cursor.getColumnIndexOrThrow("user_id")));

        if (!cursor.isNull(cursor.getColumnIndexOrThrow("client_id"))) {
            s.setClientId(cursor.getLong(cursor.getColumnIndexOrThrow("client_id")));
        }

        s.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
        s.setPaymentMethod(cursor.getString(cursor.getColumnIndexOrThrow("payment_method")));
        s.setSaleDate(cursor.getString(cursor.getColumnIndexOrThrow("sale_date")));

        // [MODIFICATION] Lecture du flag de négociation
        int negIndex = cursor.getColumnIndex(COL_HAS_NEGOTIATION);
        if (negIndex != -1) {
            s.setHasNegotiatedPrice(cursor.getInt(negIndex) == 1);
        }

        s.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        s.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));

        try {
            int userIndex = cursor.getColumnIndex("username");
            if (userIndex != -1) {
                s.setUsername(cursor.getString(userIndex));
            }
        } catch (Exception ignored) {
        }
        return s;
    }


    // ----------------------------------------------------------------------
    // --- OPÉRATIONS D'ÉCRITURE LOCALE (VENTE) ---
    // ----------------------------------------------------------------------

    /**
     * Insère la ligne d'en-tête de vente.
     */
    public long insertSaleLocally(SQLiteDatabase db, SaleHeader sale) {
        ContentValues values = new ContentValues();
        long saleId = (sale.getId() <= 0) ? DatabaseUtils.generateTempId() : sale.getId();

        values.put("id", saleId);
        values.put("user_id", sale.getUserId());
        if (sale.getClientId() != null) values.put("client_id", sale.getClientId());
        values.put("total_price", sale.getTotalPrice());
        values.put("payment_method", sale.getPaymentMethod());
        values.put("sale_date", (sale.getSaleDate() == null) ? DatabaseUtils.getCurrentIsoTime() : sale.getSaleDate());

        // [MODIFICATION] Enregistrement du flag
        values.put(COL_HAS_NEGOTIATION, sale.isHasNegotiatedPrice() ? 1 : 0);

        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        return db.insert(TABLE_SALES, null, values);
    }

    /**
     * Insère une ligne d'article (détail) pour une vente.
     * C'est ici que l'on remplit 'initial_quantity' pour garder la trace de la vente d'origine.
     */
    public long insertSaleItemLocally(SQLiteDatabase db, SaleItemRow item, long saleId) {
        ContentValues values = new ContentValues();
        values.put("sale_id", saleId);
        // [MODIFICATION UUID] L'ID produit est maintenant un String (UUID)
        values.put("product_id", item.getProductId());

        // Quantité vendue actuelle (qui diminuera si retour)
        values.put("quantity", item.getQuantity());

        // Quantité vendue à l'origine (fixe, pour référence)
        values.put("initial_quantity", item.getQuantity());

        // Ici, unit_price contient déjà le prix négocié si c'est le cas (passé depuis l'activité)
        values.put("unit_price", item.getUnitPrice());
        values.put("total_price", item.getTotal());

        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        long result = db.insert(TABLE_SALE_ITEMS, null, values);
        if (result == -1) {
            Log.e(TAG, "Erreur lors de l'insertion de l'article de vente : " + item.getProductName());
        }
        return result;
    }

    /**
     * Enregistre une vente complète en une seule transaction :
     * en-tête, lignes, décrément du stock local et entrée d'historique par produit.
     * Le stock peut devenir négatif (vente hors ligne concurrente) : c'est voulu.
     *
     * @return l'id de la vente, ou -1 en cas d'échec (rien n'est écrit).
     */
    public long recordSale(SaleHeader header, List<SaleItemRow> rows) {
        if (rows == null || rows.isEmpty()) return -1;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long saleId = -1;
        db.beginTransaction();
        try {
            saleId = insertSaleLocally(db, header);
            if (saleId == -1) throw new IllegalStateException("insertion de l'en-tête impossible");

            String now = DatabaseUtils.getCurrentIsoTime();
            for (SaleItemRow row : rows) {
                if (insertSaleItemLocally(db, row, saleId) == -1) {
                    throw new IllegalStateException("insertion de la ligne impossible : " + row.getProductName());
                }

                // Lecture du produit dans la transaction (stock avant vente, prix catalogue)
                int oldQty = 0;
                double listPrice = row.getUnitPrice();
                try (Cursor c = db.rawQuery("SELECT quantity, price FROM " + TABLE_PRODUCTS + " WHERE id = ?",
                        new String[]{row.getProductId()})) {
                    if (!c.moveToFirst()) continue; // produit supprimé entre-temps : la ligne reste
                    oldQty = c.getInt(0);
                    listPrice = c.getDouble(1);
                }
                int newQty = oldQty - row.getQuantity();

                db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET quantity = ?, sold_quantity = sold_quantity + ?, "
                                + DatabaseHelper.COL_UPDATED_AT + " = ?, " + DatabaseHelper.COL_SYNC_STATUS + " = 'pending' WHERE id = ?",
                        new Object[]{newQty, row.getQuantity(), now, row.getProductId()});

                String op = "Vente";
                if (listPrice != row.getUnitPrice()) {
                    op += String.format(java.util.Locale.US, " (Négocié: %.0f -> %.0f)", listPrice, row.getUnitPrice());
                }
                ContentValues hist = new ContentValues();
                hist.put("id", DatabaseUtils.generateTempId());
                hist.put("product_id", row.getProductId());
                hist.put("user_id", header.getUserId());
                hist.put("operation", op);
                hist.put("old_value", "Stock: " + oldQty);
                hist.put("new_value", "Stock: " + newQty);
                hist.put("timestamp", now);
                hist.put(DatabaseHelper.COL_UPDATED_AT, now);
                hist.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
                if (db.insert(DatabaseHelper.TABLE_HISTORY, null, hist) == -1) {
                    throw new IllegalStateException("insertion de l'historique impossible");
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "recordSale : " + e.getMessage(), e);
            saleId = -1;
        } finally {
            db.endTransaction();
        }
        return saleId;
    }

    // ----------------------------------------------------------------------
    // --- GESTION DES RETOURS ET MISES À JOUR ---
    // ----------------------------------------------------------------------

    /**
     * Met à jour le montant total d'une vente (suite à un retour).
     */
    public boolean updateSaleHeader(SaleHeader sale) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("total_price", sale.getTotalPrice());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        int rows = db.update(TABLE_SALES, values, "id = ?", new String[]{String.valueOf(sale.getId())});
        db.close();
        return rows > 0;
    }

    /**
     * Met à jour la quantité restante d'une ligne d'article.
     * Cette méthode est appelée par processReturn.
     */
    public boolean updateSaleItem(long itemId, int newQuantity, double newTotal) {
        if (newQuantity < 0) {
            Log.e(TAG, "Erreur: Quantité négative interdite pour l'item " + itemId);
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("quantity", newQuantity);
        values.put("total_price", newTotal);

        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");

        int rows = db.update(TABLE_SALE_ITEMS, values, "id = ?", new String[]{String.valueOf(itemId)});
        db.close();

        Log.d(TAG, "Update Item " + itemId + " : Qte=" + newQuantity + ", Total=" + newTotal);
        return rows > 0;
    }

    /**
     * Calcule combien d'articles ont déjà été retournés pour cette ligne.
     * Formule : Quantité Initiale - Quantité Actuelle (restante sur le ticket)
     */
    public int getReturnedQuantity(long itemId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int returned = 0;
        Cursor c = null;
        try {
            // Vérifie si la colonne initial_quantity existe avant de l'utiliser
            // Pour éviter les crashs si la migration DB n'est pas parfaite
            c = db.rawQuery(
                    "SELECT quantity, initial_quantity FROM " + TABLE_SALE_ITEMS + " WHERE id = ?",
                    new String[]{String.valueOf(itemId)}
            );

            if (c.moveToFirst()) {
                int currentQty = c.getInt(0);
                // Si la colonne n'existe pas ou est null, on considère 0 comme valeur par défaut
                int initialQty = 0;
                int initIndex = c.getColumnIndex("initial_quantity");
                if (initIndex != -1) {
                    initialQty = c.getInt(initIndex);
                }

                // Si initial_quantity est 0 (ex: anciennes données), on suppose qu'il n'y a pas eu de retour (initial = current)
                if (initialQty == 0 && currentQty > 0) initialQty = currentQty;

                returned = initialQty - currentQty;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur getReturnedQuantity: " + e.getMessage());
        } finally {
            if (c != null) c.close();
            db.close();
        }
        return returned;
    }

    /**
     * Vérifie si un retour est possible.
     * Le retour est autorisé si : Quantité à retourner <= Quantité restante sur le ticket.
     */
    public boolean canReturn(long itemId, int qtyToReturn) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;

        try {
            c = db.rawQuery(
                    "SELECT quantity, initial_quantity FROM " + TABLE_SALE_ITEMS + " WHERE id = ?",
                    new String[]{String.valueOf(itemId)}
            );

            if (!c.moveToFirst()) return false;

            int currentQty = c.getInt(0);
            int initialQty = c.getInt(1);

            // Sécurité anciennes données
            if (initialQty <= 0) initialQty = currentQty;

            int alreadyReturned = initialQty - currentQty;

            // ✅ RÈGLE MÉTIER FINALE
            return (alreadyReturned + qtyToReturn) <= initialQty;

        } finally {
            if (c != null) c.close();
            db.close();
        }
    }


    /**
     * Méthode métier complète pour valider et appliquer un retour.
     * C'est LA méthode à appeler depuis l'activité pour garantir la cohérence.
     */
    public boolean processReturn(long itemId, int qtyToReturn) {

        if (!canReturn(itemId, qtyToReturn)) {
            Log.e(TAG, "Retour refusé : dépassement de la quantité vendue");
            return false;
        }

        int currentQty = getSaleItemQuantity(itemId);
        int newQty = currentQty - qtyToReturn;

        double unitPrice = getUnitPrice(itemId);
        double newTotal = newQty * unitPrice;

        return updateSaleItem(itemId, newQty, newTotal);
    }


    // ----------------------------------------------------------------------
    // --- LECTURE SIMPLE ---
    // ----------------------------------------------------------------------

    /**
     * Récupère la quantité actuelle (restante) sur le ticket.
     */
    public int getSaleItemQuantity(long itemId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int quantity = 0;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT quantity FROM " + TABLE_SALE_ITEMS + " WHERE id = ?",
                    new String[]{String.valueOf(itemId)});
            if (cursor.moveToFirst()) {
                quantity = cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur getSaleItemQuantity: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return quantity;
    }

    /**
     * Récupère les lignes de détail. Modifié pour inclure initial_quantity.
     */
    public List<SaleItemRow> getSaleItems(long saleId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<SaleItemRow> list = new ArrayList<>();

        String query = "SELECT si.id, si.product_id, p.name, si.quantity, si.unit_price, si.total_price, si.initial_quantity " +
                "FROM " + TABLE_SALE_ITEMS + " si " +
                "LEFT JOIN " + TABLE_PRODUCTS + " p ON si.product_id = p.id " +
                "WHERE si.sale_id=?";
        Cursor c = null;
        try {
            c = db.rawQuery(query, new String[]{String.valueOf(saleId)});
            while (c.moveToNext()) {
                SaleItemRow row = new SaleItemRow();
                row.setId(c.getLong(0));

                // [MODIFICATION UUID] product_id est un String
                row.setProductId(c.getString(1));

                row.setProductName(c.getString(2));
                row.setQuantity(c.getInt(3));
                row.setUnitPrice(c.getDouble(4));
                row.setTotal(c.getDouble(5));

                // Lecture safe de initial_quantity (index 6)
                try {
                    int initIndex = c.getColumnIndex("initial_quantity");
                    if (initIndex != -1) {
                        int initQty = c.getInt(initIndex);
                        row.setInitialQuantity(initQty > 0 ? initQty : row.getQuantity());
                    } else {
                        row.setInitialQuantity(row.getQuantity());
                    }
                } catch (Exception e) {
                    row.setInitialQuantity(row.getQuantity());
                }
                list.add(row);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur getSaleItems: " + e.getMessage());
        } finally {
            if (c != null) c.close();
            db.close();
        }
        return list;
    }

    public SaleHeader getSaleHeaderById(long saleId) {
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        SaleHeader header = null;
        Cursor c = db.rawQuery("SELECT s.*, u.username FROM " + TABLE_SALES + " s LEFT JOIN " + TABLE_USERS + " u ON s.user_id = u.id WHERE s.id = ?", new String[]{String.valueOf(saleId)});
        if (c != null && c.moveToFirst()) {
            header = buildSaleFromCursor(c);
            c.close();
        }
        db.close();
        return header;
    }

    public List<SaleHeader> getSaleHeadersByUser(String userId) {
        List<SaleHeader> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT s.*, u.username FROM " + TABLE_SALES + " s LEFT JOIN " + TABLE_USERS + " u ON s.user_id = u.id WHERE s.user_id = ? ORDER BY s.sale_date DESC";
        Cursor c = db.rawQuery(query, new String[]{userId});
        if (c != null) {
            while (c.moveToNext()) list.add(buildSaleFromCursor(c));
            c.close();
        }
        db.close();
        return list;
    }

    public List<SaleHeader> getAllSaleHeaders() {
        List<SaleHeader> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT s.*, u.username FROM " + TABLE_SALES + " s LEFT JOIN " + TABLE_USERS + " u ON s.user_id = u.id ORDER BY s.sale_date DESC";
        Cursor c = db.rawQuery(query, null);
        if (c != null) {
            while (c.moveToNext()) list.add(buildSaleFromCursor(c));
            c.close();
        }
        db.close();
        return list;
    }

    // ----------------------------------------------------------------------
    // --- MÉTHODES DE REPORTING (STATISTIQUES) - RÉINTÉGRÉES ---
    // ----------------------------------------------------------------------

    public double getTotalAchatsParVirement() {
        double total = 0.0;
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT SUM(total_price) FROM " + TABLE_SALES + " WHERE payment_method = ?", new String[]{"virement"});
            if (cursor.moveToFirst()) total = cursor.getDouble(0);
        } catch (Exception e) { Log.e(TAG, "Erreur virement: " + e.getMessage()); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return total;
    }

    public List<SaleHeader> getRecentSalesByPayment(String paymentMethod, int limit) {
        List<SaleHeader> list = new ArrayList<>();
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String query = "SELECT * FROM " + TABLE_SALES + " WHERE payment_method = ? ORDER BY id DESC LIMIT ?";
            cursor = db.rawQuery(query, new String[]{paymentMethod, String.valueOf(limit)});
            if (cursor.moveToFirst()) {
                do {
                    SaleHeader sale = new SaleHeader();
                    sale.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    sale.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
                    sale.setPaymentMethod(cursor.getString(cursor.getColumnIndexOrThrow("payment_method")));
                    int dateIndex = cursor.getColumnIndex("sale_date");
                    if (dateIndex == -1) dateIndex = cursor.getColumnIndex("created_at");
                    if (dateIndex != -1) sale.setSaleDate(cursor.getString(dateIndex));
                    list.add(sale);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) { Log.e(TAG, "Erreur recent: " + e.getMessage()); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return list;
    }

    public double getTotalAchatsEnEspeces() {
        double total = 0.0;
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT SUM(total_price) FROM " + TABLE_SALES + " WHERE payment_method = ?", new String[]{"espece"});
            if (cursor.moveToFirst()) total = cursor.getDouble(0);
        } catch (Exception e) { Log.e(TAG, "Erreur especes: " + e.getMessage()); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return total;
    }

    public double getTotalSalesMoney() {
        double total = 0.0;
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT SUM(total_price) FROM " + TABLE_SALES, null);
            if (cursor.moveToFirst()) total = cursor.getDouble(0);
        } catch (Exception e) { Log.e(TAG, "Erreur total: " + e.getMessage()); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return total;
    }

    // ----------------------------------------------------------------------
    // --- SUPPRESSION ET NETTOYAGE - RÉINTÉGRÉES ---
    // ----------------------------------------------------------------------

    public boolean markSaleForDeletion(long saleId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "deleted");
        int rows = db.update(TABLE_SALES, values, "id = ?", new String[]{String.valueOf(saleId)});
        db.close();
        return rows > 0;
    }

    public void deleteSalePhysically(long saleId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_SALE_ITEMS, "sale_id = ?", new String[]{String.valueOf(saleId)});
            db.delete(TABLE_SALES, "id = ?", new String[]{String.valueOf(saleId)});
            db.setTransactionSuccessful();
        } catch (Exception e) { Log.e(TAG, "Delete phys error: " + e.getMessage()); }
        finally { db.endTransaction(); db.close(); }
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION (PUSH/PULL) ---
    // ----------------------------------------------------------------------

    public List<SaleHeader> getPendingSalesForSync() {
        List<SaleHeader> pendingList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SALES + " WHERE " + DatabaseHelper.COL_SYNC_STATUS + " IN ('pending', 'deleted')", null);
        if (cursor.moveToFirst()) {
            do { pendingList.add(buildSaleFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return pendingList;
    }

    public void markSaleAsSynced(long saleId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(TABLE_SALES, values, "id = ?", new String[]{String.valueOf(saleId)});
        db.close();
    }

    public long insertOrUpdateSaleFromRemote(SaleHeader remoteSale) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", remoteSale.getId());
        values.put("user_id", remoteSale.getUserId());
        if (remoteSale.getClientId() != null) values.put("client_id", remoteSale.getClientId());
        values.put("total_price", remoteSale.getTotalPrice());
        values.put("payment_method", remoteSale.getPaymentMethod());
        values.put("sale_date", remoteSale.getSaleDate());

        // [MODIFICATION] Synchro du flag depuis le serveur (si présent)
        values.put(COL_HAS_NEGOTIATION, remoteSale.isHasNegotiatedPrice() ? 1 : 0);

        values.put(DatabaseHelper.COL_UPDATED_AT, remoteSale.getUpdatedAt());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        int rows = db.update(TABLE_SALES, values, "id = ?", new String[]{String.valueOf(remoteSale.getId())});
        long res = rows > 0 ? remoteSale.getId() : db.insert(TABLE_SALES, null, values);
        db.close();
        return res;
    }

    public void updateLocalId(long oldId, long newId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("id", newId);
            db.update(TABLE_SALES, v, "id = ?", new String[]{String.valueOf(oldId)});
            ContentValues vi = new ContentValues(); vi.put("sale_id", newId);
            db.update(TABLE_SALE_ITEMS, vi, "sale_id = ?", new String[]{String.valueOf(oldId)});
            ContentValues s = new ContentValues(); s.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
            db.update(TABLE_SALES, s, "id = ?", new String[]{String.valueOf(newId)});
            db.setTransactionSuccessful();
        } catch (Exception e) { Log.e(TAG, "UpdateLocalId error: " + e.getMessage()); }
        finally { db.endTransaction(); db.close(); }
    }

    private double getUnitPrice(long itemId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT unit_price FROM " + TABLE_SALE_ITEMS + " WHERE id = ?",
                new String[]{String.valueOf(itemId)}
        );

        double price = 0;
        if (c.moveToFirst()) price = c.getDouble(0);
        c.close();
        db.close();
        return price;
    }

}