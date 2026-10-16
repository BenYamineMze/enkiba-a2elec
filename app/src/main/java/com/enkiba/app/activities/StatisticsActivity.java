package com.enkiba.app.activities;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.enkiba.app.R;
import com.enkiba.app.ui.products.ProductFormActivity;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class StatisticsActivity extends BaseActivity {

    // [MODIFICATION] Ajout de l'ID et du Prix pour le popup
    class ProductStockInfo {
        String id;
        String name, category;
        double price;
        int quantity, sold;
        int total, percentage;
        int lowStockThreshold;

        ProductStockInfo(String id, String name, String category, int quantity, int sold, int initialStock, int lowStockThreshold, double price) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.sold = sold;
            this.lowStockThreshold = lowStockThreshold;
            this.price = price;

            this.total = (initialStock > 0) ? initialStock : quantity;

            if (this.total > 0) {
                this.percentage = (quantity * 100) / this.total;
            } else {
                this.percentage = 0;
            }

            if (this.percentage > 100) this.percentage = 100;
        }
    }

    PieChart pieChart;
    TextView tvTotalProducts, tvTotalQuantity;

    private ProductDao productDao;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        sessionManager = new SessionManager(this);
        this.productDao = new ProductDao(this);

        pieChart = findViewById(R.id.pieChart);
        tvTotalProducts = findViewById(R.id.tv_total_products);
        tvTotalQuantity = findViewById(R.id.tv_total_quantity);

        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Statistiques du Stock");
        imgHeaderIcon.setImageResource(R.drawable.ic_stats);

        imgUser.setOnClickListener(this::showUserMenu);

        // Les données sont chargées dans onResume pour être toujours à jour
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
        bottomNav.getMenu().findItem(R.id.nav_stats).setChecked(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les données à chaque fois qu'on revient sur la page (ex: après modification)
        loadStatistics();
        loadProductDetails();
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

    private void loadStatistics() {
        List<Product> products = productDao.getAllProducts();

        int totalProducts = products.size();
        int totalQuantityRemaining = 0;
        int totalQuantitySold = 0;

        for (Product p : products) {
            totalQuantityRemaining += p.getQuantity();
            totalQuantitySold += p.getSoldQuantity();
        }

        tvTotalProducts.setText("Total produits : " + totalProducts);
        tvTotalQuantity.setText("Quantité totale : " + totalQuantityRemaining);

        setupPieChart(totalQuantityRemaining, totalQuantitySold);
    }

    private void setupPieChart(int remaining, int sold) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(remaining, "Stock restant"));
        entries.add(new PieEntry(sold, "Vendu (Historique)"));

        PieDataSet pieDataSet = new PieDataSet(entries, "");
        pieDataSet.setColors(ColorTemplate.PASTEL_COLORS);
        pieDataSet.setValueTextSize(14f);

        PieData pieData = new PieData(pieDataSet);
        pieChart.setData(pieData);

        pieChart.setCenterText("Inventaire");
        pieChart.setCenterTextSize(16f);
        pieChart.invalidate();
    }

    private void loadProductDetails() {
        LinearLayout container = findViewById(R.id.container_stats);
        container.removeAllViews();

        ArrayList<ProductStockInfo> productList = new ArrayList<>();

        // Données étendues pour le popup
        for (Product p : productDao.getAllProducts()) {
            productList.add(new ProductStockInfo(p.getId(), p.getName(), p.getCategory(), p.getQuantity(),
                    p.getSoldQuantity(), p.getInitial_stock(), p.getLowStockThreshold(), p.getPrice()));
        }

        // Tri par pourcentage critique
        productList.sort((a, b) -> Integer.compare(a.percentage, b.percentage));

        for (ProductStockInfo info : productList) {
            View row = getLayoutInflater().inflate(R.layout.item_stat_row, container, false);

            TextView tvName = row.findViewById(R.id.tv_product_name);
            TextView tvCategory = row.findViewById(R.id.tv_product_category);
            TextView tvStock = row.findViewById(R.id.tv_stock_info);
            ProgressBar progressBar = row.findViewById(R.id.progress_stock);
            ImageView imgAlert = row.findViewById(R.id.img_alert);

            tvName.setText(info.name);
            tvCategory.setText(info.category);
            tvStock.setText("Restant : " + info.quantity + " / " + info.total + " (" + info.percentage + "%)");

            ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", 0, info.percentage);
            animation.setDuration(800);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();

            int color;
            if (info.percentage < 40) {
                color = ContextCompat.getColor(this, android.R.color.holo_red_dark);
            } else if (info.percentage < 60) {
                color = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
            } else {
                color = ContextCompat.getColor(this, android.R.color.holo_green_dark);
            }

            progressBar.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            tvName.setTextColor(color);
            imgAlert.setVisibility(info.quantity <= info.lowStockThreshold ? View.VISIBLE : View.GONE);

            // [NOUVEAU] Clic sur la ligne pour ouvrir le détail
            row.setOnClickListener(v -> showProductDetailsDialog(info));

            container.addView(row);
        }
    }

    // [NOUVEAU] Affichage du popup de détail avec option de modification
    private void showProductDetailsDialog(ProductStockInfo info) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(info.name);

        DecimalFormat df = new DecimalFormat("#,###.##");
        String formattedPrice = df.format(info.price);

        StringBuilder sb = new StringBuilder();
        sb.append("Catégorie : ").append(info.category).append("\n\n");
        sb.append("Prix : ").append(formattedPrice).append(" KMF\n");
        sb.append("Quantité restante : ").append(info.quantity).append("\n");
        sb.append("Quantité vendue : ").append(info.sold).append("\n");
        sb.append("Stock Initial : ").append(info.total).append("\n");

        if (info.quantity <= info.lowStockThreshold) {
            sb.append("\n⚠️ STOCK CRITIQUE !");
        }

        builder.setMessage(sb.toString());

        // Bouton Modifier : formulaire produit (Compose)
        builder.setPositiveButton("Modifier", (dialog, which) -> {
            Intent intent = new Intent(StatisticsActivity.this, ProductFormActivity.class);
            intent.putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, info.id);
            startActivity(intent);
        });

        builder.setNegativeButton("Fermer", null);
        builder.show();
    }
}