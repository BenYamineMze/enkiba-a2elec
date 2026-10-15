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
import com.enkiba.app.models.User;
import com.enkiba.app.models.UserData;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.NetworkUtils;
import com.enkiba.app.utils.PasswordUtils;
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

        // Initialisation des outils
        sessionManager = new SessionManager(this);
        userDao = new UserDao(this);
        synchronizationManager = new SynchronizationManager(this);

        // Initialisation API
        api = RetrofitClient.getSupabaseService(this);

        // 1. Vérification session
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Session déjà active. Redirection.");
            // On lance une synchro en tâche de fond si possible
            if (NetworkUtils.isOnline(this)) {
                synchronizationManager.startSynchronization();
            }
            navigateToHome();
            return;
        }

        setContentView(R.layout.page_login);

        edtIdentifier = findViewById(R.id.edt_username);
        edtPass = findViewById(R.id.edt_password);
        btnLogin = findViewById(R.id.btn_login);

        // 2. Synchro préventive pour récupérer les rôles à jour
        if (NetworkUtils.isOnline(this)) {
            syncRemoteUsers();
        }

        btnLogin.setOnClickListener(v -> handleLoginAttempt());
    }

    private void syncRemoteUsers() {
        Log.d(TAG, "Tentative de synchronisation users...");
        api.getUsers("*").enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (User remoteUser : response.body()) {
                        userDao.insertOrUpdateUserFromRemote(remoteUser);
                    }
                    Log.d(TAG, "Utilisateurs synchronisés : " + response.body().size());
                } else {
                    Log.w(TAG, "Erreur API Users : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Log.e(TAG, "Échec réseau synchro users", t);
            }
        });
    }


    private void handleLoginAttempt() {
        String identifier = edtIdentifier.getText().toString().trim();
        String password = edtPass.getText().toString().trim();

        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Champs vides", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NetworkUtils.isOnline(this)) {
            User offlineUser = userDao.loginOffline(identifier, password);
            if (offlineUser != null) {
                loginLocally(offlineUser);
            } else {
                Toast.makeText(this, "Connexion hors ligne impossible", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        performSupabaseLogin(identifier, password);
    }



    private void performSupabaseLogin(String email, String password) {

        btnLogin.setEnabled(false);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);

        api.login(credentials).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {

                btnLogin.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this, "Identifiants incorrects", Toast.LENGTH_SHORT).show();
                    return;
                }

                AuthResponse auth = response.body();
                String authId = auth.getUser().getId();
                String token = auth.getAccessToken();

                // 🔹 1. Récupérer le user depuis Supabase table users
                api.getUserByAuthId("eq." + authId).enqueue(new Callback<List<User>>() {
                    @Override
                    public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            Toast.makeText(LoginActivity.this, "Utilisateur introuvable (table users)", Toast.LENGTH_LONG).show();
                            return;
                        }

                        User remoteUser = response.body().get(0);

                        // 🔐 IMPORTANT POUR LE OFFLINE
                        remoteUser.setPassword(PasswordUtils.hashPassword(password));

                        // 💾 Sauvegarde locale
                        userDao.insertOrUpdateUserFromRemote(remoteUser);

                        // 💾 Session
                        sessionManager.saveUser(
                                remoteUser.getId(),
                                remoteUser.getUsername(),
                                remoteUser.getLastname(),
                                remoteUser.getRole(),
                                token,
                                authId
                        );

                        synchronizationManager.startSynchronization();
                        navigateToHome();
                    }

                    @Override
                    public void onFailure(Call<List<User>> call, Throwable t) {
                        Toast.makeText(LoginActivity.this, "Erreur récupération utilisateur", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loginLocally(User user) {
        sessionManager.saveUser(
                user.getId(),
                user.getUsername(),
                user.getLastname(),
                user.getRole(),
                null,               // 🔴 PAS de JWT offline
                user.getAuthId()    // peut être null
        );
        navigateToHome();
    }


    private void fetchAndStoreUserByAuthId(String authId, String password, String token) {

        api.getUserByAuthId("eq." + authId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {

                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Utilisateur métier introuvable", Toast.LENGTH_LONG).show();
                    return;
                }

                User remoteUser = response.body().get(0);

                // 🔐 Hash du mot de passe pour le hors ligne
                remoteUser.setPassword(PasswordUtils.hashPassword(password));
                remoteUser.setSyncStatus("synced");

                userDao.insertOrUpdateUserFromRemote(remoteUser);

                // 💾 SESSION
                sessionManager.saveUser(
                        remoteUser.getId(),
                        remoteUser.getUsername(),
                        remoteUser.getLastname(),
                        remoteUser.getRole(),
                        token,
                        remoteUser.getAuthId()
                );

                synchronizationManager.startSynchronization();
                navigateToHome();
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Erreur récupération utilisateur", Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void navigateToHome() {
        Intent intent = new Intent(this, AcceuilActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}