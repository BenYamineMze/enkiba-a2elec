package com.enkiba.app.activities;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.DatabaseUtils;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.NetworkUtils;
import com.enkiba.app.utils.PasswordUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddUserActivity extends BaseActivity {

    private EditText txtUsername, txtLastname, txtTelephone, txtEmail, txtPassword;
    private MaterialAutoCompleteTextView roleDropdown;
    private Button btnSave;

    private UserDao userDao;
    private SupabaseService supabaseService;

    private static final String[] ROLES = {"admin","user"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_user);

        userDao = new UserDao(this);
        supabaseService = RetrofitClient.getSupabaseService(this);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès réservé aux administrateurs", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupHeader();
        setupRoleDropdown();
        setupActions();
        setupNavigation();
    }

    private void initViews() {
        txtUsername = findViewById(R.id.edtNom);
        txtLastname = findViewById(R.id.edtPrenom);
        txtTelephone = findViewById(R.id.edtTelephone);
        txtEmail = findViewById(R.id.edtEmail);
        txtPassword = findViewById(R.id.edtPassword);
        roleDropdown = findViewById(R.id.roleDropdown);
        btnSave = findViewById(R.id.btnSaveUser);
    }

    private void setupHeader() {
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);
        ImageView imgUser = findViewById(R.id.logout);
        tvHeaderTitle.setText("Ajouter un utilisateur");
        imgHeaderIcon.setImageResource(R.drawable.ic_add_user);
        imgUser.setOnClickListener(this::showUserMenu);
    }

    private void setupRoleDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, ROLES);
        roleDropdown.setAdapter(adapter);
        roleDropdown.setKeyListener(null);
    }

    private void setupActions() {
        btnSave.setOnClickListener(v -> saveUser());
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
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

        btnSave.setEnabled(false);
        btnSave.setText("Création en cours...");

        JsonObject body = new JsonObject();
        body.addProperty("shop_id", sessionManager.getShopId());
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("username", username);
        body.addProperty("lastname", lastname);
        body.addProperty("telephone", phone);

        supabaseService.createSeller(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AddUserActivity.this, "Vendeur créé ✅", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    btnSave.setEnabled(true);
                    btnSave.setText("Enregistrer");
                    Toast.makeText(AddUserActivity.this, "Échec (code " + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                btnSave.setEnabled(true);
                btnSave.setText("Enregistrer");
                Toast.makeText(AddUserActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveLocalUserSynced(String uuid, String username, String lastname, String phone, String email, String password, String role) {
        User u = new User();
        u.setId(uuid); // On utilise l'ID final du serveur
        u.setAuthId(uuid);
        u.setUsername(username);
        u.setLastname(lastname);
        u.setTelephone(phone);
        u.setEmail(email);
        u.setPassword(PasswordUtils.hashPassword(password));
        u.setRole(role);
        u.setActive(1);
        u.setUpdatedAt(DatabaseUtils.getCurrentIsoTime());
        u.setSyncStatus("synced"); // ✅ IMPORTANT : Déjà bon !

        userDao.insertOrUpdateUserLocally(u); // Méthode qui fait un insert ou update direct
    }

    private void saveLocalUserPending(String username, String lastname, String phone, String email, String password, String role) {
        userDao.addUser(username, lastname, phone, email, password, role); // Utilise votre méthode existante qui met 'pending_create'
    }

    private boolean isFormValid(String username, String lastname, String phone, String email, String password, String role) {
        if (username.isEmpty() || lastname.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty() || role.isEmpty()) {
            Toast.makeText(this, "Tous les champs sont obligatoires", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtEmail.setError("Email invalide");
            return false;
        }
        if (password.length() < 6) {
            txtPassword.setError("Mot de passe trop court (min 6)");
            return false;
        }
        return true;
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