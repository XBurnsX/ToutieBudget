package com.xburnsx.toutiebudget.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.ui.settings.HistoriqueGeneralViewModel.Periode
import com.xburnsx.toutiebudget.ui.settings.HistoriqueGeneralViewModel.Vue
import com.xburnsx.toutiebudget.ui.settings.JournalHistorique.Nature
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Historique général (section 22) : « Transactions » (opérations financières) et
 * « Mouvements du budget » (allocations, Prêt à placer, corrections) dans deux vues séparées (HG1/HGA1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: HistoriqueGeneralViewModel = viewModel { HistoriqueGeneralViewModel(context.applicationContext) }
    val etat by vm.etat.collectAsState()
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<HistoriqueGeneralViewModel.Detail?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportEnCours by remember { mutableStateOf(false) }
    var joursFermes by remember { mutableStateOf(setOf<String>()) }

    val lanceurCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportEnCours = true
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = vm.csvFiltre()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(("﻿" + csv).toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("Fichier impossible à écrire")
                    "Export terminé : ${csv.lines().count { it.isNotBlank() } - 1} ligne(s). Aucune donnée n'a été modifiée."
                }.getOrElse { "Export impossible : ${it.message}" }
            }
            exportEnCours = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique général") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(enabled = !exportEnCours, onClick = {
                        val nom = if (etat.vue == Vue.TRANSACTIONS) "transactions" else "mouvements-budget"
                        lanceurCsv.launch("toutiebudget-$nom-${java.time.LocalDate.now()}.csv")
                    }) {
                        if (exportEnCours) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.FileDownload, contentDescription = "Exporter en CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = etat.vue.ordinal) {
                Vue.entries.forEach { v ->
                    Tab(selected = etat.vue == v, onClick = { vm.choisirVue(v) }, text = { Text(v.libelle) })
                }
            }
            Text(
                if (etat.vue == Vue.TRANSACTIONS) "Dépenses, revenus, virements et paiements réellement faits."
                else "Redistribution interne : allocations, Prêt à placer et corrections. Ce ne sont ni des revenus ni des dépenses.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // HGA2 : filtres compte (archivés compris), période et nature ; HGA3 : recherche
            Filtres(etat, vm)

            when {
                etat.chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                etat.erreur != null && etat.transactions.isEmpty() && etat.journal.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // HG15 : une erreur n'est jamais présentée comme « aucun historique »
                    Text(etat.erreur ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.recharger() }) { Text("Réessayer") }
                }
                else -> {
                    val lignes: List<Pair<java.time.LocalDate?, Any>> = if (etat.vue == Vue.TRANSACTIONS) {
                        vm.transactionsVisibles(etat).map { it.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() to it }
                    } else {
                        vm.journalVisible(etat).map { JournalHistorique.jour(JournalHistorique.instantJournal(it.dateAction)) to it }
                    }
                    if (lignes.isEmpty() && etat.finAtteinte) {
                        Text(
                            if (etat.recherche.isNotBlank() || etat.nature != null) "Aucun résultat pour ces filtres."
                            else "Aucune entrée pour cette période.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    val etatListe = rememberLazyListState()
                    // HG17 : la page suivante se charge en approchant du bas ; la position est gardée
                    val prochePasDuBas by remember {
                        derivedStateOf {
                            val dernier = etatListe.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            dernier >= etatListe.layoutInfo.totalItemsCount - 5
                        }
                    }
                    LaunchedEffect(prochePasDuBas, etat.finAtteinte, etat.vue) {
                        if (prochePasDuBas && !etat.finAtteinte) vm.chargerSuite()
                    }
                    // HGA4 : regroupement par jour, chaque jour se replie
                    val parJour = lignes.groupBy { it.first }
                    LazyColumn(state = etatListe, modifier = Modifier.fillMaxSize()) {
                        parJour.forEach { (jour, elements) ->
                            val cleJour = jour?.toString() ?: "inconnu"
                            item(key = "jour:$cleJour") {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { joursFermes = if (cleJour in joursFermes) joursFermes - cleJour else joursFermes + cleJour }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(JournalHistorique.titreJour(jour), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("${elements.size} • " + if (cleJour in joursFermes) "▸" else "▾",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (cleJour !in joursFermes) {
                                items(elements, key = { (_, el) ->
                                    when (el) { is Transaction -> "t:${el.id}"; is HistoriqueAllocation -> "h:${el.id}"; else -> el.hashCode() }
                                }) { (_, el) ->
                                    when (el) {
                                        is Transaction -> LigneTransaction(el, etat, vm) { scope.launch { detail = vm.detail(el) } }
                                        is HistoriqueAllocation -> LigneJournal(el, etat, vm) { scope.launch { detail = vm.detail(el) } }
                                    }
                                }
                            }
                        }
                        item(key = "pied") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    etat.chargementSuite -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    etat.erreur != null -> TextButton(onClick = { vm.chargerSuite() }) { Text("Réessayer la suite") }
                                    etat.finAtteinte -> Text("Fin de l'historique pour cette période", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else -> TextButton(onClick = { vm.chargerSuite() }) { Text("Charger plus") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { d ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.titre) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { d.lignes.forEach { Text(it, fontSize = 14.sp) } } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Fermer") } }
        )
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filtres(etat: HistoriqueGeneralViewModel.Etat, vm: HistoriqueGeneralViewModel) {
    var menuCompte by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExposedDropdownMenuBox(expanded = menuCompte, onExpandedChange = { menuCompte = !menuCompte }) {
            OutlinedTextField(
                value = etat.compteId?.let { etat.nomCompte(it) } ?: "Tous les comptes",
                onValueChange = {}, readOnly = true, label = { Text("Compte") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCompte) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = menuCompte, onDismissRequest = { menuCompte = false }) {
                DropdownMenuItem(text = { Text("Tous les comptes") }, onClick = { menuCompte = false; vm.choisirCompte(null) })
                // HG8/HG9 : tous les types de comptes, archivés compris (consultation seulement)
                etat.comptes.forEach { c ->
                    val type = when (c) {
                        is com.xburnsx.toutiebudget.data.modeles.CompteCheque -> "chèque"
                        is com.xburnsx.toutiebudget.data.modeles.CompteCredit -> "carte"
                        is com.xburnsx.toutiebudget.data.modeles.CompteDette -> "dette"
                        is com.xburnsx.toutiebudget.data.modeles.CompteInvestissement -> "placement"
                    }
                    DropdownMenuItem(
                        text = { Text(c.nom + " ($type${if (c.estArchive) ", archivé" else ""})") },
                        onClick = { menuCompte = false; vm.choisirCompte(c.id) }
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Periode.entries.forEach { p ->
                FilterChip(selected = etat.periode == p, onClick = { vm.choisirPeriode(p) }, label = { Text(p.libelle) })
            }
        }
        val natures = if (etat.vue == Vue.TRANSACTIONS)
            listOf(Nature.DEPENSE, Nature.REVENU, Nature.VIREMENT, Nature.PAIEMENT, Nature.PRET)
        else listOf(Nature.ALLOCATION, Nature.EFFET_TRANSACTION, Nature.CORRECTION)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = etat.nature == null, onClick = { vm.choisirNature(null) }, label = { Text("Tous types") })
            natures.forEach { n ->
                FilterChip(selected = etat.nature == n, onClick = { vm.choisirNature(n) }, label = { Text(n.libelle) })
            }
        }
        OutlinedTextField(
            value = etat.recherche, onValueChange = vm::rechercher, singleLine = true,
            label = { Text("Rechercher : tiers, enveloppe, montant, note") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LigneTransaction(t: Transaction, etat: HistoriqueGeneralViewModel.Etat, vm: HistoriqueGeneralViewModel, onClick: () -> Unit) {
    val nature = JournalHistorique.nature(t)
    val montant = JournalHistorique.montantSigne(t)
    val couleur = when (nature) {
        Nature.DEPENSE -> Color(0xFFEF5350)
        Nature.REVENU -> Color(0xFF66BB6A)
        else -> MaterialTheme.colorScheme.onSurface // virements, paiements, prêts : ni revenu ni dépense
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(t.tiersUtiliser?.takeIf { it.isNotBlank() } ?: t.type.libelle) },
        supportingContent = {
            Column {
                Text("${nature.libelle} • ${etat.nomCompte(t.compteId)}", fontSize = 12.sp)
                vm.nomsEnveloppes(t, etat).takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp) }
                Text(vm.provenance(t, etat), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        // HG10 : la devise est déjà dans le formateur ; le signe n'est ajouté qu'une fois
        trailingContent = { Text(MoneyFormatter.formatAmount(montant), color = couleur, fontWeight = FontWeight.Bold) }
    )
}

@Composable
private fun LigneJournal(h: HistoriqueAllocation, etat: HistoriqueGeneralViewModel.Etat, vm: HistoriqueGeneralViewModel, onClick: () -> Unit) {
    val nature = JournalHistorique.nature(h)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(h.description) },
        supportingContent = {
            Column {
                Text("${nature.libelle} • ${etat.nomCompte(h.compteId)} • " +
                    JournalHistorique.heureLocale(JournalHistorique.instantJournal(h.dateAction)), fontSize = 12.sp)
                // HG14 : valeurs enregistrées au moment du mouvement, jamais les soldes actuels
                if (JournalHistorique.aPretAPlacer(h.collectionCompte) &&
                    Math.round(h.pretAPlacerAvant * 100.0) != Math.round(h.pretAPlacerApres * 100.0)
                ) {
                    Text("Prêt à placer : ${MoneyFormatter.formatAmount(h.pretAPlacerAvant)} → ${MoneyFormatter.formatAmount(h.pretAPlacerApres)}", fontSize = 12.sp)
                }
                JournalHistorique.soldeEnveloppe(h.details)?.let { (avant, apres) ->
                    Text("${h.enveloppeNom} : ${MoneyFormatter.formatAmount(avant)} → ${MoneyFormatter.formatAmount(apres)}", fontSize = 12.sp)
                }
                Text(vm.provenance(h, etat), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Text(
                (if (h.montant > 0) "+" else "") + MoneyFormatter.formatAmount(h.montant),
                fontWeight = FontWeight.Bold,
                color = if (nature == Nature.CORRECTION) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface
            )
        }
    )
}
