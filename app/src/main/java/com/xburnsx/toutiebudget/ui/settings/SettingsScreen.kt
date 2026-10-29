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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
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
    onNavigateToArchives: () -> Unit = {},
    onNavigateToSyncJobs: () -> Unit = {},
    onNavigateToDatabaseManager: () -> Unit = {},
    onNavigateToAliasBancaires: () -> Unit = {},
    onNavigateToOperationsBancaires: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    /** PR1 : repartir de l'écran de démarrage, toujours connecté (nouveau budget vide ou restauration). */
    onNouveauBudget: () -> Unit = onLogout
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPlanReset by remember { mutableStateOf(false) }

    if (showPlanReset) {
        PlanResetDialog(onFermer = { showPlanReset = false })
    }



    // PR18 : état réel de la synchronisation des préférences
    LaunchedEffect(Unit) { com.xburnsx.toutiebudget.utils.PreferencesSync.rafraichirStatut(context) }

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
                        scope.launch {
                            // PR7/PR18 : tout est déjà enregistré sur l'appareil ; ceci force l'envoi au serveur
                            listOf(
                                com.xburnsx.toutiebudget.utils.PreferencesSync.THEME,
                                com.xburnsx.toutiebudget.utils.PreferencesSync.FIGER,
                                com.xburnsx.toutiebudget.utils.PreferencesSync.NOTIFS,
                                com.xburnsx.toutiebudget.utils.PreferencesSync.JOURS_AVANT,
                                com.xburnsx.toutiebudget.utils.PreferencesSync.ENV_NEGATIF
                            ).forEach { com.xburnsx.toutiebudget.utils.PreferencesSync.marquerModifiee(context, it) }
                            val ok = com.xburnsx.toutiebudget.utils.PreferencesSync.envoyer(context)
                            snack.showSnackbar(
                                if (ok) "Préférences synchronisées avec le serveur"
                                else com.xburnsx.toutiebudget.utils.PreferencesSync.statut.value.etat.libelle
                            )
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
                        onSelect = {
                            onCouleurThemeChange(CouleurTheme.PINK)
                            com.xburnsx.toutiebudget.utils.PreferencesSync.marquerModifiee(context, com.xburnsx.toutiebudget.utils.PreferencesSync.THEME)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelecteurCouleur(
                        couleur = ToutieRed,
                        nom = "Rouge",
                        isSelected = couleurTheme == CouleurTheme.RED,
                        onSelect = {
                            onCouleurThemeChange(CouleurTheme.RED)
                            com.xburnsx.toutiebudget.utils.PreferencesSync.marquerModifiee(context, com.xburnsx.toutiebudget.utils.PreferencesSync.THEME)
                        }
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
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Refresh,
                        titre = "Refaire la répartition d'un mois",
                        description = "Remettre l'argent assigné dans « Prêt à placer », avec aperçu",
                        onClick = { showPlanReset = true }
                    )
                }
            }
            // CA4 : masquer les comptes à zéro dans la page Comptes
            item {
                val contextLocal = LocalContext.current
                var masquer by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getMasquerComptesAZero(contextLocal)) }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Refresh,
                        titre = "Masquer les comptes à zéro",
                        description = "Dans la page Comptes, sans les archiver (un compte avec du Prêt à placer reste visible)",
                        trailing = {
                            Switch(
                                checked = masquer,
                                onCheckedChange = { value ->
                                    masquer = value
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setMasquerComptesAZero(contextLocal, value)
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
            // VA6 : confirmation facultative des gros virements
            item {
                val contextLocal = LocalContext.current
                var confirmer by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getConfirmerGrosVirements(contextLocal)) }
                var seuil by remember { mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getSeuilGrosVirement(contextLocal)) }
                val seuils = listOf(100, 250, 500, 1000, 2500)
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Refresh,
                        titre = "Confirmer les gros virements",
                        description = if (confirmer) "À partir de $seuil $ — toucher pour changer le seuil" else "Désactivé",
                        onClick = if (confirmer) {
                            {
                                seuil = seuils[(seuils.indexOf(seuil) + 1) % seuils.size]
                                com.xburnsx.toutiebudget.utils.PreferencesManager.setSeuilGrosVirement(contextLocal, seuil)
                            }
                        } else null,
                        trailing = {
                            Switch(
                                checked = confirmer,
                                onCheckedChange = { value ->
                                    confirmer = value
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setConfirmerGrosVirements(contextLocal, value)
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
                        // PR15 : la préférence ne suffit pas, l'autorisation Android doit aussi être donnée
                        description = if (notifEnabled && !androidx.core.app.NotificationManagerCompat.from(contextLocal).areNotificationsEnabled())
                            "Bloquées par Android : touche ici pour ouvrir les réglages de notifications"
                        else "Recevoir des alertes et rappels",
                        onClick = if (notifEnabled && !androidx.core.app.NotificationManagerCompat.from(contextLocal).areNotificationsEnabled()) {
                            {
                                contextLocal.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, contextLocal.packageName)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else null,
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
                // ARA6 : facultatif, désactivé par défaut ; retour en arrière par « Réactiver » dans Archives
                val contextLocal = LocalContext.current
                var archivageAuto by remember {
                    mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager.getArchivageAutomatique(contextLocal))
                }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Archive,
                        titre = "Archiver automatiquement les comptes soldés",
                        description = "Solde à 0 $, sans Prêt à placer ni titre, et aucune transaction depuis 60 jours. " +
                            "Jamais une dette encore due. Réactivable dans Archives.",
                        trailing = {
                            Switch(
                                checked = archivageAuto,
                                onCheckedChange = { active ->
                                    archivageAuto = active
                                    com.xburnsx.toutiebudget.utils.PreferencesManager
                                        .setArchivageAutomatique(contextLocal, active)
                                }
                            )
                        }
                    )
                }
            }

            item {
                val contextLocal = LocalContext.current
                var rappelsDettes by remember {
                    mutableStateOf(com.xburnsx.toutiebudget.utils.PreferencesManager
                        .getNotifEcheanceDettes(contextLocal))
                }
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Notifications,
                        titre = "Rappels de paiement des dettes",
                        description = "Optionnel : seulement si une échéance connue reste à payer",
                        trailing = {
                            Switch(
                                checked = rappelsDettes,
                                onCheckedChange = { active ->
                                    rappelsDettes = active
                                    com.xburnsx.toutiebudget.utils.PreferencesManager
                                        .setNotifEcheanceDettes(contextLocal, active)
                                }
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
                            val req = OneTimeWorkRequestBuilder<com.xburnsx.toutiebudget.utils.notifications.RappelsWorker>()
                                .setInputData(androidx.work.workDataOf("notification_test" to true))
                                .build()
                            WorkManager.getInstance(context).enqueue(req)
                            scope.launch { snack.showSnackbar("Test de notification demandé") }
                        },
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }



            // Section Synchronisation
            item { SectionHeader(titre = "Synchronisation", icone = Icons.Default.Sync) }
            item { SettingsCard { EtatDonneesCarte() } }
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Sync,
                        titre = "Liste de tâches de synchronisation",
                        description = "Voir les tâches en attente de synchronisation",
                        onClick = onNavigateToSyncJobs,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }

            // BANQ-03 : boîte d'import séparée du budget, aucune écriture comptable.
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Sync,
                        titre = "Transactions à confirmer",
                        description = "Importer un CSV, consulter et traiter les opérations en attente",
                        onClick = onNavigateToOperationsBancaires,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }

            // BANQ-02 : gestion des règles d'alias ; aucun doublon de Tiers.
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Sync,
                        titre = "Alias bancaires",
                        description = "Corriger les noms reconnus, les règles et les comptes associés",
                        onClick = onNavigateToAliasBancaires,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }

            // Section Base de données
            item { SectionHeader(titre = "Base de données", icone = Icons.Default.Info) }
            item {
                SettingsCard {
                    RowParam(
                        icone = Icons.Default.Info,
                        titre = "Gestion de la base de données",
                        description = "Ajouter, modifier et supprimer des éléments de la base locale",
                        onClick = onNavigateToDatabaseManager,
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
                        titre = "Historique général",
                        description = "Transactions de tous les comptes et mouvements du budget, filtres, recherche et export CSV",
                        onClick = onNavigateToHistory,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    )
                }
            }
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
            // PR1/PA3/PA4/PA6 : sauvegarde, affichage et trois effacements distincts
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionDonneesEtSauvegarde(
                        onSessionFermee = onLogout,
                        onNouveauBudget = onNouveauBudget,
                        onPreferencesAffichageReinitialisees = { onCouleurThemeChange(CouleurTheme.PINK) }
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
                        description = "ToutieBudget v${com.xburnsx.toutiebudget.BuildConfig.VERSION_NAME} (build ${com.xburnsx.toutiebudget.BuildConfig.VERSION_CODE})\n" +
                            "Dernier échange réussi avec le serveur : " +
                            com.xburnsx.toutiebudget.utils.DiagnosticDonnees.formater(
                                com.xburnsx.toutiebudget.utils.DiagnosticDonnees.dernierEchange(context)
                            )
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
