package com.enkiba.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.StockMovement;
import com.enkiba.app.modelsDao.HistoryDao;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class EditProductActivity extends BaseActivity {

    private static final String TAG = "EditProductActivity";

    // Utilisation d'EditText générique pour éviter les erreurs de cast si le XML diffère
    private EditText edtName, edtQuantity, edtPrice, edtDescription, edtThreshold, edtCategory;
    private Button btnUpdate;

    private ProductDao productDao;
    private HistoryDao historyDao;

    // [MODIFICATION UUID] L'ID est maintenant un String (UUID)
    private String productId;
    private Product product;

    // Variables pour stocker l'état avant modif (pour l'historique)
    private String oldName = "";
    private int oldQuantity = 0;

    private SessionManager sessionManager;
    private SynchronizationManager syncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        sessionManager = new SessionManager(this);
        syncManager = new SynchronizationManager(this);
        this.productDao = new ProductDao(this);
        this.historyDao = new HistoryDao(this);

        // Configuration Header
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        if (tvHeaderTitle != null) tvHeaderTitle.setText("Modifier un article");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_edit);
        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);

        // 🚨 Récupération de l'ID compatible (UUID String)
        if (getIntent().hasExtra("PRODUCT_ID")) {
            productId = getIntent().getStringExtra("PRODUCT_ID");
        } else if (getIntent().hasExtra("id")) {
            productId = getIntent().getStringExtra("id");
        }

        Log.d(TAG, "onCreate: ID reçu = " + productId);

        initViews();

        if (productId != null && !productId.isEmpty()) {
            loadProduct();
        } else {
            Toast.makeText(this, "Erreur : ID Produit manquant", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> updateProduct());
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
        }
    }

    private void initViews() {
        // Tentative de trouver les vues avec les IDs probables
        edtName = findViewById(R.id.editName);
        if (edtName == null) edtName = findViewById(R.id.edtArticleName);

        edtCategory = findViewById(R.id.editCategory);
        if (edtCategory == null) {
            View v = findViewById(R.id.spinnerCategory);
            if (v instanceof EditText) edtCategory = (EditText) v;
        }

        edtQuantity = findViewById(R.id.editQuantity);
        if (edtQuantity == null) edtQuantity = findViewById(R.id.edtQuantity);

        edtPrice = findViewById(R.id.editPrice);
        if (edtPrice == null) edtPrice = findViewById(R.id.edtPrice);

        edtDescription = findViewById(R.id.editDescription);
        if (edtDescription == null) edtDescription = findViewById(R.id.edtDescription);

        edtThreshold = findViewById(R.id.editThreshold);

        btnUpdate = findViewById(R.id.btnUpdateProduct);
        if (btnUpdate == null) btnUpdate = findViewById(R.id.btnAddArticle);

        Log.d(TAG, "initViews: NameView=" + (edtName!=null) + ", PriceView=" + (edtPrice!=null));
    }

    private void loadProduct() {
        product = productDao.getProductById(productId);

        if (product == null) {
            Toast.makeText(this, "Produit introuvable en base (ID: " + productId + ")", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "loadProduct: Produit trouvé: " + product.getName());

        // Sauvegarde des anciennes valeurs pour l'historique
        oldName = product.getName();
        oldQuantity = product.getQuantity();

        // Remplissage du formulaire
        if (edtName != null) edtName.setText(product.getName());
        if (edtCategory != null) edtCategory.setText(product.getCategory());
        if (edtQuantity != null) edtQuantity.setText(String.valueOf(product.getQuantity()));

        // Gestion propre de l'affichage du prix (entier ou décimal)
        if (edtPrice != null) {
            if (product.getPrice() % 1 == 0) {
                edtPrice.setText(String.valueOf((int) product.getPrice()));
            } else {
                edtPrice.setText(String.valueOf(product.getPrice()));
            }
        }

        if (edtThreshold != null) edtThreshold.setText(String.valueOf(product.getLowStockThreshold()));

        if (edtDescription != null) {
            String desc = product.getDescription();
            if (desc != null && desc.contains("(Stock Initial Référence:")) {
                desc = desc.substring(0, desc.indexOf("(Stock Initial Référence:")).trim();
            }
            edtDescription.setText(desc);
        }
    }

    private void updateProduct() {
        // Vérification que les vues ne sont pas nulles avant de lire
        if (edtName == null || edtQuantity == null || edtPrice == null) {
            Toast.makeText(this, "Erreur interne: Champs non liés", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = edtName.getText().toString().trim();
        String category = (edtCategory != null) ? edtCategory.getText().toString().trim() : "Autre";
        String description = (edtDescription != null) ? edtDescription.getText().toString().trim() : "";
        String qtyStr = edtQuantity.getText().toString().trim();
        String priceStr = edtPrice.getText().toString().trim();
        String thresholdStr = (edtThreshold != null) ? edtThreshold.getText().toString().trim() : "5";

        if (name.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity;
        double price;
        int threshold = 5;

        try {
            quantity = Integer.parseInt(qtyStr);
            price = Double.parseDouble(priceStr);
            if (!thresholdStr.isEmpty()) {
                threshold = Integer.parseInt(thresholdStr);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valeurs numériques invalides", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mise à jour de l'objet
        product.setName(name);
        product.setCategory(category);

        // Le stock ne s'écrit pas directement : l'écart avec le stock actuel devient
        // un mouvement de stock (réassort ou ajustement), envoyé au serveur.
        // Le stock est relu en base au cas où une vente aurait eu lieu depuis l'ouverture de l'écran.
        Product fresh = productDao.getProductById(product.getId());
        int currentQuantity = (fresh != null) ? fresh.getQuantity() : product.getQuantity();
        int delta = quantity - currentQuantity;

        // On met EGALEMENT à jour le stock initial pour réinitialiser la barre de progression (100%)
        // Cela évite les décalages statistiques lors d'une correction manuelle de stock
        product.setInitial_stock(quantity);

        product.setPrice(price);
        product.setDescription(description);
        product.setLowStockThreshold(threshold);

        String userId = sessionManager.getUserId();

        boolean updated = productDao.updateProduct(product);
        if (updated && delta != 0) {
            String reason = delta > 0 ? StockMovement.REASON_RESTOCK : StockMovement.REASON_ADJUSTMENT;
            updated = productDao.applyStockMovement(product.getId(), delta, reason, userId);
        }

        if (updated) {
            // Enregistrement dans l'historique
            historyDao.insertHistory(
                    product.getId(),
                    userId,
                    "Modification",
                    oldName + " (Qte: " + oldQuantity + ")",
                    product.getName() + " (Qte: " + quantity + ")"
            );

            Toast.makeText(this, "Produit mis à jour ✅", Toast.LENGTH_SHORT).show();

            // Lancer la synchro pour envoyer les modifs au serveur
            try {
                syncManager.startSynchronization();
            } catch (Exception e) {
                e.printStackTrace();
            }

            finish(); // Retour à la liste
        } else {
            Toast.makeText(this, "Erreur lors de la mise à jour en base ❌", Toast.LENGTH_SHORT).show();
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
}