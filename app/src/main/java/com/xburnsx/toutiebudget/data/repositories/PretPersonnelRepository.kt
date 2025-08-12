package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.PretPersonnel

interface PretPersonnelRepository {
    suspend fun lister(): Result<List<PretPersonnel>>
    suspend fun creer(pret: PretPersonnel): Result<Unit>
    suspend fun supprimer(id: String): Result<Unit>
}


