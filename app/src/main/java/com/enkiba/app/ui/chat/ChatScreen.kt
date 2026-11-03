package com.enkiba.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enkiba.app.data.repository.ChatMessage
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

data class ChatActions(
    val onBack: () -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onLongPress: (ChatMessage) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
)

@Composable
fun ChatScreen(state: ChatUiState, shopName: String, actions: ChatActions) {
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text("Retirer ce message ?") },
            text = { Text("Il disparaîtra pour toute l'équipe.") },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text("Retirer", color = EnkibaTheme.colors.danger.content) } },
            dismissButton = { TextButton(onClick = actions.onCancelDelete) { Text("Annuler") } },
        )
    }
    val items = remember(state.messages) { ChatFormat.items(state.messages) }
    val listState = rememberLazyListState()
    // Dernier message toujours visible (liste inversée : l'index 0 est en bas)
    LaunchedEffect(state.messages.lastOrNull()?.id) { if (items.isNotEmpty()) listState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        ScreenHeader(title = "Discussion", subtitle = shopName.ifBlank { null }?.let { "Toute l'équipe · $it" } ?: "Toute l'équipe", onBack = actions.onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                items.isEmpty() -> EmptyState("Aucun message. Écrivez le premier : toute l'équipe le verra.", Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items.asReversed(), key = { it.key }) { item ->
                        when (item) {
                            is ChatItem.Day -> Text(
                                item.label.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                            )
                            is ChatItem.Message -> Bubble(item, canDelete = item.message.mine || state.isAdmin, onLongPress = actions.onLongPress)
                        }
                    }
                }
            }
        }
        Composer(state.draft, actions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(item: ChatItem.Message, canDelete: Boolean, onLongPress: (ChatMessage) -> Unit) {
    val m = item.message
    val colors = EnkibaTheme.colors
    val shape = if (m.mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
        if (item.showSender) {
            Text(m.senderName, style = MaterialTheme.typography.labelMedium, color = colors.link,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape)
                .background(if (m.mine) colors.mint.container else MaterialTheme.colorScheme.surface)
                .then(if (canDelete) Modifier.combinedClickable(onClickLabel = null, onLongClickLabel = "Retirer le message", onLongClick = { onLongPress(m) }, onClick = {}) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(m.body, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.pending) {
                    Icon(Icons.Rounded.Schedule, contentDescription = "En attente d'envoi", modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Composer(draft: String, actions: ChatActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = actions.onDraft,
            placeholder = { Text("Message à l'équipe") },
            maxLines = 5,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        SquareIconButton(Icons.AutoMirrored.Rounded.Send, "Envoyer le message", onClick = actions.onSend, filled = true)
    }
}
