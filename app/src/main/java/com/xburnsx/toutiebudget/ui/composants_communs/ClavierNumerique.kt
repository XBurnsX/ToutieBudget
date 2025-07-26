// chemin/simule: /ui/composants_communs/ClavierNumerique.kt
// Dépendances: Jetpack Compose, Material3, Dialog

package com.xburnsx.toutiebudget.ui.composants_communs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import kotlinx.coroutines.launch

/**
 * 🎯 CHAMP DE SAISIE UNIVERSEL POUR MONTANTS
 *
 * Affiche un champ cliquable qui délègue l'ouverture du clavier au composant parent.
 *
 * @param montant Le montant actuel (en centimes si isMoney=true, valeur normale si isMoney=false)
 * @param onClick Callback appelé lorsque le champ est cliqué.
 * @param libelle Le texte d'étiquette à afficher
 * @param isMoney Si true, traite comme de l'argent (1234 = 12.34$), si false traite comme valeur normale (12 = 12)
 * @param suffix Suffixe à ajouter après la valeur (ex: "%", " mois", etc.) - ignoré si isMoney=true
 * @param icone L'icône à afficher (optionnel)
 * @param estObligatoire Si true, affiche un indicateur visuel d'obligation
 * @param couleurMontant Couleur du texte du montant (optionnel)
 * @param tailleMontant Taille du texte du montant (optionnel)
 * @param modifier Modificateur Compose standard
 */
