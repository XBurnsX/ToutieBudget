package com.xburnsx.toutiebudget.ui.investissement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.Portefeuille
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.Champ
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Date

private val vertTitre = Color(0xFF10B981)
private val rougeTitre = Color(0xFFEF4444)

private fun couleurGain(v: Double?) = when {
    v == null -> Color.Gray
    v > 0.004 -> vertTitre
    v < -0.004 -> rougeTitre
    else -> Color.White
}
private fun signeArgent(v: Double) = (if (v > 0.004) "+" else "") + MoneyFormatter.formatAmount(v)
private fun signePct(v: Double) = (if (v > 0) "+" else "") + "%.2f".format(v) + " %"
private fun nombre(s: String) = s.replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
private fun quantiteTexte(q: Double) = if (q == Math.floor(q)) q.toLong().toString() else q.toBigDecimal().stripTrailingZeros().toPlainString()

/** Mes titres : valeur, gain ou perte en $ et en %, chaque titre avec son coût moyen et son cours. */
@Composable
fun SectionTitres(
    compte: CompteInvestissement,
    actualisationEnCours: Boolean,
    onAcheter: () -> Unit,
    onActualiser: () -> Unit,
    onTitre: (String) -> Unit
) {
    val r = Portefeuille.resume(compte.details, compte.solde)
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Totaux
        Text("Valeur du compte", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text(
            MoneyFormatter.formatAmount(r.valeurTotale ?: compte.solde),
            color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
        )
        if (r.lignes.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (r.gain?.let(::signeArgent) ?: "Gain inconnu") + (r.pourcentage?.let { "  (${signePct(it)})" } ?: ""),
                    color = couleurGain(r.gain), fontWeight = FontWeight.SemiBold
                )
                Text("  sur tes titres", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Argent non placé", color = Color.Gray, modifier = Modifier.weight(1f))
            Text(MoneyFormatter.formatAmount(r.argentNonPlace), color = Color.White)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAcheter) { Text("Ajouter un achat") }
            OutlinedButton(onClick = onActualiser, enabled = r.lignes.isNotEmpty() && !actualisationEnCours) {
                if (actualisationEnCours) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Actualiser les prix")
            }
        }

        // Titres
        r.lignes.forEach { l ->
            HorizontalDivider(color = Color(0xFF3A3A3C))
            Column(Modifier.fillMaxWidth().clickable { onTitre(l.position.symbole) }.padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(l.position.symbole, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(l.valeur?.let { MoneyFormatter.formatAmount(it) + (if (l.position.devise != "CAD") " ${l.position.devise}" else "") } ?: "prix inconnu", color = Color.White)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${quantiteTexte(l.position.quantite)} parts · coût moyen ${"%.2f".format(l.coutMoyen)} $" +
                            (l.position.cours?.let { " · prix ${"%.2f".format(it)} $" } ?: ""),
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                    )
                    Text(
                        (l.gain?.let(::signeArgent) ?: "") + (l.pourcentage?.let { " (${signePct(it)})" } ?: ""),
                        color = couleurGain(l.gain), style = MaterialTheme.typography.bodySmall
                    )
                }
                l.position.dateCours?.let {
                    Text("Prix au $it", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (r.lignes.isEmpty()) {
            Text(
                "Ajoute tes achats (ex. 30,2 XEQT à 34,50 $ le 10 octobre). « Actualiser les prix » va chercher le prix du jour.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Nouvel achat. « Déjà détenu avant le suivi » ne prend pas l'argent non placé du compte. */
@Composable
fun DialogueAchat(
    argentNonPlace: Double,
    onAcheter: (symbole: String, quantite: Double, prix: Double, frais: Double, date: String, devise: String, payeAvecArgent: Boolean) -> Unit,
    onAnnuler: () -> Unit
) {
    var symbole by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var frais by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(MoteurCarteCredit.ecrireDate(Date())) }
    var devise by remember { mutableStateOf("CAD") }
    var dejaDetenu by remember { mutableStateOf(false) }
    val q = nombre(quantite)?.takeIf { it > 0 }
    val p = nombre(prix)?.takeIf { it >= 0 }
    val f = if (frais.isBlank()) 0.0 else nombre(frais)?.takeIf { it >= 0 }
    val valide = symbole.isNotBlank() && q != null && p != null && f != null && MoteurCarteCredit.lireDate(date.trim()) != null
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Ajouter un achat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Champ("Symbole (ex. XEQT)", symbole, null, texte = true) { symbole = it.uppercase().trim() }
                Champ("Quantité (ex. 30,2)", quantite, null) { quantite = it }
                Champ("Prix payé par part ($)", prix, null, montant = true) { prix = it }
                Champ("Frais (facultatif)", frais, null, montant = true, facultatif = true) { frais = it }
                Champ("Date d'achat (AAAA-MM-JJ)", date, null, texte = true) { date = it }
                Champ("Devise (CAD, USD…)", devise, null, texte = true) { devise = it.uppercase().trim() }
                if (q != null && p != null) {
                    Text("Total : ${MoneyFormatter.formatAmount(q * p + (f ?: 0.0))} $devise", color = Color.LightGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dejaDetenu = !dejaDetenu }) {
                    Checkbox(checked = dejaDetenu, onCheckedChange = { dejaDetenu = it })
                    Text("Déjà détenu avant le suivi (ne prend pas l'argent non placé)", style = MaterialTheme.typography.bodySmall)
                }
                if (!dejaDetenu) {
                    Text("Argent non placé disponible : ${MoneyFormatter.formatAmount(argentNonPlace)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valide, onClick = {
                onAcheter(symbole, q!!, p!!, f!!, date.trim(), devise.ifBlank { "CAD" }, !dejaDetenu)
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

/** Un titre : mettre son prix à la main, ou en vendre. */
@Composable
fun DialogueTitre(
    compte: CompteInvestissement,
    symbole: String,
    onPrixManuel: (Double) -> Unit,
    onVendre: (quantite: Double, prix: Double, frais: Double, date: String) -> Unit,
    onAnnuler: () -> Unit
) {
    val ligne = Portefeuille.resume(compte.details, compte.solde).lignes.firstOrNull { it.position.symbole == symbole } ?: run { onAnnuler(); return }
    var prixManuel by remember { mutableStateOf(ligne.position.cours?.toString() ?: "") }
    var vente by remember { mutableStateOf(false) }
    var quantite by remember { mutableStateOf("") }
    var prixVente by remember { mutableStateOf(ligne.position.cours?.toString() ?: "") }
    var frais by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(MoteurCarteCredit.ecrireDate(Date())) }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(symbole) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${quantiteTexte(ligne.position.quantite)} parts · coût ${MoneyFormatter.formatAmount(ligne.position.coutTotal)}", color = Color.LightGray)
                if (!vente) {
                    Champ("Prix actuel par part (${ligne.position.devise})", prixManuel, null, montant = true, facultatif = true) { prixManuel = it }
                    Text("Pour corriger ou quand le prix en ligne est introuvable.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vente = true }) { Text("Vendre des parts") }
                } else {
                    Champ("Quantité vendue", quantite, null) { quantite = it }
                    Champ("Prix de vente par part", prixVente, null, montant = true) { prixVente = it }
                    Champ("Frais (facultatif)", frais, null, montant = true, facultatif = true) { frais = it }
                    Champ("Date (AAAA-MM-JJ)", date, null, texte = true) { date = it }
                }
            }
        },
        confirmButton = {
            if (!vente) {
                TextButton(enabled = nombre(prixManuel)?.let { it >= 0 } == true, onClick = { onPrixManuel(nombre(prixManuel)!!) }) { Text("Enregistrer le prix") }
            } else {
                val q = nombre(quantite)?.takeIf { it > 0 }
                val p = nombre(prixVente)?.takeIf { it >= 0 }
                val f = if (frais.isBlank()) 0.0 else nombre(frais)?.takeIf { it >= 0 }
                TextButton(
                    enabled = q != null && p != null && f != null && MoteurCarteCredit.lireDate(date.trim()) != null,
                    onClick = { onVendre(q!!, p!!, f!!, date.trim()) }
                ) { Text("Vendre") }
            }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Fermer") } }
    )
}
