// chemin/simule: /ui/categories/CategoriesEnveloppesViewModel.kt
package com.xburnsx.toutiebudget.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

class CategoriesEnveloppesViewModel(
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesEnveloppesUiState())
    val uiState: StateFlow<CategoriesEnveloppesUiState> = _uiState.asStateFlow()

    init {
        chargerCategoriesEtEnveloppes()
    }

    fun chargerCategoriesEtEnveloppes() {
        println("[DEBUG] chargerCategoriesEtEnveloppes appelé")
        viewModelScope.launch {
            try {
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()
                
                println("[DEBUG] Enveloppes récupérées: ${enveloppes.size}")
                println("[DEBUG] Catégories récupérées: ${categories.size}")
                
                // Créer un map pour accéder rapidement aux catégories par ID
                val categoriesMap = categories.associateBy { it.id }
                println("[DEBUG] CategoriesMap: ${categoriesMap.map { "${it.key} -> ${it.value.nom}" }}")
                
                // Grouper les enveloppes par catégorie
                val groupesEnveloppes = enveloppes.filter { !it.estArchive }.groupBy { enveloppe ->
                    println("[DEBUG] Enveloppe ${enveloppe.nom} a categorieId: ${enveloppe.categorieId}")
                    val categorie = categoriesMap[enveloppe.categorieId]
                    println("[DEBUG] Catégorie trouvée pour ${enveloppe.nom}: ${categorie?.nom ?: "NULL"}")
                    categorie?.nom ?: "Autre"
                }
                
                // Créer un map complet avec toutes les catégories (même vides)
                val groupesComplets = categories.associate { categorie ->
                    categorie.nom to (groupesEnveloppes[categorie.nom] ?: emptyList())
                }
                
                val groupes = groupesComplets
                
                println("[DEBUG] Catégories détectées: " + groupes.keys.joinToString())
                println("[DEBUG] Enveloppes récupérées: " + enveloppes.joinToString { "${it.nom} (cat: ${categoriesMap[it.categorieId]?.nom ?: "Autre"})" })
                println("[DEBUG] Groupes finaux: " + groupes.map { "${it.key}: ${it.value.size} enveloppes" })
                
                // On ne touche à enveloppesGroupees que quand tout est prêt
                _uiState.update { it.copy(enveloppesGroupees = groupes) }
            } catch (e: Exception) {
                println("[DEBUG] Erreur dans chargerCategoriesEtEnveloppes: ${e.message}")
                e.printStackTrace()
                // On ne touche PAS à enveloppesGroupees ici !
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }

    fun onOuvrirAjoutCategorieDialog() {
        _uiState.update { it.copy(isAjoutCategorieDialogVisible = true, nomNouvelleCategorie = "") }
    }

    fun onOuvrirAjoutEnveloppeDialog(categorie: String) {
        _uiState.update { it.copy(isAjoutEnveloppeDialogVisible = true, nomNouvelleEnveloppe = "", categoriePourAjout = categorie) }
    }

    fun onFermerDialogues() {
        _uiState.update { it.copy(
            isAjoutCategorieDialogVisible = false,
            isAjoutEnveloppeDialogVisible = false,
            isObjectifDialogVisible = false
        )}
    }

    fun onNomNouvelleCategorieChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleCategorie = nom) }
    }

    fun onNomNouvelleEnveloppeChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleEnveloppe = nom) }
    }

    fun sauvegarderNouvelleCategorie() {
        println("[DEBUG] sauvegarderNouvelleCategorie appelé")
        viewModelScope.launch {
            val state = _uiState.value
            println("[DEBUG] Nom de la catégorie: '${state.nomNouvelleCategorie}'")
            
            if (state.nomNouvelleCategorie.isBlank()) {
                println("[DEBUG] Nom vide, annulation")
                return@launch
            }
            
            // Récupérer l'ID de l'utilisateur connecté
            val utilisateurId = try {
                // Utiliser le client PocketBase pour récupérer l'utilisateur connecté
                val client = com.xburnsx.toutiebudget.di.PocketBaseClient
                client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur non connecté")
            } catch (e: Exception) {
                println("[DEBUG] Erreur récupération utilisateur: ${e.message}")
                throw Exception("Impossible de récupérer l'utilisateur connecté")
            }
            
            val nouvelleCategorie = Categorie(
                id = "", // L'ID sera généré par PocketBase
                utilisateurId = utilisateurId,
                nom = state.nomNouvelleCategorie
            )
            
            println("[DEBUG] Création de la catégorie: $nouvelleCategorie")
            
            categorieRepository.creerCategorie(nouvelleCategorie).onSuccess {
                println("[DEBUG] Catégorie créée avec succès")
                onFermerDialogues()
                chargerCategoriesEtEnveloppes()
            }.onFailure { e ->
                println("[DEBUG] Erreur lors de la création: ${e.message}")
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }

    fun sauvegarderNouvelleEnveloppe() {
        println("[DEBUG] sauvegarderNouvelleEnveloppe appelé")
        viewModelScope.launch {
            val state = _uiState.value
            println("[DEBUG] Nom de l'enveloppe: '${state.nomNouvelleEnveloppe}'")
            println("[DEBUG] Catégorie pour ajout: '${state.categoriePourAjout}'")
            
            if (state.nomNouvelleEnveloppe.isBlank()) {
                println("[DEBUG] Nom vide, annulation")
                return@launch
            }
            
            // Trouver l'ID de la catégorie par son nom
            val categorieId = try {
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()
                println("[DEBUG] Toutes les catégories disponibles: ${categories.map { "${it.nom} (${it.id})" }}")
                println("[DEBUG] Recherche de la catégorie: '${state.categoriePourAjout}'")
                val categorie = categories.find { it.nom == state.categoriePourAjout }
                println("[DEBUG] Catégorie trouvée: ${categorie?.nom} (ID: ${categorie?.id})")
                if (categorie == null) {
                    println("[DEBUG] Catégorie non trouvée, utilisation de 'categorie_autre'")
                }
                categorie?.id ?: "categorie_autre"
            } catch (e: Exception) {
                println("[DEBUG] Erreur récupération catégories: ${e.message}")
                "categorie_autre"
            }
            
            // Récupérer l'ID de l'utilisateur connecté
            val utilisateurId = try {
                val client = com.xburnsx.toutiebudget.di.PocketBaseClient
                client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur non connecté")
            } catch (e: Exception) {
                println("[DEBUG] Erreur récupération utilisateur: ${e.message}")
                throw Exception("Impossible de récupérer l'utilisateur connecté")
            }
            
            println("[DEBUG] Création de l'enveloppe avec categorieId: $categorieId")
            
            val nouvelleEnveloppe = Enveloppe(
                id = "", // L'ID sera généré par PocketBase
                utilisateurId = utilisateurId,
                nom = state.nomNouvelleEnveloppe,
                categorieId = categorieId,
                estArchive = false,
                ordre = (state.enveloppesGroupees.values.flatten().size) + 1
            )
            
            println("[DEBUG] Enveloppe à créer: $nouvelleEnveloppe")
            
            enveloppeRepository.creerEnveloppe(nouvelleEnveloppe).onSuccess {
                println("[DEBUG] Enveloppe créée avec succès")
                onFermerDialogues()
                chargerCategoriesEtEnveloppes()
            }.onFailure { e ->
                println("[DEBUG] Erreur lors de la création de l'enveloppe: ${e.message}")
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }

    fun onOuvrirObjectifDialog(enveloppe: Enveloppe) {
        _uiState.update {
            it.copy(
                isObjectifDialogVisible = true,
                enveloppePourObjectif = enveloppe,
                objectifFormState = ObjectifFormState(
                    type = enveloppe.objectifType,
                    montant = if (enveloppe.objectifMontant > 0) enveloppe.objectifMontant.toString() else "",
                    date = enveloppe.objectifDate,
                    jour = enveloppe.objectifJour
                )
            )
        }
    }

    fun onObjectifFormChange(type: TypeObjectif? = null, montant: String? = null, date: Date? = null, jour: Int? = null) {
        _uiState.update {
            it.copy(
                objectifFormState = it.objectifFormState.copy(
                    type = type ?: it.objectifFormState.type,
                    montant = montant ?: it.objectifFormState.montant,
                    date = date ?: it.objectifFormState.date,
                    jour = jour ?: it.objectifFormState.jour
                )
            )
        }
    }

    fun sauvegarderObjectif() {
        viewModelScope.launch {
            val enveloppeOriginale = _uiState.value.enveloppePourObjectif ?: return@launch
            val formState = _uiState.value.objectifFormState

            val enveloppeMiseAJour = enveloppeOriginale.copy(
                objectifType = formState.type,
                objectifMontant = formState.montant.toDoubleOrNull() ?: 0.0,
                objectifDate = formState.date,
                objectifJour = formState.jour
            )
            enveloppeRepository.mettreAJourEnveloppe(enveloppeMiseAJour).onSuccess {
                onFermerDialogues()
                chargerCategoriesEtEnveloppes()
            }.onFailure { e ->
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }
}
