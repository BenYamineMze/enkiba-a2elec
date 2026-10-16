package com.enkiba.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.List;

/**
 * Gère la création de la base de données (schéma) et sa mise à jour.
 * Contient également les méthodes de transactions complexes impliquant plusieurs tables.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "stock.db";
    // 🚨 CHANGEMENT DE VERSION : Passage à 4 pour appliquer la contrainte UNIQUE sur l'email
    private static final int DATABASE_VERSION = 6;

    // --- CONSTANTES DE COLONNES INTERNES ---
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String QR_CODE = "qr_code";
    private static final String INIT_QUANTITY = "initial_stock";
    private static final String COL_LOW_STOCK_THRESHOLD= "low_stock_threshold";

    // --- CONSTANTES DE SYNCHRONISATION ---
    public static final String COL_SYNC_STATUS = "sync_status"; // 'pending', 'synced', 'deleted'
    public static final String COL_UPDATED_AT = "updated_at";   // Horodatage ISO 8601

    // --- CONSTANTES DE TABLES ---
    public static final String TABLE_PRODUCTS = "products";
    public static final String TABLE_USERS = "users";
    public static final String TABLE_CLIENTS = "clients";
    public static final String TABLE_SALES = "sales";
    public static final String TABLE_SALE_ITEMS = "sale_items";
    public static final String TABLE_HISTORY = "history";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // 1. TABLE PRODUCTS
        String CREATE_PRODUCTS_TABLE = "CREATE TABLE " + TABLE_PRODUCTS + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_NAME + " TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "sold_quantity INTEGER DEFAULT 0, " +
                "price REAL NOT NULL, " +
                "description TEXT, " +
                QR_CODE + " TEXT, " +
                INIT_QUANTITY + " TEXT, " +
                COL_LOW_STOCK_THRESHOLD + " INTEGER DEFAULT 5," +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_PRODUCTS_TABLE);


        // 2. TABLE USERS (CORRECTION : EMAIL UNIQUE)
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + " (" +
                "id TEXT PRIMARY KEY, " +
                "auth_id TEXT, " +
                "username TEXT NOT NULL, " +
                "lastname TEXT NOT NULL, " +
                "telephone TEXT NOT NULL, " +
                "email TEXT NOT NULL, " + // <--- 🚨 AJOUT DE LA CONTRAINTE UNIQUE
                "password TEXT, " +
                "role TEXT NOT NULL CHECK(role IN ('admin', 'user')), " +
                "active INTEGER DEFAULT 1," +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_USERS_TABLE);

        String CREATE_UNIQUE_EMAIL_INDEX =
                "CREATE UNIQUE INDEX idx_users_email_active " +
                        "ON " + TABLE_USERS + "(email) " +
                        "WHERE " + COL_SYNC_STATUS + " != 'deleted'";
        db.execSQL(CREATE_UNIQUE_EMAIL_INDEX);

        // 3. TABLE CLIENTS
        String CREATE_CLIENT_TABLE ="CREATE TABLE " + TABLE_CLIENTS + " (" +
                "id INTEGER PRIMARY KEY, " +
                "firstname TEXT, " +
                "lastname TEXT, " +
                "address TEXT, " +
                "phone TEXT, " +
                "email TEXT," +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_CLIENT_TABLE);

        // 4. TABLE SALES (CORRIGÉ AVEC LA COLONNE DE NÉGOCIATION)
        String CREATE_SALES_TABLE = "CREATE TABLE " + TABLE_SALES + " (" +
                "id INTEGER PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "client_id INTEGER, " +
                "total_price REAL NOT NULL, " +
                "payment_method TEXT NOT NULL, " +
                "sale_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "is_archived INTEGER DEFAULT 0, " +
                "has_negotiation INTEGER DEFAULT 0, " + // [AJOUT IMPORTANT]
                "updated_at TEXT NOT NULL, " +
                "sync_status TEXT NOT NULL DEFAULT 'synced', " +
                "FOREIGN KEY(user_id) REFERENCES users(id), " +
                "FOREIGN KEY(client_id) REFERENCES clients(id)" +
                ")";
        db.execSQL(CREATE_SALES_TABLE);

        // 5. TABLE SALE_ITEMS
        String CREATE_SALE_ITEMS = "CREATE TABLE " + TABLE_SALE_ITEMS + " (" +
                "id INTEGER PRIMARY KEY, " +
                "sale_id INTEGER NOT NULL, " +
                "product_id INTEGER NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "initial_quantity INTEGER NOT NULL, " +
                "unit_price REAL NOT NULL, " +
                "total_price REAL NOT NULL," +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'," +
                "FOREIGN KEY(sale_id) REFERENCES " + TABLE_SALES + "(id), " +
                "FOREIGN KEY(product_id) REFERENCES " + TABLE_PRODUCTS + "(id)" +
                ")";
        db.execSQL(CREATE_SALE_ITEMS);

        // 6. TABLE HISTORY
        String CREATE_HISTORY_TABLE = "CREATE TABLE " + TABLE_HISTORY + " (" +
                "id INTEGER PRIMARY KEY, " +
                "product_id INTEGER NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "operation TEXT, " +
                "old_value TEXT, " +
                "new_value TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'," +
                "FOREIGN KEY(product_id) REFERENCES " + TABLE_PRODUCTS + "(id), " +
                "FOREIGN KEY(user_id) REFERENCES " + TABLE_USERS + "(id)" +
                ")";
        db.execSQL(CREATE_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Suppression des tables existantes pour recréation propre
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SALES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLIENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SALE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS sale_items_archive"); // table v1 obsolète
        db.execSQL("DROP TABLE IF EXISTS sale_archive"); // table v1 obsolète

        onCreate(db);
    }
}
