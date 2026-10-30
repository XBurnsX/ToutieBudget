package com.xburnsx.toutiebudget.data.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.room.entities.*

/**
 * Conversion d'une fiche PocketBase (ItemPocketBase) vers l'entité Room correspondante.
 * Source unique utilisée par l'import initial ET par la synchronisation temps réel,
 * pour qu'une fiche arrive dans Room de la même façon peu importe le chemin.
 */
object PocketBaseMapper {

    private val gson: Gson = GsonBuilder().create()

    /** Collections PocketBase que l'app garde en miroir dans Room. */
    val collectionsSynchronisees = listOf(
        "comptes_cheques",
        "comptes_credits",
        "comptes_dettes",
        "comptes_investissement",
        "categories",
        "enveloppes",
        "allocations_mensuelles",
        "transactions",
        "tiers",
        "alias_bancaires",
        "operations_bancaires_attente",
        "pret_personnel",
        "historique_allocation"
    )

    /** Collections ajoutées après coup : leur absence sur le serveur n'est pas une erreur. */
    val collectionsFacultatives = setOf("alias_bancaires", "operations_bancaires_attente", "historique_allocation")

    /** Entité Room pour une fiche d'une collection donnée, ou null si la collection n'est pas suivie. */
    fun versEntite(collection: String, item: ItemPocketBase): Any? = when (collection) {
        "comptes_cheques" -> versCompteCheque(item)
        "comptes_credits" -> versCompteCredit(item)
        "comptes_dettes" -> versCompteDette(item)
        "comptes_investissement" -> versCompteInvestissement(item)
        "categories" -> versCategorie(item)
        "enveloppes" -> versEnveloppe(item)
        "allocations_mensuelles" -> versAllocationMensuelle(item)
        "transactions" -> versTransaction(item)
        "tiers" -> versTiers(item)
        "alias_bancaires" -> versAliasBancaire(item)
        "operations_bancaires_attente" -> versOperationBancaire(item)
        "pret_personnel" -> versPretPersonnel(item)
        "historique_allocation" -> versHistoriqueAllocation(item)
        else -> null
    }

    /** HG4 : événement immuable du journal ; ignoré s'il est incomplet ou sans propriétaire. */
    fun versHistoriqueAllocation(item: ItemPocketBase): HistoriqueAllocation? {
        val utilisateurId = item.utilisateur_id?.takeIf { it.isNotBlank() } ?: return null
        val date = item.date_action?.takeIf { it.isNotBlank() } ?: return null
        if (item.id.isBlank()) return null
        return HistoriqueAllocation(
            id = item.id,
            utilisateurId = utilisateurId,
            compteId = item.compte_id.orEmpty(),
            collectionCompte = item.collection_compte.orEmpty(),
            enveloppeId = item.enveloppe_id.orEmpty(),
            enveloppeNom = item.enveloppe_nom.orEmpty(),
            typeAction = item.type_action.orEmpty(),
            description = item.description.orEmpty(),
            montant = item.montant?.toDoubleOrNull() ?: 0.0,
            soldeAvant = item.solde_avant?.toDoubleOrNull() ?: 0.0,
            soldeApres = item.solde_apres?.toDoubleOrNull() ?: 0.0,
            pretAPlacerAvant = item.pret_a_placer_avant?.toDoubleOrNull() ?: 0.0,
            pretAPlacerApres = item.pret_a_placer_apres?.toDoubleOrNull() ?: 0.0,
            dateAction = date,
            allocationId = item.allocation_id.orEmpty(),
            transactionId = item.transaction_id?.takeIf { it.isNotBlank() },
            virementId = item.virement_id?.takeIf { it.isNotBlank() },
            details = item.details
        )
    }

    /** Ignorer une fiche d'alias invalide, jamais l'attribuer à un autre utilisateur. */
    fun versAliasBancaire(item: ItemPocketBase): com.xburnsx.toutiebudget.data.room.entities.AliasBancaire? {
        val userId = item.utilisateur_id?.takeIf { it.isNotBlank() } ?: return null
        val libelle = item.libelle_brut?.takeIf { it.isNotBlank() } ?: return null
        val tiersId = item.tiers_id?.takeIf { it.isNotBlank() } ?: return null
        if (item.id.isBlank()) return null
        return com.xburnsx.toutiebudget.data.room.entities.AliasBancaire(
            id = item.id, utilisateurId = userId, compteId = item.compte_id?.takeIf { it.isNotBlank() },
            libelleBrut = libelle, tiersId = tiersId, active = item.active ?: true
        )
    }

