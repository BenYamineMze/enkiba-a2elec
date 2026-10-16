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
import com.enkiba.app.models.ShopMember;
import com.enkiba.app.utils.NavigationHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserManagementActivity extends BaseActivity {

    private RecyclerView recyclerUsers;
    private SupabaseService supabaseService;

    private List<ShopMember> members = new ArrayList<>();
    private UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès refusé. Réservé aux administrateurs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.user_management);

        supabaseService = RetrofitClient.getSupabaseService(this);

        recyclerUsers = findViewById(R.id.recyclerUsers);
        recyclerUsers.setLayoutManager(new LinearLayoutManager(this));

        setupHeader();

        findViewById(R.id.btnAddUser).setOnClickListener(v ->
                startActivity(new Intent(this, AddUserActivity.class)));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            try {
                bottomNav.getMenu().findItem(R.id.nav_personnel).setChecked(true);
            } catch (Exception ignored) { }
        }
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
        supabaseService.getShopMembers("eq." + sessionManager.getShopId(),
                        "user_id,role,profiles(id,username,lastname,telephone,email,active)")
                .enqueue(new Callback<List<ShopMember>>() {
                    @Override
                    public void onResponse(Call<List<ShopMember>> call, Response<List<ShopMember>> r) {
                        if (!r.isSuccessful() || r.body() == null) {
                            Toast.makeText(UserManagementActivity.this, "Chargement impossible", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        members = new ArrayList<>(r.body());
                        adapter = new UserAdapter(UserManagementActivity.this, members,
                                (m, active) -> toggleUserStatus(m, active));
                        recyclerUsers.setAdapter(adapter);
                    }

                    @Override
                    public void onFailure(Call<List<ShopMember>> call, Throwable t) {
                        Toast.makeText(UserManagementActivity.this, "Hors ligne : liste indisponible", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void toggleUserStatus(ShopMember m, boolean active) {
        JsonObject body = new JsonObject();
        body.addProperty("shop_id", sessionManager.getShopId());
        body.addProperty("user_id", m.getUserId());
        body.addProperty("active", active);
        supabaseService.setSellerStatus(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> c, Response<JsonObject> r) {
                if (!r.isSuccessful()) {
                    Toast.makeText(UserManagementActivity.this, "Échec (code " + r.code() + ")", Toast.LENGTH_SHORT).show();
                }
                loadUsers(); // l'écran reflète toujours l'état du serveur
            }

            @Override
            public void onFailure(Call<JsonObject> c, Throwable t) {
                Toast.makeText(UserManagementActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show();
                loadUsers();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers(); // appelé aussi au premier affichage : pas de double appel dans onCreate
    }
}