package com.enkiba.app.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.R;

import java.text.DecimalFormat;
import java.util.List;

public class CartBottomSheetAdapter extends RecyclerView.Adapter<CartBottomSheetAdapter.ViewHolder> {

    private List<SaleItemRow> items;
    private CartListener listener;

    public interface CartListener {
        void onCartUpdated();
    }

    public CartBottomSheetAdapter(List<SaleItemRow> items, CartListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Assurez-vous que le nom du layout XML est bien item_cart_v
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_v, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SaleItemRow item = items.get(position);
        DecimalFormat df = new DecimalFormat("#,###");

        holder.tvProductName.setText(item.getProductName());

        // Affichage du prix unitaire en petit
        holder.tvUnitPrice.setText(df.format(item.getUnitPrice()) + " KMF / u");

        // Affichage du total de la ligne en gras
        holder.tvLineTotal.setText(df.format(item.getTotal()) + " KMF");

        holder.tvQty.setText(String.valueOf(item.getQuantity()));

        holder.btnPlus.setOnClickListener(v -> {
            item.setQuantity(item.getQuantity() + 1);
            item.setTotal(item.getUnitPrice() * item.getQuantity());
            notifyItemChanged(position);
            listener.onCartUpdated();
        });

        holder.btnMinus.setOnClickListener(v -> {
            if (item.getQuantity() > 1) {
                item.setQuantity(item.getQuantity() - 1);
                item.setTotal(item.getUnitPrice() * item.getQuantity());
                notifyItemChanged(position);
                listener.onCartUpdated();
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size());
            listener.onCartUpdated();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvUnitPrice, tvLineTotal, tvQty;
        ImageButton btnPlus, btnMinus, btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
            tvQty = itemView.findViewById(R.id.tvQty);

            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}