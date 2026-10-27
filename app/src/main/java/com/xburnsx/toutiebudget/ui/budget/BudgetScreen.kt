// chemin/simule: /ui/budget/BudgetScreen.kt
// Dépendances: ClavierBudgetEnveloppe, BudgetViewModel
// INTÉGRATION du clavier spécialisé dans la page Budget existante

package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.budget.composants.ClavierBudgetEnveloppe
import com.xburnsx.toutiebudget.ui.budget.composants.CompteBudget
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import com.xburnsx.toutiebudget.ui.budget.composants.PretAPlacerCarte
import com.xburnsx.toutiebudget.ui.budget.composants.PrevisionsDialog
import com.xburnsx.toutiebudget.ui.budget.composants.RepartitionMultipleDialog
import com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import java.util.Date
import androidx.compose.material.icons.filled.Menu

/** A3 : filtres d'enveloppes proposés à la demande. */
enum class FiltreEnveloppes(val libelle: String) {
    FAVORITES("Favorites"),
    NEGATIVES("Négatives"),
    SANS_FONDS("Sans fonds"),
    A_FINANCER("Objectif à financer"),
    AVEC_OBJECTIF("Avec objectif"),
    AVEC_SOLDE("Solde disponible");

    fun accepte(enveloppe: EnveloppeUi, favorites: Set<String>): Boolean = when (this) {
        FAVORITES -> enveloppe.id in favorites
        NEGATIVES -> kotlin.math.round(enveloppe.solde * 100).toLong() < 0L
        SANS_FONDS -> kotlin.math.round(enveloppe.solde * 100).toLong() == 0L
        A_FINANCER -> kotlin.math.round(enveloppe.versementRecommande * 100).toLong() > 0L
        AVEC_OBJECTIF -> enveloppe.objectif > 0 && enveloppe.typeObjectif != TypeObjectif.Aucun
        AVEC_SOLDE -> kotlin.math.round(enveloppe.solde * 100).toLong() > 0L
    }
}

