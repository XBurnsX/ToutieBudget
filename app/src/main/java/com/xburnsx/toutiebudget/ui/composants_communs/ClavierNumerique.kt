// chemin/simule: /ui/composants_communs/ClavierNumerique.kt
// Dépendances: Jetpack Compose, Material3, Dialog

package com.xburnsx.toutiebudget.ui.composants_communs

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AttachMoney
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * 🎯 COMPOSANT UNIVERSEL POUR TOUS LES CHAMPS DE MONTANT
 * 
 * Avec MON clavier moderne que j'avais créé pour AjoutTransaction !
 * 
 * @param montant Le montant actuel (en centimes si isMoney=true, valeur normale si isMoney=false)
 * @param onMontantChange Callback appelé quand le montant change
 * @param libelle Le texte d'étiquette à afficher
 * @param nomDialog Le titre à afficher dans la dialog du clavier (optionnel, utilise libelle par défaut)
 * @param isMoney Si true, traite comme de l'argent (1234 = 12.34$), si false traite comme valeur normale (12 = 12)
 * @param suffix Suffixe à ajouter après la valeur (ex: "%", " mois", etc.) - ignoré si isMoney=true
 * @param icone L'icône à afficher (optionnel)
 * @param estObligatoire Si true, affiche un indicateur visuel d'obligation
 * @param couleurMontant Couleur du texte du montant (optionnel)
 * @param tailleMontant Taille du texte du montant (optionnel)
 * @param modifier Modificateur Compose standard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampMontantUniversel(
    montant: Long,
    onMontantChange: (Long) -> Unit,
    libelle: String,
    nomDialog: String = libelle, // Valeur par défaut = libelle
    isMoney: Boolean = true,
    suffix: String = "",
    icone: ImageVector = Icons.Default.AttachMoney,
    estObligatoire: Boolean = false,
    couleurMontant: Color? = null,
    tailleMontant: TextUnit? = null,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    
    // Formatage selon le type (argent ou valeur normale)
    val montantAffiche = if (montant > 0) {
        if (isMoney) {
            formateurMonetaire.format(montant / 100.0)
        } else {
            "$montant$suffix"
        }
    } else {
        if (isMoney) "0,00 $" else "0$suffix"
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Étiquette avec indicateur d'obligation
        if (libelle.isNotEmpty()) {
            Text(
                text = if (estObligatoire) "$libelle *" else libelle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // TextField cliquable qui ouvre la dialog
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // Pas d'effet de ripple sur la Box
                ) { showDialog = true }
        ) {
            OutlinedTextField(
                value = montantAffiche,
                onValueChange = { }, // Lecture seule
                readOnly = true,
                enabled = false, // Empêche le focus
                modifier = Modifier.fillMaxWidth(),
                label = { Text(libelle) },
                leadingIcon = if (icone != null) {
                    { Icon(icone, contentDescription = libelle) }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTextColor = couleurMontant ?: MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = tailleMontant ?: 16.sp,
                    color = couleurMontant ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
    
    // Clavier modal qui s'ouvre depuis le bas par-dessus la page
    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false, // Utilise toute la largeur
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            ClavierBottomSheet(
                montantInitial = montant,
                nomDialog = nomDialog,
                isMoney = isMoney,
                suffix = suffix,
                onMontantChange = { nouveauMontant ->
                    onMontantChange(nouveauMontant)
                },
                onFermer = { showDialog = false }
            )
        }
    }
}

/**
 * 🎯 CLAVIER MODAL QUI S'OUVRE DEPUIS LE BAS
 * Nouveau composant qui remplace le Dialog standard par une modale qui glisse depuis le bas
 */
@Composable
private fun ClavierBottomSheet(
    montantInitial: Long,
    nomDialog: String,
    isMoney: Boolean,
    suffix: String,
    onMontantChange: (Long) -> Unit,
    onFermer: () -> Unit
) {
    // Overlay semi-transparent qui couvre tout l'écran
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFermer() }
            .zIndex(10f)
    ) {
        // Le clavier qui apparaît depuis le bas
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it } // Commence depuis le bas de l'écran
            ),
            exit = slideOutVertically(
                targetOffsetY = { it } // Sort vers le bas de l'écran
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ClavierContent(
                montantInitial = montantInitial,
                nomDialog = nomDialog,
                isMoney = isMoney,
                suffix = suffix,
                onMontantChange = onMontantChange,
                onFermer = onFermer
            )
        }
    }
}

