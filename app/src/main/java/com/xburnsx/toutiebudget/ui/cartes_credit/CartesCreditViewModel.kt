package com.xburnsx.toutiebudget.ui.cartes_credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import com.xburnsx.toutiebudget.data.repositories.impl.CarteCreditRepositoryImpl
import com.xburnsx.toutiebudget.data.repositories.impl.CompteRepositoryImpl
import com.xburnsx.toutiebudget.data.services.CarteCreditNotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class CartesCreditViewModel : ViewModel() {

    private val carteCreditRepository: CarteCreditRepository = CarteCreditRepositoryImpl(CompteRepositoryImpl())
    private val notificationService: CarteCreditNotificationService = CarteCreditNotificationService(carteCreditRepository)

    private val _uiState = MutableStateFlow(CartesCreditUiState())
    val uiState: StateFlow<CartesCreditUiState> = _uiState.asStateFlow()

    private val _formulaire = MutableStateFlow(FormulaireCarteCredit())
    val formulaire: StateFlow<FormulaireCarteCredit> = _formulaire.asStateFlow()

    init {
        chargerCartesCredit()
        chargerAlertes()
    }

    /**
     * Charge toutes les cartes de crédit de l'utilisateur.
     */
    fun chargerCartesCredit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true, messageErreur = null)

            carteCreditRepository.recupererCartesCredit()
                .onSuccess { cartes ->
                    _uiState.value = _uiState.value.copy(
                        cartesCredit = cartes,
                        estEnChargement = false
                    )
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors du chargement: ${erreur.message}"
                    )
                }
        }
    }

    /**
     * Charge les alertes pour toutes les cartes de crédit.
     */
    fun chargerAlertes() {
        viewModelScope.launch {
            try {
                val alertes = notificationService.verifierAlertes()
                _uiState.value = _uiState.value.copy(alertes = alertes)
            } catch (e: Exception) {
                // Gérer l'erreur silencieusement pour les alertes
            }
        }
    }

    /**
     * Charge une carte de crédit spécifique par son ID.
     */
    fun chargerCarteSpecifique(carteCreditId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true, messageErreur = null)

            carteCreditRepository.recupererCartesCredit()
                .onSuccess { cartes ->
                    val carteSpecifique = cartes.find { it.id == carteCreditId }
                    _uiState.value = _uiState.value.copy(
                        carteSelectionnee = carteSpecifique,
                        estEnChargement = false
                    )
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors du chargement: ${erreur.message}"
                    )
                }
        }
    }

    /**
     * Sélectionne une carte de crédit pour afficher ses détails.
     */
    fun selectionnerCarte(carte: CompteCredit) {
        _uiState.value = _uiState.value.copy(carteSelectionnee = carte)
    }

    /**
     * Calcule les statistiques pour une carte de crédit.
     */
    fun calculerStatistiques(carte: CompteCredit): StatistiquesCarteCredit {
        return StatistiquesCarteCredit(
            creditDisponible = carteCreditRepository.calculerCreditDisponible(carte),
            tauxUtilisation = carteCreditRepository.calculerTauxUtilisation(carte),
            interetsMensuels = carteCreditRepository.calculerInteretsMensuels(carte),
            paiementMinimum = carteCreditRepository.calculerPaiementMinimum(carte),
            tempsRemboursementMinimum = carteCreditRepository.calculerTempsRemboursement(
                carte,
                carteCreditRepository.calculerPaiementMinimum(carte)
            ),
            totalInteretsAnnuels = carteCreditRepository.calculerInteretsMensuels(carte) * 12
        )
    }

    /**
     * Affiche le dialog d'ajout d'une nouvelle carte.
     */
    fun afficherDialogAjout() {
        _formulaire.value = FormulaireCarteCredit()
        _uiState.value = _uiState.value.copy(afficherDialogAjout = true)
    }

    /**
     * Affiche le dialog de modification d'une carte existante.
     */
    fun afficherDialogModification(carte: CompteCredit) {
        _formulaire.value = FormulaireCarteCredit(
            nom = carte.nom,
            limiteCredit = carte.limiteCredit.toString(),
            tauxInteret = carte.tauxInteret?.toString() ?: "", // Changé interet vers tauxInteret
            soldeActuel = (-carte.solde).toString(), // Affiche la dette comme positive
            couleur = carte.couleur
        )
        _uiState.value = _uiState.value.copy(
            carteSelectionnee = carte,
            afficherDialogModification = true
        )
    }

    /**
     * Met à jour le champ nom dans le formulaire.
     */
    fun mettreAJourNom(nom: String) {
        val erreur = if (nom.isBlank()) "Le nom est requis" else null
        _formulaire.value = _formulaire.value.copy(nom = nom, erreurNom = erreur)
    }

    /**
     * Met à jour la limite de crédit dans le formulaire.
     */
    fun mettreAJourLimiteCredit(limite: String) {
        val erreur = try {
            val valeur = limite.toDoubleOrNull()
            when {
                limite.isBlank() -> "La limite de crédit est requise"
                valeur == null -> "Montant invalide"
                valeur <= 0 -> "La limite doit être positive"
                else -> null
            }
        } catch (e: Exception) {
            "Montant invalide"
        }
        _formulaire.value = _formulaire.value.copy(limiteCredit = limite, erreurLimite = erreur)
    }

    /**
     * Met à jour le taux d'intérêt dans le formulaire.
     */
    fun mettreAJourTauxInteret(taux: String) {
        val erreur = try {
            val valeur = taux.toDoubleOrNull()
            when {
                taux.isBlank() -> null // Optionnel
                valeur == null -> "Taux invalide"
                valeur < 0 -> "Le taux ne peut pas être négatif"
                valeur > 100 -> "Taux trop élevé"
                else -> null
            }
        } catch (e: Exception) {
            "Taux invalide"
        }
        _formulaire.value = _formulaire.value.copy(tauxInteret = taux, erreurTaux = erreur)
    }

    /**
     * Met à jour le solde actuel dans le formulaire.
     */
    fun mettreAJourSoldeActuel(solde: String) {
        val erreur = try {
            val valeur = solde.toDoubleOrNull()
            when {
                solde.isBlank() -> "Le solde actuel est requis"
                valeur == null -> "Montant invalide"
                valeur < 0 -> "Le solde ne peut pas être négatif"
                else -> null
            }
        } catch (e: Exception) {
            "Montant invalide"
        }
        _formulaire.value = _formulaire.value.copy(soldeActuel = solde, erreurSolde = erreur)
    }

    /**
     * Met à jour la couleur dans le formulaire.
     */
    fun mettreAJourCouleur(couleur: String) {
        _formulaire.value = _formulaire.value.copy(couleur = couleur)
    }

    /**
     * Sauvegarde une nouvelle carte de crédit.
     */
    fun sauvegarderNouvelleCarteCredit() {
        if (!_formulaire.value.estValide) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)

            val formulaire = _formulaire.value
            val nouvelleCarteCredit = CompteCredit(
                nom = formulaire.nom,
                soldeUtilise = -(formulaire.soldeActuel.toDoubleOrNull() ?: 0.0), // Changé solde vers soldeUtilise
                limiteCredit = formulaire.limiteCredit.toDoubleOrNull() ?: 0.0,
                tauxInteret = formulaire.tauxInteret.toDoubleOrNull(), // Changé interet vers tauxInteret
                couleur = formulaire.couleur,
                estArchive = false,
                ordre = _uiState.value.cartesCredit.size + 1
            )

            carteCreditRepository.creerCarteCredit(nouvelleCarteCredit)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(afficherDialogAjout = false)
                    chargerCartesCredit()
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors de la création: ${erreur.message}"
                    )
                }
        }
    }

    /**
     * Sauvegarde les modifications d'une carte existante.
     */
    fun sauvegarderModificationsCarteCredit() {
        val carteOriginale = _uiState.value.carteSelectionnee ?: return
        if (!_formulaire.value.estValide) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)

            val formulaire = _formulaire.value
            val carteModifiee = carteOriginale.copy(
                nom = formulaire.nom,
                soldeUtilise = -(formulaire.soldeActuel.toDoubleOrNull() ?: 0.0),
                limiteCredit = formulaire.limiteCredit.toDoubleOrNull() ?: 0.0,
                tauxInteret = formulaire.tauxInteret.toDoubleOrNull(),
                couleur = formulaire.couleur
            )

            carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(afficherDialogModification = false)
                    chargerCartesCredit()
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors de la modification: ${erreur.message}"
                    )
                }
        }
    }

    /**
     * Génère et affiche un plan de remboursement.
     */
    fun genererPlanRemboursement(carte: CompteCredit, paiementMensuel: Double) {
        val plan = carteCreditRepository.genererPlanRemboursement(carte, paiementMensuel)
        _uiState.value = _uiState.value.copy(
            planRemboursement = plan,
            paiementMensuelSimulation = paiementMensuel,
            afficherPlanRemboursement = true
        )
    }

    /**
     * Ferme tous les dialogs.
     */
    fun fermerDialogs() {
        _uiState.value = _uiState.value.copy(
            afficherDialogAjout = false,
            afficherDialogModification = false,
            afficherDialogSuppression = false,
            afficherPlanRemboursement = false,
            afficherDialogModificationFrais = false
        )
    }

    /**
     * Efface le message d'erreur.
     */
    fun effacerErreur() {
        _uiState.value = _uiState.value.copy(messageErreur = null)
    }

    /**
     * Effectue un paiement minimum sur une carte de crédit.
     */
    fun effectuerPaiementMinimum(carte: CompteCredit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)
            
            try {
                val paiementMinimum = carteCreditRepository.calculerPaiementMinimum(carte)
                
                // TODO: Intégrer avec le service de transactions
                // argentService.effectuerPaiementCarteCredit(
                //     carteId = carte.id,
                //     montant = paiementMinimum,
                //     type = "Paiement minimum"
                // )
                
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Fonctionnalité à implémenter"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Erreur lors du paiement: ${e.message}"
                )
            }
        }
    }

    /**
     * Effectue un paiement complet sur une carte de crédit.
     */
    fun effectuerPaiementComplet(carte: CompteCredit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)
            
            try {
                val montantComplet = abs(carte.solde)
                
                // TODO: Intégrer avec le service de transactions
                // argentService.effectuerPaiementCarteCredit(
                //     carteId = carte.id,
                //     montant = montantComplet,
                //     type = "Paiement complet"
                // )
                
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Fonctionnalité à implémenter"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Erreur lors du paiement: ${e.message}"
                )
            }
        }
    }

    /**
     * Applique les intérêts mensuels sur toutes les cartes de crédit.
     */
    fun appliquerInteretsMensuels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)
            
            try {
                val cartes = carteCreditRepository.recupererCartesCredit().getOrThrow()
                
                cartes.forEach { carte ->
                    if (carte.tauxInteret != null && carte.tauxInteret!! > 0) {
                        carteCreditRepository.appliquerInteretsMensuels(carte.id)
                    }
                }
                
                chargerCartesCredit() // Recharger les données
                _uiState.value = _uiState.value.copy(estEnChargement = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Erreur lors de l'application des intérêts: ${e.message}"
                )
            }
        }
    }

    /**
     * Navigue vers l'écran d'ajout de transaction pour cette carte.
     */
    fun ajouterDepenseCarte(carte: CompteCredit) {
        // TODO: Implémenter la navigation vers l'écran d'ajout de transaction
        // avec la carte pré-sélectionnée
        _uiState.value = _uiState.value.copy(
            messageErreur = "Navigation vers ajout de transaction à implémenter"
        )
    }

    /**
     * Affiche le dialog de modification des frais mensuels fixes.
     */
    fun afficherDialogModificationFrais(carte: CompteCredit) {
        _formulaire.value = _formulaire.value.copy(
            fraisMensuelsFixes = carte.fraisMensuelsFixes?.toString() ?: "",
            nomFraisMensuels = carte.nomFraisMensuels ?: ""
        )
        _uiState.value = _uiState.value.copy(
            carteSelectionnee = carte,
            afficherDialogModificationFrais = true
        )
    }

    /**
     * Met à jour les frais mensuels fixes dans le formulaire.
     */
    fun mettreAJourFraisMensuelsFixes(frais: String) {
        val erreur = try {
            val valeur = frais.toDoubleOrNull()
            when {
                frais.isBlank() -> null // Optionnel
                valeur == null -> "Montant invalide"
                valeur < 0 -> "Les frais ne peuvent pas être négatifs"
                else -> null
            }
        } catch (e: Exception) {
            "Montant invalide"
        }
        _formulaire.value = _formulaire.value.copy(fraisMensuelsFixes = frais, erreurFrais = erreur)
    }

    /**
     * Met à jour le nom des frais mensuels fixes dans le formulaire.
     */
    fun mettreAJourNomFraisMensuels(nom: String) {
        val erreur = if (nom.isBlank()) "Le nom du frais est requis" else null
        _formulaire.value = _formulaire.value.copy(nomFraisMensuels = nom, erreurNomFrais = erreur)
    }

    /**
     * Sauvegarde les modifications des frais mensuels fixes.
     */
    fun sauvegarderModificationFrais() {
        val carteOriginale = _uiState.value.carteSelectionnee ?: return
        if (_formulaire.value.erreurFrais != null || _formulaire.value.erreurNomFrais != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)

            val frais = _formulaire.value.fraisMensuelsFixes.toDoubleOrNull()
            val nomFrais = _formulaire.value.nomFraisMensuels.takeIf { it.isNotBlank() }
            val carteModifiee = carteOriginale.copy(
                fraisMensuelsFixes = frais,
                nomFraisMensuels = nomFrais
            )

            carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        afficherDialogModificationFrais = false,
                        estEnChargement = false
                    )
                    chargerCartesCredit()
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors de la modification des frais: ${erreur.message}"
                    )
                }
        }
    }
}
