// chemin/simule: /data/modeles/TypeTransaction.kt
// Énumération des types de transactions supportés par l'application

package com.xburnsx.toutiebudget.data.modeles

/**
 * Énumération des différents types de transactions possibles dans l'application.
 * Compatible avec le code existant qui utilise Depense, Revenu, Pret, Emprunt, etc.
 */
enum class TypeTransaction(val libelle: String, val description: String) {
    
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
        fun depuisValeurPocketBase(valeur: String): TypeTransaction? {
            return values().find { it.valeurPocketBase == valeur }
        }
        
        /**
         * Retourne tous les types de transactions standards (Dépense/Revenu).
         */
        fun getTypesStandard(): List<TypeTransaction> {
            return listOf(Depense, Revenu)
        }
        
        /**
         * Retourne tous les types de transactions liés aux prêts.
         */
        fun getTypesPret(): List<TypeTransaction> {
            return listOf(Pret, RemboursementRecu)
        }
        
        /**
         * Retourne tous les types de transactions liés aux dettes.
         */
        fun getTypesDette(): List<TypeTransaction> {
            return listOf(Emprunt, RemboursementDonne)
        }
        
        /**
         * Retourne tous les types de transactions liés aux transferts.
         */
        fun getTypesTransfert(): List<TypeTransaction> {
            return listOf(TransfertSortant, TransfertEntrant)
        }
        
        /**
         * Détermine si le type de transaction représente une sortie d'argent.
         */
        fun TypeTransaction.estSortie(): Boolean {
            return when (this) {
                Depense, Pret, RemboursementDonne, Paiement, TransfertSortant -> true
                Revenu, RemboursementRecu, Emprunt, TransfertEntrant -> false
            }
        }
        
        /**
         * Détermine si le type de transaction représente une entrée d'argent.
         */
        fun TypeTransaction.estEntree(): Boolean {
            return !this.estSortie()
        }
        
        /**
         * Détermine si le type de transaction nécessite un champ "tiers".
         */
        fun TypeTransaction.necessiteTiers(): Boolean {
            return when (this) {
                Depense, Pret, RemboursementRecu, 
                Emprunt, RemboursementDonne, Paiement -> true
                Revenu, TransfertSortant, TransfertEntrant -> false
            }
        }
        
        /**
         * Détermine si le type de transaction nécessite une enveloppe.
         */
        fun TypeTransaction.necessiteEnveloppe(): Boolean {
            return when (this) {
                Depense -> true
                else -> false
            }
        }
    }
}// chemin/simule: /data/modeles/TypeTransaction.kt
// Énumération des types de transactions supportés par l'application

package com.xburnsx.toutiebudget.data.modeles

/**
 * Énumération des différents types de transactions possibles dans l'application.
 * Compatible avec le code existant qui utilise des noms comme "Depense", "Revenu", etc.
 */
enum class TypeTransaction(val libelle: String, val description: String, val libelleAffiche: String) {
    
    // Transactions standard (noms compatibles avec le code existant)
    Depense("Dépense", "Sortie d'argent standard", "Dépense"),
    Revenu("Revenu", "Entrée d'argent standard", "Revenu"),
    
    // Transactions de prêt
    Pret("Prêt", "Argent prêté à quelqu'un", "Prêt accordé"),
    RemboursementRecu("Remboursement reçu", "Remboursement d'un prêt accordé", "Remboursement reçu"),
    
    // Transactions de dette/emprunt
    Emprunt("Emprunt", "Argent emprunté à quelqu'un", "Dette contractée"),
    RemboursementDonne("Remboursement donné", "Remboursement d'une dette contractée", "Remboursement donné"),
    
    // Paiements spéciaux
    Paiement("Paiement", "Paiement spécifique (factures, etc.)", "Paiement"),
    
    // Transferts entre comptes
    TransfertSortant("Transfert sortant", "Transfert vers un autre compte", "Transfert sortant"),
    TransfertEntrant("Transfert entrant", "Transfert depuis un autre compte", "Transfert entrant");
    
    // Propriétés d'extension pour la compatibilité
    val valeurPocketBase: String get() = this.name
    
