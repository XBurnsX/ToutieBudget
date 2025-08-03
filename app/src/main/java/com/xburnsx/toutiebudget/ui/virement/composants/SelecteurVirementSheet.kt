// chemin/simule: /ui/virement/composants/SelecteurVirementSheet.kt
package com.xburnsx.toutiebudget.ui.virement.composants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import com.xburnsx.toutiebudget.ui.virement.ItemVirement
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurVirementSheet(
    titre: String,
    itemsGroupes: Map<String, List<ItemVirement>>,
    onItemSelected: (ItemVirement) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        Column {
            Text(
                text = titre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
                color = Color.White
            )
            LazyColumn(modifier = Modifier.navigationBarsPadding()) {
                itemsGroupes.forEach { (categorie, items) ->
                    item {
                        Text(
                            text = categorie.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(items) { item ->
                        VirementItemRow(item = item, onClick = { onItemSelected(item) })
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VirementItemRow(item: ItemVirement, onClick: () -> Unit) {
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        val nom: String
        val montant: Double
        val couleurMontant: Color
        when (item) {
            is ItemVirement.CompteItem -> {
                val compte = item.compte
                nom = (compte as? CompteCheque)?.nom ?: "Compte"
                montant = compte.solde
                couleurMontant = compte.couleur.toColor()
            }
            is ItemVirement.EnveloppeItem -> {
                nom = item.enveloppe.nom
                montant = item.enveloppe.solde
                couleurMontant = when {
                    item.enveloppe.solde < 0 -> Color.Red
                    MoneyFormatter.isAmountZero(item.enveloppe.solde) -> Color.Gray
                    else -> item.enveloppe.couleurProvenance?.toColor() ?: Color.Green
                }
            }
        }
        Text(text = nom, modifier = Modifier.weight(1f), color = Color.White, fontSize = 16.sp)
        Text(text = formatteurMonetaire.format(montant), color = couleurMontant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
