package com.xburnsx.toutiebudget.ui.investissement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CategorieActif
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.ModeSuiviPlacement
import com.xburnsx.toutiebudget.data.modeles.MouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.PositionTitre
import com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement
import com.xburnsx.toutiebudget.data.modeles.TypeMouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.MoteurInvestissement
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.Champ
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Calendar
import java.util.Date

private val fond = Color(0xFF2C2C2E)
private val vert = Color(0xFF10B981)
private val rouge = Color(0xFFEF4444)

/** « Gérer le placement » : valeur, capital, gains, performance, historique et (facultatif) positions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestissementScreen(compteId: String, viewModel: InvestissementViewModel, onRetour: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var dialogueValeur by remember { mutableStateOf(false) }
    var positionEditee by remember { mutableStateOf<PositionTitre?>(null) }
    var mouvementEdite by remember { mutableStateOf<MouvementPlacement?>(null) }

    LaunchedEffect(compteId) { viewModel.charger(compteId) }
    // IF5 : fermer l'éditeur seulement lorsque Room confirme l'écriture.
    LaunchedEffect(uiState.succesEcriture) {
        if (uiState.succesEcriture > 0L) {
            dialogueValeur = false
            positionEditee = null
            mouvementEdite = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text(uiState.compte?.nom ?: "Placement", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val compte = uiState.compte
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.erreurChargement != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.erreurChargement!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::reessayer) { Text("Réessayer") }
                }
                compte != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionCompte(compte, viewModel)
                    SectionCapital(compte, uiState, viewModel)
                    SectionPerformance(compte, uiState)
                    SectionGraphique(compte)
                    SectionValorisations(compte, onNouvelleValeur = { dialogueValeur = true })
                    if (compte.details.mode == ModeSuiviPlacement.DETAILLE) {
                        SectionPositions(compte, onEditer = { positionEditee = it }, onAjouter = {
                            positionEditee = PositionTitre(symbole = "", quantite = 0.0, coutTotal = 0.0)
                        })
                        SectionRepartition(compte)
                        SectionMouvements(compte, onEditer = { mouvementEdite = it }, onAjouter = {
                            mouvementEdite = MouvementPlacement(date = MoteurCarteCredit.ecrireDate(Date()), type = TypeMouvementPlacement.DIVIDENDE, montant = 0.0)
                        })
                    }
                }
            }
        }
    }

    if (dialogueValeur) {
        DialogueValeur(
            valeurActuelle = uiState.compte?.solde ?: 0.0,
            onConfirmer = { date, valeur, type, note -> viewModel.nouvelleValeur(date, valeur, type, note) },
            onAnnuler = { dialogueValeur = false }
        )
    }
    positionEditee?.let { p ->
        DialoguePosition(p, uiState.compte?.details?.positions?.any { it.id == p.id } == true,
            onEnregistrer = { viewModel.enregistrerPosition(it) },
            onSupprimer = { viewModel.supprimerPosition(p.id) },
            onAnnuler = { positionEditee = null })
    }
    mouvementEdite?.let { m ->
        DialogueMouvement(m, uiState.compte?.details?.mouvements?.any { it.id == m.id } == true,
            onEnregistrer = { viewModel.enregistrerMouvement(it) },
            onSupprimer = { viewModel.supprimerMouvement(m.id) },
            onAnnuler = { mouvementEdite = null })
    }
    uiState.message?.let { msg ->
        AlertDialog(onDismissRequest = viewModel::effacerMessage, text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::effacerMessage) { Text("OK") } })
    }
}

@Composable
private fun Carte(titre: String, contenu: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = fond)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun Note(t: String) = Text(t, color = Color.Gray, style = MaterialTheme.typography.bodySmall)

private fun signe(v: Double) = (if (v > 0.004) "+" else "") + MoneyFormatter.formatAmount(v)
private fun pct(v: Double) = (if (v > 0) "+" else "") + "%.2f".format(v) + " %"
private fun couleur(v: Double) = if (v > 0.004) vert else if (v < -0.004) rouge else Color.White

/** IA1, IA7, IF6 */
@Composable
private fun SectionCompte(compte: CompteInvestissement, vm: InvestissementViewModel) {
    val d = compte.details
    Carte("Compte") {
        Ligne("Valeur actuelle", MoneyFormatter.formatAmount(compte.solde), gras = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeSuiviPlacement.entries.forEach { m ->
                FilterChip(selected = d.mode == m, onClick = { vm.modifierDetails { copy(mode = m) } }, label = { Text(m.libelle) })
            }
        }
        Text("Type de compte", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeComptePlacement.entries.forEach { t ->
                FilterChip(selected = d.typeCompte == t, onClick = { vm.modifierDetails { copy(typeCompte = t) } }, label = { Text(t.libelle) })
            }
        }
        Note("Étiquette seulement : les droits de cotisation et la fiscalité ne sont pas calculés.")
        var institution by remember(d.institution) { mutableStateOf(d.institution ?: "") }
        Champ("Institution (ex. Wealthsimple)", institution, null, texte = true) { institution = it }
        if (institution != (d.institution ?: "")) {
            TextButton(onClick = { vm.modifierDetails { copy(institution = institution.trim().ifBlank { null }) } }) { Text("Enregistrer l'institution") }
        }
        if (d.mode == ModeSuiviPlacement.SIMPLE) {
            Note("La valeur du portefeuille n'est pas forcément retirable tout de suite (titres à vendre, délais du courtier).")
        } else {
            var encaisse by remember(d.encaisse) { mutableStateOf(d.encaisse?.toString() ?: "") }
            var retirable by remember(d.retirableConnu) { mutableStateOf(d.retirableConnu?.toString() ?: "") }
            Champ("Encaisse (espèces dans le compte)", encaisse, null) { encaisse = it }
            Champ("Montant retirable connu", retirable, null) { retirable = it }
            TextButton(onClick = {
                vm.modifierDetails {
                    copy(encaisse = encaisse.replace(',', '.').toDoubleOrNull(), retirableConnu = retirable.replace(',', '.').toDoubleOrNull())
                }
            }) { Text("Enregistrer les liquidités") }
            if (d.retirableConnu == null) Note("Montant retirable inconnu : il n'est pas déduit de la valeur.")
        }
    }
}

