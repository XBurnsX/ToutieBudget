// Définit le package auquel ce fichier appartient. C'est l'organisation de base des fichiers dans un projet Android.
package com.xburnsx.toutiebudget.ui.budget.composants

// Importations des bibliothèques nécessaires pour créer des interfaces utilisateur avec Jetpack Compose.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.xburnsx.toutiebudget.ui.budget.composants.StyleBudget.eclaircir
import kotlinx.coroutines.launch
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
fun EnveloppeItem(enveloppe: EnveloppeUi, onClick: (() -> Unit)? = null) {
    val montant = MoneyFormatter.normalizeAmount(enveloppe.solde)
    val objectif = enveloppe.objectif
    val estObjectifAtteint = StyleBudget.objectifAtteint(enveloppe)

    // --- Bulle : couleur du compte d'où vient l'argent, rouge si négatif, gris si vide ---
    val couleurBulleCible = when {
        enveloppe.solde < -0.001 -> StyleBudget.Rouge
        enveloppe.solde > 0.001 -> enveloppe.couleurProvenance.toColor()
        else -> StyleBudget.BulleVide
    }
    val bulleVide = kotlin.math.abs(enveloppe.solde) <= 0.001
    val couleurBulle by animateColorAsState(couleurBulleCible, tween(400), label = "couleurBulle")
    val couleurTexteBulle = if (bulleVide) StyleBudget.TexteSecondaire else Color.White

    // --- Couleurs d'état : une seule règle pour la barre latérale et la barre de progression ---
    val couleurStatut by animateColorAsState(
        StyleBudget.couleurEtat(enveloppe, pourBarre = false), tween(400), label = "couleurStatut"
    )
    val couleurBarre by animateColorAsState(
        StyleBudget.couleurEtat(enveloppe, pourBarre = true), tween(400), label = "couleurBarre"
    )


    // --- Le montant défile vers sa nouvelle valeur et la bulle pulse une fois ---
    val montantAnime = remember { Animatable(montant.toFloat()) }
    val echelleBulle = remember { Animatable(1f) }
    val premierAffichage = remember { booleanArrayOf(true) }
    LaunchedEffect(montant) {
        if (premierAffichage[0]) {
            premierAffichage[0] = false
            montantAnime.snapTo(montant.toFloat())
            return@LaunchedEffect
        }
        launch {
            echelleBulle.animateTo(1.12f, tween(140))
            echelleBulle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
        montantAnime.animateTo(montant.toFloat(), tween(550, easing = FastOutSlowInEasing))
    }
    // La valeur exacte s'affiche dès la fin de l'animation (jamais d'arrondi de Float qui reste)
    val montantAffiche = if (montantAnime.isRunning) montantAnime.value.toDouble() else montant

    // --- Appui : la carte s'enfonce légèrement ---
    val interaction = remember { MutableInteractionSource() }
    val presse by interaction.collectIsPressedAsState()
    val echelleCarte by animateFloatAsState(
        if (presse) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "appui"
    )

    val cardShape = RoundedCornerShape(20.dp)
    val chiffres = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .graphicsLayer {
                scaleX = echelleCarte
                scaleY = echelleCarte
            }
            .shadow(6.dp, cardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(StyleBudget.CarteHaut, StyleBudget.CarteBas)))
            .border(1.dp, StyleBudget.Bordure, cardShape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Nom de l'enveloppe et bulle de montant
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = enveloppe.nom,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    // Bulle : léger dégradé et halo de la couleur du compte
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = echelleBulle.value
                                scaleY = echelleBulle.value
                            }
                            .shadow(
                                if (bulleVide) 0.dp else 6.dp, RoundedCornerShape(50),
                                ambientColor = couleurBulle, spotColor = couleurBulle
                            )
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(couleurBulle.eclaircir(0.18f), couleurBulle)))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = MoneyFormatter.formatAmount(montantAffiche),
                            color = couleurTexteBulle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            style = chiffres
                        )
                    }
                }

                // Montant dépensé (toutes les enveloppes)
                if (enveloppe.depense > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Dépensé: ${MoneyFormatter.formatAmount(enveloppe.depense)}",
                        color = StyleBudget.TexteDepense,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        style = chiffres
                    )
                }

                // GN3 : mois passé déjà reporté ; son vrai solde de fin de mois reste visible
                if (kotlin.math.abs(enveloppe.montantReporte) >= 0.005) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Fin de mois : ${MoneyFormatter.formatAmount(enveloppe.solde + enveloppe.montantReporte)}" +
                            " (reporté au mois suivant : ${MoneyFormatter.formatAmount(enveloppe.montantReporte)})",
                        color = StyleBudget.TexteSecondaire,
                        fontSize = 11.sp,
                        lineHeight = 11.sp
                    )
                }

                if (objectif > 0) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            color = StyleBudget.TexteSecondaire,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.weight(1f)
                        )

                        val progression = if (estObjectifAtteint) 1.0f else {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> (enveloppe.alloue / objectif).coerceIn(0.0, 1.0).toFloat()
                                else -> (enveloppe.alloueCumulatif / objectif).coerceIn(0.0, 1.0).toFloat()
                            }
                        }
                        val progressionEntiere = (progression * 100).toInt()
                        val texteAffichage = if (estObjectifAtteint) {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> {
                                    if (enveloppe.depense >= objectif) "Dépensé ✓" else "Bravo ! ✓"
                                }
                                else -> "Objectif ✓"
                            }
                        } else {
                            if (progressionEntiere < 100) "$progressionEntiere %" else "Meow ! ✓"
                        }

                        Text(
                            text = texteAffichage,
                            color = if (progressionEntiere == 0 && !estObjectifAtteint) StyleBudget.TexteSecondaire else couleurBarre,
                            fontWeight = if (estObjectifAtteint) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            style = chiffres
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Barre de progression : piste sombre, remplissage en dégradé, petit rebond
                    val progressionAnimee by animateFloatAsState(
                        targetValue = if (estObjectifAtteint) 1.0f else {
                            when (enveloppe.typeObjectif) {
                                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel -> (enveloppe.alloue / objectif).coerceIn(0.0, 1.0).toFloat()
                                else -> (enveloppe.alloueCumulatif / objectif).coerceIn(0.0, 1.0).toFloat()
                            }
                        },
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
                        label = "Animation Barre de Progression"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(StyleBudget.Piste)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progressionAnimee.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(couleurBarre.copy(alpha = 0.6f), couleurBarre)))
                        )
                    }

                    // Versement recommandé, seulement pour les objectifs non mensuels
                    if (enveloppe.versementRecommande > 0 && enveloppe.typeObjectif != com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Suggéré: ${MoneyFormatter.formatAmount(enveloppe.versementRecommande)}",
                            color = StyleBudget.TexteSuggestion,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            style = chiffres,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // Barre de statut à droite : pilule arrondie en dégradé
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 10.dp, bottom = 10.dp, end = 8.dp)
                    .width(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(listOf(couleurStatut.eclaircir(0.15f), couleurStatut.copy(alpha = 0.7f)))
                    )
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
