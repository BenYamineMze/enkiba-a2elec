package com.enkiba.app.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.SaleDetailsActivity;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.utils.CurrencyUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SaleHeaderAdapter extends RecyclerView.Adapter<SaleHeaderAdapter.SaleHeaderViewHolder> {

    private final Context context;
    private final List<SaleHeader> saleHeaders;
    private final OnSaleClickListener listener;
    private ArrayList<SaleHeader> fullList; // copie complète de la liste pour la recherche

    public interface OnSaleClickListener {
        void onSaleClick(SaleHeader header);
    }

    public SaleHeaderAdapter(Context context, List<SaleHeader> saleHeaders, OnSaleClickListener listener) {
        this.context = context;
        this.saleHeaders = saleHeaders;
        this.listener = listener;
        this.fullList = new ArrayList<>(saleHeaders); // sauvegarde complète
    }

    @NonNull
    @Override
    public SaleHeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // [CORRECTION] Utilisation du layout avec le badge rouge
        // Assurez-vous que le fichier 'item_sale_history.xml' existe bien dans res/layout
        View v = LayoutInflater.from(context).inflate(R.layout.item_sale, parent, false);
        return new SaleHeaderViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull SaleHeaderViewHolder holder, int position) {
        SaleHeader header = saleHeaders.get(position);

        holder.tvSaleId.setText("Vente n° " + header.getId());

        // Gestion de la date avec CurrencyUtils
        try {
            holder.tvDate.setText(CurrencyUtils.formatSaleDate(header.getSaleDate()));
        } catch (Exception e) {
            holder.tvDate.setText(header.getSaleDate());
        }

        holder.tvUser.setText("Vendu par : " + (header.getUsername() != null ? header.getUsername() : "Inconnu"));

        // Affichage propre du mode de paiement
        String payment = header.getPaymentMethod();
        if ("espece".equalsIgnoreCase(payment)) payment = "Espèces";
        else if ("virement".equalsIgnoreCase(payment)) payment = "Virement";
        holder.tvPayment.setText("Paiement : " + payment);

        holder.tvTotal.setText(
                "Total : " + new DecimalFormat("0.00").format(header.getTotalPrice()) + " KMF"
        );

        // --- GESTION DU BADGE ROUGE "PRIX NÉGOCIÉ" ---
        // On vérifie si la vue a bien été trouvée et si la vente est marquée comme négociée
        if (holder.tvDiscountBadge != null) {
            if (header.isHasNegotiatedPrice()) {
                holder.tvDiscountBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvDiscountBadge.setVisibility(View.GONE);
            }
        }

        // Click Listener pour ouvrir les détails
        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(context, SaleDetailsActivity.class);
            i.putExtra("sale_id", header.getId());
            context.startActivity(i);
        });
    }

    // Méthode de recherche d'une vente
    public void filter(String text) {
        saleHeaders.clear();

        if (text.isEmpty()) {
            saleHeaders.addAll(fullList); // Restaurer la liste complète
        } else {
            text = text.toLowerCase();
            for (SaleHeader s : fullList) {
                // Recherche par ID ou par nom du vendeur
                boolean matchId = String.valueOf(s.getId()).contains(text);
                boolean matchUser = s.getUsername() != null && s.getUsername().toLowerCase().contains(text);

                if (matchId || matchUser) {
                    saleHeaders.add(s);
                }
            }
        }
        notifyDataSetChanged();
    }

    // Permet de rafraîchir la liste complète après une synchro par exemple
    public void updateList(List<SaleHeader> newList) {
        this.saleHeaders.clear();
        this.saleHeaders.addAll(newList);
        this.fullList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return saleHeaders.size();
    }

    static class SaleHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSaleId, tvDate, tvUser, tvPayment, tvTotal, tvDiscountBadge;

        public SaleHeaderViewHolder(@NonNull View itemView) {
            super(itemView);

            // Liaison avec les IDs du layout item_sale_history.xml
            tvSaleId = itemView.findViewById(R.id.tvSaleId);
            tvDate = itemView.findViewById(R.id.tvSaleDate);
            tvUser = itemView.findViewById(R.id.tvSaleUser);
            tvPayment = itemView.findViewById(R.id.tvSalePayment);
            tvTotal = itemView.findViewById(R.id.tvSaleTotal);

            // Le badge rouge
            tvDiscountBadge = itemView.findViewById(R.id.tvDiscountBadge);
        }
    }
}