/** IA2, IA3 */
@Composable
private fun SectionCapital(compte: CompteInvestissement, ui: InvestissementUiState, vm: InvestissementViewModel) {
    val d = compte.details
    Carte("Capital et gains") {
        if (d.valeurDepart == null) {
            Note("Valeur de départ inconnue : indiquez-la pour calculer le capital net versé et les gains.")
            var valeur by remember { mutableStateOf("") }
            var date by remember { mutableStateOf(MoteurCarteCredit.ecrireDate(Date())) }
            Champ("Valeur de départ du suivi ($)", valeur, null) { valeur = it }
            Champ("Date (AAAA-MM-JJ)", date, null, texte = true) { date = it }
            val v = valeur.replace(',', '.').toDoubleOrNull()
            TextButton(enabled = v != null && MoteurCarteCredit.lireDate(date.trim()) != null, onClick = {
                vm.modifierDetails { copy(valeurDepart = v, dateDepart = date.trim()) }
            }) { Text("Enregistrer") }
        } else {
            Ligne("Valeur de départ (${d.dateDepart ?: "?"})", MoneyFormatter.formatAmount(d.valeurDepart))
        }
        Ligne("Apports (virements reçus)", MoneyFormatter.formatAmount(MoteurInvestissement.apports(ui.transactions)))
        Ligne("Retraits (virements envoyés)", MoneyFormatter.formatAmount(MoteurInvestissement.retraits(ui.transactions)))
        MoteurInvestissement.capitalNetVerse(compte, ui.transactions)?.let { Ligne("Capital net versé", MoneyFormatter.formatAmount(it), gras = true) }
        MoteurInvestissement.gainTotal(compte, ui.transactions)?.let { g ->
            Ligne("Gain ou perte total", signe(g), couleur = couleur(g), gras = true)
            Note("Valeur actuelle − capital net versé (réalisé et non réalisé ensemble).")
        }
        MoteurInvestissement.pourcentageSimple(compte, ui.transactions)?.let { Ligne("Variation depuis le départ", pct(it), couleur = couleur(it)) }
        if (d.mode == ModeSuiviPlacement.DETAILLE) {
            Ligne("Réalisé (ventes, dividendes, intérêts − frais)", signe(MoteurInvestissement.gainRealise(compte)))
            Ligne("Non réalisé (positions avec cours)", MoteurInvestissement.gainNonRealise(compte)?.let { signe(it) } ?: "indisponible")
        }
        Note("Un gain n'entre jamais dans les enveloppes ni dans le Prêt à placer.")
    }
}

