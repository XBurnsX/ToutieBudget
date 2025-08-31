package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
    onRetour: () -> Unit,
    onVirementClick: (enveloppeId: String, montantNegatif: Double, mois: Date) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Filtrer uniquement les enveloppes avec solde négatif
    val enveloppesNegatives = uiState.categoriesEnveloppes
        .map { categorie ->
            val enveloppesFiltrees = categorie.enveloppes.filter { it.solde < -0.001 }
            if (enveloppesFiltrees.isNotEmpty()) {
                categorie.copy(enveloppes = enveloppesFiltrees)
            } else null
        }
        .filterNotNull()
    
    // État pour les catégories ouvertes/fermées
    var categoriesOuvertes by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Initialiser toutes les catégories comme ouvertes par défaut
    LaunchedEffect(enveloppesNegatives) {
        categoriesOuvertes = enveloppesNegatives.associate { it.nomCategorie to true }
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
    ) { paddingValues ->
        if (enveloppesNegatives.isEmpty()) {
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
                                .sumOf { it.solde }
                            Text(
                                text = "$totalEnveloppes enveloppe${if (totalEnveloppes > 1) "s" else ""} concernée${if (totalEnveloppes > 1) "s" else ""}",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Total des soldes négatifs : ${String.format("%.2f", totalMontantNegatif)} €",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onVirementClick(
                                                enveloppe.id,
                                                -enveloppe.solde, // Montant positif à transférer
                                                Date() // Mois actuel du système
                                            )
                                        }
                                ) {
                                    EnveloppeItem(enveloppe = enveloppe)
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
