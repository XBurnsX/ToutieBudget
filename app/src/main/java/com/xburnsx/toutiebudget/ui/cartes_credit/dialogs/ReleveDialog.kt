package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit

/**
 * Saisie d'un relevé OFFICIEL (CC6). Les montants sont ceux imprimés sur le relevé
 * (montants dus en positif) ; ils ne sont jamais recalculés après la clôture.
 */
@Composable
fun ReleveDialog(
    releve: ReleveCarte,
    estNouveau: Boolean,
    onSauvegarder: (ReleveCarte) -> Unit,
    onSupprimer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    fun t(v: Double?) = v?.takeIf { it != 0.0 }?.let { "%.2f".format(java.util.Locale.US, it) } ?: ""
    var emission by remember { mutableStateOf(releve.dateEmission) }
    var echeance by remember { mutableStateOf(releve.dateEcheance ?: "") }
    var solde by remember { mutableStateOf(t(releve.soldeOfficiel)) }
    var minimum by remember { mutableStateOf(t(releve.minimumOfficiel)) }
    var interets by remember { mutableStateOf(t(releve.interetsFactures)) }
    var frais by remember { mutableStateOf(t(releve.fraisFactures)) }
    var paiements by remember { mutableStateOf(t(releve.paiementsRecus)) }
    var confirmerSuppression by remember { mutableStateOf(false) }

    fun n(s: String) = s.trim().replace(" ", "").replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val erreurEmission = if (MoteurCarteCredit.lireDate(emission.trim()) == null) "Date AAAA-MM-JJ" else null
    val erreurEcheance = if (echeance.isNotBlank() && MoteurCarteCredit.lireDate(echeance.trim()) == null) "Date AAAA-MM-JJ" else null
    val erreurSolde = if (n(solde) == null) "Solde du relevé requis" else null
    val erreursMontants = listOf(minimum, interets, frais, paiements).any { it.isNotBlank() && (n(it) == null || n(it)!! < 0) }
    val valide = erreurEmission == null && erreurEcheance == null && erreurSolde == null && !erreursMontants

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (estNouveau) "Nouveau relevé" else "Relevé du ${releve.dateEmission}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Champ("Date d'émission (AAAA-MM-JJ)", emission, erreurEmission, texte = true) { emission = it }
                Champ("Date d'échéance (AAAA-MM-JJ)", echeance, erreurEcheance, texte = true) { echeance = it }
                Champ("Solde du relevé ($)", solde, erreurSolde) { solde = it }
                Champ("Paiement minimum ($)", minimum, null) { minimum = it }
                Champ("Intérêts facturés ($)", interets, null, aide = "Déjà inclus dans le solde du relevé.") { interets = it }
                Champ("Frais facturés ($)", frais, null) { frais = it }
                Champ("Paiements et crédits reçus ($)", paiements, null) { paiements = it }
                if (!estNouveau) {
                    TextButton(onClick = { confirmerSuppression = true }) {
                        Text("Supprimer ce relevé", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valide, onClick = {
                onSauvegarder(
                    releve.copy(
                        dateEmission = emission.trim(),
                        dateEcheance = echeance.trim().ifBlank { null },
                        soldeOfficiel = n(solde) ?: 0.0,
                        minimumOfficiel = n(minimum),
                        interetsFactures = n(interets) ?: 0.0,
                        fraisFactures = n(frais) ?: 0.0,
                        paiementsRecus = n(paiements) ?: 0.0
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Supprimer ce relevé ?") },
            text = { Text("Le solde de la carte ne change pas.") },
            confirmButton = { TextButton(onClick = { onSupprimer(releve.id) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") } }
        )
    }
}
