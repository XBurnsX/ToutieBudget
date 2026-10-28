package com.xburnsx.toutiebudget.ui.archives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteItem
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel

/**
 * ARC7/ARC8/ARC10 : la liste des enveloppes archivées vient directement
 * du repository par le ViewModel de catégories; elle ne dépend PAS du mois
 * affiché dans le budget et n'active pas un filtre global sur celui-ci.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivesScreen(
    comptesViewModel: ComptesViewModel,
    categoriesViewModel: CategoriesEnveloppesViewModel,
    onBack: () -> Unit,
    onVoirHistoriqueCompte: (String, String, String) -> Unit = { _, _, _ -> },
    onVoirHistoriqueEnveloppe: (String, String) -> Unit = { _, _ -> }
) {
    val comptesState by comptesViewModel.uiState.collectAsState()
    val categoriesState by categoriesViewModel.uiState.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var recherche by remember { mutableStateOf("") }
    var filtreCompte by remember { mutableStateOf("") }
    var compteARestaurer by remember { mutableStateOf<Compte?>(null) }
    var compteAConsulter by remember { mutableStateOf<Compte?>(null) }
    var enveloppeAConsulter by remember { mutableStateOf<Enveloppe?>(null) }
    var enveloppeARestaurer by remember { mutableStateOf<Enveloppe?>(null) }
    var destinationRestauration by remember { mutableStateOf<String?>(null) }
    var restaurationDemandee by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        comptesViewModel.chargerComptesArchives()
        categoriesViewModel.chargerDonnees()
    }
    LaunchedEffect(categoriesState.enveloppesArchivees, restaurationDemandee) {
        if (restaurationDemandee && enveloppeARestaurer != null &&
            categoriesState.enveloppesArchivees.none { it.id == enveloppeARestaurer?.id }
        ) {
            enveloppeARestaurer = null
            destinationRestauration = null
            restaurationDemandee = false
        }
    }
    LaunchedEffect(categoriesState.erreur) {
        if (categoriesState.erreur != null) restaurationDemandee = false
    }

    val comptesFiltres = RechercheArchives.comptes(
        comptesState.comptesArchives, recherche, filtreCompte
    )
    val enveloppesFiltrees = RechercheArchives.enveloppes(
        categoriesState.enveloppesArchivees, recherche,
        categoriesState.categoriesParId.mapValues { it.value.nom }
    )

    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = { Text("Archives") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
            TabRow(selectedTabIndex = tabIndex) {
                // ARC20 : compteur par onglet
                listOf(
                    "Comptes (${comptesState.comptesArchives.size})",
                    "Enveloppes (${categoriesState.enveloppesArchivees.size})"
                )
                    .forEachIndexed { index, nom ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = { Text(nom) }
                        )
                    }
            }
        }
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                label = { Text("Rechercher dans les archives") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            if (tabIndex == 0) {
                // ARA2 : filtres exclusifs à Archives, jamais au budget actif.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val choix = listOf(
                        "" to "Tous", "comptes_cheques" to "Chèques",
                        "comptes_credits" to "Cartes", "comptes_dettes" to "Dettes",
                        "comptes_investissement" to "Invest."
                    )
                    choix.forEach { (collection, libelle) ->
                        FilterChip(
                            selected = filtreCompte == collection,
                            onClick = { filtreCompte = collection },
                            label = { Text(libelle) }
                        )
                    }
                }
                // ARC14 : erreur réelle avec Réessayer, jamais un faux « aucune archive »
                comptesState.erreurArchives?.let { erreur ->
                    Text(erreur, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(onClick = { comptesViewModel.chargerComptesArchives() }) { Text("Réessayer") }
                }
                if (comptesState.archivesEnChargement && comptesState.comptesArchives.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "${comptesFiltres.size} compte(s) archivé(s)",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(comptesFiltres, key = { it.collection + ":" + it.id }) { compte ->
                        CompteItem(
                            compte = compte,
                            onClick = {
                                onVoirHistoriqueCompte(compte.id, compte.collection, compte.nom)
                            },
                            onLongClick = { compteARestaurer = compte }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                EtatArchive.libelleType(compte) + " · " + EtatArchive.statut(compte),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { compteAConsulter = compte }) { Text("Détails") }
                            TextButton(onClick = { compteARestaurer = compte }) { Text("Réactiver") }
                        }
                    }
                }
            } else {
                if (categoriesState.erreur != null) {
                    Text(
                        "Chargement des archives : ${categoriesState.erreur}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = { categoriesViewModel.chargerDonnees() }) {
                        Text("Réessayer")
                    }
                }
                Text(
                    "${enveloppesFiltrees.size} enveloppe(s) archivée(s)",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (categoriesState.isLoading && categoriesState.enveloppesArchivees.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(enveloppesFiltrees, key = { it.id }) { env ->
                        val nomCategorie = categoriesState.categoriesParId[env.categorieId]?.nom
                            ?: "Catégorie d'origine introuvable"
                        ListItem(
                            modifier = Modifier.clickable {
                                onVoirHistoriqueEnveloppe(env.id, env.nom)
                            },
                            headlineContent = { Text(env.nom) },
                            supportingContent = {
                                Text("$nomCategorie · Objectif : ${env.objectifMontant}" +
                                    (env.archiveLe?.let { " · archivée le $it" } ?: ""))
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = { enveloppeAConsulter = env }) {
                                        Text("Détails")
                                    }
                                    TextButton(onClick = {
                                        enveloppeARestaurer = env
                                        destinationRestauration =
                                            env.categorieId.takeIf {
                                                categoriesState.categoriesParId.containsKey(it)
                                            }
                                        restaurationDemandee = false
                                    }) { Text("Restaurer") }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ARA3 : les paramètres sont consultables sans restauration.
    // Aucun solde, objectif ou transaction n'est modifié par ces dialogues.
    // ARC9 : résultat de la réactivation confirmé à l'écran
    comptesState.messageArchives?.let { message ->
        AlertDialog(
            onDismissRequest = { comptesViewModel.effacerMessageArchives() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { comptesViewModel.effacerMessageArchives() }) { Text("OK") }
            }
        )
    }

    compteAConsulter?.let { compte ->
        AlertDialog(
            onDismissRequest = { compteAConsulter = null },
            title = { Text("Compte archivé — ${compte.nom}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Identifiant : ${compte.id}")
                    Text("Collection : ${compte.collection}")
                    Text("Type : " + EtatArchive.libelleType(compte))
                    Text("Statut : " + EtatArchive.statut(compte))
                    // ARA4
                    Text("Archivé le : " + (compte.archiveLe ?: "date inconnue (archivé avant cette version)"))
                    compte.raisonArchivage?.let { Text("Raison : $it") }
                    Text("Solde enregistré : " +
                        com.xburnsx.toutiebudget.ui.historique.AffichageMontantHistorique
                            .formater(compte.solde))
                    Text("Le solde n'est pas recalculé ici : consulte l'historique pour les mouvements.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    compteAConsulter = null
                    onVoirHistoriqueCompte(compte.id, compte.collection, compte.nom)
                }) { Text("Voir l'historique") }
            },
            dismissButton = {
                TextButton(onClick = { compteAConsulter = null }) { Text("Fermer") }
            }
        )
    }

    enveloppeAConsulter?.let { env ->
        AlertDialog(
            onDismissRequest = { enveloppeAConsulter = null },
            title = { Text("Enveloppe archivée — ${env.nom}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Identifiant : ${env.id}")
                    Text("Catégorie : " +
                        (categoriesState.categoriesParId[env.categorieId]?.nom
                            ?: "Supprimée (ID : ${env.categorieId})"))
                    Text("Statut : archivée")
                    // ARA4
                    Text("Archivée le : " + (env.archiveLe ?: "date inconnue (archivée avant cette version)"))
                    env.raisonArchivage?.let { Text("Raison : $it") }
                    Text("Type d'objectif : ${env.typeObjectif}")
                    Text("Montant d'objectif : " +
                        com.xburnsx.toutiebudget.ui.historique.AffichageMontantHistorique
                            .formater(env.objectifMontant))
                    Text("Les allocations et les montants historiques restent accessibles dans l'historique.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    enveloppeAConsulter = null
                    onVoirHistoriqueEnveloppe(env.id, env.nom)
                }) { Text("Voir l'historique") }
            },
            dismissButton = {
                TextButton(onClick = { enveloppeAConsulter = null }) { Text("Fermer") }
            }
        )
    }

    compteARestaurer?.let { compte ->
        AlertDialog(
            onDismissRequest = { compteARestaurer = null },
            title = { Text("Réactiver ${compte.nom} ?") },
            text = {
                // ARA5 : aperçu des conséquences avant de restaurer
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Le même compte (même ID), ses paramètres et son historique sont conservés.")
                    Text("Il réapparaîtra dans la page Comptes et redeviendra choisissable pour " +
                        "les nouvelles transactions, virements et paiements.")
                    Text("Solde qui revient dans les totaux : " +
                        com.xburnsx.toutiebudget.ui.historique.AffichageMontantHistorique.formater(compte.solde))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    comptesViewModel.reactiverCompte(compte)
                    compteARestaurer = null
                }) { Text("Réactiver") }
            },
            dismissButton = {
                TextButton(onClick = { compteARestaurer = null }) { Text("Annuler") }
            }
        )
    }

    enveloppeARestaurer?.let { env ->
        AlertDialog(
            onDismissRequest = {
                enveloppeARestaurer = null
                restaurationDemandee = false
            },
            title = { Text("Restaurer ${env.nom} ?") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Choisis la catégorie. L'ID, l'objectif, les allocations et l'historique restent inchangés.")
                    Text("Elle réapparaîtra dans le budget et redeviendra choisissable pour les " +
                        "nouvelles transactions et allocations.")
                    if (categoriesState.categoriesParId.isEmpty()) {
                        Text("Aucune catégorie : crée-en une avant de restaurer.")
                    }
                    categoriesState.categoriesParId.values.sortedBy { it.ordre }
                        .forEach { categorie ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = destinationRestauration == categorie.id,
                                    onClick = { destinationRestauration = categorie.id }
                                )
                                Text(categorie.nom)
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = destinationRestauration != null && !restaurationDemandee,
                    onClick = {
                        destinationRestauration?.let { categorieId ->
                            categoriesViewModel.restaurerEnveloppe(env.id, categorieId)
                            restaurationDemandee = true
                        }
                    }
                ) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    enveloppeARestaurer = null
                    restaurationDemandee = false
                }) { Text("Annuler") }
            }
        )
    }
}
