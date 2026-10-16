package com.enkiba.app.activities;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.NetworkUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Création d'un vendeur par un administrateur (via l'Edge Function create-seller). */
public class AddUserActivity extends BaseActivity {

    private EditText txtUsername, txtLastname, txtTelephone, txtEmail, txtPassword;
    private Button btnSave;
    private SupabaseService supabaseService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès réservé aux administrateurs", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_add_user);
        supabaseService = RetrofitClient.getSupabaseService(this);

        initViews();
        setupHeader();
        btnSave.setOnClickListener(v -> saveUser());
        NavigationHelper.setupNavigation((BottomNavigationView) findViewById(R.id.bottom_nav), this);
    }

    private void initViews() {
        txtUsername = findViewById(R.id.edtNom);
        txtLastname = findViewById(R.id.edtPrenom);
        txtTelephone = findViewById(R.id.edtTelephone);
        txtEmail = findViewById(R.id.edtEmail);
        txtPassword = findViewById(R.id.edtPassword);
        btnSave = findViewById(R.id.btnSaveUser);

        // Tout compte créé ici est un vendeur : le choix du rôle est masqué
        View roleDropdown = findViewById(R.id.roleDropdown);
        if (roleDropdown != null) {
            View parent = (View) roleDropdown.getParent();
            // Le champ est souvent enveloppé (TextInputLayout) : on masque l'enveloppe
            (parent instanceof android.view.ViewGroup && parent.getId() != android.R.id.content
                    ? parent : roleDropdown).setVisibility(View.GONE);
        }
    }

    private void setupHeader() {
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);
        ImageView imgUser = findViewById(R.id.logout);
        tvHeaderTitle.setText("Ajouter un vendeur");
        imgHeaderIcon.setImageResource(R.drawable.ic_add_user);
        imgUser.setOnClickListener(this::showUserMenu);
    }

    private void saveUser() {
        String username = txtUsername.getText().toString().trim();
        String lastname = txtLastname.getText().toString().trim();
        String phone = txtTelephone.getText().toString().trim();
        String email = txtEmail.getText().toString().trim();
        String password = txtPassword.getText().toString().trim();

        if (username.isEmpty() || lastname.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Tous les champs sont obligatoires", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { txtEmail.setError("Email invalide"); return; }
        if (password.length() < 8) { txtPassword.setError("Minimum 8 caractères"); return; }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "Connexion internet requise", Toast.LENGTH_LONG).show();
            return;
        }

        setSaving(true);

        JsonObject body = new JsonObject();
        body.addProperty("shop_id", sessionManager.getShopId());
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("username", username);
        body.addProperty("lastname", lastname);
        body.addProperty("telephone", phone);

        supabaseService.createSeller(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AddUserActivity.this, "Vendeur créé ✅", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    setSaving(false);
                    Toast.makeText(AddUserActivity.this, "Échec (code " + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setSaving(false);
                Toast.makeText(AddUserActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setSaving(boolean saving) {
        btnSave.setEnabled(!saving);
        btnSave.setText(saving ? "Création en cours..." : "Enregistrer");
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