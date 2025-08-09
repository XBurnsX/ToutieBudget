package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

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
    var showResetDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }

    // Service pour la réinitialisation
    val realtimeSyncService = remember { com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService() }

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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
            // Section Affichage Budget
            SectionParametres(
                titre = "Budget",
                icone = Icons.Default.Info
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Figer les 'Prêt à placer'",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Toujours afficher les bandeaux en haut de la page Budget",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }

                        var checked by remember { mutableStateOf(false) }
                        // Charger l'état actuel (simple, non persisté à l'écran)
                        LaunchedEffect(Unit) {
                            checked = com.xburnsx.toutiebudget.di.AppModule
                                .provideBudgetViewModel()
                                .uiState.value.figerPretAPlacer
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { value ->
                                checked = value
                                // Mettre à jour via le ViewModel Budget et préférences
                                val vm = com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel()
                                // Appel interne via event bus simple
                                vm.setFigerPretAPlacer(value)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
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
                            showResetDialog = true
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
                            Icons.AutoMirrored.Filled.Logout,
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

    // Dialog pour réinitialiser les données
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Réinitialiser les données",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Cette action supprimera toutes les données de l'application. Voulez-vous continuer ?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        showResetConfirmDialog = true
                    }
                ) {
                    Text("Réinitialiser", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text("Annuler")
                }
            },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }

    // Dialog de confirmation pour la réinitialisation
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Confirmation de réinitialisation",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isResetting) {
                    // Afficher un message de chargement pendant la réinitialisation
                    Text("Réinitialisation en cours...")
                } else {
                    Text("Êtes-vous sûr de vouloir réinitialiser les données ?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isResetting) {
                            // Lancer la réinitialisation des données
                            isResetting = true
                            resetError = null

                            // Exécuter la réinitialisation dans une coroutine
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    realtimeSyncService.supprimerToutesLesDonnees().getOrThrow()

                                    // Succès - fermer les dialogs et déconnecter l'utilisateur
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        showResetConfirmDialog = false
                                        isResetting = false
                                        // Déconnecter l'utilisateur après la réinitialisation
                                        PocketBaseClient.deconnecter(context)
                                        onLogout()
                                    }
                                } catch (e: Exception) {
                                    // Erreur - afficher le message d'erreur
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        isResetting = false
                                        resetError = "Erreur lors de la réinitialisation: ${e.message}"
                                        showResetConfirmDialog = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Red
                        )
                    } else {
                        Text("Oui, réinitialiser", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text("Annuler")
                }
            },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }

    // Afficher un message d'erreur si la réinitialisation échoue
    if (resetError != null) {
        LaunchedEffect(resetError) {
            // Afficher le message d'erreur pendant 3 secondes, puis le masquer
            kotlinx.coroutines.delay(3000)
            resetError = null
        }

        // Dialog pour afficher l'erreur
        AlertDialog(
            onDismissRequest = { resetError = null },
            title = {
                Text(
                    text = "Erreur",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(resetError ?: "Une erreur inconnue est survenue.")
            },
            confirmButton = {
                TextButton(
                    onClick = { resetError = null }
                ) {
                    Text("OK")
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
