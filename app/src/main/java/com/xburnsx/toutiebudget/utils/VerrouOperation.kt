package com.xburnsx.toutiebudget.utils

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Empêche une opération d'argent de s'exécuter deux fois (double appui, AT14).
 *
 * Le verrou est pris IMMÉDIATEMENT, au moment du clic, avant de lancer la coroutine :
 * un deuxième appui 100 ms plus tard est ignoré même si l'écran n'a pas encore
 * désactivé le bouton. Il est libéré à la fin de l'opération, réussie ou non.
 */
class VerrouOperation {
    private val enCours = AtomicBoolean(false)

    /** Vrai si l'opération peut commencer (le verrou est alors pris). */
    fun prendre(): Boolean = enCours.compareAndSet(false, true)

    fun liberer() = enCours.set(false)

    val estPris: Boolean get() = enCours.get()
}

/**
 * Lance une opération d'argent une seule fois à la fois : un appel pendant qu'elle tourne est ignoré.
 * Le verrou est pris avant de lancer la coroutine et libéré à la fin, réussite, erreur ou annulation.
 */
fun kotlinx.coroutines.CoroutineScope.lancerUneFois(
    verrou: VerrouOperation,
    bloc: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
) {
    if (!verrou.prendre()) return
    val job = launch { bloc() }
    job.invokeOnCompletion { verrou.liberer() }
}
