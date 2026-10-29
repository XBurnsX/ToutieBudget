package com.xburnsx.toutiebudget.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xburnsx.toutiebudget.data.services.SauvegardeBudget
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.ui.settings.GestionBaseViewModel.Action
import com.xburnsx.toutiebudget.ui.settings.GestionBaseViewModel.Element
import com.xburnsx.toutiebudget.ui.settings.GestionBaseViewModel.Onglet
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire de base de données (section 23) : outil de diagnostic, PAS un raccourci pour contourner
 * les règles financières. Consultation par défaut ; mode développeur explicite pour les seules actions
 * sûres, toujours par les moteurs métier, avec aperçu et confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") couleurTheme: CouleurTheme = CouleurTheme.PINK
) {
    val context = LocalContext.current
    val vm: GestionBaseViewModel = viewModel { GestionBaseViewModel(context.applicationContext) }
    val etat by vm.etat.collectAsState()
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Element?>(null) }
    var demandeModeDev by remember { mutableStateOf(false) }
    var actionDemandee by remember { mutableStateOf<Pair<Action, Element>?>(null) }
    var motDePasseExport by remember { mutableStateOf<CharArray?>(null) }
    var demandeExport by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    // BDA3 : sauvegarde protégée avant une réparation avancée (même format que Paramètres)
    val lanceurExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val mdp = motDePasseExport
        motDePasseExport = null
        if (uri == null || mdp == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportMessage = withContext(Dispatchers.IO) {
                runCatching {
                    val (octets, contenu) = AppModule.provideSauvegardeBudget().exporter(mdp)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(octets) } ?: error("écriture impossible")
                    val relu = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("relecture impossible")
                    check(SauvegardeBudget.lire(relu, mdp).total == contenu.total) { "le fichier relu ne correspond pas" }
                    "Sauvegarde exportée et vérifiée : ${contenu.resume()}."
                }.getOrElse { "Export impossible : ${it.message}" }
            }
            mdp.fill(' ')
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Base de données (diagnostic)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // BDA5 : santé
            item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Santé de la base", fontWeight = FontWeight.Bold)
                        etat.sante?.lignes?.forEach { Text(it, fontSize = 13.sp) } ?: Text("Lecture…", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.chargerSante() }) { Text("Actualiser") }
                            OutlinedButton(enabled = !etat.analyseEnCours, onClick = { vm.analyser() }) {
                                if (etat.analyseEnCours) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("Vérifier la cohérence")
                            }
                        }
                        // BDA2
                        etat.constats?.let { constats ->
                            if (constats.isEmpty()) Text("Aucune incohérence trouvée.", color = Color(0xFF81C784), fontSize = 13.sp)
                            else {
                                Text("${constats.size} constat(s) — rien n'est réparé automatiquement :", fontSize = 13.sp)
                                constats.take(50).forEach { c ->
                                    Text("• ${c.gravite.libelle} : ${c.texte}", fontSize = 12.sp,
                                        color = if (c.gravite == DiagnosticBase.Gravite.ERREUR) MaterialTheme.colorScheme.error else Color(0xFFFFB74D))
                                }
                                if (constats.size > 50) Text("… et ${constats.size - 50} autre(s).", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            // BD1 : mode consultation par défaut
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (etat.modeDeveloppeur) "Mode développeur actif" else "Consultation seulement", fontWeight = FontWeight.Bold)
                            Text(
                                if (etat.modeDeveloppeur) "Actions limitées, faites par les moteurs de l'app, avec aperçu."
                                else "Aucune donnée ne peut être modifiée ici.", fontSize = 12.sp
                            )
                        }
                        Switch(checked = etat.modeDeveloppeur, onCheckedChange = {
                            if (it) demandeModeDev = true else vm.activerModeDeveloppeur(false)
                        })
                    }
                }
            }
            // BD20 : onglets défilants, aucun coupé sur téléphone
            item {
                ScrollableTabRow(selectedTabIndex = etat.onglet.ordinal, edgePadding = 8.dp) {
                    Onglet.entries.forEach { o ->
                        Tab(selected = etat.onglet == o, onClick = { vm.choisirOnglet(o) }, text = { Text(o.libelle) })
                    }
                }
            }
            // BDA7 : recherche
            item {
                OutlinedTextField(
                    value = etat.recherche, onValueChange = vm::rechercher, singleLine = true,
                    label = { Text("Rechercher (nom, montant, date, ID)") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
            when {
                etat.chargement -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                etat.erreur != null && etat.elements.isEmpty() -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text(etat.erreur ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.recharger() }) { Text("Réessayer") }
                    }
                }
                else -> {
                    val visibles = vm.elementsVisibles(etat)
                    if (visibles.isEmpty()) item { Text("Aucun élément.", modifier = Modifier.padding(16.dp)) }
                    items(visibles, key = { it.id.ifBlank { it.json.hashCode().toString() } }) { el ->
                        ListItem(
                            modifier = Modifier.clickable { detail = el },
                            headlineContent = { Text(el.titre) },
                            supportingContent = { Text(el.resume, fontSize = 12.sp) }
                        )
                        HorizontalDivider()
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                etat.erreur != null -> TextButton(onClick = { vm.chargerSuite() }) { Text("Réessayer : ${etat.erreur}") }
                                !etat.finAtteinte -> TextButton(onClick = { vm.chargerSuite() }) { Text("Charger plus") }
                                else -> Text("${visibles.size} élément(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // BDA6 : détail en lecture seule, JSON et ID copiables
    detail?.let { el ->
        val presse = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(el.titre) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(el.resume, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ID : ${el.id}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { presse.setText(AnnotatedString(el.id)) }) { Text("Copier") }
                    }
                    SelectionContainer {
                        Text(el.json, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                    el.ouModifier?.let { Text("Modifier : $it", fontSize = 12.sp, color = Color(0xFFFFB74D)) }
                    // BD22 : actions destructives distinctes, seulement en mode développeur
                    if (etat.modeDeveloppeur && el.actions.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Actions (mode développeur)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        el.actions.forEach { a ->
                            val libelle = when (a) {
                                Action.SUPPRIMER_TRANSACTION -> "Supprimer la transaction…"
                                Action.RENOMMER_CATEGORIE -> "Renommer la catégorie…"
                                Action.SUPPRIMER_CATEGORIE -> "Supprimer la catégorie…"
                                Action.RENOMMER_TIERS -> "Renommer le tiers…"
                                Action.SUPPRIMER_TIERS -> "Supprimer le tiers…"
                            }
                            val destructive = a == Action.SUPPRIMER_TRANSACTION || a == Action.SUPPRIMER_CATEGORIE || a == Action.SUPPRIMER_TIERS
                            TextButton(onClick = { detail = null; actionDemandee = a to el }) {
                                Text(libelle, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Fermer") } }
        )
    }

    // BD4/BDA4 : aperçu exact puis confirmation explicite
    actionDemandee?.let { (action, el) ->
        val renommer = action == Action.RENOMMER_CATEGORIE || action == Action.RENOMMER_TIERS
        var nom by remember(el.id, action) { mutableStateOf(el.titre) }
        var apercu by remember(el.id, action) { mutableStateOf<String?>(null) }
        LaunchedEffect(el.id, action, nom) {
            apercu = runCatching { vm.apercu(action, el, nom) }.getOrElse { "Aperçu impossible : ${it.message}" }
        }
        AlertDialog(
            onDismissRequest = { if (!etat.actionEnCours) actionDemandee = null },
            title = { Text("Avant / après") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (renommer) OutlinedTextField(value = nom, onValueChange = { nom = it }, singleLine = true, label = { Text("Nouveau nom") })
                    Text(apercu ?: "Préparation de l'aperçu…", fontSize = 14.sp)
                    Text("Conseil : exporte d'abord une sauvegarde protégée.", fontSize = 12.sp, color = Color(0xFFFFB74D))
                    TextButton(onClick = { demandeExport = true }) { Text("Exporter une sauvegarde maintenant") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !etat.actionEnCours && apercu != null && (!renommer || (nom.isNotBlank() && nom.trim() != el.titre)),
                    onClick = { vm.executer(action, el, nom); actionDemandee = null }
                ) { Text("Confirmer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { actionDemandee = null }) { Text("Annuler") } }
        )
    }

    if (demandeModeDev) {
        AlertDialog(
            onDismissRequest = { demandeModeDev = false },
            title = { Text("Activer le mode développeur ?") },
            text = {
                Text("Permet seulement : supprimer une transaction (tous ses effets annulés par le moteur de l'app), " +
                    "renommer ou supprimer une catégorie, renommer ou supprimer un tiers inutilisé. " +
                    "Les soldes, le Prêt à placer, les allocations, les dettes, les enveloppes et les prêts ne se modifient jamais ici. " +
                    "Le mode se désactive en quittant cet écran.")
            },
            confirmButton = { TextButton(onClick = { demandeModeDev = false; vm.activerModeDeveloppeur(true) }) { Text("Activer") } },
            dismissButton = { TextButton(onClick = { demandeModeDev = false }) { Text("Annuler") } }
        )
    }

    if (demandeExport) {
        DialogueMotDePasse(
            titre = "Mot de passe de la sauvegarde",
            confirmer = true,
            onAnnuler = { demandeExport = false },
            onValider = { mdp ->
                demandeExport = false
                motDePasseExport = mdp
                lanceurExport.launch("toutiebudget-${java.time.LocalDate.now()}.toutiebudget.json")
            }
        )
    }

    // BD16/BD22 : vrai résultat, affiché jusqu'à « OK »
    (etat.message ?: exportMessage)?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.effacerMessage(); exportMessage = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { vm.effacerMessage(); exportMessage = null }) { Text("OK") } }
        )
    }
}
