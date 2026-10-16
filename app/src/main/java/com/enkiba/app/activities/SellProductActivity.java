package com.enkiba.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.enkiba.app.R;
import com.enkiba.app.adapters.ProductAutoCompleteAdapter; // Import de l'adaptateur personnalisé
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.modelsDao.ClientDao;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.modelsDao.QuoteDao;
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.ui.CartBottomSheet;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.PDFUtils;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.ShopInfo;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SellProductActivity extends BaseActivity {

    private static final String TAG = "SellProductActivity";
    ActivityResultLauncher<ScanOptions> qrLauncher;

    // UI Components
    private AutoCompleteTextView spinnerProducts;
    private EditText etQuantitySell;
    private EditText etUnitPrice;
    private TextView tvCalculatedTotal;
    private SwitchMaterial switchNegotiatedPrice;
    private FloatingActionButton btnAddToCart;
    private ShapeableImageView btnOpenCartFab;
    private TextView txtCartBadge;
    private MaterialButton btnValidateSell;

    private MaterialButton btnGenerateQuote;
    private FloatingActionButton btnHistory;
    private CheckBox checkEditInvoice;
    private View cardInvoiceForm;
    private EditText etClientNom, etClientPrenom, etClientAddress, etClientPhone, etClientEmail;
    private SwitchMaterial switchConfirm;
    private RadioGroup radioGroupPayment;

    // Database & Logic
    private ProductDao productDao;
    private ClientDao clientDao;
    private QuoteDao quoteDao;
    private SaleDao saleDao;
    private SessionManager sessionManagerLocal;
    private SynchronizationManager syncManager;

    // Data
    private List<Product> products = new ArrayList<>();
    private final ArrayList<SaleItemRow> cartRows = new ArrayList<>();
    private Product currentSelectedProduct = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);

        sessionManagerLocal = new SessionManager(this);
        syncManager = new SynchronizationManager(this);
        productDao = new ProductDao(this);
        clientDao = new ClientDao(this);
        quoteDao = new QuoteDao(this);
        saleDao = new SaleDao(this);

        initViews();
        setupHeader();
        loadProductDropdown();
        setupListeners();
        setupNavigation();
        setupQrScanner();
        updateCartBadge();

        if (getIntent().hasExtra("id")) {
            String prodId = getIntent().getStringExtra("id");
            selectProductById(prodId);
        }
    }

    private void initViews() {
        spinnerProducts = findViewById(R.id.spinnerProducts);
        etQuantitySell = findViewById(R.id.etQuantitySell);
        etUnitPrice = findViewById(R.id.etUnitPrice);
        tvCalculatedTotal = findViewById(R.id.tvCalculatedTotal);
        switchNegotiatedPrice = findViewById(R.id.switchNegotiatedPrice);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnOpenCartFab = findViewById(R.id.btnOpenCart);
        txtCartBadge = findViewById(R.id.txtCartBadge);
        btnValidateSell = findViewById(R.id.btnValidateSell);
        btnHistory = findViewById(R.id.btnHistory);
        checkEditInvoice = findViewById(R.id.checkEditInvoice);
        cardInvoiceForm = findViewById(R.id.cardInvoiceForm);
        etClientNom = findViewById(R.id.etClientNom);
        etClientPrenom = findViewById(R.id.etClientPrenom);
        etClientAddress = findViewById(R.id.etClientAddress);
        etClientPhone = findViewById(R.id.etClientPhone);
        etClientEmail = findViewById(R.id.etClientEmail);
        switchConfirm = findViewById(R.id.switchConfirm);
        radioGroupPayment = findViewById(R.id.radioGroupPayment);
        btnGenerateQuote = findViewById(R.id.btnGenerateQuote);
    }

    private void setupHeader() {
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Vente");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_sell);
        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);
    }

    private void setupListeners() {
        btnValidateSell.setEnabled(false);
        btnValidateSell.setAlpha(0.5f);
        switchConfirm.setOnCheckedChangeListener((b, checked) -> {
            btnValidateSell.setEnabled(checked);
            btnValidateSell.setAlpha(checked ? 1f : 0.5f);
        });

        checkEditInvoice.setOnCheckedChangeListener((b, checked) ->
                cardInvoiceForm.setVisibility(checked ? View.VISIBLE : View.GONE)
        );

        btnAddToCart.setOnClickListener(v -> addToCartAction(false)); // false = ne pas valider auto
        btnOpenCartFab.setOnClickListener(v -> openCartBottomSheet());

        if (btnValidateSell != null) btnValidateSell.setOnClickListener(v -> validateSaleAction());
        // --- Écouteur pour le nouveau bouton Devis ---
        if (btnGenerateQuote != null) btnGenerateQuote.setOnClickListener(v -> generateQuoteAction());

        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, com.enkiba.app.activities.SalesHistoryActivity.class)));

        findViewById(R.id.btnScan).setOnClickListener(v -> startQrScan());

        switchNegotiatedPrice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etUnitPrice.setEnabled(isChecked);
            if (!isChecked && currentSelectedProduct != null) {
                etUnitPrice.setText(String.valueOf(currentSelectedProduct.getPrice()));
            } else if (isChecked) {
                etUnitPrice.requestFocus();
            }
        });

        TextWatcher calculationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateTotalLine(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantitySell.addTextChangedListener(calculationWatcher);
        etUnitPrice.addTextChangedListener(calculationWatcher);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            bottomNav.getMenu().findItem(R.id.nav_sale).setChecked(true);
        }
    }

    private void calculateTotalLine() {
        if (currentSelectedProduct == null) {
            if (tvCalculatedTotal != null) tvCalculatedTotal.setText("Total ligne : 0 KMF");
            return;
        }
        try {
            String qtyStr = etQuantitySell.getText().toString().trim();
            String priceStr = etUnitPrice.getText().toString().trim();
            if (qtyStr.isEmpty() || priceStr.isEmpty()) {
                if (tvCalculatedTotal != null) tvCalculatedTotal.setText("Total ligne : 0 KMF");
                return;
            }
            int qty = Integer.parseInt(qtyStr);
            double price = Double.parseDouble(priceStr);
            double total = qty * price;
            if (tvCalculatedTotal != null) {
                tvCalculatedTotal.setText(String.format("Total ligne : %.0f KMF", total));
            }
        } catch (NumberFormatException e) {
            if (tvCalculatedTotal != null) tvCalculatedTotal.setText("Total ligne : -");
        }
    }

    private void updateUIWithProduct(Product product) {
        if (product == null) return;
        currentSelectedProduct = product;
        etUnitPrice.setText(String.valueOf(product.getPrice()));
        if (etQuantitySell.getText().toString().isEmpty()) {
            etQuantitySell.setText("1");
        }
        switchNegotiatedPrice.setChecked(false);
        etUnitPrice.setEnabled(false);
        calculateTotalLine();
    }

    private void selectProductById(String productId) {
        if (products == null) return;
        for (Product p : products) {
            if (p.getId().equals(productId)) {
                spinnerProducts.setText(p.getName(), false);
                updateUIWithProduct(p);
                break;
            }
        }
    }

    // --- MISE À JOUR : Utilisation de ProductAutoCompleteAdapter ---
    private void loadProductDropdown() {
        // Chargement des données
        products = productDao.getAllProductsList();

        // Utilisation de l'adaptateur personnalisé
        ProductAutoCompleteAdapter adapter = new ProductAutoCompleteAdapter(this, products);
        spinnerProducts.setAdapter(adapter);
        spinnerProducts.setThreshold(1); // Commence à filtrer dès 1 caractère

        // GESTION DU CLIC : On récupère l'objet Product direct
        spinnerProducts.setOnItemClickListener((parent, view, position, id) -> {
            Product selectedP = (Product) parent.getItemAtPosition(position);
            updateUIWithProduct(selectedP);

            // Cacher le clavier
            View v = this.getCurrentFocus();
            if (v != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });

        // Gestion de l'effacement du texte
        spinnerProducts.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Si l'utilisateur vide le champ, on réinitialise la sélection
                if (s.toString().isEmpty()) {
                    currentSelectedProduct = null;
                    etUnitPrice.setText("");
                    calculateTotalLine();
                }
            }
        });

        spinnerProducts.setOnClickListener(v -> spinnerProducts.showDropDown());
    }

    // --- ANCIENNE MÉTHODE SUPPRIMÉE : findProductByLabel n'est plus nécessaire ---
    /*
    private void findProductByLabel(String label) { ... }
    */

    private void addToCartAction(boolean autoProcess) {
        String qtyText = etQuantitySell.getText().toString().trim();
        String priceText = etUnitPrice.getText().toString().trim();
        Product selP = currentSelectedProduct;

        // Fallback recherche si l'utilisateur n'a pas cliqué sur la liste mais a scanné ou tapé exact
        if (selP == null) {
            String typedText = spinnerProducts.getText().toString().trim();
            for (Product p : products) {
                // Recherche plus robuste insensibles à la casse
                if (p.getName().equalsIgnoreCase(typedText)) {
                    selP = p;
                    break;
                }
            }
        }

        if (selP == null) {
            Toast.makeText(this, "Produit introuvable.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qtyText.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(this, "Champs manquants", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty;
        double finalPrice;
        try {
            qty = Integer.parseInt(qtyText);
            finalPrice = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valeurs invalides", Toast.LENGTH_SHORT).show();
            return;
        }

        if (qty <= 0 || qty > selP.getQuantity()) {
            Toast.makeText(this, "Quantité invalide (Stock: " + selP.getQuantity() + ")", Toast.LENGTH_SHORT).show();
            return;
        }
        if (finalPrice < 0) {
            Toast.makeText(this, "Prix négatif invalide", Toast.LENGTH_SHORT).show();
            return;
        }

        final Product productToAdd = selP;
        final int quantityToAdd = qty;
        final double priceToAdd = finalPrice;
        final boolean isAutoProcess = autoProcess;

        double catalogPrice = productToAdd.getPrice();
        if (priceToAdd < catalogPrice) {
            double difference = catalogPrice - priceToAdd;
            double percentage = difference / catalogPrice;
            if (percentage > 0.20) {
                new AlertDialog.Builder(this)
                        .setTitle("⚠️ Remise Exceptionnelle")
                        .setMessage(String.format("Remise de %.0f%% détectée !\nConfirmer ?", percentage * 100))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("Confirmer", (dialog, which) -> {
                            addProductToCart(productToAdd, quantityToAdd, priceToAdd);
                            resetAddToCartUI();
                            if (isAutoProcess) processTransaction();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
                return;
            }
        }

        addProductToCart(productToAdd, quantityToAdd, priceToAdd);
        resetAddToCartUI();
        if (isAutoProcess) processTransaction();
    }

    private void resetAddToCartUI() {
        etQuantitySell.setText("");
        spinnerProducts.setText("");
        currentSelectedProduct = null;
        etUnitPrice.setText("");
        switchNegotiatedPrice.setChecked(false);
        spinnerProducts.clearFocus();
    }

    private void addProductToCart(Product p, int qty, double price) {
        boolean merged = false;
        for (SaleItemRow row : cartRows) {
            if (row.getProductId().equals(p.getId()) && row.getUnitPrice() == price) {
                int newQty = row.getQuantity() + qty;
                if (newQty > p.getQuantity()) {
                    Toast.makeText(this, "Stock insuffisant", Toast.LENGTH_SHORT).show();
                    return;
                }
                row.setQuantity(newQty);
                row.setTotal(row.getUnitPrice() * newQty);
                merged = true;
                break;
            }
        }

        if (!merged) {
            SaleItemRow row = new SaleItemRow(p.getId(), p.getName(), price, qty);
            cartRows.add(row);
        }
        updateCartBadge();
        Toast.makeText(this, "Ajouté au panier", Toast.LENGTH_SHORT).show();
    }

    private void openCartBottomSheet() {
        CartBottomSheet sheet = new CartBottomSheet(cartRows, this::updateCartBadge);
        sheet.show(getSupportFragmentManager(), "CartBottomSheet");
    }

    private void updateCartBadge() {
        int count = cartRows.size();
        txtCartBadge.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        txtCartBadge.setText(String.valueOf(count));
    }

    private void validateSaleAction() {
        if (!switchConfirm.isChecked()) {
            Toast.makeText(this, "Veuillez confirmer la vente", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cartRows.isEmpty()) {
            if (currentSelectedProduct != null && !etQuantitySell.getText().toString().isEmpty()) {
                addToCartAction(true); // Auto-process
                if (cartRows.isEmpty()) return;
            } else {
                Toast.makeText(this, "Panier vide et aucun produit sélectionné", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        processTransaction();
    }

    // =========================================================
    // NOUVELLE MÉTHODE : GÉNÉRATION DU DEVIS
    // =========================================================
    private void generateQuoteAction() {
        if (cartRows.isEmpty()) {
            Toast.makeText(this, "Le panier est vide. Ajoutez des articles pour le devis.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Un devis nécessite un client. On force l'ouverture du formulaire si non coché.
        if (!checkEditInvoice.isChecked()) {
            Toast.makeText(this, "Veuillez cocher 'Éditer une facture' pour saisir le nom du client sur le devis.", Toast.LENGTH_LONG).show();
            checkEditInvoice.setChecked(true);
            // Scroll vers le formulaire
            if (cardInvoiceForm != null) {
                cardInvoiceForm.setVisibility(View.VISIBLE);
                cardInvoiceForm.getParent().requestChildFocus(cardInvoiceForm, cardInvoiceForm);
            }
            return;
        }

        String nomClient = etClientNom.getText().toString().trim();
        if (nomClient.isEmpty()) {
            Toast.makeText(this, "Le nom du client est obligatoire pour un devis.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Client du devis (enregistré : il sera repris à la conversion en vente)
        Client clientObj = new Client();
        clientObj.setLastname(nomClient);
        clientObj.setFirstname(etClientPrenom.getText().toString().trim());
        clientObj.setAddress(etClientAddress.getText().toString().trim());
        clientObj.setPhone(etClientPhone.getText().toString().trim());
        clientObj.setEmail(etClientEmail.getText().toString().trim());
        if (!clientDao.saveClientLocally(clientObj)) {
            Toast.makeText(this, "Impossible d'enregistrer le client", Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Devis et lignes (une transaction) : brouillon, valable 30 jours
        Quote quote = new Quote();
        quote.setClientId(clientObj.getId());
        quote.setSellerId(sessionManagerLocal.getUserId());
        List<SaleItemRow> quoteRows = new ArrayList<>(cartRows);
        if (quoteDao.createQuote(quote, quoteRows) == null) {
            Toast.makeText(this, "Erreur lors de l'enregistrement du devis", Toast.LENGTH_LONG).show();
            return;
        }
        syncManager.startSynchronization();

        // 3. PDF avec la vraie référence du devis
        Toast.makeText(this, "Devis " + quote.getShortRef() + " enregistré, génération du PDF...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File pdf = PDFUtils.generateQuotePdf(SellProductActivity.this, quote, quoteRows, clientObj,
                    ShopInfo.NAME, ShopInfo.ADDRESS, ShopInfo.PHONE, ShopInfo.EMAIL);
            runOnUiThread(() -> {
                if (pdf == null) {
                    Toast.makeText(this, "Devis enregistré, mais le PDF n'a pas pu être généré", Toast.LENGTH_LONG).show();
                }
                new android.app.AlertDialog.Builder(SellProductActivity.this)
                        .setTitle("Devis " + quote.getShortRef() + " ✅")
                        .setMessage("Le devis est enregistré (Historique → Devis). Voulez-vous vider le panier ?")
                        .setPositiveButton("Vider", (dialog, which) -> {
                            cartRows.clear();
                            updateCartBadge();
                        })
                        .setNegativeButton("Garder", null)
                        .show();
            });
        }).start();
    }
    // =========================================================

    private void processTransaction() {
        String clientId = null;
        Client clientObj = null;

        if (checkEditInvoice.isChecked()) {
            String ln = etClientNom.getText().toString().trim();
            if (!ln.isEmpty()) {
                Client newClient = new Client();
                newClient.setLastname(ln);
                newClient.setFirstname(etClientPrenom.getText().toString().trim());
                newClient.setAddress(etClientAddress.getText().toString().trim());
                newClient.setPhone(etClientPhone.getText().toString().trim());
                newClient.setEmail(etClientEmail.getText().toString().trim());
                if (clientDao.saveClientLocally(newClient)) {
                    clientId = newClient.getId();
                    clientObj = newClient;
                }
            }
        }

        // Le vendeur est toujours l'utilisateur de la session (profiles.id = auth.users.id)
        String userId = sessionManagerLocal.getUserId();
        if (userId == null) {
            Toast.makeText(this, "Erreur session", Toast.LENGTH_LONG).show();
            return;
        }

        double totalAmount = 0;
        for (SaleItemRow item : cartRows) totalAmount += item.getTotal();

        RadioButton radEspeces = findViewById(R.id.radEspeces);
        String paymentMethod = (radEspeces != null && radEspeces.isChecked()) ? "espece" : "virement";

        SaleHeader header = new SaleHeader();
        header.setSellerId(userId);
        header.setClientId(clientId);
        header.setTotalPrice(totalAmount);
        header.setPaymentMethod(paymentMethod);

        // Le drapeau « prix négocié » est calculé par recordSale
        String saleId = saleDao.recordSale(header, cartRows);

        if (saleId != null) {
            if (checkEditInvoice.isChecked() && clientObj != null) generateInvoice(saleId, clientObj);
            Toast.makeText(this, "Vente enregistrée ✅", Toast.LENGTH_LONG).show();
            cartRows.clear();
            updateCartBadge();
            syncManager.startSynchronization();
            reloadActivity();
        } else {
            Toast.makeText(this, "Erreur lors de l'enregistrement de la vente", Toast.LENGTH_LONG).show();
        }
    }

    private void generateInvoice(String saleId, Client client) {
        SaleHeader header = saleDao.getSaleHeaderById(saleId);
        List<SaleItemRow> items = saleDao.getSaleItems(saleId);
        new Thread(() -> PDFUtils.generateInvoicePdf(SellProductActivity.this, header, items, client, "A2 ELEC", "Moroni", "Tel", "Email")).start();
    }

    private void reloadActivity() {
        Intent intent = getIntent();
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void setupQrScanner() {
        qrLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) handleQrResult(result.getContents());
        });
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scannez le code article");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrLauncher.launch(options);
    }

    private void handleQrResult(String code) {
        Product product = productDao.getProductByQr(code);
        if (product == null) {
            Toast.makeText(this, "Produit inconnu", Toast.LENGTH_LONG).show();
            return;
        }
        showQuantityDialog(product);
    }

    private void showQuantityDialog(Product product) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_scan_quantity, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvStock = view.findViewById(R.id.tvStockInfo);
        com.google.android.material.textfield.TextInputEditText edtQuantity = view.findViewById(R.id.edtQuantity);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText(product.getName());
        tvStock.setText("Stock: " + product.getQuantity());
        edtQuantity.requestFocus();

        btnConfirm.setOnClickListener(v -> {
            String qtyStr = edtQuantity.getText().toString().trim();
            if (!qtyStr.isEmpty()) {
                try {
                    int qty = Integer.parseInt(qtyStr);
                    if (qty > 0 && qty <= product.getQuantity()) {
                        // Pour le scanner, on ajoute simplement au panier sans valider auto
                        addProductToCart(product, qty, product.getPrice());
                        dialog.dismiss();
                    } else Toast.makeText(this, "Quantité invalide", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) { Toast.makeText(this, "Format invalide", Toast.LENGTH_SHORT).show(); }
            }
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
}