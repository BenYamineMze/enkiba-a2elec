package com.enkiba.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.KeyStore;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.enkiba.app.ui.login.LoginActivity;

/** Session chiffrée : JWT, refresh token, boutique et rôle. Aucun mot de passe stocké. */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "user_session_secure";
    /** Fichier de clés par défaut de security-crypto (exclu de la sauvegarde, effacé si illisible). */
    private static final String LEGACY_KEYSET_PREF_NAME = "__androidx_security_crypto_encrypted_prefs__";

    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER = "username";
    private static final String KEY_USER_LASTNAME = "lastname";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_SHOP_ID = "shop_id";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static SharedPreferences sPrefs;

    private final SharedPreferences prefs;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = getPrefs(this.context);
    }

    /**
     * Ouvre la session chiffrée. Si elle est illisible (fichier restauré depuis un autre
     * appareil, clé Keystore perdue ou invalidée), on efface le fichier et on repart d'une
     * session vide : l'utilisateur est renvoyé vers la connexion au lieu de voir un plantage.
     * Les règles de sauvegarde (res/xml) excluent déjà ce fichier ; ceci reste le filet de sécurité.
     */
    private static synchronized SharedPreferences getPrefs(Context c) {
        if (sPrefs != null) return sPrefs;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                MasterKey key = new MasterKey.Builder(c)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                sPrefs = EncryptedSharedPreferences.create(c, PREF_NAME, key,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                return sPrefs;
            } catch (Exception e) {
                Log.e(TAG, "Prefs chiffrées illisibles (essai " + (attempt + 1) + "), réinitialisation", e);
                c.deleteSharedPreferences(PREF_NAME);
                c.deleteSharedPreferences(LEGACY_KEYSET_PREF_NAME);
                // Au deuxième échec, la clé maître elle-même est suspecte : on la recrée.
                if (attempt >= 1) deleteMasterKey();
            }
        }
        throw new IllegalStateException("Impossible d'ouvrir le stockage chiffré");
    }

    /** Supprime la clé maître de l'Android Keystore (elle sera régénérée à l'essai suivant). */
    private static void deleteMasterKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS);
        } catch (Exception e) {
            Log.e(TAG, "Suppression de la clé maître impossible", e);
        }
    }

    /**
     * Une valeur peut être indéchiffrable même si le fichier s'ouvre (SecurityException à la
     * lecture). Dans ce cas on vide la session et on renvoie la valeur par défaut : isLoggedIn()
     * devient faux et l'écran de connexion s'affiche.
     */
    private String readString(String key, String def) {
        try {
            return prefs.getString(key, def);
        } catch (RuntimeException e) {
            onUnreadable(e);
            return def;
        }
    }

    private long readLong(String key, long def) {
        try {
            return prefs.getLong(key, def);
        } catch (RuntimeException e) {
            onUnreadable(e);
            return def;
        }
    }

    private boolean readBoolean(String key, boolean def) {
        try {
            return prefs.getBoolean(key, def);
        } catch (RuntimeException e) {
            onUnreadable(e);
            return def;
        }
    }

    private void onUnreadable(RuntimeException e) {
        Log.e(TAG, "Valeur de session indéchiffrable, session effacée", e);
        clearSession();
    }

    /** Enregistre seulement les jetons (utilisé pendant la connexion et lors du refresh). */
    public void saveTokens(String accessToken, String refreshToken, long expiresAtSeconds) {
        prefs.edit()
                .putString(KEY_JWT_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAtSeconds)
                .apply();
    }

    /** Finalise la session une fois le profil et la boutique connus. */
    public void saveSession(String userId, String username, String lastname, String email,
                            String role, String shopId) {
        prefs.edit()
                .putString(KEY_EMAIL, email != null ? email : "")
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER, username != null ? username : "")
                .putString(KEY_USER_LASTNAME, lastname != null ? lastname : "")
                .putString(KEY_ROLE, role != null ? role.toLowerCase() : "user")
                .putString(KEY_SHOP_ID, shopId)
                .apply();
    }

    public String getJwtToken() { return readString(KEY_JWT_TOKEN, null); }
    public String getRefreshToken() { return readString(KEY_REFRESH_TOKEN, null); }
    public long getExpiresAt() { return readLong(KEY_EXPIRES_AT, 0); }
    public String getShopId() { return readString(KEY_SHOP_ID, null); }
    public String getUserId() { return readString(KEY_USER_ID, null); }
    public String getAuthId() { return getUserId(); } // v2 : profiles.id = auth.users.id
    public String getRole() { return readString(KEY_ROLE, "user"); }

    /** Prénom (profiles.username). */
    public String getFirstName() { return readString(KEY_USER, ""); }
    /** Nom de famille (profiles.lastname). */
    public String getLastName() { return readString(KEY_USER_LASTNAME, ""); }
    /** Met à jour le prénom et le nom affichés (après « Mon profil »). */
    public void updateNames(String username, String lastname) {
        prefs.edit()
                .putString(KEY_USER, username != null ? username : "")
                .putString(KEY_USER_LASTNAME, lastname != null ? lastname : "")
                .apply();
    }

    /** E-mail de connexion (repli d'affichage quand le profil n'a pas de nom). */
    public String getEmail() { return readString(KEY_EMAIL, ""); }
    public boolean isAdmin() { return "admin".equalsIgnoreCase(getRole()); }

    public String getUsername() {
        String u = readString(KEY_USER, "");
        String l = readString(KEY_USER_LASTNAME, "");
        return (u + " " + l).trim();
    }

    public boolean isLoggedIn() {
        return readBoolean(KEY_IS_LOGGED_IN, false)
                && getUserId() != null
                && getRefreshToken() != null;
    }

    /** Plus d'expiration par inactivité : la validité dépend du refresh token. */
    public boolean isSessionExpired() { return false; }
    public void updateLastActiveTime() { /* conservé pour compatibilité */ }

    public void clearSession() {
        try {
            prefs.edit().clear().commit();
        } catch (RuntimeException e) {
            // Dernier recours : effacer le fichier ; il sera recréé au prochain démarrage.
            Log.e(TAG, "Effacement de la session impossible, suppression du fichier", e);
            context.deleteSharedPreferences(PREF_NAME);
        }
        Log.i(TAG, "Session effacée.");
    }

    public void logout() {
        clearSession();
        Intent i = new Intent(context, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
    }
}