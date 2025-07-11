// chemin/simule: /data/modeles/AllocationMensuelle.kt
package com.xburnsx.toutiebudget.data.modeles

import java.util.Date

data class AllocationMensuelle(
    val id: String,
    val utilisateurId: String,
    val enveloppeId: String,
    val mois: Date,
    val solde: Double,
    val alloue: Double,
    val depense: Double,
    val compteSourceId: String?,
    val collectionCompteSource: String?
)
