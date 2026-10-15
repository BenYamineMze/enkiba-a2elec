package com.enkiba.app.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.EditUserActivity;
import com.enkiba.app.activities.ShowUserActivity;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    // ✅ Interface pour gérer les actions (Suppression ET Changement de statut)
    public interface UserActionListener {
        void onDelete(User user);
        void onStatusChange(User user, boolean isActive);
    }

    private final ArrayList<User> users;
    private final UserDao userDao;
    private final Context context;
    private final UserActionListener actionListener; // Listener pour communiquer avec l'Activity

    // ✅ Constructeur complet (celui qui manquait et causait l'erreur)
    public UserAdapter(Context context, ArrayList<User> users, UserActionListener actionListener) {
        this.context = context;
        this.users = users;
        this.actionListener = actionListener;
        this.userDao = new UserDao(context);
    }

    // Constructeur simple (optionnel, pour d'autres usages)
    public UserAdapter(Context context, ArrayList<User> users) {
        this(context, users, null);
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.infos_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);

        holder.name.setText(user.getUsername());
        holder.role.setText("Rôle : " + user.getRole());

        // --- GESTION DU SWITCH (ACTIF/INACTIF) ---
        holder.switchActive.setOnCheckedChangeListener(null); // Important pour le recyclage
        holder.switchActive.setChecked(user.getActive() == 1);

        holder.switchActive.setOnCheckedChangeListener((button, isChecked) -> {
            // 1. Mise à jour locale (SQLite)
            boolean success = userDao.toggleUserActive(user.getId(), isChecked);

            if (success) {
                user.setActive(isChecked ? 1 : 0);

                // 2. Appel au serveur via l'interface (C'est ici que la synchro se lance)
                if (actionListener != null) {
                    actionListener.onStatusChange(user, isChecked);
                } else {
                    Toast.makeText(context, isChecked ? "Activé (Local)" : "Désactivé (Local)", Toast.LENGTH_SHORT).show();
                }
            } else {
                holder.switchActive.setChecked(!isChecked); // Revert en cas d'erreur locale
                Toast.makeText(context, "Erreur locale ❌", Toast.LENGTH_SHORT).show();
            }
        });

        // --- GESTION DE LA SUPPRESSION ---
        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer cet utilisateur ?")
                    .setPositiveButton("Oui", (dialog, which) -> {
                        if (actionListener != null) {
                            // On délègue à l'activité pour l'appel API
                            actionListener.onDelete(user);
                        } else {
                            // Fallback local uniquement
                            userDao.markUserForDeletion(user.getId());
                            users.remove(position);
                            notifyItemRemoved(position);
                        }
                    })
                    .setNegativeButton("Non", null)
                    .show();
        });

        // --- NAVIGATION ---
        holder.btnShow.setOnClickListener(v ->
                context.startActivity(new Intent(context, ShowUserActivity.class)
                        .putExtra("userId", user.getId()))
        );

        holder.btnEdit.setOnClickListener(v ->
                context.startActivity(new Intent(context, EditUserActivity.class)
                        .putExtra("userId", user.getId()))
        );
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView name, role;
        SwitchMaterial switchActive;
        ImageButton btnShow, btnEdit, btnDelete;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtUserName);
            role = itemView.findViewById(R.id.txtUserRole);
            switchActive = itemView.findViewById(R.id.switchActive);
            btnShow = itemView.findViewById(R.id.btnShow);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}