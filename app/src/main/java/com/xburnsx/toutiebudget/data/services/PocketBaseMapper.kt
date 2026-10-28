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
        "pret_personnel"
    )

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
        "pret_personnel" -> versPretPersonnel(item)
        else -> null
    }

    /** Ignorer une fiche d'alias invalide, jamais l'attribuer à un autre utilisateur. */
    fun versAliasBancaire(item: ItemPocketBase): com.xburnsx.toutiebudget.data.room.entities.AliasBancaire? {
        val userId = item.utilisateur_id?.takeIf { it.isNotBlank() } ?: return null
        val libelle = item.libelle_brut?.takeIf { it.isNotBlank() } ?: return null
        val tiersId = item.tiers_id?.takeIf { it.isNotBlank() } ?: return null
        if (item.id.isBlank()) return null
        return com.xburnsx.toutiebudget.data.room.entities.AliasBancaire(
            id = item.id, utilisateurId = userId, compteId = item.compte_id,
            libelleBrut = libelle, tiersId = tiersId, active = item.active ?: true
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
        collection = item.collection ?: "comptes_cheques"
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
        detailsCarteJson = item.details_carte_json?.let { if (it is String) it else gson.toJson(it) }
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
        detailsDetteJson = item.details_dette_json?.let { if (it is String) it else gson.toJson(it) }
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
        detailsInvestissementJson = item.details_investissement_json?.let { if (it is String) it else gson.toJson(it) }
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
        allocationMensuelleId = item.allocation_mensuelle_id ?: "",
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
        resetApresEcheance = item.reset_apres_echeance ?: false
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
        collectionCompteSource = item.collection_compte_source
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
