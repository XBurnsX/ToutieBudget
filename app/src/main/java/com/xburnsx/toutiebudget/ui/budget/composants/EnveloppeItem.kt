// Définit le package auquel ce fichier appartient. C'est l'organisation de base des fichiers dans un projet Android.
package com.xburnsx.toutiebudget.ui.budget.composants

// Importations des bibliothèques nécessaires pour créer des interfaces utilisateur avec Jetpack Compose.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import java.text.NumberFormat
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fonction d'extension pour la classe String.
 * Elle convertit une chaîne de caractères représentant une couleur hexadécimale (ex: "#FF5733") en un objet Color de Compose.
 * Utilise un bloc try-catch pour éviter que l'application ne plante si la chaîne n'est pas une couleur valide,
 * et retourne Color.Gray par défaut en cas d'erreur.
 */
fun String.toColor(): Color {
    return try {
        // Tente de parser la chaîne en une couleur Android native, puis la convertit en couleur Compose.
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        // Si le format est invalide (ex: "bleu" au lieu de "#0000FF"), retourne une couleur grise par défaut.
        Color.Gray
    }
}

/**
 * Le composant principal qui représente une seule "enveloppe" budgétaire dans la liste.
 * @param enveloppe L'objet de données contenant toutes les informations sur l'enveloppe à afficher.
 */
