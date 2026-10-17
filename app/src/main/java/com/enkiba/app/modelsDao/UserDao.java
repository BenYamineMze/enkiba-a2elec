package com.enkiba.app.modelsDao;

import android.content.Context;

import com.enkiba.app.data.local.AppDatabase;
import com.enkiba.app.data.local.Mappers;
import com.enkiba.app.data.local.dao.UserRoomDao;
import com.enkiba.app.data.local.entity.UserEntity;
import com.enkiba.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache local des utilisateurs (noms des vendeurs pour l'affichage), au-dessus de Room.
 * Les utilisateurs sont gérés par le serveur : rien n'est synchronisé depuis le téléphone.
 */
public class UserDao {

    private final UserRoomDao dao;

    public UserDao(Context context) {
        this.dao = AppDatabase.get(context).userDao();
    }

    /** Enregistre (ou remplace) l'utilisateur reçu du serveur. */
    public String insertOrUpdateUserFromRemote(User user) {
        dao.upsert(Mappers.toEntity(user));
        return user.getId();
    }

    public User getUserById(String id) {
        if (id == null) return null;
        UserEntity e = dao.getById(id);
        return e != null ? Mappers.toModel(e) : null;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        for (UserEntity e : dao.getAll()) list.add(Mappers.toModel(e));
        return list;
    }
}
