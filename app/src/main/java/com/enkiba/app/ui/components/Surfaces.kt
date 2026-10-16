package com.enkiba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.ui.theme.Tile

/** Ombre très douce de la charte (6–10 %), teintée du texte principal. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 10.dp): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color(0x1417241F),
    spotColor = Color(0x1417241F),
)

/** Carte blanche arrondie : le conteneur de base des écrans. */
@Composable
fun EnkibaCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.xl,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .softShadow(shape)
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Pastille carrée arrondie contenant une icône (tuiles, lignes de liste). */
@Composable
fun IconBadge(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Tuile pastel de l'accueil : icône, chiffre, libellé. */
@Composable
fun StatTile(
    tile: Tile,
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(tile.container)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 104.dp)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, background = tile.iconBackground, tint = tile.content, size = 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = tile.content)
        }
    }
}

/** Puce de statut (BROUILLON, ACCEPTÉ, EXPIRÉ…). */
@Composable
fun StatusChip(label: String, tile: Tile, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = tile.content,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(tile.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
