// chemin/simule: /ui/composants_communs/ClavierNumerique.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.composants_communs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 🎯 CLAVIER NUMÉRIQUE RÉUTILISABLE
 *
 * Clavier numérique personnalisé identique au design Flutter.
 * Peut gérer les montants d'argent (mode centimes) ou les valeurs normales.
 *
 * @param montantInitial La valeur initiale (en centimes si isMoney=true)
 * @param isMoney Si true, gère les montants d'argent avec décimales
 * @param suffix Suffixe pour les valeurs non-monétaires (ex: "%", " mois")
 * @param onMontantChange Callback appelé à chaque changement de valeur
 * @param onFermer Callback appelé pour fermer le clavier
 */
@Composable
fun ClavierNumerique(
    montantInitial: Long,
    isMoney: Boolean,
    suffix: String,
    onMontantChange: (Long) -> Unit,
    onFermer: () -> Unit,
    onFermerAvecRestoration: (() -> Unit)? = null
) {
    // 📱 STATE IDENTIQUE FLUTTER : Utilise String comme Flutter
    var texteActuel by remember {
        mutableStateOf(
            if (isMoney) {
                if (montantInitial == 0L) "0.00 $"
                else {
                    MoneyFormatter.formatAmountFromCents(montantInitial)
                }
            } else {
                // Mode non-monétaire : on démarre VIDE pour éviter les artefacts (ex: 0.0)
                ""
            }
        )
    }

    // 🔄 GARDE LE MONTANT ORIGINAL POUR RESTAURATION
    var montantOriginal by remember { mutableStateOf(montantInitial) }
    var aCommenceAEcrire by remember { mutableStateOf(false) }

    // 🔄 FONCTION UNIVERSELLE POUR FERMER AVEC RESTAURATION
    val fermerAvecRestoration = {
        // 🔄 RESTAURER LE MONTANT ORIGINAL SI ON N'A PAS ÉCRIT
        if (!aCommenceAEcrire) {
            onMontantChange(montantOriginal)
        }
        onFermerAvecRestoration?.invoke() ?: onFermer()
    }

    /**
     * 🔢 LOGIQUE IDENTIQUE FLUTTER : _onKeyTap
     * Reproduit EXACTEMENT la fonction du NumericKeyboard Flutter
     */
    val gererToucheNumerique = { touche: String ->
        // 🔄 RÉINITIALISER SI PREMIÈRE TOUCHE
        if (!aCommenceAEcrire) {
            aCommenceAEcrire = true
            if (isMoney) {
                texteActuel = if (touche == "-") {
                    "-0.00 $"
                } else {
                    "0.0$touche $"
                }
            } else {
                // === LOGIQUE MODE DÉCIMAL (ex: taux d'intérêt) ===
                when (touche) {
                    "." -> {
                        if (!texteActuel.contains(".")) {
                            texteActuel = if (texteActuel.isEmpty()) {
                                if (suffix.isEmpty()) "0." else "0.$suffix"
                            } else {
                                val texteSansSuffix = texteActuel.replace(suffix, "")
                                if (suffix.isEmpty()) "$texteSansSuffix." else "$texteSansSuffix.$suffix"
                            }
                        }
                    }
                    else -> {
                        val texteSansSuffix = texteActuel.replace(suffix, "")
                        texteActuel = if (texteSansSuffix.isEmpty() || texteSansSuffix == "0" || texteSansSuffix == "0.") {
                            touche + suffix
                        } else {
                            texteSansSuffix + touche + suffix
                        }
                    }
                }
            }
        } else {
            // === LOGIQUE NORMALE ===
            if (isMoney) {
                // === LOGIQUE EXACTE DU FLUTTER MODE ARGENT ===
                var texteActuelTravail = texteActuel
                if (texteActuelTravail != "0.00 $") {
                    texteActuelTravail = texteActuelTravail.replace("$", "").replace(" ", "")
                }

                when {
                    texteActuelTravail == "0.00" || texteActuelTravail == "0.00 $" || texteActuelTravail.isEmpty() -> {
                        texteActuel = if (touche == "-") {
                            "-0.00 $"
                        } else {
                            "0.0$touche $"
                        }
                    }

                    touche == "-" -> {
                        texteActuel = if (texteActuelTravail.startsWith("-")) {
                            "${texteActuelTravail.substring(1)} $"
                        } else {
                            "-$texteActuelTravail $"
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

                        val resultat = "$partieEntiere.$partieDecimale $"
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
        }

        // Convertir vers Long pour le callback
        val valeurLong = if (isMoney) {
            val texteNettoye = texteActuel.replace("$", "").replace(" ", "")
            // Conversion directe sans passer par MoneyFormatter pour éviter les erreurs de précision
            try {
                val valeurDouble = texteNettoye.toDouble()
                // Utiliser BigDecimal pour une conversion parfaitement précise
                val bigDecimal = BigDecimal.valueOf(valeurDouble)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                val resultat = bigDecimal.toLong()
                resultat
            } catch (e: NumberFormatException) {
                0L
            }
        } else {
            // Mode décimal : convertir en centimes (ex: 12.9 -> 1290)
            val texteNettoye = texteActuel.replace(suffix, "")
            try {
                val valeurDouble = texteNettoye.toDoubleOrNull() ?: 0.0
                val bigDecimal = BigDecimal.valueOf(valeurDouble)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                bigDecimal.toLong()
            } catch (e: NumberFormatException) {
                0L
            }
        }

        onMontantChange(valeurLong)
    }

    /**
     * ⌫ LOGIQUE BACKSPACE CORRIGÉE : Efface chiffre par chiffre SANS EXCEPTION
     * Plus de reset à 0 - efface vraiment chiffre par chiffre jusqu'au bout
     */
    val gererBackspace = {
            if (isMoney) {
                val texteActuelTravail = texteActuel.replace("$", "").replace(" ", "")

                val estNegatif = texteActuelTravail.startsWith("-")
                val textePositif = if (estNegatif) {
                    texteActuelTravail.substring(1)
                } else {
                    texteActuelTravail
                }.replace(".", "")

                // ✅ CORRECTION : Efface chiffre par chiffre jusqu'à 0, jamais de reset brutal
                if (textePositif.length <= 1) {
                    // Si on arrive au dernier chiffre, on va à 0.00
                    texteActuel = "0.00 $"
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

                    val resultat = "$partieEntiere.$partieDecimale $"
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
                val texteNettoye = texteActuel.replace("$", "").replace(" ", "")
                // Conversion directe sans passer par MoneyFormatter pour éviter les erreurs de précision
                try {
                    val valeurDouble = texteNettoye.toDouble()
                    val bigDecimal = BigDecimal.valueOf(valeurDouble)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                    bigDecimal.toLong()
                } catch (e: NumberFormatException) {
                    0L
                }
            } else {
                // Pour les valeurs non-monétaires (comme les taux d'intérêt), on garde la valeur telle quelle
                val texteNettoye = texteActuel.replace(suffix, "")
                try {
                    // NE PAS multiplier par 100 pour les taux d'intérêt !
                    // 19.9% reste 19.9, pas 1990 !
                    val valeurDouble = texteNettoye.toDoubleOrNull() ?: 0.0
                    val bigDecimal = BigDecimal.valueOf(valeurDouble)
                        .setScale(2, RoundingMode.HALF_UP)
                    // Pour les non-monétaires, on garde la valeur exacte * 100 seulement pour la précision interne
                    (bigDecimal.toDouble() * 100).toLong()
                } catch (e: NumberFormatException) {
                    0L
                }
            }

            onMontantChange(valeurLong)
        }

        // Affichage du texte EXACT comme Flutter
        val montantAffiche = texteActuel

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
                    verticalAlignment = Alignment.CenterVertically // ✅ CORRECTION: Centrer le bouton backspace
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
                            overflow = TextOverflow.Ellipsis,
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

                    // Bouton Backspace avec couleurs du thème - MAINTENANT BIEN CENTRÉ
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
                    for (i in 1..3) {
                        ClavierToucheFlutter(
                            texte = i.toString(),
                            onPressed = { gererToucheNumerique(i.toString()) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rangée 4-5-6 (COMPLÈTE)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 4..6) {
                        ClavierToucheFlutter(
                            texte = i.toString(),
                            onPressed = { gererToucheNumerique(i.toString()) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rangée 7-8-9 (COMPLÈTE)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 7..9) {
                        ClavierToucheFlutter(
                            texte = i.toString(),
                            onPressed = { gererToucheNumerique(i.toString()) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rangée finale : - - 0 - ✓ (comme votre image)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Bouton moins (si mode argent) ou point (si mode normal)
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
                        onPressed = fermerAvecRestoration,
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

    @Preview(
        showBackground = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES,
        name = "Clavier Numérique"
    )
    @Composable
    fun ClavierNumeriquePreview() {
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
