package com.xburnsx.toutiebudget.donnees.depot

import com.xburnsx.toutiebudget.donnees.mappeur.versCompte
import com.xburnsx.toutiebudget.domaine.depot.DepotCompte
import com.xburnsx.toutiebudget.domaine.modele.Compte
import com.xburnsx.toutiebudget.donnees.temporaire.Pocketbase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class DepotCompteImpl @Inject constructor(
    private val pb: Pocketbase
) : DepotCompte {

    override fun recupererTousLesComptes(): Flow<List<Compte>> = flowOf(
        // Données temporaires pour la compilation
        emptyList()
    )
}
