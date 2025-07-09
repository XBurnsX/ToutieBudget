package com.xburnsx.toutiebudget.ui.ecrans.connexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.domaine.depot.DepotAuthentification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewModelConnexion @Inject constructor(
    private val depotAuth: DepotAuthentification
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatConnexion())
    val etat = _etat.asStateFlow()

    fun connexionAvecGoogle(idToken: String) {
        viewModelScope.launch {
            _etat.update { it.copy(isLoading = true) }
            val result = depotAuth.connexionAvecGoogle(idToken)
            _etat.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, connexionReussie = true, erreurConnexion = null)
                } else {
                    it.copy(
                        isLoading = false,
                        connexionReussie = false,
                        erreurConnexion = result.exceptionOrNull()?.message ?: "Erreur inconnue"
                    )
                }
            }
        }
    }
}