/** IA10 */
@Composable
private fun SectionPerformance(compte: CompteInvestissement, ui: InvestissementUiState) {
    val twr = MoteurInvestissement.rendementPondereTemps(compte, ui.transactions)
    val mwr = MoteurInvestissement.rendementPondereFlux(compte, ui.transactions)
    Carte("Performance") {
        Ligne("Pondéré dans le temps (cumulatif)", twr?.let { pct(it) } ?: "indisponible", couleur = twr?.let { couleur(it) } ?: Color.White)
        Ligne("Pondéré selon les flux (annualisé)", mwr?.let { pct(it) } ?: "indisponible", couleur = mwr?.let { couleur(it) } ?: Color.White)
        Note(
            "Pondéré dans le temps : entre deux valeurs datées, sans l'effet des apports/retraits. " +
                "Pondéré selon les flux : TRI de vos apports et retraits. Il faut au moins deux valeurs datées ; " +
                "aucun pourcentage n'est tiré d'un seul solde. Brut de frais si les frais ne sont pas notés."
        )
    }
}

/** IA4 : valeurs connues seulement ; une période sans valeur reste vide. */
@Composable
private fun SectionGraphique(compte: CompteInvestissement) {
    var periode by remember { mutableStateOf(12) } // mois ; 0 = tout
    val tous = MoteurInvestissement.serieValeurs(compte)
    val debut = if (periode == 0) null else Calendar.getInstance().apply { add(Calendar.MONTH, -periode) }.time
    val points = tous.filter { debut == null || !it.date.before(debut) }
    Carte("Évolution de la valeur") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "1 mois", 3 to "3 mois", 12 to "1 an", 60 to "5 ans", 0 to "Tout").forEach { (m, l) ->
                FilterChip(selected = periode == m, onClick = { periode = m }, label = { Text(l) })
            }
        }
        if (points.size < 2) {
            Note("Pas assez de valeurs datées sur cette période. Ajoutez une « Nouvelle valeur » à chaque relevé.")
        } else {
            val min = points.minOf { it.valeur }
            val max = points.maxOf { it.valeur }
            val t0 = points.first().date.time.toFloat()
            val t1 = points.last().date.time.toFloat()
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val etendue = (max - min).takeIf { it > 0 } ?: 1.0
                val coords = points.map { p ->
                    Offset(
                        x = if (t1 > t0) (p.date.time - t0) / (t1 - t0) * size.width else 0f,
                        y = (size.height - ((p.valeur - min) / etendue * size.height)).toFloat()
                    )
                }
                coords.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF60A5FA), a, b, strokeWidth = 4f) }
                coords.forEach { drawCircle(Color.White, radius = 5f, center = it) }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(MoneyFormatter.formatAmount(min), color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(MoneyFormatter.formatAmount(max), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Note("Graphique de la VALEUR (points = valeurs connues, reliés en ligne droite). Ce n'est pas un graphique de rendement.")
        }
    }
}

/** IF7 */
@Composable
private fun SectionValorisations(compte: CompteInvestissement, onNouvelleValeur: () -> Unit) {
    Carte("Valeurs datées") {
        compte.details.valorisations.sortedByDescending { it.date }.forEach { v ->
            Text(
                "${v.date} · ${v.type.libelle} : ${v.ancienneValeur?.let { MoneyFormatter.formatAmount(it) + " → " } ?: ""}${MoneyFormatter.formatAmount(v.valeur)}" +
                    (v.note?.let { " · $it" } ?: ""),
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall
            )
        }
        Button(onClick = onNouvelleValeur, modifier = Modifier.fillMaxWidth()) { Text("Nouvelle valeur") }
        Note("Une nouvelle valeur n'est ni un dépôt ni un revenu. Les vrais dépôts et retraits se font avec Virer argent.")
    }
}

