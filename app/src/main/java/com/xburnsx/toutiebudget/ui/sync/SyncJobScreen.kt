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
    
    // État de la synchronisation
    var isSyncRunning by remember { mutableStateOf(false) }
    var lastSyncStatus by remember { mutableStateOf<String?>(null) }
    
    // 🆕 État de filtrage - par défaut afficher seulement les tâches en attente
    var selectedFilter by remember { mutableStateOf("PENDING") }
    
    // 🆕 Liste filtrée des SyncJob
    val filteredSyncJobs = remember(syncJobs, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> syncJobs.filter { it.status == "PENDING" }
            "COMPLETED" -> syncJobs.filter { it.status == "COMPLETED" }
            "FAILED" -> syncJobs.filter { it.status == "FAILED" }
            else -> syncJobs // "ALL" - toutes les tâches
        }
    }
    
    // Vérifier le statut de la synchronisation toutes les 2 secondes
    LaunchedEffect(Unit) {
        while (true) {
            isSyncRunning = SyncWorkManager.estSynchronisationEnCours(context)
            lastSyncStatus = SyncWorkManager.getStatutDerniereSynchronisation(context)?.name
            delay(2000)
        }
    }
    
    // 🚀 ÉCOUTEUR AUTOMATIQUE : Détecter les nouveaux SyncJob et déclencher la synchronisation immédiatement
    LaunchedEffect(syncJobs) {
        val pendingJobs = syncJobs.filter { it.status == "PENDING" }
        if (pendingJobs.isNotEmpty()) {
            // 🚨 ${pendingJobs.size} SyncJob en attente détectés - DÉCLENCHEMENT IMMÉDIAT de la synchronisation
            
            // DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION
            try {
                com.xburnsx.toutiebudget.workers.SyncWorkManager.demarrerSynchronisation(context)
                // ✅ Synchronisation déclenchée avec succès
            } catch (e: Exception) {
                // ❌ Erreur lors du déclenchement de la synchronisation
            }
        }
    }
    
    // Afficher les erreurs
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            snackbarHostState.showSnackbar(message = errorMessage)
        }
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
                        
                        // Indicateur de statut de synchronisation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSyncRunning) Icons.Default.Sync else Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (isSyncRunning) Color(0xFFFFA500) else Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSyncRunning) "Synchronisation en cours..." else "Prêt à synchroniser",
                                color = if (isSyncRunning) Color(0xFFFFA500) else Color(0xFF4CAF50),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Statut de la dernière synchronisation
                        lastSyncStatus?.let { status ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dernière sync: $status",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
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
                items(filteredSyncJobs) { syncJob ->
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
            
            if (syncJob.dataJson.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Données: ${syncJob.dataJson.take(100)}${if (syncJob.dataJson.length > 100) "..." else ""}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
