package com.xburnsx.toutiebudget.ui.dette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.ui.dette.composants.SectionCalculsAutomatiques
import com.xburnsx.toutiebudget.ui.dette.composants.SectionInformationsPrincipales
import com.xburnsx.toutiebudget.ui.dette.composants.SectionParametresAvances

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetteScreen(
    detteId: String,
    viewModel: DetteViewModel,
    onRetour: () -> Unit,
    onSauvegarder: (CompteDette) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(detteId) {
        viewModel.chargerDette(detteId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Paramètres de la dette",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Profil utilisateur */ }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profil",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.clearError() }) {
                        Text("Réessayer")
                    }
                }
            } else {
                uiState.dette?.let { dette ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section Informations principales
                        SectionInformationsPrincipales(
                            dette = dette,
                            onDetteChange = { nouvelleDette ->
                                viewModel.sauvegarderDette(nouvelleDette)
                            }
                        )
                        
                        // Section Paramètres avancés
                        SectionParametresAvances(
                            dette = dette,
                            onDetteChange = { nouvelleDette ->
                                viewModel.sauvegarderDette(nouvelleDette)
                            }
                        )
                        
                        // Section Calculs automatiques
                        SectionCalculsAutomatiques(dette = dette)
                        
                        // Bouton de sauvegarde
                        Button(
                            onClick = { onSauvegarder(dette) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Sauvegarder",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
} 