/** IA5 */
@Composable
private fun SectionPositions(compte: CompteInvestissement, onEditer: (PositionTitre) -> Unit, onAjouter: () -> Unit) {
    Carte("Positions") {
        compte.details.positions.sortedBy { it.symbole }.forEach { p ->
            Column(Modifier.fillMaxWidth().clickable { onEditer(p) }.padding(vertical = 4.dp)) {
                Row {
                    Text("${p.symbole} · ${p.categorie.libelle}", color = Color.White, modifier = Modifier.weight(1f))
                    Text(p.valeur?.let { MoneyFormatter.formatAmount(it) } ?: "cours inconnu", color = Color.White)
                }
                Text(
                    "${p.quantite} parts · coût ${MoneyFormatter.formatAmount(p.coutTotal)} · ${p.devise}" +
                        (p.valeur?.let { " · " + signe(it - p.coutTotal) } ?: ""),
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        TextButton(onClick = onAjouter) { Text("Ajouter une position") }
    }
}

/** IA9 */
@Composable
private fun SectionRepartition(compte: CompteInvestissement) {
    var vue by remember { mutableStateOf(0) }
    val parts = when (vue) {
        0 -> MoteurInvestissement.repartitionParCategorie(compte)
        1 -> MoteurInvestissement.repartitionParTitre(compte)
        else -> MoteurInvestissement.repartitionParDevise(compte)
    }
    if (parts.isEmpty()) return
    Carte("Répartition") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Catégorie", "Titre", "Devise").forEachIndexed { i, l -> FilterChip(selected = vue == i, onClick = { vue = i }, label = { Text(l) }) }
        }
        parts.forEach { Ligne(it.libelle, "${MoneyFormatter.formatAmount(it.valeur)} · ${"%.1f".format(it.pourcentage)} %") }
        Note("Encaisse incluse ; positions sans cours exclues. Devises non converties.")
    }
}

/** IA6 */
@Composable
private fun SectionMouvements(compte: CompteInvestissement, onEditer: (MouvementPlacement) -> Unit, onAjouter: () -> Unit) {
    Carte("Mouvements du placement") {
        compte.details.mouvements.sortedByDescending { it.date }.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { onEditer(m) }.padding(vertical = 4.dp)) {
                Text("${m.date} · ${m.type.libelle}${m.symbole?.let { " $it" } ?: ""}", color = Color.LightGray, modifier = Modifier.weight(1f))
                Text(MoneyFormatter.formatAmount(m.montant), color = Color.White)
            }
        }
        TextButton(onClick = onAjouter) { Text("Ajouter un mouvement") }
        Note("Achats, ventes et réinvestissements restent dans le portefeuille : ce ne sont pas des dépenses budgétaires. La valeur du compte suit les « Nouvelles valeurs ».")
    }
}

// ===== Dialogues =====

