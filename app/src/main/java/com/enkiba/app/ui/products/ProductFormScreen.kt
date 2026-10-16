package com.enkiba.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun ProductFormScreen(
    state: ProductFormUiState,
    onBack: () -> Unit,
    onChange: ((ProductForm) -> ProductForm) -> Unit,
    onSave: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    val colors = EnkibaTheme.colors
    val f = state.form
    val e = state.errors

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                title = if (state.isEdit) "Modifier l'article" else "Nouvel article",
                subtitle = state.original?.qr_code?.let { "Code $it" },
                onBack = onBack,
            )
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }
            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                EnkibaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        FieldWithError(e.name) {
                            EnkibaTextField(f.name, { v -> onChange { it.copy(name = v) } }, "Nom de l'article *",
                                isError = e.name != null, enabled = !state.saving,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        }
                        FieldWithError(e.category) {
                            CategoryField(f.category, state.categories, isError = e.category != null, enabled = !state.saving) { v ->
                                onChange { it.copy(category = v) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Box(Modifier.weight(1f)) {
                                FieldWithError(e.price) {
                                    EnkibaTextField(f.priceText, { v -> onChange { it.copy(priceText = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                                        "Prix (KMF) *", isError = e.price != null, enabled = !state.saving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                FieldWithError(e.quantity) {
                                    EnkibaTextField(f.quantityText, { v -> onChange { it.copy(quantityText = v.filter(Char::isDigit)) } },
                                        if (state.isEdit) "Stock actuel *" else "Stock initial *",
                                        isError = e.quantity != null, enabled = !state.saving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                }
                            }
                        }
                        state.stockDelta?.let { d ->
                            StatusChip(
                                if (d > 0) "Réassort : +$d (mouvement de stock)" else "Ajustement : $d (mouvement de stock)",
                                if (d > 0) colors.mint else colors.peach,
                            )
                        }
                        FieldWithError(e.threshold) {
                            EnkibaTextField(f.thresholdText, { v -> onChange { it.copy(thresholdText = v.filter(Char::isDigit)) } },
                                "Seuil d'alerte (stock faible)", isError = e.threshold != null, enabled = !state.saving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        OutlinedTextField(
                            value = f.description,
                            onValueChange = { v -> onChange { it.copy(description = v.take(500)) } },
                            label = { Text("Description") },
                            enabled = !state.saving,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                }
                if (!state.isEdit) {
                    Text(
                        "Un code QR est créé automatiquement. Le stock initial est enregistré comme un mouvement de stock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.failure?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.saving) {
                    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    PrimaryButton(
                        if (state.isEdit) "Enregistrer les modifications" else "Ajouter l'article",
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

@Composable
private fun FieldWithError(error: String?, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        field()
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Catégorie : liste proposée, ou saisie libre d'une nouvelle catégorie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, options: List<String>, isError: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = options.filter { value.isBlank() || it.contains(value.trim(), ignoreCase = true) || options.contains(value) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text("Catégorie *") },
            singleLine = true,
            isError = isError,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        ExposedDropdownMenu(expanded = expanded && enabled && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { onChange(c); expanded = false })
            }
        }
    }
}
