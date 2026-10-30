package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import java.util.Date


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GererSoldeNegatifScreen(
    viewModel: BudgetViewModel,
    moisSelectionne: Date,
    onRetour: () -> Unit,
    onVirementClick: (enveloppeId: String, montant: Double, mois: Date, couvertureTotale: Boolean) -> Unit,
    onVoirHistoriqueEnveloppe: (enveloppeId: String, nom: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    // GA1 : mois choisi sur la page, parti de celui du Budget. Le ViewModel est partagé :
    // le Budget affichera ce même mois au retour.
    var mois by remember(moisSelectionne.time) { mutableStateOf(moisSelectionne) }
    // GN1/GN2 : la page doit lire les allocations du mois envoyé par Budget,
    // non réutiliser par hasard celles du mois courant du ViewModel partagé.
    LaunchedEffect(mois.time) {
        viewModel.chargerDonneesBudget(mois)
    }
    // GN9/GA4 : montant personnalisé, validé au cent, jamais de virement à l'ouverture
    var enveloppePersonnalisee by remember { mutableStateOf<EnveloppeUi?>(null) }
    // GA3 : couverture de plusieurs enveloppes, confirmée explicitement, en une seule opération
    var couvertureMultipleOuverte by remember { mutableStateOf(false) }
    var messageCouverture by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.resultatRepartition.collect { r ->
            messageCouverture = r.erreur?.let { "Rien n'a été couvert : $it" }
                ?: "Couvert : ${r.reussies.joinToString()}"
        }
    }
    
    // Filtrer uniquement les enveloppes avec solde négatif
    val enveloppesNegatives = uiState.categoriesEnveloppes
        .map { categorie ->
            val enveloppesFiltrees = categorie.enveloppes.filter { kotlin.math.round(it.solde * 100.0).toLong() < 0L }
            if (enveloppesFiltrees.isNotEmpty()) {
                categorie.copy(enveloppes = enveloppesFiltrees)
            } else null
        }
        .filterNotNull()
    
    // État pour les catégories ouvertes/fermées
    var categoriesOuvertes by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Initialiser toutes les catégories comme ouvertes par défaut
    LaunchedEffect(enveloppesNegatives) {
        // GN14 : conserver le choix à travers les mises à jour de solde.
        categoriesOuvertes = categoriesOuvertes.toMutableMap().apply {
            enveloppesNegatives.forEach { categorie -> putIfAbsent(categorie.nomCategorie, true) }
        }
    }
    
    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Attention",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Gérer Soldes Négatifs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingScaffold ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingScaffold)) {
        SelecteurMoisDeficit(mois = mois, onChanger = { mois = it })
        val paddingValues = PaddingValues(0.dp)
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.erreur != null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Impossible de charger les enveloppes : ${uiState.erreur}", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.chargerDonneesBudget(mois) }) {
                        Text("Réessayer")
                    }
                }
            }
        } else if (enveloppesNegatives.isEmpty()) {
            // Aucune enveloppe négative
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Aucun problème",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun solde négatif !",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toutes vos enveloppes sont en bon état",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                    if (uiState.montantDejaCouvertMois > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Déjà couvert ce mois-ci : " +
                                com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(uiState.montantDejaCouvertMois),
                            color = Color(0xFF10B981),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // Affichage des enveloppes négatives
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // En-tête avec résumé
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Attention",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Résumé des soldes négatifs",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val totalEnveloppes = enveloppesNegatives.sumOf { it.enveloppes.size }
                            val totalMontantNegatif = enveloppesNegatives
                                .flatMap { it.enveloppes }
                                .sumOf { -kotlin.math.round(it.solde * 100.0).toLong() } / 100.0
                            Text(
                                text = "$totalEnveloppes enveloppe${if (totalEnveloppes > 1) "s" else ""} concernée${if (totalEnveloppes > 1) "s" else ""}",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "À couvrir : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(totalMontantNegatif)}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // GA5 : ce qui a déjà été couvert ce mois-ci (virements notés sur cet appareil)
                            Text(
                                text = "Déjà couvert ce mois-ci : " +
                                    com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(uiState.montantDejaCouvertMois),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            if (totalEnveloppes > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { couvertureMultipleOuverte = true }) {
                                    Text("Couvrir plusieurs enveloppes", color = Color.White)
                                }
                            }
                            // GA2 : Prêt à placer propre à chaque compte (aucune réserve globale)
                            if (uiState.bandeauxPretAPlacer.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Prêt à placer disponible :", color = Color.White, fontSize = 14.sp)
                                uiState.bandeauxPretAPlacer.forEach { p ->
                                    Text(
                                        "• ${p.nomCompte} : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(p.montant)}",
                                        color = Color.White, fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Enveloppes groupées par catégorie
                items(enveloppesNegatives, key = { it.nomCategorie }) { categorie ->
                    Column {
                        // En-tête de catégorie avec flèche pliable
                        val estOuverte = categoriesOuvertes[categorie.nomCategorie] ?: true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1F1F1F))
                                .clickable { 
                                    categoriesOuvertes = categoriesOuvertes.toMutableMap().apply {
                                        put(categorie.nomCategorie, !estOuverte)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = categorie.nomCategorie,
                                color = Color(0xFFDC2626),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Compteur d'enveloppes négatives dans cette catégorie
                            Text(
                                text = "${categorie.enveloppes.size} enveloppe${if (categorie.enveloppes.size > 1) "s" else ""}",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            
                            // Flèche pliable
                            Icon(
                                imageVector = if (estOuverte) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (estOuverte) "Fermer la catégorie" else "Ouvrir la catégorie",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Enveloppes (affichées seulement si la catégorie est ouverte)
                        if (estOuverte) {
                            categorie.enveloppes.forEach { enveloppe ->
                                val deficit = -kotlin.math.round(enveloppe.solde * 100.0).toLong() / 100.0 // GN5 : montant positif au cent
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onVirementClick(enveloppe.id, deficit, mois, true) }
                                ) {
                                    EnveloppeItem(enveloppe = enveloppe)
                                }
                                // GA4 / GA6 : raccourcis explicites ; rien ne se fait sans appui
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onVoirHistoriqueEnveloppe(enveloppe.id, enveloppe.nom) }) {
                                        Text("Historique", color = Color.LightGray)
                                    }
                                    TextButton(onClick = { enveloppePersonnalisee = enveloppe }) {
                                        Text("Autre montant")
                                    }
                                    TextButton(onClick = { onVirementClick(enveloppe.id, deficit, mois, true) }) {
                                        Text("Couvrir entièrement")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Espace en bas
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
      }
    }

    messageCouverture?.let { message ->
        AlertDialog(
            onDismissRequest = { messageCouverture = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { messageCouverture = null }) { Text("OK") } }
        )
    }

    if (couvertureMultipleOuverte) {
        val negatives = uiState.categoriesEnveloppes.flatMap { it.enveloppes }
            .filter { kotlin.math.round(it.solde * 100.0).toLong() < 0L }
        var choisies by remember { mutableStateOf(negatives.map { it.id }.toSet()) }
        var compteId by remember { mutableStateOf(uiState.bandeauxPretAPlacer.firstOrNull()?.compteId) }
        val montants = negatives.filter { it.id in choisies }
            .associate { it.id to -kotlin.math.round(it.solde * 100.0).toLong() }
        val totalCentimes = montants.values.sum()
        val compte = uiState.bandeauxPretAPlacer.firstOrNull { it.compteId == compteId }
        val fondsSuffisants = compte != null && kotlin.math.round(compte.montant * 100.0).toLong() >= totalCentimes
        AlertDialog(
            onDismissRequest = { couvertureMultipleOuverte = false },
            title = { Text("Couvrir plusieurs enveloppes") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Depuis le Prêt à placer de :", fontWeight = FontWeight.Bold)
                    uiState.bandeauxPretAPlacer.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = compteId == p.compteId, onClick = { compteId = p.compteId })
                            Text("${p.nomCompte} (${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(p.montant)})")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Écritures prévues :", fontWeight = FontWeight.Bold)
                    negatives.forEach { env ->
                        val centimes = -kotlin.math.round(env.solde * 100.0).toLong()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = env.id in choisies,
                                onCheckedChange = { coche -> choisies = if (coche) choisies + env.id else choisies - env.id }
                            )
                            Text("${env.nom} : +${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(centimes / 100.0)}")
                        }
                    }
                    Text(
                        "Total : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(totalCentimes / 100.0)}",
                        fontWeight = FontWeight.Bold
                    )
                    if (compte != null && !fondsSuffisants) {
                        Text("Prêt à placer insuffisant sur ${compte.nomCompte}.", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Tout est fait en une seule opération : si une écriture échoue, rien n'est changé.",
                        fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = compteId != null && montants.isNotEmpty() && fondsSuffisants,
                    onClick = {
                        couvertureMultipleOuverte = false
                        compteId?.let { viewModel.assignerAPlusieursEnveloppes(it, montants) }
                    }
                ) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { couvertureMultipleOuverte = false }) { Text("Annuler") } }
        )
    }

    enveloppePersonnalisee?.let { env ->
        val deficit = -kotlin.math.round(env.solde * 100.0).toLong() / 100.0
        var saisieCentimes by remember(env.id) { mutableStateOf(0L) }
        val centimes = saisieCentimes.takeIf { it > 0L }
        AlertDialog(
            onDismissRequest = { enveloppePersonnalisee = null },
            title = { Text("Couvrir une partie de « ${env.nom} »") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Déficit : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(deficit)}. " +
                        "Le reste peut rester négatif.")
                    com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel(
                        valeur = saisieCentimes,
                        onValeurChange = { saisieCentimes = it },
                        libelle = "Montant à couvrir"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = centimes != null,
                    onClick = {
                        val montant = centimes ?: return@TextButton
                        enveloppePersonnalisee = null
                        onVirementClick(env.id, montant / 100.0, mois, false)
                    }
                ) { Text("Continuer") }
            },
            dismissButton = { TextButton(onClick = { enveloppePersonnalisee = null }) { Text("Annuler") } }
        )
    }
}

/** GA1 : changer de mois sans quitter la page. */
@Composable
private fun SelecteurMoisDeficit(mois: Date, onChanger: (Date) -> Unit) {
    fun decaler(n: Int) = java.util.Calendar.getInstance().apply {
        time = mois; set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, n)
    }.time
    val libelle = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.CANADA_FRENCH).format(mois)
        .replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChanger(decaler(-1)) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mois précédent", tint = Color.White)
        }
        Text(libelle, color = Color.White, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChanger(decaler(1)) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Mois suivant", tint = Color.White)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GererSoldeNegatifScreenPreview() {
    // Preview simplifié - affichage d'un message d'information
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Attention",
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Gérer Soldes Négatifs",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Page de gestion des enveloppes avec solde négatif",
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
