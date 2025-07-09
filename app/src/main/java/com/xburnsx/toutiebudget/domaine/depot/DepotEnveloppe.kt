package com.xburnsx.toutiebudget.domaine.depot
import com.xburnsx.toutiebudget.domaine.modele.Enveloppe
import com.xburnsx.toutiebudget.domaine.modele.EtatEnveloppeMensuel
import kotlinx.coroutines.flow.Flow

/**
 * Contrat pour la gestion des données des enveloppes.
 */
interface DepotEnveloppe {
    fun recupererToutesLesEnveloppes(): Flow<List<Enveloppe>>
    fun recupererEtatsMensuels(mois: String): Flow<List<EtatEnveloppeMensuel>>
}
