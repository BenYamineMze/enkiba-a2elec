package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SynchronizationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class EditUserActivity extends BaseActivity {

    EditText edtName, edtLastName, edtEmail, edtPhone;
    AutoCompleteTextView edtRole;
    Switch switchActive;
    Button btnSaveUser;

    private UserDao userDao;

    // 🚨 CORRECTION : L'ID est maintenant un String (UUID)
    String userId;
    User currentUser;

    // sessionManager est hérité de BaseActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Contrôle d'accès : Admin uniquement
        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès refusé. Réservé aux administrateurs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_edit_user);

        // Initialisation du DAO
        this.userDao = new UserDao(this);

        edtName = findViewById(R.id.edtName);
        edtLastName = findViewById(R.id.edtLastName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtRole = findViewById(R.id.edtRole);
        switchActive = findViewById(R.id.switchActive);
        btnSaveUser = findViewById(R.id.btnSaveUser);

        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Modifier l'utilisateur");
        imgHeaderIcon.setImageResource(R.drawable.ic_edit_user);

        imgUser.setOnClickListener(this::showUserMenu);

        // 🚨 CORRECTION : Récupération de l'ID en tant que String
        if (getIntent().hasExtra("userId")) {
            // On tente de récupérer en String d'abord
            userId = getIntent().getStringExtra("userId");

            // Fallback de sécurité si l'appelant passe encore un long
            if (userId == null) {
                long idLong = getIntent().getLongExtra("userId", -1L);
                if (idLong != -1) {
                    userId = String.valueOf(idLong);
                }
            }
        }

        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable ❌", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadUserData();

        btnSaveUser.setOnClickListener(v -> saveUser());

        // Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
    }

    private void loadUserData() {
        // Appel avec l'ID String
        currentUser = userDao.getUserById(userId);

        if (currentUser == null) {
            Toast.makeText(this, "Erreur chargement utilisateur ❌", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        edtName.setText(currentUser.getUsername());
        edtLastName.setText(currentUser.getLastname());
        edtEmail.setText(currentUser.getEmail());
        edtPhone.setText(currentUser.getTelephone());
        edtRole.setText(currentUser.getRole(), false);

        switchActive.setChecked(currentUser.getActive() == 1);

        String[] roles = {"admin", "user", "vendeur"};
        // Utilisation d'un layout standard pour éviter les erreurs de ressource
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
        edtRole.setAdapter(adapter);
    }

    private void saveUser() {
        String name = edtName.getText().toString().trim();
        String lastname = edtLastName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String role = edtRole.getText().toString().trim();
        int active = switchActive.isChecked() ? 1 : 0;

        if (name.isEmpty() || lastname.isEmpty() || email.isEmpty() || phone.isEmpty() || role.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs ⚠️", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mise à jour de l'objet local
        currentUser.setUsername(name);
        currentUser.setLastname(lastname);
        currentUser.setEmail(email);
        currentUser.setTelephone(phone);
        currentUser.setRole(role);
        currentUser.setActive(active);

        // 🚨 CORRECTION : insertOrUpdateUserLocally retourne maintenant un String (ou null si erreur)
        String result = userDao.insertOrUpdateUserLocally(currentUser);

        if (result != null) {
            Toast.makeText(this, "Modifications enregistrées ✅", Toast.LENGTH_SHORT).show();

            // Lancer la synchronisation pour mettre à jour Supabase
            new SynchronizationManager(this).startSynchronization();

            finish();
        } else {
            Toast.makeText(this, "Erreur lors de la mise à jour ❌", Toast.LENGTH_SHORT).show();
        }
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
}