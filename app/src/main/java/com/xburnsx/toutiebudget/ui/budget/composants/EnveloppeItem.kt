// Définit le package auquel ce fichier appartient. C'est l'organisation de base des fichiers dans un projet Android.
package com.xburnsx.toutiebudget.ui.budget.composants

// Importations des bibliothèques nécessaires pour créer des interfaces utilisateur avec Jetpack Compose.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.graphics.toColorInt
// import android.util.Log

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
        Color(this.toColorInt())
    } catch (_: Exception) {
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
    // Récupère le solde actuel de l'enveloppe.
    // 🎯 UTILISER LA NORMALISATION GLOBALE POUR UNE PRÉCISION COHÉRENTE
    val montant = MoneyFormatter.normalizeAmount(enveloppe.solde)
    // Récupère le montant de l'objectif, s'il y en a un.
    val objectif = enveloppe.objectif
    
    // 🎨 SOLUTION : Utiliser couleurObjectif pour les barres (toujours garder couleur du compte)
    // couleurProvenance reste pour la bulle (reset à null si solde = 0)
    val couleurCompteSource = enveloppe.couleurObjectif ?: "#4CAF50" // Couleur par défaut si pas de couleur objectif
    
    // LOGS POUR DÉBOGUER LA COULEUR DU COMPTE SOURCE

    // --- LOGIQUE POUR DÉTERMINER SI L'OBJECTIF EST ATTEINT ---
    val estObjectifAtteint = when (enveloppe.typeObjectif) {
        // Pour les objectifs de dépense : vérifier si depense >= objectif (pas alloue + depense !)
        com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.depense >= objectif
        // Pour les objectifs d'épargne/accumulation : vérifier si alloueCumulatif >= objectif
        else -> enveloppe.alloueCumulatif >= objectif
    }
    
    // LOGS POUR DÉBOGUER LA DÉTECTION DE L'OBJECTIF

    // --- LOGIQUE POUR LA BULLE DE MONTANT ---
    // Détermine la couleur de fond de la bulle qui affiche le solde.
    val couleurBulle = when {
        // Rouge pour soldes négatifs (seulement si vraiment négatif, pas juste proche de zéro)
        enveloppe.solde < -0.001 -> Color(0xFFEF4444) // Rouge pour négatif
        // Si le solde est positif et une couleur est définie, on utilise cette couleur.
        enveloppe.solde > 0.001 -> enveloppe.couleurProvenance.toColor()
        // Sinon (solde à zéro ou très proche de zéro), on utilise un gris foncé.
        else -> Color(0xFF444444)
    }

    // Détermine la couleur du texte dans la bulle.
    val couleurTexteBulle = when {
        enveloppe.solde < -0.001 -> Color.White // Texte blanc pour le rouge
        enveloppe.solde > 0.001 -> Color.White // Texte blanc pour les couleurs
        else -> Color.LightGray // Texte gris clair pour le gris
    }

    // --- LOGIQUE POUR LA BARRE LATÉRALE DE STATUT ---
    // Détermine la couleur de la barre verticale à droite de la carte, indiquant le statut global.
    // MÊME LOGIQUE QUE LA BARRE DE PROGRESSION pour la cohérence
    val couleurStatut = when {
        // Si pas d'objectif (objectif = 0 ou typeObjectif = Aucun) et solde = 0 : Gris
        (objectif <= 0 || enveloppe.typeObjectif == com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun) && enveloppe.solde <= 0.001 -> Color.Gray
        // Si pas d'objectif mais solde > 0 : JAUNE pour indiquer l'activité
        (objectif <= 0 || enveloppe.typeObjectif == com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun) && enveloppe.solde > 0.001 -> Color(0xFFFFC107)
        // Objectif atteint : Vert ou couleur du compte source si dépensé
        estObjectifAtteint -> {
            when (enveloppe.typeObjectif) {
                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                    // LOGS POUR DÉBOGUER LA COULEUR DU COMPTE
                    
                    if (enveloppe.depense >= objectif) {
                        val couleurCompte = couleurCompteSource.toColor()
                        couleurCompte
                    } else {
                        Color(0xFF4CAF50) // Vert si atteint par allocation
                    }
                }
                else -> {
                    Color(0xFF4CAF50) // Vert pour les objectifs d'épargne atteints
                }
            }
        }
        // Vert si objectif atteint (même logique que barre de progression)
        when (enveloppe.typeObjectif) {
            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue >= objectif
            else -> enveloppe.alloueCumulatif >= objectif
        } -> Color(0xFF4CAF50)
        // Jaune si en cours (même logique que barre de progression)
        when (enveloppe.typeObjectif) {
            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue > 0.001
            else -> enveloppe.alloueCumulatif > 0.001
        } -> Color(0xFFFFC107)
        // Gris si pas d'argent
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
                            text = MoneyFormatter.formatAmount(montant),
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
                        text = "Dépensé: ${MoneyFormatter.formatAmount(enveloppe.depense)}",
                        color = Color(0xFFFF6B6B), // Couleur rouge/orange pour les dépenses
                        fontSize = 11.sp,
                        lineHeight = 11.sp, // SUPPRIME l'espace vertical interne
                        fontWeight = FontWeight.Medium
                    )
                }

                // GN3 : mois passé déjà reporté ; son vrai solde de fin de mois reste visible
                if (kotlin.math.abs(enveloppe.montantReporte) >= 0.005) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Fin de mois : ${MoneyFormatter.formatAmount(enveloppe.solde + enveloppe.montantReporte)}" +
                            " (reporté au mois suivant : ${MoneyFormatter.formatAmount(enveloppe.montantReporte)})",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 11.sp
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
                                 if (enveloppe.alloueCumulatif >= objectif) { // ← MODIFIÉ : alloueCumulatif
                                     "Objectif atteint pour le $dateTexte"
                                 } else {
                                     "Objectif: ${MoneyFormatter.formatAmount(objectif)} pour le ${dateFormatee?.toStringCourtAvecAnnee() ?: "date limite"}"
                                 }
                             }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel -> {
                                                                                                  // dateObjectif est déjà la date de fin de l'objectif annuel
                                 val dateFinAnnuel = enveloppe.dateObjectif?.let { dateString ->
                                     try {
                                         
                                         val dateFin = when {
                                             dateString.contains("T") -> {
                                                 val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                                                 isoFormat.parse(dateString)
                                             }
                                             dateString.contains(" ") -> {
                                                 // Essayer d'abord le format avec Z
                                                 try {
                                                     val spaceFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.getDefault())
                                                     spaceFormat.parse(dateString)
                                                 } catch (_: Exception) {
                                                     // Si ça échoue, essayer le format sans Z
                                                     try {
                                                         val spaceFormatSansZ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                                         spaceFormatSansZ.parse(dateString)
                                                     } catch (_: Exception) {
                                                         null
                                                     }
                                                 }
                                             }
                                             dateString.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                                                 val simpleFormat =
                                                     SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                 simpleFormat.parse(dateString)
                                             }
                                             else -> null
                                         }

                                                                                  if (dateFin != null) {
                                              // Utiliser directement la date de fin (pas besoin d'ajouter 1 an)
                                              val dateFormatee = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateFin)
                                              dateFormatee.toDateFormatee()
                                          } else null
                                     } catch (_: Exception) {
                                         null
                                     }
                                 }

                                if (enveloppe.alloueCumulatif >= objectif) { // ← MODIFIÉ : alloueCumulatif
                                     "Objectif annuel atteint: ${MoneyFormatter.formatAmount(objectif)} pour le ${dateFinAnnuel?.toStringCourtAvecAnnee() ?: "fin d'année"}"
                                 } else {
                                     "Objectif annuel: ${MoneyFormatter.formatAmount(objectif)} jusqu'au ${dateFinAnnuel?.toStringCourtAvecAnnee() ?: "fin d'année"}"
                                 }
                            }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                val dateTexte = when {
                                    // Si on a stocké juste le jour (ex: "10"), on l'affiche tel quel
                                    enveloppe.dateObjectif?.matches(Regex("\\d{1,2}")) == true -> "le ${enveloppe.dateObjectif}"
                                    else -> {
                                        val df = enveloppe.dateObjectif?.toDateFormatee()
                                        df?.toStringCourt() ?: "fin du mois"
                                    }
                                }
                                if (enveloppe.alloueCumulatif >= objectif) {
                                    "Objectif mensuel atteint: ${MoneyFormatter.formatAmount(objectif)} pour $dateTexte"
                                } else {
                                    "${MoneyFormatter.formatAmount(objectif)} pour $dateTexte"
                                }
                            }
                            com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire -> {
                                val dateFormatee = enveloppe.dateObjectif?.toDateFormatee()
                                val dateTexte = dateFormatee?.toStringCourt() ?: "date limite"
                                if (enveloppe.alloueCumulatif >= objectif) { // ← MODIFIÉ : alloueCumulatif
                                    "Objectif de période atteint: ${MoneyFormatter.formatAmount(objectif)}"
                                } else {
                                    "Objectif / 2 sem: ${MoneyFormatter.formatAmount(objectif)} pour le $dateTexte"
                                }
                            }
                            else -> {
                                // Pour TypeObjectif.Aucun et autres cas non gérés
                                "Objectif: ${MoneyFormatter.formatAmount(objectif)}"
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
                        val progression = if (estObjectifAtteint) {
                            1.0f
                        } else {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> (enveloppe.alloue / objectif).coerceIn(0.0, 1.0).toFloat()
                                else -> (enveloppe.alloueCumulatif / objectif).coerceIn(0.0, 1.0).toFloat()
                            }
                        }

                                                 // Couleur de la barre de progression
                         val couleurBarreProgression = when {
                             estObjectifAtteint -> {
                                 when (enveloppe.typeObjectif) {
                                     com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                         // LOGS POUR DÉBOGUER LA COULEUR DE LA BARRE DE PROGRESSION
                                         
                                         if (enveloppe.depense >= objectif) {
                                             val couleurCompte = couleurCompteSource.toColor()
                                             couleurCompte
                                         } else {
                                             Color(0xFF4CAF50) // Vert si atteint par allocation
                                         }
                                     }
                                     else -> {
                                         // Barre progression - Objectif d'épargne atteint - Couleur verte
                                         Color(0xFF4CAF50) // Vert pour les objectifs d'épargne atteints
                                     }
                                 }
                             }
                             when (enveloppe.typeObjectif) {
                                 com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue >= objectif
                                 else -> enveloppe.alloueCumulatif >= objectif
                             } -> Color(0xFF4CAF50) // Vert si objectif atteint
                             when (enveloppe.typeObjectif) {
                                 com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue > 0.001
                                 else -> enveloppe.alloueCumulatif > 0.001
                             } -> Color(0xFFFFC107) // Jaune si en cours
                             else -> Color.Gray // Gris si pas d'argent
                         }

                        // Pourcentage à droite
                        val progressionEntiere = (progression * 100).toInt()
                        val texteAffichage = if(estObjectifAtteint) {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                    if (enveloppe.depense >= objectif) "Dépensé ✓" else "Bravo ! ✓"
                                }
                                else -> "Objectif ✓"
                            }
                        } else {
                            if (progressionEntiere < 100) {
                                "$progressionEntiere %"
                            } else {
                                "Meow ! ✓"
                            }
                        }

                        val couleurTexte = if (progressionEntiere == 0 && !estObjectifAtteint) {
                            Color.LightGray
                        } else {
                            couleurBarreProgression
                        }

                        Text(
                            text = texteAffichage,
                            color = couleurTexte,
                            fontWeight = if (estObjectifAtteint) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 12.sp // SUPPRIME l'espace vertical interne
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp)) // ESPACE CONTRÔLÉ entre objectif et barre

                    // Barre de progression directement sous le texte
                    val progressionAnimee by animateFloatAsState(
                        targetValue = if (estObjectifAtteint) 1.0f else {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> (enveloppe.alloue / objectif).coerceIn(0.0, 1.0).toFloat()
                                else -> (enveloppe.alloueCumulatif / objectif).coerceIn(0.0, 1.0).toFloat()
                            }
                        },
                        label = "Animation Barre de Progression"
                    )

                                                                                   val couleurBarre = when {
                          estObjectifAtteint -> {
                              when (enveloppe.typeObjectif) {
                                  com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                      // LOGS POUR DÉBOGUER LA COULEUR DE LA BARRE PRINCIPALE
                                      
                                      if (enveloppe.depense >= objectif) {
                                          val couleurCompte = couleurCompteSource.toColor()
                                          couleurCompte
                                      } else {
                                          Color(0xFF4CAF50) // Vert si atteint par allocation
                                      }
                                  }
                                  else -> {
                                      Color(0xFF4CAF50) // Vert pour les objectifs d'épargne atteints
                                  }
                              }
                          }
                          when (enveloppe.typeObjectif) {
                              com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue >= objectif
                              else -> enveloppe.alloueCumulatif >= objectif
                          } -> Color(0xFF4CAF50) // Vert si objectif atteint
                          when (enveloppe.typeObjectif) {
                              com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> enveloppe.alloue > 0.001
                              else -> enveloppe.alloueCumulatif > 0.001
                          } -> Color(0xFFFFC107) // Jaune si en cours
                          else -> Color.Gray // Gris si pas d'argent
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
                            text = "Suggéré: ${MoneyFormatter.formatAmount(enveloppe.versementRecommande)}",
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
                    alloue = 200.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 800.0, // ← NOUVEAU : Total alloué depuis le début
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
                    alloue = 350.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 400.0, // ← NOUVEAU : Total alloué depuis le début
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
                    alloue = 750.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 1500.0, // ← NOUVEAU : Total alloué depuis le début
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
                    alloue = 175.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 200.0, // ← NOUVEAU : Total alloué depuis le début
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
                    alloue = 0.0, // Exemple d'allocation ce mois (déjà atteint)
                    alloueCumulatif = 1200.0, // ← NOUVEAU : Total alloué depuis le début (objectif atteint)
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
                    alloue = 100.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 600.0, // ← NOUVEAU : Total alloué depuis le début (objectif dépassé)
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
    fun toStringCourtAvecAnnee(): String = "$jourTexte $mois $annee"
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
                 // Essayer d'abord le format avec Z
                 try {
                     val spaceFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.getDefault())
                     spaceFormat.parse(this)
                 } catch (_: Exception) {
                     // Si ça échoue, essayer le format sans Z
                     try {
                         val spaceFormatSansZ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                         spaceFormatSansZ.parse(this)
                     } catch (_: Exception) {
                         null
                     }
                 }
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
    } catch (_: Exception) {
        null
    }
}
