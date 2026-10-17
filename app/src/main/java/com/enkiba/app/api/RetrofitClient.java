package com.enkiba.app.api;

import android.content.Context;

import com.enkiba.app.BuildConfig;
import com.enkiba.app.models.AuthResponse;
import com.enkiba.app.utils.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // URL de base racine (l'interface gère les sous-chemins 'auth/v1' et 'rest/v1')
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_KEY;

    private static final Object REFRESH_LOCK = new Object();

    private static Retrofit retrofit = null;

    private static final Gson gson = new GsonBuilder()
            .setLenient()
            .create();

    /** Nombre de tentatives déjà faites pour cette requête (évite les boucles). */
    private static int priorResponseCount(Response response) {
        int n = 1;
        while ((response = response.priorResponse()) != null) n++;
        return n;
    }

    /** Appel synchrone, avec un client sans authenticator pour éviter toute boucle. */
    private static AuthResponse refreshTokens(Context context, String refreshToken) {
        try {
            OkHttpClient plain = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            JsonObject json = new JsonObject();
            json.addProperty("refresh_token", refreshToken);

            Request req = new Request.Builder()
                    .url(BASE_URL + "auth/v1/token?grant_type=refresh_token")
                    .header("apikey", SUPABASE_KEY)
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response res = plain.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    return gson.fromJson(res.body().string(), AuthResponse.class);
                }
                // Refresh token refusé : la session est définitivement invalide
                if (res.code() == 400 || res.code() == 401 || res.code() == 403) {
                    new SessionManager(context).clearSession();
                }
            }
        } catch (Exception e) {
            // Problème réseau : on garde la session, on réessaiera plus tard
        }
        return null;
    }

    /** Construit le client HTTP (headers, logs, renouvellement automatique du JWT). */
    private static OkHttpClient buildOkHttpClient(Context context) {
        final Context appContext = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);
        logging.redactHeader("Authorization");
        logging.redactHeader("apikey");

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        httpClient.addInterceptor(logging);

        // Interceptor : clé API + JWT courant
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request.Builder requestBuilder = original.newBuilder()
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json");

            SessionManager sessionManager = new SessionManager(appContext);
            String token = sessionManager.getJwtToken();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            return chain.proceed(requestBuilder.build());
        });

        // Sur 401 : renouvelle le JWT avec le refresh token puis rejoue la requête
        httpClient.authenticator((route, response) -> {
            synchronized (REFRESH_LOCK) {
                if (priorResponseCount(response) >= 2) return null;
                if (response.request().url().encodedPath().contains("/auth/v1/")) return null;

                SessionManager s = new SessionManager(appContext);
                String refresh = s.getRefreshToken();
                if (refresh == null) return null;

                // Un autre appel a peut-être déjà renouvelé le jeton
                String sent = response.request().header("Authorization");
                String current = s.getJwtToken();
                if (current != null && sent != null && !sent.equals("Bearer " + current)) {
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + current).build();
                }

                AuthResponse fresh = refreshTokens(appContext, refresh);
                if (fresh == null || fresh.getAccessToken() == null) return null;

                s.saveTokens(fresh.getAccessToken(), fresh.getRefreshToken(), fresh.computeExpiresAt());
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + fresh.getAccessToken()).build();
            }
        });

        return httpClient.build();
    }

    /** Retourne l'instance Retrofit (singleton). */
    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(buildOkHttpClient(context.getApplicationContext()))
                    .build();
        }
        return retrofit;
    }

    public static SupabaseService getSupabaseService(Context context) {
        return getClient(context).create(SupabaseService.class);
    }

    /** Alias pour compatibilité. */
    public static SupabaseService getAuthService(Context context) {
        return getSupabaseService(context);
    }
}