package com.xburnsx.toutiebudget.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.ui.settings.SyncJobViewModel
import com.xburnsx.toutiebudget.workers.SyncWorkManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncJobScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val syncJobViewModel = remember { com.xburnsx.toutiebudget.di.AppModule.provideSyncJobViewModel() }
    val syncJobs by syncJobViewModel.syncJobs.collectAsState()
    val isLoading by syncJobViewModel.isLoading.collectAsState()
    val error by syncJobViewModel.error.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // SJ20 : état du worker observé (Flow WorkManager), sans .get() bloquant ni sondage
    val infosWorker by remember { SyncWorkManager.etatSynchronisation(context) }.collectAsState(initial = emptyList())
    val isSyncRunning = infosWorker.any { it.state == androidx.work.WorkInfo.State.RUNNING }
    val comparaison by syncJobViewModel.comparaison.collectAsState()
    val message by syncJobViewModel.message.collectAsState()
    val enLigne = remember(syncJobs, isSyncRunning) { com.xburnsx.toutiebudget.utils.DiagnosticDonnees.estEnLigne(context) }
    
    // 🆕 État de filtrage - par défaut afficher seulement les tâches en attente
    var selectedFilter by remember { mutableStateOf("PENDING") }
    
    // 🆕 Liste filtrée des SyncJob
    val filteredSyncJobs = remember(syncJobs, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> syncJobs.filter { it.status == "PENDING" || it.status == "IN_PROGRESS" }
            "COMPLETED" -> syncJobs.filter { it.status == "COMPLETED" }
            "FAILED" -> syncJobs.filter { it.status == "FAILED" }
            else -> syncJobs // "ALL" - toutes les tâches
        }
    }
    
    // Afficher les erreurs et les résultats d'action
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            snackbarHostState.showSnackbar(message = errorMessage)
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); syncJobViewModel.effacerMessage() }
    }
    
    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Liste de tâches", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Retour", 
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { syncJobViewModel.loadSyncJobs() }) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "Actualiser", 
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { syncJobViewModel.clearCompletedJobs() }) {
                        Icon(
                            Icons.Default.Clear, 
                            contentDescription = "Vider terminées", 
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F0F),
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { 
                Spacer(modifier = Modifier.height(8.dp)) 
                
                // En-tête avec statistiques
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Statistiques",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilterButton(
                                title = "Total",
                                value = syncJobs.size.toString(),
                                color = Color(0xFF2196F3),
                                isSelected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" },
                                modifier = Modifier.weight(1f)
                            )
                            FilterButton(
                                title = "En attente",
                                value = syncJobs.count { it.status == "PENDING" }.toString(),
                                color = Color(0xFFFFA500),
                                isSelected = selectedFilter == "PENDING",
                                onClick = { selectedFilter = "PENDING" },
                                modifier = Modifier.weight(1f)
                            )
                            FilterButton(
                                title = "Terminées",
                                value = syncJobs.count { it.status == "COMPLETED" }.toString(),
                                color = Color(0xFF4CAF50),
                                isSelected = selectedFilter == "COMPLETED",
                                onClick = { selectedFilter = "COMPLETED" },
                                modifier = Modifier.weight(1f)
                            )
                            FilterButton(
                                title = "Échouées",
                                value = syncJobs.count { it.status == "FAILED" }.toString(),
                                color = Color(0xFFF44336),
                                isSelected = selectedFilter == "FAILED",
                                onClick = { selectedFilter = "FAILED" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // SJ17 : statut véridique — hors ligne, en cours, bloqué, partiel, en attente ou à jour
                        val bloquees = syncJobs.count { it.status == "FAILED" && it.bloque }
                        val aReessayer = syncJobs.count { it.status == "FAILED" && !it.bloque }
                        val enAttente = syncJobs.count { it.status == "PENDING" || it.status == "IN_PROGRESS" }
                        val (statut, couleurStatut) = when {
                            isSyncRunning -> "Synchronisation en cours…" to Color(0xFF2196F3)
                            bloquees > 0 -> "Bloquée : $bloquees tâche(s) demandent ton intervention" to Color(0xFFF44336)
                            !enLigne && (enAttente + aReessayer) > 0 -> "Hors ligne : ${enAttente + aReessayer} tâche(s) partiront au retour d'Internet" to Color(0xFFFFA500)
                            aReessayer > 0 -> "Partielle : $aReessayer tâche(s) seront réessayées automatiquement" to Color(0xFFFFA500)
                            enAttente > 0 -> "$enAttente tâche(s) en attente d'envoi" to Color(0xFFFFA500)
                            !enLigne -> "Hors ligne — rien en attente" to Color.Gray
                            else -> "À jour : tout est sur le serveur" to Color(0xFF4CAF50)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = couleurStatut, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = statut, color = couleurStatut, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        // SJA3 : diagnostic de progression, sans aucune donnée financière
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Dernier envoi réussi : " + com.xburnsx.toutiebudget.utils.DiagnosticDonnees.formater(
                                com.xburnsx.toutiebudget.utils.DiagnosticDonnees.dernierEchange(context)
                            ),
                            color = Color.Gray, fontSize = 12.sp
                        )
                        Text(
                            "En attente : $enAttente • à réessayer : $aReessayer • bloquées : $bloquees",
                            color = Color.Gray, fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // SJA1 : n'annule jamais un envoi en cours
                            OutlinedButton(onClick = { syncJobViewModel.synchroniserMaintenant(context) }) {
                                Text("Synchroniser maintenant", color = Color.White)
                            }
                            OutlinedButton(onClick = { syncJobViewModel.comparerAvecServeur(context) }) {
                                Text("Comparer avec le serveur", color = Color.White)
                            }
                        }
                        // SJA5 : comparaison Room ↔ PocketBase, lecture seule
                        comparaison?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (filteredSyncJobs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                            text = when (selectedFilter) {
                                "PENDING" -> "Aucune tâche en attente"
                                "COMPLETED" -> "Aucune tâche terminée"
                                "FAILED" -> "Aucune tâche échouée"
                                else -> "Aucune tâche de synchronisation"
                            },
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when (selectedFilter) {
                                "PENDING" -> "Les tâches en attente apparaîtront ici"
                                "COMPLETED" -> "Les tâches terminées apparaîtront ici"
                                "FAILED" -> "Les tâches échouées apparaîtront ici"
                                else -> "Les tâches apparaîtront ici quand vous modifierez des données"
                            },
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        }
                    }
                }
            } else {
                items(filteredSyncJobs, key = { it.id }) { syncJob ->
                    SyncJobCard(
                        syncJob = syncJob,
                        syncJobViewModel = syncJobViewModel
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun FilterButton(
    title: String,
    value: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.3f) else Color(0xFF2A2A2A)
        ),
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) color else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = if (isSelected) color else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SyncJobCard(
    syncJob: SyncJob,
    syncJobViewModel: SyncJobViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${syncJobViewModel.getActionText(syncJob.action)} ${syncJobViewModel.getTypeText(syncJob.type)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ID: ${syncJob.id}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                // Badge de statut
                Box(
                    modifier = Modifier
                        .background(
                            color = syncJobViewModel.getStatusColor(syncJob.status),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = syncJobViewModel.getStatusText(syncJob.status),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Créé le: ${syncJobViewModel.formatDate(syncJob.createdAt)}",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            // Diagnostic de la tâche, jamais son contenu (données financières) (SJ11, SJ19)
            if (syncJob.tentatives > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Essais : ${syncJob.tentatives} — dernier le ${syncJobViewModel.formatDate(syncJob.derniereTentative)}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            syncJob.derniereErreur?.let { erreur ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (syncJob.bloque) {
                        "Bloquée : $erreur. Corrige la cause puis appuie sur Réessayer."
                    } else {
                        "Erreur : $erreur. Nouvel essai automatique."
                    },
                    color = if (syncJob.bloque) Color(0xFFF44336) else Color(0xFFFFA500),
                    fontSize = 12.sp
                )
            }
            if (syncJob.status == "FAILED") {
                val context = LocalContext.current
                Spacer(modifier = Modifier.height(6.dp))
                if (syncJob.derniereErreur?.startsWith("Conflit") == true) {
                    Text("Ce changement a été fait ici pendant qu'une autre version arrivait sur le serveur. Choisis laquelle garder :",
                        color = Color.LightGray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { syncJobViewModel.utiliserMaVersion(context, syncJob) }) { Text("Utiliser ma version") }
                        TextButton(onClick = { syncJobViewModel.garderVersionServeur(context, syncJob) }) { Text("Garder celle du serveur") }
                    }
                } else {
                    TextButton(onClick = { syncJobViewModel.retrySingleFailedJob(syncJob.id) }) {
                        Text(if (syncJob.bloque) "Réessayer (une fois la cause corrigée)" else "Réessayer maintenant")
                    }
                }
            }
        }
    }
}
