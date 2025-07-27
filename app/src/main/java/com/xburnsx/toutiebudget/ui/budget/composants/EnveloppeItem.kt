// Définit le package auquel ce fichier appartient. C'est l'organisation de base des fichiers dans un projet Android.
package com.xburnsx.toutiebudget.ui.budget.composants

// Importations des bibliothèques nécessaires pour créer des interfaces utilisateur avec Jetpack Compose.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Calendar

/**
 * Fonction d'extension pour la classe String.
 * Elle convertit une chaîne de caractères représentant une couleur hexadécimale (ex: "#FF5733") en un objet Color de Compose.
 * Utilise un bloc try-catch pour éviter que l'application ne plante si la chaîne n'est pas une couleur valide,
 * et retourne Color.Gray par défaut en cas d'erreur.
 */
fun String?.toColor(): Color {
    return try {
        // Vérification de sécurité pour éviter NullPointerException
        if (this.isNullOrBlank()) {
            return Color.Gray
        }
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
        montant > 0 -> enveloppe.couleurProvenance.toColor()
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
                // SUPPRIMÉ verticalArrangement = Arrangement.spacedBy((-2).dp) - plus besoin !
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
                        lineHeight = 15.sp, // SUPPRIME l'espace vertical interne
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
                            fontSize = 14.sp, // Taille réduite
                            lineHeight = 14.sp // SUPPRIME l'espace vertical interne
                        )
                    }
                }

                // Afficher le montant dépensé sur toutes les enveloppes (pas seulement celles avec objectif)
                if (enveloppe.depense > 0) {
                    Spacer(modifier = Modifier.height(2.dp)) // ESPACE CONTRÔLÉ entre nom et dépensé

                    // Afficher le montant dépensé
                    Text(
                        text = "Dépensé: ${formatteurMonetaire.format(enveloppe.depense)}",
                        color = Color(0xFFFF6B6B), // Couleur rouge/orange pour les dépenses
                        fontSize = 11.sp,
                        lineHeight = 11.sp, // SUPPRIME l'espace vertical interne
                        fontWeight = FontWeight.Medium
                    )
                }

                // Afficher le texte de l'objectif directement sous le nom si un objectif est défini
                if (objectif > 0) {
                    Spacer(modifier = Modifier.height(2.dp)) // ESPACE CONTRÔLÉ entre dépensé et objectif

                    // Row pour le texte de l'objectif et le pourcentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Texte de l'objectif adapté selon le type
                        val texteObjectif = when (enveloppe.typeObjectif) {
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Echeance -> {
                                val dateFormatee = enveloppe.dateObjectif?.toDateFormatee()
                                val dateTexte = dateFormatee?.toStringCourt() ?: "date limite"
                                if (enveloppe.solde >= objectif) {
                                    "Objectif atteint pour le $dateTexte"
                                } else {
                                    "Objectif: ${formatteurMonetaire.format(objectif)} pour le $dateTexte"
                                }
                            }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel -> {
                                // Calculer la date de fin de l'objectif annuel (dateObjectif + 12 mois)
                                val dateFinAnnuel = enveloppe.dateObjectif?.let { dateString ->
                                    try {
                                        val dateDebut = when {
                                            dateString.contains("T") -> {
                                                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                                                isoFormat.parse(dateString)
                                            }
                                            dateString.contains(" ") -> {
                                                val spaceFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                                                spaceFormat.parse(dateString)
                                            }
                                            dateString.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                                                val simpleFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                                simpleFormat.parse(dateString)
                                            }
                                            else -> null
                                        }

                                        if (dateDebut != null) {
                                            val calendar = java.util.Calendar.getInstance()
                                            calendar.time = dateDebut
                                            calendar.add(java.util.Calendar.MONTH, 12) // + 12 mois
                                            // Formater la date de fin
                                            val dateFormatee = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
                                            dateFormatee.toDateFormatee()
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                val dateTexte = dateFinAnnuel?.toStringCourt() ?: "fin d'année"

                                if (enveloppe.solde >= objectif) {
                                    "Objectif annuel atteint: ${formatteurMonetaire.format(objectif)}"
                                } else {
                                    "Objectif annuel: ${formatteurMonetaire.format(objectif)} jusqu'au $dateTexte"
                                }
                            }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                val dateFormatee = enveloppe.dateObjectif?.toDateFormatee()
                                val dateTexte = dateFormatee?.let { "${it.jourTexte} ${it.mois}" } ?: "fin du mois"
                                if (enveloppe.solde >= objectif) {
                                    "Objectif mensuel atteint: ${formatteurMonetaire.format(objectif)}"
                                } else {
                                    "Objectif mensuel: ${formatteurMonetaire.format(objectif)} pour le $dateTexte"
                                }
                            }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire -> {
                                val dateFormatee = enveloppe.dateObjectif?.toDateFormatee()
                                val dateTexte = dateFormatee?.toStringCourt() ?: "date limite"
                                if (enveloppe.solde >= objectif) {
                                    "Objectif de période atteint: ${formatteurMonetaire.format(objectif)}"
                                } else {
                                    "Objectif / 2 sem: ${formatteurMonetaire.format(objectif)} pour le $dateTexte"
                                }
                            }
                            else -> {
                                // Pour TypeObjectif.Aucun et autres cas non gérés
                                "Objectif: ${formatteurMonetaire.format(objectif)}"
                            }
                        }

                        Text(
                            text = texteObjectif,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 12.sp, // SUPPRIME l'espace vertical interne
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
                            fontSize = 12.sp,
                            lineHeight = 12.sp // SUPPRIME l'espace vertical interne
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp)) // ESPACE CONTRÔLÉ entre objectif et barre

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

                    // AJOUT: Afficher le versement recommandé SEULEMENT pour certains types d'objectifs
                    if (enveloppe.versementRecommande > 0 && enveloppe.typeObjectif != com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Suggéré: ${formatteurMonetaire.format(enveloppe.versementRecommande)}",
                            color = Color(0xFF82DD86), // Vert clair pour la suggestion
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.End) // Aligner à droite
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
            // --- EXEMPLES DES DIFFÉRENTS TYPES D'OBJECTIFS ---

            // 1. OBJECTIF ANNUEL - En cours
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "1",
                    nom = "🏠 Objectif Annuel",
                    solde = 250.0,
                    depense = 0.0,
                    objectif = 1200.0,
                    couleurProvenance = "#E91E63", // rose
                    statutObjectif = StatutObjectif.JAUNE,
                    versementRecommande = 95.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel
                )
            )

            // 2. OBJECTIF MENSUEL - En cours
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "2",
                    nom = "🍔 Objectif Mensuel",
                    solde = 300.0,
                    depense = 50.0,
                    objectif = 500.0,
                    couleurProvenance = "#2196F3", // bleu
                    statutObjectif = StatutObjectif.JAUNE,
                    versementRecommande = 0.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel
                )
            )

            // 3. OBJECTIF ÉCHÉANCE - En cours
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "3",
                    nom = "🚗 Objectif Échéance",
                    solde = 800.0,
                    depense = 0.0,
                    objectif = 2000.0,
                    couleurProvenance = "#FF9800", // orange
                    statutObjectif = StatutObjectif.JAUNE,
                    versementRecommande = 120.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Echeance,
                    dateObjectif = "15"
                )
            )

            // 4. OBJECTIF BIHEBDOMADAIRE - En cours
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "4",
                    nom = "💼 Objectif Bihebdomadaire",
                    solde = 150.0,
                    depense = 25.0,
                    objectif = 300.0,
                    couleurProvenance = "#4CAF50", // vert
                    statutObjectif = StatutObjectif.JAUNE,
                    versementRecommande = 75.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire
                )
            )

            // 5. OBJECTIF ANNUEL - Atteint
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "5",
                    nom = "🎯 Annuel Atteint ✓",
                    solde = 1200.0,
                    depense = 0.0,
                    objectif = 1200.0,
                    couleurProvenance = "#9C27B0", // violet
                    statutObjectif = StatutObjectif.VERT,
                    versementRecommande = 0.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel
                )
            )

            // 6. OBJECTIF MENSUEL - Atteint
            EnveloppeItem(
                enveloppe = EnveloppeUi(
                    id = "6",
                    nom = "💳 Mensuel Atteint ✓",
                    solde = 500.0,
                    depense = 100.0,
                    objectif = 500.0,
                    couleurProvenance = "#607D8B", // bleu-gris
                    statutObjectif = StatutObjectif.VERT,
                    versementRecommande = 0.0,
                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel
                )
            )
        }
    }
}

