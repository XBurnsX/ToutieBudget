package com.xburnsx.toutiebudget.ui.virement

import android.annotation.SuppressLint

/**
 * Centralise tous les messages d'erreur pour les virements d'argent.
 * Organisé par type de virement et situation d'erreur.
 */
object VirementErrorMessages {

    // ===== ERREURS GÉNÉRALES =====

    object General {
        const val SOURCE_NULL = "Veuillez sélectionner une source."
        const val DESTINATION_NULL = "Veuillez sélectionner une destination."
        const val MONTANT_INVALIDE = "Veuillez entrer un montant valide."
        const val SOURCE_DESTINATION_IDENTIQUES = "La source et la destination ne peuvent pas être identiques."
        const val TYPE_VIREMENT_NON_SUPPORTE = "Type de virement non supporté."

        @SuppressLint("DefaultLocale")
        fun soldeInsuffisant(soldeDisponible: Double): String {
            return "Solde insuffisant dans la source sélectionnée. Solde disponible: ${String.format("%.2f", soldeDisponible)}$"
        }
    }

    // ===== ERREURS PRÊT À PLACER → ENVELOPPE =====

    object PretAPlacerVersEnveloppe {

        fun enveloppeIntrouvable(nomEnveloppe: String): String {
            return "Enveloppe destination introuvable: $nomEnveloppe"
        }

        fun conflitProvenance(
            nomCompteExistant: String,
            nomCompteTente: String
        ): String {
            return """❌ CONFLIT DE PROVENANCE !

Cette enveloppe contient déjà de l'argent provenant d'un autre compte.

• Provenance actuelle : $nomCompteExistant
• Provenance tentée : $nomCompteTente

Vous ne pouvez pas mélanger l'argent de différents comptes dans une même enveloppe."""
        }
    }

    // ===== ERREURS ENVELOPPE → ENVELOPPE =====

    object EnveloppeVersEnveloppe {
        const val ENVELOPPE_SOURCE_INTROUVABLE = "Enveloppe source introuvable"
        const val ENVELOPPE_DESTINATION_INTROUVABLE = "Enveloppe destination introuvable"
        const val ENVELOPPE_SOURCE_VIDE = "L'enveloppe source ne contient pas d'argent à transférer"

        fun conflitProvenance(
            nomCompteSource: String,
            nomCompteCible: String
        ): String {
            return """❌ CONFLIT DE PROVENANCE !

Les deux enveloppes contiennent de l'argent de comptes différents.

• Enveloppe source : $nomCompteSource
• Enveloppe cible : $nomCompteCible

Veuillez vous assurer que les deux enveloppes partagent la même provenance."""
        }
    }

    // ===== ERREURS ENVELOPPE → PRÊT À PLACER =====

    object EnveloppeVersPretAPlacer {
        const val COMPTE_DESTINATION_INTROUVABLE = "Compte destination introuvable pour le prêt à placer"

        fun enveloppeSourceIntrouvable(nomEnveloppe: String): String {
            return "Enveloppe source introuvable: $nomEnveloppe"
        }

        fun conflitProvenance(
            nomCompteSource: String,
            nomCompteCible: String
        ): String {
            return """❌ CONFLIT DE PROVENANCE !

L'argent de cette enveloppe provient d'un autre compte.

• Provenance de l'enveloppe : $nomCompteSource
• Compte de destination : $nomCompteCible

Vous ne pouvez retourner l'argent que vers son compte d'origine."""
        }
    }

    // ===== ERREURS COMPTE → COMPTE =====

    // ===== ERREURS CLAVIER BUDGET =====

    object ClavierBudget {
        const val COMPTE_NON_SELECTIONNE = "Veuillez sélectionner un compte source."

        fun fondsInsuffisants(soldeDisponible: Double): String {
            val formateurMonetaire = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.CANADA_FRENCH)
            return "⚠️ Fonds insuffisants. Disponible : ${formateurMonetaire.format(soldeDisponible)}"
        }

    }

    // ===== MESSAGES DE SUCCÈS =====

    // ===== MESSAGES DE DEBUG =====

    // ===== UTILITAIRES =====

    /**
     * Détermine si un message d'erreur doit être affiché dans un dialogue spécialisé
     */
    fun estErreurProvenance(message: String): Boolean {
        return message.contains("CONFLIT DE PROVENANCE") ||
               message.contains("ERREUR DE CONFIGURATION")
    }

    /**
     * Extrait le titre approprié pour le dialogue d'erreur
     */
    fun obtenirTitreDialogue(message: String): String {
        return when {
            message.contains("CONFLIT DE PROVENANCE") -> "Conflit de provenance"
            message.contains("ERREUR DE CONFIGURATION") -> "Erreur de configuration"
            else -> "Erreur"
        }
    }
}
