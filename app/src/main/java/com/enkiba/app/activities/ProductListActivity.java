package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.enkiba.app.R;
import com.enkiba.app.adapters.ProductAdapter;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductListActivity extends BaseActivity {

    private RecyclerView recyclerProducts;
    private ProductDao productDao;
    private ProductAdapter adapter;
    private List<Product> productList;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText edtSearch;
    private SessionManager sessionManagerLocal;
    private SynchronizationManager syncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        // 1. Init Utils & DAO
        productDao = new ProductDao(this);
        sessionManagerLocal = new SessionManager(this);
        syncManager = new SynchronizationManager(this);

        // 2. View Bindings
        recyclerProducts = findViewById(R.id.recyclerProducts);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        edtSearch = findViewById(R.id.edtSearch);

        FloatingActionButton btnAdd = findViewById(R.id.btnAddProduct);
        FloatingActionButton btnQr = findViewById(R.id.btnShowQrList);

        // 3. Setup Header
        setupHeader();

        // 4. Setup RecyclerView
        recyclerProducts.setLayoutManager(new LinearLayoutManager(this));

        // 5. Setup SwipeRefresh (NOUVEAU)
        setupSwipeRefresh();

        // 6. Listeners
        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddProductActivity.class)));
        btnQr.setOnClickListener(v -> startActivity(new Intent(this, QrListActivity.class)));
        //btnQr.setOnClickListener(v -> Toast.makeText(this, "Liste QR Code (À implémenter)", Toast.LENGTH_SHORT).show());

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 7. Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            try {
                bottomNav.getMenu().findItem(R.id.nav_inventory).setChecked(true);
            } catch (Exception e) { e.printStackTrace(); }
        }

        // 8. Chargement initial
        loadProducts();
    }

    private void setupHeader() {
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        if (tvHeaderTitle != null) tvHeaderTitle.setText("Liste des Articles");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_list);
        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);
    }

    private void setupSwipeRefresh() {
        // Configuration des couleurs du petit cercle de chargement
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_green_dark, R.color.button_secondary_blue);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Lancement de la synchro avec callback
            syncManager.startSynchronization(() -> {
                // On revient sur le thread principal pour toucher à l'UI
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadProducts(); // Recharger la liste depuis la BDD locale
                    swipeRefreshLayout.setRefreshing(false); // Arrêter l'animation
                    Toast.makeText(ProductListActivity.this, "Liste actualisée 🔄", Toast.LENGTH_SHORT).show();
                });
            });
        });
    }

    private void loadProducts() {
        productList = productDao.getAllProducts();
        if (productList == null) productList = new ArrayList<>();

        // CORRECTION ERREUR COMPILATION
        // 1. Déterminer si Admin
        boolean isAdmin = "admin".equalsIgnoreCase(sessionManagerLocal.getRole());

        // 2. Convertir List en ArrayList (pour satisfaire le constructeur)
        ArrayList<Product> arrayListProducts = new ArrayList<>(productList);

        if (adapter == null) {
            // 3. Utiliser le constructeur avec 3 arguments : Context, ArrayList, boolean
            adapter = new ProductAdapter(this, arrayListProducts, isAdmin);
            recyclerProducts.setAdapter(adapter);
        } else {
            adapter = new ProductAdapter(this, arrayListProducts, isAdmin);
            recyclerProducts.setAdapter(adapter);
        }
    }

    private void filterProducts(String query) {
        if (adapter != null) {
            adapter.filter().filter(query);
        }
    }

    private void showUserMenu(View view) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                sessionManagerLocal.logout();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts(); // Recharger au retour sur la page pour voir les ajouts récents
    }
}