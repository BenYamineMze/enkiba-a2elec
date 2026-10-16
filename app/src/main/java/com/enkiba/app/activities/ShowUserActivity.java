package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.ShopMember;
import com.enkiba.app.utils.NavigationHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Fiche d'un membre de la boutique (lecture seule, données du serveur). */
public class ShowUserActivity extends BaseActivity {

    private TextView txtName, txtEmail, txtPhone, txtRole, txtActive;
    private Button btnEditUser;
    private SupabaseService supabaseService;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès réservé aux administrateurs", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_show_user);
        supabaseService = RetrofitClient.getSupabaseService(this);

        userId = getIntent().getStringExtra("userId");
        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable ❌", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);
        ImageView imgUser = findViewById(R.id.logout);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Profil utilisateur");
        if (imgHeaderIcon != null) imgHeaderIcon.setImageResource(R.drawable.ic_user);
        if (imgUser != null) imgUser.setOnClickListener(this::showUserMenu);

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);
        txtRole = findViewById(R.id.txtRole);
        txtActive = findViewById(R.id.txtActive);
        btnEditUser = findViewById(R.id.btnEditUser);

        // La suppression n'existe plus : on désactive depuis la liste
        View btnDelete = findViewById(R.id.btnDeleteUser);
        if (btnDelete != null) btnDelete.setVisibility(View.GONE);

        btnEditUser.setVisibility(View.GONE); // affiché seulement pour un vendeur (voir display)
        btnEditUser.setOnClickListener(v -> startActivity(
                new Intent(this, EditUserActivity.class).putExtra("userId", userId)));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            NavigationHelper.setupNavigation(bottomNav, this);
            try { bottomNav.getMenu().findItem(R.id.nav_personnel).setChecked(true); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null) loadMember(); // recharge aussi après une modification
    }

    private void loadMember() {
        supabaseService.getShopMember(
                        "eq." + sessionManager.getShopId(), "eq." + userId,
                        "user_id,role,profiles(id,username,lastname,telephone,email,active)")
                .enqueue(new Callback<List<ShopMember>>() {
                    @Override
                    public void onResponse(Call<List<ShopMember>> call, Response<List<ShopMember>> r) {
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                            Toast.makeText(ShowUserActivity.this, "Utilisateur introuvable ❌", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        display(r.body().get(0));
                    }

                    @Override
                    public void onFailure(Call<List<ShopMember>> call, Throwable t) {
                        Toast.makeText(ShowUserActivity.this, "Hors ligne : fiche indisponible", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void display(ShopMember m) {
        Profile p = m.getProfile();
        if (p == null) return;
        boolean isSeller = "seller".equals(m.getRole());

        txtName.setText((p.getUsername() + " " + p.getLastname()).trim());
        txtEmail.setText(p.getEmail());
        txtPhone.setText(p.getTelephone());
        txtRole.setText(isSeller ? "Vendeur" : "Administrateur");
        txtActive.setText(p.isActive() ? "Actif ✅" : "Désactivé ❌");
        btnEditUser.setVisibility(isSeller ? View.VISIBLE : View.GONE);
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