package com.enkiba.app.modelsDao;

import android.content.Context;
import android.util.Log;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.QuoteRoomDao;
import com.enkiba.app.data.local.entity.EntitiesKt;
import com.enkiba.app.data.local.entity.ProductEntity;
import com.enkiba.app.data.local.entity.QuoteEntity;
import com.enkiba.app.data.local.entity.QuoteItemEntity;
import com.enkiba.app.data.local.entity.QuoteWithClient;
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
 * Accès local aux devis (quotes, quote_items, ids UUID), au-dessus de Room.
 * Les lignes sont écrites une seule fois, à la création du devis.
 */
public class QuoteDao {

    private static final String TAG = "QuoteDao";

    private final AppDatabase db;
    private final QuoteRoomDao dao;
    private final SessionManager sessionManager;

    public QuoteDao(Context context) {
        this.db = AppDatabase.get(context);
        this.dao = db.quoteDao();
        this.sessionManager = new SessionManager(context);
    }

    private static List<Quote> toModels(List<QuoteWithClient> rows) {
        List<Quote> list = new ArrayList<>(rows.size());
        for (QuoteWithClient r : rows) list.add(Mappers.toModel(r));
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
        if (quote.getShopId() == null) quote.setShopId(sessionManager.getShopId());
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

        try {
            db.runInTransaction(() -> {
                dao.insertQuote(Mappers.toEntity(quote, now, EntitiesKt.SYNC_PENDING));
                for (SaleItemRow r : rows) {
                    ProductEntity p = db.productDao().getEntity(r.getProductId());
                    double listPrice = p != null ? p.getPrice() : r.getUnitPrice();
                    dao.insertItem(new QuoteItemEntity(UUID.randomUUID().toString(), quote.getShopId(),
                            quote.getId(), r.getProductId(), r.getProductName() != null ? r.getProductName() : "",
                            r.getQuantity(), listPrice, r.getUnitPrice(), r.getTotal(), now, EntitiesKt.SYNC_PENDING));
                }
            });
            return quote.getId();
        } catch (Exception e) {
            Log.e(TAG, "createQuote : " + e.getMessage(), e);
            return null;
        }
    }

    // ----------------------------------------------------------------------
    // --- LECTURE ---
    // ----------------------------------------------------------------------

    /** Devis non supprimés, du plus récent au plus ancien ; sellerId null = tous les vendeurs. */
    public List<Quote> getQuotes(String sellerId) {
        return toModels(sellerId == null ? dao.getActive() : dao.getActiveBySeller(sellerId));
    }

    public Quote getQuoteById(String id) {
        QuoteWithClient q = dao.getById(id);
        return q != null ? Mappers.toModel(q) : null;
    }

    /** Lignes d'un devis, au format des lignes de panier (PDF, conversion en vente). */
    public List<SaleItemRow> getQuoteItems(String quoteId) {
        List<SaleItemRow> list = new ArrayList<>();
        for (QuoteItemEntity e : dao.getItems(quoteId)) list.add(Mappers.toRow(e));
        return list;
    }

    // ----------------------------------------------------------------------
    // --- MODIFICATIONS (statut, suppression) ---
    // ----------------------------------------------------------------------

    /** Change le statut d'un devis non converti (accepté, refusé, brouillon). */
    public boolean updateStatus(String quoteId, String status) {
        return dao.updateStatus(quoteId, status, DatabaseUtils.getCurrentIsoTime()) > 0;
    }

    /** Suppression logique d'un devis non converti. */
    public boolean markDeleted(String quoteId) {
        return dao.markDeleted(quoteId, DatabaseUtils.getCurrentIsoTime()) > 0;
    }

    // ----------------------------------------------------------------------
    // --- SUPPORT SYNCHRONISATION ---
    // ----------------------------------------------------------------------

    public List<Quote> getPendingQuotesForSync() {
        List<Quote> list = new ArrayList<>();
        for (QuoteEntity e : dao.getPendingQuotes()) list.add(Mappers.toModel(e));
        return list;
    }

    public List<QuoteItem> getPendingQuoteItemsForSync() {
        List<QuoteItem> list = new ArrayList<>();
        for (QuoteItemEntity e : dao.getPendingItems()) list.add(Mappers.toModel(e));
        return list;
    }

    public void markQuoteAsSynced(String id) {
        dao.markQuoteSynced(id);
    }

    public void markQuoteItemAsSynced(String id) {
        dao.markItemSynced(id);
    }

    /** Insère ou remplace un devis reçu du serveur, sauf modification locale non envoyée. */
    public void upsertQuoteFromRemote(Quote q) {
        db.runInTransaction(() -> {
            if (EntitiesKt.SYNC_PENDING.equals(dao.getSyncStatus(q.getId()))) return;
            dao.upsertQuote(Mappers.toEntity(q, q.getUpdatedAt(), EntitiesKt.SYNC_SYNCED));
        });
    }

    /** Insère ou remplace une ligne de devis reçue du serveur. */
    public void upsertQuoteItemFromRemote(QuoteItem i) {
        dao.upsertItem(Mappers.toEntity(i, EntitiesKt.SYNC_SYNCED));
    }
}
