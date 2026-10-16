package com.enkiba.app.ui.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.FilterPill
import com.enkiba.app.ui.components.FloatingBottomBar
import com.enkiba.app.ui.components.IconBadge
import com.enkiba.app.ui.components.ListRow
import com.enkiba.app.ui.components.NavDestination
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ProgressBar
import com.enkiba.app.ui.components.ProgressRing
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SecondaryButton
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.SegmentedTabs
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatTile
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.SyncState
import com.enkiba.app.ui.components.UserHeader
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/**
 * Catalogue du système de design (build debug uniquement) : tous les composants
 * communs, en clair ou en sombre, pour les vérifier sur appareil. Données d'exemple.
 */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by rememberSaveable { mutableStateOf(false) }
            EnkibaTheme(darkTheme = dark) {
                Catalog(dark = dark, onDarkChange = { dark = it })
            }
        }
    }
}

@Composable
private fun Catalog(dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    val colors = EnkibaTheme.colors
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var pill by remember { mutableIntStateOf(0) }
    var nav by remember { mutableIntStateOf(0) }
    var sync by remember { mutableStateOf(SyncState.SYNCED) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ScreenHeader(
                title = "Catalogue design",
                subtitle = "Charte v2 · données d'exemple · navigation non branchée",
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Sombre", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = dark, onCheckedChange = onDarkChange)
                    }
                },
            )

            // En-tête de l'accueil (utilisateur, synchronisation) : touche le bouton pour changer d'état
            UserHeader(
                firstName = "Mze",
                lastName = "Ben",
                roleLabel = "Admin",
                syncState = sync,
                onSync = { sync = SyncState.entries[(sync.ordinal + 1) % SyncState.entries.size] },
                onAvatarClick = {},
            )

            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                // Anneau + montant
                EnkibaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        ProgressRing(progress = 0.72f) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("72 %", style = MaterialTheme.typography.titleLarge)
                                Text("de l'objectif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Encaissé aujourd'hui", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AmountText(184_500.0)
                            StatusChip("+12 % vs hier", colors.mint)
                        }
                    }
                }

                // Tuiles
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(colors.mint, Icons.Rounded.ShoppingBag, "23", "Ventes du jour", Modifier.weight(1f), onClick = {})
                    StatTile(colors.peach, Icons.Rounded.Warning, "7", "Stock faible", Modifier.weight(1f), onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(colors.sky, Icons.Rounded.Description, "4", "Devis en cours", Modifier.weight(1f), onClick = {})
                    StatTile(colors.lavender, Icons.Rounded.BarChart, "1,24 M", "Valeur du stock (KMF)", Modifier.weight(1f), onClick = {})
                }

                // Boutons
                SectionHeader("Boutons")
                PrimaryButton("Encaisser", onClick = {}, modifier = Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward)
                SecondaryButton("Partager", onClick = {}, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SquareIconButton(Icons.Rounded.Sync, "Synchroniser", onClick = {})
                    SquareIconButton(Icons.Rounded.QrCodeScanner, "Scanner un code QR", onClick = {}, filled = true)
                }

                // Statuts
                SectionHeader("Statuts")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusChip("Brouillon", colors.neutral)
                    StatusChip("Accepté", colors.sky)
                    StatusChip("Converti", colors.mint)
                    StatusChip("Expiré", colors.peach)
                }
                StatusChip("Refusé", colors.danger)

                // Saisie et filtres
                SectionHeader("Recherche et filtres")
                SearchField(search, { search = it }, "Rechercher un article")
                SegmentedTabs(listOf("Tous", "Stock faible · 7", "Rupture · 2"), tab, { tab = it })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("Tout", "Câbles", "Éclairage").forEachIndexed { i, label ->
                        FilterPill(label, selected = i == pill, onClick = { pill = i })
                    }
                }

                // Listes
                SectionHeader("Dernières ventes", actionLabel = "Tout voir", onAction = {})
                ListRow(
                    title = "Vente #3F2A9C1B",
                    subtitle = "Espèces · 14:32",
                    onClick = {},
                    leading = { IconBadge(Icons.Rounded.Payments, colors.mint.container, colors.link) },
                    trailing = { Text(formatKmf(12_500.0), style = MaterialTheme.typography.bodyLarge) },
                )
                ListRow(
                    title = "Prise double",
                    subtitle = "Stock faible · 3 sur 20",
                    subtitleColor = colors.peach.content,
                    leading = {
                        ProgressRing(0.15f, size = 52.dp, strokeWidth = 6.dp, color = colors.warning, trackColor = colors.peach.container) {
                            Text("15 %", style = MaterialTheme.typography.labelSmall, color = colors.peach.content)
                        }
                    },
                    trailing = { Text(formatKmf(3_000.0), style = MaterialTheme.typography.bodyLarge) },
                )
                EnkibaCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Encaissé par jour", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.md))
                    ProgressBar(0.54f, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.sm))
                    ProgressBar(1f, Modifier.fillMaxWidth(), color = colors.warning, trackColor = colors.peach.container)
                }

                EmptyState("Aucun devis. Créez-en un depuis l'écran de vente.", actionLabel = "Nouvelle vente", onAction = {})
            }
        }

        FloatingBottomBar(
            destinations = listOf(
                NavDestination("Accueil", Icons.Rounded.Home),
                NavDestination("Produits", Icons.Rounded.Inventory2),
                NavDestination("Historique", Icons.Rounded.History),
                NavDestination("Stats", Icons.Rounded.BarChart),
            ),
            selectedIndex = nav,
            onSelect = { nav = it },
            centerActionLabel = "Nouvelle vente",
            onCenterAction = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}
