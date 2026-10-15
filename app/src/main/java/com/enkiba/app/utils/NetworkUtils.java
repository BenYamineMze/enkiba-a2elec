package com.enkiba.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

public class NetworkUtils {

    /**
     * Vérifie si l'appareil est actuellement connecté à Internet (Wi-Fi ou Mobile Data).
     * * @param context Le contexte de l'application ou de l'activité.
     * @return true si une connexion Internet active est détectée.
     */
    public static boolean isOnline(Context context) {

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        // --- Pour les API modernes (API 23 et supérieur) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }

        // --- Pour les anciennes API (Moins recommandé mais supporté par l'API 24) ---
        else {
            // Bien que dépréciée, cette méthode est utilisée pour la compatibilité
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }
}