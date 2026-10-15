package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.UserAdapter;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserManagementActivity extends BaseActivity {

    RecyclerView recyclerUsers;
    private UserDao userDao;
    private SupabaseService supabaseService;

    List<User> userList;
    UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Vérification des droits Admin
        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès refusé. Réservé aux administrateurs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.user_management);

        // Initialisations
        this.userDao = new UserDao(this);
        this.supabaseService = RetrofitClient.getSupabaseService(this);

        recyclerUsers = findViewById(R.id.recyclerUsers);
        recyclerUsers.setLayoutManager(new LinearLayoutManager(this));

        setupHeader();

        findViewById(R.id.btnAddUser).setOnClickListener(v ->
                startActivity(new Intent(this, AddUserActivity.class))
        );

        // --- CORRECTION NAVIGATION ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            // Cette ligne force l'onglet "Utilisateurs" à être actif visuellement
            // (Assurez-vous que l'ID dans votre menu xml est bien @id/nav_users)
            try {
                bottomNav.getMenu().findItem(R.id.nav_personnel).setChecked(true);
            } catch (Exception e) {
                // Au cas où l'ID serait différent (ex: nav_personnels), on évite le crash
                e.printStackTrace();
            }
        }

        loadUsers();
    }

    private void setupHeader() {
        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        tvHeaderTitle.setText("Gestion des utilisateurs");
        imgHeaderIcon.setImageResource(R.drawable.ic_users);

        imgUser.setOnClickListener(this::showUserMenu);
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

    private void loadUsers() {
        userList = userDao.getAllUsers();
        if (userList == null) userList = new ArrayList<>();

        adapter = new UserAdapter(this, (ArrayList<User>) userList, new UserAdapter.UserActionListener() {
            @Override
            public void onDelete(User user) {
                deleteUser(user);
            }

            @Override
            public void onStatusChange(User user, boolean isActive) {
                toggleUserStatus(user, isActive);
            }
        });

        recyclerUsers.setAdapter(adapter);
    }

    // --- Actions Serveur (Edge Functions) ---

    private void deleteUser(User user) {
        if (user == null || user.getId() == null) return;

        Map<String, String> body = new HashMap<>();
        body.put("id", user.getAuthId() != null ? user.getAuthId() : user.getId());

        supabaseService.deleteUser(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    userDao.deleteUserPhysically(user.getId());
                    Toast.makeText(UserManagementActivity.this, "Utilisateur supprimé ✅", Toast.LENGTH_SHORT).show();
                    loadUsers();
                } else {
                    Toast.makeText(UserManagementActivity.this, "Erreur serveur (Code " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                userDao.markUserForDeletion(user.getId());
                Toast.makeText(UserManagementActivity.this, "Marqué pour suppression (Hors ligne)", Toast.LENGTH_SHORT).show();
                loadUsers();
            }
        });
    }

    private void toggleUserStatus(User user, boolean isActive) {
        // Préparation de l'appel pour l'Edge Function toggle-user-status
        Map<String, Object> body = new HashMap<>();
        // On utilise l'Auth ID s'il est disponible, sinon l'ID local (qui devrait être le même en mode synced)
        String targetId = user.getAuthId() != null ? user.getAuthId() : user.getId();
        body.put("id", targetId);
        body.put("isActive", isActive);

        // Appel décommenté
        supabaseService.toggleUserStatus(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Optionnel : afficher un message de succès discret
                    // Toast.makeText(UserManagementActivity.this, "Statut mis à jour", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UserManagementActivity.this, "Erreur synchro statut (Code " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Le SyncManager gérera la cohérence plus tard via le flag local
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers();
    }
}