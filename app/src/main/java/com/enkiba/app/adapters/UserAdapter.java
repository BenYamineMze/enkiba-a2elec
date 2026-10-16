package com.enkiba.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.EditUserActivity;
import com.enkiba.app.activities.ShowUserActivity;
import com.enkiba.app.models.Profile;
import com.enkiba.app.models.ShopMember;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    public interface StatusListener {
        void onStatusChange(ShopMember member, boolean active);
    }

    private final Context context;
    private final List<ShopMember> members;
    private final StatusListener listener;

    public UserAdapter(Context context, List<ShopMember> members, StatusListener listener) {
        this.context = context;
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.infos_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        ShopMember m = members.get(position);
        Profile p = m.getProfile();
        boolean isSeller = "seller".equals(m.getRole());

        holder.name.setText(p != null ? (p.getUsername() + " " + p.getLastname()).trim() : "?");
        holder.role.setText(isSeller ? "Rôle : Vendeur" : "Rôle : Administrateur");

        // L'interrupteur reflète l'état du serveur ; un administrateur ne se désactive pas
        holder.switchActive.setOnCheckedChangeListener(null);
        holder.switchActive.setChecked(p != null && p.isActive());
        holder.switchActive.setEnabled(isSeller);
        holder.switchActive.setOnCheckedChangeListener((b, checked) -> {
            if (listener != null) listener.onStatusChange(m, checked);
        });

        // Plus de suppression : on désactive uniquement
        holder.btnDelete.setVisibility(View.GONE);
        holder.btnEdit.setVisibility(isSeller ? View.VISIBLE : View.GONE);

        holder.btnShow.setOnClickListener(v -> context.startActivity(
                new Intent(context, ShowUserActivity.class).putExtra("userId", m.getUserId())));
        holder.btnEdit.setOnClickListener(v -> context.startActivity(
                new Intent(context, EditUserActivity.class).putExtra("userId", m.getUserId())));
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView name, role;
        SwitchMaterial switchActive;
        ImageButton btnShow, btnEdit, btnDelete;

        UserViewHolder(@NonNull View itemView) {
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