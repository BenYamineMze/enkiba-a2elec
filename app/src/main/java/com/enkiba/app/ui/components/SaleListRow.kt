package com.enkiba.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.theme.EnkibaTheme

/** Libellé d'un mode de paiement. */
fun paymentLabel(method: String?): String = when (method) {
    "espece" -> "Espèces"
    "virement" -> "Virement"
    "mobile_money" -> "Mobile money"
    "cheque" -> "Chèque"
    "carte" -> "Carte"
    else -> method.orEmpty()
}

/** Ligne de vente (accueil, historique) : paiement, moment, vendeur, montant net des retours. */
@Composable
fun SaleListRow(sale: SaleHeader, onClick: () -> Unit, modifier: Modifier = Modifier, showSeller: Boolean = false) {
    val colors = EnkibaTheme.colors
    ListRow(
        title = "Vente #${sale.shortRef}",
        subtitle = buildString {
            append(paymentLabel(sale.paymentMethod)).append(" · ").append(formatSaleMoment(sale.saleDate))
            if (showSeller && !sale.username.isNullOrBlank()) append(" · ").append(sale.username)
            if (sale.isHasNegotiatedPrice) append(" · négocié")
            if (sale.refundedAmount > 0) append(" · retour ").append(formatKmf(sale.refundedAmount))
        },
        onClick = onClick,
        modifier = modifier,
        leading = {
            if (sale.paymentMethod == "espece") IconBadge(Icons.Rounded.Payments, colors.mint.container, colors.link)
            else IconBadge(Icons.Rounded.AccountBalance, colors.sky.container, colors.sky.content)
        },
        trailing = { Text(formatKmf(sale.netTotal), style = MaterialTheme.typography.bodyLarge) },
    )
}
