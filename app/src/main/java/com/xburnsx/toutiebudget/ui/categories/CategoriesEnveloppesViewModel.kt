// chemin/simule: /ui/categories/CategoriesEnveloppesViewModel.kt
package com.xburnsx.toutiebudget.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*

class CategoriesEnveloppesViewModel(
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesEnveloppesUiState())
    val uiState: StateFlow<CategoriesEnveloppesUiState> = _uiState.asStateFlow()

    // Cache pour éviter les rechargements visibles
    private var donneesCachees: CategoriesEnveloppesUiState? = null
    
    // Map pour accéder rapidement aux catégories par ID
    private var categoriesMap = mapOf<String, Categorie>()
    
    // Timestamp de la dernière modification
    private var derniereMaj = 0L

    init {
        // Préparer une liste vide mais valide pour éviter l'écran noir
        _uiState.update { it.copy(isLoading = false, enveloppesGroupees = mapOf()) }
        chargerCategoriesEtEnveloppes()
    }

    fun chargerCategoriesEtEnveloppes() {
        // Si on a des données en cache, les afficher immédiatement
        donneesCachees?.let { cache ->
            _uiState.update { cache }
        }
        
        // Puis charger en arrière-plan
        chargerCategoriesEtEnveloppesSilencieusement(false)
    }
    
    override fun onCleared() {
        super.onCleared()
    }

    private fun chargerCategoriesEtEnveloppesSilencieusement(forceRefresh: Boolean = false) {
        // Vérifier si un rechargement est nécessaire
        val maintenant = System.currentTimeMillis()
        if (!forceRefresh && maintenant - derniereMaj < 500) {
            // Éviter les rechargements trop fréquents
            return
        }
        
        derniereMaj = maintenant
        viewModelScope.launch {
            try {
                // Récupération des données
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()
                
                // Mettre à jour la map des catégories pour un accès rapide
                categoriesMap = categories.associateBy { it.id }
                
                // Créer une map pour stocker les enveloppes par catégorie
                val groupesEnveloppes = mutableMapOf<String, MutableList<Enveloppe>>()
                
                // Initialiser les catégories avec des listes vides
                categories.forEach { categorie ->
                    groupesEnveloppes[categorie.nom] = mutableListOf()
                }
                
                // Ajouter "Sans catégorie" pour les enveloppes sans catégorie valide
                groupesEnveloppes["Sans catégorie"] = mutableListOf()
                
                // Remplir les groupes avec les enveloppes correspondantes
                enveloppes.filter { !it.estArchive }.forEach { enveloppe ->
                    val categorie = if (enveloppe.categorieId.isNotBlank()) {
                        categoriesMap[enveloppe.categorieId] ?: null
                    } else null
                    
                    val nomCategorie = categorie?.nom ?: "Sans catégorie"
                    
                    // S'assurer que la catégorie existe dans la map
                    if (!groupesEnveloppes.containsKey(nomCategorie)) {
                        groupesEnveloppes[nomCategorie] = mutableListOf()
                    }
                    
                    // Ajouter l'enveloppe à la catégorie correspondante
                    groupesEnveloppes[nomCategorie]?.add(enveloppe)
                }
                
                // Ne supprimer que "Sans catégorie" si elle est vide, mais conserver les autres catégories même vides
                val groupesFiltres = groupesEnveloppes.filter { (nomCategorie, enveloppes) ->
                    nomCategorie != "Sans catégorie" || enveloppes.isNotEmpty()
                }
                
                // Trier les catégories par ordre alphabétique (sauf "Sans catégorie" qui reste en dernier)
                val categoriesTriees = groupesFiltres.entries.sortedWith(
                    compareBy(
                        { it.key == "Sans catégorie" }, // Mettre "Sans catégorie" à la fin
                        { it.key } // Trier les autres par ordre alphabétique
                    )
                )
                
                // Créer une nouvelle map triée
                val groupesTries = categoriesTriees.associate { it.toPair() }
                
                // Vérifier si les données ont changé avant de mettre à jour l'UI
                val etatActuel = _uiState.value
                val donneesDifferentes = etatActuel.enveloppesGroupees != groupesTries
                
                if (donneesDifferentes) {
                    val nouvelEtat = CategoriesEnveloppesUiState(
                        isLoading = false,
                        enveloppesGroupees = groupesTries,
                        // Conserver les états des dialogues et autres propriétés
                        isAjoutCategorieDialogVisible = etatActuel.isAjoutCategorieDialogVisible,
                        isAjoutEnveloppeDialogVisible = etatActuel.isAjoutEnveloppeDialogVisible,
                        isObjectifDialogVisible = etatActuel.isObjectifDialogVisible,
                        categoriePourAjout = etatActuel.categoriePourAjout,
                        nomNouvelleCategorie = etatActuel.nomNouvelleCategorie,
                        nomNouvelleEnveloppe = etatActuel.nomNouvelleEnveloppe,
                        enveloppePourObjectif = etatActuel.enveloppePourObjectif,
                        objectifFormState = etatActuel.objectifFormState
                    )
                    
                    // Mettre en cache et mettre à jour l'UI
                    donneesCachees = nouvelEtat
                    _uiState.update { nouvelEtat }
                } else {
                    println("[DEBUG] Aucun changement détecté dans les données")
                }
                
            } catch (e: Exception) {
                println("[DEBUG] Erreur lors du chargement: ${e.message}")
                e.printStackTrace()
                // Erreur silencieuse - on garde les données précédentes si disponibles
                if (donneesCachees == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            erreur = "Erreur lors du chargement: ${e.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun onOuvrirAjoutCategorieDialog() {
        _uiState.update { it.copy(isAjoutCategorieDialogVisible = true) }
    }

    fun onFermerAjoutCategorieDialog() {
        _uiState.update { it.copy(isAjoutCategorieDialogVisible = false, nomNouvelleCategorie = "") }
    }

    fun onNomCategorieChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleCategorie = nom) }
    }

    fun onAjouterCategorie() {
        val nom = _uiState.value.nomNouvelleCategorie.trim()
        if (nom.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    // Récupérer l'ID de l'utilisateur connecté
                    val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                        ?: throw Exception("Utilisateur non connecté")
                        
                    // Créer la catégorie avec les paramètres requis
                    val nouvelleCategorie = Categorie(
                        id = "", // Sera généré par PocketBase
                        utilisateurId = utilisateurId,
                        nom = nom
                    )
                    
                    // Mettre à jour l'UI immédiatement pour une expérience utilisateur fluide
                    // Créer une catégorie temporaire avec un ID unique temporaire
                    val categorieTemp = nouvelleCategorie.copy(id = "temp_${System.currentTimeMillis()}")
                    
                    // Ajouter temporairement à l'UI
                    _uiState.update { etatActuel ->
                        val groupesActuels = etatActuel.enveloppesGroupees.toMutableMap()
                        groupesActuels[nom] = emptyList()
                        etatActuel.copy(
                            enveloppesGroupees = groupesActuels,
                            isAjoutCategorieDialogVisible = false,
                            nomNouvelleCategorie = ""
                        )
                    }
                    
                    // Envoyer à PocketBase
                    categorieRepository.creerCategorie(nouvelleCategorie).onSuccess {
                        // Recharger silencieusement pour obtenir l'ID réel
                        chargerCategoriesEtEnveloppesSilencieusement(true)
                    }.onFailure { e ->
                        // En cas d'échec, retirer la catégorie temporaire et afficher l'erreur
                        _uiState.update { etatActuel ->
                            val groupesActuels = etatActuel.enveloppesGroupees.toMutableMap()
                            groupesActuels.remove(nom)
                            etatActuel.copy(
                                enveloppesGroupees = groupesActuels,
                                erreur = "Erreur lors de la création de la catégorie: ${e.message}"
                            )
                        }
                        chargerCategoriesEtEnveloppesSilencieusement(true)
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(erreur = "Erreur lors de la création de la catégorie: ${e.message}") }
                    onFermerAjoutCategorieDialog()
                }
            }
        } else {
            _uiState.update { it.copy(erreur = "Le nom de la catégorie ne peut pas être vide") }
        }
    }

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

    fun onAjouterEnveloppe() {
        val nom = _uiState.value.nomNouvelleEnveloppe.trim()
        val categorie = _uiState.value.categoriePourAjout
        if (nom.isNotEmpty() && categorie != null) {
            viewModelScope.launch {
                try {
                    // Récupérer l'ID de l'utilisateur connecté
                    val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                        ?: throw Exception("Utilisateur non connecté")
                        
                    // Utiliser la map des catégories pour trouver rapidement l'ID de la catégorie
                    val categorieObj = categoriesMap.values.find { it.nom == categorie }
                        ?: throw Exception("Catégorie '$categorie' introuvable")
                    
                    println("[DEBUG] Création d'enveloppe pour catégorie: ${categorieObj.nom} (${categorieObj.id})")
                    
                    // Créer l'enveloppe avec les paramètres requis
                    val nouvelleEnveloppe = Enveloppe(
                        id = "", // Sera généré par PocketBase
                        utilisateurId = utilisateurId,
                        nom = nom,
                        categorieId = categorieObj.id,
                        estArchive = false,
                        ordre = 0
                    )
                    
                    // Mettre à jour l'UI immédiatement pour une expérience utilisateur fluide
                    // Créer une enveloppe temporaire avec un ID unique temporaire
                    val enveloppeTemp = nouvelleEnveloppe.copy(id = "temp_${System.currentTimeMillis()}")
                    
                    // Ajouter temporairement à l'UI
                    _uiState.update { etatActuel ->
                        val groupesActuels = etatActuel.enveloppesGroupees.toMutableMap()
                        val enveloppesActuelles = groupesActuels[categorie]?.toMutableList() ?: mutableListOf()
                        enveloppesActuelles.add(enveloppeTemp)
                        groupesActuels[categorie] = enveloppesActuelles
                        
                        etatActuel.copy(
                            enveloppesGroupees = groupesActuels,
                            isAjoutEnveloppeDialogVisible = false,
                            nomNouvelleEnveloppe = "",
                            categoriePourAjout = null
                        )
                    }
                    
                    // Envoyer à PocketBase
                    enveloppeRepository.creerEnveloppe(nouvelleEnveloppe).onSuccess {
                        // Recharger silencieusement pour obtenir l'ID réel
                        chargerCategoriesEtEnveloppesSilencieusement(true)
                    }.onFailure { e ->
                        // En cas d'échec, retirer l'enveloppe temporaire et afficher l'erreur
                        _uiState.update { etatActuel ->
                            val groupesActuels = etatActuel.enveloppesGroupees.toMutableMap()
                            val enveloppesActuelles = groupesActuels[categorie]?.toMutableList() ?: mutableListOf()
                            enveloppesActuelles.removeIf { it.id.startsWith("temp_") }
                            groupesActuels[categorie] = enveloppesActuelles
                            
                            etatActuel.copy(
                                enveloppesGroupees = groupesActuels,
                                erreur = "Erreur lors de la création de l'enveloppe: ${e.message}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(erreur = "Erreur lors de la création de l'enveloppe: ${e.message}") }
                    onFermerAjoutEnveloppeDialog()
                }
            }
        } else {
            _uiState.update { it.copy(erreur = "Le nom de l'enveloppe ne peut pas être vide") }
        }
    }

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
            it.copy(
                objectifFormState = it.objectifFormState.copy(type = type)
            ) 
        }
    }

    fun onObjectifMontantChange(montant: String) {
        _uiState.update { 
            it.copy(
                objectifFormState = it.objectifFormState.copy(montant = montant)
            ) 
        }
    }

    fun onObjectifDateChange(date: Date?) {
        _uiState.update { 
            it.copy(
                objectifFormState = it.objectifFormState.copy(date = date)
            ) 
        }
    }

    fun onObjectifJourChange(jour: Int?) {
        _uiState.update { 
            it.copy(
                objectifFormState = it.objectifFormState.copy(jour = jour)
            ) 
        }
    }

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
                
                // Mettre à jour l'UI immédiatement pour une expérience utilisateur fluide
                _uiState.update { etatActuel ->
                    // Trouver la catégorie qui contient cette enveloppe
                    var categorieNom: String? = null
                    var enveloppesModifiees: List<Enveloppe>? = null
                    
                    val groupesActuels = etatActuel.enveloppesGroupees.toMutableMap()
                    
                    // Parcourir toutes les catégories pour trouver l'enveloppe à mettre à jour
                    groupesActuels.forEach { (nomCategorie, enveloppes) ->
                        val index = enveloppes.indexOfFirst { it.id == enveloppe.id }
                        if (index != -1) {
                            categorieNom = nomCategorie
                            val nouvellesEnveloppes = enveloppes.toMutableList()
                            nouvellesEnveloppes[index] = enveloppeModifiee
                            enveloppesModifiees = nouvellesEnveloppes
                        }
                    }
                    
                    // Si on a trouvé l'enveloppe, mettre à jour la catégorie
                    if (categorieNom != null && enveloppesModifiees != null) {
                        groupesActuels[categorieNom!!] = enveloppesModifiees!!
                    }
                    
                    etatActuel.copy(
                        enveloppesGroupees = groupesActuels,
                        isObjectifDialogVisible = false,
                        enveloppePourObjectif = null,
                        objectifFormState = ObjectifFormState()
                    )
                }
                
                // Envoyer à PocketBase
                enveloppeRepository.mettreAJourEnveloppe(enveloppeModifiee).onSuccess {
                    // Recharger silencieusement pour synchroniser
                    chargerCategoriesEtEnveloppesSilencieusement(true)
                }.onFailure { e ->
                    _uiState.update { it.copy(erreur = "Erreur lors de la sauvegarde de l'objectif: ${e.message}") }
                    // Recharger pour annuler les modifications locales en cas d'erreur
                    chargerCategoriesEtEnveloppesSilencieusement()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors de la sauvegarde de l'objectif: ${e.message}") }
                onFermerObjectifDialog()
            }
        }
    }

    fun onEffacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }
    
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
    
    fun onConfirmerSuppressionEnveloppe() {
        val enveloppe = _uiState.value.enveloppePourSuppression ?: return
        
        // Fermer la boîte de dialogue
        onFermerConfirmationSuppressionEnveloppe()
        
        viewModelScope.launch {
            try {
                // Trouver la catégorie qui contient cette enveloppe
                var categorieNom: String? = null
                
                val groupesActuels = _uiState.value.enveloppesGroupees.toMutableMap()
                
                // Parcourir toutes les catégories pour trouver l'enveloppe à supprimer
                groupesActuels.forEach { (nomCategorie, enveloppes) ->
                    if (enveloppes.any { it.id == enveloppe.id }) {
                        categorieNom = nomCategorie
                    }
                }
                
                if (categorieNom == null) {
                    _uiState.update { it.copy(erreur = "Enveloppe introuvable") }
                    return@launch
                }
                
                // Mettre à jour l'UI immédiatement pour une expérience utilisateur fluide
                _uiState.update { etatActuel ->
                    val groupesModifies = etatActuel.enveloppesGroupees.toMutableMap()
                    val enveloppesActuelles = groupesModifies[categorieNom]?.toMutableList() ?: mutableListOf()
                    enveloppesActuelles.removeIf { it.id == enveloppe.id }
                    groupesModifies[categorieNom!!] = enveloppesActuelles
                    
                    etatActuel.copy(enveloppesGroupees = groupesModifies)
                }
                
                // Envoyer à PocketBase
                enveloppeRepository.supprimerEnveloppe(enveloppe.id).onSuccess {
                    // Recharger silencieusement pour s'assurer que tout est synchronisé
                    chargerCategoriesEtEnveloppesSilencieusement(true)
                }.onFailure { e ->
                    // En cas d'échec, recharger pour annuler les modifications locales
                    _uiState.update { it.copy(erreur = "Erreur lors de la suppression de l'enveloppe: ${e.message}") }
                    chargerCategoriesEtEnveloppesSilencieusement()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors de la suppression de l'enveloppe: ${e.message}") }
                chargerCategoriesEtEnveloppesSilencieusement()
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
    
    fun onConfirmerSuppressionCategorie() {
        val nomCategorie = _uiState.value.categoriePourSuppression ?: return
        
        // Fermer la boîte de dialogue
        onFermerConfirmationSuppressionCategorie()
        
        viewModelScope.launch {
            try {
                // Vérifier si la catégorie existe
                val categorieObj = categoriesMap.values.find { it.nom == nomCategorie }
                    ?: throw Exception("Catégorie '$nomCategorie' introuvable")
                
                // Vérifier si la catégorie contient des enveloppes
                val enveloppes = _uiState.value.enveloppesGroupees[nomCategorie] ?: emptyList()
                if (enveloppes.isNotEmpty()) {
                    _uiState.update { it.copy(erreur = "Impossible de supprimer une catégorie contenant des enveloppes. Veuillez d'abord supprimer ou déplacer toutes les enveloppes.") }
                    return@launch
                }
                
                // Mettre à jour l'UI immédiatement pour une expérience utilisateur fluide
                _uiState.update { etatActuel ->
                    val groupesModifies = etatActuel.enveloppesGroupees.toMutableMap()
                    groupesModifies.remove(nomCategorie)
                    
                    etatActuel.copy(enveloppesGroupees = groupesModifies)
                }
                
                // Envoyer à PocketBase
                categorieRepository.supprimerCategorie(categorieObj.id).onSuccess {
                    // Recharger silencieusement pour s'assurer que tout est synchronisé
                    chargerCategoriesEtEnveloppesSilencieusement(true)
                }.onFailure { e ->
                    // En cas d'échec, recharger pour annuler les modifications locales
                    _uiState.update { it.copy(erreur = "Erreur lors de la suppression de la catégorie: ${e.message}") }
                    chargerCategoriesEtEnveloppesSilencieusement()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors de la suppression de la catégorie: ${e.message}") }
                chargerCategoriesEtEnveloppesSilencieusement()
            }
        }
    }
}
