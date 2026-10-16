package com.enkiba.app.modelsDao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.User;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class UserDao {

    private final DatabaseHelper dbHelper;

    public UserDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // --- CRÉATION / INSERTION CORRIGÉE ---

    public String insertOrUpdateUserFromRemote(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", user.getId());
        if (user.getAuthId() != null) values.put("auth_id", user.getAuthId());
        values.put("username", user.getUsername() != null ? user.getUsername() : "");
        values.put("lastname", user.getLastname() != null ? user.getLastname() : "");
        values.put("email", user.getEmail());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) values.put("password", user.getPassword());
        if (user.getRole() != null) values.put("role", user.getRole());
        values.put("telephone", user.getTelephone() != null ? user.getTelephone() : "");
        values.put("active", user.getActive());
        values.put(DatabaseHelper.COL_UPDATED_AT, user.getUpdatedAt() != null ? user.getUpdatedAt() : DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");

        int rows = db.update(DatabaseHelper.TABLE_USERS, values, "id = ?", new String[]{user.getId()});
        if (rows == 0) {
            rows = db.update(DatabaseHelper.TABLE_USERS, values, "email = ?", new String[]{user.getEmail()});
            if (rows == 0) db.insert(DatabaseHelper.TABLE_USERS, null, values);
        }
        db.close();
        return user.getId();
    }

    public String insertOrUpdateUserLocally(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", user.getUsername());
        values.put("lastname", user.getLastname());
        values.put("email", user.getEmail());
        values.put("telephone", user.getTelephone());
        values.put("role", user.getRole());
        values.put("active", user.getActive());
        if (user.getAuthId() != null) values.put("auth_id", user.getAuthId());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(DatabaseHelper.TABLE_USERS, values, "id = ?", new String[]{user.getId()});
        db.close();
        return (rows > 0) ? user.getId() : null;
    }

    public User getUserByEmail(String email) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, "email=?", new String[]{email}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) user = cursorToUser(cursor);
        } catch (Exception e) { Log.e("UserDao", "Erreur getUserByEmail", e); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return user;
    }

    public User getUserByUsername(String username) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, "username=?", new String[]{username}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) user = cursorToUser(cursor);
        } catch (Exception e) { Log.e("UserDao", "Erreur getUserByUsername", e); }
        finally { if (cursor != null) cursor.close(); db.close(); }
        return user;
    }

    public User getUserById(String id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, "id=?", new String[]{id}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) user = cursorToUser(cursor);
        } finally { if (cursor != null) cursor.close(); db.close(); }
        return user;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, "sync_status != ?", new String[]{"deleted"}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do { list.add(cursorToUser(cursor)); } while (cursor.moveToNext());
            }
        } finally { if (cursor != null) cursor.close(); db.close(); }
        return list;
    }

    public List<User> getPendingUsersForSync() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, "sync_status=?", new String[]{"pending"}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do { list.add(cursorToUser(cursor)); } while (cursor.moveToNext());
            }
        } finally { if (cursor != null) cursor.close(); db.close(); }
        return list;
    }

    public void updateUserAuthId(String userId, String authId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("auth_id", authId);
        db.update(DatabaseHelper.TABLE_USERS, values, "id=?", new String[]{userId});
        db.close();
    }

    public boolean toggleUserActive(String userId, boolean isActive) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("active", isActive ? 1 : 0);
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "pending");
        int rows = db.update(DatabaseHelper.TABLE_USERS, values, "id=?", new String[]{userId});
        db.close();
        return rows > 0;
    }

    public List<User> getUsersBySyncStatus(String status) {
        List<User> users = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_USERS, null, DatabaseHelper.COL_SYNC_STATUS + " = ?", new String[]{status}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do { users.add(cursorToUser(cursor)); } while (cursor.moveToNext());
            }
        } finally { if (cursor != null) cursor.close(); db.close(); }
        return users;
    }

    public void updateUser(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (user.getAuthId() != null) values.put("auth_id", user.getAuthId());
        values.put("username", user.getUsername());
        values.put("lastname", user.getLastname());
        values.put("email", user.getEmail());
        values.put("telephone", user.getTelephone());
        values.put("role", user.getRole());
        values.put("active", user.getActive());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) values.put("password", user.getPassword());
        values.put(DatabaseHelper.COL_SYNC_STATUS, user.getSyncStatus());
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        db.update(DatabaseHelper.TABLE_USERS, values, "id = ?", new String[]{user.getId()});
        db.close();
    }

    public void markUserAsSynced(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SYNC_STATUS, "synced");
        db.update(DatabaseHelper.TABLE_USERS, values, "id=?", new String[]{id});
        db.close();
    }

    public boolean markUserForDeletion(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_UPDATED_AT, DatabaseUtils.getCurrentIsoTime());
        values.put(DatabaseHelper.COL_SYNC_STATUS, "deleted");
        int rows = db.update(DatabaseHelper.TABLE_USERS, values, "id = ?", new String[]{id});
        db.close();
        return rows > 0;
    }

    public void deleteUserPhysically(String id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_USERS, "id = ?", new String[]{id});
        db.close();
    }

    private User cursorToUser(Cursor cursor) {
        User user = new User();
        int idxId = cursor.getColumnIndex("id");
        if (idxId != -1) user.setId(cursor.getString(idxId));
        int idxAuth = cursor.getColumnIndex("auth_id");
        if (idxAuth != -1) user.setAuthId(cursor.getString(idxAuth));
        int idxUser = cursor.getColumnIndex("username");
        if (idxUser != -1) user.setUsername(cursor.getString(idxUser));
        int idxLast = cursor.getColumnIndex("lastname");
        if (idxLast != -1) user.setLastname(cursor.getString(idxLast));
        int idxEmail = cursor.getColumnIndex("email");
        if (idxEmail != -1) user.setEmail(cursor.getString(idxEmail));
        int idxPass = cursor.getColumnIndex("password");
        if (idxPass != -1) user.setPassword(cursor.getString(idxPass));
        int idxRole = cursor.getColumnIndex("role");
        if (idxRole != -1) user.setRole(cursor.getString(idxRole));
        int idxTel = cursor.getColumnIndex("telephone");
        if (idxTel != -1) user.setTelephone(cursor.getString(idxTel));
        int idxActive = cursor.getColumnIndex("active");
        if (idxActive != -1) user.setActive(cursor.getInt(idxActive));
        int idxUpdate = cursor.getColumnIndex(DatabaseHelper.COL_UPDATED_AT);
        if (idxUpdate != -1) user.setUpdatedAt(cursor.getString(idxUpdate));
        int idxSync = cursor.getColumnIndex(DatabaseHelper.COL_SYNC_STATUS);
        if (idxSync != -1) user.setSyncStatus(cursor.getString(idxSync));
        return user;
    }
}