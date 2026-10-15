package com.enkiba.app.activities;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.CartAdapter;
import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.CartItem;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.modelsDao.ClientDao;
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.PDFUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends BaseActivity {

    RecyclerView rvCart;
    TextView txtTotalKmf, txtTotalEur;
    MaterialButton btnConfirm;
    ArrayList<CartItem> cart;

    private DatabaseHelper dbHelper; // Pour la transaction recordSaleWithItems
    private UserDao userDao;
    private ClientDao clientDao;
    private SaleDao saleDao;

    boolean invoiceRequested;
    String paymentMethod;
    String clientNom, clientPrenom, clientAddress, clientPhone, clientEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        // --- Initialisation des DAOs ---
        // Note: sessionManager est déjà initialisé dans BaseActivity.onCreate()

        this.dbHelper = new DatabaseHelper(this);
        this.userDao = new UserDao(this);
        this.clientDao = new ClientDao(this);
        this.saleDao = new SaleDao(this);

        rvCart = findViewById(R.id.rvCart);
        txtTotalKmf = findViewById(R.id.txtTotalKmf);
        txtTotalEur = findViewById(R.id.txtTotalEur);
        btnConfirm = findViewById(R.id.btnConfirmSale);

        @SuppressWarnings("deprecation")
        ArrayList<CartItem> incomingCart = (ArrayList<CartItem>) getIntent().getSerializableExtra("cart");

        cart = incomingCart;
        if (cart == null) cart = new ArrayList<>();

        invoiceRequested = getIntent().getBooleanExtra("invoiceRequested", false);
        paymentMethod = getIntent().getStringExtra("paymentMethod");
        if (paymentMethod == null) paymentMethod = "espece";

        clientNom = getIntent().getStringExtra("client_nom");
        clientPrenom = getIntent().getStringExtra("client_prenom");
        clientAddress = getIntent().getStringExtra("client_address");
        clientPhone = getIntent().getStringExtra("client_phone");
        clientEmail = getIntent().getStringExtra("client_email");

        CartAdapter adapter = new CartAdapter(this, cart, () -> updateTotals());
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        rvCart.setAdapter(adapter);

        updateTotals();

        btnConfirm.setOnClickListener(v -> {
            if (cart.isEmpty()) { Toast.makeText(this, "Panier vide", Toast.LENGTH_SHORT).show(); return; }

            // 🚨 CORRECTION : Récupération de l'ID utilisateur (UUID String)
            // On utilise directement le SessionManager hérité pour plus de fiabilité
            String userId = sessionManager.getUserId();

            if (userId == null || userId.isEmpty()) {
                Toast.makeText(this, "Erreur : Session utilisateur invalide.", Toast.LENGTH_SHORT).show();
                return;
            }

            Integer clientId = null;
            Client clientObj = null;
            if (invoiceRequested) {
                if (clientNom == null) clientNom = "";
                if (clientPrenom == null) clientPrenom = "";

                if (clientNom.isEmpty() || clientPrenom.isEmpty()) {
                    Toast.makeText(this, "Nom et prénom du client requis pour la facture", Toast.LENGTH_LONG).show();
                    return;
                }

                // Insertion locale du client (génère un ID long, potentiellement négatif si temporaire)
                Client newClient = new Client();
                newClient.setFirstname(clientPrenom);
                newClient.setLastname(clientNom);
                newClient.setAddress(clientAddress);
                newClient.setPhone(clientPhone);
                newClient.setEmail(clientEmail);

                long cid = clientDao.insertOrUpdateClientLocally(newClient);

                if (cid == -1) {
                    Toast.makeText(this, "Impossible d'enregistrer le client", Toast.LENGTH_LONG).show();
                    return;
                }

                clientId = (int) cid;
                clientObj = clientDao.getClientById(cid);
            }

            List<SaleItemRow> rows = new ArrayList<>();
            for (CartItem ci : cart) {
                SaleItemRow r = new SaleItemRow();
                r.setProductId(ci.getProductId());
                r.setQuantity(ci.getQuantity());
                r.setUnitPrice(ci.getUnitPrice());
                r.setTotal(ci.getUnitPrice() * ci.getQuantity());
                r.setProductName(ci.getName());
                rows.add(r);
            }

            // 🚨 CORRECTION : On passe userId en String.
            // Assurez-vous que DatabaseHelper.recordSaleWithItems a été mis à jour pour accepter String.
            long saleId = dbHelper.recordSaleWithItems(userId, clientId, rows, paymentMethod);

            if (saleId == -1) {
                Toast.makeText(this, "Erreur enregistrement vente", Toast.LENGTH_SHORT).show();
                return;
            }

            if (invoiceRequested) {
                SaleHeader header = saleDao.getSaleHeaderById(saleId);
                // Pour l'instant, on utilise les lignes du panier pour la facture immédiate
                // car saleDao.getSaleItems pourrait ne pas être implémenté pour retourner des SaleItemRow
                final List<SaleItemRow> fItems = rows;

                if (header != null) {
                    final SaleHeader fHeader = header;
                    final Client fClient = clientObj;
                    new Thread(() -> {
                        PDFUtils.generateInvoicePdf(CartActivity.this, fHeader, fItems, fClient, "A2 ELEC", "Moroni - Comores", "269 ...", "a2elec269@gmail.com");
                    }).start();
                }
            }

            Toast.makeText(this, "Vente enregistrée", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void updateTotals() {
        double total = 0;
        for (CartItem c : cart) total += c.getUnitPrice() * c.getQuantity();
        txtTotalKmf.setText(String.format(Locale.getDefault(), "Total : %.0f KMF", total));
        txtTotalEur.setText(PDFUtils.formatEur(PDFUtils.kmfToEur(total)) + " €");
    }
}