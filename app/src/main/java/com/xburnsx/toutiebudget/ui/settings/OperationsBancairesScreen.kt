package com.xburnsx.toutiebudget.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.banque.ReconnaissanceTiersBancaire
import com.xburnsx.toutiebudget.data.banque.RegleAliasBancaire
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTiers
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BANQ-03 : import et révision uniquement. Rien ici n'écrit dans Transactions,
 * dans les comptes, ni dans les enveloppes. La confirmation comptable vient ensuite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsBancairesScreen(onBack: () -> Unit) {
    val repository = remember { AppModule.provideBoiteOperationsBancairesRepository() }
    val aliasRepository = remember { AppModule.provideAliasBancaireRepository() }
    val compteRepository = remember { AppModule.provideCompteRepository() }
    val tiersRepository = remember { AppModule.provideTiersRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var operations by remember { mutableStateOf<List<OperationBancaireEnAttente>>(emptyList()) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var tiersReconnus by remember { mutableStateOf<Map<String, Tiers>>(emptyMap()) }
    var editionId by remember { mutableStateOf<String?>(null) }
    var nomSaisi by remember { mutableStateOf("") }
    var tiersChoisi by remember { mutableStateOf<Tiers?>(null) }
    var memoriserAssociation by remember { mutableStateOf(false) }
    var compteId by remember { mutableStateOf("") }
    var menuComptes by remember { mutableStateOf(false) }
    var afficherIgnorees by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var progression by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                .filter { !it.estArchive && it.collection in setOf("comptes_cheques", "comptes_credits") }
            tiers = tiersRepository.recupererTousLesTiers().getOrThrow()
            repository.toutesLesOperations().collectLatest { operations = it }
        } catch (e: Exception) {
            erreur = e.message ?: "Impossible de charger les opérations bancaires."
        }
    }

    LaunchedEffect(operations) {
        try {
            val userId = PocketBaseClient.obtenirUtilisateurConnecte()?.id.orEmpty()
            val regles = aliasRepository.toutes()
            tiers = tiersRepository.recupererTousLesTiers().getOrThrow()
            tiersReconnus = operations.mapNotNull { operation ->
                val reconnu = ReconnaissanceTiersBancaire.reconnaitre(
                    userId, operation.compteId, operation.libelleBrut, regles, tiers
                )
                if (reconnu == null) null else operation.id to reconnu
            }.toMap()
        } catch (_: Exception) {
            tiersReconnus = emptyMap()
        }
    }

    val ouvrirCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || compteId.isBlank() || enCours) return@rememberLauncherForActivityResult
        val compte = comptes.firstOrNull { it.id == compteId }
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            enCours = true
            erreur = null
            progression = null
            try {
                val csv = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { lecteur ->
                        val resultat = StringBuilder()
                        val bloc = CharArray(4096)
                        while (true) {
                            val lu = lecteur.read(bloc)
                            if (lu < 0) break
                            resultat.append(bloc, 0, lu)
                            require(resultat.length <= 5_000_000) { "CSV trop grand (5 Mo maximum)." }
                        }
                        resultat.toString()
                    } ?: error("Le fichier choisi ne peut pas être lu.")
                }
                val lignes = withContext(Dispatchers.IO) {
                    repository.importerCsv(compte.id, compte.collection, csv)
                }
                progression = lignes.size.toString() +
                    " opération(s) traitée(s), en attente de vérification. Aucun débit effectué."
            } catch (e: Exception) {
                erreur = e.message ?: "Import CSV impossible."
            } finally {
                enCours = false
            }
        }
    }

    val affichees = if (afficherIgnorees) operations else operations.filter { it.decision == "A_CONFIRMER" }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Transactions à confirmer") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }
        ) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Les opérations importées restent séparées du budget jusqu'à leur confirmation. " +
                        "Ni le compte ni les enveloppes ne sont débités par l'import.")
                    OutlinedButton(onClick = { menuComptes = true }) {
                        Text(comptes.firstOrNull { it.id == compteId }?.nom ?: "Compte du fichier CSV")
                    }
                    DropdownMenu(expanded = menuComptes, onDismissRequest = { menuComptes = false }) {
                        comptes.forEach { compte ->
                            DropdownMenuItem(
                                text = { Text(compte.nom) },
                                onClick = { compteId = compte.id; menuComptes = false }
                            )
                        }
                    }
                    Button(
                        enabled = compteId.isNotBlank() && !enCours,
                        onClick = { ouvrirCsv.launch(arrayOf("text/*", "application/vnd.ms-excel")) }
                    ) { Text(if (enCours) "Import en cours..." else "Importer un CSV bancaire") }
                    Text("CSV : date, montant/amount, description/libellé, ID optionnel. " +
                        "Devise CAD uniquement. Aucun rapprochement n'est automatique.")
                    erreur?.let { Text("Erreur : " + it) }
                    progression?.let { Text(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { afficherIgnorees = !afficherIgnorees }) {
                            Text(if (afficherIgnorees) "Masquer les ignorées" else "Voir les ignorées")
                        }
                        Text("À confirmer : " + operations.count { it.decision == "A_CONFIRMER" })
                    }
                }
            }
            items(affichees, key = { it.id }) { operation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(operation.libelleBrut)
                        Text("Date : " + operation.dateOperation + "  •  " +
                            BigDecimal.valueOf(operation.montantCentimes, 2).toPlainString() + " $")
                        Text("Compte : " + (comptes.firstOrNull { it.id == operation.compteId }?.nom
                            ?: "Compte archivé ou indisponible"))
                        Text("Statut bancaire : " + operation.statutBancaire)
                        Text("Tiers choisi : " + (tiers.firstOrNull { it.id == operation.tiersId }?.nom
                            ?: "aucun"))
                        Text("Tiers suggéré : " + (tiersReconnus[operation.id]?.nom
                            ?: "aucun (choix manuel requis)"))
                        if (operation.verificationRequise) {
                            Text("Montant modifié par la banque : vérifier avant de classer.")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (operation.decision == "A_CONFIRMER") {
                                TextButton(
                                    enabled = !enCours,
                                    onClick = {
                                        scope.launch {
                                            try { repository.ignorer(operation.id) }
                                            catch (e: Exception) {
                                                erreur = e.message ?: "Impossible d'ignorer l'opération."
                                            }
                                        }
                                    }
                                ) { Text("Ignorer") }
                                TextButton(onClick = {
                                    if (editionId == operation.id) {
                                        editionId = null
                                    } else {
                                        editionId = operation.id
                                        tiersChoisi = tiers.firstOrNull { it.id == operation.tiersId }
                                            ?: tiersReconnus[operation.id]
                                        nomSaisi = tiersChoisi?.nom.orEmpty()
                                        memoriserAssociation = false
                                    }
                                }) { Text(if (editionId == operation.id) "Fermer" else "Choisir le tiers") }
                                Text("À confirmer")
                            } else {
                                TextButton(
                                    enabled = !enCours,
                                    onClick = {
                                        scope.launch {
                                            try { repository.restaurer(operation.id) }
                                            catch (e: Exception) {
                                                erreur = e.message ?: "Impossible de restaurer l'opération."
                                            }
                                        }
                                    }
                                ) { Text("Remettre en attente") }
                            }
                        }
                        if (operation.decision == "A_CONFIRMER" && editionId == operation.id) {
                            SelecteurTiers(
                                tiersDisponibles = tiers,
                                tiersUtiliser = nomSaisi,
                                onTiersUtiliserChange = { nom ->
                                    nomSaisi = nom
                                    if (tiersChoisi?.nom != nom) tiersChoisi = null
                                },
                                onTiersSelectionne = { choix ->
                                    tiersChoisi = choix
                                    nomSaisi = choix.nom
                                },
                                onCreerNouveauTiers = { nom ->
                                    scope.launch {
                                        try {
                                            val nouveau = tiersRepository.creerTiers(Tiers(nom = nom)).getOrThrow()
                                            tiers = tiersRepository.recupererTousLesTiers().getOrThrow()
                                            tiersChoisi = tiers.firstOrNull { it.id == nouveau.id } ?: nouveau
                                            nomSaisi = tiersChoisi?.nom.orEmpty()
                                        } catch (e: Exception) {
                                            erreur = e.message ?: "Création du tiers impossible."
                                        }
                                    }
                                },
                                isLoading = enCours,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row {
                                Checkbox(
                                    checked = memoriserAssociation,
                                    onCheckedChange = { memoriserAssociation = it }
                                )
                                Text("Mémoriser pour les prochaines fois",
                                    modifier = Modifier.padding(top = 12.dp))
                            }
                            Button(
                                enabled = !enCours && tiersChoisi != null,
                                onClick = {
                                    val choix = tiersChoisi ?: return@Button
                                    scope.launch {
                                        enCours = true
                                        try {
                                            repository.selectionnerTiers(operation.id, choix.id)
                                            if (memoriserAssociation) {
                                                val user = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                                                    ?: error("Utilisateur non connecté.")
                                                val existing = aliasRepository.toutes().firstOrNull {
                                                    it.compteId == operation.compteId &&
                                                        ReconnaissanceTiersBancaire.normaliser(it.libelleBrut) ==
                                                        ReconnaissanceTiersBancaire.normaliser(operation.libelleBrut)
                                                }
                                                aliasRepository.enregistrer(RegleAliasBancaire(
                                                    id = existing?.id.orEmpty(),
                                                    utilisateurId = user,
                                                    compteId = operation.compteId,
                                                    libelleBrut = operation.libelleBrut,
                                                    tiersId = choix.id
                                                ))
                                            }
                                            editionId = null
                                            progression = "Tiers attribué au brouillon; aucun débit effectué."
                                        } catch (e: Exception) {
                                            erreur = e.message ?: "Association impossible."
                                        } finally {
                                            enCours = false
                                        }
                                    }
                                }
                            ) { Text("Appliquer au brouillon") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 18.dp)) }
        }
    }
}
