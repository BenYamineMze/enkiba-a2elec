package com.enkiba.app.utils;

import android.app.Activity;
import android.content.Intent;
import com.enkiba.app.R;
import com.enkiba.app.activities.AcceuilActivity;
import com.enkiba.app.activities.HistoryActivity;
import com.enkiba.app.activities.ProductListActivity;
import com.enkiba.app.activities.SellProductActivity;
import com.enkiba.app.activities.StatisticsActivity;
import com.enkiba.app.activities.UserManagementActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NavigationHelper {

    public static void setupNavigation(BottomNavigationView bottomNav, Activity activity) {

        // ✅ CRÉATION DE L'INSTANCE du SessionManager
        SessionManager sessionManager = new SessionManager(activity);

        // ✅ Récupérer le rôle (CORRIGÉ: Utilisation de l'instance non-statique)
        String role = sessionManager.getRole();

        // ✅ Masquer les items réservés à l'admin
        if ("user".equalsIgnoreCase(role)) {
            //bottomNav.getMenu().findItem(R.id.nav_add).setVisible(false);
            bottomNav.getMenu().findItem(R.id.nav_stats).setVisible(false);
            bottomNav.getMenu().findItem(R.id.nav_personnel).setVisible(false);
            bottomNav.getMenu().findItem(R.id.nav_history).setVisible(false);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                if (!(activity instanceof AcceuilActivity))
                    activity.startActivity(new Intent(activity, AcceuilActivity.class));
                return true;
            }
            else if (id == R.id.nav_inventory) {
                if (!(activity instanceof ProductListActivity))
                    activity.startActivity(new Intent(activity, ProductListActivity.class));
                return true;
            }
            /*
            else if (id == R.id.nav_add) {
                if ("admin".equalsIgnoreCase(role)) { // ✅ Sécurité anti contournement
                    if (!(activity instanceof AddProductActivity))
                        activity.startActivity(new Intent(activity, AddProductActivity.class));
                }
                return true;
            }*/
            else if (id == R.id.nav_personnel) {
                if ("admin".equalsIgnoreCase(role)) {
                    if (!(activity instanceof UserManagementActivity))
                        activity.startActivity(new Intent(activity, UserManagementActivity.class));
                }
                return true;
            }
            else if (id == R.id.nav_history) {
                if ("admin".equalsIgnoreCase(role)) {
                    if (!(activity instanceof HistoryActivity))
                        activity.startActivity(new Intent(activity, HistoryActivity.class));
                }
                return true;
            }
            else if (id == R.id.nav_sale) {
                if (!(activity instanceof SellProductActivity))
                    activity.startActivity(new Intent(activity, SellProductActivity.class));
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