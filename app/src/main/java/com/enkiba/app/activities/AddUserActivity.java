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
        String role = roleDropdown.getText().toString().trim();

        if (!isFormValid(username, lastname, phone, email, password, role)) return;

        btnSave.setEnabled(false);
        btnSave.setText("Création en cours...");

        // 1. Préparer les données pour le serveur
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("email", email);
        jsonBody.addProperty("password", password);
        jsonBody.addProperty("role", role);
        jsonBody.addProperty("username", username);
        jsonBody.addProperty("lastname", lastname);
        jsonBody.addProperty("telephone", phone);

        // 2. Appel à l'Edge Function
        supabaseService.createUser(jsonBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // SUCCÈS SERVEUR !
                    // On récupère l'ID généré par Supabase (UUID)
                    JsonObject userObj = response.body().getAsJsonObject("user");
                    String serverId = userObj.get("id").getAsString();

                    // 3. Sauvegarde Locale "Propre" (Marqué comme Synced)
                    saveLocalUserSynced(serverId, username, lastname, phone, email, password, role);

                    Toast.makeText(AddUserActivity.this, "Utilisateur créé et synchronisé !", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    // ÉCHEC SERVEUR (Ex: Email existe déjà)
                    // On tente quand même une sauvegarde locale "Pending" pour réessayer plus tard ?
                    // NON, pour un utilisateur (Auth), mieux vaut forcer l'erreur maintenant.
                    btnSave.setEnabled(true);
                    btnSave.setText("Enregistrer");
                    try {
                        String error = response.errorBody().string();
                        Toast.makeText(AddUserActivity.this, "Erreur serveur: " + error, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(AddUserActivity.this, "Erreur création utilisateur", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // ÉCHEC RÉSEAU (Pas d'internet)
                // On sauvegarde en "Pending" pour que le SyncManager s'en charge quand internet reviendra
                saveLocalUserPending(username, lastname, phone, email, password, role);
                Toast.makeText(AddUserActivity.this, "Hors ligne : Sauvegardé localement", Toast.LENGTH_SHORT).show();
                finish();
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