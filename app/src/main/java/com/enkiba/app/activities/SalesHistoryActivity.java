package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.SaleHeaderAdapter;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class SalesHistoryActivity extends BaseActivity {

    RecyclerView recyclerSales;
    SaleHeaderAdapter saleAdapter;

    private SaleDao saleDao;
    private UserDao userDao;

    List<SaleHeader> salesList;

    // sessionManager est déjà présent dans BaseActivity, mais on peut le garder en local pour la clarté
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_history);

        sessionManager = new SessionManager(this);

        // Initialisation des DAOs
        this.saleDao = new SaleDao(this);
        this.userDao = new UserDao(this);

        EditText edtSearchSale = findViewById(R.id.edtSearchSale);

        recyclerSales = findViewById(R.id.recyclerSalesHistory);
        recyclerSales.setLayoutManager(new LinearLayoutManager(this));

        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Historique des Ventes");
        imgHeaderIcon.setImageResource(R.drawable.ic_history);

        // Méthode de recherche
        edtSearchSale.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (saleAdapter != null) {
                    saleAdapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 🔹 Charger les ventes
        loadSalesData();

        // 🔹 Affichage dans le RecyclerView
        if (salesList == null || salesList.isEmpty()) {
            Toast.makeText(this, "Aucune vente enregistrée", Toast.LENGTH_LONG).show();
        } else {
            saleAdapter = new SaleHeaderAdapter(this, salesList, this::openSaleDetails);
            recyclerSales.setAdapter(saleAdapter);
        }

        // 🔹 Bas de page navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
        bottomNav.getMenu().findItem(R.id.nav_history).setChecked(true);

        imgUser.setOnClickListener(this::showUserMenu);
    }

    private void loadSalesData() {
        String role = sessionManager.getRole();

        // 🚨 CORRECTION 1 : L'ID utilisateur est un String (UUID)
        String userId = sessionManager.getUserId();

        // Vérification adaptée pour String
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Erreur : ID utilisateur non valide. Veuillez vous reconnecter.", Toast.LENGTH_LONG).show();
            salesList = null;
            return;
        }

        // 🚨 CORRECTION 2 : Charger les ventes selon le rôle
        if ("admin".equalsIgnoreCase(role)) {
            salesList = saleDao.getAllSaleHeaders();
        } else {
            // Filtrer par l'ID utilisateur (UUID)
            // Assurez-vous que saleDao.getSaleHeadersByUser accepte bien un String
            salesList = saleDao.getSaleHeadersByUser(userId);
        }
    }

    private void openSaleDetails(SaleHeader header) {
        Intent intent = new Intent(this, SaleDetailsActivity.class);
        // Utilise l'ID Long de la vente (inchangé)
        intent.putExtra("sale_id", header.getId());
        startActivity(intent);
    }

    private void showUserMenu(View view) {
        androidx.appcompat.widget.PopupMenu popupMenu =
                new androidx.appcompat.widget.PopupMenu(this, view);
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

    @Override
    protected void onResume() {
        super.onResume();
        // Recharge et rafraîchit la liste des ventes
        loadSalesData();
        if (saleAdapter != null && salesList != null) {
            // Si l'adaptateur existe, on met à jour sa liste source et on notifie
            // Idéalement, l'adaptateur devrait avoir une méthode updateList(newList)
            // Ici, on recrée l'adaptateur ou on notifie si la référence de liste est partagée
            if (salesList.isEmpty()) {
                Toast.makeText(this, "Aucune vente enregistrée", Toast.LENGTH_SHORT).show();
            }
            // Pour simplifier, on recrée souvent l'adaptateur ou on met à jour les données internes
            saleAdapter = new SaleHeaderAdapter(this, salesList, this::openSaleDetails);
            recyclerSales.setAdapter(saleAdapter);
        }
    }
}