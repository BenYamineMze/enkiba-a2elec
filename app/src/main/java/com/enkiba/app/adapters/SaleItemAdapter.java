package com.enkiba.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
// import com.enkiba.app.database.DatabaseHelper; // 🚨 SUPPRIMÉ 🚨
import com.enkiba.app.models.SaleItemRow;

import java.util.List;
import java.util.Locale;

public class SaleItemAdapter extends RecyclerView.Adapter<SaleItemAdapter.SaleItemHolder> {

    private final Context context;
    private final List<SaleItemRow> list;
    // private final DatabaseHelper db; // 🚨 SUPPRIMÉ 🚨

    // 🚨 CORRECTION 1 : Constructeur simplifié 🚨
    public SaleItemAdapter(Context context, List<SaleItemRow> list) {
        this.context = context;
        this.list = list;
        // La dépendance db est retirée.
    }

    @NonNull
    @Override
    public SaleItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_sale_item, parent, false);
        return new SaleItemHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SaleItemHolder holder, int position) {
        SaleItemRow item = list.get(position);

        // 🚨 CORRECTION 2 : Utilisation directe du nom du produit dans SaleItemRow 🚨
        // On suppose que SaleItemRow.getProductName() retourne le nom
        String productName = item.getProductName();

        // Product produit = db.getProductById(item.getProductId()); // 🚨 SUPPRIMÉ 🚨
        // if (produit == null) return; // Si le produit n'est pas trouvé, le nom sera null/vide

        // String productName = produit.getName(); // 🚨 SUPPRIMÉ 🚨

        int qty = item.getQuantity();
        double price = item.getUnitPrice();
        double total = item.getTotal(); // Utiliser le total précalculé dans la row si disponible, sinon recalculer.

        // Si total n'est pas précalculé :
        // double total = qty * price;

        holder.tvItemName.setText(productName);
        holder.tvItemTotal.setText(String.format(Locale.getDefault(), "%,.0f KMF", total));
        holder.tvItemDetails.setText(qty + " x " + String.format(Locale.getDefault(), "%,.0f KMF", price));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class SaleItemHolder extends RecyclerView.ViewHolder {

        TextView tvItemName, tvItemTotal, tvItemDetails;

        public SaleItemHolder(@NonNull View itemView) {
            super(itemView);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvItemTotal = itemView.findViewById(R.id.tvItemTotal);
            tvItemDetails = itemView.findViewById(R.id.tvItemDetails);
        }
    }
}