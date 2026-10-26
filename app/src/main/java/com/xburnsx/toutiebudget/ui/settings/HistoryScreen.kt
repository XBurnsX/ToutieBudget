package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.di.AppModule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Journal des allocations et ajustements. Il est volontairement distinct de l'historique des transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var compteSelectionne by remember { mutableStateOf<Compte?>(null) }
    var comptesCheques by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var historiqueAllocations by remember { mutableStateOf<List<HistoriqueAllocation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var historiqueEnChargement by remember { mutableStateOf(false) }
    var nouvelEssai by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val comptesViewModel = remember { AppModule.provideComptesViewModel() }
    
    // Charger les comptes au démarrage
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            comptesViewModel.uiState.collect { uiState ->
                // HG8/HG9 : permettre les historiques des autres types de comptes
                // et des comptes archivés, sans les réactiver.
                val tousComptes = uiState.comptesGroupes.values.flatten() + uiState.comptesArchives
                comptesCheques = tousComptes.distinctBy { it.id }
                isLoading = false // Mettre à false dès qu'on a les comptes
            }
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }
    
    // Charger l'historique quand un compte est sélectionné
    LaunchedEffect(compteSelectionne?.id, nouvelEssai) {
        historiqueAllocations = emptyList()
        error = null
        historiqueEnChargement = compteSelectionne != null
        if (compteSelectionne != null) {
            try {
                val historiqueRepository = AppModule.provideHistoriqueAllocationRepository()
                
                historiqueRepository.getHistoriqueByCompte(compteSelectionne!!.id).collect { historique ->
                    historiqueAllocations = historique
                    historiqueEnChargement = false
                }
            } catch (e: Exception) {
                error = e.message ?: "Chargement du journal impossible"
                historiqueEnChargement = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal des allocations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Dropdown pour sélectionner le compte
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Sélectionner un compte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = compteSelectionne?.nom ?: "Aucun compte sélectionné",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (comptesCheques.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Aucun compte disponible") },
                                    onClick = {
                                        expanded = false
                                    }
                                )
                            } else {
                                comptesCheques.forEach { compte ->
                                    DropdownMenuItem(
                                        text = { Text(compte.nom) },
                                        onClick = {
                                            compteSelectionne = compte
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Affichage de l'historique
            if (isLoading || historiqueEnChargement) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Erreur: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { nouvelEssai++ }) {
                        Text("Réessayer")
                    }
                }
            } else if (compteSelectionne != null) {
                // Afficher l'historique des allocations
                if (historiqueAllocations.isNotEmpty()) {
                    Text(
                        text = "Historique des allocations (${historiqueAllocations.size} entrées)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = historiqueAllocations.sortedWith(
                                compareByDescending<HistoriqueAllocation> { lireDateHistoriqueUtc(it.dateAction)?.time ?: Long.MIN_VALUE }
                                    .thenByDescending { it.id }
                            ),
                            key = { it.id }
                        ) { historique ->
                            HistoriqueAllocationCard(historique = historique)
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aucun historique trouvé pour ce compte",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sélectionnez un compte pour voir son historique",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Card pour afficher un historique d'allocation
 */
@Composable
fun HistoriqueAllocationCard(historique: HistoriqueAllocation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // En-tête avec description et date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = historique.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = afficherDateHistoriqueLocale(historique.dateAction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Montant avec icône
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (historique.montant > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (historique.montant > 0) Color.Green else Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (historique.montant > 0) "+" else ""}${MoneyFormatter.formatAmount(historique.montant)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (historique.montant > 0) Color.Green else Color.Red
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Détails des soldes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Solde du compte
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Solde",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MoneyFormatter.formatAmount(historique.soldeAvant)} → ${MoneyFormatter.formatAmount(historique.soldeApres)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Prêt à placer
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Prêt à placer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MoneyFormatter.formatAmount(historique.pretAPlacerAvant)} → ${MoneyFormatter.formatAmount(historique.pretAPlacerApres)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** HG11 : les événements sont stockés en UTC (sans suffixe horaire).
 * Lire explicitement en UTC avant d'afficher dans le fuseau de l'appareil.
 */
private fun lireDateHistoriqueUtc(valeur: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }.parse(valeur)
}.getOrNull()

private fun afficherDateHistoriqueLocale(valeur: String): String {
    val date = lireDateHistoriqueUtc(valeur) ?: return valeur
    return SimpleDateFormat("d MMM yyyy HH:mm", Locale.FRENCH).format(date)
}
