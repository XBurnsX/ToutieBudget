package com.xburnsx.toutiebudget.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.services.SauvegardeBudget
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.PreferencesManager
import com.xburnsx.toutiebudget.utils.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PR1 : trois actions distinctes, chacune avec ses conséquences propres. */
private enum class TypeEffacement(
    val titre: String,
    val resume: String,
    val details: List<String>,
    val bouton: String
) {
    APPAREIL(
        "Effacer les données de cet appareil",
        "Cet appareil seulement ; le serveur garde tout.",
        listOf(
            "Effacé : toutes les données locales (comptes, transactions, enveloppes, historique) de CET appareil.",
            "Effacé aussi : la file de synchronisation. Les changements faits hors ligne et pas encore envoyés seront PERDUS.",
            "Conservé : tout ce qui est déjà sur le serveur PocketBase ; ça revient à la prochaine connexion.",
            "La session est fermée."
        ),
        "Effacer cet appareil"
    ),
    NOUVEAU_BUDGET(
        "Commencer un nouveau budget vide",
        "Supprime le budget sur le serveur et ici ; garde ton compte et tes préférences.",
        listOf(
            "Supprimé DÉFINITIVEMENT sur le serveur et ici : comptes, cartes, dettes, placements, prêts, transactions, " +
                "enveloppes, catégories, allocations, tiers, alias et opérations bancaires importées.",
            "Conservé : ton compte de connexion, ton thème et tes réglages.",
            "Irréversible, sauf si tu as exporté une sauvegarde avant.",
            "Tu restes connecté et repars d'un budget vide."
        ),
        "Supprimer et repartir à zéro"
    ),
    DEFINITIF(
        "Supprimer définitivement toutes mes données",
        "Tout ce qui t'appartient sur le serveur et ici, préférences comprises.",
        listOf(
            "Supprimé DÉFINITIVEMENT sur le serveur PocketBase : tout le budget (comme ci-dessus) ET tes préférences.",
            "Supprimé ici : toutes les données locales et la file de synchronisation.",
            "Seules les fiches qui t'appartiennent sont touchées ; jamais celles d'un autre utilisateur.",
            "Irréversible, sauf si tu as exporté une sauvegarde avant. La session est fermée."
        ),
        "Supprimer définitivement"
    )
}

