package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

/**
 * Palette et règles de couleur de la page Budget, à UN seul endroit.
 * La barre latérale, la barre de progression et le pourcentage lisent tous [couleurEtat] :
 * une enveloppe ne peut plus afficher deux couleurs différentes pour le même état.
 */
object StyleBudget {
    // Fond : le même que le reste de l'app (continuité avec la barre du bas); cartes en léger dégradé neutre
    val Fond = Color(0xFF121212)
    val CarteHaut = Color(0xFF282828)
    val CarteBas = Color(0xFF202020)
    val Bordure = Color.White.copy(alpha = 0.06f)
    val Piste = Color(0xFF333333)

    // États : mêmes repères qu'avant (gris, jaune, vert, rouge), teintes plus douces
    val Vert = Color(0xFF34C77B)
    val Jaune = Color(0xFFF5B83D)
    val Rouge = Color(0xFFE5484D)
    val Gris = Color(0xFF6B6B6B)
    val BulleVide = Color(0xFF444444)

    // Textes secondaires
    val TexteSecondaire = Color(0xFFBDBDBD)
    val TexteDepense = Color(0xFFFF7B7B)
    val TexteSuggestion = Color(0xFF7EE2A8)

    fun Color.eclaircir(part: Float): Color = lerp(this, Color.White, part)

    fun objectifAtteint(e: EnveloppeUi): Boolean = when (e.typeObjectif) {
        // Objectif de dépense : atteint quand la dépense couvre l'objectif
        TypeObjectif.Mensuel -> e.depense >= e.objectif
        // Épargne / accumulation : atteint par le cumul alloué
        else -> e.alloueCumulatif >= e.objectif
    }

    /**
     * Couleur d'état d'une enveloppe. [pourBarre] : la barre de progression n'existe que s'il y a
     * un objectif; la barre latérale gère aussi les enveloppes sans objectif.
     */
    fun couleurEtat(e: EnveloppeUi, pourBarre: Boolean): Color {
        val sansObjectif = e.objectif <= 0 || e.typeObjectif == TypeObjectif.Aucun
        val mensuel = e.typeObjectif == TypeObjectif.Mensuel
        val progres = if (mensuel) e.alloue else e.alloueCumulatif
        return when {
            !pourBarre && sansObjectif && e.solde <= 0.001 -> Gris
            !pourBarre && sansObjectif -> Jaune
            // Objectif mensuel dépensé : couleur du compte qui l'a financé
            objectifAtteint(e) -> if (mensuel && e.depense >= e.objectif) {
                e.couleurObjectif?.toColor() ?: Vert
            } else Vert
            progres >= e.objectif -> Vert
            progres > 0.001 -> Jaune
            else -> Gris
        }
    }
}