/**
 * Contenu du clavier (séparé pour une meilleure organisation)
 */
@Composable
private fun ClavierContent(
    montantInitial: Long,
    nomDialog: String,
    isMoney: Boolean,
    suffix: String,
    onMontantChange: (Long) -> Unit,
    onFermer: () -> Unit
) {
    var montantValeur by remember { mutableStateOf(montantInitial) }
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    val onKeyPress = { key: String ->
        val nouveauMontant = when (key) {
            "del" -> {
                // Supprimer le dernier chiffre (diviser par 10)
                if (montantValeur > 0) montantValeur / 10 else 0L
            }
            "." -> {
                // Pour l'argent, ignorer le point (on travaille en centimes)
                montantValeur
            }
            else -> {
                // Ajouter un chiffre (multiplier par 10 et ajouter)
                val chiffre = key.toLongOrNull() ?: 0L
                val limite = if (isMoney) 999999999L else 999999L
                if (montantValeur < limite) {
                    montantValeur * 10 + chiffre
                } else {
                    montantValeur
                }
            }
        }
        montantValeur = nouveauMontant
        onMontantChange(nouveauMontant)
    }

    // Formatage selon le type pour l'affichage
    val montantAffiche = if (isMoney) {
        formateurMonetaire.format(montantValeur / 100.0)
    } else {
        "$montantValeur$suffix"
    }

    // Card qui s'ouvre depuis le bas avec coins arrondis uniquement en haut
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }, // Empêche la fermeture quand on clique sur le clavier
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E2E2E) // Fond gris foncé
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Petite barre de glissement en haut pour indiquer que c'est un modal
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(2.dp)
                    )
                    .padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Titre personnalisable (comme "Montant de la dépense")
            Text(
                text = nomDialog,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Barre avec le montant utilisant la couleur primaire du thème
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary // 🎨 Couleur primaire du thème
                )
            ) {
                Text(
                    text = montantAffiche,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // VOTRE clavier avec boutons ronds gris !
            VotreClavierOriginal(onKeyPress = onKeyPress)

            Spacer(modifier = Modifier.height(20.dp))

            // Boutons Annuler et Valider (comme votre image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Espacement entre les boutons
            ) {
                // Bouton Annuler
                OutlinedButton(
                    onClick = onFermer,
                    modifier = Modifier.weight(1f), // Poids 1 pour le bouton Annuler
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Annuler")
                }

                // Bouton Valider
                Button(
                    onClick = onFermer,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C) // Rouge comme votre image
                    )
                ) {
                    Text("Valider", color = Color.White)
                }
            }

            // Espacement supplémentaire pour éviter les barres de navigation
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Dialog avec VOTRE clavier original que vous voulez !
 * Affichage en temps réel du montant formaté selon le type (argent ou valeur).
 */