/**
 * Classe pour représenter une date formatée avec jour, mois et année
 */
data class DateFormatee(
    val jour: Int,
    val mois: String,
    val annee: Int,
    val jourTexte: String // ex: "15", "1er", "2e", etc.
) {
    fun toStringCourt(): String = "$jourTexte $mois"
    fun toStringComplet(): String = "$jourTexte $mois $annee"
}

/**
 * Fonction d'extension pour convertir une chaîne de date en DateFormatee
 */
fun String?.toDateFormatee(): DateFormatee? {
    return try {
        if (this.isNullOrBlank()) return null

        // Parser la date depuis différents formats possibles
        val date = when {
            this.contains("T") -> {
                // Format ISO complet avec T (ex: "2025-08-09T00:00:00.000Z")
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                isoFormat.parse(this)
            }
            this.contains(" ") -> {
                // Format avec espace (ex: "2025-08-09 00:00:00.000Z")
                val spaceFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.getDefault())
                spaceFormat.parse(this)
            }
            this.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                // Format date simple (ex: "2025-08-09")
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                simpleFormat.parse(this)
            }
            else -> null
        }

        if (date == null) return null

        val calendar = Calendar.getInstance()
        calendar.time = date

        val jour = calendar.get(Calendar.DAY_OF_MONTH)
        val moisNum = calendar.get(Calendar.MONTH)
        val annee = calendar.get(Calendar.YEAR)

        // Noms des mois en français
        val nomsMois = arrayOf(
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre"
        )

        // Formatage du jour avec ordinal français
        val jourTexte = when (jour) {
            1 -> "1er"
            else -> "${jour}"
        }

        DateFormatee(
            jour = jour,
            mois = nomsMois[moisNum],
            annee = annee,
            jourTexte = jourTexte
        )
    } catch (e: Exception) {
        println("[ERROR] Erreur parsing date: $this - ${e.message}")
        null
    }
}