    fun versOperationBancaire(item: ItemPocketBase):
        com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente? {
        val utilisateurId = item.utilisateur_id?.takeIf { it.isNotBlank() } ?: return null
        val compteId = item.compte_id?.takeIf { it.isNotBlank() } ?: return null
        val collection = item.collection_compte
            ?.takeIf { it in setOf("comptes_cheques", "comptes_credits") } ?: return null
        val source = item.source?.takeIf { it in setOf("CSV", "PLAID", "NOTIFICATION") } ?: return null
        val externe = item.id_externe?.takeIf { it.isNotBlank() } ?: return null
        val date = item.date_operation ?: return null
        if (runCatching { java.time.LocalDate.parse(date) }.isFailure) return null
        val cents = item.montant_centimes?.toLongOrNull()?.takeIf { it != 0L } ?: return null
        val libelle = item.libelle_brut?.takeIf { it.isNotBlank() } ?: return null
        val statut = item.statut_bancaire?.takeIf { it in setOf("EN_ATTENTE", "FINALISEE") }
            ?: return null
        val decision = item.decision?.takeIf { it in setOf("A_CONFIRMER", "IGNOREE") }
            ?: return null
        if (item.id.isBlank()) return null
        return com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente(
            id = item.id, utilisateurId = utilisateurId, compteId = compteId,
            collectionCompte = collection, source = source, idExterne = externe,
            dateOperation = date, montantCentimes = cents, libelleBrut = libelle,
            statutBancaire = statut, decision = decision,
            tiersId = item.tiers_id?.takeIf { it.isNotBlank() },
            verificationRequise = item.verification_requise ?: false,
            montantFinalCentimes = item.montant_final_centimes?.toLongOrNull()
        )
    }

    fun stringVersTypeObjectif(typeString: String?): TypeObjectif = when (typeString?.trim()) {
        "Aucun" -> TypeObjectif.Aucun
        "Mensuel" -> TypeObjectif.Mensuel
        "Bihebdomadaire" -> TypeObjectif.Bihebdomadaire
        "Echeance" -> TypeObjectif.Echeance
        "Annuel" -> TypeObjectif.Annuel
        else -> TypeObjectif.Aucun
    }

    fun versCompteCheque(item: ItemPocketBase) = CompteCheque(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        solde = item.solde?.toDoubleOrNull() ?: 0.0,
        pretAPlacerRaw = item.pret_a_placer?.toDoubleOrNull(),
        couleur = item.couleur ?: "#FF0000",
        estArchive = item.archive ?: false,
        ordre = item.ordre ?: 0,
        collection = item.collection ?: "comptes_cheques",
        archiveLe = item.archive_le?.takeIf { it.isNotBlank() },
        raisonArchivage = item.raison_archivage?.takeIf { it.isNotBlank() }
    )

    fun versCompteCredit(item: ItemPocketBase) = CompteCredit(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        soldeUtilise = item.solde_utilise?.toDoubleOrNull() ?: 0.0,
        couleur = item.couleur ?: "#FF0000",
        estArchive = item.archive ?: false,
        ordre = item.ordre ?: 0,
        limiteCredit = item.limite_credit?.toDoubleOrNull() ?: 0.0,
        tauxInteret = item.taux_interet?.toDoubleOrNull(),
        paiementMinimum = item.paiement_minimum?.toDoubleOrNull(),
        fraisMensuelsJson = item.frais_mensuels_json?.let { gson.toJson(it) },
        collection = item.collection ?: "comptes_credits",
        detailsCarteJson = item.details_carte_json?.let { if (it is String) it else gson.toJson(it) },
        archiveLe = item.archive_le?.takeIf { it.isNotBlank() },
        raisonArchivage = item.raison_archivage?.takeIf { it.isNotBlank() }
    )

