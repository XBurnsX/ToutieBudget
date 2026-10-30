package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date

data class TransactionUi(
    val id: String,
    val type: TypeTransaction,
    val montant: Double,
    val date: Date,
    val tiersUtiliser: String,
    val nomEnveloppe: String?,
    val note: String?,
    val estFractionnee: Boolean = false,
    val sousItems: String? = null,
    val nomsEnveloppesFractions: List<String> = emptyList(),
    /** Lecture seule : informations de compte pour le détail d'une transaction. */
    val compteId: String? = null,
    val nomCompte: String? = null,
    val collectionCompte: String? = null,
    /** Montant bancaire total en cas d'historique d'une enveloppe fractionnée.
     * [montant] reste la portion affichée dans cet historique. */
    val montantTotalTransaction: Double? = null,
    /** Valeurs d'origine pour confirmer qu'une suppression vise bien la version consultée. */
    val tiersBrut: String? = null,
    val allocationMensuelleId: String? = null,
    val revisionTransaction: Date? = null,
    /** EA6 : dates techniques connues, pas un statut de sync inventé. */
    val dateCreationEnregistrement: Date? = null,
    val statutSynchronisationLocale: String? = null,
    /** HA5 : provenance connue avec certitude (jamais devinée). */
    val provenance: String? = null
) 