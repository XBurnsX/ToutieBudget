package com.xburnsx.toutiebudget.ui.virement

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

        fun soldeInsuffisant(soldeDisponible: Double): String {
            return "Solde insuffisant dans la source sélectionnée. Solde disponible: ${String.format("%.2f", soldeDisponible)}$"
        }
    }

    // ===== ERREURS PRÊT À PLACER → ENVELOPPE =====

    object PretAPlacerVersEnveloppe {
        const val COMPTE_VERS_PRET_A_PLACER = """❌ ERREUR DE CONFIGURATION

Impossible de virer d'un compte vers un prêt à placer.
Veuillez sélectionner une enveloppe comme destination."""

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
        const val AUCUN_COMPTE_CHEQUE_DISPONIBLE = "Aucun compte chèque disponible pour le virement"
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

    object CompteVersCompte {
        fun virementEchoue(message: String): String {
            return "Erreur lors du virement entre comptes: $message"
        }
    }

    // ===== ERREURS CLAVIER BUDGET =====

    object ClavierBudget {
        const val COMPTE_NON_SELECTIONNE = "Veuillez sélectionner un compte source."
        const val MONTANT_ZERO = "Veuillez entrer un montant supérieur à zéro."

        fun fondsInsuffisants(soldeDisponible: Double): String {
            val formateurMonetaire = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.CANADA_FRENCH)
            return "⚠️ Fonds insuffisants. Disponible : ${formateurMonetaire.format(soldeDisponible)}"
        }

        fun assignationReussie(nomEnveloppe: String): String {
            return "✅ Montant assigné à $nomEnveloppe avec succès !"
        }

        fun montantSuperieurDisponible(montantDemande: Double, soldeDisponible: Double): String {
            val formateurMonetaire = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.CANADA_FRENCH)
            return "Le montant demandé (${formateurMonetaire.format(montantDemande)}) dépasse le solde disponible (${formateurMonetaire.format(soldeDisponible)})"
        }
    }

    // ===== MESSAGES DE SUCCÈS =====

    object Succes {
        const val VIREMENT_REUSSI = "✅ Virement effectué avec succès !"
        const val DONNEES_RECHARGEES = "✅ Données rechargées"
        const val VALIDATION_OK = "✅ Validation de provenance OK"
    }

    // ===== MESSAGES DE DEBUG =====

    object Debug {
        const val VALIDATION_COMPTE_VERS_COMPTE = "🔍 Virement Compte vers Compte - Aucune validation de provenance nécessaire"
        const val VALIDATION_COMPTE_VERS_ENVELOPPE = "🔍 Validation: Compte vers Enveloppe"
        const val VALIDATION_ENVELOPPE_VERS_COMPTE = "🔍 Validation: Enveloppe vers Compte"
        const val VALIDATION_ENVELOPPE_VERS_ENVELOPPE = "🔍 Validation: Enveloppe vers Enveloppe"
        const val VALIDATION_ENVELOPPE_VERS_PRET_A_PLACER = "🔍 Validation: Enveloppe vers Prêt à placer"

        const val VIREMENT_COMPTE_VERS_COMPTE = "🔄 Virement Compte vers Compte..."
        const val VIREMENT_COMPTE_VERS_ENVELOPPE = "🔄 Virement Compte vers Enveloppe..."
        const val VIREMENT_ENVELOPPE_VERS_COMPTE = "🔄 Virement Enveloppe vers Compte..."
        const val VIREMENT_ENVELOPPE_VERS_ENVELOPPE = "🔄 Virement Enveloppe vers Enveloppe..."
        const val VIREMENT_ENVELOPPE_VERS_PRET_A_PLACER = "🔄 Virement Enveloppe vers Prêt à placer..."
    }

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
