package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.NatureMouvementCarte
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CC5 : renseigner la nature bancaire d'un mouvement sans modifier ses écritures.
 * À la demande et sur « Gérer la carte », jamais en permanence sur Comptes.
 */
@Composable
fun NaturesMouvementsCarteCard(
    carte: CompteCredit,
    transactions: List<Transaction>,
    onClasser: (String, NatureMouvementCarte?) -> Unit
) {
    var developpe by remember(carte.id) { mutableStateOf(false) }
    var limite by remember(carte.id) { androidx.compose.runtime.mutableIntStateOf(50) }
    val classables = transactions.filter {
        it.type == TypeTransaction.Depense ||
            it.type == TypeTransaction.TransfertSortant ||
            it.type == TypeTransaction.Pret ||
            it.type == TypeTransaction.PaiementEffectue
    }.sortedByDescending { it.date }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Nature des mouvements", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { developpe = !developpe }) {
                    Text(if (developpe) "Masquer" else "Afficher")
                }
            }
            if (developpe) {
                Text(
                    "Classe les achats, avances de fonds ou transferts selon ton relevé. " +
                        "Ça ne crée aucune transaction ni aucun intérêt facturé.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (classables.isEmpty()) {
                    Text("Aucun mouvement à classer.", style = MaterialTheme.typography.bodySmall)
                }
                val dateForm = remember { SimpleDateFormat("d MMM yyyy", Locale.CANADA_FRENCH) }
                classables.take(limite).forEach { tx ->
                    var menuOuvert by remember(carte.id, tx.id) { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.tiersUtiliser?.takeIf { it.isNotBlank() }
                                ?: tx.description?.takeIf { it.isNotBlank() }
                                ?: tx.type.libelle, style = MaterialTheme.typography.bodyMedium)
                            Text("${dateForm.format(tx.date)} · ${MoneyFormatter.formatAmount(tx.montant)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            val nature = carte.details.naturesMouvements[tx.id]
                            TextButton(onClick = { menuOuvert = true }) {
                                Text(nature?.libelle ?: "À classer")
                            }
                            if (nature != null) {
                                val taux = MoteurCarteCredit.tauxPourNature(carte, nature, tx.date)
                                Text(taux?.let { "%.2f %%".format(Locale.CANADA_FRENCH, it) }
                                    ?: "Taux inconnu", style = MaterialTheme.typography.bodySmall)
                            }
                            DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                                DropdownMenuItem(text = { Text("Indéterminé") }, onClick = {
                                    menuOuvert = false
                                    onClasser(tx.id, null)
                                })
                                NatureMouvementCarte.entries.forEach { nature ->
                                    DropdownMenuItem(text = { Text(nature.libelle) }, onClick = {
                                        menuOuvert = false
                                        onClasser(tx.id, nature)
                                    })
                                }
                            }
                        }
                    }
                }
                if (classables.size > limite) {
                    TextButton(onClick = { limite += 50 }) {
                        Text("Afficher 50 autres mouvements (${classables.size - limite} restants)")
                    }
                }
            }
        }
    }
}
