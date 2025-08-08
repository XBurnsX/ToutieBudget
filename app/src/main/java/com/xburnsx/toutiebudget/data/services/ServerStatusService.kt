package com.xburnsx.toutiebudget.data.services

/**
 * Service pour vérifier l'état du serveur PocketBase
 */
class ServerStatusService {
    /**
     * Vérifie la connectivité au serveur PocketBase et retourne un état détaillé.
     * @return Pair(isAvailable, errorMessage)
     */
    suspend fun verifierConnectiviteDetaillee(): Pair<Boolean, String> {
        return try {
            val estDisponible = com.xburnsx.toutiebudget.di.PocketBaseClient.verifierConnexionServeur()
            if (estDisponible) {
                true to ""
            } else {
                val urlCourante = com.xburnsx.toutiebudget.di.UrlResolver.obtenirUrlActuelle()
                false to (
                    "Serveur PocketBase indisponible" +
                        (if (urlCourante != null) " à l'adresse $urlCourante." else ".")
                )
            }
        } catch (e: Exception) {
            // Invalider le cache pour forcer une nouvelle résolution d'URL lors de la prochaine tentative
            com.xburnsx.toutiebudget.di.UrlResolver.invaliderCache()
            false to ("Erreur de connectivité: ${e.message ?: "inconnue"}")
        }
    }
}
