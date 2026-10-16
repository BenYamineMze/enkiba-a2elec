package com.enkiba.app.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.enkiba.app.activities.HistoryActivity
import com.enkiba.app.activities.StatisticsActivity
import com.enkiba.app.ui.components.FloatingBottomBar
import com.enkiba.app.ui.components.NavDestination
import com.enkiba.app.ui.home.HomeActivity
import com.enkiba.app.ui.products.ProductsActivity
import com.enkiba.app.ui.sale.SaleActivity

/** Onglets de la barre de navigation (les statistiques sont réservées aux admins). */
enum class AppTab(val destination: NavDestination) {
    HOME(NavDestination("Accueil", Icons.Rounded.Home)),
    PRODUCTS(NavDestination("Produits", Icons.Rounded.Inventory2)),
    HISTORY(NavDestination("Historique", Icons.Rounded.History)),
    STATS(NavDestination("Stats", Icons.Rounded.BarChart)),
    ;

    companion object {
        fun visible(isAdmin: Boolean): List<AppTab> = if (isAdmin) entries else entries - STATS
    }
}

/** Barre de navigation commune à tous les écrans Compose principaux. */
@Composable
fun AppBottomBar(
    selected: AppTab,
    isAdmin: Boolean,
    onTab: (AppTab) -> Unit,
    onNewSale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = AppTab.visible(isAdmin)
    FloatingBottomBar(
        destinations = tabs.map { it.destination },
        selectedIndex = tabs.indexOf(selected),
        onSelect = { i -> if (tabs[i] != selected) onTab(tabs[i]) },
        centerActionLabel = "Nouvelle vente",
        onCenterAction = onNewSale,
        modifier = modifier,
    )
}

/** Ouverture des écrans depuis la barre (les écrans pas encore migrés restent en XML). */
object AppNavigator {

    fun open(activity: Activity, tab: AppTab, isAdmin: Boolean) {
        val intent = when (tab) {
            // L'accueil est la racine : on y revient sans empiler d'écrans
            AppTab.HOME -> Intent(activity, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            AppTab.PRODUCTS -> Intent(activity, ProductsActivity::class.java)
            AppTab.HISTORY -> Intent(activity, HistoryActivity::class.java)
                .putExtra(HistoryActivity.EXTRA_TAB, HistoryActivity.TAB_SALES)
            AppTab.STATS -> if (isAdmin) Intent(activity, StatisticsActivity::class.java) else return
        }
        activity.startActivity(intent)
    }

    fun newSale(activity: Activity) = activity.startActivity(Intent(activity, SaleActivity::class.java))
}
