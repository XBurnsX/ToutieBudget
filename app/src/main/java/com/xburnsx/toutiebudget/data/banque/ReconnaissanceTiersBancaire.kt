package com.xburnsx.toutiebudget.data.banque

import com.xburnsx.toutiebudget.data.modeles.Tiers
import java.text.Normalizer
import java.util.Locale

/** Associe un libellé bancaire à un tiers existant, sans créer de fiches parallèles. */
data class RegleAliasBancaire(
    val id: String,
    val utilisateurId: String,
    val compteId: String?,
    val libelleBrut: String,
    val tiersId: String,
    val active: Boolean = true
)

object ReconnaissanceTiersBancaire {
    /** Conserver les numéros de terminal afin de ne pas fusionner deux lieux. */
    fun normaliser(libelle: String): String =
        Normalizer.normalize(libelle.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    /** Null si règle absente, ambiguë, inactive, ou tiers non retrouvé pour l'utilisateur. */
    fun reconnaitre(
        utilisateurId: String,
        compteId: String,
        libelleBrut: String,
        regles: List<RegleAliasBancaire>,
        tiersExistants: List<Tiers>
    ): Tiers? {
        if (utilisateurId.isBlank() || compteId.isBlank() || libelleBrut.isBlank()) return null
        val cle = normaliser(libelleBrut)
        val candidates = regles.filter {
            it.active && it.utilisateurId == utilisateurId &&
                it.tiersId.isNotBlank() && normaliser(it.libelleBrut) == cle &&
                (it.compteId == compteId || it.compteId == null)
        }
        val specifiques = candidates.filter { it.compteId == compteId }
        val retenues = specifiques.ifEmpty { candidates.filter { it.compteId == null } }
        val ids = retenues.map { it.tiersId }.distinct()
        if (ids.size != 1) return null
        return tiersExistants.singleOrNull {
            it.id == ids.single() && it.utilisateur_id == utilisateurId
        }
    }
}
