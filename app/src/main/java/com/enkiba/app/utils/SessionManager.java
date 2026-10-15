package com.enkiba.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.enkiba.app.activities.LoginActivity;

/**
 * Gère la session utilisateur en utilisant SharedPreferences.
 * Adapté pour supporter les UUID (String) de Supabase.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "user_session";

    private static final String KEY_USER_ID = "user_id"; // Maintenant stocké en String (UUID)
    private static final String KEY_USER = "username";
    private static final String KEY_USER_LASTNAME = "lastname";
    private static final String KEY_ROLE = "role";
    private static final String KEY_AUTH_ID = "auth_id";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static final String PREF_LAST_ACTIVE_TIME = "last_active_time";
    public static final long SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1000; // 2 heures

    private final SharedPreferences prefs;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ----------------------------------------------------------------
    // 🔐 AUTH / SESSION
    // ----------------------------------------------------------------

    /**
     * Sauvegarde les informations de l'utilisateur.
     * @param userId L'ID unique (UUID String de Supabase)
     */
    public void saveUser(String userId, String username, String lastname, String role, String jwtToken, String authId) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USER_ID, userId) // Changement : putLong -> putString
                .putString(KEY_USER, username)
                .putString(KEY_USER_LASTNAME, lastname != null ? lastname : "")
                .putString(KEY_ROLE, role != null ? role.toLowerCase() : "user")
                .putString(KEY_JWT_TOKEN, jwtToken)
                .putString(KEY_AUTH_ID, authId)
                .putLong(PREF_LAST_ACTIVE_TIME, System.currentTimeMillis())
                .apply();

        Log.i(TAG, "Session sauvegardée. UUID=" + userId + ", Role=" + role);
    }

    public String getJwtToken() {
        return prefs.getString(KEY_JWT_TOKEN, null);
    }

    public String getAuthId() {
        return prefs.getString(KEY_AUTH_ID, null);
    }

    /**
     * Vérifie si l'utilisateur est connecté.
     * La session est valide si le flag est à true, l'ID est présent et non expiré.
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
                && getUserId() != null
                && !isSessionExpired();
    }

    // ----------------------------------------------------------------
    // 👤 USER INFO
    // ----------------------------------------------------------------

    /**
     * @return L'identifiant unique de l'utilisateur (UUID)
     */
    public String getUserId() {
        // Changement : getLong -> getString
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUsername() {
        String u = prefs.getString(KEY_USER, "");
        String l = prefs.getString(KEY_USER_LASTNAME, "");
        return (u + " " + l).trim();
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "user");
    }

    // ----------------------------------------------------------------
    // ⏱️ TIMEOUT
    // ----------------------------------------------------------------

    public boolean isSessionExpired() {
        long lastActive = prefs.getLong(PREF_LAST_ACTIVE_TIME, 0);
        if (lastActive == 0) return false;
        return (System.currentTimeMillis() - lastActive) > SESSION_TIMEOUT_MS;
    }

    public void updateLastActiveTime() {
        prefs.edit()
                .putLong(PREF_LAST_ACTIVE_TIME, System.currentTimeMillis())
                .apply();
    }

    // ----------------------------------------------------------------
    // 🚪 LOGOUT
    // ----------------------------------------------------------------

    public void clearSession() {
        prefs.edit().clear().commit();
        Log.i(TAG, "Session effacée.");
    }

    public void logout() {
        clearSession();
        Intent i = new Intent(context, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
    }
}