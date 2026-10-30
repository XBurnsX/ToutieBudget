package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.EmetteurCarte
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit

/**
 * Modifier la carte. L'essentiel d'abord (CC3) : nom, émetteur, solde SIGNÉ, limite, taux des achats ;
 * le cycle, le minimum et les autres taux sont optionnels (CC4, CC7, CC10).
 */
@Composable
fun ModifierCarteCreditDialog(
    formulaire: FormulaireCarteCredit,
    enCours: Boolean,
    onChange: (FormulaireCarteCredit.() -> FormulaireCarteCredit) -> Unit,
    onSauvegarder: () -> Unit,
    onDismiss: () -> Unit
) {
    var avance by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!enCours) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Modifier la carte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Champ("Nom", formulaire.nom, formulaire.erreurs["nom"], texte = true) { v -> onChange { copy(nom = v) } }

                Text("Émetteur", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmetteurCarte.entries.forEach { e ->
                        FilterChip(selected = formulaire.emetteur == e, onClick = { onChange { copy(emetteur = e) } }, label = { Text(e.libelle) })
                    }
                }

                Champ(
                    "Solde actuel (dette en négatif, ex. -400,00)",
                    formulaire.soldeActuel,
                    formulaire.erreurs["solde"],
                    aide = "0 = carte à zéro ; positif = trop-payé. Une correction est notée dans l'historique, sans créer de paiement.",
                    montant = true
                ) { v -> onChange { copy(soldeActuel = v) } }
                Champ("Limite de crédit", formulaire.limiteCredit, formulaire.erreurs["limite"], montant = true) { v -> onChange { copy(limiteCredit = v) } }
                Champ("Taux annuel des achats (%)", formulaire.tauxInteret, formulaire.erreurs["taux"]) { v -> onChange { copy(tauxInteret = v) } }

                TextButton(onClick = { avance = !avance }) {
                    Text(if (avance) "Masquer les options" else "Relevé, minimum et autres taux (optionnel)")
                }

                if (avance) {
                    Text("Cycle du relevé", style = MaterialTheme.typography.labelLarge)
                    Champ("Jour de clôture du relevé (1-31)", formulaire.jourCloture, formulaire.erreurs["cloture"], entier = true) { v ->
                        onChange { copy(jourCloture = v) }
                    }
                    Champ(
                        "Jours entre la clôture et l'échéance",
                        formulaire.joursAvantEcheance,
                        formulaire.erreurs["delai"],
                        entier = true,
                        aide = "Selon votre carte et votre relevé (ex. 21 ou 25) : ne pas deviner."
                    ) { v -> onChange { copy(joursAvantEcheance = v) } }

                    Text("Paiement minimum", style = MaterialTheme.typography.labelLarge)
                    Champ(
                        "Pourcentage du solde du relevé (défaut ${formulaire.emetteur.pourcentageMinimum} %)",
                        formulaire.pourcentageMinimum,
                        formulaire.erreurs["pourcentage"]
                    ) { v -> onChange { copy(pourcentageMinimum = v) } }
                    Champ(
                        "Plancher prévu au contrat ($)",
                        formulaire.plancherMinimum,
                        formulaire.erreurs["plancher"],
                        aide = "Seulement s'il figure dans votre contrat.",
                        montant = true,
                        facultatif = true
                    ) { v -> onChange { copy(plancherMinimum = v) } }
                    Champ(
                        "Minimum officiel connu ($)",
                        formulaire.paiementMinimum,
                        formulaire.erreurs["minimum"],
                        aide = "Le minimum d'un relevé saisi dans « Relevés officiels » a priorité.",
                        montant = true,
                        facultatif = true
                    ) { v -> onChange { copy(paiementMinimum = v) } }

                    Text("Autres taux annuels (%)", style = MaterialTheme.typography.labelLarge)
                    Champ("Avances de fonds", formulaire.tauxAvances, formulaire.erreurs["tauxAvances"]) { v -> onChange { copy(tauxAvances = v) } }
                    Champ("Transferts de solde", formulaire.tauxTransferts, formulaire.erreurs["tauxTransferts"]) { v -> onChange { copy(tauxTransferts = v) } }
                    Champ("Taux promotionnel", formulaire.tauxPromo, formulaire.erreurs["tauxPromo"]) { v -> onChange { copy(tauxPromo = v) } }
                    Champ("Début de la promotion (AAAA-MM-JJ)", formulaire.debutPromo, formulaire.erreurs["debutPromo"], texte = true) { v ->
                        onChange { copy(debutPromo = v) }
                    }
                    Champ("Fin de la promotion (AAAA-MM-JJ)", formulaire.finPromo, formulaire.erreurs["finPromo"], texte = true) { v ->
                        onChange { copy(finPromo = v) }
                    }
                    Champ("Taux de retard", formulaire.tauxRetard, formulaire.erreurs["tauxRetard"]) { v -> onChange { copy(tauxRetard = v) } }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, enabled = !enCours, modifier = Modifier.weight(1f)) { Text("Annuler") }
                    Button(onClick = onSauvegarder, enabled = !enCours && formulaire.estValide, modifier = Modifier.weight(1f)) {
                        if (enCours) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Enregistrer")
                    }
                }
            }
        }
    }
}

@Composable
internal fun Champ(
    libelle: String,
    valeur: String,
    erreur: String?,
    texte: Boolean = false,
    entier: Boolean = false,
    aide: String? = null,
    montant: Boolean = false,
    facultatif: Boolean = false,
    onValeur: (String) -> Unit
) {
    // Un montant se saisit TOUJOURS avec le clavier de l'app (ChampUniversel → ClavierNumerique)
    if (montant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel(
                valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(valeur),
                onValeurChange = { centimes ->
                    onValeur(com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(centimes, videSiZero = facultatif))
                },
                libelle = libelle,
                modifier = Modifier.fillMaxWidth()
            )
            (erreur ?: aide)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (erreur != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
        return
    }
    OutlinedTextField(
        value = valeur,
        onValueChange = onValeur,
        label = { Text(libelle) },
        isError = erreur != null,
        supportingText = (erreur ?: aide)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                texte -> KeyboardType.Text
                entier -> KeyboardType.Number
                else -> KeyboardType.Text // permet le signe « - » et la virgule sur tous les claviers
            }
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
