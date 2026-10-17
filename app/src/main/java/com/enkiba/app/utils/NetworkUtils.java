package com.enkiba.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

public class NetworkUtils {

    /**
     * Vérifie si l'appareil est actuellement connecté à Internet (Wi-Fi ou Mobile Data).
     * @param context Le contexte de l'application ou de l'activité.
     * @return true si une connexion Internet active est détectée.
     */
    public static boolean isOnline(Context context) {

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        // minSdk 24 : getActiveNetwork() (API 23) est toujours disponible
        android.net.Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}