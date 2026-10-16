package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable; // Import ajouté
import android.text.TextWatcher; // Import ajouté
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// Changement d'héritage
// import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.QrListAdapter;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.ProductDao; // 🚨 NOUVEAU 🚨
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.PDFUtils;

import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText; // Import ajouté

import java.util.ArrayList;
import java.util.List;

// L'activité hérite de BaseActivity pour la gestion de session
public class QrListActivity extends BaseActivity {

    private RecyclerView recycler;
    private MaterialButton btnExportPdf;
    private TextInputEditText edtSearch; // Champ de recherche

    private ProductDao productDao;

    private List<Product> products = new ArrayList<>(); // Liste complète
    private List<Product> filteredProducts = new ArrayList<>(); // Liste filtrée

    private SessionManager sessionManager;
    private QrListAdapter adapter; // Référence à l'adaptateur

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_list);

        sessionManager = new SessionManager(this);

        // 🚨 CRITIQUE : Initialisation du DAO 🚨
        this.productDao = new ProductDao(this);

        recycler = findViewById(R.id.recyclerQrList);
        btnExportPdf = findViewById(R.id.btnExportPdf);
        edtSearch = findViewById(R.id.edtSearch); // Liaison du champ de recherche

        // 🚨 CORRECTION : Appel à ProductDao pour charger la liste 🚨
        products = productDao.getAllProductsList();
        // Au départ, la liste filtrée est identique à la liste complète
        filteredProducts.addAll(products);

        // Adapter
        // 🚨 FIX : On passe 'this' (le Context) en premier argument
        adapter = new QrListAdapter(this, filteredProducts);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        // --- Gestion de la Recherche ---
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Export PDF C2 (Utilise la liste filtrée si une recherche est en cours, ou complète sinon)
        btnExportPdf.setOnClickListener(v -> exportQrPdf());

        // Modifier titre et icône selon la page
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Liste des QRCode");
        imgHeaderIcon.setImageResource(R.drawable.ic_qr_code);

        // Déconnexion
        imgUser.setOnClickListener(this::showUserMenu);


        // ---------- Navigation bar ----------
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    // Méthode de filtrage
    private void filter(String text) {
        filteredProducts.clear();
        if (text.isEmpty()) {
            filteredProducts.addAll(products);
        } else {
            String query = text.toLowerCase().trim();
            for (Product item : products) {
                if (item.getName().toLowerCase().contains(query)) {
                    filteredProducts.add(item);
                }
            }
        }
        // Mise à jour de l'adaptateur
        // Note: Idéalement, ajoutez une méthode 'updateList' dans votre Adapter pour éviter de le recréer
        // Mais recréer fonctionne ici pour simplifier.
        adapter = new QrListAdapter(this, filteredProducts);
        recycler.setAdapter(adapter);
    }

    private void exportQrPdf() {
        new Thread(() -> {
            try {
                // On exporte ce qui est actuellement affiché (filteredProducts)
                PDFUtils.generateQrListPdf(QrListActivity.this, filteredProducts);
                runOnUiThread(() ->
                        Toast.makeText(this, "PDF généré !", Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Erreur PDF : " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void showUserMenu(android.view.View view) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                sessionManager.logout();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }
}