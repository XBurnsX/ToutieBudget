// chemin/simule: /ui/comptes/dialogs/AjoutCompteDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampMontantUniversel, CouleurSelecteur

package com.xburnsx.toutiebudget.ui.comptes.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.CompteFormState
import com.xburnsx.toutiebudget.ui.comptes.COULEURS_COMPTES
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.MoteurDette
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?, String?) -> Unit, // AJOUT DU 5ème PARAMÈTRE
    onSave: () -> Unit,
    onOpenKeyboard: (Long, (Long) -> Unit) -> Unit,
    onCarteChange: (String?, String?, com.xburnsx.toutiebudget.data.modeles.EmetteurCarte?) -> Unit = { _, _, _ -> },
    onDetteChange: (
        String?,
        String?,
        String?,
        String?,
        com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette?
    ) -> Unit = { _, _, _, _, _ -> },
    onTypePlacementChange: (com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement) -> Unit = {}
) {
    val typesDeCompte = listOf("Compte chèque", "Carte de crédit", "Dette", "Investissement")
    val couleursDisponibles = COULEURS_COMPTES
    var expanded by remember { mutableStateOf(false) }
    var choisirDateDette by remember { mutableStateOf(false) }

    // État du clavier
    val montantEnCentimes = remember(formState.solde) {
        if (formState.solde.isBlank()) {
            0L
        } else {
            try {
                (formState.solde.toDouble() * 100).toLong()
            } catch (_: Exception) {
                0L
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nouveau Compte") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.ime)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
            ) {
                // Champ nom du compte
                OutlinedTextField(
                    value = formState.nom,
                    onValueChange = { onValueChange(it, null, null, null, null) },
                    label = { Text("Nom du compte") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                    )
                )
                
                // Sélecteur de type de compte
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = formState.type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type de compte") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        typesDeCompte.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    onValueChange(null, type, null, null, null)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                ChampUniversel(
                    valeur = montantEnCentimes,
                    onValeurChange = { nouveauMontant ->
                        val nouveauSolde = (nouveauMontant / 100.0).toString()
                        onValueChange(null, null, nouveauSolde, null, null)
                    },
                    libelle = when (formState.type) {
                        "Carte de crédit" -> "Limite de crédit"
                        "Dette" -> "Montant total original du prêt"
                        else -> "Solde initial"
                    },
                    utiliserClavier = false, // Désactiver le clavier intégré
                    isMoney = true,
                    icone = when (formState.type) {
                        "Carte de crédit" -> Icons.Default.CreditCard
                        "Compte chèque" -> Icons.Default.AccountBalance
                        "Dette" -> Icons.Default.MoneyOff
                        "Investissement" -> Icons.AutoMirrored.Filled.TrendingUp
                        else -> Icons.Default.AttachMoney
                    },
                    estObligatoire = false,
                    onClicPersonnalise = {
                        // Utiliser le système de clavier global
                        onOpenKeyboard(montantEnCentimes) { nouveauMontant ->
                            val nouveauSolde = (nouveauMontant / 100.0).toString()
                            onValueChange(null, null, nouveauSolde, null, null)
                        }
                    },
                    modifier = Modifier
                )

                if (formState.type == "Dette") {
                    OutlinedButton(
                        onClick = { choisirDateDette = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        Text(
                            formState.detteDateDebut.takeIf { it.isNotBlank() }
                                ?.let { "Date de début : $it" }
                                ?: "Choisir la date de début"
                        )
                    }
                    Text("Fréquence des paiements", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette.entries.forEach { frequence ->
                            FilterChip(
                                selected = formState.detteFrequence == frequence,
                                onClick = { onDetteChange(null, null, null, null, frequence) },
                                label = { Text(frequence.libelle) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = formState.detteDureeMois,
                        onValueChange = { onDetteChange(null, it, null, null, null) },
                        label = { Text("Durée du prêt (mois)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantClavier(
                        valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(formState.detteVersementMensuel),
                        onValeurChange = { onDetteChange(null, null, com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it, videSiZero = true), null, null) },
                        libelle = "Montant de chaque paiement ($)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = formState.detteTauxAnnuel,
                        onValueChange = { onDetteChange(null, null, null, it, null) },
                        label = { Text("Taux annuel (%)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val situation = remember(
                        formState.solde,
                        formState.detteDateDebut,
                        formState.detteDureeMois,
                        formState.detteVersementMensuel,
                        formState.detteTauxAnnuel,
                        formState.detteFrequence
                    ) {
                        val debut = MoteurCarteCredit.lireDate(formState.detteDateDebut)
                        val duree = formState.detteDureeMois.toIntOrNull()
                        val versement = formState.detteVersementMensuel.replace(',', '.').toDoubleOrNull()
                        val taux = formState.detteTauxAnnuel.replace(',', '.').toDoubleOrNull()
                        val montantTotal = formState.solde.replace(',', '.').toDoubleOrNull()
                        if (debut != null && duree != null && versement != null && taux != null && montantTotal != null) {
                            MoteurDette.situationContrat(
                                debut, duree, versement, taux,
                                principalInitial = montantTotal,
                                frequence = formState.detteFrequence
                            )
                        } else null
                    }
                    situation?.let { s ->
                        Text("Montant total original : ${MoneyFormatter.formatAmount(s.principalInitial)}", style = MaterialTheme.typography.bodySmall)
                        Text("${s.paiementsEcoules} paiement(s) déjà écoulé(s) sur ${s.nombrePaiements}", style = MaterialTheme.typography.bodyMedium)
                        Text("Solde actuel calculé : ${MoneyFormatter.formatAmount(s.soldeActuel)}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Capital payé : ${MoneyFormatter.formatAmount(s.capitalPaye)} · Intérêts payés : ${MoneyFormatter.formatAmount(s.interetsPayes)}", style = MaterialTheme.typography.bodySmall)
                        Text("Fin calculée : ${MoteurCarteCredit.ecrireDate(s.dateFin)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                // CC3 : configuration minimale d'une carte (émetteur, solde SIGNÉ, taux des achats)
                if (formState.type == "Carte de crédit") {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.xburnsx.toutiebudget.data.modeles.EmetteurCarte.entries.forEach { e ->
                            FilterChip(
                                selected = formState.emetteurCarte == e,
                                onClick = { onCarteChange(null, null, e) },
                                label = { Text(e.libelle) }
                            )
                        }
                    }
                    com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantClavier(
                        valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(formState.soldeCarte),
                        onValeurChange = { onCarteChange(com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it), null, null) },
                        libelle = "Solde actuel (dette en négatif, ex. -400,00)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = formState.tauxCarte,
                        onValueChange = { onCarteChange(null, it, null) },
                        label = { Text("Taux annuel des achats (%)") },
                        singleLine = true
                    )
                }

                // IA7 : type de compte de placement (CELI, REER…)
                if (formState.type == "Investissement") {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement.entries.forEach { t ->
                            FilterChip(
                                selected = formState.typePlacement == t,
                                onClick = { onTypePlacementChange(t) },
                                label = { Text(t.libelle) }
                            )
                        }
                    }
                }

                // Sélecteur de couleur (sauf pour les dettes qui sont toujours rouges)
                if (formState.type != "Dette") {
                    CouleurSelecteur(
                        couleurs = couleursDisponibles,
                        couleurSelectionnee = formState.couleur,
                        onCouleurSelected = { onValueChange(null, null, null, null, it) } // CORRECTION : 5ème PARAMÈTRE
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = formState.nom.isNotBlank() && formState.type.isNotBlank() &&
                    (formState.type != "Dette" || (
                        formState.solde.isNotBlank() && formState.detteDateDebut.isNotBlank() && formState.detteDureeMois.isNotBlank() &&
                            formState.detteVersementMensuel.isNotBlank() && formState.detteTauxAnnuel.isNotBlank()
                    ))
            ) {
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Annuler")
            }
        }
    )

    if (choisirDateDette) {
        val initiale = MoteurCarteCredit.lireDate(formState.detteDateDebut) ?: java.util.Date()
        val locale = Calendar.getInstance().apply { time = initiale }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(locale.get(Calendar.YEAR), locale.get(Calendar.MONTH), locale.get(Calendar.DAY_OF_MONTH))
        }
        val etatDate = rememberDatePickerState(initialSelectedDateMillis = utc.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { choisirDateDette = false },
            confirmButton = {
                TextButton(onClick = {
                    etatDate.selectedDateMillis?.let { millis ->
                        val jourUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        val jourLocal = Calendar.getInstance().apply {
                            clear()
                            set(jourUtc.get(Calendar.YEAR), jourUtc.get(Calendar.MONTH), jourUtc.get(Calendar.DAY_OF_MONTH))
                        }
                        onDetteChange(MoteurCarteCredit.ecrireDate(jourLocal.time), null, null, null, null)
                    }
                    choisirDateDette = false
                }) { Text("Choisir") }
            },
            dismissButton = { TextButton(onClick = { choisirDateDette = false }) { Text("Annuler") } }
        ) { DatePicker(state = etatDate) }
    }
}