@Composable
fun EnveloppeItem(enveloppe: EnveloppeUi) {
    // Crée un formateur de monnaie pour le format canadien-français (ex: 1 234,56 $).
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    // Récupère le solde actuel de l'enveloppe.
    val montant = enveloppe.solde
    // Récupère le montant de l'objectif, s'il y en a un.
    val objectif = enveloppe.objectif

    // --- LOGIQUE POUR LA BULLE DE MONTANT ---
    // Détermine la couleur de fond de la bulle qui affiche le solde.
    val couleurBulle = when {
        // CORRECTION: Rouge pour soldes négatifs
        montant < 0 -> Color(0xFFEF4444) // Rouge pour négatif
        // Si le solde est positif et une couleur est définie, on utilise cette couleur.
        montant > 0 && enveloppe.couleurProvenance != null -> enveloppe.couleurProvenance.toColor()
        // Sinon (solde à zéro ou pas de couleur définie), on utilise un gris foncé.
        else -> Color(0xFF444444)
    }

    // Détermine la couleur du texte dans la bulle.
    val couleurTexteBulle = when {
        montant < 0 -> Color.White // Texte blanc pour le rouge
        montant > 0 -> Color.White // Texte blanc pour les couleurs
        else -> Color.LightGray // Texte gris clair pour le gris
    }

    // --- LOGIQUE POUR LA BARRE LATÉRALE DE STATUT ---
    // Détermine la couleur de la barre verticale à droite de la carte, indiquant le statut global.
    val couleurStatut = when {
        // Vert : L'objectif est défini et le solde atteint ou dépasse l'objectif.
        objectif > 0 && enveloppe.solde >= objectif -> Color(0xFF4CAF50)
        // Jaune : L'objectif est défini et il y a de l'argent dans l'enveloppe, mais l'objectif n'est pas encore atteint.
        objectif > 0 && enveloppe.solde > 0 -> Color(0xFFFFC107)
        // Jaune : Pas d'objectif, mais il y a de l'argent dans l'enveloppe.
        objectif <= 0 && montant > 0 -> Color(0xFFFFC107)
        // Gris : Cas par défaut (pas d'objectif et pas d'argent).
        else -> Color.Gray
    }

    // Définit la forme de la carte avec des coins arrondis de 20.dp.
    val cardShape = RoundedCornerShape(20.dp)

    // Le composant Card est le conteneur principal de l'item.
    Card(
        modifier = Modifier
            .fillMaxWidth() // La carte prend toute la largeur de son parent.
            .padding(horizontal = 8.dp, vertical = 6.dp), // Ajoute de l'espace autour de la carte.
        shape = cardShape, // Applique la forme avec coins arrondis définie plus haut.
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Ajoute une légère ombre sous la carte.
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF232323) // Définit la couleur de fond de la carte.
        )
    ) {
        // Row arrange ses enfants (la colonne de texte et la barre de statut) horizontalement.
        Row(
            modifier = Modifier
                .fillMaxWidth() // Prend toute la largeur.
                .height(IntrinsicSize.Min), // La hauteur de la Row sera la hauteur minimale nécessaire pour contenir ses enfants.
            verticalAlignment = Alignment.CenterVertically // Centre les enfants verticalement.
        ) {
            // Column pour la partie principale, avec le nom, le montant et la barre de progression.
            Column(
                modifier = Modifier
                    .weight(1f) // La colonne prend tout l'espace disponible dans la Row (après que la barre de statut ait pris sa largeur fixe).
                    .padding(horizontal = 16.dp, vertical = 12.dp) // Espace intérieur.
            ) {
                // Row pour le nom de l'enveloppe et la bulle de montant.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically // Aligne le nom et la bulle sur la même ligne verticale.
                ) {
                    // Texte affichant le nom de l'enveloppe.
                    Text(
                        text = enveloppe.nom,
                        fontWeight = FontWeight.Bold, // Texte en gras.
                        fontSize = 15.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f) // Le texte prend l'espace restant pour pousser la bulle à droite.
                    )

                    // --- BULLE DE MONTANT (RÉDUITE) ---
                    // Box est utilisé comme conteneur pour la bulle afin de pouvoir lui donner un fond et une forme.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50)) // Découpe la Box en une forme de pilule (coins très arrondis).
                            .background(couleurBulle) // Applique la couleur de fond calculée plus tôt.
                            .padding(horizontal = 12.dp, vertical = 4.dp) // Espace réduit à l'intérieur de la bulle.
                    ) {
                        // Texte affichant le montant formaté en devise.
                        Text(
                            text = formatteurMonetaire.format(montant),
                            color = couleurTexteBulle, // Applique la couleur de texte calculée.
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp // Taille réduite
                        )
                    }
                }

                // Afficher le montant dépensé sur toutes les enveloppes (pas seulement celles avec objectif)
                if (enveloppe.depense > 0) {
                    // Espace entre le nom/bulle et le montant dépensé
                    Spacer(modifier = Modifier.height(4.dp))

                    // Afficher le montant dépensé
                    Text(
                        text = "Dépensé: ${formatteurMonetaire.format(enveloppe.depense)}",
                        color = Color(0xFFFF6B6B), // Couleur rouge/orange pour les dépenses
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Afficher le texte de l'objectif directement sous le nom si un objectif est défini
                if (objectif > 0) {
                    // Espace réduit entre le nom et l'objectif (ou entre dépensé et objectif)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Row pour le texte de l'objectif et le pourcentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Texte de l'objectif avec la date
                        val texteObjectif = if (enveloppe.dateObjectif != null) {
                            "${formatteurMonetaire.format(objectif)} pour le ${enveloppe.dateObjectif}"
                        } else {
                            "Objectif: ${formatteurMonetaire.format(objectif)}"
                        }

                        Text(
                            text = texteObjectif,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )

                        // Calculs pour le pourcentage
                        val estDepenseComplete = enveloppe.depense == objectif
                        val progression = if (estDepenseComplete) {
                            1.0f
                        } else {
                            (enveloppe.solde / objectif).coerceIn(0.0, 1.0).toFloat()
                        }

                        // Couleur de la barre de progression
                        val couleurBarreProgression = when {
                            estDepenseComplete -> enveloppe.couleurProvenance?.toColor() ?: Color(0xFF4CAF50)
                            enveloppe.solde >= objectif -> Color(0xFF4CAF50)
                            enveloppe.solde > 0 -> Color(0xFFFFC107)
                            else -> Color.Gray
                        }

                        // Pourcentage à droite
                        val progressionEntiere = (progression * 100).toInt()
                        val texteAffichage = if(estDepenseComplete) "Dépensé ✓" else "$progressionEntiere %"

                        val couleurTexte = if (progressionEntiere == 0 && !estDepenseComplete) {
                            Color.LightGray
                        } else {
                            couleurBarreProgression
                        }

                        Text(
                            text = texteAffichage,
                            color = couleurTexte,
                            fontWeight = if (estDepenseComplete) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    // Espace réduit entre le texte et la barre de progression
                    Spacer(modifier = Modifier.height(6.dp))

                    // Barre de progression directement sous le texte
                    val progressionAnimee by animateFloatAsState(
                        targetValue = if (enveloppe.depense == objectif) 1.0f else (enveloppe.solde / objectif).coerceIn(0.0, 1.0).toFloat(),
                        label = "Animation Barre de Progression"
                    )

                    val couleurBarre = when {
                        enveloppe.depense == objectif -> enveloppe.couleurProvenance?.toColor() ?: Color(0xFF4CAF50)
                        enveloppe.solde >= objectif -> Color(0xFF4CAF50)
                        enveloppe.solde > 0 -> Color(0xFFFFC107)
                        else -> Color.Gray
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF333333))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progressionAnimee)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(couleurBarre)
                        )
                    }
                }
            }

            // Box pour la barre de statut verticale à droite.
            Box(
                modifier = Modifier
                    .fillMaxHeight() // Remplit toute la hauteur de la carte.
                    .width(8.dp) // Largeur fixe.
                    .background(couleurStatut) // Applique la couleur de statut calculée au début.
            )
        }
    }
}

