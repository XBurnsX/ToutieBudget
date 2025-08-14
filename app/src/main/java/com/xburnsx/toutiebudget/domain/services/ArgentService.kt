package com.xburnsx.toutiebudget.domain.services

import java.util.Date

/**
 * Service pour gérer la logique métier liée aux mouvements d'argent,
 * comme les allocations, les dépenses et les transferts.
 */
interface ArgentService {

    /**
     * Alloue un montant d'un compte source vers une enveloppe pour un mois donné.
     *
     * @param enveloppeId L'ID de l'enveloppe à créditer.
     * @param compteSourceId L'ID du compte d'où provient l'argent.
     * @param collectionCompteSource Le nom de la collection du compte source (ex: "comptes_cheque").
     * @param montant Le montant à allouer.
     * @param mois Le mois de l'allocation (le premier jour du mois).
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend fun allouerArgentEnveloppe(
        enveloppeId: String,
        compteSourceId: String,
        collectionCompteSource: String,
        montant: Double,
        mois: Date
    ): Result<Unit>

    /**
     * Alloue un montant d'un compte source vers une enveloppe pour un mois donné SANS créer de transaction.
     * Utilisé pour les virements internes (prêt à placer vers enveloppe).
     *
     * @param enveloppeId L'ID de l'enveloppe à créditer.
     * @param compteSourceId L'ID du compte d'où provient l'argent.
     * @param collectionCompteSource Le nom de la collection du compte source (ex: "comptes_cheque").
     * @param montant Le montant à allouer.
     * @param mois Le mois de l'allocation (le premier jour du mois).
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend fun allouerArgentEnveloppeSansTransaction(
        enveloppeId: String,
        compteSourceId: String,
        collectionCompteSource: String,
        montant: Double,
        mois: Date
    ): Result<Unit>

    /**
     * Enregistre une nouvelle transaction (dépense ou revenu) et met à jour les soldes correspondants.
     *
     * @param type Le type de transaction (Depense, Revenu, Pret, Emprunt).
     * @param montant Le montant de la transaction.
     * @param date La date de la transaction.
     * @param compteId L'ID du compte affecté.
     * @param collectionCompte Le nom de la collection du compte affecté.
     * @param allocationMensuelleId (Optionnel) L'ID de l'allocation mensuelle si c'est une dépense liée à une enveloppe.
     * @param note Une description pour la transaction.
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend fun enregistrerTransaction(
        type: String,
        montant: Double,
        date: Date,
        compteId: String,
        collectionCompte: String,
        allocationMensuelleId: String? = null,
        note: String? = null
    ): Result<Unit>

    /**
     * Transfère de l'argent entre deux comptes.
     *
     * @param compteSourceId L'ID du compte source.
     * @param collectionCompteSource La collection du compte source.
     * @param compteDestId L'ID du compte de destination.
     * @param collectionCompteDest La collection du compte de destination.
     * @param montant Le montant à transférer.
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend fun transfererArgentEntreComptes(
        compteSourceId: String,
        collectionCompteSource: String,
        compteDestId: String,
        collectionCompteDest: String,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement d'un compte vers un autre compte.
     */
    suspend fun effectuerVirementCompteVersCompte(
        compteSource: com.xburnsx.toutiebudget.data.modeles.Compte,
        compteDestination: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement d'un compte vers une enveloppe.
     */
    suspend fun effectuerVirementCompteVersEnveloppe(
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement d'une enveloppe vers un compte.
     */
    suspend fun effectuerVirementEnveloppeVersCompte(
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement d'une enveloppe vers un compte SANS créer de transaction.
     * Utilisé pour les virements internes (enveloppe vers prêt à placer).
     */
    suspend fun effectuerVirementEnveloppeVersCompteSansTransaction(
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement d'une enveloppe vers une autre enveloppe.
     */
    suspend fun effectuerVirementEnveloppeVersEnveloppe(
        enveloppeSource: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        enveloppeDestination: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement depuis le "prêt à placer" d'un compte vers une enveloppe.
     */
    suspend fun effectuerVirementPretAPlacerVersEnveloppe(
        compteId: String,
        enveloppeId: String,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement depuis une enveloppe vers le "prêt à placer" d'un compte.
     */
    suspend fun effectuerVirementEnveloppeVersPretAPlacer(
        enveloppeId: String,
        compteId: String,
        montant: Double
    ): Result<Unit>

    /**
     * Effectue un virement entre deux comptes, en créant une transaction pour chaque.
     * @param compteSourceId ID du compte source.
     * @param compteDestId ID du compte de destination.
     * @param montant Montant à transférer.
     * @param nomCompteSource Nom du compte source pour la description de la transaction.
     * @param nomCompteDest Nom du compte de destination pour la description de la transaction.
     * @return Result<Unit> indiquant le succès ou l'échec.
     */
    suspend fun effectuerVirementEntreComptes(
        compteSourceId: String,
        compteDestId: String,
        montant: Double,
        nomCompteSource: String,
        nomCompteDest: String
    ): Result<Unit>

    /**
     * Effectue un paiement de carte de crédit ou de dette.
     * Crée deux transactions : une sortie du compte qui paie et une entrée sur la carte/dette.
     * @param compteQuiPaieId ID du compte qui effectue le paiement.
     * @param collectionCompteQuiPaie Collection du compte qui paie.
     * @param carteOuDetteId ID de la carte de crédit ou dette à payer.
     * @param collectionCarteOuDette Collection de la carte/dette.
     * @param montant Montant du paiement.
     * @param note Note optionnelle pour les transactions.
     * @return Result<Unit> indiquant le succès ou l'échec.
     */
    suspend fun effectuerPaiementCarteOuDette(
        compteQuiPaieId: String,
        collectionCompteQuiPaie: String,
        carteOuDetteId: String,
        collectionCarteOuDette: String,
        montant: Double,
        note: String? = null
    ): Result<Unit>
}
