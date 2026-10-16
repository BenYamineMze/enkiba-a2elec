package com.enkiba.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.HistoryDao;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.SynchronizationManager;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.UUID;

public class AddProductActivity extends BaseActivity {

    private Spinner spinnerCategory;
    private EditText edtArticleName, edtQuantity, edtPrice, edtDescription;
    private Button btnAddArticle;

    private ProductDao productDao;
    private HistoryDao historyDao;
    private SessionManager sessionManager;
    private SynchronizationManager syncManager;

    // Variable de verrouillage pour empêcher les doubles clics
    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialisation
        sessionManager = new SessionManager(this);
        syncManager = new SynchronizationManager(this);
        productDao = new ProductDao(this);
        historyDao = new HistoryDao(this);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès refusé.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_add_product);

        // Récupération des vues
        edtArticleName = findViewById(R.id.edtArticleName);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        edtQuantity = findViewById(R.id.edtQuantity);
        edtPrice = findViewById(R.id.edtPrice);
        edtDescription = findViewById(R.id.edtDescription);
        btnAddArticle = findViewById(R.id.btnAddArticle);

        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Ajouter un article");
        imgHeaderIcon.setImageResource(R.drawable.ic_add_box);

        imgUser.setOnClickListener(this::showUserMenu);

        // Configuration Spinner
        String[] categories = {
                "Appareillage", "Câbles & Fils Électriques", "Éclairage",
                "Tableaux & Protections", "Domotique & Smart Home", "Outils",
                "Sécurité", "Boîtiers", "Autre"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // 2. Logique d'Ajout
        btnAddArticle.setOnClickListener(v -> saveProduct());

        // 3. Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    private void saveProduct() {
        // Bloquer si déjà en cours de sauvegarde pour éviter les doublons
        if (isSaving) return;

        String name = edtArticleName.getText().toString().trim();
        String qteText = edtQuantity.getText().toString().trim();
        String priceText = edtPrice.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem() != null ?
                spinnerCategory.getSelectedItem().toString() : "Autre";
        String description = edtDescription.getText().toString().trim();

        if (name.isEmpty() || qteText.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verrouiller le processus
        isSaving = true;
        btnAddArticle.setEnabled(false);
        btnAddArticle.setText("Enregistrement...");

        try {
            int quantity = Integer.parseInt(qteText);
            double price = Double.parseDouble(priceText);

            // A. Génération d'un QR Code Stable
            String uniqueID = UUID.randomUUID().toString().substring(0, 8); // Version courte
            String stableQrCode = "PROD-" + uniqueID;

            // B. Création de l'objet Produit
            // [NOTE] Le constructeur de Product génère maintenant automatiquement l'ID (UUID)
            Product product = new Product(name, category, quantity, price, description, 5, quantity);

            // CORRECTION: On définit le QR Code sur l'objet avant insertion
            product.setQr_code(stableQrCode);

            // C. Insertion en base
            // [MODIFICATION UUID] Le type de retour est maintenant String (l'UUID) ou null
            String result = productDao.insertProduct(product);

            if (result != null) {
                // L'ID est déjà défini dans l'objet product, mais 'result' contient la confirmation
                // product.setId(result); // Redondant avec UUID, mais inoffensif

                // 🚨 SAFEGUARD :
                // On s'assure que le QR Code est bien persistant
                product.setQr_code(stableQrCode);
                productDao.updateProduct(product);

                Log.d("AddProduct", "Produit sauvegardé. ID=" + result + ", QR=" + stableQrCode);

                // Historique
                // [MODIFICATION UUID] L'ID passé est maintenant un String
                historyDao.insertHistory(
                        result,
                        sessionManager.getUserId(),
                        "Ajout",
                        "-",
                        "Stock initial: " + quantity
                );

                Toast.makeText(this, "Produit ajouté avec succès ✅", Toast.LENGTH_SHORT).show();

                // ---------------------------------------------------------
                // AJOUT : Lancement automatique de la synchronisation
                // ---------------------------------------------------------
                try {
                    // Utilisation de votre SynchronizationManager
                    syncManager.startSynchronization();
                    Log.d("AddProduct", "Synchronisation déclenchée après ajout.");
                } catch (Exception e) {
                    Log.e("AddProduct", "Impossible de lancer la synchro auto", e);
                }
                // ---------------------------------------------------------

                resetForm();
            } else {
                Toast.makeText(this, "Erreur lors de l'ajout en base", Toast.LENGTH_SHORT).show();
            }

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Format de nombre invalide", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("AddProduct", "Erreur inattendue", e);
            Toast.makeText(this, "Une erreur est survenue", Toast.LENGTH_SHORT).show();
        } finally {
            // Délai de sécurité plus long (1.5s) avant de réactiver le bouton
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isSaving = false;
                if (btnAddArticle != null) {
                    btnAddArticle.setEnabled(true);
                    btnAddArticle.setText("Ajouter l'article");
                }
            }, 1500);
        }
    }

    private void resetForm() {
        edtArticleName.setText("");
        edtQuantity.setText("");
        edtPrice.setText("");
        edtDescription.setText("");
        if (spinnerCategory.getAdapter() != null && spinnerCategory.getAdapter().getCount() > 0) {
            spinnerCategory.setSelection(0);
        }
        edtArticleName.requestFocus();
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