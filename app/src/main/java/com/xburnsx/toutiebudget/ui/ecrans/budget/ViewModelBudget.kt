package com.xburnsx.toutiebudget.ui.ecrans.budget

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.domaine.depot.DepotCompte
import com.xburnsx.toutiebudget.domaine.depot.DepotEnveloppe
import com.xburnsx.toutiebudget.domaine.modele.Compte
import com.xburnsx.toutiebudget.domaine.modele.Enveloppe
import com.xburnsx.toutiebudget.domaine.modele.EtatEnveloppeMensuel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ViewModelBudget @Inject constructor(
    private val depotCompte: DepotCompte,
    private val depotEnveloppe: DepotEnveloppe
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatBudget())
    val etat = _etat.asStateFlow()

    init {
        chargerDonnees()
    }

    private fun chargerDonnees() {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val moisActuel = sdf.format(Date())

            combine(
                depotCompte.recupererTousLesComptes(),
                depotEnveloppe.recupererToutesLesEnveloppes(),
                depotEnveloppe.recupererEtatsMensuels(moisActuel)
            ) { comptes, enveloppesConfig, etatsMensuels ->
                val enveloppesUi = creerEnveloppesUi(enveloppesConfig, etatsMensuels, comptes)
                EtatBudget(
                    comptes = comptes,
                    enveloppes = enveloppesUi,
                    isLoading = false
                )
            }.catch { e ->
                e.printStackTrace()
            }.collect { etatCalcule ->
                _etat.value = etatCalcule
            }
        }
    }

    private fun creerEnveloppesUi(
        enveloppes: List<Enveloppe>,
        etatsMensuels: List<EtatEnveloppeMensuel>,
        comptes: List<Compte>
    ): List<EnveloppeUi> {
        return enveloppes.map { enveloppe ->
            val etatActuel = etatsMensuels.find { it.idEnveloppe == enveloppe.id }
                ?: EtatEnveloppeMensuel("", enveloppe.id, "", 0.0, 0.0, null)

            val objectif = enveloppe.objectifMontant
            val aUnObjectif = objectif > 0.0

            val statut: StatutObjectif
            val pourcentage: Float

            if (!aUnObjectif) {
                statut = StatutObjectif.AUCUN
                pourcentage = 0f
            } else {
                if (etatActuel.depensesDuMois >= objectif) {
                    statut = StatutObjectif.DEPENSE
                    pourcentage = 1f
                } else if (etatActuel.solde >= objectif) {
                    statut = StatutObjectif.ATTEINT
                    pourcentage = 1f
                } else {
                    statut = StatutObjectif.EN_COURS
                    pourcentage = (etatActuel.solde / objectif).toFloat().coerceIn(0f, 1f)
                }
            }

            val couleurProvenance = comptes.find { it.id == etatActuel.idCompteProvenance }?.couleurCompose ?: Color.Gray.copy(alpha = 0.5f)
            val couleurStatutLateral = when (statut) {
                StatutObjectif.AUCUN, StatutObjectif.EN_COURS -> if (etatActuel.solde > 0) Color(0xFFFFC107) else Color.Gray
                StatutObjectif.ATTEINT, StatutObjectif.DEPENSE -> Color(0xFF4CAF50)
            }

            val texteObjectif = "${String.format("%.2f", objectif)} $ pour le ${enveloppe.jourObjectif ?: ""}"

            EnveloppeUi(
                enveloppeOriginale = enveloppe,
                etatMensuel = etatActuel,
                statutObjectif = statut,
                pourcentage = pourcentage,
                texteObjectif = texteObjectif,
                couleurProvenance = couleurProvenance,
                couleurStatutLateral = couleurStatutLateral
            )
        }
    }
}