@Composable
fun SectionDonneesEtSauvegarde(
    onSessionFermee: () -> Unit,
    onNouveauBudget: () -> Unit,
    onPreferencesAffichageReinitialisees: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var effacement by remember { mutableStateOf<TypeEffacement?>(null) }
    var confirmationFinale by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    var resultat by remember { mutableStateOf<String?>(null) }
    var affichageAReinitialiser by remember { mutableStateOf(false) }

    // ----- PA3 : sauvegarde -----
    var demandeMotDePasseExport by remember { mutableStateOf(false) }
    var motDePasseExport by remember { mutableStateOf<CharArray?>(null) }
    var fichierRestauration by remember { mutableStateOf<ByteArray?>(null) }
    var contenuARestaurer by remember { mutableStateOf<SauvegardeBudget.Contenu?>(null) }
    var operationSauvegarde by remember { mutableStateOf(false) }

    val lanceurExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val motDePasse = motDePasseExport
        motDePasseExport = null
        if (uri == null || motDePasse == null) return@rememberLauncherForActivityResult
        operationSauvegarde = true
        scope.launch {
            resultat = withContext(Dispatchers.IO) {
                runCatching {
                    val (octets, contenu) = AppModule.provideSauvegardeBudget().exporter(motDePasse)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(octets) }
                        ?: throw IllegalStateException("Impossible d'écrire le fichier.")
                    // Test de restauration : relire le fichier écrit et le déchiffrer, sans rien modifier
                    val relu = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Impossible de relire le fichier.")
                    val verifie = SauvegardeBudget.lire(relu, motDePasse)
                    check(verifie.total == contenu.total) { "Le fichier relu ne correspond pas aux données." }
                    "Sauvegarde exportée et vérifiée (relue et déchiffrée) : ${contenu.resume()}. " +
                        "Garde bien ton mot de passe : sans lui, le fichier est illisible."
                }.getOrElse { "Échec de l'export : ${it.message}" }
            }
            motDePasse.fill(' ')
            operationSauvegarde = false
        }
    }
    val lanceurRestauration = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fichierRestauration = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (fichierRestauration == null) resultat = "Impossible de lire ce fichier."
        }
    }

    SettingsCard {
        Column {
            RowParam(
                icone = Icons.Default.Save,
                titre = "Exporter une sauvegarde protégée",
                description = "Fichier chiffré par mot de passe, vérifié après l'écriture. À faire avant tout effacement.",
                onClick = { if (!operationSauvegarde) demandeMotDePasseExport = true },
                trailing = {
                    if (operationSauvegarde) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            )
            RowParam(
                icone = Icons.Default.Refresh,
                titre = "Restaurer une sauvegarde",
                description = "Dans un budget vide du même compte seulement (aucun mélange ni doublon)",
                onClick = { if (!operationSauvegarde) lanceurRestauration.launch(arrayOf("application/json", "*/*")) },
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
            )
        }
    }

    // ----- PA4 : affichage seulement -----
    SettingsCard {
        RowParam(
            icone = Icons.Default.Refresh,
            titre = "Réinitialiser l'affichage",
            description = "Thème et options d'affichage par défaut ; aucune donnée de budget touchée",
            onClick = { affichageAReinitialiser = true },
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
        )
    }

    // ----- PR1 : trois effacements distincts -----
    TypeEffacement.entries.forEach { type ->
        val couleur = if (type == TypeEffacement.APPAREIL) Color(0xFFFFB74D) else Color.Red
        SettingsCard(backgroundColor = if (type == TypeEffacement.APPAREIL) Color(0xFF252525) else Color(0xFF2A1F1F)) {
            RowParam(
                icone = Icons.Default.Delete,
                titre = type.titre,
                description = type.resume,
                couleurTexte = couleur,
                onClick = { if (!enCours) effacement = type },
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = couleur) }
            )
        }
    }

    if (demandeMotDePasseExport) {
        DialogueMotDePasse(
            titre = "Mot de passe de la sauvegarde",
            confirmer = true,
            onAnnuler = { demandeMotDePasseExport = false },
            onValider = { mdp ->
                demandeMotDePasseExport = false
                motDePasseExport = mdp
                lanceurExport.launch("toutiebudget-${java.time.LocalDate.now()}.toutiebudget.json")
            }
        )
    }

    fichierRestauration?.let { octets ->
        DialogueMotDePasse(
            titre = "Mot de passe de cette sauvegarde",
            confirmer = false,
            onAnnuler = { fichierRestauration = null },
            onValider = { mdp ->
                fichierRestauration = null
                scope.launch {
                    val lu = withContext(Dispatchers.Default) { runCatching { SauvegardeBudget.lire(octets, mdp) } }
                    mdp.fill(' ')
                    lu.onSuccess { contenuARestaurer = it }.onFailure { resultat = it.message }
                }
            }
        )
    }

    contenuARestaurer?.let { contenu ->
        AlertDialog(
            onDismissRequest = { if (!operationSauvegarde) contenuARestaurer = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Créée le ${com.xburnsx.toutiebudget.utils.DiagnosticDonnees.formater(contenu.creeLe)}.")
                    Text("Contenu : ${contenu.resume()}.")
                    Text("Tout est recréé avec les mêmes identifiants puis envoyé au serveur. " +
                        "Refusé si le budget actuel n'est pas vide ou si la sauvegarde est d'un autre compte.",
                        fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(enabled = !operationSauvegarde, onClick = {
                    operationSauvegarde = true
                    scope.launch {
                        val restaure = withContext(Dispatchers.IO + NonCancellable) {
                            runCatching { AppModule.provideSauvegardeBudget().restaurer(contenu) }
                        }
                        operationSauvegarde = false
                        contenuARestaurer = null
                        restaure.onSuccess {
                            // Les écrans repartent des données restaurées
                            AppModule.terminerSession()
                            onNouveauBudget()
                        }.onFailure { resultat = "Restauration refusée : ${it.message}" }
                    }
                }) {
                    if (operationSauvegarde) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Restaurer")
                }
            },
            dismissButton = { TextButton(enabled = !operationSauvegarde, onClick = { contenuARestaurer = null }) { Text("Annuler") } }
        )
    }

    if (affichageAReinitialiser) {
        AlertDialog(
            onDismissRequest = { affichageAReinitialiser = false },
            title = { Text("Réinitialiser l'affichage ?") },
            text = { Text("Thème rose, bandeaux « Prêt à placer » non figés, comptes à zéro visibles, archives masquées dans le Budget, " +
                "groupes de comptes ouverts. Aucun compte, transaction ni montant n'est touché.") },
            confirmButton = {
                TextButton(onClick = {
                    affichageAReinitialiser = false
                    ThemePreferences.sauvegarderCouleurTheme(context, com.xburnsx.toutiebudget.ui.theme.CouleurTheme.PINK)
                    com.xburnsx.toutiebudget.utils.PreferencesSync.marquerModifiee(context, com.xburnsx.toutiebudget.utils.PreferencesSync.THEME)
                    PreferencesManager.setFigerPretAPlacer(context, false)
                    AppModule.provideBudgetViewModel().setFigerPretAPlacer(false)
                    PreferencesManager.setMasquerComptesAZero(context, false)
                    PreferencesManager.setShowArchived(context, false)
                    AppModule.provideBudgetViewModel().setShowArchived(false)
                    PreferencesManager.setGroupesComptesFermes(context, emptySet())
                    onPreferencesAffichageReinitialisees()
                    resultat = "Affichage remis par défaut. Tes données n'ont pas changé."
                }) { Text("Réinitialiser") }
            },
            dismissButton = { TextButton(onClick = { affichageAReinitialiser = false }) { Text("Annuler") } }
        )
    }

    // ----- PA6 : confirmation détaillée, PR17 : double confirmation et progression -----
    effacement?.let { type ->
        if (!confirmationFinale) {
            AlertDialog(
                onDismissRequest = { effacement = null },
                title = { Text(type.titre, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        type.details.forEach { Text("• $it", fontSize = 14.sp) }
                        if (type != TypeEffacement.APPAREIL) {
                            Text("Conseil : exporte d'abord une sauvegarde protégée (ci-dessus).",
                                color = Color(0xFFFFB74D), fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { confirmationFinale = true }) { Text("Continuer", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { effacement = null }) { Text("Annuler") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!enCours) { confirmationFinale = false; effacement = null } },
                title = { Text("Dernière confirmation", fontWeight = FontWeight.Bold) },
                text = {
                    if (enCours) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (type == TypeEffacement.APPAREIL) "Effacement de l'appareil…"
                                else "Suppression puis vérification sur le serveur… Ne ferme pas l'application.")
                        }
                    } else Text("${type.bouton} ? Cette action ne peut pas être annulée.")
                },
                confirmButton = {
                    TextButton(enabled = !enCours, onClick = {
                        enCours = true
                        scope.launch {
                            // PR17 : l'opération va au bout même si l'écran change
                            val message = withContext(Dispatchers.IO + NonCancellable) {
                                when (type) {
                                    TypeEffacement.APPAREIL -> runCatching {
                                        AppModule.effacerToutesLesDonneesLocales()
                                        null
                                    }.getOrElse { "Effacement de l'appareil impossible : ${it.message}" }
                                    else -> AppModule.provideRealtimeSyncService()
                                        .supprimerToutesLesDonnees(garderPreferences = type == TypeEffacement.NOUVEAU_BUDGET)
                                        .fold({ null }, { it.message ?: "Erreur inconnue." })
                                }
                            }
                            enCours = false
                            confirmationFinale = false
                            effacement = null
                            if (message != null) {
                                // PR5 : jamais de faux succès ; le message reste affiché jusqu'à « OK »
                                resultat = message
                            } else when (type) {
                                TypeEffacement.NOUVEAU_BUDGET -> {
                                    AppModule.terminerSession()
                                    onNouveauBudget()
                                }
                                else -> {
                                    PocketBaseClient.deconnecter(context)
                                    onSessionFermee()
                                }
                            }
                        }
                    }) { Text(type.bouton, color = Color.Red) }
                },
                dismissButton = {
                    TextButton(enabled = !enCours, onClick = { confirmationFinale = false; effacement = null }) { Text("Annuler") }
                }
            )
        }
    }

    // PR17 : résultat persistant jusqu'à ce que l'utilisateur le ferme (plus de disparition après 2,5 s)
    resultat?.let { message ->
        AlertDialog(
            onDismissRequest = { resultat = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { resultat = null }) { Text("OK") } }
        )
    }
}

@Composable
internal fun DialogueMotDePasse(
    titre: String,
    confirmer: Boolean,
    onAnnuler: () -> Unit,
    onValider: (CharArray) -> Unit
) {
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    val valide = mdp.length >= 8 && (!confirmer || mdp == mdp2)
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mdp, onValueChange = { mdp = it }, label = { Text("Mot de passe (8 caractères min.)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmer) {
                    OutlinedTextField(
                        value = mdp2, onValueChange = { mdp2 = it }, label = { Text("Confirmer le mot de passe") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        isError = mdp2.isNotEmpty() && mdp2 != mdp,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valide, onClick = { onValider(mdp.toCharArray()) }) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
