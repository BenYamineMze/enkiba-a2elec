package com.enkiba.app.utils;

import java.util.Locale;

public class CurrencyUtils {

    private static final double KMF_TO_EUR_RATE = 0.00203;

    /** Convertit un montant en KMF vers EUR */
    public static double kmfToEur(double kmf) {
        return kmf * KMF_TO_EUR_RATE;
    }

    /** Convertit et arrondit le résultat à 2 décimales */
    public static String kmfToEurFormatted(double kmf) {
        double eur = kmf * KMF_TO_EUR_RATE;
        return String.format(Locale.getDefault(), "%.2f €", eur);
    }

    /**
     * Formate un montant en KMF avec séparateur de milliers et sans décimale.
     * Exemple : 1 500 KMF
     */
    public static String formatKMF(double amount) {
        // "%,.0f" formate le nombre avec des séparateurs de milliers (selon la locale) et 0 décimale
        return String.format(Locale.getDefault(), "%,.0f KMF", amount);
    }

    // --- HELPER DATE ---
    public static String formatSaleDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "-";
        try {
            // Remplace le séparateur T (format ISO) par un espace
            String clean = dateStr.replace("T", " ");
            // Si la chaîne est assez longue, on garde les 16 premiers caractères : "YYYY-MM-DD HH:mm"
            if (clean.length() >= 16) {
                return clean.substring(0, 16);
            }
            return clean;
        } catch (Exception e) {
            return dateStr;
        }
    }
}