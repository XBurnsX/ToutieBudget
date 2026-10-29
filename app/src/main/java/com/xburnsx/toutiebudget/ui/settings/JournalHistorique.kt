package com.xburnsx.toutiebudget.ui.settings

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.ui.historique.RechercheHistorique
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Règles pures de l'historique général (section 22), sans Android : libellés comptables,
 * dates, regroupement, recherche, doublons et export CSV. Elles ne modifient aucune donnée.
 */
object JournalHistorique {

    /** HG12 : nature comptable, pour ne jamais présenter un mouvement interne comme un revenu. */
    enum class Nature(val libelle: String) {
        DEPENSE("Dépense"), REVENU("Revenu"), VIREMENT("Virement entre comptes"),
        PAIEMENT("Paiement de carte ou de dette"), PRET("Prêt / remboursement"),
        ALLOCATION("Mouvement interne du budget"), CORRECTION("Correction de solde"),
        EFFET_TRANSACTION("Effet d'une transaction sur le budget")
    }

    fun nature(t: Transaction): Nature = when (t.type) {
        TypeTransaction.Depense -> Nature.DEPENSE
        TypeTransaction.Revenu -> Nature.REVENU
        TypeTransaction.TransfertSortant, TypeTransaction.TransfertEntrant -> Nature.VIREMENT
        TypeTransaction.Paiement, TypeTransaction.PaiementEffectue -> Nature.PAIEMENT
        else -> Nature.PRET
    }

    fun nature(h: HistoriqueAllocation): Nature = when (h.typeAction.uppercase()) {
        "CREATION", "MODIFICATION", "SUPPRESSION" -> Nature.ALLOCATION
        "TRANSACTION" -> Nature.EFFET_TRANSACTION
        else -> Nature.CORRECTION
    }

    /** Sens affiché d'une transaction : sortie (−) ou entrée (+) pour SON compte. */
    fun montantSigne(t: Transaction): Double = when (t.type) {
        TypeTransaction.Depense, TypeTransaction.Pret, TypeTransaction.RemboursementDonne,
        TypeTransaction.PaiementEffectue, TypeTransaction.TransfertSortant -> -kotlin.math.abs(t.montant)
        else -> kotlin.math.abs(t.montant)
    }

    // ----- HG11 : dates -----
    private val formatUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /** Le journal est écrit en UTC ; on lit en UTC puis on affiche dans le fuseau de l'appareil. */
    fun instantJournal(dateAction: String): Instant? = runCatching {
        LocalDateTime.parse(dateAction.trim().take(19), formatUtc).toInstant(ZoneOffset.UTC)
    }.getOrNull()

    fun jour(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? = instant?.atZone(zone)?.toLocalDate()

    fun heureLocale(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.atZone(zone)?.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.CANADA_FRENCH)) ?: "date inconnue"

    fun titreJour(jour: LocalDate?): String = jour?.format(
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.CANADA_FRENCH)
    )?.replaceFirstChar { it.uppercase() } ?: "Date inconnue"

    /** HG13 : un même événement reçu deux fois (même effet, même seconde) n'est affiché qu'une fois. */
    fun sansDoublons(journal: List<HistoriqueAllocation>): List<HistoriqueAllocation> =
        journal.distinctBy { it.id }.distinctBy {
            listOf(it.utilisateurId, it.compteId, it.allocationId, it.enveloppeId, it.typeAction,
                Math.round(it.montant * 100.0), it.dateAction).joinToString("|")
        }

    /** HG14 : solde de l'enveloppe avant/après noté au moment du mouvement (null pour les anciennes entrées). */
    fun soldeEnveloppe(details: String?): Pair<Double, Double>? {
        if (details == null) return null
        val valeurs = details.split(';').mapNotNull { morceau ->
            val (cle, valeur) = morceau.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            valeur.toLongOrNull()?.let { cle to it / 100.0 }
        }.toMap()
        val avant = valeurs["avant"] ?: return null
        val apres = valeurs["apres"] ?: return null
        return avant to apres
    }

    /** Le Prêt à placer n'existe que pour un compte chèque (jamais inventé pour une carte, dette ou placement). */
    fun aPretAPlacer(collectionCompte: String): Boolean =
        collectionCompte == "compte_cheque" || collectionCompte == "comptes_cheques"

    // ----- HGA3 : recherche légère (accents et casse ignorés) -----
    fun correspond(t: Transaction, requete: String, nomCompte: String, nomsEnveloppes: String): Boolean {
        val q = RechercheHistorique.normaliser(requete)
        if (q.isBlank()) return true
        val texte = RechercheHistorique.normaliser(listOfNotNull(
            t.tiersUtiliser, t.note, t.description, nomCompte, nomsEnveloppes, t.type.libelle,
            MoneyFormatter.formatAmount(t.montant), String.format(Locale.ROOT, "%.2f", t.montant)
        ).joinToString(" "))
        return q.split(' ').filter { it.isNotBlank() }.all { texte.contains(it) }
    }

    fun correspond(h: HistoriqueAllocation, requete: String, nomCompte: String): Boolean {
        val q = RechercheHistorique.normaliser(requete)
        if (q.isBlank()) return true
        val texte = RechercheHistorique.normaliser(listOf(
            h.description, h.enveloppeNom, nomCompte, h.typeAction, h.details.orEmpty(),
            MoneyFormatter.formatAmount(h.montant), String.format(Locale.ROOT, "%.2f", h.montant)
        ).joinToString(" "))
        return q.split(' ').filter { it.isNotBlank() }.all { texte.contains(it) }
    }

    // ----- HGA5 : export CSV (lecture seule) -----
    private fun champ(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""
    private fun montantCsv(v: Double): String = String.format(Locale.ROOT, "%.2f", v)

    fun csvTransactions(lignes: List<Transaction>, nomCompte: (Transaction) -> String): String = buildString {
        appendLine("date;compte;nature;type;tiers;montant_signe;note;id_transaction")
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        lignes.forEach { t ->
            appendLine(listOf(
                champ(f.format(t.date)), champ(nomCompte(t)), champ(nature(t).libelle), champ(t.type.libelle),
                champ(t.tiersUtiliser.orEmpty()), montantCsv(montantSigne(t)), champ(t.note.orEmpty()), champ(t.id)
            ).joinToString(";"))
        }
    }

    fun csvJournal(lignes: List<HistoriqueAllocation>, nomCompte: (HistoriqueAllocation) -> String): String = buildString {
        appendLine("date_locale;compte;nature;enveloppe;description;montant;solde_avant;solde_apres;pret_a_placer_avant;pret_a_placer_apres;id_transaction;id_virement;id_allocation")
        lignes.forEach { h ->
            appendLine(listOf(
                champ(heureLocale(instantJournal(h.dateAction))), champ(nomCompte(h)), champ(nature(h).libelle),
                champ(h.enveloppeNom), champ(h.description), montantCsv(h.montant),
                montantCsv(h.soldeAvant), montantCsv(h.soldeApres),
                if (aPretAPlacer(h.collectionCompte)) montantCsv(h.pretAPlacerAvant) else "",
                if (aPretAPlacer(h.collectionCompte)) montantCsv(h.pretAPlacerApres) else "",
                champ(h.transactionId.orEmpty()), champ(h.virementId.orEmpty()), champ(h.allocationId)
            ).joinToString(";"))
        }
    }
}
