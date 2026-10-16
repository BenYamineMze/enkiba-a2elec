package com.enkiba.app.utils;

import android.app.Activity;
import android.content.Intent;
import com.enkiba.app.R;
import com.enkiba.app.activities.HistoryActivity;
import com.enkiba.app.activities.StatisticsActivity;
import com.enkiba.app.activities.UserManagementActivity;
import com.enkiba.app.ui.home.HomeActivity;
import com.enkiba.app.ui.products.ProductsActivity;
import com.enkiba.app.ui.sale.SaleActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NavigationHelper {

    public static void setupNavigation(BottomNavigationView bottomNav, Activity activity) {

        // ✅ CRÉATION DE L'INSTANCE du SessionManager
        SessionManager sessionManager = new SessionManager(activity);

        // ✅ Récupérer le rôle (CORRIGÉ: Utilisation de l'instance non-statique)
        String role = sessionManager.getRole();

        // Items réservés à l'admin (l'historique reste visible : un vendeur y retrouve
        // ses ventes et ses devis)
        if ("user".equalsIgnoreCase(role)) {
            bottomNav.getMenu().findItem(R.id.nav_stats).setVisible(false);
            bottomNav.getMenu().findItem(R.id.nav_personnel).setVisible(false);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                // L'accueil (Compose) est la racine : on y revient sans empiler d'écrans
                activity.startActivity(new Intent(activity, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return true;
            }
            else if (id == R.id.nav_inventory) {
                if (!(activity instanceof ProductsActivity))
                    activity.startActivity(new Intent(activity, ProductsActivity.class));
                return true;
            }
            else if (id == R.id.nav_personnel) {
                if ("admin".equalsIgnoreCase(role)) {
                    if (!(activity instanceof UserManagementActivity))
                        activity.startActivity(new Intent(activity, UserManagementActivity.class));
                }
                return true;
            }
            else if (id == R.id.nav_history) {
                if (!(activity instanceof HistoryActivity))
                    activity.startActivity(new Intent(activity, HistoryActivity.class));
                return true;
            }
            else if (id == R.id.nav_sale) {
                if (!(activity instanceof SaleActivity))
                    activity.startActivity(new Intent(activity, SaleActivity.class));
                return true;
            }
            else if (id == R.id.nav_stats) {
                if (!(activity instanceof StatisticsActivity))
                    activity.startActivity(new Intent(activity, StatisticsActivity.class));
                return true;
            }

            return false;
        });
    }



    // ✅ Optionnel : sélection automatique de l’onglet
    public static void selectNavItem(BottomNavigationView bottomNav, int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }
}