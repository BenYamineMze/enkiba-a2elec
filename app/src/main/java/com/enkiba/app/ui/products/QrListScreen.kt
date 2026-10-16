package com.enkiba.app.ui.products

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.utils.QrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QrListActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onZoom: (Product?) -> Unit = {},
    val onExport: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Images des codes déjà calculées (le calcul d'un QR 500 × 500 prend du temps). */
private class QrCache {
    private val map = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 120
    }
    @Synchronized fun get(code: String): Bitmap? = map[code]
    @Synchronized fun put(code: String, bmp: Bitmap) { map[code] = bmp }
}

@Composable
private fun rememberQrBitmap(code: String, cache: QrCache): Bitmap? {
    val bmp by produceState(cache.get(code), code) {
        if (value == null) value = withContext(Dispatchers.Default) { QrUtils.generateQrBitmap(code)?.also { cache.put(code, it) } }
    }
    return bmp
}

@Composable
fun QrListScreen(state: QrListUiState, actions: QrListActions) {
    val snackbar = remember { SnackbarHostState() }
    val cache = remember { QrCache() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() } }
    val visible = state.visible

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Codes QR",
                subtitle = if (state.loading) null else "${visible.size} article${if (visible.size > 1) "s" else ""}",
                onBack = actions.onBack,
                actions = {
                    if (state.exporting) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    else SquareIconButton(Icons.Rounded.PictureAsPdf, "Exporter la planche en PDF", onClick = actions.onExport)
                },
            )
            SearchField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = "Article, catégorie ou code",
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                visible.isEmpty() -> EmptyState(
                    if (state.query.isBlank()) "Aucun article n'a de code QR." else "Aucun article ne correspond à « ${state.query.trim()} ».",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(visible, key = { it.id }) { p -> QrCard(p, cache, onClick = { actions.onZoom(p) }) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Le PDF reprend les articles affichés : filtrez avant d'exporter pour imprimer une partie seulement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    state.zoomed?.let { p ->
        val code = p.qr_code.orEmpty()
        AlertDialog(
            onDismissRequest = { actions.onZoom(null) },
            title = { Text(p.name.orEmpty()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    QrImage(rememberQrBitmap(code, cache), Modifier.fillMaxWidth())
                    Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { actions.onZoom(null) }) { Text("Fermer") } },
        )
    }
}

/** QR toujours noir sur blanc, même en mode sombre : sinon les lecteurs le lisent mal. */
@Composable
private fun QrImage(bmp: Bitmap?, modifier: Modifier) {
    Box(
        modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color.White).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp == null) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
        else Image(bmp.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun QrCard(p: Product, cache: QrCache, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = "Agrandir le code", onClick = onClick).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        QrImage(rememberQrBitmap(p.qr_code.orEmpty(), cache), Modifier.fillMaxWidth())
        Text(p.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        p.category?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
