package com.enkiba.app.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.ClientDao;
import com.enkiba.app.modelsDao.QuoteDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.CurrencyUtils;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.PDFUtils;
import com.enkiba.app.utils.ShopInfo;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Détail d'un devis : informations, lignes, PDF, changement de statut, suppression.
 * Seuls l'auteur du devis et les admins peuvent le modifier (règle du serveur).
 */
public class QuoteDetailsActivity extends BaseActivity {

    public static final String EXTRA_QUOTE_ID = "quote_id";

    private TextView tvRef, tvStatus, tvDate, tvValidity, tvSeller, tvClient, tvSale, tvTotal;
    private RecyclerView recyclerItems;
    private MaterialButton btnConvert, btnPdf, btnAccept, btnReject, btnDelete;
    private View layoutStatusActions;

    private QuoteDao quoteDao;
    private ClientDao clientDao;
    private UserDao userDao;
    private SynchronizationManager syncManager;

    private String quoteId;
    private Quote quote;
    private List<SaleItemRow> items;
    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quote_details);

        quoteDao = new QuoteDao(this);
        clientDao = new ClientDao(this);
        userDao = new UserDao(this);
        syncManager = new SynchronizationManager(this);

        quoteId = getIntent().getStringExtra(EXTRA_QUOTE_ID);
        if (quoteId == null) {
            Toast.makeText(this, "Erreur : devis introuvable", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        loadQuote();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    private void initViews() {
        tvRef = findViewById(R.id.tvQuoteRef);
        tvStatus = findViewById(R.id.tvQuoteStatus);
        tvDate = findViewById(R.id.tvQuoteDate);
        tvValidity = findViewById(R.id.tvQuoteValidity);
        tvSeller = findViewById(R.id.tvQuoteSeller);
        tvClient = findViewById(R.id.tvQuoteClient);
        tvSale = findViewById(R.id.tvQuoteSale);
        tvTotal = findViewById(R.id.tvQuoteTotal);
        recyclerItems = findViewById(R.id.recyclerQuoteItems);
        recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        btnConvert = findViewById(R.id.btnQuoteConvert);
        btnPdf = findViewById(R.id.btnQuotePdf);
        btnAccept = findViewById(R.id.btnQuoteAccept);
        btnReject = findViewById(R.id.btnQuoteReject);
        btnDelete = findViewById(R.id.btnQuoteDelete);
        layoutStatusActions = findViewById(R.id.layoutQuoteStatusActions);

        View back = findViewById(R.id.imgBack);
        if (back != null) back.setOnClickListener(v -> finish());

        btnPdf.setOnClickListener(v -> generatePdf());
        btnAccept.setOnClickListener(v -> changeStatus(Quote.STATUS_ACCEPTED, "accepté"));
        btnReject.setOnClickListener(v -> changeStatus(Quote.STATUS_REJECTED, "refusé"));
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void loadQuote() {
        quote = quoteDao.getQuoteById(quoteId);
        if (quote == null || quote.getDeletedAt() != null) {
            Toast.makeText(this, "Devis introuvable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        items = quoteDao.getQuoteItems(quoteId);
        client = clientDao.getClientById(quote.getClientId());

        tvRef.setText("Devis " + quote.getShortRef());
        tvStatus.setText("Statut : " + quote.getStatusLabel());
        tvDate.setText("Date : " + DatabaseUtils.formatIsoToDisplay(quote.getCreatedAt()));
        tvValidity.setText("Valable jusqu'au : " + (quote.getValidUntil() != null ? quote.getValidUntil() : "—"));
        User seller = userDao.getUserById(quote.getSellerId());
        tvSeller.setText("Établi par : " + (seller != null ? seller.getUsername() + " " + seller.getLastname() : "—"));
        tvClient.setText("Client : " + (client != null
                ? (client.getFirstname() + " " + client.getLastname()).trim() : "—"));
        tvTotal.setText("Total : " + CurrencyUtils.formatKMF(quote.getTotalPrice()));

        if (quote.getConvertedSaleId() != null) {
            tvSale.setVisibility(View.VISIBLE);
            tvSale.setText("Vente : #" + SaleHeader.shortRef(quote.getConvertedSaleId()));
        } else {
            tvSale.setVisibility(View.GONE);
        }

        recyclerItems.setAdapter(new QuoteItemAdapter(items));
        updateActions();
    }

    /** Actions possibles selon le statut et les droits (auteur du devis ou admin). */
    private void updateActions() {
        boolean canEdit = "admin".equalsIgnoreCase(sessionManager.getRole())
                || (quote.getSellerId() != null && quote.getSellerId().equals(sessionManager.getUserId()));
        boolean converted = Quote.STATUS_CONVERTED.equals(quote.getStatus());

        layoutStatusActions.setVisibility(canEdit && !converted ? View.VISIBLE : View.GONE);
        btnAccept.setEnabled(!Quote.STATUS_ACCEPTED.equals(quote.getStatus()));
        btnReject.setEnabled(!Quote.STATUS_REJECTED.equals(quote.getStatus()));
        btnDelete.setVisibility(canEdit && !converted ? View.VISIBLE : View.GONE);
        btnConvert.setVisibility(View.GONE); // conversion : commit suivant
    }

    private void generatePdf() {
        new Thread(() -> {
            java.io.File file = PDFUtils.generateQuotePdf(this, quote, items, client,
                    ShopInfo.NAME, ShopInfo.ADDRESS, ShopInfo.PHONE, ShopInfo.EMAIL);
            if (file == null) {
                runOnUiThread(() -> Toast.makeText(this, "Erreur lors de la génération du PDF", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void changeStatus(String status, String label) {
        if (quoteDao.updateStatus(quoteId, status)) {
            Toast.makeText(this, "Devis marqué " + label, Toast.LENGTH_SHORT).show();
            syncManager.startSynchronization();
            loadQuote();
        } else {
            Toast.makeText(this, "Impossible de modifier ce devis", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le devis " + quote.getShortRef() + " ?")
                .setMessage("Le devis disparaîtra de la liste sur tous les téléphones.")
                .setPositiveButton("Supprimer", (d, w) -> {
                    if (quoteDao.markDeleted(quoteId)) {
                        Toast.makeText(this, "Devis supprimé", Toast.LENGTH_SHORT).show();
                        syncManager.startSynchronization();
                        finish();
                    } else {
                        Toast.makeText(this, "Impossible de supprimer ce devis", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ---------------------------------------------------------
    // ADAPTATEUR DES LIGNES (sans bouton de retour)
    // ---------------------------------------------------------

    private static class QuoteItemAdapter extends RecyclerView.Adapter<QuoteItemAdapter.ViewHolder> {
        private final List<SaleItemRow> rows;

        QuoteItemAdapter(List<SaleItemRow> rows) {
            this.rows = rows;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sale_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SaleItemRow r = rows.get(position);
            holder.tvName.setText(r.getProductName());
            holder.tvDetails.setText(r.getQuantity() + " x " + CurrencyUtils.formatKMF(r.getUnitPrice()));
            holder.tvTotal.setText(CurrencyUtils.formatKMF(r.getTotal()));
            if (holder.btnReturn != null) holder.btnReturn.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName, tvDetails, tvTotal;
            final View btnReturn;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvItemName);
                tvDetails = v.findViewById(R.id.tvItemDetails);
                tvTotal = v.findViewById(R.id.tvItemTotal);
                btnReturn = v.findViewById(R.id.btnReturnItem);
            }
        }
    }
}
