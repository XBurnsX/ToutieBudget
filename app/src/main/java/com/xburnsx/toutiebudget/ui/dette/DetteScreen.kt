package com.xburnsx.toutiebudget.ui.dette

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.ChangementTaux
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.MoteurDette
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val fondCarte = Color(0xFF2C2C2E)

/**
 * « Gérer la dette » : informe, configure, projette. Les paiements réels restent dans Transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetteScreen(
    detteId: String,
    viewModel: DetteViewModel,
    onRetour: () -> Unit,
    onSauvegarder: (CompteDette) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmerQuitter by remember { mutableStateOf(false) }
    var echeancierOuvert by remember { mutableStateOf(false) }

    LaunchedEffect(detteId) { viewModel.chargerDette(detteId) }
    // Le ViewModel est partagé : on repart d'un état vide à chaque sortie de l'écran
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.reinitialiser() } }

    // DF1 : fermer seulement après une vraie sauvegarde
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved && uiState.dette?.id == detteId) uiState.dette?.let(onSauvegarder)
    }

    val quitter = { if (uiState.aDesModifications) confirmerQuitter = true else onRetour() }
    BackHandler(enabled = uiState.aDesModifications) { confirmerQuitter = true }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Gérer la dette", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = quitter) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.erreurChargement != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.erreurChargement!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.reessayer() }) { Text("Réessayer") } // DF13
                }
                uiState.dette != null -> {
                    val dette = uiState.dette!!
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SectionResume(dette, uiState)
                        SectionEcheance(dette, uiState)
                        SectionParametres(uiState.formulaire, viewModel::modifierFormulaire)
                        SectionProjection(dette, onVoirEcheancier = { echeancierOuvert = true })
                        SectionVersements(uiState)

                        Button(
                            onClick = { viewModel.sauvegarder() },
                            enabled = !uiState.isSaving && uiState.aDesModifications,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSaving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(20.dp).height(20.dp))
                            else Text("Sauvegarder", modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmerQuitter) {
        AlertDialog(
            onDismissRequest = { confirmerQuitter = false },
            title = { Text("Quitter sans sauvegarder ?") },
            text = { Text("Les changements non enregistrés seront perdus.") },
            confirmButton = { TextButton(onClick = { confirmerQuitter = false; onRetour() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { confirmerQuitter = false }) { Text("Rester") } }
        )
    }

    if (echeancierOuvert) {
        uiState.dette?.let { dette ->
            DialogueEcheancier(dette, onFermer = { echeancierOuvert = false })
        }
    }

    (uiState.messageErreur ?: uiState.messageInfo)?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::effacerMessages,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::effacerMessages) { Text("OK") } }
        )
    }
}

// ===== Sections =====

@Composable
private fun CarteSection(titre: String, contenu: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = fondCarte)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            contenu()
        }
    }
}

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color = Color.White, gras: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(libelle, color = Color.LightGray, modifier = Modifier.weight(1f))
        Text(valeur, color = couleur, fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Note(texte: String) {
    Text(texte, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
}

/** DA6 / DF7 : solde réel, versements réels et intérêts confirmés, chacun pour ce qu'il est. */
@Composable
private fun SectionResume(dette: CompteDette, uiState: DetteUiState) {
    val restant = MoteurDette.restant(dette)
    CarteSection(dette.nom) {
        Ligne("Solde restant (réel)", MoneyFormatter.formatAmount(restant), couleur = if (restant > 0) Color(0xFFEF4444) else Color(0xFF10B981), gras = true)
        if (dette.soldeDette > 0.005) Note("Solde créditeur : ce n'est pas une nouvelle dette.")
        when {
            dette.estArchive && restant < 0.01 -> Note("Remboursée et archivée.")
            dette.estArchive -> Note("Archivée sans être remboursée : le solde réel est conservé.")
        }
        Ligne("Montant total original", MoneyFormatter.formatAmount(dette.montantInitial))
        MoteurDette.situationContrat(dette)?.let { contrat ->
            Ligne("Fréquence", contrat.frequence.libelle)
            Ligne("Paiements déjà écoulés", "${contrat.paiementsEcoules} / ${contrat.nombrePaiements}")
            Ligne("Capital payé", MoneyFormatter.formatAmount(contrat.capitalPaye), couleur = Color(0xFF10B981))
            Ligne("Intérêts payés", MoneyFormatter.formatAmount(contrat.interetsPayes))
            Ligne("Total versé avant le suivi", MoneyFormatter.formatAmount(contrat.totalVerse))
        }
        val paiements = MoteurDette.paiementsReels(uiState.transactions)
        if (paiements.isNotEmpty()) {
            Ligne("Versements enregistrés dans l'app (${paiements.size})", MoneyFormatter.formatAmount(MoteurDette.totalVerse(uiState.transactions)))
        }
        MoteurDette.echeancesCouvertes(dette, uiState.transactions)?.let {
            Ligne("Équivalent de mensualités (selon montants)", "$it")
        }
    }
}

