package com.enkiba.app.utils;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class DatabaseUtils {

    // Pour garantir l'unicité de l'ID temporaire dans une session
    private static final java.util.concurrent.atomic.AtomicLong tempIdCounter = new java.util.concurrent.atomic.AtomicLong(-1);

    // Retourne un horodatage au format ISO 8601 (requis par Supabase/PostgreSQL)
    // Fonctionne sur toutes les versions d'API Android.
    public static String getCurrentIsoTime() {
        // Format ISO 8601 avec le 'Z' indiquant le fuseau horaire UTC
        //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    /**
     * Convertit une date ISO (de Supabase ou SQLite) en format lisible pour l'utilisateur.
     * Affiche : Jour/Mois/Année Heure:Minute (ex: 19/12/2025 14:30)
     * * @param isoDate La chaîne de date brute (ex: 2025-12-19T14:30:00.000Z)
     * @return La date formatée pour l'affichage
     */
    public static String formatIsoToDisplay(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";

        try {
            // 1. Détection du format d'entrée (avec ou sans millisecondes)
            String inputPattern = isoDate.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : "yyyy-MM-dd'T'HH:mm:ss'Z'";

            // Si la date ne contient pas de 'T', c'est peut-être un format SQLite simple (yyyy-MM-dd HH:mm:ss)
            if (!isoDate.contains("T")) {
                inputPattern = "yyyy-MM-dd HH:mm:ss";
            }

            SimpleDateFormat inputFormat = new SimpleDateFormat(inputPattern, Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            // 2. Format de sortie souhaité
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            outputFormat.setTimeZone(TimeZone.getDefault()); // Conversion auto à l'heure locale du téléphone

            Date date = inputFormat.parse(isoDate);
            return outputFormat.format(date);
        } catch (Exception e) {
            // En cas d'erreur de parsing, on retourne la chaîne brute pour éviter un crash
            return isoDate;
        }
    }

    // Génère un ID négatif temporaire pour les insertions hors ligne
    public static long generateTempId() {
        // Utiliser un ID basé sur le décrément du compteur
        // Assurez-vous d'utiliser un type long ici pour correspondre à la méthode de synchronisation
        return tempIdCounter.decrementAndGet();
    }
}