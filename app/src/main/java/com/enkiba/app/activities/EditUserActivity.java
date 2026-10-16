package com.enkiba.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.enkiba.app.R;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.ShopMember;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.NetworkUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Modification d'un vendeur : nom, prénom, téléphone et mot de passe (facultatif). */
public class EditUserActivity extends BaseActivity {

    private EditText edtName, edtLastName, edtEmail, edtPhone, edtPassword;
    private Button btnSaveUser;
    private SupabaseService supabaseService;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!"admin".equalsIgnoreCase(sessionManager.getRole())) {
            Toast.makeText(this, "Accès refusé. Réservé aux administrateurs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_edit_user);
        supabaseService = RetrofitClient.getSupabaseService(this);

        userId = getIntent().getStringExtra("userId");
        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable ❌", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        edtName = findViewById(R.id.edtName);
        edtLastName = findViewById(R.id.edtLastName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtPassword = findViewById(R.id.edtPassword); // null si absent du layout
        btnSaveUser = findViewById(R.id.btnSaveUser);

        // E-mail = identifiant de connexion : lecture seule
        edtEmail.setEnabled(false);

        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);
        ImageView imgUser = findViewById(R.id.logout);
        tvHeaderTitle.setText("Modifier le vendeur");
        imgHeaderIcon.setImageResource(R.drawable.ic_edit_user);
        imgUser.setOnClickListener(this::showUserMenu);

        btnSaveUser.setOnClickListener(v -> saveUser());
        NavigationHelper.setupNavigation((BottomNavigationView) findViewById(R.id.bottom_nav), this);

        loadMember();
    }

    /** Masque une vue et son conteneur direct si c'est une enveloppe de champ. */

    private void loadMember() {
        supabaseService.getShopMember(
                        "eq." + sessionManager.getShopId(), "eq." + userId,
                        "user_id,role,profiles(id,username,lastname,telephone,email,active)")
                .enqueue(new Callback<List<ShopMember>>() {
                    @Override
                    public void onResponse(Call<List<ShopMember>> call, Response<List<ShopMember>> r) {
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                            Toast.makeText(EditUserActivity.this, "Utilisateur introuvable ❌", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        Profile p = r.body().get(0).getProfile();
                        if (p == null) { finish(); return; }
                        edtName.setText(p.getUsername());
                        edtLastName.setText(p.getLastname());
                        edtEmail.setText(p.getEmail());
                        edtPhone.setText(p.getTelephone());
                    }

                    @Override
                    public void onFailure(Call<List<ShopMember>> call, Throwable t) {
                        Toast.makeText(EditUserActivity.this, "Hors ligne : modification impossible", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void saveUser() {
        String name = edtName.getText().toString().trim();
        String lastname = edtLastName.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String password = edtPassword != null ? edtPassword.getText().toString().trim() : "";

        if (name.isEmpty() || lastname.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs ⚠️", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.isEmpty() && password.length() < 8) {
            edtPassword.setError("Minimum 8 caractères");
            return;
        }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "Connexion internet requise", Toast.LENGTH_LONG).show();
            return;
        }

        btnSaveUser.setEnabled(false);

        JsonObject body = new JsonObject();
        body.addProperty("shop_id", sessionManager.getShopId());
        body.addProperty("user_id", userId);
        body.addProperty("username", name);
        body.addProperty("lastname", lastname);
        body.addProperty("telephone", phone);
        if (!password.isEmpty()) body.addProperty("password", password);

        supabaseService.updateSeller(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> r) {
                if (r.isSuccessful()) {
                    Toast.makeText(EditUserActivity.this, "Modifications enregistrées ✅", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    btnSaveUser.setEnabled(true);
                    Toast.makeText(EditUserActivity.this, "Échec (code " + r.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                btnSaveUser.setEnabled(true);
                Toast.makeText(EditUserActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show();
            }
        });
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