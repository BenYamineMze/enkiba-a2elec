package com.enkiba.app.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.ProductHistory;

import java.util.List;

public class ProductHistoryAdapter extends RecyclerView.Adapter<ProductHistoryAdapter.HistoryViewHolder> {

    private Context context;
    private List<ProductHistory> historyList;

    public ProductHistoryAdapter(Context context, List<ProductHistory> historyList) {
        this.context = context;
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Charge le layout XML. Assurez-vous que item_product_history.xml existe.
        View view = LayoutInflater.from(context).inflate(R.layout.item_product_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        // SÉCURITÉ 1 : Vérifie si la liste ou l'élément est null
        if (historyList == null || position >= historyList.size()) return;
        ProductHistory history = historyList.get(position);
        if (history == null) return;

        // SÉCURITÉ 2 : On vérifie que chaque vue existe avant de l'utiliser

        // 1. Nom du produit
        if (holder.tvProductName != null) {
            String pName = history.getProductName();
            if (pName == null || pName.isEmpty() || "null".equals(pName)) {
                holder.tvProductName.setText("Produit ID: " + (history.getProductId() != null ? history.getProductId() : "?"));
            } else {
                holder.tvProductName.setText(pName);
            }
        }

        // 2. Date
        if (holder.tvDate != null) {
            holder.tvDate.setText(formatDate(history.getTimestamp()));
        }

        // 3. Utilisateur
        if (holder.tvUser != null) {
            String uName = history.getUsername();
            holder.tvUser.setText((uName != null && !uName.isEmpty()) ? uName : "Utilisateur inconnu");
        }

        // 4. Détails (Fusion des valeurs)
        if (holder.tvDetails != null) {
            String oldV = history.getOldValue() != null ? history.getOldValue() : "-";
            String newV = history.getNewValue() != null ? history.getNewValue() : "-";

            if (oldV.equals(newV)) {
                holder.tvDetails.setText(newV);
            } else {
                holder.tvDetails.setText(String.format("%s ➔ %s", oldV, newV));
            }
        }

        // 5. Opération et Couleur
        if (holder.tvOperation != null) {
            String operation = history.getOperation();
            if (operation == null) operation = "Info";

            holder.tvOperation.setText(operation);

            // --- COULEURS SÉCURISÉES ---
            try {
                if (operation.contains("Négocié")) {
                    holder.tvOperation.setTextColor(Color.RED);
                } else if ("Ajout".equalsIgnoreCase(operation)) {
                    // Utilisation d'un vert générique si le context ou la ressource pose problème
                    int color = (context != null) ? ContextCompat.getColor(context, R.color.primary_green_dark) : Color.GREEN;
                    holder.tvOperation.setTextColor(color);
                } else if (operation.toUpperCase().contains("DELETE") || operation.contains("Suppression")) {
                    holder.tvOperation.setTextColor(Color.DKGRAY);
                } else {
                    int color = (context != null) ? ContextCompat.getColor(context, R.color.text_primary) : Color.BLACK;
                    holder.tvOperation.setTextColor(color);
                }
            } catch (Exception e) {
                // Fallback ultime : couleur noire si erreur de ressources
                holder.tvOperation.setTextColor(Color.BLACK);
            }
        }
    }

    @Override
    public int getItemCount() {
        return (historyList != null) ? historyList.size() : 0;
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "-";
        try {
            if (isoDate.contains("T")) {
                return isoDate.replace("T", " ").substring(0, Math.min(isoDate.length(), 16));
            }
            return isoDate;
        } catch (Exception e) {
            return isoDate;
        }
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvOperation, tvDetails, tvDate, tvUser;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // On utilise des try-catch lors du findView pour éviter le crash si un ID manque dans le XML
            // findViewById ne lance pas d'exception mais retourne null, on gère le null dans onBindViewHolder
            tvProductName = itemView.findViewById(R.id.txtProductName);
            tvOperation = itemView.findViewById(R.id.txtOperation);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            tvDate = itemView.findViewById(R.id.txtDate);
            tvUser = itemView.findViewById(R.id.txtUser);

            // Log de débogage pour identifier quel champ manque éventuellement
            if (tvProductName == null) Log.w("AdapterHistory", "⚠️ tvProductName introuvable dans le layout XML");
            if (tvOperation == null) Log.w("AdapterHistory", "⚠️ tvOperation introuvable dans le layout XML");
            if (tvDetails == null) Log.w("AdapterHistory", "⚠️ tvDetails introuvable dans le layout XML");
        }
    }
}