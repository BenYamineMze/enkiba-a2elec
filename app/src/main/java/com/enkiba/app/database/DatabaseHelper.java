package com.enkiba.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Gère la création de la base de données locale (schéma) et sa mise à jour.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "stock.db";
    // v7 : schéma aligné sur Supabase v2 (UUID TEXT, shop_id, deleted_at, plus d'archives)
    // v8 : table stock_movements (mouvements saisis localement, en attente d'envoi)
    private static final int DATABASE_VERSION = 8;

    // --- CONSTANTES DE COLONNES INTERNES ---
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String QR_CODE = "qr_code";
    private static final String INIT_QUANTITY = "initial_stock";
    private static final String COL_LOW_STOCK_THRESHOLD= "low_stock_threshold";

    // --- CONSTANTES DE SYNCHRONISATION ---
    public static final String COL_SYNC_STATUS = "sync_status"; // 'pending', 'synced', 'deleted'
    public static final String COL_UPDATED_AT = "updated_at";   // Horodatage ISO 8601
    public static final String COL_DELETED_AT = "deleted_at";   // Suppression logique (NULL = actif)
    public static final String COL_SHOP_ID = "shop_id";

    // --- CONSTANTES DE TABLES ---
    public static final String TABLE_PRODUCTS = "products";
    public static final String TABLE_USERS = "users";
    public static final String TABLE_CLIENTS = "clients";
    public static final String TABLE_SALES = "sales";
    public static final String TABLE_SALE_ITEMS = "sale_items";
    public static final String TABLE_HISTORY = "history";
    public static final String TABLE_STOCK_MOVEMENTS = "stock_movements";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // 1. TABLE PRODUCTS (id = UUID ; la quantité vendue se calcule depuis sale_items)
        String CREATE_PRODUCTS_TABLE = "CREATE TABLE " + TABLE_PRODUCTS + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                COL_NAME + " TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "price REAL NOT NULL, " +
                "description TEXT, " +
                QR_CODE + " TEXT, " +
                INIT_QUANTITY + " INTEGER NOT NULL DEFAULT 0, " +
                COL_LOW_STOCK_THRESHOLD + " INTEGER NOT NULL DEFAULT 5, " +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_DELETED_AT + " TEXT, " +
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

        // 3. TABLE CLIENTS (id = UUID généré par le téléphone)
        String CREATE_CLIENT_TABLE = "CREATE TABLE " + TABLE_CLIENTS + " (" +
                "id TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                "firstname TEXT, " +
                "lastname TEXT, " +
                "address TEXT, " +
                "phone TEXT, " +
                "email TEXT, " +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_DELETED_AT + " TEXT, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_CLIENT_TABLE);

        // 4. TABLE SALES (id = UUID ; seller_id = profiles.id)
        String CREATE_SALES_TABLE = "CREATE TABLE " + TABLE_SALES + " (" +
                "id TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                "seller_id TEXT NOT NULL, " +
                "client_id TEXT, " +
                "total_price REAL NOT NULL, " +
                "payment_method TEXT NOT NULL, " +
                "has_negotiation INTEGER NOT NULL DEFAULT 0, " +
                "sale_date TEXT NOT NULL, " +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_DELETED_AT + " TEXT, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_SALES_TABLE);
        db.execSQL("CREATE INDEX idx_sales_date ON " + TABLE_SALES + "(sale_date)");

        // 5. TABLE SALE_ITEMS (copie du nom et du prix catalogue au moment de la vente)
        // initial_quantity est purement local : les retours ne sont pas encore synchronisés.
        String CREATE_SALE_ITEMS = "CREATE TABLE " + TABLE_SALE_ITEMS + " (" +
                "id TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                "sale_id TEXT NOT NULL, " +
                "product_id TEXT NOT NULL, " +
                "product_name TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "initial_quantity INTEGER NOT NULL, " +
                "list_price REAL NOT NULL, " +
                "unit_price REAL NOT NULL, " +
                "total_price REAL NOT NULL, " +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_SALE_ITEMS);
        db.execSQL("CREATE INDEX idx_sale_items_sale ON " + TABLE_SALE_ITEMS + "(sale_id)");
        db.execSQL("CREATE INDEX idx_sale_items_product ON " + TABLE_SALE_ITEMS + "(product_id)");

        // 6. TABLE HISTORY (id = UUID ; deviendra audit_log côté serveur à l'étape 4)
        String CREATE_HISTORY_TABLE = "CREATE TABLE " + TABLE_HISTORY + " (" +
                "id TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                "product_id TEXT NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "operation TEXT, " +
                "old_value TEXT, " +
                "new_value TEXT, " +
                "timestamp TEXT NOT NULL, " +
                COL_UPDATED_AT + " TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'synced'" +
                ")";
        db.execSQL(CREATE_HISTORY_TABLE);
        db.execSQL("CREATE INDEX idx_history_timestamp ON " + TABLE_HISTORY + "(timestamp)");

        // 7. TABLE STOCK_MOVEMENTS : seulement les mouvements saisis sur ce téléphone
        // ('initial', 'restock', 'adjustment'). Les ventes et retours génèrent les leurs côté serveur.
        String CREATE_STOCK_MOVEMENTS = "CREATE TABLE " + TABLE_STOCK_MOVEMENTS + " (" +
                "id TEXT PRIMARY KEY, " +
                COL_SHOP_ID + " TEXT, " +
                "product_id TEXT NOT NULL, " +
                "delta INTEGER NOT NULL, " +
                "reason TEXT NOT NULL, " +
                "created_by TEXT, " +
                "created_at TEXT NOT NULL, " +
                COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'pending'" +
                ")";
        db.execSQL(CREATE_STOCK_MOVEMENTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // ⚠️ ATTENTION : supprime TOUTES les données locales, y compris les ventes non synchronisées.
        // Acceptable tant qu'il n'y a aucun utilisateur réel (données jetables).
        // TODO : remplacer par de vraies migrations (ALTER TABLE / copie) avant la première
        // publication de la v2 (prévu avec Room à l'étape 6).
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SALES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLIENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SALE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STOCK_MOVEMENTS);
        db.execSQL("DROP TABLE IF EXISTS sale_items_archive"); // table v1 obsolète
        db.execSQL("DROP TABLE IF EXISTS sale_archive"); // table v1 obsolète

        onCreate(db);
    }
}
