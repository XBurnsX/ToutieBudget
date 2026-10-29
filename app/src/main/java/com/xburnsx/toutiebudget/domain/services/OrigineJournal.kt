package com.xburnsx.toutiebudget.domain.services

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * HG7 : opération d'origine des écritures du journal faites pendant un bloc (transaction ou virement).
 * Passée par le contexte de la coroutine : chaque événement journalisé dans le bloc garde ce lien stable,
 * sans rapprochement par nom ou montant. Ne change pas de dispatcher : reste dans la transaction Room.
 */
class OrigineJournal(val transactionId: String? = null, val virementId: String? = null) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<OrigineJournal>
}

suspend fun <T> avecOrigineJournal(transactionId: String? = null, virementId: String? = null, bloc: suspend () -> T): T =
    withContext(OrigineJournal(transactionId, virementId)) { bloc() }

suspend fun origineJournalCourante(): OrigineJournal? = currentCoroutineContext()[OrigineJournal]