/**
 * Un composant privé et réutilisable pour afficher la barre de progression,
 * le montant de l'objectif et le pourcentage d'accomplissement.
 * @param progression La progression de 0.0f à 1.0f.
 * @param objectif Le montant de l'objectif, déjà formaté en chaîne de caractères.
 * @param estDepenseComplete Booléen pour savoir si l'objectif est considéré comme totalement dépensé.
 * @param couleurBarre La couleur à utiliser pour la barre de progression et le texte du pourcentage.
 */
@Composable
private fun ProgressBarreObjectif(
    progression: Float,
    objectif: String,
    estDepenseComplete: Boolean,
    couleurBarre: Color,
    dateObjectif: String? // Changé de LocalDate? à String?
) {
    // Crée une valeur de type Float qui s'animera automatiquement vers sa `targetValue`.
    // Quand `progression` change, la barre grandira ou rétrécira avec une animation fluide.
    val progressionAnimee by animateFloatAsState(
        targetValue = progression,
        label = "Animation Barre de Progression" // Libellé pour l'outil d'inspection de Compose.
    )

    // Column arrange le texte et la barre de progression verticalement.
    Column {
        // Row pour le pourcentage seulement (le texte d'objectif est maintenant affiché ailleurs)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End // Aligner le pourcentage à droite
        ) {

            // Calcule le pourcentage entier (ex: 0.75 -> 75).
            val progressionEntiere = (progression * 100).toInt()
            // Détermine le texte à afficher : "Dépensé ✓" si c'est le cas, sinon le pourcentage.
            val texteAffichage = if(estDepenseComplete) "Dépensé ✓" else "$progressionEntiere %"

            // Détermine la couleur du texte du pourcentage.
            val couleurTexte = if (progressionEntiere == 0 && !estDepenseComplete) {
                // Gris clair si la progression est à 0.
                Color.LightGray
            } else {
                // Sinon, la même couleur que la barre de progression.
                couleurBarre
            }

            // Texte affichant le pourcentage ou le statut "Dépensé".
            Text(
                text = texteAffichage,
                color = couleurTexte,
                fontWeight = if (estDepenseComplete) FontWeight.Bold else FontWeight.Medium, // Gras si dépensé.
                fontSize = 14.sp
            )
        }
        // Espace entre le texte et la barre de progression.
        Spacer(modifier = Modifier.height(8.dp))
        // Conteneur pour la barre de progression (fond + progression).
        Box(
            modifier = Modifier
                .fillMaxWidth() // Prend toute la largeur.
                .height(8.dp)   // Hauteur fixe de la barre.
                .clip(RoundedCornerShape(50)) // Coins arrondis pour un aspect de pilule.
                .background(Color(0xFF333333)) // Couleur de fond (la partie "vide" de la barre).
        ) {
            // La barre de progression colorée elle-même.
            Box(
                modifier = Modifier
                    // Sa largeur est une fraction de la largeur totale, contrôlée par la valeur animée.
                    .fillMaxWidth(fraction = progressionAnimee)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)) // Coins arrondis également.
                    .background(couleurBarre) // La couleur vive de la progression.
            )
        }
    }
}


