package com.xburnsx.toutiebudget.ui.cartes_credit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.FraisMensuel
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.impl.CarteCreditRepositoryImpl
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Classe pour stocker les calculs du calculateur.
 */
data class CalculsCalculateur(
    val tempsRemboursement: Int?,
    val interetsTotal: Double?
)

class CartesCreditViewModel(
    private val carteCreditRepository: CarteCreditRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartesCreditUiState())
    val uiState: StateFlow<CartesCreditUiState> = _uiState.asStateFlow()

    private val _formulaire = MutableStateFlow(FormulaireCarteCredit())
    val formulaire: StateFlow<FormulaireCarteCredit> = _formulaire.asStateFlow()

    // Calculs du calculateur
    private val _calculsCalculateur = mutableStateOf<CalculsCalculateur?>(null)
    val calculsCalculateur: CalculsCalculateur? by _calculsCalculateur

    init {
        chargerCartesCredit()
        
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase pour les cartes de crédit
        viewModelScope.launch {
            realtimeSyncService.comptesUpdated.collectLatest {
                chargerCartesCredit()
            }
        }
    }

    /**
     * Charge toutes les cartes de crédit de l'utilisateur.
     */
    fun chargerCartesCredit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true, messageErreur = null)

            carteCreditRepository.recupererCartesCredit()
                .onSuccess { cartes ->
                    // Préserver la carte sélectionnée si elle existe
                    val carteSelectionneeId = _uiState.value.carteSelectionnee?.id
                    val nouvelleCarteSelectionnee = if (carteSelectionneeId != null) {
                        cartes.find { it.id == carteSelectionneeId }
                    } else {
                        _uiState.value.carteSelectionnee
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        cartesCredit = cartes,
                        carteSelectionnee = nouvelleCarteSelectionnee,
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
     * Affiche le dialog de modification d'une carte existante.
     */
    fun afficherDialogModification(carte: CompteCredit) {
        _formulaire.value = FormulaireCarteCredit(
            nom = carte.nom,
            limiteCredit = carte.limiteCredit.toString(),
            tauxInteret = carte.tauxInteret?.toString() ?: "", // Changé interet vers tauxInteret
            soldeActuel = (-carte.solde).toString(), // Affiche la dette comme positive
            paiementMinimum = carte.paiementMinimum?.toString() ?: "",
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            "Montant invalide"
        }
        _formulaire.value = _formulaire.value.copy(soldeActuel = solde, erreurSolde = erreur)
    }

    /**
     * Met à jour le paiement minimum dans le formulaire.
     */
    fun mettreAJourPaiementMinimum(paiementMinimum: String) {
        val erreur = try {
            val valeur = paiementMinimum.toDoubleOrNull()
            when {
                paiementMinimum.isBlank() -> null // Optionnel
                valeur == null -> "Montant invalide"
                valeur < 0 -> "Le paiement minimum ne peut pas être négatif"
                else -> null
            }
        } catch (_: Exception) {
            "Montant invalide"
        }
        _formulaire.value = _formulaire.value.copy(paiementMinimum = paiementMinimum, erreurPaiementMinimum = erreur)
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
                paiementMinimum = formulaire.paiementMinimum.toDoubleOrNull(),
                couleur = formulaire.couleur,
                collection = "comptes_credits"
            )

            carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(afficherDialogModification = false)
                    // Recharger les cartes (la carte sélectionnée sera préservée automatiquement)
                    chargerCartesCredit()
                    
                    // 🚀 DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR LES COMPTES
                    realtimeSyncService.declencherMiseAJourComptes()
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
        // Utiliser EXACTEMENT la même méthode de calcul que le calculateur
        val dureeRemboursement = calculerTempsRemboursement(carte, paiementMensuel)
        
        // Utiliser la durée calculée ou une valeur par défaut si impossible
        val nombreMoisMax = dureeRemboursement ?: 60
        
        val plan = carteCreditRepository.genererPlanRemboursement(carte, paiementMensuel, nombreMoisMax)
        _uiState.value = _uiState.value.copy(
            planRemboursement = plan,
            paiementMensuelSimulation = paiementMensuel,
            afficherPlanRemboursement = true
        )
    }

    /**
     * Calcule le temps de remboursement en utilisant la même logique que le calculateur.
     */
    private fun calculerTempsRemboursement(carte: CompteCredit, paiementMensuel: Double): Int? {
        val dette = abs(carte.solde)
        val taux = carte.tauxInteret ?: 0.0
        val tauxMensuel = taux / 100.0 / 12.0

        if (dette <= 0) return 0

        // Simulation mois par mois pour un calcul précis
        var soldeRestant = dette
        var mois = 0
        val maxMois = 600 // 50 ans maximum pour éviter les boucles infinies

        while (soldeRestant > 0.01 && mois < maxMois) {
            mois++
            
            // Calculer les frais moyens pour cette durée estimée
            val dureeEstimee = maxMois - mois + 1
            val fraisMensuelsMoyens = carte.calculerFraisMensuelsMoyens(dureeEstimee)
            
            // Calculer les intérêts du mois
            val interetsMois = soldeRestant * tauxMensuel
            
            // Vérifier si le paiement est suffisant
            if (paiementMensuel <= interetsMois + fraisMensuelsMoyens) {
                return null // Impossible à rembourser
            }
            
            // Calculer le capital remboursé ce mois
            val paiementDisponible = paiementMensuel - fraisMensuelsMoyens
            val capitalMois = min(paiementDisponible - interetsMois, soldeRestant)
            
            // Mettre à jour le solde
            soldeRestant -= capitalMois
        }

        return if (mois >= maxMois) null else mois
    }

    /**
     * Calcule les intérêts totaux en utilisant la même logique que le calculateur.
     */
    private fun calculerInteretsTotal(carte: CompteCredit, paiementMensuel: Double, tempsMois: Int?): Double? {
        if (tempsMois == null) return null

        val dette = abs(carte.solde)
        val taux = carte.tauxInteret ?: 0.0
        val tauxMensuel = taux / 100.0 / 12.0

        if (tauxMensuel == 0.0) {
            return 0.0
        }

        // Simulation mois par mois pour calculer les intérêts réels payés
        var soldeRestant = dette
        var totalInterets = 0.0
        var mois = 0

        while (soldeRestant > 0.01 && mois < tempsMois) {
            mois++
            
            // Calculer les frais moyens pour cette durée estimée
            val dureeEstimee = tempsMois - mois + 1
            val fraisMensuelsMoyens = carte.calculerFraisMensuelsMoyens(dureeEstimee)
            
            // Calculer les intérêts du mois
            val interetsMois = soldeRestant * tauxMensuel
            
            // Calculer le capital remboursé ce mois
            val paiementDisponible = paiementMensuel - fraisMensuelsMoyens
            val capitalMois = min(paiementDisponible - interetsMois, soldeRestant)
            
            // Accumuler les intérêts payés
            totalInterets += interetsMois
            
            // Mettre à jour le solde
            soldeRestant -= capitalMois
        }

        return totalInterets
    }

    /**
     * Calcule les résultats du calculateur pour un paiement donné.
     */
    fun calculerResultatsCalculateur(carte: CompteCredit, paiementMensuel: Double) {
        val tempsRemboursement = calculerTempsRemboursement(carte, paiementMensuel)
        val interetsTotal = calculerInteretsTotal(carte, paiementMensuel, tempsRemboursement)
        
        _calculsCalculateur.value = CalculsCalculateur(
            tempsRemboursement = tempsRemboursement,
            interetsTotal = interetsTotal
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
     * Affiche le dialog de modification des frais mensuels fixes.
     */
    fun afficherDialogModificationFrais(carte: CompteCredit) {
        // ✅ CORRECTION : Ouvrir le dialogue avec des champs vides pour ajouter un nouveau frais
        _formulaire.value = _formulaire.value.copy(
            fraisMensuelsFixes = "", // Champ vide pour nouveau frais
            nomFraisMensuels = "",   // Champ vide pour nouveau frais
            erreurFrais = null,      // Réinitialiser les erreurs
            erreurNomFrais = null    // Réinitialiser les erreurs
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
        } catch (

            _: Exception) {
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
            
            // ✅ CORRECTION : Ajouter le nouveau frais à la liste existante au lieu de remplacer
            val nouveauxFrais = if (frais != null && nomFrais != null) {
                // Récupérer les frais existants et ajouter le nouveau
                carteOriginale.fraisMensuels + FraisMensuel(nom = nomFrais, montant = frais)
            } else {
                carteOriginale.fraisMensuels // Garder les frais existants si pas de nouveau frais
            }
            
            // Convertir en JsonElement
            val fraisJson = if (nouveauxFrais.isNotEmpty()) {
                val gson = com.google.gson.Gson()
                val jsonString = gson.toJson(nouveauxFrais.toTypedArray())
                gson.fromJson(jsonString, com.google.gson.JsonElement::class.java)
            } else {
                null
            }
            
            val carteModifiee = carteOriginale.copy(
                fraisMensuelsJson = fraisJson,
                collection = "comptes_credits"
            )

            carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        afficherDialogModificationFrais = false,
                        estEnChargement = false
                    )
                    chargerCartesCredit()
                    
                    // 🚀 DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR LES COMPTES
                    realtimeSyncService.declencherMiseAJourComptes()
                }
                .onFailure { erreur ->
                    _uiState.value = _uiState.value.copy(
                        estEnChargement = false,
                        messageErreur = "Erreur lors de la modification des frais: ${erreur.message}"
                    )
                }
        }
    }

    /**
     * Supprime un frais mensuel d'une carte de crédit.
     */
    fun supprimerFraisMensuel(carte: CompteCredit, frais: FraisMensuel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(estEnChargement = true)

            try {
                // ✅ CORRECTION : Supprimer exactement le frais spécifique en utilisant l'ID unique
                val nouveauxFrais = carte.fraisMensuels.filter { 
                    it.id != frais.id // Utiliser l'ID unique pour une suppression précise
                }
                
                // Convertir en JsonElement pour la sauvegarde
                val fraisJson = if (nouveauxFrais.isNotEmpty()) {
                    val gson = com.google.gson.Gson()
                    val jsonString = gson.toJson(nouveauxFrais.toTypedArray())
                    gson.fromJson(jsonString, com.google.gson.JsonElement::class.java)
                } else {
                    null
                }
                
                // Mettre à jour la carte avec les nouveaux frais en s'assurant que la collection est définie
                val carteModifiee = carte.copy(
                    fraisMensuelsJson = fraisJson,
                    collection = "comptes_credits"  // Valeur explicite
                )
                
                // Sauvegarder la modification
                carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                    .onSuccess {
                        // Mettre à jour directement l'état local pour un rafraîchissement immédiat
                        val cartesMisesAJour = _uiState.value.cartesCredit.map { carteExistante ->
                            if (carteExistante.id == carte.id) {
                                carteModifiee
                            } else {
                                carteExistante
                            }
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            cartesCredit = cartesMisesAJour,
                            carteSelectionnee = if (_uiState.value.carteSelectionnee?.id == carte.id) {
                                carteModifiee
                            } else {
                                _uiState.value.carteSelectionnee
                            },
                            estEnChargement = false
                        )
                        
                        // 🚀 DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR LES COMPTES
                        realtimeSyncService.declencherMiseAJourComptes()
                    }
                    .onFailure { erreur ->
                        _uiState.value = _uiState.value.copy(
                            estEnChargement = false,
                            messageErreur = "Erreur lors de la suppression: ${erreur.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    estEnChargement = false,
                    messageErreur = "Erreur lors de la suppression: ${e.message}"
                )
            }
        }
    }

}
