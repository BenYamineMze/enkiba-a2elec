package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.enkiba.app.R;
import com.enkiba.app.api.RetrofitClient;
import com.enkiba.app.api.SupabaseService;
import com.enkiba.app.models.AuthResponse;
import com.enkiba.app.models.Membership;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NetworkUtils;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText edtIdentifier, edtPass;
    private Button btnLogin;

    private UserDao userDao;
    private SessionManager sessionManager;
    private SynchronizationManager synchronizationManager;
    private SupabaseService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        userDao = new UserDao(this);
        synchronizationManager = new SynchronizationManager(this);
        api = RetrofitClient.getSupabaseService(this);

        // Session persistante : l'app s'ouvre même hors-ligne
        if (sessionManager.isLoggedIn()) {
            if (NetworkUtils.isOnline(this)) synchronizationManager.startSynchronization();
            navigateToHome();
            return;
        }

        setContentView(R.layout.page_login);
        edtIdentifier = findViewById(R.id.edt_username);
        edtPass = findViewById(R.id.edt_password);
        btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> handleLoginAttempt());
    }

    private void handleLoginAttempt() {
        String email = edtIdentifier.getText().toString().trim();
        String password = edtPass.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Champs vides", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "Une connexion internet est nécessaire pour se connecter", Toast.LENGTH_LONG).show();
            return;
        }
        performLogin(email, password);
    }

    private void performLogin(String email, String password) {
        btnLogin.setEnabled(false);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);

        api.login(credentials).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                AuthResponse auth = response.body();
                if (!response.isSuccessful() || auth == null || auth.getUser() == null) {
                    fail("Identifiants incorrects");
                    return;
                }
                // Les jetons doivent exister avant les appels suivants (intercepteur)
                sessionManager.saveTokens(auth.getAccessToken(), auth.getRefreshToken(), auth.computeExpiresAt());
                loadProfile(auth.getUser().getId());
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                fail("Erreur réseau");
            }
        });
    }

    private void loadProfile(String userId) {
        api.getProfile("eq." + userId, "*").enqueue(new Callback<List<Profile>>() {
            @Override
            public void onResponse(Call<List<Profile>> call, Response<List<Profile>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    fail("Profil introuvable");
                    return;
                }
                Profile profile = response.body().get(0);
                if (!profile.isActive()) {
                    fail("Ce compte est désactivé");
                    return;
                }
                loadMembership(userId, profile);
            }

            @Override
            public void onFailure(Call<List<Profile>> call, Throwable t) {
                fail("Erreur réseau");
            }
        });
    }

    private void loadMembership(String userId, Profile profile) {
        api.getMemberships("eq." + userId, "shop_id,role").enqueue(new Callback<List<Membership>>() {
            @Override
            public void onResponse(Call<List<Membership>> call, Response<List<Membership>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    fail("Compte non rattaché à une boutique");
                    return;
                }
                Membership m = response.body().get(0); // une seule boutique pour l'instant

                // Le local garde 'admin' / 'user' (CHECK de la table) : seller -> user
                String localRole = "admin".equals(m.getRole()) ? "admin" : "user";

                sessionManager.saveSession(userId, profile.getUsername(), profile.getLastname(),
                        localRole, m.getShopId());

                User local = new User();
                local.setId(userId);
                local.setAuthId(userId);
                local.setUsername(profile.getUsername());
                local.setLastname(profile.getLastname());
                local.setTelephone(profile.getTelephone() != null ? profile.getTelephone() : "");
                local.setEmail(profile.getEmail());
                local.setRole(localRole);
                local.setActive(1);
                local.setUpdatedAt(profile.getUpdatedAt());
                userDao.insertOrUpdateUserFromRemote(local); // aucun mot de passe stocké

                synchronizationManager.startSynchronization();
                navigateToHome();
            }

            @Override
            public void onFailure(Call<List<Membership>> call, Throwable t) {
                fail("Erreur réseau");
            }
        });
    }

    private void fail(String message) {
        sessionManager.clearSession();
        btnLogin.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.w(TAG, message);
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, AcceuilActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}