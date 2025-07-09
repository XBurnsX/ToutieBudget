package com.xburnsx.toutiebudget

import androidx.lifecycle.ViewModel
import com.xburnsx.toutiebudget.domaine.depot.DepotAuthentification
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Le cerveau de notre MainActivity.
 * Son unique rôle est de demander l'état de connexion au dépôt d'authentification.
 * @HiltViewModel indique à Hilt comment créer ce ViewModel.
 * @Inject constructor(...) est la magie de Hilt : il voit que ce ViewModel a besoin
 * d'un DepotAuthentification, et il va le fournir automatiquement.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val depotAuth: DepotAuthentification
) : ViewModel() {
    // On expose directement le Flow du dépôt. L'UI pourra s'y abonner.
    val etatAuth = depotAuth.recupererEtatAuth()
}
