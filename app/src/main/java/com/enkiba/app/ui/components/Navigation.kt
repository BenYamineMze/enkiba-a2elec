package com.enkiba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** Une destination de la barre de navigation. */
data class NavDestination(val label: String, val icon: ImageVector)

/**
 * Barre de navigation flottante (carte blanche) avec un bouton central « Nouvelle vente »,
 * centré verticalement dans la barre. Les destinations sont réparties de part et d'autre.
 */
@Composable
fun FloatingBottomBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    centerActionLabel: String,
    onCenterAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val half = (destinations.size + 1) / 2
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .softShadow(shape, 14.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, d ->
                if (index == half) {
                    // Bouton central « Nouvelle vente », centré dans la barre
                    SquareIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = centerActionLabel,
                        onClick = onCenterAction,
                        filled = true,
                        size = 52.dp,
                    )
                }
                NavItem(d, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun NavItem(destination: NavDestination, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) EnkibaTheme.colors.link else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .widthIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(destination.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing,
            ),
            color = color,
        )
    }
}

/** En-tête d'écran : retour facultatif, titre, sous-titre, actions à droite. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (onBack != null) {
            SquareIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Retour",
                onClick = onBack,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (subtitle != null && onBack == null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
            if (subtitle != null && onBack != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}