/** DA3 : prochain versement attendu et son état d'après les paiements enregistrés. */
@Composable
private fun SectionEcheance(dette: CompteDette, uiState: DetteUiState) {
    val etat = MoteurDette.etatProchaineEcheance(dette, uiState.transactions) ?: return
    val format = SimpleDateFormat("d MMMM yyyy", Locale.CANADA_FRENCH)
    CarteSection("Prochaine échéance") {
        Ligne("Date", format.format(etat.date))
        Ligne("Versement prévu", MoneyFormatter.formatAmount(etat.prevu))
        Ligne("Déjà versé pour cette échéance", MoneyFormatter.formatAmount(etat.verse))
        Ligne(
            "État",
            etat.statut.libelle,
            couleur = when (etat.statut) {
                MoteurDette.StatutEcheance.PAYEE -> Color(0xFF10B981)
                MoteurDette.StatutEcheance.PARTIELLE -> Color(0xFFFFB74D)
                MoteurDette.StatutEcheance.NON_PAYEE -> Color.White
            }
        )
        if (etat.reste > 0.005) Ligne("Encore exigible", MoneyFormatter.formatAmount(etat.reste), gras = true)
        Note("Payez depuis Transactions ; ToutieBudget n'envoie jamais de paiement lui-même.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionParametres(f: FormulaireDette, onChange: (FormulaireDette.() -> FormulaireDette) -> Unit) {
    var choisirDate by remember { mutableStateOf(false) }
    val situation = remember(
        f.montantInitial,
        f.dateDebut,
        f.dureeMois,
        f.versementPrevu,
        f.taux,
        f.frequencePaiement
    ) {
        val debut = MoteurCarteCredit.lireDate(f.dateDebut)
        val duree = f.dureeMois.toIntOrNull()
        val versement = f.versementPrevu.replace(',', '.').toDoubleOrNull()
        val taux = f.taux.replace(',', '.').toDoubleOrNull()
        val montantTotal = f.montantInitial.replace(',', '.').toDoubleOrNull()
        if (debut != null && duree != null && versement != null && taux != null && montantTotal != null) {
            MoteurDette.situationContrat(
                debut, duree, versement, taux,
                principalInitial = montantTotal,
                frequence = f.frequencePaiement
            )
        } else null
    }
    CarteSection("Paramètres") {
        Champ("Nom", f.nom, f.erreurs["nom"], texte = true) { v -> onChange { copy(nom = v) } }
        OutlinedButton(onClick = { choisirDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(f.dateDebut.takeIf { it.isNotBlank() }?.let { "Date de début : $it" } ?: "Choisir la date de début")
        }
        f.erreurs["dateDebut"]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text("Fréquence des paiements", color = Color.White)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FrequencePaiementDette.entries.forEach { frequence ->
                FilterChip(
                    selected = f.frequencePaiement == frequence,
                    onClick = { onChange { copy(frequencePaiement = frequence) } },
                    label = { Text(frequence.libelle) }
                )
            }
        }
        Champ("Durée du prêt (mois)", f.dureeMois, f.erreurs["duree"], entier = true) { v -> onChange { copy(dureeMois = v) } }
        com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.Champ(
            "Montant de chaque paiement ($)", f.versementPrevu, f.erreurs["versement"], montant = true
        ) { v -> onChange { copy(versementPrevu = v) } }
        Champ("Taux annuel (%)", f.taux, f.erreurs["taux"]) { v -> onChange { copy(taux = v) } }
        situation?.let { s ->
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            Ligne("Date de fin calculée", MoteurCarteCredit.ecrireDate(s.dateFin))
            Ligne("Paiements déjà écoulés", "${s.paiementsEcoules}")
            Ligne("Montant total original", MoneyFormatter.formatAmount(s.principalInitial))
            Ligne("Capital payé", MoneyFormatter.formatAmount(s.capitalPaye))
            Ligne("Intérêts payés", MoneyFormatter.formatAmount(s.interetsPayes))
            Ligne("Solde actuel calculé", MoneyFormatter.formatAmount(s.soldeActuel), gras = true)
            Note("La première mensualité est due un mois après la date de début.")
        }

        // DA7 : taux variable avec changements datés
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Taux variable", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = f.tauxVariable, onCheckedChange = { v -> onChange { copy(tauxVariable = v) } })
        }
        if (f.tauxVariable) {
            f.changementsTaux.sortedBy { it.date }.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("À partir du ${c.date} : ${c.taux} %", color = Color.LightGray, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange { copy(changementsTaux = changementsTaux.filter { it.id != c.id }) } }) { Text("Retirer") }
                }
            }
            var date by remember { mutableStateOf("") }
            var taux by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(taux, { taux = it }, label = { Text("Taux %") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            val tauxValide = taux.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
            TextButton(
                enabled = MoteurCarteCredit.lireDate(date.trim()) != null && tauxValide != null,
                onClick = {
                    onChange { copy(changementsTaux = changementsTaux + ChangementTaux(date = date.trim(), taux = tauxValide!!)) }
                    date = ""; taux = ""
                }
            ) { Text("Ajouter le changement de taux") }
            Note("Le passé n'est jamais recalculé : seuls les versements futurs utilisent le nouveau taux.")
        }
    }
    if (choisirDate) {
        val initiale = MoteurCarteCredit.lireDate(f.dateDebut) ?: Date()
        val local = Calendar.getInstance().apply { time = initiale }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }
        val etatDate = rememberDatePickerState(initialSelectedDateMillis = utc.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { choisirDate = false },
            confirmButton = {
                TextButton(onClick = {
                    etatDate.selectedDateMillis?.let { millis ->
                        val choisiUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        val choisi = Calendar.getInstance().apply {
                            clear()
                            set(choisiUtc.get(Calendar.YEAR), choisiUtc.get(Calendar.MONTH), choisiUtc.get(Calendar.DAY_OF_MONTH))
                        }
                        onChange { copy(dateDebut = MoteurCarteCredit.ecrireDate(choisi.time)) }
                    }
                    choisirDate = false
                }) { Text("Choisir") }
            },
            dismissButton = { TextButton(onClick = { choisirDate = false }) { Text("Annuler") } }
        ) { DatePicker(state = etatDate) }
    }
}

