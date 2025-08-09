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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
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
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    couleurTheme: CouleurTheme = CouleurTheme.PINK,
    onCouleurThemeChange: (CouleurTheme) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onNavigateToArchives: () -> Unit = {}
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
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        text = "Paramètres",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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
                    containerColor = Color(0xFF0F0F0F),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Section Apparence
            item {
                SectionHeader(titre = "Apparence", icone = Icons.Default.Palette)
            }
            
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Couleur du thème",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Personnalisez l'apparence",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelecteurCouleur(
                                couleur = ToutiePink,
                                nom = "Rose",
                                isSelected = couleurTheme == CouleurTheme.PINK
                            ) { onCouleurThemeChange(CouleurTheme.PINK) }
                            SelecteurCouleur(
                                couleur = ToutieRed,
                                nom = "Rouge",
                                isSelected = couleurTheme == CouleurTheme.RED
                            ) { onCouleurThemeChange(CouleurTheme.RED) }
                        }
                    }
                }
            }

            // Section Budget
            item {
                SectionHeader(titre = "Budget", icone = Icons.Default.Lock)
            }

            item {
                var checked by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getFigerPretAPlacer(context)) }
                SettingsOptionCard(
                    titre = "Figer les 'Prêt à placer'",
                    description = "Toujours afficher les bandeaux en haut de la page Budget",
                    icone = Icons.Default.Lock,
                    trailing = {
                        Switch(
                            checked = checked,
                            onCheckedChange = { value ->
                                checked = value
                                val vm = com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel()
                                vm.setFigerPretAPlacer(value)
                                com.xburnsx.toutiebudget.utils.PreferencesManager.setFigerPretAPlacer(context, value)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            // Section Notifications
            item {
                SectionHeader(titre = "Notifications", icone = Icons.Default.Notifications)
            }

            item {
                var notifEnabled by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotificationsEnabled(context)) }
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (!granted) {
                        notifEnabled = false
                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(context, false)
                    }
                }
                
                SettingsOptionCard(
                    titre = "Activer les notifications",
                    description = "Recevoir des alertes et rappels",
                    icone = Icons.Default.Notifications,
                    trailing = {
                        Switch(
                            checked = notifEnabled,
                            onCheckedChange = { enabled ->
                                notifEnabled = enabled
                                com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(context, enabled)
                                if (enabled && Build.VERSION.SDK_INT >= 33) {
                                    val st = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    if (st != PackageManager.PERMISSION_GRANTED) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            item {
                var joursAvant by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifObjJoursAvant(context)) }
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Rappel objectifs",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Jours avant échéance",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (joursAvant > 0) {
                                        joursAvant -= 1
                                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(context, joursAvant)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White)
                            }
                            Text(
                                "$joursAvant j",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (joursAvant < 30) {
                                        joursAvant += 1
                                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(context, joursAvant)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            item {
                var notifEnvNeg by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifEnveloppeNegative(context)) }
                SettingsOptionCard(
                    titre = "Alerte enveloppe en négatif",
                    description = "Notification quand une enveloppe est négative",
                    icone = Icons.Default.Notifications,
                    trailing = {
                        Switch(
                            checked = notifEnvNeg,
                            onCheckedChange = {
                                notifEnvNeg = it
                                com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifEnveloppeNegative(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            item {
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                
                SettingsOptionCard(
                    titre = "Tester les notifications",
                    description = "Envoyer une notification de test",
                    icone = Icons.Default.Notifications,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val st = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            if (st != PackageManager.PERMISSION_GRANTED) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@SettingsOptionCard
                            }
                        }
                        val req = androidx.work.OneTimeWorkRequestBuilder<com.xburnsx.toutiebudget.utils.notifications.RappelsWorker>().build()
                        androidx.work.WorkManager.getInstance(context).enqueue(req)
                    },
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                )
            }

            // Section Données
            item {
                SectionHeader(titre = "Données", icone = Icons.Default.Archive)
            }

            item {
                SettingsOptionCard(
                    titre = "Éléments archivés",
                    description = "Voir et gérer les éléments archivés",
                    icone = Icons.Default.Archive,
                    onClick = onNavigateToArchives,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                )
            }

            item {
                SettingsOptionCard(
                    titre = "Réinitialiser les données",
                    description = "Supprimer toutes les données de l'application",
                    icone = Icons.Default.Delete,
                    couleurTexte = Color.Red,
                    onClick = { showResetDialog = true },
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Red
                        )
                    }
                )
            }

            // Section À propos
            item {
                SectionHeader(titre = "À propos", icone = Icons.Default.Info)
            }

            item {
                SettingsOptionCard(
                    titre = "Version de l'application",
                    description = "ToutieBudget v1.0.0",
                    icone = Icons.Default.Info,
                    onClick = null
                )
            }

            // Déconnexion
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsCard(
                    backgroundColor = Color(0xFF2A1F1F)
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
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Red
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Dialogs (unchanged)
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
                    Text("Réinitialisation en cours...")
                } else {
                    Text("Êtes-vous sûr de vouloir réinitialiser les données ?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isResetting) {
                            isResetting = true
                            resetError = null

                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    realtimeSyncService.supprimerToutesLesDonnees().getOrThrow()

                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        showResetConfirmDialog = false
                                        isResetting = false
                                        PocketBaseClient.deconnecter(context)
                                        onLogout()
                                    }
                                } catch (e: Exception) {
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

    if (resetError != null) {
        LaunchedEffect(resetError) {
            kotlinx.coroutines.delay(3000)
            resetError = null
        }

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
fun SectionHeader(
    titre: String,
    icone: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
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
}

@Composable
fun SettingsCard(
    backgroundColor: Color = Color(0xFF1C1C1E),
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun SettingsOptionCard(
    titre: String,
    description: String,
    icone: ImageVector,
    couleurTexte: Color = Color.White,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    SettingsCard {
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
                tint = if (couleurTexte == Color.Red) Color.Red else MaterialTheme.colorScheme.primary,
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
            trailing?.invoke()
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
                .size(36.dp)
                .clip(CircleShape)
                .background(couleur)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
        Text(
            text = nom,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}