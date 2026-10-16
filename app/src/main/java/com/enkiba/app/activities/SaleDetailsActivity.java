package com.enkiba.app.activities;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.ClientDao;
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.CurrencyUtils;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.PDFUtils;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class SaleDetailsActivity extends BaseActivity {

    // UI Elements
    TextView tvSaleId, tvSaleDate, tvPayment, tvSeller, tvClient, tvTotal;
    RecyclerView recyclerSaleItems;
    FloatingActionButton btnPrint;

    // Data Access Objects
    private SaleDao saleDao;
    private ClientDao clientDao;
    private UserDao userDao;

    // Managers
    private SessionManager sessionManager;
    private SynchronizationManager syncManager;

    // Data
    private String currentSaleId;
    private SaleHeader currentSale;
    private List<SaleItemRow> saleItems;
    private Client currentClient; // Stocké pour le PDF

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_details);

        // 1. Initialisation des outils
        this.saleDao = new SaleDao(this);
        this.clientDao = new ClientDao(this);
        this.userDao = new UserDao(this);
        this.sessionManager = new SessionManager(this);
        this.syncManager = new SynchronizationManager(this);

        // 2. Récupération de l'ID Vente
        currentSaleId = getIntent().getStringExtra("sale_id");
        if (currentSaleId == null) {
            Toast.makeText(this, "Erreur : ID Vente manquant", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 3. Initialisation UI
        initViews();

        // 4. Chargement des données
        loadSaleData();

        // 5. Configuration Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    private void initViews() {
        tvSaleId = findViewById(R.id.tvSaleId);
        tvSaleDate = findViewById(R.id.tvSaleDate);
        tvPayment = findViewById(R.id.tvSalePayment);
        tvSeller = findViewById(R.id.tvSaleSeller);
        tvClient = findViewById(R.id.tvSaleClient);
        tvTotal = findViewById(R.id.tvSaleTotal);

        recyclerSaleItems = findViewById(R.id.recyclerSaleItems);
        recyclerSaleItems.setLayoutManager(new LinearLayoutManager(this));

        btnPrint = findViewById(R.id.btnPrintInvoice);
        btnPrint.setOnClickListener(v -> generatePdf());
    }

    private void loadSaleData() {
        currentSale = saleDao.getSaleHeaderById(currentSaleId);
        saleItems = saleDao.getSaleItems(currentSaleId);

        if (currentSale == null) {
            Toast.makeText(this, "Vente introuvable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- Remplissage Header ---
        tvSaleId.setText("Vente #" + currentSale.getShortRef());
        tvSaleDate.setText("Date : " + CurrencyUtils.formatSaleDate(currentSale.getSaleDate()));
        tvPayment.setText("Paiement : " + currentSale.getPaymentMethod());
        if (currentSale.getRefundedAmount() > 0) {
            tvTotal.setText("Total : " + CurrencyUtils.formatKMF(currentSale.getNetTotal())
                    + " (remboursé : " + CurrencyUtils.formatKMF(currentSale.getRefundedAmount()) + ")");
        } else {
            tvTotal.setText("Total : " + CurrencyUtils.formatKMF(currentSale.getTotalPrice()));
        }

        // --- Info Client ---
        if (currentSale.getClientId() != null) {
            currentClient = clientDao.getClientById(currentSale.getClientId());
        }
        if (currentClient != null) {
            tvClient.setText("Client : " + currentClient.getFirstname() + " " + currentClient.getLastname());
        } else {
            tvClient.setText("Client : -");
        }

        // --- Info Vendeur ---
        User seller = userDao.getUserById(currentSale.getSellerId());
        if (seller != null) {
            tvSeller.setText("Vendu par : " + seller.getUsername() + " " + seller.getLastname());
        } else if (currentSale.getUsername() != null) {
            tvSeller.setText("Vendu par : " + currentSale.getUsername());
        } else {
            tvSeller.setText("Vendu par : Inconnu");
        }

        // --- Liste Articles (Avec support du bouton retour) ---
        // On utilise l'adaptateur interne pour gérer le clic "Retourner"
        SaleDetailAdapter adapter = new SaleDetailAdapter(saleItems);
        recyclerSaleItems.setAdapter(adapter);
    }

    private void generatePdf() {
        PDFUtils.generateInvoicePdf(
                this,
                currentSale,
                saleItems,
                currentClient,
                "A2 ELEC",
                "Moroni - Comores",
                "269 410 06 07",
                "a2elec269@gmail.com"
        );
    }

    // ---------------------------------------------------------
    // LOGIQUE DE RETOUR D'ARTICLE
    // ---------------------------------------------------------

    private void showReturnDialog(SaleItemRow item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_return_product, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvName = view.findViewById(R.id.tvProductName);
        TextView tvMax = view.findViewById(R.id.tvMaxReturn);
        EditText edtQty = view.findViewById(R.id.edtReturnQty);
        Button btnConfirm = view.findViewById(R.id.btnConfirmReturn);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        tvName.setText(item.getProductName());
        tvMax.setText("Quantité retournable : " + item.getQuantity());

        btnConfirm.setOnClickListener(v -> {
            String qtyStr = edtQty.getText().toString().trim();
            if (qtyStr.isEmpty()) return;

            try {
                int qtyToReturn = Integer.parseInt(qtyStr);

                if (qtyToReturn <= 0) {
                    Toast.makeText(this, "Quantité invalide", Toast.LENGTH_SHORT).show();
                } else if (qtyToReturn > item.getQuantity()) {
                    Toast.makeText(this, "Impossible de retourner plus que la quantité restante", Toast.LENGTH_LONG).show();
                } else {
                    processProductReturn(item, qtyToReturn);
                    dialog.dismiss();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Format nombre invalide", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void processProductReturn(SaleItemRow item, int qtyToReturn) {
        // Retour, remise en stock locale et historique en une transaction ;
        // la vente d'origine n'est pas modifiée (le remboursement est déduit à l'affichage)
        double refund = saleDao.processReturn(item.getId(), qtyToReturn, sessionManager.getUserId());
        if (refund < 0) {
            Toast.makeText(this, "Retour refusé (quantité invalide)", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Retour effectué ✅ (" + CurrencyUtils.formatKMF(refund) + " remboursés)", Toast.LENGTH_SHORT).show();
        syncManager.startSynchronization();
        loadSaleData();
    }


    // ---------------------------------------------------------
    // ADAPTATEUR INTERNE (Pour gérer le bouton Retour)
    // ---------------------------------------------------------

    class SaleDetailAdapter extends RecyclerView.Adapter<SaleDetailAdapter.ViewHolder> {
        List<SaleItemRow> items;

        SaleDetailAdapter(List<SaleItemRow> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Utilise votre layout existant "item_sale_item.xml" qui contient le bouton retour
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sale_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SaleItemRow item = items.get(position);

            holder.tvName.setText(item.getProductName());

            // Formatage : "3 x 500 KMF" (quantité nette), et ce qui a été retourné
            String details = item.getQuantity() + " x " + CurrencyUtils.formatKMF(item.getUnitPrice());
            if (item.getReturnedQuantity() > 0) details += " (" + item.getReturnedQuantity() + " retourné)";
            holder.tvDetails.setText(details);

            holder.tvTotal.setText(CurrencyUtils.formatKMF(item.getTotal()));

            // Clic sur le bouton "Retourner" (masqué quand tout a été retourné)
            if (holder.btnReturn != null) {
                holder.btnReturn.setVisibility(item.getQuantity() > 0 ? View.VISIBLE : View.GONE);
                holder.btnReturn.setOnClickListener(v -> showReturnDialog(item));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails, tvTotal;
            Button btnReturn; // Peut être null si votre ancien layout n'a pas le bouton

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvItemName);
                tvDetails = v.findViewById(R.id.tvItemDetails);
                tvTotal = v.findViewById(R.id.tvItemTotal);
                // Le bouton ajouté dans item_sale_item.xml
                btnReturn = v.findViewById(R.id.btnReturnItem);
            }
        }
    }
}