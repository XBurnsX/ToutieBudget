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
        } ?: run {
            // Sinon, s'assurer qu'on a au moins une structure vide mais valide
            // pour éviter l'écran noir
            _uiState.update { it.copy(enveloppesGroupees = mapOf()) }
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
        
        if (nom.isEmpty()) {
            _uiState.update { it.copy(erreur = "Le nom de la catégorie ne peut pas être vide") }
            return
        }
        
        viewModelScope.launch {
            try {
                // 1. Valider que l'utilisateur est connecté
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")
                
                // 2. Vérifier si la catégorie existe déjà
                if (categoriesMap.values.any { it.nom.equals(nom, ignoreCase = true) }) {
                    throw Exception("Une catégorie avec ce nom existe déjà")
                }
                
                // 3. Créer l'objet Categorie
                val nouvelleCategorie = Categorie(
                    id = "temp_${System.currentTimeMillis()}", // ID temporaire
                    utilisateurId = utilisateurId,
                    nom = nom
                )
                
                // 4. Mise à jour optimiste de l'UI
                _uiState.update { etatActuel ->
                    val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                    nouveauxGroupes[nom] = emptyList()
                    
                    etatActuel.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        isAjoutCategorieDialogVisible = false,
                        nomNouvelleCategorie = "",
                        erreur = null
                    )
                }
                
                // 5. Envoyer la requête à PocketBase
                val result = categorieRepository.creerCategorie(nouvelleCategorie)
                
                result.onSuccess { categorieCreee ->
                    // Mettre à jour la map des catégories
                    categoriesMap = categoriesMap + (categorieCreee.id to categorieCreee)
                    
                    // Mise à jour avec les vraies données du serveur
                    _uiState.update { etatActuel ->
                        val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                        val enveloppes = nouveauxGroupes.remove(nom) ?: emptyList()
                        nouveauxGroupes[categorieCreee.nom] = enveloppes
                        
                        etatActuel.copy(
                            enveloppesGroupees = nouveauxGroupes
                        )
                    }
                }.onFailure { e ->
                    // En cas d'erreur, on supprime la catégorie temporaire
                    _uiState.update { etatActuel ->
                        val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                        nouveauxGroupes.remove(nom)
                        
                        etatActuel.copy(
                            enveloppesGroupees = nouveauxGroupes,
                            erreur = "Erreur lors de la création: ${e.message}"
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
        val categorieNom = _uiState.value.categoriePourAjout
        
        if (nom.isEmpty() || categorieNom == null) {
            _uiState.update { it.copy(erreur = "Le nom de l'enveloppe ne peut pas être vide") }
            return
        }
        
        viewModelScope.launch {
            try {
                // 1. Valider que l'utilisateur est connecté
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")
                
                // 2. Trouver la catégorie correspondante
                val categorie = categoriesMap.values.find { it.nom == categorieNom }
                    ?: throw Exception("Catégorie '$categorieNom' introuvable")
                
                // 3. Créer l'objet Enveloppe avec l'ID de la catégorie
                val nouvelleEnveloppe = Enveloppe(
                    id = "temp_${System.currentTimeMillis()}", // ID temporaire
                    utilisateurId = utilisateurId,
                    nom = nom,
                    categorieId = categorie.id, // Lien avec la catégorie
                    estArchive = false,
                    ordre = 0
                )
                
                // 4. Mise à jour optimiste de l'UI
                _uiState.update { etatActuel ->
                    val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                    val nouvellesEnveloppes = (nouveauxGroupes[categorieNom] ?: emptyList()).toMutableList()
                    nouvellesEnveloppes.add(nouvelleEnveloppe)
                    nouveauxGroupes[categorieNom] = nouvellesEnveloppes
                    
                    etatActuel.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        isAjoutEnveloppeDialogVisible = false,
                        nomNouvelleEnveloppe = "",
                        categoriePourAjout = null,
                        erreur = null
                    )
                }
                
                // 5. Envoyer la requête à PocketBase
                val result = enveloppeRepository.creerEnveloppe(nouvelleEnveloppe)
                
                result.onSuccess { enveloppeCreee ->
                    // Mise à jour avec les vraies données du serveur
                    _uiState.update { etatActuel ->
                        val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                        val nouvellesEnveloppes = (nouveauxGroupes[categorieNom] ?: emptyList())
                            .toMutableList()
                            .map { if (it.id == nouvelleEnveloppe.id) enveloppeCreee else it }
                        
                        nouveauxGroupes[categorieNom] = nouvellesEnveloppes
                        
                        etatActuel.copy(
                            enveloppesGroupees = nouveauxGroupes
                        )
                    }
                }.onFailure { e ->
                    // En cas d'erreur, on supprime l'enveloppe temporaire
                    _uiState.update { etatActuel ->
                        val nouveauxGroupes = etatActuel.enveloppesGroupees.toMutableMap()
                        val nouvellesEnveloppes = (nouveauxGroupes[categorieNom] ?: emptyList())
                            .toMutableList()
                            .filterNot { it.id == nouvelleEnveloppe.id }
                        
                        nouveauxGroupes[categorieNom] = nouvellesEnveloppes
                        
                        etatActuel.copy(
                            enveloppesGroupees = nouveauxGroupes,
                            erreur = "Erreur lors de la création: ${e.message}"
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