@Composable
fun ChampMontantUniversel(
    montant: Long,
    onClick: () -> Unit,
    libelle: String,
    isMoney: Boolean = true,
    suffix: String = "",
    icone: ImageVector = Icons.Default.AttachMoney,
    estObligatoire: Boolean = false,
    couleurMontant: Color? = null,
    tailleMontant: androidx.compose.ui.unit.TextUnit? = null,
    modifier: Modifier = Modifier
) {
    val montantAffiche = remember(montant, isMoney, suffix) {
        if (isMoney) {
            String.format("%.2f \$", montant / 100.0)
        } else {
            "$montant$suffix"
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

        // Champ cliquable pour ouvrir le clavier
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(),
                    onClick = onClick
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
                    text = montantAffiche,
                    style = LocalTextStyle.current.copy(
                        fontSize = tailleMontant ?: 16.sp,
                        color = couleurMontant ?: MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 🎯 CLAVIER IDENTIQUE AU FLUTTER NumericKeyboard
 * Reproduit EXACTEMENT le comportement, l'apparence et la logique
 */
@Composable
fun ClavierNumerique(
    montantInitial: Long,
    isMoney: Boolean,
    suffix: String,
    onMontantChange: (Long) -> Unit,
    onFermer: () -> Unit
) {
    // 📱 STATE IDENTIQUE FLUTTER : Utilise String comme Flutter
    var texteActuel by remember {
        mutableStateOf(
            if (isMoney) {
                if (montantInitial == 0L) "0.00 \$"
                else {
                    val montantFormate = (montantInitial / 100.0)
                    String.format("%.2f \$", montantFormate)
                }
            } else {
                montantInitial.toString()
            }
        )
    }

    /**
     * 🔢 LOGIQUE IDENTIQUE FLUTTER : _onKeyTap
     * Reproduit EXACTEMENT la fonction du NumericKeyboard Flutter
     */
    val gererToucheNumerique = { touche: String ->
        if (isMoney) {
            // === LOGIQUE EXACTE DU FLUTTER MODE ARGENT ===
            var texteActuelTravail = texteActuel
            if (texteActuelTravail != "0.00 \$") {
                texteActuelTravail = texteActuelTravail.replace("\$", "").replace(" ", "")
            }

            when {
                texteActuelTravail == "0.00" || texteActuelTravail == "0.00 \$" || texteActuelTravail.isEmpty() -> {
                    texteActuel = if (touche == "-") {
                        "-0.00 \$"
                    } else {
                        "0.0$touche \$"
                    }
                }
                touche == "-" -> {
                    texteActuel = if (texteActuelTravail.startsWith("-")) {
                        "${texteActuelTravail.substring(1)} \$"
                    } else {
                        "-$texteActuelTravail \$"
                    }
                }
                else -> {
                    val estNegatif = texteActuelTravail.startsWith("-")
                    val textePositif = if (estNegatif) {
                        texteActuelTravail.substring(1)
                    } else {
                        texteActuelTravail
                    }.replace(".", "")

                    var nouveauTexte = textePositif + touche

                    // Supprimer les zéros de tête (sauf si un seul caractère)
                    while (nouveauTexte.length > 1 && nouveauTexte.startsWith("0")) {
                        nouveauTexte = nouveauTexte.substring(1)
                    }

                    // Assurer au moins 3 caractères pour les centimes
                    if (nouveauTexte.length < 3) {
                        nouveauTexte = nouveauTexte.padStart(3, '0')
                    }

                    val partieEntiere = nouveauTexte.substring(0, nouveauTexte.length - 2)
                    val partieDecimale = nouveauTexte.substring(nouveauTexte.length - 2)

                    val resultat = "$partieEntiere.$partieDecimale \$"
                    texteActuel = if (estNegatif) "-$resultat" else resultat
                }
            }
        } else {
            // === LOGIQUE EXACTE DU FLUTTER MODE NORMAL ===
            when (touche) {
                "." -> {
                    if (!texteActuel.contains(".")) {
                        texteActuel = if (texteActuel.isEmpty()) "0." else "$texteActuel."
                    }
                }
                else -> {
                    texteActuel = if (texteActuel == "0") {
                        touche
                    } else {
                        texteActuel + touche
                    }
                }
            }
        }

        // Convertir vers Long pour le callback
        val valeurLong = if (isMoney) {
            val texteNettoye = texteActuel.replace("\$", "").replace(" ", "")
            val valeurDouble = texteNettoye.toDoubleOrNull() ?: 0.0
            (valeurDouble * 100).toLong()
        } else {
            texteActuel.replace(suffix, "").toLongOrNull() ?: 0L
        }
        onMontantChange(valeurLong)
    }

    /**
     * ⌫ LOGIQUE BACKSPACE CORRIGÉE : Efface chiffre par chiffre SANS EXCEPTION
     * Plus de reset à 0 - efface vraiment chiffre par chiffre jusqu'au bout
     */
    val gererBackspace = {
        if (isMoney) {
            val texteActuelTravail = texteActuel.replace("\$", "").replace(" ", "")

            val estNegatif = texteActuelTravail.startsWith("-")
            val textePositif = if (estNegatif) {
                texteActuelTravail.substring(1)
            } else {
                texteActuelTravail
            }.replace(".", "")

            // ✅ CORRECTION : Efface chiffre par chiffre jusqu'à 0, jamais de reset brutal
            if (textePositif.length <= 1) {
                // Si on arrive au dernier chiffre, on va à 0.00
                texteActuel = "0.00 \$"
            } else {
                // Sinon on enlève le dernier chiffre et on reforme le montant
                val nouveauTexte = textePositif.substring(0, textePositif.length - 1)

                // S'assurer qu'on a au moins 3 caractères pour former XX.XX
                val texteFormate = if (nouveauTexte.length < 3) {
                    nouveauTexte.padStart(3, '0')
                } else {
                    nouveauTexte
                }

                val partieEntiere = texteFormate.substring(0, texteFormate.length - 2)
                val partieDecimale = texteFormate.substring(texteFormate.length - 2)

                val resultat = "$partieEntiere.$partieDecimale \$"
                texteActuel = if (estNegatif) "-$resultat" else resultat
            }
        } else {
            // Mode normal : efface simplement le dernier caractère
            if (texteActuel.isNotEmpty() && texteActuel != "0") {
                texteActuel = texteActuel.substring(0, texteActuel.length - 1)
                if (texteActuel.isEmpty()) {
                    texteActuel = "0"
                }
            }
        }

        // Convertir vers Long pour le callback
        val valeurLong = if (isMoney) {
            val texteNettoye = texteActuel.replace("\$", "").replace(" ", "")
            val valeurDouble = texteNettoye.toDoubleOrNull() ?: 0.0
            (valeurDouble * 100).toLong()
        } else {
            texteActuel.replace(suffix, "").toLongOrNull() ?: 0L
        }
        onMontantChange(valeurLong)
    }

    // Affichage du texte EXACT comme Flutter
    val montantAffiche = if (isMoney) {
        texteActuel
    } else {
        "$texteActuel$suffix"
    }

    // 🎨 DESIGN VISUEL AVEC COULEURS DU THÈME
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = MaterialTheme.colorScheme.surface, // ✅ Couleur du thème
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // === ZONE D'AFFICHAGE AVEC COULEURS DU THÈME ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                // Zone d'affichage du montant
                Column(
                    modifier = Modifier.width(213.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = montantAffiche,
                        style = LocalTextStyle.current.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface // ✅ Couleur du thème
                        ),
                        textAlign = TextAlign.Left,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Ligne sous le texte avec couleur du thème
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(213.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary) // ✅ Couleur primaire du thème
                    )
                }

                Spacer(modifier = Modifier.width(35.dp))

                // Bouton Backspace avec couleurs du thème
                ClavierToucheFlutter(
                    texte = "",
                    icone = Icons.Outlined.Backspace,
                    onPressed = gererBackspace,
                    estAction = true // Bouton d'action
                )
            }

            Spacer(modifier = Modifier.height(16.dp)) // Plus d'espace

            // === CLAVIER COMPLET COMME FLUTTER (TOUS LES CHIFFRES) ===
            // Rangée 1-2-3 (COMPLÈTE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClavierToucheFlutter(
                    texte = "1",
                    onPressed = { gererToucheNumerique("1") }
                )
                ClavierToucheFlutter(
                    texte = "2",
                    onPressed = { gererToucheNumerique("2") }
                )
                ClavierToucheFlutter(
                    texte = "3",
                    onPressed = { gererToucheNumerique("3") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rangée 4-5-6 (COMPLÈTE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClavierToucheFlutter(
                    texte = "4",
                    onPressed = { gererToucheNumerique("4") }
                )
                ClavierToucheFlutter(
                    texte = "5",
                    onPressed = { gererToucheNumerique("5") }
                )
                ClavierToucheFlutter(
                    texte = "6",
                    onPressed = { gererToucheNumerique("6") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rangée 7-8-9 (COMPLÈTE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClavierToucheFlutter(
                    texte = "7",
                    onPressed = { gererToucheNumerique("7") }
                )
                ClavierToucheFlutter(
                    texte = "8",
                    onPressed = { gererToucheNumerique("8") }
                )
                ClavierToucheFlutter(
                    texte = "9",
                    onPressed = { gererToucheNumerique("9") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rangée finale : - - 0 - ✓ (comme votre image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Bouton moins (si mode argent)
                if (isMoney) {
                    ClavierToucheFlutter(
                        texte = "-",
                        onPressed = { gererToucheNumerique("-") }
                    )
                } else {
                    ClavierToucheFlutter(
                        texte = ".",
                        onPressed = { gererToucheNumerique(".") }
                    )
                }

                ClavierToucheFlutter(
                    texte = "0",
                    onPressed = { gererToucheNumerique("0") }
                )

                // Bouton Done BLANC OBLONG comme votre image
                ClavierToucheFlutter(
                    texte = "",
                    icone = Icons.Default.Check,
                    onPressed = onFermer,
                    estAction = true // Bouton blanc oblong
                )
            }

            Spacer(modifier = Modifier.height(8.dp)) // Espacement final exact Flutter
        }
    }
}

/**
 * 🔘 BOUTON DE CLAVIER ÉCRASÉ EN HAUTEUR - COULEURS DU THÈME
 * Boutons d'action : fond blanc + icône colorée selon le thème !
 */
@Composable
private fun ClavierToucheFlutter(
    texte: String,
    icone: ImageVector? = null,
    onPressed: () -> Unit = {},
    estAction: Boolean = false, // true pour X et ✓
    paddingZero: Boolean = false
) {
    val retourHaptique = LocalHapticFeedback.current

    // 🎨 COULEURS SPÉCIALES POUR BOUTONS D'ACTION
    val couleurBouton = if (estAction) {
        Color.White // ✅ FOND BLANC pour X et ✓
    } else {
        MaterialTheme.colorScheme.primary // Couleur primaire du thème pour chiffres
    }

    val couleurTexte = if (estAction) {
        MaterialTheme.colorScheme.primary // ✅ ICÔNE COLORÉE selon le thème
    } else {
        MaterialTheme.colorScheme.onPrimary // Contraste pour boutons chiffres
    }

    // 🥞 BOUTONS ENCORE PLUS ÉCRASÉS EN HAUTEUR
    ElevatedButton(
        onClick = {
            retourHaptique.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPressed()
        },
        modifier = Modifier
            .width(85.dp)     // Largeur normale
            .height(55.dp),   // ✅ HAUTEUR ENCORE PLUS ÉCRASÉE (était 65dp)
        shape = RoundedCornerShape(28.dp), // Coins adaptés à la nouvelle hauteur
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = couleurBouton, // ✅ Blanc pour actions, thème pour chiffres
            contentColor = couleurTexte     // ✅ Couleur thème pour icônes, blanc pour chiffres
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = if (estAction) 4.dp else 2.dp, // Plus d'élévation pour boutons blancs
            pressedElevation = 2.dp
        ),
        contentPadding = if (paddingZero) PaddingValues(0.dp) else ButtonDefaults.ContentPadding
    ) {
        if (icone != null) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = couleurTexte, // ✅ Icône colorée selon le thème
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = texte,
                style = LocalTextStyle.current.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = couleurTexte // ✅ Texte coloré selon le thème
            )
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Champ Montant Flutter")
@Composable
fun ChampMontantUniverselPreview() {
    ToutieBudgetTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChampMontantUniversel(
                    montant = 123456L, // 1234.56$
                    onClick = {},
                    libelle = "Montant de la dépense",
                    isMoney = true,
                    estObligatoire = true
                )

                ChampMontantUniversel(
                    montant = 12L, // 12 mois
                    onClick = {},
                    libelle = "Durée en mois",
                    isMoney = false,
                    suffix = " mois",
                    icone = Icons.Default.AttachMoney
                )

                ChampMontantUniversel(
                    montant = 525L, // 525%
                    onClick = {},
                    libelle = "Taux d'intérêt",
                    isMoney = false,
                    suffix = "%",
                    icone = Icons.Default.AttachMoney
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Clavier Flutter Identique")
@Composable
fun ClavierFlutterIdentiquePreview() {
    ToutieBudgetTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                ClavierNumerique(
                    montantInitial = 123456L,
                    isMoney = true,
                    suffix = "",
                    onMontantChange = {},
                    onFermer = {}
                )
            }
        }
    }
}