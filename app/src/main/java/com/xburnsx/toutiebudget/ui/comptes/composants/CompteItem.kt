// chemin/simule: /ui/comptes/composants/CompteItem.kt
package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.xburnsx.toutiebudget.ui.budget.composants.StyleBudget
import com.xburnsx.toutiebudget.ui.budget.composants.StyleBudget.eclaircir
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs

/** Montant qui défile vers sa nouvelle valeur; la valeur exacte s'affiche dès la fin. */
@Composable
private fun montantAnime(valeur: Double): Double {
    val anime = remember { Animatable(valeur.toFloat()) }
    val premier = remember { booleanArrayOf(true) }
    LaunchedEffect(valeur) {
        if (premier[0]) {
            premier[0] = false
            anime.snapTo(valeur.toFloat())
        } else {
            anime.animateTo(valeur.toFloat(), tween(550, easing = FastOutSlowInEasing))
        }
    }
    return if (anime.isRunning) anime.value.toDouble() else valeur
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompteItem(
    compte: Compte,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    // Les dettes restent toujours en rouge
    val couleurFinale = if (compte is CompteDette) StyleBudget.Rouge else compte.couleur.toColor()

    // Appui : la carte s'enfonce légèrement
    val interaction = remember { MutableInteractionSource() }
    val presse by interaction.collectIsPressedAsState()
    val echelle by animateFloatAsState(
        if (presse) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "appuiCompte"
    )

    val forme = RoundedCornerShape(16.dp)
    val chiffres = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    val solde = montantAnime(compte.solde)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .shadow(6.dp, forme, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(StyleBudget.CarteHaut, StyleBudget.CarteBas)))
            .border(1.dp, StyleBudget.Bordure, forme)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône dans une pastille teintée de la couleur du compte
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(couleurFinale.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                IconePourCompte(compte = compte, tint = couleurFinale)
            }

            Spacer(Modifier.width(10.dp))

            // Barre de couleur du compte : pilule en dégradé
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .fillMaxHeight()
                    .width(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(couleurFinale.eclaircir(0.15f), couleurFinale.copy(alpha = 0.7f))))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = compte.nom,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(solde),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (compte.solde >= 0) Color.White else StyleBudget.Rouge,
                        style = chiffres
                    )
                }

                if (compte is CompteCheque) {
                    val pretAPlacer = montantAnime(compte.pretAPlacer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Prêt à placer: ${MoneyFormatter.formatAmount(pretAPlacer)}",
                        fontSize = 13.sp,
                        color = if (compte.pretAPlacer >= 0) StyleBudget.Vert else StyleBudget.Rouge,
                        fontWeight = FontWeight.SemiBold,
                        style = chiffres,
                        modifier = Modifier.align(Alignment.End)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoSecondaireCompte(compte = compte)
                }
            }
        }
    }
}

@Composable
private fun IconePourCompte(compte: Compte, tint: Color) {
    val icone = when (compte) {
        is CompteCheque -> Icons.Default.AccountBalanceWallet
        is CompteCredit -> Icons.Default.CreditCard
        is CompteDette -> Icons.Default.RequestQuote
        is CompteInvestissement -> Icons.AutoMirrored.Filled.ShowChart
    }
    Icon(
        imageVector = icone,
        contentDescription = "Type de compte",
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

/** Barre de progression : piste sombre, remplissage en dégradé, petit rebond. */
@Composable
private fun BarreProgression(progression: Float, couleur: Color) {
    val anime by animateFloatAsState(
        progression.coerceIn(0f, 1f),
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "progressionCompte"
    )
    val couleurAnimee by animateColorAsState(couleur, tween(400), label = "couleurProgression")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(StyleBudget.Piste)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(anime)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(couleurAnimee.copy(alpha = 0.6f), couleurAnimee)))
        )
    }
}

