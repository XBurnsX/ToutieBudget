package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

private val couleurAlerte = Color(0xFFEF4444)
private val couleurOk = Color(0xFF10B981)

/**
 * Résumé de la carte : tous les chiffres viennent de MoteurCarteCredit (CC1).
 * « Officiel » = relevé de la banque ; « estimé » = calcul de l'app, jamais présenté comme confirmé.
 */
@Composable
fun CarteCreditDetailCard(
    carte: CompteCredit,
    transactionsCarte: List<Transaction>
) {
    val formatJour = SimpleDateFormat("d MMM yyyy", Locale.CANADA_FRENCH)
    val dette = MoteurCarteCredit.dette(carte)
    val disponible = MoteurCarteCredit.creditDisponible(carte)
    val utilisation = MoteurCarteCredit.tauxUtilisation(carte)
    val minimum = MoteurCarteCredit.minimumRestantSelonTransactions(carte, transactionsCarte)
    val releve = MoteurCarteCredit.dernierReleve(carte)
    val cycle = MoteurCarteCredit.interetsCycleCourant(carte, transactionsCarte)
    val d = carte.details

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${carte.nom} · ${d.emetteur.libelle}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Solde courant SIGNÉ (CC3)
            Ligne(
                "Solde actuel",
                MoneyFormatter.formatAmount(carte.solde),
                couleur = when {
                    carte.solde < -0.005 -> couleurAlerte
                    carte.solde > 0.005 -> couleurOk
                    else -> Color.White
                },
                gras = true
            )
            if (carte.solde > 0.005) {
                Text("Solde créditeur (trop-payé) : aucune dette.", color = couleurOk, style = MaterialTheme.typography.bodySmall)
            }
            Ligne("Limite", MoneyFormatter.formatAmount(carte.limiteCredit))
            Ligne(
                "Crédit disponible",
                MoneyFormatter.formatAmount(disponible),
                couleur = if (disponible < 0) couleurAlerte else Color.White
            )

            // Utilisation non plafonnée : un dépassement reste visible
            LinearProgressIndicator(
                progress = { utilisation.toFloat().coerceIn(0f, 1f) },
                color = if (utilisation > 1.0) couleurAlerte else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text(
                "Utilisation : ${"%.1f".format(utilisation * 100)} %" + if (utilisation > 1.0) " — limite dépassée" else "",
                color = if (utilisation > 1.0) couleurAlerte else Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            // Montant dû au relevé, distinct du solde courant (CC1, CC4)
            if (releve != null) {
                Ligne("Solde du relevé (${releve.dateEmission})", MoneyFormatter.formatAmount(releve.soldeOfficiel) + " · officiel")
            }
            if (minimum != null) {
                MoteurCarteCredit.dernierReleve(carte)?.minimumOfficiel?.let { officiel ->
                    Ligne("Minimum officiel du relevé", MoneyFormatter.formatAmount(officiel))
                }
                Ligne(
                    "Minimum restant (selon l'app)",
                    MoneyFormatter.formatAmount(minimum.montant) + if (minimum.officiel) " · officiel" else " · estimé",
                    gras = true
                )
                Text(minimum.source, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            MoteurCarteCredit.prochaineEcheance(carte)?.let { echeance ->
                val echeanceReleve = MoteurCarteCredit.lireDate(releve?.dateEcheance)
                val officielle = echeanceReleve != null &&
                    MoteurCarteCredit.ecrireDate(echeanceReleve) == MoteurCarteCredit.ecrireDate(echeance)
                Ligne(if (officielle) "Échéance du relevé (officielle)"
                    else "Prochaine échéance estimée", formatJour.format(echeance))
            }
            MoteurCarteCredit.prochaineCloture(carte)?.let { Ligne("Prochain relevé", formatJour.format(it)) }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            // Taux (CC10)
            Ligne("Taux achats", taux(MoteurCarteCredit.tauxAchats(carte)))
            d.tauxAvances?.let { Ligne("Taux avances de fonds", taux(it)) }
            d.tauxTransferts?.let { Ligne("Taux transferts de solde", taux(it)) }
            d.tauxPromo?.let { promotion ->
                Ligne("Taux promotionnel", taux(promotion) +
                    (d.debutPromo?.let { " dès le ${it}" } ?: "") +
                    (d.finPromo?.let { " jusqu'au ${it}" } ?: ""))
            }
            d.tauxRetard?.let { Ligne("Taux de retard", taux(it)) }

            // Intérêts : estimation seulement (CC5)
            if (dette > 0.005) {
                val tauxAchatsConnu = MoteurCarteCredit.tauxPourNature(
                    carte, com.xburnsx.toutiebudget.data.modeles.NatureMouvementCarte.ACHAT
                ) != null
                if (!tauxAchatsConnu) {
                    Ligne("Intérêts estimés (30 jours)", "Taux achats non configuré")
                } else if (d.naturesMouvements.values.any {
                    it != com.xburnsx.toutiebudget.data.modeles.NatureMouvementCarte.ACHAT
                }) {
                    Ligne("Intérêts estimés (30 jours)", "Non calculables sans ventilation des soldes")
                } else {
                    Ligne("Intérêts estimés (30 jours, taux achats)",
                        MoneyFormatter.formatAmount(MoteurCarteCredit.interetsMensuelsEstimes(carte)))
                }
                cycle?.let { estimation ->
                    Ligne(
                        "Intérêts du cycle (${estimation.jours} j)",
                        if (!estimation.estimationDisponible) "Calcul indisponible" else
                            if (estimation.graceProbable) "0,00 $ · grâce présumée" else
                                MoneyFormatter.formatAmount(estimation.montant)
                    )
                    estimation.avertissement?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Les projections ne sont pas des intérêts facturés. Elles ne modifient jamais le solde.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun taux(valeur: Double) = "${"%.2f".format(valeur)} %"

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color = Color.White, gras: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(libelle, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            valeur,
            color = couleur,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal
        )
    }
}
