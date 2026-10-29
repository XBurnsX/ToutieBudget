package com.xburnsx.toutiebudget.workers

/**
 * Source unique de la correspondance type de SyncJob → collection PocketBase.
 * Utilisée par le worker ET par la synchronisation immédiate des repositories,
 * pour qu'ils visent toujours la même collection.
 */
object SyncCollections {

    fun collectionPourType(type: String): String = when (type.uppercase()) {
        "TRANSACTION" -> "transactions"
        "COMPTE" -> "comptes_cheques" // Collection par défaut
        "COMPTE_CHEQUE" -> "comptes_cheques"
        "COMPTE_CREDIT" -> "comptes_credits"
        "COMPTE_DETTE" -> "comptes_dettes"
        "COMPTE_INVESTISSEMENT" -> "comptes_investissement"
        "ALLOCATION_MENSUELLE" -> "allocations_mensuelles"
        "PRET_PERSONNEL" -> "pret_personnel"
        "ENVELOPPE" -> "enveloppes"
        "CATEGORIE" -> "categories"
        "TIERS" -> "tiers"
        "ALIAS_BANCAIRE" -> "alias_bancaires"
        "OPERATION_BANCAIRE" -> "operations_bancaires_attente"
        "HISTORIQUE_ALLOCATION" -> "historique_allocation"
        else -> type.lowercase()
    }
}
