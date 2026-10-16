package com.enkiba.app.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.Quote;
import com.enkiba.app.utils.CurrencyUtils;
import com.enkiba.app.utils.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liste des devis. Réutilise la carte des ventes (item_sale) :
 * référence, date, client, validité, total, et le badge pour le statut.
 */
public class QuoteAdapter extends RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder> {

    public interface OnQuoteClickListener {
        void onQuoteClick(Quote quote);
    }

    private final List<Quote> quotes;
    private final List<Quote> fullList;
    private final OnQuoteClickListener listener;

    public QuoteAdapter(List<Quote> quotes, OnQuoteClickListener listener) {
        this.quotes = new ArrayList<>(quotes);
        this.fullList = new ArrayList<>(quotes);
        this.listener = listener;
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sale, parent, false);
        return new QuoteViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote q = quotes.get(position);
        holder.tvRef.setText("Devis " + q.getShortRef());
        holder.tvDate.setText(DatabaseUtils.formatIsoToDisplay(q.getCreatedAt()));
        String client = q.getClientName();
        holder.tvClient.setText("Client : " + (client != null && !client.isEmpty() ? client : "—"));
        holder.tvValidity.setText("Valable jusqu'au : " + (q.getValidUntil() != null ? q.getValidUntil() : "—"));
        holder.tvTotal.setText("Total : " + CurrencyUtils.formatKMF(q.getTotalPrice()));
        if (holder.tvStatus != null) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(q.getStatusLabel().toUpperCase(Locale.FRENCH));
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onQuoteClick(q);
        });
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    /** Filtre par référence (début de l'UUID) ou nom du client, sans tenir compte de la casse. */
    @SuppressLint("NotifyDataSetChanged")
    public void filter(String text) {
        quotes.clear();
        String t = text.toLowerCase(Locale.ROOT).replace("dev-", "");
        for (Quote q : fullList) {
            boolean matchRef = q.getId() != null && q.getId().toLowerCase(Locale.ROOT).contains(t);
            boolean matchClient = q.getClientName() != null && q.getClientName().toLowerCase(Locale.ROOT).contains(t);
            if (t.isEmpty() || matchRef || matchClient) quotes.add(q);
        }
        notifyDataSetChanged();
    }

    static class QuoteViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRef, tvDate, tvClient, tvValidity, tvTotal, tvStatus;

        QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRef = itemView.findViewById(R.id.tvSaleId);
            tvDate = itemView.findViewById(R.id.tvSaleDate);
            tvClient = itemView.findViewById(R.id.tvSaleUser);
            tvValidity = itemView.findViewById(R.id.tvSalePayment);
            tvTotal = itemView.findViewById(R.id.tvSaleTotal);
            tvStatus = itemView.findViewById(R.id.tvDiscountBadge);
        }
    }
}
