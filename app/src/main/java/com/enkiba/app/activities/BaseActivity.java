package com.enkiba.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.enkiba.app.utils.SessionManager;

/**
 * Activité de base gérant la vérification automatique de la session.
 * Adaptée pour les IDs de type String (UUID Supabase).
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);

        // Debugging des informations de session
        Log.d("SESSION_CHECK", "User ID: " + sessionManager.getUserId());
        Log.d("SESSION_CHECK", "Auth ID (Email/UUID): " + sessionManager.getAuthId());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 🔐 Vérification de la validité de la session
        // Changement : On vérifie si l'ID est null au lieu de 0
        String userId = sessionManager.getUserId();

        if (userId == null || userId.isEmpty()) {
            Log.e("BaseActivity", "Session invalide : ID utilisateur manquant. Redirection...");
            handleSessionInvalid();
            return;
        }

        // Vérification de l'expiration par inactivité
        if (sessionManager.isSessionExpired()) {
            Log.w("BaseActivity", "La session a expiré par inactivité.");
            handleSessionExpired();
        } else {
            // Session valide → on rafraîchit l'horodatage de dernière activité
            sessionManager.updateLastActiveTime();
        }
    }

    /**
     * Gère la redirection en cas de session absente.
     */
    private void handleSessionInvalid() {
        Toast.makeText(this, "Veuillez vous connecter.", Toast.LENGTH_SHORT).show();
        sessionManager.logout();
    }

    /**
     * Gère la redirection en cas de session expirée.
     */
    private void handleSessionExpired() {
        Toast.makeText(this, "Session expirée.", Toast.LENGTH_SHORT).show();
        sessionManager.logout();
    }
}