private const val CATEGORIE_FAVORIS = "★ Favoris"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onCategoriesClick: (() -> Unit)? = null,
    onVirementClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onPretPersonnelClick: (() -> Unit)? = null,
    onGererSoldeNegatifClick: ((Date) -> Unit)? = null,
    onVoirHistoriqueEnveloppe: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    var moisSelectionne by remember { mutableStateOf(Date()) }

    // 🆕 ÉTATS POUR LE CLAVIER ENVELOPPE
    var enveloppeSelectionnee by remember { mutableStateOf<EnveloppeUi?>(null) }
    var showClavierEnveloppe by remember { mutableStateOf(false) }
    var comptesDisponibles by remember { mutableStateOf<List<CompteBudget>>(emptyList()) }

    // A3 / A5 : recherche et filtres à la demande (rien d'affiché tant qu'ils sont fermés)
    var rechercheOuverte by rememberSaveable { mutableStateOf(false) }
    var recherche by rememberSaveable { mutableStateOf("") }
    var filtre by rememberSaveable { mutableStateOf<FiltreEnveloppes?>(null) }
    val filtreActif = rechercheOuverte && (recherche.isNotBlank() || filtre != null)

    // A2 / A7
    var showRepartition by remember { mutableStateOf(false) }
    var repartitionEnCours by remember { mutableStateOf(false) }
    var repartitionErreur by remember { mutableStateOf<String?>(null) }
    var showPrevisions by remember { mutableStateOf(false) }

    // 🆕 CHARGER LES COMPTES POUR LE CLAVIER - CORRECTION
    LaunchedEffect(uiState.bandeauxPretAPlacer, uiState.isLoading) {
        // Mettre à jour la liste chaque fois que les données changent
        if (!uiState.isLoading) {
            comptesDisponibles = uiState.bandeauxPretAPlacer
                .filter { it.montant > 0.0 } // Seulement les comptes avec de l'argent
                .map { bandeau ->
                    CompteBudget(
                        id = bandeau.compteId,
                        nom = bandeau.nomCompte,
                        pretAPlacer = bandeau.montant,
                        couleur = bandeau.couleurCompte
                    )
                }
        }
    }

    // C3 : fermer le clavier seulement quand SON assignation a réussi
    // (un simple rafraîchissement du budget ne le ferme plus)
    LaunchedEffect(Unit) {
        viewModel.assignationReussie.collect { enveloppeId ->
            if (enveloppeSelectionnee?.id == enveloppeId) {
                showClavierEnveloppe = false
                enveloppeSelectionnee = null
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resultatRepartition.collect { resultat ->
            repartitionEnCours = false
            if (resultat.erreur == null) {
                showRepartition = false
                repartitionErreur = null
            } else {
                repartitionErreur = if (resultat.reussies.isEmpty()) {
                    resultat.erreur
                } else {
                    "Déjà assignées : ${resultat.reussies.joinToString()}. Arrêt : ${resultat.erreur}"
                }
            }
        }
    }

    // Enveloppes affichées : recherche et filtre appliqués à la demande
    val toutesEnveloppes = uiState.categoriesEnveloppes.flatMap { it.enveloppes }
    val categoriesAffichees = if (!filtreActif) {
        uiState.categoriesEnveloppes
    } else {
        uiState.categoriesEnveloppes.mapNotNull { categorie ->
            val gardees = categorie.enveloppes.filter { enveloppe ->
                (recherche.isBlank() || enveloppe.nom.contains(recherche.trim(), ignoreCase = true)) &&
                    (filtre?.accepte(enveloppe, uiState.enveloppesFavorites) ?: true)
            }
            if (gardees.isEmpty()) null else categorie.copy(enveloppes = gardees)
        }
    }
    // A4 : section compacte des favoris, en tête, sans retirer les enveloppes de leur catégorie
    val favoris = if (filtreActif) emptyList() else {
        toutesEnveloppes.filter { it.id in uiState.enveloppesFavorites }
            .distinctBy { it.id }
    }

    val ouvrirClavier: (EnveloppeUi) -> Unit = { enveloppe ->
        enveloppeSelectionnee = enveloppe
        showClavierEnveloppe = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    SelecteurMoisAnnee(
                        moisSelectionne = moisSelectionne,
                        onMoisChange = {
                            moisSelectionne = it
                            viewModel.chargerDonneesBudget(it)
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                ),
                actions = {
                    // Menu hamburger (à droite, le plus à gauche des icônes)
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp) // Zone de clic plus grande
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp) // Icône plus grande
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Prêt personnel",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onPretPersonnelClick?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Virer argent",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onVirementClick?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Répartir l'argent",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    repartitionErreur = null
                                    showRepartition = true
                                },
                                enabled = !uiState.isLoading && uiState.erreur == null
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Prévisions",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showPrevisions = true
                                },
                                enabled = !uiState.isLoading && uiState.erreur == null
                            )
                        }
                    }

                    // A5 : recherche / filtres, ouverts seulement sur demande
                    IconButton(onClick = {
                        rechercheOuverte = !rechercheOuverte
                        if (!rechercheOuverte) {
                            recherche = ""
                            filtre = null
                        }
                    }) {
                        Icon(
                            imageVector = if (rechercheOuverte) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (rechercheOuverte) "Fermer la recherche" else "Rechercher une enveloppe",
                            tint = Color.White
                        )
                    }

                    // Icône des catégories (séparée)
                    IconButton(onClick = { onCategoriesClick?.invoke() }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.gerer_categorie),
                            contentDescription = "Catégories",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Icône Settings (ouvre une page)
                    IconButton(onClick = { onSettingsClick?.invoke() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Contenu de la page
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.figerPretAPlacer) {
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(vertical = 8.dp)
                    ) {
                        uiState.bandeauxPretAPlacer.forEach { bandeau ->
                            PretAPlacerCarte(
                                nomCompte = bandeau.nomCompte,
                                montant = bandeau.montant,
                                couleurCompte = bandeau.couleurCompte
                            )
                        }
                    }
                }
            } else {
                // Bandeaux non épinglés
                items(uiState.bandeauxPretAPlacer, key = { it.compteId }) { bandeau ->
                    PretAPlacerCarte(
                        nomCompte = bandeau.nomCompte,
                        montant = bandeau.montant,
                        couleurCompte = bandeau.couleurCompte
                    )
                }
            }

            // 🔎 A5 / A3 : barre de recherche et filtres (visible seulement si demandée)
            if (rechercheOuverte) {
                item(key = "recherche_filtres") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = recherche,
                            onValueChange = { recherche = it },
                            placeholder = { Text("Nom de l'enveloppe") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(FiltreEnveloppes.entries.toList()) { option ->
                                FilterChip(
                                    selected = filtre == option,
                                    onClick = { filtre = if (filtre == option) null else option },
                                    label = { Text(option.libelle) }
                                )
                            }
                        }
                        if (filtreActif && categoriesAffichees.isEmpty()) {
                            Text(
                                "Aucune enveloppe ne correspond.",
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 🚨 CARTE D'ALERTE POUR ENVELOPPES NÉGATIVES
            val enveloppesNegatives = toutesEnveloppes
                .filter { kotlin.math.round(it.solde * 100.0).toLong() < 0L }
                .count()

            if (enveloppesNegatives > 0 && !filtreActif) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDC2626) // Rouge vif pour l'alerte
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onGererSoldeNegatifClick?.invoke(moisSelectionne) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Attention",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$enveloppesNegatives enveloppe${if (enveloppesNegatives > 1) "s" else ""} ont besoin d'attention !",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // ⭐ A4 : favoris (catégorie compacte repliable, l'ordre des vraies catégories ne change pas)
            if (favoris.isNotEmpty()) {
                item(key = "favoris") {
                    val estOuverte = uiState.categoriesOuvertes[CATEGORIE_FAVORIS] ?: true
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF121212))
                                .clickable { viewModel.toggleCategorie(CATEGORIE_FAVORIS) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Favoris",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (estOuverte) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (estOuverte) "Fermer les favoris" else "Ouvrir les favoris",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (estOuverte) {
                            favoris.forEach { enveloppe ->
                                Box(modifier = Modifier.clickable { ouvrirClavier(enveloppe) }) {
                                    EnveloppeItem(enveloppe = enveloppe)
                                }
                            }
                        }
                    }
                }
            }

            // Enveloppes groupées par catégorie
            items(categoriesAffichees, key = { it.nomCategorie }) { categorie ->
                Column {
                    // En-tête de catégorie avec flèche pliable
                    // Pendant une recherche/filtre, les résultats restent visibles sans changer l'état mémorisé
                    val estOuverte = filtreActif || (uiState.categoriesOuvertes[categorie.nomCategorie] ?: true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .clickable(enabled = !filtreActif) { viewModel.toggleCategorie(categorie.nomCategorie) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categorie.nomCategorie,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        // Flèche pliable
                        Icon(
                            imageVector = if (estOuverte) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (estOuverte) "Fermer la catégorie" else "Ouvrir la catégorie",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 🆕 ENVELOPPES (affichées seulement si la catégorie est ouverte)
                    if (estOuverte) {
                        categorie.enveloppes.forEach { enveloppe ->
                            Box(
                                modifier = Modifier.clickable { ouvrirClavier(enveloppe) }
                            ) {
                                EnveloppeItem(enveloppe = enveloppe)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // 🆕 CLAVIER BUDGET ENVELOPPE
    if (showClavierEnveloppe && enveloppeSelectionnee != null) {
        val enveloppeClavier = enveloppeSelectionnee!!
        ClavierBudgetEnveloppe(
            enveloppe = enveloppeClavier,
            comptesDisponibles = comptesDisponibles,
            comptePreselectionne = comptesDisponibles.firstOrNull(), // Premier compte par défaut
            onAssigner = { montantCentimes, compteSourceId ->
                // 🎯 LOGIQUE D'ASSIGNATION - le clavier se ferme seulement sur réussite (C3)
                viewModel.assignerArgentAEnveloppe(
                    enveloppeId = enveloppeClavier.id,
                    compteSourceId = compteSourceId,
                    montantCentimes = montantCentimes
                )
            },
            onFermer = {
                showClavierEnveloppe = false
                enveloppeSelectionnee = null
            },
            onVoirHistorique = { enveloppeId, nomEnveloppe ->
                onVoirHistoriqueEnveloppe(enveloppeId, nomEnveloppe)
                showClavierEnveloppe = false
                enveloppeSelectionnee = null
            },
            estFavori = enveloppeClavier.id in uiState.enveloppesFavorites,
            onBasculerFavori = { viewModel.basculerFavori(enveloppeClavier.id) }
        )
    }

    // A2 : répartition vers plusieurs enveloppes
    if (showRepartition) {
        RepartitionMultipleDialog(
            comptes = comptesDisponibles,
            categories = uiState.categoriesEnveloppes,
            enCours = repartitionEnCours,
            erreur = repartitionErreur,
            onConfirmer = { compteId, montants ->
                if (uiState.isLoading || uiState.erreur != null) {
                    repartitionErreur = "Le budget doit être chargé complètement avant la répartition."
                } else {
                    repartitionErreur = null
                    repartitionEnCours = true
                    viewModel.assignerAPlusieursEnveloppes(compteId, montants)
                }
            },
            onFermer = {
                showRepartition = false
                repartitionErreur = null
            }
        )
    }

    // A7 : prévisions de financement, seulement sur demande
    if (showPrevisions) {
        PrevisionsDialog(
            categories = uiState.categoriesEnveloppes,
            moisSelectionne = moisSelectionne,
            onFermer = { showPrevisions = false }
        )
    }

    // 🚨 AFFICHAGE DES ERREURS - en dialogue pour rester visible au-dessus du clavier (C3)
    uiState.erreur?.let { erreur ->
        val estProvenance = VirementErrorMessages.estErreurProvenance(erreur)
        AlertDialog(
            onDismissRequest = { viewModel.effacerErreur() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (estProvenance) VirementErrorMessages.obtenirTitreDialogue(erreur) else "Erreur",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = erreur,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.effacerErreur() }
                ) {
                    Text("Compris")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.error,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BudgetScreenPreview() {
    // Interface simplifiée pour le preview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            text = "Budget Preview",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