/**
 * Fonction d'aperçu pour Android Studio.
 * Elle n'est pas utilisée dans l'application finale mais permet de visualiser le composant `EnveloppeItem`
 * avec différentes données sans avoir à lancer l'application sur un émulateur ou un appareil.
 */
@Preview(showBackground = true) // Affiche un fond derrière l'aperçu.
@Composable
fun ApercuEnveloppeItem() {
    // Un Box avec un fond sombre pour simuler le thème de l'application.
    Box(modifier = Modifier.background(Color(0xFF121212))) {
        // Une colonne pour afficher plusieurs exemples d'EnveloppeItem les uns sous les autres.
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // --- EXEMPLES ---
            // Chaque appel à EnveloppeItem teste un scénario différent.

            // 1. Objectif atteint mais pas encore dépensé. Devrait avoir une barre de statut verte.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "1",
                    nom = "🏠 Objectif Atteint (Vert)",
                    solde = 750.0,
                    depense = 0.0,
                    objectif = 750.0,
                    couleurProvenance = "#E91E63", // rose
                    statutObjectif = StatutObjectif.VERT
                )
            )
            // 2. Objectif en cours. Devrait avoir une barre de statut jaune.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "3",
                    nom = "🚗 Objectif en cours (Jaune)",
                    solde = 85.50,
                    depense = 10.0,
                    objectif = 200.0,
                    couleurProvenance = "#2196F3", // bleu
                    statutObjectif = StatutObjectif.JAUNE
                )
            )
            // 3. Objectif atteint et entièrement dépensé. La barre de progression utilise la couleur du compte.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "4",
                    nom = "💳 Dépensé (Couleur Compte)",
                    solde = 0.0,
                    depense = 50.0,
                    objectif = 50.0,
                    couleurProvenance = "#E91E63", // rose
                    statutObjectif = StatutObjectif.JAUNE
                )
            )
            // 4. Enveloppe sans objectif mais avec de l'argent. Barre de statut jaune.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "5",
                    nom = "🎁 Sans objectif / Argent (Jaune)",
                    solde = 120.0,
                    depense = 0.0,
                    objectif = 0.0,
                    couleurProvenance = "#FF9800", // orange
                    statutObjectif = StatutObjectif.VERT
                )
            )
            // 5. Enveloppe vide et sans objectif. Barre de statut grise.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "2",
                    nom = "🏡 Sans objectif / Rien (Gris)",
                    solde = 0.0,
                    depense = 34.23,
                    objectif = 0.0,
                    couleurProvenance = null,
                    statutObjectif = StatutObjectif.GRIS
                )
            )
            // 6. Enveloppe avec objectif mais sans argent. Barre de statut grise.
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "6",
                    nom = "🏡 objectif / Rien (Gris)",
                    solde = -10.0,
                    depense = 0.0,
                    objectif = 50.0,
                    couleurProvenance = null,
                    statutObjectif = StatutObjectif.GRIS
                )
            )
        }
    }
}