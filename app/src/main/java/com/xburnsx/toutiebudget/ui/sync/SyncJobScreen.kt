package com.xburnsx.toutiebudget.ui.sync

import androidx.compose.foundation.background
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
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.ui.settings.SyncJobViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncJobScreen(
    onBack: () -> Unit
) {
    val syncJobViewModel = remember { com.xburnsx.toutiebudget.di.AppModule.provideSyncJobViewModel() }
    val syncJobs by syncJobViewModel.syncJobs.collectAsState()
    val isLoading by syncJobViewModel.isLoading.collectAsState()
    val error by syncJobViewModel.error.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
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
                            StatCard(
                                title = "Total",
                                value = syncJobs.size.toString(),
                                color = Color(0xFF2196F3),
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "En attente",
                                value = syncJobs.count { it.status == "PENDING" }.toString(),
                                color = Color(0xFFFFA500),
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Terminées",
                                value = syncJobs.count { it.status == "COMPLETED" }.toString(),
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Échouées",
                                value = syncJobs.count { it.status == "FAILED" }.toString(),
                                color = Color(0xFFF44336),
                                modifier = Modifier.weight(1f)
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
            } else if (syncJobs.isEmpty()) {
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
                                text = "Aucune tâche de synchronisation",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Les tâches apparaîtront ici quand vous modifierez des données",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(syncJobs) { syncJob ->
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
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = modifier.padding(horizontal = 4.dp)
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
                color = Color.Gray,
                fontSize = 12.sp
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
