// chemin/simule: /data/modeles/TypeTransaction.kt
// Énumération des types de transactions supportés par l'application

package com.xburnsx.toutiebudget.data.modeles

/**
 * Énumération des différents types de transactions possibles dans l'application.
 * Compatible avec le code existant qui utilise Depense, Revenu, Pret, Emprunt, etc.
 */
enum class TypeTransaction(
    val libelle: String,
    @Suppress("unused") val description: String
) {
    
    // Transactions standard (noms compatibles avec le code existant)
    Depense("Dépense", "Sortie d'argent standard"),
    Revenu("Revenu", "Entrée d'argent standard"),
    
    // Transactions de prêt
    Pret("Prêt accordé", "Argent prêté à quelqu'un"),
    RemboursementRecu("Remboursement reçu", "Remboursement d'un prêt accordé"),
    
    // Transactions de dette/emprunt
    Emprunt("Dette contractée", "Argent emprunté à quelqu'un"),
    RemboursementDonne("Remboursement donné", "Remboursement d'une dette contractée"),
    
    // Paiements spéciaux
    Paiement("Paiement", "Paiement spécifique (factures, etc.)"),
    PaiementEffectue("Paiement effectue", "Paiement effectué sur une dette ou carte de crédit"),
    
    // Transferts entre comptes
    TransfertSortant("Transfert sortant", "Transfert vers un autre compte"),
    TransfertEntrant("Transfert entrant", "Transfert depuis un autre compte");
    
    // Propriété pour la compatibilité avec PocketBase
    val valeurPocketBase: String get() = this.name
    val libelleAffiche: String get() = this.libelle
    
    companion object {
        /**
         * Convertit depuis une valeur PocketBase vers l'enum.
         */
        @Suppress("unused")
        fun depuisValeurPocketBase(valeur: String): TypeTransaction? {
            return entries.find { it.valeurPocketBase == valeur }
        }
    }
}