@Composable
private fun InfoSecondaireCompte(compte: Compte) {
    when (compte) {
        is CompteCredit -> {
            val progression = (abs(compte.solde) / compte.limiteCredit).toFloat().coerceIn(0f, 1f)
            val couleurProgression = when {
                progression > 0.9f -> StyleBudget.Rouge
                progression > 0.7f -> StyleBudget.Jaune
                else -> StyleBudget.Vert
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                BarreProgression(progression, couleurProgression)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "Utilisé : ${MoneyFormatter.formatAmount(abs(compte.solde))}",
                        fontSize = 12.sp, color = StyleBudget.TexteSecondaire
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Limite : ${MoneyFormatter.formatAmount(compte.limiteCredit)}",
                        fontSize = 12.sp, color = StyleBudget.Gris
                    )
                }
            }
        }
        is CompteDette -> {
            val base = (compte.prixTotal ?: compte.montantInitial).coerceAtLeast(0.0)
            val remaining = if (compte.soldeDette < 0) abs(compte.soldeDette) else compte.soldeDette
            val progression = if (base > 0) {
                ((base - remaining) / base).toFloat().coerceIn(0f, 1f)
            } else 0f
            Column {
                BarreProgression(progression, StyleBudget.Vert)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Remboursé à ${(progression * 100).toInt()}% sur ${MoneyFormatter.formatAmount(base)}",
                    fontSize = 12.sp, color = StyleBudget.Gris
                )
            }
        }
        is CompteInvestissement -> {
            // Gain sur les titres d'après le dernier prix connu (actualisé à l'ouverture de l'app)
            val resume = androidx.compose.runtime.remember(compte.detailsInvestissementJson, compte.solde) {
                compte.details.takeIf { it.positions.isNotEmpty() }?.let {
                    com.xburnsx.toutiebudget.domain.services.Portefeuille.resume(it, compte.solde)
                }
            }
            val gain = resume?.gain
            if (gain == null) {
                Text("Compte d'investissement", fontSize = 13.sp, color = StyleBudget.Gris)
            } else {
                val signe = if (gain < 0) "−" else "+"
                val pourcentage = resume.pourcentage?.let {
                    " (" + signe + String.format(java.util.Locale.CANADA_FRENCH, "%.2f", abs(it)) + " %)"
                }.orEmpty()
                Text(
                    text = signe + MoneyFormatter.formatAmount(abs(gain)) + pourcentage,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (gain < 0) StyleBudget.Rouge else StyleBudget.Vert
                )
            }
        }
        else -> { /* Ne rien faire pour les autres types de comptes */ }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompteItemPreview() {
    val compteCheque = CompteCheque(
        id = "1",
        utilisateurId = "user",
        nom = "WealthSimple",
        solde = 2543.75,
        couleur = "#4CAF50",
        estArchive = false,
        ordre = 1
    )
    
    val compteCreditNormal = CompteCredit(
        id = "2",
        utilisateurId = "user",
        nom = "Mastercard",
        soldeUtilise = -1250.47,
        couleur = "#FFC107",
        estArchive = false,
        ordre = 2,
        limiteCredit = 5000.0,
        tauxInteret = 19.99
    )
    
    val compteCreditLimite = CompteCredit(
        id = "3",
        utilisateurId = "user",
        nom = "Marge de Crédit",
        soldeUtilise = -9500.0,
        couleur = "#F44336",
        estArchive = false,
        ordre = 3,
        limiteCredit = 10000.0,
        tauxInteret = 7.5
    )
    
    val compteDette = CompteDette(
        id = "4",
        utilisateurId = "user",
        nom = "Prêt Auto",
        soldeDette = 15200.0,
        estArchive = false,
        ordre = 4,
        montantInitial = 25000.0,
        tauxInteret = 5.5,
        paiementMinimum = 500.0,
        dureeMoisPret = 60
    )
    
    val compteInvestissement = CompteInvestissement(
        id = "5",
        utilisateurId = "user",
        nom = "CELI",
        solde = 12750.33,
        couleur = "#9C27B0",
        estArchive = false,
        ordre = 5
    )

    MaterialTheme {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompteItem(compte = compteCheque, onClick = {}, onLongClick = {})
            CompteItem(compte = compteCreditNormal, onClick = {}, onLongClick = {})
            CompteItem(compte = compteCreditLimite, onClick = {}, onLongClick = {})
            CompteItem(compte = compteDette, onClick = {}, onLongClick = {})
            CompteItem(compte = compteInvestissement, onClick = {}, onLongClick = {})
        }
    }
}