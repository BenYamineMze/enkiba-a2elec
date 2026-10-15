package com.enkiba.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ShowUserActivity extends BaseActivity {

    TextView txtName, txtEmail, txtPhone, txtRole, txtActive;

    private UserDao userDao;
    // sessionManager is inherited from BaseActivity

    // 🚨 CHANGED: userId is now a String (UUID)
    String userId;
    User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_user);

        // DAO Initialization
        this.userDao = new UserDao(this);

        // ID Extraction (String UUID)
        if (getIntent().hasExtra("userId")) {
            userId = getIntent().getStringExtra("userId");
            // Safety fallback if a long was passed
            if (userId == null) {
                long idLong = getIntent().getLongExtra("userId", -1L);
                if (idLong != -1) {
                    userId = String.valueOf(idLong);
                }
            }
        }

        // Correction 1: Read user via UserDao with String ID
        if (userId != null) {
            currentUser = userDao.getUserById(userId);
        }

        if (currentUser == null) {
            Toast.makeText(this, "Utilisateur introuvable ou ID manquant ❌", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // --- UI Header Setup ---
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        if (tvHeaderTitle != null) tvHeaderTitle.setText("Profil Utilisateur");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_user); // Assuming you have an icon

        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);

        // --- UI Content ---
        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);
        txtRole = findViewById(R.id.txtRole);
        txtActive = findViewById(R.id.txtActive);

        txtName.setText(currentUser.getUsername() + " " + currentUser.getLastname());
        txtEmail.setText(currentUser.getEmail());
        txtPhone.setText(currentUser.getTelephone());
        txtRole.setText(currentUser.getRole());
        txtActive.setText(currentUser.getActive() == 1 ? "Actif ✅" : "Désactivé ❌");

        Button btnEditUser = findViewById(R.id.btnEditUser);
        Button btnDeleteUser = findViewById(R.id.btnDeleteUser);

        btnEditUser.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditUserActivity.class);
            intent.putExtra("userId", userId); // Passes String ID
            startActivity(intent);
        });

        btnDeleteUser.setOnClickListener(v -> confirmDeleteUser());

        // Navigation bar
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            bottomNav.getMenu().findItem(R.id.nav_personnel).setChecked(true);
        }
    }

    private void confirmDeleteUser() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmation de suppression")
                .setMessage("Voulez-vous vraiment supprimer " + currentUser.getUsername() + " ? Cette action sera synchronisée (Soft Delete).")
                .setPositiveButton("Oui, Supprimer", (dialog, which) -> {
                    // Correction 2: Mark for deletion via UserDao (Soft Delete)
                    boolean marked = userDao.markUserForDeletion(userId);

                    if (marked) {
                        Toast.makeText(this, "Utilisateur marqué pour suppression ✅", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Erreur de suppression ❌", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
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

    @Override
    protected void onResume() {
        super.onResume();
        // Reload data if edited
        if (userId != null) {
            User updatedUser = userDao.getUserById(userId);
            if (updatedUser != null) {
                currentUser = updatedUser;
                txtName.setText(currentUser.getUsername() + " " + currentUser.getLastname());
                txtEmail.setText(currentUser.getEmail());
                txtPhone.setText(currentUser.getTelephone());
                txtRole.setText(currentUser.getRole());
                txtActive.setText(currentUser.getActive() == 1 ? "Actif ✅" : "Désactivé ❌");
            }
        }
    }
}