package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.banque.RegleAliasBancaire
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTiers
import kotlinx.coroutines.launch

/** BANQ-02 : gestion manuelle des associations, sans deuxième catalogue de marchands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasBancairesScreen(onBack: () -> Unit) {
    val repository = remember { AppModule.provideAliasBancaireRepository() }
    val tiersRepository = remember { AppModule.provideTiersRepository() }
    val compteRepository = remember { AppModule.provideCompteRepository() }
    val scope = rememberCoroutineScope()

    var regles by remember { mutableStateOf<List<RegleAliasBancaire>>(emptyList()) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var libelle by remember { mutableStateOf("") }
    var nomSaisi by remember { mutableStateOf("") }
    var tiersChoisi by remember { mutableStateOf<Tiers?>(null) }
    var compteSelection by remember { mutableStateOf("") } // aucun choix n'est implicite
    var selectionOuverte by remember { mutableStateOf(false) }
    var regleId by remember { mutableStateOf("") }
    var regleActive by remember { mutableStateOf(true) }
    var suppressionConfirmee by remember { mutableStateOf<RegleAliasBancaire?>(null) }
    var libelleTest by remember { mutableStateOf("") }
    var compteTestId by remember { mutableStateOf("") }
    var compteTestOuvert by remember { mutableStateOf(false) }
    var resultatTest by remember { mutableStateOf<String?>(null) }

    suspend fun charger() {
        isLoading = true
        try {
            tiers = tiersRepository.recupererTousLesTiers().getOrThrow()
            // Aucun compte Dette / Investissement : réservé aux sources bancaires.
            comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                .filter { it.collection == "comptes_cheques" || it.collection == "comptes_credits" }
                .filterNot { it.estArchive }
            regles = repository.toutes()
        } catch (e: Exception) {
            message = e.message ?: "Chargement impossible."
        } finally {
            isLoading = false
        }
    }

    fun effacerFormulaire() {
        regleId = ""
        libelle = ""
        nomSaisi = ""
        tiersChoisi = null
        compteSelection = ""
        regleActive = true
    }

    LaunchedEffect(Unit) { charger() }

    suppressionConfirmee?.let { regle ->
        AlertDialog(
            onDismissRequest = { suppressionConfirmee = null },
            title = { Text("Supprimer cette règle?") },
            text = { Text("Le tiers existant et ses transactions ne seront pas supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    suppressionConfirmee = null
                    scope.launch {
                        try {
                            repository.supprimer(regle.id)
                            message = "Règle supprimée localement (synchro en attente)."
                            charger()
                        } catch (e: Exception) { message = e.message ?: "Suppression impossible." }
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { suppressionConfirmee = null }) { Text("Annuler") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alias bancaires") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Un libellé bancaire est associé à un tiers DÉJÀ existant. " +
                "La catégorie et l'enveloppe restent à confirmer à chaque achat.")
            message?.let { Text(it, color = Color(0xFFFF8993)) }
            if (isLoading) Text("Chargement...")

            OutlinedTextField(
                value = libelle,
                onValueChange = { libelle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Libellé bancaire original") },
                placeholder = { Text("Ex. IRV 0421 QC") },
                singleLine = true
            )
            SelecteurTiers(
                tiersDisponibles = tiers,
                tiersUtiliser = nomSaisi,
                onTiersUtiliserChange = {
                    nomSaisi = it
                    if (tiersChoisi?.nom != it) tiersChoisi = null
                },
                onTiersSelectionne = {
                    tiersChoisi = it
                    nomSaisi = it.nom
                },
                onCreerNouveauTiers = { nom ->
                    scope.launch {
                        try {
                            val nouveau = tiersRepository.creerTiers(Tiers(nom = nom)).getOrThrow()
                            charger()
                            val memeTiers = tiers.firstOrNull { it.id == nouveau.id } ?: nouveau
                            tiersChoisi = memeTiers
                            nomSaisi = memeTiers.nom
                        } catch (e: Exception) {
                            message = e.message ?: "Impossible de créer ce tiers."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { selectionOuverte = true }) {
                    val titre = when (compteSelection) {
                        "" -> "Choisir un compte"
                        "__GLOBAL__" -> "Tous les comptes (explicite)"
                        else -> comptes.firstOrNull { it.id == compteSelection }?.nom ?: "Compte indisponible"
                    }
                    Text(titre)
                }
                DropdownMenu(expanded = selectionOuverte, onDismissRequest = { selectionOuverte = false }) {
                    DropdownMenuItem(
                        text = { Text("Tous les comptes — règle générale") },
                        onClick = { compteSelection = "__GLOBAL__"; selectionOuverte = false }
                    )
                    comptes.forEach { compte ->
                        DropdownMenuItem(
                            text = { Text(compte.nom) },
                            onClick = { compteSelection = compte.id; selectionOuverte = false }
                        )
                    }
                }
            }
            Row {
                Checkbox(checked = regleActive, onCheckedChange = { regleActive = it })
                Text("Règle active", modifier = Modifier.padding(top = 12.dp))
            }
            Button(
                enabled = !isLoading && libelle.isNotBlank() && tiersChoisi != null &&
                    compteSelection.isNotBlank(),
                onClick = {
                    val cible = tiersChoisi ?: return@Button
                    val userId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    if (userId.isNullOrBlank()) {
                        message = "Connexion utilisateur requise."
                        return@Button
                    }
                    scope.launch {
                        try {
                            repository.enregistrer(RegleAliasBancaire(
                                id = regleId, utilisateurId = userId,
                                compteId = if (compteSelection == "__GLOBAL__") null else compteSelection,
                                libelleBrut = libelle, tiersId = cible.id, active = regleActive
                            ))
                            effacerFormulaire()
                            message = "Règle enregistrée localement (synchro en attente)."
                            charger()
                        } catch (e: Exception) {
                            message = e.message ?: "Impossible d'enregistrer la règle."
                        }
                    }
                }
            ) { Text(if (regleId.isBlank()) "Mémoriser l'association" else "Enregistrer les modifications") }
            if (regleId.isNotBlank()) {
                TextButton(onClick = { effacerFormulaire() }) { Text("Annuler la modification") }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tester la reconnaissance (sans créer de transaction)")
            OutlinedTextField(
                value = libelleTest,
                onValueChange = { libelleTest = it; resultatTest = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Libellé tel que fourni par la banque") },
                singleLine = true
            )
            OutlinedButton(onClick = { compteTestOuvert = true }) {
                Text(comptes.firstOrNull { it.id == compteTestId }?.nom ?: "Sélectionner le compte à tester")
            }
            DropdownMenu(
                expanded = compteTestOuvert,
                onDismissRequest = { compteTestOuvert = false }
            ) {
                comptes.forEach { compte ->
                    DropdownMenuItem(
                        text = { Text(compte.nom) },
                        onClick = {
                            compteTestId = compte.id
                            compteTestOuvert = false
                            resultatTest = null
                        }
                    )
                }
            }
            Button(
                enabled = libelleTest.isNotBlank() && compteTestId.isNotBlank() && !isLoading,
                onClick = {
                    scope.launch {
                        try {
                            val trouve = repository.reconnaitre(compteTestId, libelleTest)
                            resultatTest = if (trouve == null)
                                "Aucune correspondance certaine : sélection manuelle requise."
                            else "Tiers reconnu : ${trouve.nom} (ID : ${trouve.id})"
                        } catch (e: Exception) {
                            resultatTest = e.message ?: "Reconnaissance impossible."
                        }
                    }
                }
            ) { Text("Vérifier le tiers proposé") }
            resultatTest?.let { Text(it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Règles enregistrées")
                TextButton(onClick = { scope.launch { charger() } }) { Text("Actualiser") }
            }
            regles.forEach { regle ->
                val cible = tiers.firstOrNull { it.id == regle.tiersId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(regle.libelleBrut)
                        Text("Tiers : " + (cible?.nom ?: "Tiers supprimé ou indisponible"))
                        Text("Compte : " + (regle.compteId?.let { id ->
                            comptes.firstOrNull { it.id == id }?.nom ?: "Compte archivé"
                        } ?: "Tous (règle générale)"))
                        Text(if (regle.active) "Active" else "Désactivée")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                regleId = regle.id
                                libelle = regle.libelleBrut
                                tiersChoisi = cible
                                nomSaisi = cible?.nom ?: ""
                                compteSelection = regle.compteId ?: "__GLOBAL__"
                                regleActive = regle.active
                            }) { Text("Modifier") }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        repository.enregistrer(regle.copy(active = !regle.active))
                                        charger()
                                    } catch (e: Exception) {
                                        message = e.message ?: "Modification impossible."
                                    }
                                }
                            }) { Text(if (regle.active) "Désactiver" else "Activer") }
                            TextButton(onClick = { suppressionConfirmee = regle }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
}