@Composable
private fun DialogueValeur(valeurActuelle: Double, onConfirmer: (String, Double, TypeValorisation, String?) -> Unit, onAnnuler: () -> Unit) {
    var valeur by remember { mutableStateOf("%.2f".format(java.util.Locale.US, valeurActuelle)) }
    var date by remember { mutableStateOf(MoteurCarteCredit.ecrireDate(Date())) }
    var type by remember { mutableStateOf(TypeValorisation.MARCHE) }
    var note by remember { mutableStateOf("") }
    val v = valeur.replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    val dateOk = MoteurCarteCredit.lireDate(date.trim()) != null
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Nouvelle valeur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeValorisation.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(if (t == TypeValorisation.MARCHE) "Marché" else "Correction") }) }
                }
                Champ("Valeur totale du compte ($)", valeur, null) { valeur = it }
                Champ("Date (AAAA-MM-JJ)", date, if (dateOk) null else "Date invalide", texte = true) { date = it }
                Champ("Note (optionnel)", note, null, texte = true) { note = it }
                v?.let { Note("Écart : ${signe(it - valeurActuelle)}") }
            }
        },
        confirmButton = { TextButton(enabled = v != null && dateOk, onClick = { onConfirmer(date.trim(), v!!, type, note.trim().ifBlank { null }) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

@Composable
private fun DialoguePosition(p: PositionTitre, existe: Boolean, onEnregistrer: (PositionTitre) -> Unit, onSupprimer: () -> Unit, onAnnuler: () -> Unit) {
    var symbole by remember { mutableStateOf(p.symbole) }
    var categorie by remember { mutableStateOf(p.categorie) }
    var quantite by remember { mutableStateOf(if (p.quantite == 0.0) "" else p.quantite.toString()) }
    var cout by remember { mutableStateOf(if (p.coutTotal == 0.0) "" else p.coutTotal.toString()) }
    var cours by remember { mutableStateOf(p.cours?.toString() ?: "") }
    var devise by remember { mutableStateOf(p.devise) }
    fun n(s: String) = s.replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val valide = symbole.isNotBlank() && n(quantite) != null && n(cout) != null && (cours.isBlank() || n(cours) != null)
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(if (existe) "Position ${p.symbole}" else "Nouvelle position") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Champ("Symbole", symbole, null, texte = true) { symbole = it.uppercase() }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategorieActif.entries.forEach { c -> FilterChip(selected = categorie == c, onClick = { categorie = c }, label = { Text(c.libelle) }) }
                }
                Champ("Quantité (fractions permises)", quantite, null) { quantite = it }
                Champ("Coût total, frais inclus ($)", cout, null) { cout = it }
                Champ("Cours actuel (optionnel)", cours, null) { cours = it }
                Champ("Devise", devise, null, texte = true) { devise = it.uppercase() }
                if (existe) TextButton(onClick = onSupprimer) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = valide, onClick = {
                onEnregistrer(p.copy(
                    symbole = symbole.trim(), categorie = categorie, quantite = n(quantite)!!, coutTotal = n(cout)!!,
                    cours = n(cours), dateCours = if (cours.isNotBlank()) MoteurCarteCredit.ecrireDate(Date()) else p.dateCours,
                    devise = devise.trim().ifBlank { "CAD" }
                ))
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

@Composable
private fun DialogueMouvement(m: MouvementPlacement, existe: Boolean, onEnregistrer: (MouvementPlacement) -> Unit, onSupprimer: () -> Unit, onAnnuler: () -> Unit) {
    var type by remember { mutableStateOf(m.type) }
    var date by remember { mutableStateOf(m.date) }
    var montant by remember { mutableStateOf(if (m.montant == 0.0) "" else m.montant.toString()) }
    var symbole by remember { mutableStateOf(m.symbole ?: "") }
    var quantite by remember { mutableStateOf(m.quantite?.toString() ?: "") }
    var gain by remember { mutableStateOf(m.gainRealise?.toString() ?: "") }
    var note by remember { mutableStateOf(m.note ?: "") }
    fun n(s: String) = s.replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    val valide = n(montant)?.let { it >= 0 } == true && MoteurCarteCredit.lireDate(date.trim()) != null
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(if (existe) "Mouvement" else "Nouveau mouvement") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeMouvementPlacement.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.libelle) }) }
                }
                Champ("Date (AAAA-MM-JJ)", date, null, texte = true) { date = it }
                Champ("Montant ($)", montant, null) { montant = it }
                Champ("Symbole (optionnel)", symbole, null, texte = true) { symbole = it.uppercase() }
                if (type == TypeMouvementPlacement.ACHAT || type == TypeMouvementPlacement.VENTE || type == TypeMouvementPlacement.REINVESTISSEMENT) {
                    Champ("Quantité", quantite, null) { quantite = it }
                }
                if (type == TypeMouvementPlacement.VENTE) Champ("Gain ou perte réalisé ($)", gain, null) { gain = it }
                Champ("Note (optionnel)", note, null, texte = true) { note = it }
                if (existe) TextButton(onClick = onSupprimer) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = valide, onClick = {
                onEnregistrer(m.copy(
                    type = type, date = date.trim(), montant = MoneyFormatter.roundAmount(n(montant)!!),
                    symbole = symbole.trim().ifBlank { null }, quantite = n(quantite),
                    gainRealise = if (type == TypeMouvementPlacement.VENTE) n(gain) else null, note = note.trim().ifBlank { null }
                ))
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
