package com.enkiba.app.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.EditProductActivity;
import com.enkiba.app.activities.ProductDetailActivity;
import com.enkiba.app.models.Product;
import com.enkiba.app.modelsDao.HistoryDao;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.utils.SessionManager;
import com.enkiba.app.utils.SynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> implements Filterable {

    private Context context;
    private List<Product> productList;      // Liste affichée (filtrée)
    private List<Product> productListFull;  // Copie complète pour la recherche
    private boolean isAdmin;

    // DAOs & Managers
    private ProductDao productDao;
    private HistoryDao historyDao;
    private SessionManager sessionManager;
    private SynchronizationManager syncManager;

    // Constructeur
    public ProductAdapter(Context context, ArrayList<Product> productList, boolean isAdmin) {
        this.context = context;
        this.productList = productList;
        // On crée une copie indépendante pour la recherche
        this.productListFull = new ArrayList<>(productList);
        this.isAdmin = isAdmin;

        // Initialisation des composants DB et Session
        this.productDao = new ProductDao(context);
        this.historyDao = new HistoryDao(context);
        this.sessionManager = new SessionManager(context);
        this.syncManager = new SynchronizationManager(context);

        // Tri alphabétique par défaut
        Collections.sort(this.productList, Comparator.comparing(Product::getName, String::compareToIgnoreCase));
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);

        // Affichage des données
        holder.txtName.setText(product.getName());
        holder.txtCategory.setText(product.getCategory());
        holder.txtQuantity.setText("Stock : " + product.getQuantity());
        // Formatage du prix (sans décimale inutile)
        holder.txtPrice.setText(String.format("%.0f KMF", product.getPrice()));

        // Gestion de la visibilité des boutons Admin
        if (isAdmin) {
            holder.action_Edit.setVisibility(View.VISIBLE);
            holder.action_Delete.setVisibility(View.VISIBLE);
        } else {
            holder.action_Edit.setVisibility(View.GONE);
            holder.action_Delete.setVisibility(View.GONE);
        }

        // Action : Voir Détails
        holder.action_Show.setOnClickListener(v -> {
            Intent i = new Intent(context, ProductDetailActivity.class);
            // [UUID] product.getId() est un String
            i.putExtra("id", product.getId());
            context.startActivity(i);
        });

        // Action : Modifier
        holder.action_Edit.setOnClickListener(v -> {
            Intent i = new Intent(context, EditProductActivity.class);
            // [UUID] On envoie l'ID String
            i.putExtra("PRODUCT_ID", product.getId());
            context.startActivity(i);
        });

        // Action : Supprimer (Soft Delete)
        holder.action_Delete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Supprimer")
                    .setMessage("Supprimer " + product.getName() + " ? L'article sera marqué pour suppression.")
                    .setPositiveButton("Oui", (dialog, which) -> {

                        // 1. Enregistrement Historique (product.getId() est String, historyDao est adapté)
                        historyDao.insertHistory(
                                product.getId(),
                                sessionManager.getUserId(),
                                "DELETE",
                                product.getName() + " (ID: " + product.getId() + ")",
                                "Supprimé localement (Soft Delete)"
                        );

                        // 2. Marquage Suppression (productDao attend un String)
                        boolean marked = productDao.markProductForDeletion(product.getId());

                        if (marked) {
                            // Supprimer de la liste affichée
                            productList.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, productList.size());

                            // Supprimer aussi de la liste complète pour la recherche
                            // [CORRECTION] Appel de la méthode adaptée pour String
                            removeFromFullList(product.getId());

                            // Lancer la synchro pour envoyer la suppression au serveur
                            try {
                                syncManager.startSynchronization();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            Toast.makeText(context, "Supprimé (Soft Delete) ✅", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Erreur lors de la suppression ❌", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        });
    }

    // [CORRECTION UUID] La méthode prend un String et utilise .equals()
    private void removeFromFullList(String id) {
        for (int i = 0; i < productListFull.size(); i++) {
            if (productListFull.get(i).getId().equals(id)) {
                productListFull.remove(i);
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    // --- FILTRE DE RECHERCHE ---

    @Override
    public Filter getFilter() {
        return productFilter;
    }

    public Filter filter() {
        return productFilter;
    }

    private final Filter productFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Product> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(productListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Product item : productListFull) {
                    if ((item.getName() != null && item.getName().toLowerCase().contains(filterPattern)) ||
                            (item.getQr_code() != null && item.getQr_code().toLowerCase().contains(filterPattern))) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            productList.clear();
            productList.addAll((List<Product>) results.values);
            notifyDataSetChanged();
        }
    };

    // --- ViewHolder ---

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtCategory, txtPrice, txtQuantity;
        ImageView imgIcon;
        ImageButton action_Edit, action_Delete, action_Show;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            txtName = itemView.findViewById(R.id.txtName);
            txtCategory = itemView.findViewById(R.id.txtCategory);
            txtPrice = itemView.findViewById(R.id.txtPrix);
            txtQuantity = itemView.findViewById(R.id.txtQuantity);

            action_Edit = itemView.findViewById(R.id.action_edit);
            action_Delete = itemView.findViewById(R.id.action_delete);
            action_Show = itemView.findViewById(R.id.action_Show);
        }
    }
}