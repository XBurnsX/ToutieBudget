package com.xburnsx.toutiebudget.ui.navigation

import java.net.URLEncoder

/**
 * NVA1/NV2/NVA5 : routes typées, construites à partir d'IDs stables.
 * - Chaque argument est encodé (« / », « ? », « # », accents, espaces, apostrophes, Unicode).
 * - Un argument vide devient « - » : un segment vide ne correspond à aucune route et faisait planter la navigation.
 * - [inventaire] liste toutes les routes à arguments avec un exemple, vérifié par un test JVM.
 * Fonctions pures (sans Android) : testables sans émulateur.
 */
object Routes {
    const val VIDE = "-"

    fun encoder(valeur: String): String =
        URLEncoder.encode(valeur.ifBlank { VIDE }, "UTF-8").replace("+", "%20")

    /** NV10 : un ID de route utilisable (non vide, sans espace, longueur raisonnable). */
    fun idValide(id: String?): Boolean =
        !id.isNullOrBlank() && id != VIDE && id.length <= 64 && id.none { it.isWhitespace() || it == '/' }

    fun historiqueCompte(compteId: String, collection: String, nom: String) =
        "historique_compte/${encoder(compteId)}/${encoder(collection)}/${encoder(nom)}"

    fun historiqueEnveloppe(enveloppeId: String, nom: String) =
        "historique_enveloppe/${encoder(enveloppeId)}/${encoder(nom)}"

    fun modifierTransaction(transactionId: String) = "modifier_transaction/${encoder(transactionId)}"
    fun reprendreTransaction(transactionId: String) = "reprendre_transaction/${encoder(transactionId)}"
    fun gestionCarteCredit(carteId: String) = "gestion_carte_credit/${encoder(carteId)}"
    fun gestionDette(detteId: String) = "gestion_dette/${encoder(detteId)}"
    fun gestionInvestissement(compteId: String) = "gestion_investissement/${encoder(compteId)}"
    fun paiementCarte(carteId: String) = "nouvelle_transaction_paiement/${encoder(carteId)}"
    fun gererSoldeNegatif(moisMillis: Long) = "gerer_solde_negatif/$moisMillis"

    fun virementPrerempli(enveloppeId: String, montant: Double, mois: Int, annee: Int, total: Boolean) =
        "virer_argent_pre_rempli/${encoder(enveloppeId)}/$montant/$mois/$annee?total=$total"

    /** NVA5 : modèle de route déclaré dans le NavHost → exemple produit par le constructeur typé. */
    val inventaire: List<Pair<String, String>> = listOf(
        "historique_compte/{compteId}/{collectionCompte}/{nomCompte}" to historiqueCompte("abc123", "comptes_cheques", "Desjardins / Épargne"),
        "historique_enveloppe/{enveloppeId}/{nomEnveloppe}" to historiqueEnveloppe("env1", "Auto / Réparations ?#"),
        "modifier_transaction/{transactionId}" to modifierTransaction("tx1"),
        "reprendre_transaction/{transactionId}" to reprendreTransaction("tx1"),
        "gestion_carte_credit/{carteCreditId}" to gestionCarteCredit("visa1"),
        "gestion_dette/{detteId}" to gestionDette("dette1"),
        "gestion_investissement/{compteId}" to gestionInvestissement("celi1"),
        "nouvelle_transaction_paiement/{carteCreditId}" to paiementCarte("visa1"),
        "gerer_solde_negatif/{moisMillis}" to gererSoldeNegatif(1_790_000_000_000L),
        "virer_argent_pre_rempli/{enveloppeId}/{montantNegatif}/{moisInt}/{anneeInt}?total={total}" to
            virementPrerempli("env1", 25.0, 9, 2026, true)
    )
}
