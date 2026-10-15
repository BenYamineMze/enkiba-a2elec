package com.enkiba.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.enkiba.app.R;
import com.enkiba.app.models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductAutoCompleteAdapter extends ArrayAdapter<Product> {

    private final List<Product> productListFull; // Liste complète pour le filtrage
    private final Context context;

    public ProductAutoCompleteAdapter(@NonNull Context context, @NonNull List<Product> productList) {
        super(context, 0, productList);
        this.context = context;
        this.productListFull = new ArrayList<>(productList);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return productFilter;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_dropdown_product, parent, false);
        }

        Product product = getItem(position);

        if (product != null) {
            TextView tvName = convertView.findViewById(R.id.tvDropdownName);
            TextView tvStock = convertView.findViewById(R.id.tvDropdownStock);

            tvName.setText(product.getName());

            // Gestion couleur stock
            tvStock.setText("(" + product.getQuantity() + ")");

            if (product.getQuantity() <= 5) {
                tvStock.setTextColor(0xFFD32F2F); // Rouge si stock bas
            } else {
                tvStock.setTextColor(0xFF666666); // Gris sinon
            }

            // Le prix a été retiré du design compact, on ne le traite plus ici.
        }

        return convertView;
    }

    private final Filter productFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<Product> suggestions = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                suggestions.addAll(productListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Product item : productListFull) {
                    if (item.getName().toLowerCase().contains(filterPattern)) {
                        suggestions.add(item);
                    }
                }
            }

            results.values = suggestions;
            results.count = suggestions.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            clear();
            if (results.values != null) {
                addAll((List) results.values);
            }
            notifyDataSetChanged();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return ((Product) resultValue).getName();
        }
    };
}