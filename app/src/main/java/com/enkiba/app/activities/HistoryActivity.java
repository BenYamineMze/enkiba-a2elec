package com.enkiba.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.enkiba.app.R;
import com.enkiba.app.adapters.HistoryPagerAdapter;
import com.enkiba.app.utils.NavigationHelper;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

// L'activité hérite de BaseActivity pour la vérification de session et le délai d'inactivité
public class HistoryActivity extends BaseActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Ajout de l'instance SessionManager
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Appelle super.onCreate() de BaseActivity, qui initialise la session
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Initialisation de l'instance SessionManager
        sessionManager = new SessionManager(this);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        ImageView imgUser = findViewById(R.id.logout);
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        ImageView imgHeaderIcon = findViewById(R.id.imgHeaderIcon);

        // Modifier titre et icône selon la page
        tvHeaderTitle.setText("Historique");
        imgHeaderIcon.setImageResource(R.drawable.ic_history);

        // Déconnexion
        imgUser.setOnClickListener(v -> {
            showUserMenu(v);
        });

        // Adapter avec deux fragments
        HistoryPagerAdapter adapter = new HistoryPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Liaison des onglets avec le ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) tab.setText("Historique des ventes");
            else if (position == 1) tab.setText(R.string.tab_quotes);
            else tab.setText("Operations des articles ");
        }).attach();

        // ✅ Navigation bar
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationHelper.setupNavigation(bottomNav, this);
        bottomNav.getMenu().findItem(R.id.nav_history).setChecked(true);
    }

    private void showUserMenu(android.view.View view) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                // CORRECTION: Utiliser la méthode d'instance logout() qui gère le clear et la redirection
                sessionManager.logout();
                // Les lignes suivantes ne sont plus nécessaires car logout() fait la redirection + finish()
                // startActivity(new Intent(HistoryActivity.this, LoginActivity.class));
                // finish();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }
}