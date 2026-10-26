package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit.Strategie
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ModeSimulation(val libelle: String) {
    MINIMUM("Minimum"), FIXE("Montant fixe"), POURCENTAGE("% du solde"), DATE_CIBLE("Date cible")
}

/**
 * Calculateur + stratégies de remboursement (CC8), sur le moteur unique.
 * Compare toujours avec le paiement minimum pour montrer l'effet d'un remboursement accéléré (CC11).
 */
@Composable
fun SimulateurCarte(carte: CompteCredit) {
    val dette = MoteurCarteCredit.dette(carte)
    if (dette <= 0.005) return

    var mode by remember { mutableStateOf(ModeSimulation.FIXE) }
    var saisie by remember {
        mutableStateOf(MoteurCarteCredit.minimumAPayer(carte)?.montant?.let { "%.2f".format(Locale.US, it) } ?: "")
    }
    var tableauOuvert by remember { mutableStateOf(false) }
    val valeur = saisie.replace(',', '.').toDoubleOrNull()

    val paiementDateCible = remember(mode, saisie, carte) {
        if (mode == ModeSimulation.DATE_CIBLE) {
            valeur?.toInt()?.takeIf { it in 1..600 }?.let { MoteurCarteCredit.paiementPourDateCible(carte, it) }
        } else null
    }

    val resultat = remember(mode, saisie, carte, paiementDateCible) { when (mode) {
        ModeSimulation.MINIMUM -> MoteurCarteCredit.simuler(carte, Strategie.MINIMUM)
        ModeSimulation.FIXE -> valeur?.takeIf { it > 0 }?.let { MoteurCarteCredit.simuler(carte, Strategie.FIXE, it) }
        ModeSimulation.POURCENTAGE -> valeur?.takeIf { it > 0 && it <= 100 }?.let { MoteurCarteCredit.simuler(carte, Strategie.POURCENTAGE, it) }
        ModeSimulation.DATE_CIBLE -> paiementDateCible?.let { MoteurCarteCredit.simuler(carte, Strategie.FIXE, it) }
    } }
    val referenceMinimum = remember(carte) { MoteurCarteCredit.simuler(carte, Strategie.MINIMUM) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simuler le remboursement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ModeSimulation.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.libelle) })
                }
            }
            if (mode != ModeSimulation.MINIMUM) {
                OutlinedTextField(
                    value = saisie,
                    onValueChange = { saisie = it },
                    label = {
                        Text(
                            when (mode) {
                                ModeSimulation.FIXE -> "Versement par mois ($)"
                                ModeSimulation.POURCENTAGE -> "Pourcentage du solde (%)"
                                else -> "Rembourser en (mois)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            paiementDateCible?.let {
                Ligne("Versement mensuel nécessaire", MoneyFormatter.formatAmount(it), gras = true)
            }

            if (resultat != null) {
                Ligne(
                    "Durée",
                    resultat.nombreMois?.let { duree(it) } ?: "impossible",
                    couleur = if (resultat.nombreMois == null) Color(0xFFEF4444) else Color.White,
                    gras = true
                )
                Ligne("Intérêts estimés", MoneyFormatter.formatAmount(resultat.totalInterets))
                if (resultat.totalFrais > 0.005) Ligne("Frais", MoneyFormatter.formatAmount(resultat.totalFrais))
                Ligne("Total payé", MoneyFormatter.formatAmount(resultat.totalPaye))
                resultat.avertissement?.let { Text(it, color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall) }

                // CC11 : effet par rapport au minimum
                val nMin = referenceMinimum.nombreMois
                val nCourant = resultat.nombreMois
                if (mode != ModeSimulation.MINIMUM && nCourant != null && nMin != null) {
                    val economie = referenceMinimum.totalInterets - resultat.totalInterets
                    if (economie > 0.005) {
                        Text(
                            "Par rapport au minimum : ${MoneyFormatter.formatAmount(economie)} d'intérêts en moins, " +
                                "${duree(nMin - nCourant)} plus tôt.",
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (resultat.lignes.isNotEmpty()) {
                    TextButton(onClick = { tableauOuvert = true }) { Text("Voir le tableau mois par mois") }
                }
                Text(
                    "Estimation : taux annuel ÷ 365 par jour, minimum recalculé chaque mois, frais ponctuels non projetés.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (tableauOuvert && resultat != null) {
        TableauRemboursement(resultat, onFermer = { tableauOuvert = false })
    }
}

@Composable
private fun TableauRemboursement(resultat: MoteurCarteCredit.ResultatSimulation, onFermer: () -> Unit) {
    val format = SimpleDateFormat("MMM yyyy", Locale.CANADA_FRENCH)
    Dialog(onDismissRequest = onFermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row {
                    Text("Tableau mensuel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onFermer) { Text("Fermer") }
                }
                val scroll = rememberScrollState()
                Column(modifier = Modifier.weight(1f).horizontalScroll(scroll)) {
                    Row { listOf("Mois", "Début", "Intérêts", "Frais", "Paiement", "Capital", "Fin").forEach { Cellule(it, gras = true) } }
                    LazyColumn {
                        items(resultat.lignes, key = { it.mois }) { l ->
                            Row {
                                Cellule(format.format(l.date))
                                Cellule(MoneyFormatter.formatAmount(l.soldeInitial))
                                Cellule(MoneyFormatter.formatAmount(l.interets))
                                Cellule(MoneyFormatter.formatAmount(l.frais))
                                Cellule(MoneyFormatter.formatAmount(l.paiement))
                                Cellule(MoneyFormatter.formatAmount(l.capital))
                                Cellule(MoneyFormatter.formatAmount(l.soldeFinal))
                            }
                        }
                    }
                }
                Text(
                    "Total des intérêts : ${MoneyFormatter.formatAmount(resultat.totalInterets)} · total payé : ${MoneyFormatter.formatAmount(resultat.totalPaye)}",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Cellule(texte: String, gras: Boolean = false) {
    Text(
        texte,
        modifier = Modifier.width(96.dp).padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal
    )
}

private fun duree(mois: Int): String {
    val ans = mois / 12
    val reste = mois % 12
    return listOfNotNull(
        if (ans > 0) "$ans an${if (ans > 1) "s" else ""}" else null,
        if (reste > 0 || ans == 0) "$reste mois" else null
    ).joinToString(" ")
}

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color = Color.White, gras: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(libelle, color = Color.LightGray, modifier = Modifier.weight(1f))
        Text(valeur, color = couleur, fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal)
    }
}
