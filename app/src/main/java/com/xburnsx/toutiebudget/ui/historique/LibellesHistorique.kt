package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Enveloppe

/**
 * HC8 : en lecture historique, ne jamais transformer un ID manquant en
 * "Sans enveloppe" et ne jamais substituer un nom inventé.
 * Une enveloppe archivée reste identifiable par son véritable nom.
 */
object LibellesHistorique {
    fun enveloppe(enveloppeId: String, index: Map<String, Enveloppe>): String {
        val enveloppe = index[enveloppeId]
        return if (enveloppe == null) {
            "Enveloppe historique introuvable (ID : $enveloppeId)"
        } else if (enveloppe.estArchive) {
            "${enveloppe.nom} (archivée)"
        } else {
            enveloppe.nom
        }
    }

    fun allocationIntrouvable(id: String): String =
        "Allocation historique introuvable (ID : $id)"
}
