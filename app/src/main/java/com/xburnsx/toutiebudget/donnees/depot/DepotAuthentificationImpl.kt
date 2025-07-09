package com.xburnsx.toutiebudget.donnees.depot

import com.xburnsx.toutiebudget.domaine.depot.DepotAuthentification
import com.xburnsx.toutiebudget.donnees.temporaire.Pocketbase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class DepotAuthentificationImpl @Inject constructor(
    private val pb: Pocketbase
) : DepotAuthentification {

    override fun recupererEtatAuth(): Flow<Boolean> {
        return flowOf(pb.authStore.isValid)
    }

    override suspend fun connexionAvecGoogle(idToken: String): Result<Unit> {
        return try {
            // Implémentation temporaire - remplacez par la vraie logique PocketBase
            pb.authStore.token = "temp_token"
            pb.authStore.isValid = true
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun deconnexion() {
        pb.authStore.clear()
    }

    override val idUtilisateurActuel: String?
        get() = if (pb.authStore.isValid) "temp_user_id" else null
}