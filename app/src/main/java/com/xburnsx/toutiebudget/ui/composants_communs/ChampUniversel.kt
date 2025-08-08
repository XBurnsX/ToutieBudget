// chemin/simule: /ui/composants_communs/ChampUniversel.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.composants_communs

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 🎯 CHAMP DE SAISIE UNIVERSEL POUR MONTANTS ET VALEURS
 *
 * Composant réutilisable qui affiche un champ cliquable et peut optionnellement
 * ouvrir un clavier numérique personnalisé.
 *
 * @param valeur La valeur actuelle (en centimes si isMoney=true, valeur normale sinon)
 * @param onValeurChange Callback appelé quand la valeur change
 * @param libelle Le texte d'étiquette à afficher
 * @param utiliserClavier Si true, ouvre le clavier numérique personnalisé au clic
 * @param isMoney Si true, traite comme de l'argent (1234 = 12.34$), sinon comme valeur normale avec décimales
 * @param suffix Suffixe à ajouter après la valeur (ex: "%", " mois", etc.) - ignoré si isMoney=true
 * @param icone L'icône à afficher
 * @param estObligatoire Si true, affiche un indicateur visuel d'obligation
 * @param couleurValeur Couleur du texte de la valeur (optionnel)
 * @param tailleValeur Taille du texte de la valeur (optionnel)
 * @param onClicPersonnalise Callback personnalisé au clic (si utiliserClavier=false)
 * @param modifier Modificateur Compose standard
 */
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampUniversel(
    valeur: Long,
    onValeurChange: (Long) -> Unit,
    libelle: String,
    utiliserClavier: Boolean = true,
    isMoney: Boolean = true,
    suffix: String = "",
    icone: ImageVector = Icons.Default.AttachMoney,
    estObligatoire: Boolean = false,
    couleurValeur: Color? = null,
    tailleValeur: TextUnit? = null,
    onClicPersonnalise: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {

    // État pour contrôler l'affichage du clavier
    var afficherClavier by remember { mutableStateOf(false) }

    // Formatage de la valeur affichée
    val valeurAffichee = remember(valeur, isMoney, suffix) {
        if (isMoney) {
            // Utiliser BigDecimal pour éviter les erreurs de précision
            val bigDecimal = BigDecimal.valueOf(valeur)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            
            // Forcer le formatage avec exactement 2 décimales SANS le "$"
            val resultat = String.format("%.2f", bigDecimal.toDouble())
            resultat
        } else {
            // Mode non-monétaire : toujours avec décimales (ex: 1290 -> 12.9)
            val bigDecimal = BigDecimal.valueOf(valeur)
                .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP)
            val resultat = String.format("%.1f", bigDecimal.toDouble())
            resultat
        }
    }

    Column(modifier = modifier) {
        // Étiquette avec indicateur d'obligation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = if (estObligatoire) "$libelle *" else libelle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Champ cliquable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(),
                    onClick = {
                        if (utiliserClavier) {
                            afficherClavier = true
                        } else {
                            onClicPersonnalise?.invoke()
                        }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                Text(
                    text = valeurAffichee,
                    style = LocalTextStyle.current.copy(
                        fontSize = tailleValeur ?: 16.sp,
                        color = couleurValeur ?: MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (!isMoney && suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suffix,
                        style = LocalTextStyle.current.copy(
                            fontSize = tailleValeur ?: 16.sp,
                            color = couleurValeur ?: MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }

    // Clavier numérique en modal (si activé)
    if (afficherClavier && utiliserClavier) {
        ModalBottomSheet(
            onDismissRequest = { afficherClavier = false },
            dragHandle = {
                // Poignée de glissement personnalisée
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        ) {
            ClavierNumerique(
                montantInitial = valeur,
                isMoney = isMoney,
                suffix = if (isMoney) suffix else "",
                onMontantChange = { nouveauMontant ->
                    onValeurChange(nouveauMontant)
                },
                onFermer = { afficherClavier = false }
            )
        }
    }
}

