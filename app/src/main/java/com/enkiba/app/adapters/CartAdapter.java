package com.enkiba.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.CartItem;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

    public interface Callback { void onChanged(); }

    private Context ctx;
    private List<CartItem> items;
    private Callback callback;

    public CartAdapter(Context ctx, List<CartItem> items, Callback callback) {
        this.ctx = ctx;
        this.items = items;
        this.callback = callback;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_cart_v, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CartItem ci = items.get(position);
        holder.tvName.setText(ci.getName());
        holder.tvQty.setText(String.valueOf(ci.getQuantity()));
        holder.tvTotal.setText(String.format("%.0f KMF", ci.getTotal()));

        holder.btnPlus.setOnClickListener(v -> {
            ci.setQuantity(ci.getQuantity() + 1);
            notifyItemChanged(position);
            if (callback != null) callback.onChanged();
        });
        holder.btnMinus.setOnClickListener(v -> {
            if (ci.getQuantity() > 1) {
                ci.setQuantity(ci.getQuantity() - 1);
                notifyItemChanged(position);
                if (callback != null) callback.onChanged();
            }
        });
        holder.btnRemove.setOnClickListener(v -> {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size());
            if (callback != null) callback.onChanged();
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvTotal;
        ImageButton btnPlus, btnMinus, btnRemove;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvProductName);
            tvQty = v.findViewById(R.id.tvQty);
            tvTotal = v.findViewById(R.id.tvLineTotal);
            btnPlus = v.findViewById(R.id.btnPlus);
            btnMinus = v.findViewById(R.id.btnMinus);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }
}
