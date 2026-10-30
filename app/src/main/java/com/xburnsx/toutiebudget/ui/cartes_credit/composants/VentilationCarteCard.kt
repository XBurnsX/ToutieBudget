package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter

/**
 * CC5 : à la demande, renseigner les soldes courants par nature.
 * Ce n'est jamais une facturation ni une modification du solde du compte.
 */
@Composable
fun VentilationCarteCard(
    carte: CompteCredit,
    onEnregistrer: (achats: String, avances: String, transferts: String) -> Unit,
    onEffacer: () -> Unit
) {
    var developpe by remember(carte.id) { mutableStateOf(false) }
    val donnee = carte.details.ventilationCourante
    var achats by remember(carte.id, donnee) { mutableStateOf(donnee?.achats?.toString() ?: "") }
    var avances by remember(carte.id, donnee) { mutableStateOf(donnee?.avances?.toString() ?: "") }
    var transferts by remember(carte.id, donnee) { mutableStateOf(donnee?.transferts?.toString() ?: "") }
    val estimation = MoteurCarteCredit.interetsFutursVentiles(carte)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Intérêts selon les types de soldes",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { developpe = !developpe }) {
                    Text(if (developpe) "Masquer" else "Afficher")
                }
            }
            if (developpe) {
                Text(
                    "Saisis les soldes DUS actuels indiqués par ta banque. " +
                        "Leur total doit égaler ${MoneyFormatter.formatAmount(MoteurCarteCredit.dette(carte))}. " +
                        "Aucun montant n'est débité ni crédité.",
                    style = MaterialTheme.typography.bodySmall
                )
                com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantClavier(
                    valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(achats),
                    onValeurChange = { achats = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it) },
                    libelle = "Solde achats ($)",
                    modifier = Modifier.fillMaxWidth()
                )
                com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantClavier(
                    valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(avances),
                    onValeurChange = { avances = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it) },
                    libelle = "Solde avances de fonds ($)",
                    modifier = Modifier.fillMaxWidth()
                )
                com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantClavier(
                    valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(transferts),
                    onValeurChange = { transferts = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it) },
                    libelle = "Solde transferts de solde ($)",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEnregistrer(achats, avances, transferts) },
                        enabled = !carte.estArchive) { Text("Enregistrer") }
                    if (donnee != null) {
                        TextButton(onClick = onEffacer) { Text("Effacer la ventilation") }
                    }
                }
                if (estimation != null) {
                    Text("Projection indicative des ${estimation.jours} prochains jours, à soldes constants",
                        fontWeight = FontWeight.Medium)
                    Text("Achats : ${MoneyFormatter.formatAmount(estimation.achats)}")
                    Text("Avances : ${MoneyFormatter.formatAmount(estimation.avances)}")
                    Text("Transferts : ${MoneyFormatter.formatAmount(estimation.transferts)}")
                    Text("Total estimé : ${MoneyFormatter.formatAmount(estimation.total)}",
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Cette projection tient compte des taux par type et de leur date de validité, " +
                            "mais pas des paiements, achats ou frais futurs, ni des règles exactes de grâce de la banque.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        if (donnee == null)
                            "Aucune ventilation enregistrée."
                        else
                            "Ventilation devenue périmée, incomplète ou taux manquant : " +
                                "actualise les soldes et les taux pour estimer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
