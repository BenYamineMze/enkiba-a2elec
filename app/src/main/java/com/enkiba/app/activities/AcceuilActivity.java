package com.enkiba.app.activities;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.utils.CurrencyUtils;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AcceuilActivity extends BaseActivity {

    PieChart pieChart;
    TextView txtStockValue, txtStockMoneyValue, txtVirement, txtEspece, txtArgentStock, txtArgentBenefice;
    TextView txtPurchaseTransfer, txtPurchaseCash;

    private ProductDao productDao;
    private SaleDao saleDao;
    private SessionManager sessionManagerLocal;
    private SynchronizationManager syncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        syncManager = new SynchronizationManager(this);
        sessionManagerLocal = new SessionManager(this);
        this.productDao = new ProductDao(this);
        this.saleDao = new SaleDao(this);
        syncManager.startSynchronization();

        initHeaderAndUserInfo();
        initDashboardCards();
        loadStatistics();
        setupNavigation();
    }

    private void initHeaderAndUserInfo() {
        TextView nameuser = findViewById(R.id.userName);
        String username = sessionManagerLocal.getUsername();
        ImageView avatar = findViewById(R.id.avatar);
        avatar.setOnClickListener(this::showUserMenu);
        if (username != null) nameuser.setText(username + " 👋");

        // Initialisation des TextViews principaux
        pieChart = findViewById(R.id.pieChart);
        txtStockValue = findViewById(R.id.txtStockValue);
        txtStockMoneyValue = findViewById(R.id.txtStockMoneyValue);

        txtPurchaseTransfer = findViewById(R.id.txtPurchaseTransfer);
        txtPurchaseCash = findViewById(R.id.txtPurchaseCash);

        txtVirement = findViewById(R.id.txtVirement);
        txtEspece = findViewById(R.id.txtEspece);
        txtArgentStock = findViewById(R.id.txtArgentStock);
        txtArgentBenefice = findViewById(R.id.txtArgentBenefice);
    }

    private void initDashboardCards() {
        // Carte 1 : Reste Stock (Vert) -> Top 5 Stock
        CardView cardStockRemaining = findViewById(R.id.cardStockRemaining);
        if (cardStockRemaining != null) cardStockRemaining.setOnClickListener(v -> showTopStockPopup());

        // Carte 2 : Rupture Stock (Rouge) -> Low Stock
        CardView cardLowStock = findViewById(R.id.cardLowStock);
        if (cardLowStock != null) cardLowStock.setOnClickListener(v -> showLowStockPopup());

        // Carte 3 : Achats Virement (Bleu)
        CardView cardPurchaseTransfer = findViewById(R.id.cardPurchaseTransfer);
        if (cardPurchaseTransfer != null) cardPurchaseTransfer.setOnClickListener(v -> showTransferSalesPopup());

        // Carte 4 : Achats Espèces (Vert Foncé)
        CardView cardPurchaseCash = findViewById(R.id.cardPurchaseCash);
        if (cardPurchaseCash != null) cardPurchaseCash.setOnClickListener(v -> showCashSalesPopup());

        // Carte 5 : Gains Stock (Jaune)
        CardView cardStockGains = findViewById(R.id.cardStockGains);
        if (cardStockGains != null) cardStockGains.setOnClickListener(v -> showStockGainsPopup());

        // Carte 6 : Bénéfices (Bleu Foncé)
        CardView cardBenefits = findViewById(R.id.cardBenefits);
        if (cardBenefits != null) cardBenefits.setOnClickListener(v -> showBenefitsPopup());

        // Gestion Admin/User
        String role = sessionManagerLocal.getRole();
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        LinearLayout blocAchats = findViewById(R.id.blocAchats);
        LinearLayout blocGains = findViewById(R.id.blocGains);

        if (isAdmin) {
            blocAchats.setVisibility(View.VISIBLE);
            blocGains.setVisibility(View.VISIBLE);
        } else {
            blocAchats.setVisibility(View.GONE);
            blocGains.setVisibility(View.GONE);
        }
    }

    // --- 1. Top Stock (Top 5) ---
    private void showTopStockPopup() {
        List<Product> allProducts = productDao.getAllProducts();
        Collections.sort(allProducts, (p1, p2) -> Integer.compare(p2.getQuantity(), p1.getQuantity()));
        List<Product> topList = allProducts.subList(0, Math.min(allProducts.size(), 5));

        showGenericListPopup("📦 Top 5 Stock (Dormant)", topList, (holder, item) -> {
            Product p = (Product) item;
            holder.setText(R.id.tvMainText, p.getName());
            holder.setText(R.id.tvSubText, p.getCategory());
            holder.setText(R.id.tvValue, p.getQuantity() + " u.");
            holder.setTextColor(R.id.tvValue, getColor(R.color.green_dark));
        });
    }

    // --- 2. Low Stock ---
    private void showLowStockPopup() {
        List<Product> allProducts = productDao.getAllProducts();
        List<Product> lowStockList = new ArrayList<>();
        for (Product p : allProducts) {
            int threshold = p.getLowStockThreshold() > 0 ? p.getLowStockThreshold() : 5;
            if (p.getQuantity() <= threshold) lowStockList.add(p);
        }

        showGenericListPopup("⚠️ Rupture de Stock", lowStockList, (holder, item) -> {
            Product p = (Product) item;
            holder.setText(R.id.tvMainText, p.getName());
            holder.setText(R.id.tvSubText, p.getCategory());
            holder.setText(R.id.tvValue, p.getQuantity() + " rest.");
            holder.setTextColor(R.id.tvValue, getColor(R.color.red_dark));
        });
    }

    // --- 3. Ventes Virement ---
    private void showTransferSalesPopup() {
        List<SaleHeader> sales = saleDao.getRecentSalesByPayment("virement", 10);
        showGenericListPopup("💳 Derniers Virements", sales, (holder, item) -> {
            SaleHeader s = (SaleHeader) item;
            holder.setText(R.id.tvMainText, "Vente #" + s.getShortRef());
            // UTILISATION DU FORMATTER
            holder.setText(R.id.tvSubText, CurrencyUtils.formatSaleDate(s.getSaleDate()));
            holder.setText(R.id.tvValue, CurrencyUtils.formatKMF(s.getTotalPrice()));
            holder.setTextColor(R.id.tvValue, getColor(R.color.blue_dark));
        });
    }

    // --- 4. Ventes Espèces ---
    private void showCashSalesPopup() {
        List<SaleHeader> sales = saleDao.getRecentSalesByPayment("espece", 10);
        showGenericListPopup("💵 Dernières Espèces", sales, (holder, item) -> {
            SaleHeader s = (SaleHeader) item;
            holder.setText(R.id.tvMainText, "Vente #" + s.getShortRef());
            // UTILISATION DU FORMATTER
            holder.setText(R.id.tvSubText, CurrencyUtils.formatSaleDate(s.getSaleDate()));
            holder.setText(R.id.tvValue, CurrencyUtils.formatKMF(s.getTotalPrice()));
            holder.setTextColor(R.id.tvValue, getColor(R.color.green_dark_2));
        });
    }

    // --- 5. Gains Stock ---
    private void showStockGainsPopup() {
        List<Product> allProducts = productDao.getAllProducts();
        Collections.sort(allProducts, (p1, p2) -> Double.compare(p2.getQuantity() * p2.getPrice(), p1.getQuantity() * p1.getPrice()));
        List<Product> topValueList = allProducts.subList(0, Math.min(allProducts.size(), 5));

        showGenericListPopup("💰 Valeur Stock (Top 5)", topValueList, (holder, item) -> {
            Product p = (Product) item;
            double totalVal = p.getQuantity() * p.getPrice();
            holder.setText(R.id.tvMainText, p.getName());
            holder.setText(R.id.tvSubText, p.getQuantity() + " unités");
            holder.setText(R.id.tvValue, CurrencyUtils.formatKMF(totalVal));
            holder.setTextColor(R.id.tvValue, getColor(R.color.yellow_dark));
        });
    }

    // --- 6. Bénéfices ---
    private void showBenefitsPopup() {
        List<Product> allProducts = productDao.getAllProducts();
        Collections.sort(allProducts, (p1, p2) -> Integer.compare(p2.getSoldQuantity(), p1.getSoldQuantity()));
        List<Product> bestSellers = allProducts.subList(0, Math.min(allProducts.size(), 5));

        showGenericListPopup("🏆 Meilleures Ventes (Top 5)", bestSellers, (holder, item) -> {
            Product p = (Product) item;
            holder.setText(R.id.tvMainText, p.getName());
            holder.setText(R.id.tvSubText, p.getCategory());
            holder.setText(R.id.tvValue, p.getSoldQuantity() + " vendus");
            holder.setTextColor(R.id.tvValue, getColor(R.color.blue_dark_2));
        });
    }

    // --- POPUP BUILDER GÉNÉRIQUE ---

    interface ItemBinder {
        void bind(ViewHolder holder, Object item);
    }

    static class ViewHolder {
        View itemView;
        ViewHolder(View v) { itemView = v; }
        void setText(int id, String text) { ((TextView)itemView.findViewById(id)).setText(text); }
        void setTextColor(int id, int color) { ((TextView)itemView.findViewById(id)).setTextColor(color); }
    }

    private void showGenericListPopup(String title, List<?> dataList, ItemBinder binder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyMessage);
        RecyclerView recycler = dialogView.findViewById(R.id.recyclerList);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDialog);

        tvTitle.setText(title);

        if (dataList == null || dataList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_row, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }
                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    binder.bind(new ViewHolder(holder.itemView), dataList.get(position));
                }
                @Override public int getItemCount() { return dataList.size(); }
            });
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
        bottomNav.getMenu().findItem(R.id.nav_home).setChecked(true);
    }

    private void showUserMenu(View v) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
        popup.getMenuInflater().inflate(R.menu.user_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                sessionManagerLocal.logout();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadStatistics() {
        List<Product> products = productDao.getAllProducts();
        int totalProducts = 0;
        int totalQuantityRemaining = 0;
        int totalQuantitySold = 0;

        if (products != null) {
            totalProducts = products.size();
            for (Product p : products) {
                totalQuantityRemaining += p.getQuantity();
                totalQuantitySold += p.getSoldQuantity();
            }
        }

        double totalStockValue = productDao.getTotalStockValue();
        double totalSalesMoney = saleDao.getTotalSalesMoney();
        int lowStockCount = productDao.getLowStockCount();

        double totalVirement = saleDao.getTotalAchatsParVirement();
        double totalCash = saleDao.getTotalAchatsEnEspeces();

        // Affichage
        txtStockValue.setText(String.valueOf(totalProducts));
        txtStockMoneyValue.setText(CurrencyUtils.formatKMF(totalStockValue));
        ((TextView)findViewById(R.id.txtLowStockValue)).setText(lowStockCount + " articles");
        ((TextView)findViewById(R.id.txtBestSellerValue)).setText(CurrencyUtils.formatKMF(totalSalesMoney));

        txtPurchaseTransfer.setText(CurrencyUtils.formatKMF(totalVirement));
        txtPurchaseCash.setText(CurrencyUtils.formatKMF(totalCash));

        txtVirement.setText(CurrencyUtils.kmfToEurFormatted(totalVirement));
        txtEspece.setText(CurrencyUtils.kmfToEurFormatted(totalCash));
        txtArgentStock.setText(CurrencyUtils.kmfToEurFormatted(totalStockValue));
        txtArgentBenefice.setText(CurrencyUtils.kmfToEurFormatted(totalSalesMoney));

        setupPieChart(totalQuantityRemaining, totalQuantitySold, lowStockCount);
    }

    private void setupPieChart(int remaining, int sold, int lowStockCount) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(remaining, "Stock restant"));
        entries.add(new PieEntry(sold, "Vendu"));
        entries.add(new PieEntry(lowStockCount, "Stock critique"));

        PieDataSet pieDataSet = new PieDataSet(entries, "");
        pieDataSet.setColors(ColorTemplate.PASTEL_COLORS);
        pieDataSet.setValueTextSize(14f);

        PieData pieData = new PieData(pieDataSet);
        pieChart.setData(pieData);
        pieChart.setCenterText("Inventaire");
        pieChart.setCenterTextSize(16f);
        pieChart.getDescription().setEnabled(false);
        pieChart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
        sessionManagerLocal.updateLastActiveTime();
    }
}