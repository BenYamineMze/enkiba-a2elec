package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.QuoteItem;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accès local aux devis (tables quotes et quote_items, ids UUID).
 * Les lignes sont écrites une seule fois, à la création du devis.
 */
public class QuoteDao {

    private final DatabaseHelper dbHelper;
    private final SessionManager sessionManager;
    private static final String TABLE_QUOTES = DatabaseHelper.TABLE_QUOTES;
    private static final String TABLE_QUOTE_ITEMS = DatabaseHelper.TABLE_QUOTE_ITEMS;
    private static final String TAG = "QuoteDao";

    // Devis + nom du client
    private static final String SELECT_WITH_CLIENT =
            "SELECT q.*, TRIM(COALESCE(c.firstname, '') || ' ' || COALESCE(c.lastname, '')) AS client_name " +
            "FROM " + TABLE_QUOTES + " q LEFT JOIN " + DatabaseHelper.TABLE_CLIENTS + " c ON c.id = q.client_id ";

    public QuoteDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    // ----------------------------------------------------------------------
    // --- LECTURE DU CURSEUR ---
    // ----------------------------------------------------------------------

    private Quote buildQuoteFromCursor(Cursor c) {
        Quote q = new Quote();
        q.setId(c.getString(c.getColumnIndexOrThrow("id")));
        q.setShopId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
        q.setSellerId(c.getString(c.getColumnIndexOrThrow("seller_id")));
        q.setClientId(c.getString(c.getColumnIndexOrThrow("client_id")));
        q.setStatus(c.getString(c.getColumnIndexOrThrow("status")));
        q.setTotalPrice(c.getDouble(c.getColumnIndexOrThrow("total_price")));
        q.setValidUntil(c.getString(c.getColumnIndexOrThrow("valid_until")));
        q.setNotes(c.getString(c.getColumnIndexOrThrow("notes")));
        q.setConvertedSaleId(c.getString(c.getColumnIndexOrThrow("converted_sale_id")));
        q.setCreatedAt(c.getString(c.getColumnIndexOrThrow("created_at")));
        q.setUpdatedAt(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT)));
        q.setDeletedAt(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_DELETED_AT)));
        q.setSyncStatus(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
        int nameIndex = c.getColumnIndex("client_name");
        if (nameIndex != -1) q.setClientName(c.getString(nameIndex));
        return q;
    }

    private static ContentValues quoteValues(Quote q) {
        ContentValues v = new ContentValues();
        v.put("id", q.getId());
        v.put(DatabaseHelper.COL_SHOP_ID, q.getShopId());
        v.put("seller_id", q.getSellerId());
        v.put("client_id", q.getClientId());
        v.put("status", q.getStatus());
        v.put("total_price", q.getTotalPrice());
        v.put("valid_until", q.getValidUntil());
        v.put("notes", q.getNotes());
        v.put("converted_sale_id", q.getConvertedSaleId());
        v.put("created_at", q.getCreatedAt());
        v.put(DatabaseHelper.COL_DELETED_AT, q.getDeletedAt());
        return v;
    }

    private List<Quote> queryQuotes(String sql, String[] args) {
        List<Quote> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(buildQuoteFromCursor(c));
        } catch (Exception e) {
            Log.e(TAG, "Erreur de lecture des devis : " + e.getMessage(), e);
        }
        db.close();
        return list;
    }

    // ----------------------------------------------------------------------
    // --- CRÉATION ---
    // ----------------------------------------------------------------------

    /**
     * Enregistre un devis et ses lignes en une transaction (statut brouillon,
     * validité par défaut). Le prix catalogue de chaque ligne est lu dans les produits.
     *
     * @return l'id (UUID) du devis, ou null en cas d'échec (rien n'est écrit).
     */
    public String createQuote(Quote quote, List<SaleItemRow> rows) {
        if (rows == null || rows.isEmpty()) return null;
        String now = DatabaseUtils.getCurrentIsoTime();
        String shopId = quote.getShopId() != null ? quote.getShopId() : sessionManager.getShopId();
        quote.setShopId(shopId);
        if (quote.getSellerId() == null) quote.setSellerId(sessionManager.getUserId());
        if (quote.getStatus() == null) quote.setStatus(Quote.STATUS_DRAFT);
        if (quote.getValidUntil() == null) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, Quote.DEFAULT_VALIDITY_DAYS);
            quote.setValidUntil(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime()));
        }
        quote.setCreatedAt(now);
        double total = 0;
        for (SaleItemRow r : rows) total += r.getTotal();
        quote.setTotalPrice(total);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues qv = quoteValues(quote);
            qv.put(DatabaseHelper.COL_UPDATED_AT, now);
            qv.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
            if (db.insert(TABLE_QUOTES, null, qv) == -1) return null;

            for (SaleItemRow r : rows) {
                double listPrice = r.getUnitPrice();
                try (Cursor c = db.rawQuery("SELECT price FROM " + DatabaseHelper.TABLE_PRODUCTS + " WHERE id = ?",
                        new String[]{r.getProductId()})) {
                    if (c.moveToFirst()) listPrice = c.getDouble(0);
                }
                ContentValues iv = new ContentValues();
                iv.put("id", UUID.randomUUID().toString());
                iv.put(DatabaseHelper.COL_SHOP_ID, shopId);
                iv.put("quote_id", quote.getId());
                iv.put("product_id", r.getProductId());
                iv.put("product_name", r.getProductName() != null ? r.getProductName() : "");
                iv.put("quantity", r.getQuantity());
                iv.put("list_price", listPrice);
                iv.put("unit_price", r.getUnitPrice());
                iv.put("total_price", r.getTotal());
                iv.put(DatabaseHelper.COL_UPDATED_AT, now);
                iv.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
                if (db.insert(TABLE_QUOTE_ITEMS, null, iv) == -1) return null;
            }
            db.setTransactionSuccessful();
            return quote.getId();
        } catch (Exception e) {
            Log.e(TAG, "createQuote : " + e.getMessage(), e);
            return null;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Devis non supprimés, du plus récent au plus ancien ; sellerId null = tous les vendeurs. */
    public List<Quote> getQuotes(String sellerId) {
        if (sellerId == null) {
            return queryQuotes(SELECT_WITH_CLIENT + "WHERE q." + DatabaseHelper.COL_DELETED_AT
                    + " IS NULL ORDER BY q.created_at DESC", null);
        }
        return queryQuotes(SELECT_WITH_CLIENT + "WHERE q.seller_id = ? AND q." + DatabaseHelper.COL_DELETED_AT
                + " IS NULL ORDER BY q.created_at DESC", new String[]{sellerId});
    }

    public Quote getQuoteById(String id) {
        List<Quote> list = queryQuotes(SELECT_WITH_CLIENT + "WHERE q.id = ?", new String[]{id});
        return list.isEmpty() ? null : list.get(0);
    }

    /** Lignes d'un devis, au format des lignes de panier (PDF, conversion en vente). */
    public List<SaleItemRow> getQuoteItems(String quoteId) {
        List<SaleItemRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT product_id, product_name, unit_price, quantity FROM "
                + TABLE_QUOTE_ITEMS + " WHERE quote_id = ? ORDER BY rowid", new String[]{quoteId})) {
            while (c.moveToNext()) {
                list.add(new SaleItemRow(c.getString(0), c.getString(1), c.getDouble(2), c.getInt(3)));
            }
        }
        db.close();
        return list;
    }

    // ----------------------------------------------------------------------
    // --- MODIFICATIONS (statut, suppression) ---
    // ----------------------------------------------------------------------

    /** Change le statut d'un devis non converti (accepté, refusé, brouillon). */
    public boolean updateStatus(String quoteId, String status) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        v.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_QUOTES, v, "id = ? AND status != ?", new String[]{quoteId, Quote.STATUS_CONVERTED});
        db.close();
        return rows > 0;
    }

    /** Suppression logique d'un devis non converti. */
    public boolean markDeleted(String quoteId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = DatabaseUtils.getCurrentIsoTime();
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_DELETED_AT, now);
        v.put(DatabaseHelper.COL_UPDATED_AT, now);
        v.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(TABLE_QUOTES, v, "id = ? AND status != ?", new String[]{quoteId, Quote.STATUS_CONVERTED});
        db.close();
        return rows > 0;
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<Quote> getPendingQuotesForSync() {
        return queryQuotes("SELECT * FROM " + TABLE_QUOTES + " WHERE "
                + DatabaseHelper.COL_SYNC_STATUS + " = 'pending' ORDER BY created_at", null);
    }

    public List<QuoteItem> getPendingQuoteItemsForSync() {
        List<QuoteItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM " + TABLE_QUOTE_ITEMS + " WHERE "
                + DatabaseHelper.COL_SYNC_STATUS + " = 'pending'", null)) {
            while (c.moveToNext()) {
                QuoteItem i = new QuoteItem();
                i.setId(c.getString(c.getColumnIndexOrThrow("id")));
                i.setShopId(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SHOP_ID)));
                i.setQuoteId(c.getString(c.getColumnIndexOrThrow("quote_id")));
                i.setProductId(c.getString(c.getColumnIndexOrThrow("product_id")));
                i.setProductName(c.getString(c.getColumnIndexOrThrow("product_name")));
                i.setQuantity(c.getInt(c.getColumnIndexOrThrow("quantity")));
                i.setListPrice(c.getDouble(c.getColumnIndexOrThrow("list_price")));
                i.setUnitPrice(c.getDouble(c.getColumnIndexOrThrow("unit_price")));
                i.setTotalPrice(c.getDouble(c.getColumnIndexOrThrow("total_price")));
                i.setSyncStatus(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SYNC_STATUS)));
                list.add(i);
            }
        }
        db.close();
        return list;
    }

    public void markQuoteAsSynced(String id) {
        markSynced(TABLE_QUOTES, id);
    }

    public void markQuoteItemAsSynced(String id) {
        markSynced(TABLE_QUOTE_ITEMS, id);
    }

    private void markSynced(String table, String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(table, v, "id = ?", new String[]{id});
        db.close();
    }

    /** Insère ou remplace un devis reçu du serveur, sauf modification locale non envoyée. */
    public void upsertQuoteFromRemote(Quote q) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (!ProductDao.isPending(db, TABLE_QUOTES, q.getId())) {
            ContentValues v = quoteValues(q);
            v.put(DatabaseHelper.COL_UPDATED_AT, q.getUpdatedAt());
            v.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
            db.insertWithOnConflict(TABLE_QUOTES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        db.close();
    }

    /** Insère ou remplace une ligne de devis reçue du serveur. */
    public void upsertQuoteItemFromRemote(QuoteItem i) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", i.getId());
        v.put(DatabaseHelper.COL_SHOP_ID, i.getShopId());
        v.put("quote_id", i.getQuoteId());
        v.put("product_id", i.getProductId());
        v.put("product_name", i.getProductName() != null ? i.getProductName() : "");
        v.put("quantity", i.getQuantity());
        v.put("list_price", i.getListPrice());
        v.put("unit_price", i.getUnitPrice());
        v.put("total_price", i.getTotalPrice());
        v.put(DatabaseHelper.COL_UPDATED_AT, i.getUpdatedAt());
        v.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.insertWithOnConflict(TABLE_QUOTE_ITEMS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }
}
