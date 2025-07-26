// chemin/simule: /ui/categories/CategoriesEnveloppesViewModel.kt
// Dépendances: ViewModel, Repositories, PocketBaseClient, Modèles

package com.xburnsx.toutiebudget.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel pour la gestion des catégories et enveloppes.
 * Gère les créations, suppressions et mises à jour avec synchronisation instantanée.
 */
class CategoriesEnveloppesViewModel(
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    // Callback pour notifier les autres ViewModels des changements
    var onEnveloppeChange: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(CategoriesEnveloppesUiState())
    val uiState: StateFlow<CategoriesEnveloppesUiState> = _uiState.asStateFlow()

    // Cache des données pour optimiser les performances
    private var categoriesMap = mapOf<String, Categorie>()
    private var enveloppesList = listOf<Enveloppe>()

    init {
        // Initialiser avec un état vide mais fonctionnel
        _uiState.update { it.copy(isLoading = false, enveloppesGroupees = mapOf()) }
        chargerDonnees()
    }

    /**
     * Charge les catégories et enveloppes depuis PocketBase.
     * Met à jour l'interface utilisateur de manière fluide.
     */
    fun chargerDonnees() {
        viewModelScope.launch {
            try {
                // Charger les données en parallèle
                val categoriesResult = categorieRepository.recupererToutesLesCategories()
                val enveloppesResult = enveloppeRepository.recupererToutesLesEnveloppes()
                
                val categories = categoriesResult.getOrElse { 
                    println("[ERROR] Erreur chargement catégories: ${it.message}")
                    emptyList() 
                }
                val enveloppes = enveloppesResult.getOrElse { 
                    println("[ERROR] Erreur chargement enveloppes: ${it.message}")
                    emptyList() 
                }
                
                // Mettre à jour le cache
                categoriesMap = categories.associateBy { it.id }
                enveloppesList = enveloppes.filter { !it.estArchive }
                
                // Organiser les données pour l'affichage
                val enveloppesGroupees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(categories, enveloppesList)

                // Mettre à jour l'interface
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        enveloppesGroupees = enveloppesGroupees,
                        erreur = null
                    )
                }
                

                
            } catch (e: Exception) {
                println("[ERROR] Erreur lors du chargement: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = "Erreur de chargement: ${e.message}"
                    )
                }
            }
        }
    }


    // ===== GESTION DES CATÉGORIES =====

    fun onOuvrirAjoutCategorieDialog() {
        _uiState.update { it.copy(isAjoutCategorieDialogVisible = true) }
    }

    fun onFermerAjoutCategorieDialog() {
        _uiState.update { 
            it.copy(
                isAjoutCategorieDialogVisible = false, 
                nomNouvelleCategorie = ""
            ) 
        }
    }

    fun onNomCategorieChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleCategorie = nom) }
    }

    /**
     * Crée une nouvelle catégorie avec mise à jour instantanée.
     */
    fun onAjouterCategorie() {
        val nom = _uiState.value.nomNouvelleCategorie.trim()
        
        if (nom.isEmpty()) {
            _uiState.update { it.copy(erreur = "Le nom de la catégorie ne peut pas être vide") }
            return
        }
        
        // Vérifier si la catégorie existe déjà
        if (categoriesMap.values.any { it.nom.equals(nom, ignoreCase = true) }) {
            _uiState.update { it.copy(erreur = "Une catégorie avec ce nom existe déjà") }
            return
        }
        
        viewModelScope.launch {
            try {
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")
                
                // Créer l'objet catégorie temporaire
                val categorieTemporaire = Categorie(
                    id = "temp_${System.currentTimeMillis()}",
                    utilisateurId = utilisateurId,
                    nom = nom
                )
                
                // Mise à jour optimiste de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes[nom] = emptyList()
                
                _uiState.update { currentState ->
                    currentState.copy(
                        enveloppesGroupees = organiserGroupes(nouveauxGroupes),
                        isAjoutCategorieDialogVisible = false,
                        nomNouvelleCategorie = "",
                        erreur = null
                    )
                }
                
                // Envoyer à PocketBase
                val resultat = categorieRepository.creerCategorie(categorieTemporaire)
                
                resultat.onSuccess { categorieCreee ->
                    // Mettre à jour le cache avec la vraie catégorie
                    categoriesMap = categoriesMap + (categorieCreee.id to categorieCreee)
                    
                    // Recharger pour s'assurer de la cohérence
                    chargerDonnees()

                    // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier tous les autres ViewModels
                    realtimeSyncService.declencherMiseAJourBudget()

                    println("[SYNC] Nouvelle catégorie créée et notification envoyée : ${categorieCreee.nom}")

                }.onFailure { erreur ->
                    // Supprimer la catégorie temporaire en cas d'erreur
                    val groupesCorriges = _uiState.value.enveloppesGroupees.toMutableMap()
                    groupesCorriges.remove(nom)

                    _uiState.update { currentState ->
                        currentState.copy(
                            enveloppesGroupees = groupesCorriges,
                            erreur = "Erreur lors de la création: ${erreur.message}"
                        )
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        erreur = "Erreur: ${e.message}",
                        isAjoutCategorieDialogVisible = false
                    )
                }
            }
        }
    }

    // ===== GESTION DES ENVELOPPES =====

    fun onOuvrirAjoutEnveloppeDialog(categorie: String) {
        _uiState.update { 
            it.copy(
                isAjoutEnveloppeDialogVisible = true,
                categoriePourAjout = categorie
            ) 
        }
    }

    fun onFermerAjoutEnveloppeDialog() {
        _uiState.update { 
            it.copy(
                isAjoutEnveloppeDialogVisible = false, 
                nomNouvelleEnveloppe = "",
                categoriePourAjout = null
            ) 
        }
    }

    fun onNomEnveloppeChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleEnveloppe = nom) }
    }

    /**
     * Crée une nouvelle enveloppe VIDE avec mise à jour instantanée.
     * Une enveloppe fraîche n'a aucun objectif et tous les montants à 0.
     */
    fun onAjouterEnveloppe() {
        val nom = _uiState.value.nomNouvelleEnveloppe.trim()
        val categorieNom = _uiState.value.categoriePourAjout
        
        if (nom.isEmpty() || categorieNom == null) {
            _uiState.update { it.copy(erreur = "Le nom de l'enveloppe ne peut pas être vide") }
            return
        }
        
        viewModelScope.launch {
            try {
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")
                
                // Trouver la catégorie correspondante
                val categorie = categoriesMap.values.find { it.nom == categorieNom }
                    ?: throw Exception("Catégorie '$categorieNom' introuvable")
                
                // ✅ CRÉATION D'UNE ENVELOPPE COMPLÈTEMENT VIDE
                val enveloppeVide = Enveloppe(
                    id = "temp_${System.currentTimeMillis()}",
                    utilisateurId = utilisateurId,
                    nom = nom,
                    categorieId = categorie.id, // IMPORTANT: Lien avec la catégorie
                    estArchive = false,
                    ordre = 0,
                    // ✅ OBJECTIFS VIDES PAR DÉFAUT
                    objectifType = TypeObjectif.Aucun,  // Pas d'objectif
                    objectifMontant = 0.0,             // Pas de montant objectif
                    objectifDate = null,               // Pas de date d'échéance
                    objectifJour = null                // Pas de jour spécifique
                )

                
                // Mise à jour optimiste de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                val enveloppesCategorie = (nouveauxGroupes[categorieNom] ?: emptyList()).toMutableList()
                enveloppesCategorie.add(enveloppeVide)
                nouveauxGroupes[categorieNom] = enveloppesCategorie
                
                _uiState.update { currentState ->
                    currentState.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        isAjoutEnveloppeDialogVisible = false,
                        nomNouvelleEnveloppe = "",
                        categoriePourAjout = null,
                        erreur = null
                    )
                }
                
                // Envoyer à PocketBase
                val resultat = enveloppeRepository.creerEnveloppe(enveloppeVide)
                
                resultat.onSuccess { enveloppeCreee ->
                    // Mettre à jour le cache avec la vraie enveloppe
                    enveloppesList = enveloppesList.map {
                        if (it.id == enveloppeVide.id) enveloppeCreee else it
                    }
                    
                    // Recharger pour s'assurer de la cohérence
                    chargerDonnees()
                    
                    // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier tous les autres ViewModels
                    realtimeSyncService.declencherMiseAJourBudget()

                    println("[SYNC] Nouvelle enveloppe créée et notification envoyée : ${enveloppeCreee.nom}")

                }.onFailure { erreur ->
                    // Supprimer l'enveloppe temporaire en cas d'erreur
                    val groupesCorrigees = _uiState.value.enveloppesGroupees.toMutableMap()
                    val enveloppesCorrigees = (groupesCorrigees[categorieNom] ?: emptyList())
                        .filterNot { it.id == enveloppeVide.id }
                    groupesCorrigees[categorieNom] = enveloppesCorrigees

                    _uiState.update { currentState ->
                        currentState.copy(
                            enveloppesGroupees = groupesCorrigees,
                            erreur = "Erreur lors de la création: ${erreur.message}"
                        )
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        erreur = "Erreur: ${e.message}",
                        isAjoutEnveloppeDialogVisible = false
                    )
                }
            }
        }
    }

    // ===== GESTION DES OBJECTIFS =====

    fun onOuvrirObjectifDialog(enveloppe: Enveloppe) {
        _uiState.update { 
            it.copy(
                isObjectifDialogVisible = true,
                enveloppePourObjectif = enveloppe
            ) 
        }
    }

    fun onFermerObjectifDialog() {
        _uiState.update { 
            it.copy(
                isObjectifDialogVisible = false,
                enveloppePourObjectif = null,
                objectifFormState = ObjectifFormState()
            ) 
        }
    }

    fun onObjectifTypeChange(type: TypeObjectif) {
        _uiState.update { 
            it.copy(objectifFormState = it.objectifFormState.copy(type = type))
        }
    }

    fun onObjectifMontantChange(montant: String) {
        _uiState.update { 
            it.copy(objectifFormState = it.objectifFormState.copy(montant = montant))
        }
    }

    fun onObjectifDateChange(date: Date?) {
        _uiState.update { 
            it.copy(objectifFormState = it.objectifFormState.copy(date = date))
        }
    }

    fun onObjectifJourChange(jour: Int?) {
        _uiState.update { 
            it.copy(objectifFormState = it.objectifFormState.copy(jour = jour))
        }
    }

    /**
     * Sauvegarde l'objectif d'une enveloppe avec mise à jour instantanée.
     */
    fun onSauvegarderObjectif() {
        val enveloppe = _uiState.value.enveloppePourObjectif ?: return
        val formState = _uiState.value.objectifFormState
        val montant = formState.montant.toDoubleOrNull() ?: 0.0
        
        viewModelScope.launch {
            try {
                val enveloppeModifiee = enveloppe.copy(
                    objectifMontant = montant,
                    objectifType = formState.type,
                    objectifDate = formState.date,
                    objectifJour = formState.jour
                )
                
                // Mise à jour instantanée de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes.forEach { (categorie, enveloppes) ->
                    val nouvellesEnveloppes = enveloppes.map { env ->
                        if (env.id == enveloppe.id) enveloppeModifiee else env
                    }
                    nouveauxGroupes[categorie] = nouvellesEnveloppes
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        isObjectifDialogVisible = false,
                        enveloppePourObjectif = null,
                        objectifFormState = ObjectifFormState()
                    )
                }
                
                // Envoyer à PocketBase
                enveloppeRepository.mettreAJourEnveloppe(enveloppeModifiee).onSuccess {

                }.onFailure { erreur ->
                    _uiState.update { it.copy(erreur = "Erreur sauvegarde objectif: ${erreur.message}") }
                    chargerDonnees() // Recharger en cas d'erreur
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                onFermerObjectifDialog()
            }
        }
    }

    // ===== GESTION DES SUPPRESSIONS =====

    fun onOuvrirConfirmationSuppressionEnveloppe(enveloppe: Enveloppe) {
        _uiState.update { it.copy(
            enveloppePourSuppression = enveloppe,
            isConfirmationSuppressionEnveloppeVisible = true
        )}
    }
    
    fun onFermerConfirmationSuppressionEnveloppe() {
        _uiState.update { it.copy(
            enveloppePourSuppression = null,
            isConfirmationSuppressionEnveloppeVisible = false
        )}
    }
    
    /**
     * Supprime une enveloppe avec mise à jour instantanée.
     */
    fun onConfirmerSuppressionEnveloppe() {
        val enveloppe = _uiState.value.enveloppePourSuppression ?: return
        
        onFermerConfirmationSuppressionEnveloppe()
        
        viewModelScope.launch {
            try {
                // Mise à jour instantanée de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes.forEach { (categorie, enveloppes) ->
                    val nouvellesEnveloppes = enveloppes.filterNot { it.id == enveloppe.id }
                    nouveauxGroupes[categorie] = nouvellesEnveloppes
                }
                
                _uiState.update { currentState ->
                    currentState.copy(enveloppesGroupees = nouveauxGroupes)
                }
                
                // Envoyer à PocketBase
                enveloppeRepository.supprimerEnveloppe(enveloppe.id).onSuccess {

                }.onFailure { erreur ->
                    _uiState.update { it.copy(erreur = "Erreur suppression: ${erreur.message}") }
                    chargerDonnees() // Recharger en cas d'erreur
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                chargerDonnees()
            }
        }
    }
    
    fun onOuvrirConfirmationSuppressionCategorie(nomCategorie: String) {
        _uiState.update { it.copy(
            categoriePourSuppression = nomCategorie,
            isConfirmationSuppressionCategorieVisible = true
        )}
    }
    
    fun onFermerConfirmationSuppressionCategorie() {
        _uiState.update { it.copy(
            categoriePourSuppression = null,
            isConfirmationSuppressionCategorieVisible = false
        )}
    }
    
    /**
     * Supprime une catégorie avec mise à jour instantanée.
     */
    fun onConfirmerSuppressionCategorie() {
        val nomCategorie = _uiState.value.categoriePourSuppression ?: return
        
        onFermerConfirmationSuppressionCategorie()
        
        viewModelScope.launch {
            try {
                val categorieObj = categoriesMap.values.find { it.nom == nomCategorie }
                    ?: throw Exception("Catégorie '$nomCategorie' introuvable")
                
                // Vérifier qu'elle est vide
                val enveloppes = _uiState.value.enveloppesGroupees[nomCategorie] ?: emptyList()
                if (enveloppes.isNotEmpty()) {
                    _uiState.update { it.copy(erreur = "Impossible de supprimer une catégorie contenant des enveloppes") }
                    return@launch
                }
                
                // Mise à jour instantanée de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes.remove(nomCategorie)
                
                _uiState.update { currentState ->
                    currentState.copy(enveloppesGroupees = nouveauxGroupes)
                }
                
                // Envoyer à PocketBase
                categorieRepository.supprimerCategorie(categorieObj.id).onSuccess {
                    // Mettre à jour le cache
                    categoriesMap = categoriesMap.filterNot { it.key == categorieObj.id }
                }.onFailure { erreur ->
                    _uiState.update { it.copy(erreur = "Erreur suppression: ${erreur.message}") }
                    chargerDonnees() // Recharger en cas d'erreur
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                chargerDonnees()
            }
        }
    }

    // ===== GESTION DU MODE ÉDITION ET DRAG & DROP =====

    fun onActiverModeEdition() {
        _uiState.update { it.copy(isModeEdition = true) }
    }

    fun onAnnulerModeEdition() {
        _uiState.update {
            it.copy(
                isModeEdition = false,
                isDragMode = false,
                draggedItemId = null,
                draggedItemType = null
            )
        }
        // Recharger les données pour annuler les changements temporaires
        chargerDonnees()
    }

    fun onSauvegarderOrdreCategories() {
        // TODO: Implémenter la sauvegarde de l'ordre des catégories
        _uiState.update {
            it.copy(
                isModeEdition = false,
                isDragMode = false,
                draggedItemId = null,
                draggedItemType = null
            )
        }
    }

    /**
     * Démarre le mode drag pour un élément avec long tap
     */
    fun onStartDrag(itemId: String, itemType: DragItemType) {
        _uiState.update {
            it.copy(
                isDragMode = true,
                draggedItemId = itemId,
                draggedItemType = itemType
            )
        }
    }

    /**
     * Arrête le mode drag
     */
    fun onEndDrag() {
        _uiState.update {
            it.copy(
                isDragMode = false,
                draggedItemId = null,
                draggedItemType = null
            )
        }
    }

    /**
     * Déplace une catégorie vers une nouvelle position
     */
    fun onMoveCategorie(fromIndex: Int, toIndex: Int) {
        val currentGroupes = _uiState.value.enveloppesGroupees
        val categoriesList = currentGroupes.keys.toList()

        if (fromIndex >= 0 && fromIndex < categoriesList.size &&
            toIndex >= 0 && toIndex < categoriesList.size &&
            fromIndex != toIndex) {

            val mutableList = categoriesList.toMutableList()
            val item = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, item)

            // Réorganiser les groupes selon le nouvel ordre
            val nouveauxGroupes = linkedMapOf<String, List<Enveloppe>>()
            mutableList.forEach { nomCategorie ->
                nouveauxGroupes[nomCategorie] = currentGroupes[nomCategorie] ?: emptyList()
            }

            _uiState.update {
                it.copy(enveloppesGroupees = nouveauxGroupes)
            }
        }
    }

    /**
     * Déplace une enveloppe d'une catégorie à une autre
     */
    fun onMoveEnveloppe(enveloppeId: String, fromCategorie: String, toCategorie: String, toIndex: Int) {
        val currentGroupes = _uiState.value.enveloppesGroupees.toMutableMap()

        // Trouver l'enveloppe à déplacer
        val fromEnveloppes = currentGroupes[fromCategorie]?.toMutableList() ?: return
        val enveloppe = fromEnveloppes.find { it.id == enveloppeId } ?: return

        // Retirer l'enveloppe de la catégorie source
        fromEnveloppes.remove(enveloppe)
        currentGroupes[fromCategorie] = fromEnveloppes

        // Ajouter l'enveloppe à la catégorie destination
        val toEnveloppes = currentGroupes[toCategorie]?.toMutableList() ?: mutableListOf()
        val safeIndex = minOf(toIndex, toEnveloppes.size)
        toEnveloppes.add(safeIndex, enveloppe)
        currentGroupes[toCategorie] = toEnveloppes

        _uiState.update {
            it.copy(enveloppesGroupees = currentGroupes)
        }

        // Mettre à jour la catégorie de l'enveloppe dans la base de données
        viewModelScope.launch {
            try {
                val nouvelleCategorie = categoriesMap.values.find { it.nom == toCategorie }
                if (nouvelleCategorie != null) {
                    val enveloppeModifiee = enveloppe.copy(categorieId = nouvelleCategorie.id)
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeModifiee)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors du déplacement: ${e.message}") }
                chargerDonnees() // Recharger en cas d'erreur
            }
        }
    }

    // ===== UTILITAIRES =====

    fun onEffacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    /**
     * Organise les groupes triés par ordre alphabétique.
     */
    private fun organiserGroupes(groupes: Map<String, List<Enveloppe>>): Map<String, List<Enveloppe>> {
        return groupes.entries
            .sortedWith(compareBy(
                { it.key == "Sans catégorie" },
                { it.key }
            ))
            .associate { it.toPair() }
    }
}