package com.xburnsx.toutiebudget.ui.settings

/*
================================================================================
Fichier : ui/settings/SettingsScreen.kt
Projet  : ToutieBudget
Rôle    : Écran Paramètres (Material 3, thème sombre) avec design raffiné,
          sans créer d'autres fichiers. Tout est contenu ici.

Dépendances directes :
- com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService
- com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel
- com.xburnsx.toutiebudget.di.PocketBaseClient
- com.xburnsx.toutiebudget.utils.PreferencesManager
- com.xburnsx.toutiebudget.utils.notifications.RappelsWorker

Connexions :
- onCouleurThemeChange(...) : remonte le choix de thème à l'App
- onLogout()                : déclenche la déconnexion côté App
- onBack()                  : navigation retour
- onNavigateToArchives()    : navigation vers la page Archives
================================================================================
*/

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.ui.theme.ToutiePink
import com.xburnsx.toutiebudget.ui.theme.ToutieRed
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

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
                title = { Text(text = "Paramètres", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val ctx = context
                        val payload = mapOf(
                            "theme" to (if (couleurTheme == CouleurTheme.RED) "RED" else "PINK"),
                            "figer_pret_a_placer" to com.xburnsx.toutiebudget.utils.PreferencesManager.getFigerPretAPlacer(ctx),
                            "notifications_enabled" to com.xburnsx.toutiebudget.utils.PreferencesManager.getNotificationsEnabled(ctx),
                            "notif_obj_jours_avant" to com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifObjJoursAvant(ctx),
                            "notif_enveloppe_negatif" to com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifEnveloppeNegative(ctx)
                        )
                        android.util.Log.d("PrefsSyncUI", "SAVE click, payload=${payload}")
                        scope.launch {
                            try {
                                android.util.Log.d("PrefsSyncUI", "appel service → mettreAJourPreferencesUtilisateur")
                                com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService().mettreAJourPreferencesUtilisateur(payload)
                                snack.showSnackbar("Préférences enregistrées")
                            } catch (e: Exception) {
                                android.util.Log.e("PrefsSyncUI", "SAVE error", e)
                                snack.showSnackbar("Échec de l'enregistrement")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Enregistrer", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F0F),
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // — HÉRO (aperçu thème + sélection rapide)
            item {
                HeroHeader(
                    titre = "Personnalisez l'app",
                    sousTitre = "Thème, rappels et préférences",
                    accent = if (couleurTheme == CouleurTheme.RED) ToutieRed else ToutiePink,
                ) {
                    SelecteurCouleur(
                        couleur = ToutiePink,
                        nom = "Rose",
                        isSelected = couleurTheme == CouleurTheme.PINK,
                        onSelect = { onCouleurThemeChange(CouleurTheme.PINK) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelecteurCouleur(
                        couleur = ToutieRed,
                        nom = "Rouge",
                        isSelected = couleurTheme == CouleurTheme.RED,
                        onSelect = { onCouleurThemeChange(CouleurTheme.RED) }
                    )
                }
            }

            

            // Section Budget
            item { SectionHeader(titre = "Budget", icone = Icons.Default.Refresh) }
            item {
                val contextLocal = LocalContext.current
                var checked by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getFigerPretAPlacer(contextLocal)) }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Refresh,
                        titre = "Figer les 'Prêt à placer'",
                        description = "Toujours afficher les bandeaux en haut de la page Budget",
                        trailing = {
                            Switch(
                                checked = checked,
                                onCheckedChange = { value ->
                                    checked = value
                                    val vm = com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel()
                                    vm.setFigerPretAPlacer(value)
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setFigerPretAPlacer(contextLocal, value)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    )
                }
            }

            // Section Notifications
            item { SectionHeader(titre = "Notifications", icone = Icons.Default.Notifications) }
            item {
                val contextLocal = LocalContext.current
                var notifEnabled by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotificationsEnabled(contextLocal)) }
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (!granted) {
                        notifEnabled = false
                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(contextLocal, false)
                    }
                }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Notifications,
                        titre = "Activer les notifications",
                        description = "Recevoir des alertes et rappels",
                        trailing = {
                            Switch(
                                checked = notifEnabled,
                                onCheckedChange = { enabled ->
                                    notifEnabled = enabled
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(contextLocal, enabled)
                                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                                        val st = ContextCompat.checkSelfPermission(contextLocal, Manifest.permission.POST_NOTIFICATIONS)
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
            }

            item {
                val contextLocal = LocalContext.current
                var joursAvant by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifObjJoursAvant(contextLocal)) }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Refresh,
                        titre = "Rappel objectifs",
                        description = "Jours avant échéance",
                        trailing = {
                            Stepper(
                                valeur = joursAvant,
                                onMoins = {
                                    if (joursAvant > 0) {
                                        joursAvant -= 1
                                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(contextLocal, joursAvant)
                                    }
                                },
                                onPlus = {
                                    if (joursAvant < 30) {
                                        joursAvant += 1
                                        com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(contextLocal, joursAvant)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            item {
                val contextLocal = LocalContext.current
                var notifEnvNeg by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getNotifEnveloppeNegative(contextLocal)) }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Notifications,
                        titre = "Alerte enveloppe en négatif",
                        description = "Notification quand une enveloppe est négative",
                        trailing = {
                            Switch(
                                checked = notifEnvNeg,
                                onCheckedChange = {
                                    notifEnvNeg = it
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifEnveloppeNegative(contextLocal, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    )
                }
            }

            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Notifications,
                        titre = "Tester les notifications",
                        description = "Envoyer une notification de test",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                val st = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                if (st != PackageManager.PERMISSION_GRANTED) {
                                    scope.launch { snack.showSnackbar("Autorisez d'abord les notifications") }
                                    return@RowParam
                                }
                            }
                            val req = OneTimeWorkRequestBuilder<com.xburnsx.toutiebudget.utils.notifications.RappelsWorker>().build()
                            WorkManager.getInstance(context).enqueue(req)
                            scope.launch { snack.showSnackbar("Notification de test envoyée") }
                        },
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }

            // Section Données
            item { SectionHeader(titre = "Données", icone = Icons.Default.Archive) }
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Archive,
                        titre = "Éléments archivés",
                        description = "Voir et gérer les éléments archivés",
                        onClick = onNavigateToArchives,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }
            item {
                SettingsCard(backgroundColor = Color(0xFF2A1F1F)) {
                    RowParam(
                        icone = Icons.Default.Delete,
                        titre = "Réinitialiser les données",
                        description = "Supprimer toutes les données de l'application",
                        couleurTexte = Color.Red,
                        onClick = { showResetDialog = true },
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Red) }
                    )
                }
            }

            // Section À propos
            item { SectionHeader(titre = "À propos", icone = Icons.Default.Info) }
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Info,
                        titre = "Version de l'application",
                        description = "ToutieBudget v1.0.0"
                    )
                }
            }

            // Déconnexion
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(backgroundColor = Color(0xFF2A1F1F)) {
                    RowParam(
                        icone = Icons.AutoMirrored.Filled.Logout,
                        titre = "Se déconnecter",
                        description = "Fermer la session actuelle",
                        couleurTexte = Color.Red,
                        onClick = { showLogoutDialog = true },
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Red) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Dialogs — Déconnexion
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(text = "Déconnexion", fontWeight = FontWeight.Bold) },
            text = { Text("Êtes-vous sûr de vouloir vous déconnecter ?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    PocketBaseClient.deconnecter(context)
                    onLogout()
                }) { Text("Déconnexion", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Annuler") } },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }

    // Dialogs — Réinitialisation (double confirmation)
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Réinitialiser les données", fontWeight = FontWeight.Bold) },
            text = { Text("Cette action supprimera toutes les données de l'application. Voulez-vous continuer ?") },
            confirmButton = { TextButton(onClick = { showResetDialog = false; showResetConfirmDialog = true }) { Text("Réinitialiser", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Annuler") } },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(text = "Confirmation de réinitialisation", fontWeight = FontWeight.Bold) },
            text = { if (isResetting) Text("Réinitialisation en cours...") else Text("Êtes-vous sûr de vouloir réinitialiser les données ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isResetting) {
                            isResetting = true
                            resetError = null
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    realtimeSyncService.supprimerToutesLesDonnees().getOrThrow()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        showResetConfirmDialog = false
                                        isResetting = false
                                        PocketBaseClient.deconnecter(context)
                                        onLogout()
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
                    if (isResetting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Red) else Text("Oui, réinitialiser", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmDialog = false }) { Text("Annuler") } },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }

    if (resetError != null) {
        LaunchedEffect(resetError) {
            kotlinx.coroutines.delay(2500)
            resetError = null
        }
        AlertDialog(
            onDismissRequest = { resetError = null },
            title = { Text(text = "Erreur", fontWeight = FontWeight.Bold) },
            text = { Text(resetError ?: "Une erreur inconnue est survenue.") },
            confirmButton = { TextButton(onClick = { resetError = null }) { Text("OK") } },
            containerColor = Color(0xFF2C2C2E),
            textContentColor = Color.White
        )
    }
}

// ==============================
// Composants UI (dans le même fichier)
// ==============================

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
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text = titre, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsCard(
    backgroundColor: Color = Color(0xFF1C1C1E),
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { content() }
}

@Composable
fun RowParam(
    icone: ImageVector,
    titre: String,
    description: String,
    couleurTexte: Color = Color.White,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icone, contentDescription = null, tint = if (couleurTexte == Color.Red) Color.Red else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titre, color = couleurTexte, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = description, color = Color.Gray, fontSize = 14.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun SelecteurCouleur(
    couleur: Color,
    nom: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.08f else 1.0f, label = "scaleColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect() }) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(couleur)
                .border(width = if (isSelected) 2.dp else 0.dp, color = Color.White, shape = CircleShape)
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
        Text(
            text = nom,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun Stepper(
    valeur: Int,
    onMoins: () -> Unit,
    onPlus: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMoins, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White) }
        Text("${valeur} j", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
    }
}

@Composable
fun HeroHeader(
    titre: String,
    sousTitre: String,
    accent: Color,
    contenuRight: @Composable () -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    val bg = Brush.linearGradient(listOf(accent.copy(0.45f), accent.copy(0.12f)))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(titre, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(sousTitre, color = Color(0xFFBDBDBD), fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { contenuRight() }
        }
    }
}