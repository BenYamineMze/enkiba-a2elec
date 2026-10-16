package com.enkiba.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.ProductHistoryAdapter;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.modelsDao.HistoryDao; // 🚨 NOUVEAU 🚨
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

// L'activité hérite de BaseActivity pour la gestion de session
public class ProductHistoryActivity extends BaseActivity {

    RecyclerView recyclerProductHistory;
    TextView txtEmptyProduct;

    private HistoryDao historyDao; // 🚨 NOUVEAU 🚨

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_history);

        sessionManager = new SessionManager(this);

        // 🚨 CRITIQUE : Initialisation du DAO 🚨
        this.historyDao = new HistoryDao(this);

        // HEADER
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Historique des Articles");
        imgHeaderIcon.setImageResource(R.drawable.ic_history);

        imgUser.setOnClickListener(this::showUserMenu);


        recyclerProductHistory = findViewById(R.id.recyclerProductHistory);
        txtEmptyProduct = findViewById(R.id.txtEmptyProduct);

        // La logique d'affichage est déplacée dans onResume() si vous voulez qu'elle se rafraîchisse.
        loadHistoryData();


        // BARRE DE NAVIGATION
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    // Méthode pour charger et afficher les données d'historique
    private void loadHistoryData() {
        // CORRECTION DE L'ERREUR : Appel à HistoryDao pour récupérer la liste
        ArrayList<ProductHistory> list = (ArrayList<ProductHistory>) historyDao.getProductHistory();

        if (list.isEmpty()) {
            txtEmptyProduct.setVisibility(View.VISIBLE);
            recyclerProductHistory.setVisibility(View.GONE);
        } else {
            txtEmptyProduct.setVisibility(View.GONE);
            recyclerProductHistory.setVisibility(View.VISIBLE);

            ProductHistoryAdapter adapter = new ProductHistoryAdapter(ProductHistoryActivity.this, list);

            recyclerProductHistory.setLayoutManager(new LinearLayoutManager(this));
            recyclerProductHistory.setAdapter(adapter);
        }
    }


    // Ajout de la méthode showUserMenu pour la déconnexion
    private void showUserMenu(View view) {
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

    @Override
    protected void onResume() {
        super.onResume();
        loadHistoryData(); // Recharger les données si l'activité revient au premier plan
    }
}