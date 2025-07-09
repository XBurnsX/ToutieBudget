package com.xburnsx.toutiebudget.ui.ecrans.budget

import androidx.compose.ui.graphics.Color
import com.xburnsx.toutiebudget.domaine.modele.Compte
import com.xburnsx.toutiebudget.domaine.modele.Enveloppe
import com.xburnsx.toutiebudget.domaine.modele.EtatEnveloppeMensuel

enum class StatutObjectif {
    AUCUN, EN_COURS, ATTEINT, DEPENSE
}

data class EnveloppeUi(
    val enveloppeOriginale: Enveloppe,
    val etatMensuel: EtatEnveloppeMensuel,
    val statutObjectif: StatutObjectif,
    val pourcentage: Float,
    val texteObjectif: String,
    val couleurProvenance: Color,
    val couleurStatutLateral: Color
)

data class EtatBudget(
    val comptes: List<Compte> = emptyList(),
    val enveloppes: List<EnveloppeUi> = emptyList(),
    val isLoading: Boolean = true
)
