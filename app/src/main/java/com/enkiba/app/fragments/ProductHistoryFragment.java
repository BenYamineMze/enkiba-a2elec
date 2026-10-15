package com.enkiba.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.adapters.ProductHistoryAdapter;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.HistoryDao;
import com.enkiba.app.modelsDao.ProductDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.PDFUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductHistoryFragment extends Fragment {

    private static final String TAG = "ProductHistoryFragment";

    private RecyclerView recyclerProducts;
    private TextView txtEmpty;

    private HistoryDao historyDao;
    private ProductDao productDao;
    private UserDao userDao;

    private List<ProductHistory> productHistoryList;
    private FloatingActionButton btnExportPdf;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_product_history, container, false);

        recyclerProducts = view.findViewById(R.id.recyclerProductHistory);
        txtEmpty = view.findViewById(R.id.txtEmptyProduct);
        btnExportPdf = view.findViewById(R.id.btnExportPdf);

        // Initialisation des DAOs
        Context context = requireContext();
        this.historyDao = new HistoryDao(context);
        this.productDao = new ProductDao(context);
        this.userDao = new UserDao(context);

        recyclerProducts.setLayoutManager(new LinearLayoutManager(getContext()));

        // On charge les données dès la création de la vue
        loadProductHistory();

        btnExportPdf.setOnClickListener(v -> {
            if (productHistoryList == null || productHistoryList.isEmpty()) {
                Toast.makeText(requireContext(), "Aucun historique à exporter", Toast.LENGTH_SHORT).show();
                return;
            }

            // Confirmation avant suppression
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Exporter et supprimer ?")
                    .setMessage("Voulez-vous vraiment exporter puis supprimer l’historique des produits ? Cette action est irréversible localement (marquée comme archivée).")
                    .setPositiveButton("Oui", (dialog, which) -> {

                        btnExportPdf.setEnabled(false);

                        new Thread(() -> {
                            // Génération PDF pour ProductHistory
                            boolean success = PDFUtils.generateProductHistoryPdf(
                                    requireContext(),
                                    productHistoryList,
                                    "A2 ELEC - Historique des produits"
                            );

                            requireActivity().runOnUiThread(() -> {
                                btnExportPdf.setEnabled(true);

                                if (success) {
                                    // Suppression/Archivage via HistoryDao
                                    historyDao.clearProductHistory();

                                    productHistoryList.clear();
                                    if (recyclerProducts.getAdapter() != null)
                                        recyclerProducts.getAdapter().notifyDataSetChanged();

                                    txtEmpty.setVisibility(View.VISIBLE);
                                    recyclerProducts.setVisibility(View.GONE);

                                    Toast.makeText(requireContext(),
                                            "PDF généré avec succès.\nHistorique archivé localement.",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(requireContext(),
                                            "Erreur : Le PDF n'a pas été généré.\nAucune suppression effectuée.",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }).start();

                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProductHistory();
    }

    // Méthode utilitaire pour charger la liste et mettre à jour l'UI
    private void loadProductHistory() {
        if (historyDao == null) return;

        // Lecture des données brutes
        productHistoryList = historyDao.getProductHistory();

        if (productHistoryList == null || productHistoryList.isEmpty()) {
            Log.d(TAG, "Historique vide ou null");
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerProducts.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "Historique chargé : " + productHistoryList.size() + " éléments");

            // 🔹 HYDRATATION MANUELLE : On remplit les noms manquants 🔹
            // On charge tous les utilisateurs une fois pour optimiser la recherche
            List<User> allUsers = userDao.getAllUsers();

            for (ProductHistory h : productHistoryList) {
                // 1. Récupération du Nom du Produit si manquant
                if (h.getProductName() == null || h.getProductName().isEmpty() || "null".equals(h.getProductName())) {
                    Product p = productDao.getProductById(h.getProductId());
                    if (p != null) {
                        h.setProductName(p.getName());
                    } else {
                        h.setProductName("Produit #" + h.getProductId());
                    }
                }

                // 2. Récupération du Nom de l'Utilisateur
                String historyUserId = h.getUserId();
                User foundUser = null;

                if (historyUserId != null && allUsers != null) {
                    for (User u : allUsers) {
                        // On vérifie à la fois l'ID local et l'AuthID pour être sûr de trouver
                        boolean matchId = u.getId() != null && u.getId().equals(historyUserId);
                        boolean matchAuthId = u.getAuthId() != null && u.getAuthId().equals(historyUserId);

                        if (matchId || matchAuthId) {
                            foundUser = u;
                            break;
                        }
                    }
                }

                if (foundUser != null) {
                    h.setUsername((foundUser.getUsername() + " " + foundUser.getLastname()).trim());
                } else {
                    // Si on ne trouve pas l'utilisateur, on affiche l'ID brut pour aider au débogage
                    h.setUsername("Utilisateur inconnu");
                }
            }

            txtEmpty.setVisibility(View.GONE);
            recyclerProducts.setVisibility(View.VISIBLE);

            // Création sécurisée de l'ArrayList pour l'adaptateur
            ArrayList<ProductHistory> adapterList = new ArrayList<>(productHistoryList);
            ProductHistoryAdapter adapter = new ProductHistoryAdapter(getContext(), adapterList);
            recyclerProducts.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }
}