    fun versCompteDette(item: ItemPocketBase) = CompteDette(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        soldeDette = item.solde_dette?.toDoubleOrNull() ?: 0.0,
        estArchive = item.archive ?: false,
        ordre = item.ordre ?: 0,
        montantInitial = item.montant_initial?.toDoubleOrNull() ?: 0.0,
        tauxInteret = item.taux_interet?.toDoubleOrNull(),
        paiementMinimum = item.paiement_minimum?.toDoubleOrNull(),
        dureeMoisPret = item.duree_mois_pret?.toIntOrNull(),
        paiementEffectue = item.paiement_effectue ?: 0,
        prixTotal = item.prix_total?.toDoubleOrNull(),
        collection = item.collection ?: "comptes_dettes",
        detailsDetteJson = item.details_dette_json?.let { if (it is String) it else gson.toJson(it) },
        archiveLe = item.archive_le?.takeIf { it.isNotBlank() },
        raisonArchivage = item.raison_archivage?.takeIf { it.isNotBlank() }
    )

    fun versCompteInvestissement(item: ItemPocketBase) = CompteInvestissement(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        solde = item.solde?.toDoubleOrNull() ?: 0.0,
        couleur = item.couleur ?: "#FF0000",
        estArchive = item.archive ?: false,
        ordre = item.ordre ?: 0,
        collection = item.collection ?: "comptes_investissement",
        detailsInvestissementJson = item.details_investissement_json?.let { if (it is String) it else gson.toJson(it) },
        archiveLe = item.archive_le?.takeIf { it.isNotBlank() },
        raisonArchivage = item.raison_archivage?.takeIf { it.isNotBlank() }
    )

    fun versCategorie(item: ItemPocketBase) = Categorie(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        ordre = item.ordre ?: 0
    )

    fun versTransaction(item: ItemPocketBase) = Transaction(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        type = item.type ?: "",
        montant = item.montant?.toDoubleOrNull() ?: 0.0,
        date = item.date ?: "",
        note = item.note,
        compteId = item.compte_id ?: "",
        collectionCompte = item.collection_compte ?: "",
        allocationMensuelleId = item.allocation_mensuelle_id?.trim()?.takeIf { it.isNotEmpty() },
        estFractionnee = item.est_fractionnee ?: false,
        sousItems = item.sous_items?.let { gson.toJson(it) },
        tiersUtiliser = item.tiers_utiliser,
        created = item.created,
        updated = item.updated
    )

    fun versEnveloppe(item: ItemPocketBase) = Enveloppe(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        categorieId = item.categorie_id ?: "",
        estArchive = item.est_archive ?: false,
        ordre = item.ordre ?: 0,
        typeObjectif = stringVersTypeObjectif(item.frequence_objectif),
        objectifMontant = item.montant_objectif?.toDoubleOrNull() ?: 0.0,
        dateObjectif = item.date_objectif,
        dateDebutObjectif = item.date_debut_objectif,
        objectifJour = item.objectif_jour?.toIntOrNull(),
        resetApresEcheance = item.reset_apres_echeance ?: false,
        archiveLe = item.archive_le?.takeIf { it.isNotBlank() },
        raisonArchivage = item.raison_archivage?.takeIf { it.isNotBlank() }
    )

    fun versAllocationMensuelle(item: ItemPocketBase) = AllocationMensuelle(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        enveloppeId = item.enveloppe_id ?: "",
        mois = item.mois ?: "",
        solde = item.solde?.toDoubleOrNull() ?: 0.0,
        alloue = item.alloue?.toDoubleOrNull() ?: 0.0,
        depense = item.depense?.toDoubleOrNull() ?: 0.0,
        compteSourceId = item.compte_source_id,
        collectionCompteSource = item.collection_compte_source,
        montantReporte = item.montant_reporte?.toDoubleOrNull() ?: 0.0
    )

    fun versTiers(item: ItemPocketBase) = Tiers(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nom = item.nom ?: "",
        created = item.created ?: "",
        updated = item.updated ?: "",
        collectionId = item.collectionId ?: "",
        collectionName = item.collectionName ?: ""
    )

    fun versPretPersonnel(item: ItemPocketBase) = PretPersonnel(
        id = item.id,
        utilisateurId = item.utilisateur_id ?: "",
        nomTiers = item.nom_tiers,
        montantInitial = item.montant_initial?.toDoubleOrNull() ?: 0.0,
        solde = item.solde?.toDoubleOrNull() ?: 0.0,
        type = item.type ?: "",
        estArchive = item.archive ?: false,
        dateCreation = item.date_creation,
        created = item.created,
        updated = item.updated
    )
}