@Composable
private fun ClavierModerneDialog(
    montantInitial: Long,
    nomDialog: String,
    isMoney: Boolean,
    suffix: String,
    onMontantChange: (Long) -> Unit,
    onFermer: () -> Unit
) {
    var montantValeur by remember { mutableStateOf(montantInitial) }
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    
    val onKeyPress = { key: String ->
        val nouveauMontant = when (key) {
            "del" -> {
                // Supprimer le dernier chiffre (diviser par 10)
                if (montantValeur > 0) montantValeur / 10 else 0L
            }
            "." -> {
                // Pour l'argent, ignorer le point (on travaille en centimes)
                montantValeur
            }
            else -> {
                // Ajouter un chiffre (multiplier par 10 et ajouter)
                val chiffre = key.toLongOrNull() ?: 0L
                val limite = if (isMoney) 999999999L else 999999L
                if (montantValeur < limite) {
                    montantValeur * 10 + chiffre
                } else {
                    montantValeur
                }
            }
        }
        montantValeur = nouveauMontant
        onMontantChange(nouveauMontant)
    }

    // Formatage selon le type pour l'affichage
    val montantAffiche = if (isMoney) {
        formateurMonetaire.format(montantValeur / 100.0)
    } else {
        "$montantValeur$suffix"
    }

    // VOTRE DESIGN ORIGINAL !
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E2E2E) // Fond gris foncé comme votre image
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Titre personnalisable (comme "Montant de la dépense")
            Text(
                text = nomDialog,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Barre avec le montant utilisant la couleur primaire du thème
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary // 🎨 Couleur primaire du thème
                )
            ) {
                Text(
                    text = montantAffiche,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp)) // Espacement entre le clavier et les boutons
            // VOTRE clavier avec boutons ronds gris !
            VotreClavierOriginal(onKeyPress = onKeyPress)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Boutons Annuler et Valider (comme votre image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Espacement entre les boutons
            ) {
                // Bouton Annuler
                OutlinedButton(
                    onClick = onFermer,
                    modifier = Modifier.weight(1f), // Poids 1 pour le bouton Annuler
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Annuler")
                }
                
                // Bouton Valider
                Button(
                    onClick = onFermer,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C) // Rouge comme votre image
                    )
                ) {
                    Text("Valider", color = Color.White)
                }
            }
        }
    }
}

/**
 * VOTRE clavier original avec boutons ronds gris comme dans l'image !
 */
@Composable
private fun VotreClavierOriginal(onKeyPress: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    
    val touches = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫") // Utilise le symbole backspace
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        touches.forEach { rangee ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rangee.forEach { touche ->
                    VotreToucheOriginale(
                        texte = touche,
                        onPressed = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // Convertir le symbole backspace en "del"
                            val key = if (touche == "⌫") "del" else touche
                            onKeyPress(key)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * VOTRE touche originale - ronde et grise comme dans l'image !
 */
@Composable
private fun VotreToucheOriginale(
    texte: String,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(60.dp) // Taille comme dans votre image
            .background(
                Color(0xFF4A4A4A), // Gris comme dans votre image
                CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onPressed
            ),
        contentAlignment = Alignment.Center
    ) {
        if (texte == "⌫") {
            Icon(
                Icons.Default.Backspace,
                contentDescription = "Effacer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = texte,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Champ Montant avec VOTRE clavier")
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
                    onMontantChange = {},
                    libelle = "Montant de la dépense",
                    nomDialog = "Montant de la dépense", // Titre personnalisé
                    isMoney = true,
                    estObligatoire = true
                )
                
                ChampMontantUniversel(
                    montant = 12L, // 12 mois
                    onMontantChange = {},
                    libelle = "Durée en mois",
                    nomDialog = "Durée du prêt", // Titre personnalisé
                    isMoney = false,
                    suffix = " mois",
                    icone = Icons.Default.AttachMoney
                )
                
                ChampMontantUniversel(
                    montant = 525L, // 525%
                    onMontantChange = {},
                    libelle = "Taux d'intérêt",
                    nomDialog = "Taux d'intérêt annuel", // Titre personnalisé
                    isMoney = false,
                    suffix = "%",
                    icone = Icons.Default.AttachMoney
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dialog MON Clavier")
@Composable
fun ClavierModerneDialogPreview() {
    ToutieBudgetTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                ClavierModerneDialog(
                    montantInitial = 123456L,
                    nomDialog = "Montant de la dépense",
                    isMoney = true,
                    suffix = "",
                    onMontantChange = {},
                    onFermer = {}
                )
            }
        }
    }
}