    companion object {
        /**
         * Convertit depuis une valeur PocketBase vers l'enum.
         */
        fun depuisValeurPocketBase(valeur: String): TypeTransaction? {
            return values().find { it.valeurPocketBase == valeur }
        }
        
        /**
         * Retourne tous les types de transactions standards (Dépense/Revenu).
         */
        fun getTypesStandard(): List<TypeTransaction> {
            return listOf(Depense, Revenu)
        }
        
        /**
         * Retourne tous les types de transactions liés aux prêts.
         */
        fun getTypesPret(): List<TypeTransaction> {
            return listOf(Pret, RemboursementRecu)
        }
        
        /**
         * Retourne tous les types de transactions liés aux dettes.
         */
        fun getTypesDette(): List<TypeTransaction> {
            return listOf(Emprunt, RemboursementDonne)
        }
        
        /**
         * Retourne tous les types de transactions liés aux transferts.
         */
        fun getTypesTransfert(): List<TypeTransaction> {
            return listOf(TransfertSortant, TransfertEntrant)
        }
        
        /**
         * Détermine si le type de transaction représ// chemin/simule: /data/modeles/TypeTransaction.kt
// Énumération des types de transactions supportés par l'application

package com.xburnsx.toutiebudget.data.modeles

/**
 * Énumération des différents types de transactions possibles dans l'application.
 */
enum class TypeTransaction(val libelle: String, val description: String) {
    
    // Transactions standard
    DEPENSE("Dépense", "Sortie d'argent standard"),
    REVENU("Revenu", "Entrée d'argent standard"),
    
    // Transactions de prêt
    PRET_ACCORDE("Prêt accordé", "Argent prêté à quelqu'un"),
    REMBOURSEMENT_RECU("Remboursement reçu", "Remboursement d'un prêt accordé"),
    
    // Transactions de dette
    DETTE_CONTRACTEE("Dette contractée", "Argent emprunté à quelqu'un"),
    REMBOURSEMENT_DONNE("Remboursement donné", "Remboursement d'une dette contractée"),
    
    // Paiements spéciaux
    PAIEMENT("Paiement", "Paiement spécifique (factures, etc.)"),
    
    // Transferts entre comptes
    TRANSFERT_SORTANT("Transfert sortant", "Transfert vers un autre compte"),
    TRANSFERT_ENTRANT("Transfert entrant", "Transfert depuis un autre compte");
    
    companion object {
        /**
         * Retourne tous les types de transactions standards (Dépense/Revenu).
         */
        fun getTypesStandard(): List<TypeTransaction> {
            return listOf(DEPENSE, REVENU)
        }
        
        /**
         * Retourne tous les types de transactions liés aux prêts.
         */
        fun getTypesPret(): List<TypeTransaction> {
            return listOf(PRET_ACCORDE, REMBOURSEMENT_RECU)
        }
        
        /**
         * Retourne tous les types de transactions liés aux dettes.
         */
        fun getTypesDette(): List<TypeTransaction> {
            return listOf(DETTE_CONTRACTEE, REMBOURSEMENT_DONNE)
        }
        
        /**
         * Retourne tous les types de transactions liés aux transferts.
         */
        fun getTypesTransfert(): List<TypeTransaction> {
            return listOf(TRANSFERT_SORTANT, TRANSFERT_ENTRANT)
        }
        
        /**
         * Détermine si le type de transaction représente une sortie d'argent.
         */
        fun TypeTransaction.estSortie(): Boolean {
            return when (this) {
                DEPENSE, PRET_ACCORDE, REMBOURSEMENT_DONNE, PAIEMENT, TRANSFERT_SORTANT -> true
                REVENU, REMBOURSEMENT_RECU, DETTE_CONTRACTEE, TRANSFERT_ENTRANT -> false
            }
        }
        
        /**
         * Détermine si le type de transaction représente une entrée d'argent.
         */
        fun TypeTransaction.estEntree(): Boolean {
            return !this.estSortie()
        }
        
        /**
         * Détermine si le type de transaction nécessite un champ "tiers".
         */
        fun TypeTransaction.necessiteTiers(): Boolean {
            return when (this) {
                DEPENSE, PRET_ACCORDE, REMBOURSEMENT_RECU, 
                DETTE_CONTRACTEE, REMBOURSEMENT_DONNE, PAIEMENT -> true
                REVENU, TRANSFERT_SORTANT, TRANSFERT_ENTRANT -> false
            }
        }
        
        /**
         * Détermine si le type de transaction nécessite une enveloppe.
         */
        fun TypeTransaction.necessiteEnveloppe(): Boolean {
            return when (this) {
                DEPENSE -> true
                else -> false
            }
        }
    }
}