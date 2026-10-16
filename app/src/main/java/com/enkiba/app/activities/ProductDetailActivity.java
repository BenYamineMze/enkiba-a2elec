package com.enkiba.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.enkiba.app.R;
import com.enkiba.app.ui.sale.SaleActivity;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.QrUtils;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ProductDetailActivity extends BaseActivity {

    private TextView txtName, txtCategory, txtQuantity, txtPrice, txtDescription, txtValue, txtThreshold, txtSold;
    private ImageView imgQr;

    private FloatingActionButton btnSell, btnEdit;

    private ProductDao productDao;

    private Product product;

    // [MODIFICATION UUID] L'ID est maintenant un String (UUID)
    private String productId;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        sessionManager = new SessionManager(this);

        // Initialisation du DAO
        this.productDao = new ProductDao(this);

        // [MODIFICATION UUID] Récupération de l’ID du produit (String)
        // On gère "id" (standard) et "PRODUCT_ID" (parfois utilisé dans d'autres activités)
        if (getIntent().hasExtra("id")) {
            productId = getIntent().getStringExtra("id");
        } else if (getIntent().hasExtra("PRODUCT_ID")) {
            productId = getIntent().getStringExtra("PRODUCT_ID");
        }

        // Vérification de sécurité
        if (productId == null) {
            Toast.makeText(this, "Erreur : ID Produit manquant", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Appel à ProductDao pour la première lecture (attend un String)
        product = productDao.getProductById(productId);

        // HEADER
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        if (tvHeaderTitle != null) tvHeaderTitle.setText("Détails du Produit");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_box);
        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);

        initViews();
        loadData();

        String role = sessionManager.getRole();
        boolean isAdmin = "admin".equalsIgnoreCase(role);

        if (isAdmin) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(this, EditProductActivity.class);
                intent.putExtra("id", productId); // Envoi de l'UUID
                startActivity(intent);
            });
        } else {
            btnEdit.setVisibility(View.GONE);
        }

        btnSell.setOnClickListener(v -> {
            // Caisse Compose : la saisie de cet article s'ouvre directement
            Intent intent = new Intent(this, SaleActivity.class);
            intent.putExtra(SaleActivity.EXTRA_PRODUCT_ID, productId);
            startActivity(intent);
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
        if (bottomNav != null) {
            bottomNav.getMenu().findItem(R.id.nav_inventory).setChecked(true);
        }
    }

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

    private void initViews() {
        txtName = findViewById(R.id.txtName);
        txtCategory = findViewById(R.id.txtCategory);
        txtDescription = findViewById(R.id.txtDescription);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtPrice = findViewById(R.id.txtPrice);
        txtValue = findViewById(R.id.txtStockValue);
        txtThreshold = findViewById(R.id.txtThreshold);
        txtSold = findViewById(R.id.txtSold);
        imgQr = findViewById(R.id.imgQr);

        btnSell = findViewById(R.id.btnSell);
        btnEdit = findViewById(R.id.btnEditProduct);
    }

    private void loadData() {
        if (product == null) {
            Toast.makeText(this, "Produit introuvable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔳 Afficher le QR Code
        if (product.getQr_code() != null && !product.getQr_code().isEmpty()) {
            try {
                Bitmap qrBmp = QrUtils.generateQrBitmap(product.getQr_code());
                imgQr.setImageBitmap(qrBmp);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Erreur lors du chargement du QR code", Toast.LENGTH_SHORT).show();
            }
        }

        txtName.setText(product.getName());
        txtCategory.setText("Catégorie : " + product.getCategory());
        txtDescription.setText(product.getDescription());
        txtQuantity.setText("Quantité disponible : " + product.getQuantity());
        txtSold.setText("Quantité vendue : " + product.getSoldQuantity());
        txtPrice.setText("Prix unitaire : " + product.getPrice() + " KMF");
        txtThreshold.setText("Seuil d’alerte : " + product.getLowStockThreshold());

        double value = product.getPrice() * product.getQuantity();
        txtValue.setText("Valeur du stock : " + value + " KMF");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recharger les données via ProductDao avec l'UUID
        if (productId != null) {
            product = productDao.getProductById(productId);
            loadData();
        }
    }
}