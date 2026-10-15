package com.enkiba.app.api;

import android.content.Context;
import com.enkiba.app.utils.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // URL de base racine (l'interface gère les sous-chemins 'auth/v1' et 'rest/v1')
    private static final String BASE_URL = "https://brnlbtlhuamtvpwbwypm.supabase.co/";
    private static final String SUPABASE_KEY = "sb_publishable_Q7w6vjWFs5S7Gm1_28FWlA_bwQroePU";

    private static Retrofit retrofit = null;

    private static final Gson gson = new GsonBuilder()
            .setLenient()
            .create();

    /**
     * Construit le client HTTP avec les Interceptors (Headers + Logs).
     * Méthode privée interne.
     */
    private static OkHttpClient buildOkHttpClient(Context context) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        httpClient.addInterceptor(logging);

        // Interceptor pour ajouter l'API Key et le Token JWT
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request.Builder requestBuilder = original.newBuilder()
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json");

            // Récupération dynamique du token
            SessionManager sessionManager = new SessionManager(context);
            String token = sessionManager.getJwtToken();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            return chain.proceed(requestBuilder.build());
        });

        return httpClient.build();
    }

    /**
     * Retourne l'instance Retrofit (CORRECTION ICI : retourne Retrofit et non OkHttpClient).
     * Cela permet d'appeler .create(Service.class) dessus.
     */
    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(buildOkHttpClient(context)) // On utilise le client construit ci-dessus
                    .build();
        }
        return retrofit;
    }

    /**
     * Méthode utilitaire recommandée pour obtenir le service.
     */
    public static SupabaseService getSupabaseService(Context context) {
        return getClient(context).create(SupabaseService.class);
    }

    /**
     * Alias pour compatibilité.
     * Retourne le même service car l'interface SupabaseService gère les deux types d'appels.
     */
    public static SupabaseService getAuthService(Context context) {
        return getSupabaseService(context);
    }
}