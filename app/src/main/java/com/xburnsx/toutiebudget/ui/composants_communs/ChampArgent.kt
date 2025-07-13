// chemin/simule: /ui/composants_communs/ChampArgent.kt
// Dépendances: Jetpack Compose, Material3, ClavierNumerique

package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.NumberFormat
import java.util.Locale
import com.xburnsx.toutiebudget.ui.composants_communs.BlocSaisieMontant

/**
 * Composant universel pour tous les champs d'argent dans l'application.
 * Affiche le montant formaté et ouvre le clavier numérique au clic.
 * 
 * @param montant Le montant actuel en centimes (ex: 1234 = 12.34$)
 * @param onMontantChange Callback appelé quand le montant change
 * @param libelle Le texte d'étiquette à afficher
 * @param icone L'icône à afficher (optionnel)
 * @param estObligatoire Si true, affiche un indicateur visuel d'obligation
 * @param couleurFond Couleur de fond personnalisée (optionnel)
 * @param tailleMontant Taille du texte du montant (optionnel)
 * @param couleurMontant Couleur du texte du montant quand il y a une valeur (optionnel)
 * @param couleurPlaceholder Couleur du texte placeholder quand le montant est 0 (optionnel)
 * @param styleMontant Style de texte personnalisé pour le montant (optionnel)
 * @param modifier Modificateur Compose standard
 */
@Composable
fun ChampArgent(
    montant: Long,
    onMontantChange: (Long) -> Unit,
    libelle: String,
    icone: ImageVector = Icons.Default.AttachMoney,
    estObligatoire: Boolean = false,
    couleurFond: Color? = null,
    tailleMontant: androidx.compose.ui.unit.TextUnit? = null,
    couleurMontant: Color? = null,
    couleurPlaceholder: Color? = null,
    styleMontant: androidx.compose.ui.text.TextStyle? = null,
    modifier: Modifier = Modifier
) {
    // Gestion de l'état d'ouverture du clavier
    var clavierOuvert by remember { mutableStateOf(false) }
    
    // État local pour la saisie en cours (en format texte pour le clavier)
    var saisieEnCours by remember { mutableStateOf("") }
    
    // Formateur monétaire canadien-français
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    
    // Conversion du montant en centimes vers dollars pour affichage
    val montantEnDollars = montant / 100.0
    val montantFormate = formateurMonetaire.format(montantEnDollars)
    
    // Couleur de fond par défaut ou personnalisée
    val couleurContenant = couleurFond ?: Color.White.copy(alpha = 0.05f)
    
    // Couleurs personnalisables pour le montant
    val couleurMontantActuelle = couleurMontant ?: MaterialTheme.colorScheme.primary
    val couleurPlaceholderActuelle = couleurPlaceholder ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    
    // Style de texte pour le montant
    val styleTexteBase = styleMontant ?: if (montant == 0L) 
        MaterialTheme.typography.bodyLarge
    else 
        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    
    // Application de la taille personnalisée si fournie
    val styleTexteFinal = if (tailleMontant != null) {
        styleTexteBase.copy(fontSize = tailleMontant)
    } else {
        styleTexteBase
    }

    Column(modifier = modifier) {
        // Étiquette avec indicateur d'obligation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (estObligatoire) {
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Zone cliquable principale du champ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(couleurContenant)
                .clickable { 
                    // Initialiser la saisie avec le montant actuel
                    saisieEnCours = if (montant == 0L) "" else montant.toString()
                    clavierOuvert = true 
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icône
                Icon(
                    imageVector = icone,
                    contentDescription = libelle,
                    tint = if (montant > 0) couleurMontantActuelle else couleurPlaceholderActuelle,
                    modifier = Modifier.size(24.dp)
                )
                
                // Affichage du montant avec styles personnalisables
                Text(
                    text = if (montant == 0L) "Toucher pour saisir" else montantFormate,
                    style = styleTexteFinal,
                    color = if (montant > 0) couleurMontantActuelle else couleurPlaceholderActuelle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    
    // Dialog avec le clavier numérique
    if (clavierOuvert) {
        Dialog(onDismissRequest = { clavierOuvert = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // En-tête du dialog
                    Text(
                        text = libelle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Affichage du montant en cours de saisie
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = formaterSaisieEnCours(saisieEnCours),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Clavier numérique
                    BlocSaisieMontant(
                        montantInitial = saisieEnCours,
                        onTermine = { nouveauMontant ->
                            saisieEnCours = nouveauMontant
                        },
                        onFermer = {
                            val nouveauMontant = convertirSaisieEnCentimes(saisieEnCours)
                            onMontantChange(nouveauMontant)
                            clavierOuvert = false
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Boutons d'action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bouton Annuler
                        OutlinedButton(
                            onClick = { clavierOuvert = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Annuler")
                        }
                        
                        // Bouton Valider
                        Button(
                            onClick = {
                                val nouveauMontant = convertirSaisieEnCentimes(saisieEnCours)
                                onMontantChange(nouveauMontant)
                                clavierOuvert = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Valider")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gère la saisie d'une touche du clavier numérique.
 * Applique la logique de formatage automatique en centimes.
 */
private fun gererSaisieTouche(saisieActuelle: String, touche: String): String {
    return when (touche) {
        "del" -> {
            // Supprime le dernier caractère
            if (saisieActuelle.isNotEmpty()) {
                saisieActuelle.dropLast(1)
            } else {
                saisieActuelle
            }
        }
        "." -> {
            // Ignore le point décimal car on gère automatiquement les centimes
            saisieActuelle
        }
        else -> {
            // Ajoute le chiffre (limite à 8 chiffres pour éviter les débordements)
            if (saisieActuelle.length < 8 && touche.matches(Regex("\\d"))) {
                saisieActuelle + touche
            } else {
                saisieActuelle
            }
        }
    }
}

/**
 * Formate la saisie en cours pour affichage en temps réel.
 * Exemples: "12" -> "0,12 $", "564" -> "5,64 $", "1234" -> "12,34 $"
 */
private fun formaterSaisieEnCours(saisie: String): String {
    if (saisie.isEmpty()) return "0,00 $"
    
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    val montantEnCentimes = saisie.toLongOrNull() ?: 0L
    val montantEnDollars = montantEnCentimes / 100.0
    
    return formateurMonetaire.format(montantEnDollars)
}

/**
 * Convertit la saisie texte en montant en centimes.
 * Exemples: "12" -> 12L, "564" -> 564L, "1234" -> 1234L
 */
private fun convertirSaisieEnCentimes(saisie: String): Long {
    return saisie.toLongOrNull() ?: 0L
}