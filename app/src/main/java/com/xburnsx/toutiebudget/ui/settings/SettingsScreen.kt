package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.ui.theme.ToutiePink
import com.xburnsx.toutiebudget.ui.theme.ToutieRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    couleurTheme: CouleurTheme = CouleurTheme.PINK,
    onCouleurThemeChange: (CouleurTheme) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Paramètres",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Section Apparence
                SectionParametres(
                    titre = "Apparence",
                    icone = Icons.Default.Palette
                ) {
                    // Sélecteur de couleur de thème
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2C2C2E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Couleur du thème",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Thème Rose
                                SelecteurCouleur(
                                    couleur = ToutiePink,
                                    nom = "Rose",
                                    isSelected = couleurTheme == CouleurTheme.PINK,
                                    onSelect = { onCouleurThemeChange(CouleurTheme.PINK) }
                                )

                                // Thème Rouge
                                SelecteurCouleur(
                                    couleur = ToutieRed,
                                    nom = "Rouge",
                                    isSelected = couleurTheme == CouleurTheme.RED,
                                    onSelect = { onCouleurThemeChange(CouleurTheme.RED) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Section Données
                SectionParametres(
                    titre = "Données",
                    icone = Icons.Default.Delete
                ) {
                    OptionParametre(
                        titre = "Réinitialiser les données",
                        description = "Supprimer toutes les données de l'application",
                        icone = Icons.Default.Delete,
                        couleurTexte = Color.Red,
                        onClick = {
                            // TODO: Implémenter réinitialisation
                        }
                    )
                }
            }

            item {
                // Section À propos
                SectionParametres(
                    titre = "À propos",
                    icone = Icons.Default.Info
                ) {
                    Column {
                        OptionParametre(
                            titre = "Version de l'application",
                            description = "ToutieBudget v1.0.0",
                            icone = Icons.Default.Info,
                            onClick = null // Non cliquable
                        )
                    }
                }
            }

            item {
                // Bouton Déconnexion (séparé)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Se déconnecter",
                            color = Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Dialog de confirmation pour logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Déconnexion",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Êtes-vous sûr de vouloir vous déconnecter ?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        PocketBaseClient.deconnecter(context)
                        onLogout()
                    }
                ) {
                    Text("Déconnexion", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Annuler")
                }
            },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }
}

@Composable
fun SectionParametres(
    titre: String,
    icone: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        // En-tête de section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = titre,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Contenu de la section
        content()
    }
}

@Composable
fun OptionParametre(
    titre: String,
    description: String,
    icone: ImageVector,
    couleurTexte: Color = Color.White,
    onClick: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = couleurTexte,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titre,
                    color = couleurTexte,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SelecteurCouleur(
    couleur: Color,
    nom: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onSelect() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(couleur)
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
        Text(
            text = nom,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
