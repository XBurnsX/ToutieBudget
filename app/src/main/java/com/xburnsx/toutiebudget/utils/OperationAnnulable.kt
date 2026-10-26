package com.xburnsx.toutiebudget.utils

/**
 * AT4 : une opération d'argent faite de plusieurs écritures (compte, enveloppe, prêt, transaction)
 * réussit en entier ou pas du tout. Chaque écriture faite note son inverse avec [siEchec] ;
 * si une étape suivante échoue, les inverses sont rejoués du plus récent au plus ancien.
 */
class OperationAnnulable {
    private val retours = mutableListOf<suspend () -> Unit>()

    /** À appeler juste APRÈS une écriture réussie, avec l'écriture qui la défait. */
    fun siEchec(retour: suspend () -> Unit) {
        retours += retour
    }

    internal suspend fun annuler() {
        retours.asReversed().forEach { retour -> runCatching { retour() } }
    }
}

/** Exécute [bloc] ; en cas d'erreur, défait tout ce qui a été écrit, puis relance l'erreur. */
suspend fun <T> toutOuRien(bloc: suspend OperationAnnulable.() -> T): T {
    val operation = OperationAnnulable()
    try {
        return operation.bloc()
    } catch (e: Exception) {
        operation.annuler()
        throw e
    }
}
