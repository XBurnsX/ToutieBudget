package com.xburnsx.toutiebudget.domaine.modele
import java.util.Date

data class Enveloppe(
    val id: String,
    val nom: String,
    val objectifMontant: Double,
    val frequenceObjectif: String,
    val dateCibleObjectif: Date?,
    val jourObjectif: Int?,
    val estArchivee: Boolean,
    val ordre: Int?
)
