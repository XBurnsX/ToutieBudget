package com.xburnsx.toutiebudget.donnees.depot

import com.xburnsx.toutiebudget.donnees.mappeur.versEnveloppe
import com.xburnsx.toutiebudget.donnees.mappeur.versEtatEnveloppeMensuel
import com.xburnsx.toutiebudget.domaine.depot.DepotEnveloppe
import com.xburnsx.toutiebudget.domaine.modele.Enveloppe
import com.xburnsx.toutiebudget.domaine.modele.EtatEnveloppeMensuel
import com.xburnsx.toutiebudget.donnees.temporaire.Pocketbase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class DepotEnveloppeImpl @Inject constructor(
    private val pb: Pocketbase
) : DepotEnveloppe {

    override fun recupererToutesLesEnveloppes(): Flow<List<Enveloppe>> = flowOf(
        // Données temporaires pour la compilation
        emptyList()
    )

    override fun recupererEtatsMensuels(mois: String): Flow<List<EtatEnveloppeMensuel>> = flowOf(
        // Données temporaires pour la compilation
        emptyList()
    )
}