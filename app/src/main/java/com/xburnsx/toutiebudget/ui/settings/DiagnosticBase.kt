package com.xburnsx.toutiebudget.ui.settings

import com.xburnsx.toutiebudget.data.services.SauvegardeBudget
import kotlin.math.abs
import kotlin.math.round

/**
 * BDA2 : diagnostic de cohérence en LECTURE SEULE. Il signale, il ne répare rien.
 * IDs vides, entités orphelines, fractions incohérentes, allocations en double,
 * liens cassés et montants impossibles.
 */
object DiagnosticBase {
    enum class Gravite(val libelle: String) { ERREUR("Erreur"), AVERTISSEMENT("À vérifier") }
    data class Constat(val gravite: Gravite, val texte: String)

    private fun centimes(v: Double) = round(v * 100.0).toLong()

    fun analyser(c: SauvegardeBudget.Contenu): List<Constat> {
        val constats = mutableListOf<Constat>()
        fun erreur(t: String) { constats += Constat(Gravite.ERREUR, t) }
        fun avert(t: String) { constats += Constat(Gravite.AVERTISSEMENT, t) }

        // IDs vides
        val ids = listOf(
            "compte chèque" to c.comptesCheques.map { it.id }, "carte" to c.comptesCredits.map { it.id },
            "dette" to c.comptesDettes.map { it.id }, "placement" to c.comptesInvestissement.map { it.id },
            "catégorie" to c.categories.map { it.id }, "enveloppe" to c.enveloppes.map { it.id },
            "allocation" to c.allocations.map { it.id }, "transaction" to c.transactions.map { it.id },
            "tiers" to c.tiers.map { it.id }, "prêt" to c.pretsPersonnels.map { it.id }
        )
        ids.forEach { (nom, liste) ->
            val vides = liste.count { it.isBlank() }
            if (vides > 0) erreur("$vides $nom(s) sans identifiant")
        }

        val comptes = (c.comptesCheques.map { it.id } + c.comptesCredits.map { it.id } +
            c.comptesDettes.map { it.id } + c.comptesInvestissement.map { it.id }).toSet()
        val enveloppes = c.enveloppes.associateBy { it.id }
        val categories = c.categories.map { it.id }.toSet()
        val allocations = c.allocations.associateBy { it.id }

        // Liens cassés
        c.enveloppes.filter { it.categorieId.isNotBlank() && it.categorieId !in categories }.forEach {
            avert("Enveloppe « ${it.nom} » : catégorie introuvable (${it.categorieId})")
        }
        c.allocations.filter { it.enveloppeId !in enveloppes }.forEach {
            erreur("Allocation ${it.id} (${it.mois}) : enveloppe introuvable (${it.enveloppeId})")
        }
        c.transactions.filter { it.compteId.isNotBlank() && it.compteId !in comptes }.forEach {
            erreur("Transaction ${it.id} (${it.date}) : compte introuvable (${it.compteId})")
        }
        c.transactions.filter { !it.allocationMensuelleId.isNullOrBlank() && it.allocationMensuelleId !in allocations }.forEach {
            avert("Transaction ${it.id} (${it.date}) : allocation introuvable (${it.allocationMensuelleId})")
        }

        // Fractions incohérentes
        c.transactions.filter { it.estFractionnee || !it.sousItems.isNullOrBlank() }.forEach { t ->
            val total = runCatching {
                com.google.gson.JsonParser.parseString(t.sousItems ?: "[]").asJsonArray.sumOf { el ->
                    val o = el.asJsonObject
                    (o.get("enveloppeId") ?: o.get("enveloppe_id"))?.asString?.let { idEnv ->
                        if (idEnv !in enveloppes) avert("Transaction ${t.id} : fraction vers une enveloppe introuvable ($idEnv)")
                    }
                    centimes(o.get("montant")?.asDouble ?: Double.NaN)
                }
            }.getOrElse {
                erreur("Transaction ${t.id} : fractions illisibles (JSON invalide)")
                return@forEach
            }
            if (t.estFractionnee && total != centimes(abs(t.montant))) {
                erreur("Transaction ${t.id} : fractions = ${total / 100.0} $ mais montant = ${abs(t.montant)} $")
            }
        }

        // Allocations en double : même enveloppe, même mois, même provenance
        c.allocations.groupBy { Triple(it.enveloppeId, it.mois.take(7), it.compteSourceId) }
            .filter { it.value.size > 1 }
            .forEach { (cle, liste) ->
                val nom = enveloppes[cle.first]?.nom ?: cle.first
                avert("Enveloppe « $nom » : ${liste.size} allocations pour ${cle.second} avec la même provenance")
            }

        // Montants impossibles
        c.transactions.filter { !it.montant.isFinite() }.forEach { erreur("Transaction ${it.id} : montant invalide") }
        c.allocations.filter { !it.solde.isFinite() || !it.alloue.isFinite() || !it.depense.isFinite() }.forEach {
            erreur("Allocation ${it.id} : montant invalide")
        }
        c.allocations.filter { it.depense < -0.005 }.forEach { avert("Allocation ${it.id} : dépense négative (${it.depense} $)") }
        (c.comptesCheques.map { it.nom to it.solde } + c.comptesCredits.map { it.nom to it.soldeUtilise } +
            c.comptesDettes.map { it.nom to it.soldeDette } + c.comptesInvestissement.map { it.nom to it.solde })
            .filter { !it.second.isFinite() }.forEach { erreur("Compte « ${it.first} » : solde invalide") }
        c.comptesCheques.filter { !(it.pretAPlacerRaw ?: 0.0).isFinite() }.forEach { erreur("Compte « ${it.nom} » : Prêt à placer invalide") }
        c.pretsPersonnels.filter { it.solde > it.montantInitial + 0.005 && it.montantInitial > 0 }.forEach {
            avert("Prêt « ${it.nomTiers} » : solde (${it.solde} $) plus grand que le montant initial (${it.montantInitial} $)")
        }
        return constats.sortedBy { it.gravite.ordinal }
    }
}