/** DA2 / DA4 : échéancier prévisionnel et versements supplémentaires, depuis le solde RÉEL. */
@Composable
private fun SectionProjection(dette: CompteDette, onVoirEcheancier: () -> Unit) {
    var extra by remember { mutableStateOf("") }
    val actuel = remember(dette) { MoteurDette.echeancier(dette) }
    CarteSection("Projection") {
        if (actuel == null) {
            Note("Indiquez un versement prévu (ou un prêt financé complet) pour voir l'échéancier. Aucun taux ni durée n'est supposé.")
            return@CarteSection
        }
        val format = SimpleDateFormat("MMMM yyyy", Locale.CANADA_FRENCH)
        Ligne("Fin estimée", actuel.dateFin?.let { format.format(it) } ?: "impossible")
        Ligne("Intérêts futurs estimés", MoneyFormatter.formatAmount(actuel.totalInterets))
        actuel.avertissement?.let { Text(it, color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onVoirEcheancier) { Text("Voir l'échéancier détaillé") }

        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
        com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel(
            valeur = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versCentimes(extra),
            onValeurChange = { extra = com.xburnsx.toutiebudget.ui.composants_communs.ConversionMontant.versTexte(it, videSiZero = true) },
            libelle = "Versement supplémentaire par mois ($)",
            modifier = Modifier.fillMaxWidth()
        )
        val montantExtra = extra.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        if (montantExtra != null) {
            val accelere = remember(dette, montantExtra) { MoteurDette.echeancier(dette, montantExtra) }
            if (accelere != null && accelere.nombrePeriodes != null && actuel.nombrePeriodes != null) {
                Ligne("Nouvelle fin estimée", accelere.dateFin?.let { format.format(it) } ?: "—")
                Ligne("Temps économisé", "${actuel.nombrePeriodes - accelere.nombrePeriodes} mois")
                Ligne("Intérêts évités (estimés)", MoneyFormatter.formatAmount(actuel.totalInterets - accelere.totalInterets), couleur = Color(0xFF10B981))
            }
            Note("Simulation seulement, pas une transaction. Hypothèses : taux du contrat, sans pénalité de remboursement anticipé ni frais.")
        }
    }
}

/** DF3 : versements réels, par date et montant. */
@Composable
private fun SectionVersements(uiState: DetteUiState) {
    val paiements = MoteurDette.paiementsReels(uiState.transactions).sortedByDescending { it.date }
    if (paiements.isEmpty()) return
    val format = SimpleDateFormat("d MMM yyyy", Locale.CANADA_FRENCH)
    CarteSection("Versements enregistrés") {
        paiements.forEach { p ->
            Ligne(format.format(p.date), MoneyFormatter.formatAmount(p.montant))
        }
        Note("Pour corriger un versement, supprimez-le dans l'historique : les deux comptes sont remis ensemble.")
    }
}

// ===== Dialogues =====

@Composable
private fun DialogueEcheancier(dette: CompteDette, onFermer: () -> Unit) {
    val echeancier = remember(dette) { MoteurDette.echeancier(dette) } ?: return
    val format = SimpleDateFormat("MMM yyyy", Locale.CANADA_FRENCH)
    Dialog(onDismissRequest = onFermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Échéancier prévisionnel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onFermer) { Text("Fermer") }
                }
                Note("Depuis le solde réel d'aujourd'hui ; ce tableau ne remplace jamais le solde réel.")
                Column(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Row { listOf("Date", "Début", "Taux", "Intérêts", "Versement", "Capital", "Fin").forEach { Cellule(it, true) } }
                    LazyColumn {
                        items(echeancier.periodes, key = { it.numero }) { p ->
                            Row {
                                Cellule(format.format(p.date))
                                Cellule(MoneyFormatter.formatAmount(p.soldeInitial))
                                Cellule("${p.taux} %")
                                Cellule(MoneyFormatter.formatAmount(p.interets))
                                Cellule(MoneyFormatter.formatAmount(p.versement))
                                Cellule(MoneyFormatter.formatAmount(p.capital))
                                Cellule(MoneyFormatter.formatAmount(p.soldeFinal))
                            }
                        }
                    }
                }
                Text("Intérêts futurs estimés : ${MoneyFormatter.formatAmount(echeancier.totalInterets)}", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Cellule(texte: String, gras: Boolean = false) {
    Text(texte, modifier = Modifier.width(92.dp).padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun Champ(
    libelle: String,
    valeur: String,
    erreur: String?,
    texte: Boolean = false,
    entier: Boolean = false,
    onValeur: (String) -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onValeur,
        label = { Text(libelle) },
        isError = erreur != null,
        supportingText = erreur?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                texte -> KeyboardType.Text
                entier -> KeyboardType.Number
                else -> KeyboardType.Decimal
            }
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
