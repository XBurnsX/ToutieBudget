package com.xburnsx.toutiebudget.domaine.modele

data class EtatEnveloppeMensuel(
    val id: String,
    val idEnveloppe: String,
    val mois: String, // "AAAA-MM"
    val solde: Double,
    val depensesDuMois: Double,
    val idCompteProvenance: String?
)
