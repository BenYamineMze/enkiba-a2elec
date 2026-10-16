package com.enkiba.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.SaleDetailsActivity;
import com.enkiba.app.adapters.SaleHeaderAdapter;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.User; // Import ajouté
import com.enkiba.app.modelsDao.SaleDao;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.utils.PDFUtils;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class SalesHistoryFragment extends Fragment {

    RecyclerView recyclerSales;
    SaleHeaderAdapter saleAdapter;

    private SaleDao saleDao;
    private UserDao userDao;

    List<SaleHeader> salesList;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_sales_history, container, false);

        TextInputEditText edtSearchSale = view.findViewById(R.id.edtSearchSale);

        sessionManager = new SessionManager(getContext());

        // Initialisation des DAOs
        this.saleDao = new SaleDao(getContext());
        this.userDao = new UserDao(getContext());

        recyclerSales = view.findViewById(R.id.recyclerSalesHistory);
        recyclerSales.setLayoutManager(new LinearLayoutManager(getContext()));

        FloatingActionButton btnExportPdf = view.findViewById(R.id.btnExportPdf);

        // Méthode de recherche
        edtSearchSale.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (saleAdapter != null) {
                    saleAdapter.filter(s.toString()); // Filtrage dynamique
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Charger les ventes
        loadSalesData();

        // Affichage dans le RecyclerView
        if (salesList == null || salesList.isEmpty()) {
            // Toast.makeText(getContext(), "Aucune vente enregistrée", Toast.LENGTH_LONG).show();
            // On évite le toast ici pour ne pas spammer au démarrage si vide
        } else {
            saleAdapter = new SaleHeaderAdapter(getContext(), salesList, this::openSaleDetails);
            recyclerSales.setAdapter(saleAdapter);
        }

        // Export PDF
        btnExportPdf.setOnClickListener(v -> {
            if (salesList == null || salesList.isEmpty()) {
                Toast.makeText(requireContext(), "Aucun historique à exporter", Toast.LENGTH_SHORT).show();
                return;
            }

            // Export PDF de l'historique (plus d'archivage : les ventes restent en base)
            btnExportPdf.setEnabled(false);
            new Thread(() -> {
                boolean success = PDFUtils.generateSalesReportPdf(
                        requireContext(),
                        salesList,
                        "A2 ELEC - Historique des ventes"
                );
                requireActivity().runOnUiThread(() -> {
                    btnExportPdf.setEnabled(true);
                    Toast.makeText(requireContext(),
                            success ? "PDF généré." : "Erreur lors de la génération du PDF.",
                            Toast.LENGTH_LONG).show();
                });
            }).start();
        });

        return view;
    }

    private void loadSalesData() {
        String role = sessionManager.getRole();
        String userId = sessionManager.getUserId();

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(getContext(), "Erreur : ID utilisateur non valide.", Toast.LENGTH_LONG).show();
            salesList = null;
            return;
        }

        // 🔹 Charger les ventes selon le rôle 🔹
        if ("admin".equalsIgnoreCase(role)) {
            salesList = saleDao.getAllSaleHeaders();
        } else {
            salesList = saleDao.getSaleHeadersByUser(userId);
        }

        // 🔹 CORRECTION ROBUSTE : Récupérer le nom du vendeur 🔹
        // On récupère tous les utilisateurs pour faire une recherche croisée (ID ou AuthID)
        if (salesList != null && !salesList.isEmpty() && userDao != null) {
            List<User> allUsers = userDao.getAllUsers();

            for (SaleHeader sale : salesList) {
                String saleUserId = sale.getUserId();
                User foundUser = null;

                // Recherche dans la liste locale : on vérifie si l'ID de la vente correspond
                // soit à l'ID local (ex: "117"), soit à l'AuthID (UUID Supabase)
                if (saleUserId != null) {
                    for (User u : allUsers) {
                        boolean matchId = u.getId() != null && u.getId().equals(saleUserId);
                        boolean matchAuthId = u.getAuthId() != null && u.getAuthId().equals(saleUserId);

                        if (matchId || matchAuthId) {
                            foundUser = u;
                            break;
                        }
                    }
                }

                if (foundUser != null) {
                    String nom = foundUser.getLastname() != null ? foundUser.getLastname() : "";
                    String prenom = foundUser.getUsername() != null ? foundUser.getUsername() : "";
                    sale.setUsername((prenom + " " + nom).trim());
                } else {
                    sale.setUsername("Vendeur inconnu");
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSalesData();
        if (saleAdapter != null && salesList != null) {
            // Recréer l'adaptateur pour rafraîchir la vue avec les nouvelles données
            saleAdapter = new SaleHeaderAdapter(getContext(), salesList, this::openSaleDetails);
            recyclerSales.setAdapter(saleAdapter);
        }
    }

    private void openSaleDetails(SaleHeader header) {
        Intent intent = new Intent(getContext(), SaleDetailsActivity.class);
        intent.putExtra("sale_id", header.getId());
        startActivity(intent);
    }
}