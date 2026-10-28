package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.modeles.PositionTitre
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.data.modeles.Valorisation
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.domain.services.Portefeuille
import com.xburnsx.toutiebudget.utils.OperationAtomique

/**
 * Prix des titres à jour, à la demande (« Actualiser ») ou tout seul à l'ouverture de l'app.
 * Ce qui échoue en ligne garde son ancien prix ; rien n'est inventé.
 */
class ActualisationCours(
    private val compteRepository: CompteRepository,
    private val operationAtomique: OperationAtomique,
    private val realtimeSyncService: RealtimeSyncService,
    private val coursEnLigne: CoursEnLigne
) {
    data class Cotations(val prix: Map<String, Cotation>, val taux: Map<String, Double>, val echecs: List<String>) {
        val vide get() = prix.isEmpty() && taux.isEmpty()
    }

    @Volatile private var derniereActualisation = 0L

    /** Dernier prix de chaque titre et taux des devises étrangères vers CAD. */
    suspend fun coter(positions: List<PositionTitre>): Cotations {
        val prix = mutableMapOf<String, Cotation>()
        val echecs = mutableListOf<String>()
        positions.forEach { p ->
            coursEnLigne.cotation(p.symbole, p.symboleCotation)
                .onSuccess { prix[p.symbole] = it }
                .onFailure { echecs += p.symbole }
        }
        val taux = mutableMapOf<String, Double>()
        (prix.values.map { it.devise } + positions.map { it.devise }).toSet().filter { it != "CAD" }.forEach { devise ->
            coursEnLigne.tauxVersCad(devise).onSuccess { taux[devise] = it }.onFailure { echecs += "taux $devise" }
        }
        return Cotations(prix, taux, echecs)
    }

    /** Applique les cotations au compte tel qu'il est maintenant ; sa valeur suit celle des titres. */
    fun appliquer(compte: CompteInvestissement, cotations: Cotations): CompteInvestissement {
        var d = compte.details.copy(tauxVersCad = compte.details.tauxVersCad.orEmpty() + cotations.taux)
        cotations.prix.forEach { (symbole, c) ->
            if (d.positions.any { it.symbole == symbole }) {
                d = Portefeuille.mettreCours(d, symbole, c.prix, aujourdhui(), c.devise, c.symboleCotation)
            }
        }
        return avecValeurDuPortefeuille(compte, d, noterValeurDeMarche = true)
    }

    /**
     * Tous les comptes de placement qui ont des titres, au plus une fois par [intervalleMs].
     * Appelé à l'ouverture de l'app : la page Comptes montre ainsi un gain à jour sans rien toucher.
     */
    suspend fun actualiserTout(intervalleMs: Long = 15 * 60 * 1000L) {
        val maintenant = System.currentTimeMillis()
        if (maintenant - derniereActualisation < intervalleMs) return
        val comptes = compteRepository.recupererTousLesComptes().getOrNull().orEmpty()
            .filterIsInstance<CompteInvestissement>()
            .filter { !it.estArchive && it.details.positions.isNotEmpty() }
        // Pas encore connecté ou aucun titre : on réessaiera à la prochaine ouverture
        if (comptes.isEmpty()) return
        derniereActualisation = maintenant
        var modifie = false
        comptes.forEach { compte ->
            val cotations = coter(compte.details.positions)
            if (cotations.vide) return@forEach
            runCatching {
                operationAtomique.executer {
                    val aJour = compteRepository.getCompteById(compte.id, "comptes_investissement") as? CompteInvestissement
                    if (aJour != null && !aJour.estArchive) {
                        val resultat = appliquer(aJour, cotations)
                        // Même prix qu'avant : aucune écriture ni synchro inutile
                        if (resultat.solde != aJour.solde || resultat.details != aJour.details) {
                            compteRepository.mettreAJourCompte(resultat).getOrThrow()
                            modifie = true
                        }
                    }
                }
            }
        }
        if (modifie) realtimeSyncService.declencherMiseAJourComptes()
    }

    companion object {
        fun aujourdhui(): String = MoteurCarteCredit.ecrireDate(java.util.Date())

        /**
         * Les titres décident de la valeur du compte : le solde devient la valeur du portefeuille (titres + argent
         * non placé) dès qu'elle est connue. Un changement de cours est noté comme valeur de marché datée (IF7),
         * une seule par jour : les actualisations suivantes du même jour remplacent la note du jour.
         */
        fun avecValeurDuPortefeuille(
            compte: CompteInvestissement,
            details: DetailsInvestissement,
            noterValeurDeMarche: Boolean
        ): CompteInvestissement {
            val valeur = Portefeuille.valeurCompte(details, compte.solde)
            val avecTrace = if (noterValeurDeMarche && valeur != null && kotlin.math.abs(valeur - compte.solde) > 0.005) {
                val jour = aujourdhui()
                val derniere = details.valorisations.lastOrNull()
                    ?.takeIf { it.date == jour && it.type == TypeValorisation.MARCHE && it.note == NOTE_COURS }
                val trace = Valorisation(
                    date = jour, valeur = valeur, ancienneValeur = derniere?.ancienneValeur ?: compte.solde,
                    type = TypeValorisation.MARCHE, note = NOTE_COURS
                )
                details.copy(valorisations = if (derniere != null) details.valorisations.dropLast(1) + trace
                    else details.valorisations + trace)
            } else details
            return compte.copy(solde = valeur ?: compte.solde, detailsInvestissementJson = avecTrace.versJson())
        }

        private const val NOTE_COURS = "Cours des titres à jour"
    }
}
