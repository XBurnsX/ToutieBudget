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
                    emptyList()
                }
                val enveloppes = enveloppesResult.getOrElse {
                    emptyList()
                }

                // Mettre à jour le cache
                categoriesMap = categories.associateBy { it.id }
                enveloppesList = enveloppes.filter { !it.estArchive }

                // Organiser les données pour l'affichage
                val enveloppesGroupees =
                    OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(
                        categories,
                        enveloppesList
                    )

                // Mettre à jour l'interface
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        enveloppesGroupees = enveloppesGroupees,
                        erreur = null
                    )
                }


            } catch (e: Exception) {
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
                        enveloppesGroupees = nouveauxGroupes,
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

                }.onFailure { erreur ->
                    // Supprimer la catégorie temporaire en cas d'erreur
                    val groupesCorriges = _uiState.value.enveloppesGroupees.toMutableMap()
                    groupesCorriges.remove(nom)

                    _uiState.update { currentState ->
                        currentState.copy(
                            enveloppesGroupees = groupesCorriges
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
                    // ✅ OBJECTIFS VIDES PAR DÉFAUT avec nouveaux noms
                    typeObjectif = TypeObjectif.Aucun,  // Pas d'objectif
                    objectifMontant = 0.0,             // Pas de montant objectif
                    dateObjectif = null,               // Pas de date d'objectif (String)
                    dateDebutObjectif = null,          // Pas de date de début
                    objectifJour = null                // Pas de jour spécifique
                )


                // Mise à jour optimiste de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                val enveloppesDeCategorie =
                    (nouveauxGroupes[categorieNom] ?: emptyList()).toMutableList()
                enveloppesDeCategorie.add(enveloppeVide)
                nouveauxGroupes[categorieNom] = enveloppesDeCategorie

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
                    enveloppesList = enveloppesList.map { enveloppe ->
                        if (enveloppe.id == enveloppeVide.id) enveloppeCreee else enveloppe
                    }

                    // Recharger pour s'assurer de la cohérence
                    chargerDonnees()

                    // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier tous les autres ViewModels
                    realtimeSyncService.declencherMiseAJourBudget()

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
                enveloppePourObjectif = enveloppe,
                // PRÉ-REMPLIR LE FORMULAIRE AVEC LES DONNÉES EXISTANTES
                objectifFormState = ObjectifFormState(
                    type = enveloppe.typeObjectif,
                    montant = if (enveloppe.objectifMontant > 0) enveloppe.objectifMontant.toString() else "",
                    date = enveloppe.dateDebutObjectif, // Utilise dateDebutObjectif au lieu d'objectifDate
                    dateDebut = enveloppe.dateDebutObjectif, // CHARGER LA DATE DE DÉBUT
                    dateFin = enveloppe.dateObjectif, // 🆕 CHARGER LA DATE DE FIN
                    jour = enveloppe.objectifJour,
                    resetApresEcheance = enveloppe.resetApresEcheance // 🆕 CHARGER LE CHAMP RESET
                )
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
            // Pour les objectifs annuels, pas besoin d'initialiser une date spéciale
            // La date de début peut être aujourd'hui (null = aujourd'hui par défaut)
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

    fun onObjectifDateDebutChange(date: Date?) {
        _uiState.update {
            it.copy(objectifFormState = it.objectifFormState.copy(dateDebut = date))
        }
    }

    fun onObjectifDateFinChange(date: Date?) {
        _uiState.update {
            it.copy(objectifFormState = it.objectifFormState.copy(dateFin = date))
        }
    }

    fun onObjectifJourChange(jour: Int?) {
        _uiState.update {
            it.copy(objectifFormState = it.objectifFormState.copy(jour = jour))
        }
    }

    fun onObjectifResetApresEcheanceChange(resetApresEcheance: Boolean) {
        _uiState.update {
            it.copy(objectifFormState = it.objectifFormState.copy(resetApresEcheance = resetApresEcheance))
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
                // 🔥 CALCULER LA DATE DE DÉBUT SELON LE TYPE D'OBJECTIF
                val dateDebutCalculee = when (formState.type) {
                    TypeObjectif.Mensuel -> {
                        // Pour les objectifs mensuels, utiliser le jour sélectionné du mois actuel
                        val calendar = Calendar.getInstance()
                        val jourSelectionne = formState.jour ?: calendar.get(Calendar.DAY_OF_MONTH)
                        calendar.set(Calendar.DAY_OF_MONTH, jourSelectionne)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        // S'assurer que c'est le mois actuel ou le suivant si le jour est déjà passé
                        if (calendar.time.before(Date())) {
                            calendar.add(Calendar.MONTH, 1)
                        }
                        calendar.time
                    }

                    TypeObjectif.Bihebdomadaire -> {
                        // 🔥 CORRECTION: Pour les objectifs bihebdomadaires, utiliser formState.date (pas dateDebut)
                        val dateSelectionnee = formState.date
                        if (dateSelectionnee != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = dateSelectionnee
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            calendar.time
                        } else {
                            null
                        }
                    }

                    TypeObjectif.Annuel -> {
                        // Pour les objectifs annuels, utiliser la date sélectionnée dans le date picker
                        formState.date ?: Date()
                    }

                    TypeObjectif.Echeance -> {
                        // Pour les échéances, utiliser la date définie ou aujourd'hui
                        formState.dateDebut ?: Date()
                    }

                    else -> null
                }

                // 🔥 CALCULER LA DATE D'OBJECTIF SELON LE TYPE
                val dateObjectifCalculee = when (formState.type) {
                    TypeObjectif.Mensuel -> {
                        // Pour les objectifs mensuels, la date d'objectif est la même que la date de début
                        dateDebutCalculee
                    }

                    TypeObjectif.Bihebdomadaire -> {
                        // Pour les objectifs bihebdomadaires, date d'objectif = date de début + 14 jours
                        dateDebutCalculee?.let { dateDebut ->
                            val calendar = Calendar.getInstance()
                            calendar.time = dateDebut
                            calendar.add(Calendar.DAY_OF_YEAR, 14) // Ajouter 14 jours
                            calendar.time
                        }
                    }

                    TypeObjectif.Echeance -> {
                        // 🆕 Pour les échéances, utiliser la date de fin sélectionnée
                        formState.dateFin
                    }

                    TypeObjectif.Annuel -> {
                        // Pour les objectifs annuels, calculer date de fin = date début + 12 mois
                        dateDebutCalculee?.let { dateDebut ->
                            val calendar = Calendar.getInstance()
                            calendar.time = dateDebut
                            calendar.add(Calendar.MONTH, 12) // Ajouter 12 mois
                            calendar.time
                        }
                    }

                    else -> null
                }

                val enveloppeModifiee = enveloppe.copy(
                    objectifMontant = montant,
                    typeObjectif = formState.type,
                    dateDebutObjectif = dateDebutCalculee, // 🔥 UTILISER la date de début calculée
                    dateObjectif = if (formState.type == TypeObjectif.Echeance) formState.dateFin else dateObjectifCalculee, // 🆕 UTILISER dateObjectif au lieu de dateFinObjectif
                    objectifJour = formState.jour,
                    resetApresEcheance = formState.resetApresEcheance // 🆕 AJOUTER le champ resetApresEcheance
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
                    // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier le budget après modification d'objectif
                    realtimeSyncService.declencherMiseAJourBudget()
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

    // ===== GESTION DE LA SUPPRESSION D'OBJECTIF =====

    /**
     * Supprime uniquement l'objectif d'une enveloppe (remet à zéro) sans supprimer l'enveloppe.
     */
    fun onSupprimerObjectifEnveloppe(enveloppe: Enveloppe) {
        viewModelScope.launch {
            try {
                // Créer une copie de l'enveloppe sans objectif
                val enveloppeSansObjectif = enveloppe.copy(
                    typeObjectif = TypeObjectif.Aucun,
                    objectifMontant = 0.0,
                    dateDebutObjectif = null,
                    dateObjectif = null, // 🆕 RESETTER la date d'objectif
                    objectifJour = null,
                    resetApresEcheance = false // 🆕 RESETTER le champ resetApresEcheance
                )

                // Mise à jour instantanée de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes.forEach { (categorie, enveloppes) ->
                    val nouvellesEnveloppes = enveloppes.map { env ->
                        if (env.id == enveloppe.id) enveloppeSansObjectif else env
                    }
                    nouveauxGroupes[categorie] = nouvellesEnveloppes
                }

                _uiState.update { currentState ->
                    currentState.copy(enveloppesGroupees = nouveauxGroupes)
                }

                // Envoyer à PocketBase
                enveloppeRepository.mettreAJourEnveloppe(enveloppeSansObjectif).onSuccess {
                    // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier le budget après suppression d'objectif
                    realtimeSyncService.declencherMiseAJourBudget()
                }.onFailure { erreur ->
                    _uiState.update { it.copy(erreur = "Erreur suppression objectif: ${erreur.message}") }
                    chargerDonnees() // Recharger en cas d'erreur
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                chargerDonnees()
            }
        }
    }

    // ===== GESTION DES SUPPRESSIONS =====

    fun onOuvrirConfirmationSuppressionEnveloppe(enveloppe: Enveloppe) {
        _uiState.update {
            it.copy(
                enveloppePourSuppression = enveloppe,
                isConfirmationSuppressionEnveloppeVisible = true
            )
        }
    }

    fun onFermerConfirmationSuppressionEnveloppe() {
        _uiState.update {
            it.copy(
                enveloppePourSuppression = null,
                isConfirmationSuppressionEnveloppeVisible = false
            )
        }
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
        _uiState.update {
            it.copy(
                categoriePourSuppression = nomCategorie,
                isConfirmationSuppressionCategorieVisible = true
            )
        }
    }

    fun onFermerConfirmationSuppressionCategorie() {
        _uiState.update {
            it.copy(
                categoriePourSuppression = null,
                isConfirmationSuppressionCategorieVisible = false
            )
        }
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


    // ===== GESTION DU DÉPLACEMENT DES CATÉGORIES =====

    /**
     * Active ou désactive le mode de réorganisation des catégories.
     */
    fun onToggleModeReorganisation() {
        _uiState.update {
            it.copy(
                isModeReorganisation = !it.isModeReorganisation,
                categorieEnDeplacement = null,
                ordreTemporaire = emptyMap()
            )
        }
    }

    /**
     * Démarre le déplacement d'une catégorie.
     */
    fun onDebuterDeplacementCategorie(nomCategorie: String) {
        _uiState.update {
            it.copy(categorieEnDeplacement = nomCategorie)
        }
    }

    /**
     * Termine le déplacement d'une catégorie.
     */
    fun onTerminerDeplacementCategorie() {
        _uiState.update {
            it.copy(categorieEnDeplacement = null)
        }
    }

    /**
     * Déplace une catégorie vers une nouvelle position.
     * Met à jour l'ordre des catégories et synchronise avec PocketBase.
     */
    fun onDeplacerCategorie(nomCategorie: String, nouvellePosition: Int) {
        viewModelScope.launch {
            try {
                // Obtenir la liste actuelle des catégories triées par ordre
                val categoriesOrdonnees = categoriesMap.values.sortedBy { it.ordre }
                val categorieADeplacer = categoriesOrdonnees.find { it.nom == nomCategorie }
                    ?: throw Exception("Catégorie '$nomCategorie' introuvable")

                // Calculer les nouveaux ordres
                val nouvellesCategories = calculerNouveauxOrdres(
                    categoriesOrdonnees,
                    categorieADeplacer,
                    nouvellePosition
                )

                // 🔥 FORCE LE REFRESH IMMÉDIAT - APPROCHE SIMPLE
                // Créer une nouvelle map ordonnée selon les nouveaux ordres
                val nouveauxGroupes = mutableMapOf<String, List<Enveloppe>>()

                // Reconstruire la map dans le bon ordre
                nouvellesCategories.sortedBy { it.ordre }.forEach { categorie ->
                    val enveloppesExistantes = _uiState.value.enveloppesGroupees[categorie.nom] ?: emptyList()
                    nouveauxGroupes[categorie.nom] = enveloppesExistantes
                }

                // Mettre à jour le cache local AVANT l'interface
                categoriesMap = nouvellesCategories.associateBy { it.id }

                // Force le refresh complet en créant une nouvelle instance
                _uiState.update {
                    CategoriesEnveloppesUiState(
                        isLoading = false,
                        erreur = null,
                        enveloppesGroupees = nouveauxGroupes.toMap(),
                        isModeReorganisation = it.isModeReorganisation,
                        categorieEnDeplacement = null,
                        // Copier tous les autres états existants
                        isAjoutCategorieDialogVisible = it.isAjoutCategorieDialogVisible,
                        isAjoutEnveloppeDialogVisible = it.isAjoutEnveloppeDialogVisible,
                        isObjectifDialogVisible = it.isObjectifDialogVisible,
                        isConfirmationSuppressionCategorieVisible = it.isConfirmationSuppressionCategorieVisible,
                        isConfirmationSuppressionEnveloppeVisible = it.isConfirmationSuppressionEnveloppeVisible,
                        categoriePourAjout = it.categoriePourAjout,
                        categoriePourSuppression = it.categoriePourSuppression,
                        enveloppePourSuppression = it.enveloppePourSuppression,
                        nomNouvelleCategorie = it.nomNouvelleCategorie,
                        nomNouvelleEnveloppe = it.nomNouvelleEnveloppe,
                        enveloppePourObjectif = it.enveloppePourObjectif,
                        objectifFormState = it.objectifFormState,
                        enveloppeEnDeplacement = it.enveloppeEnDeplacement,
                        ordreTemporaire = emptyMap()
                    )
                }

                // Synchroniser avec PocketBase en batch
                val categoriesModifiees = nouvellesCategories.filter { nouvelle ->
                    val ancienne = categoriesOrdonnees.find { it.id == nouvelle.id }
                    ancienne?.ordre != nouvelle.ordre
                }

                categoriesModifiees.forEach { categorie ->
                    categorieRepository.mettreAJourCategorie(categorie).onFailure { erreur ->
                        _uiState.update { it.copy(erreur = "Erreur déplacement: ${erreur.message}") }
                        // En cas d'erreur, recharger les données depuis PocketBase
                        chargerDonnees()
                        return@launch
                    }
                }

                // 🔥 SYNCHRONISATION TEMPS RÉEL : Notifier les autres ViewModels
                realtimeSyncService.declencherMiseAJourBudget()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                chargerDonnees()
            }
        }
    }

    /**
     * Échange la position de deux catégories adjacentes.
     */
    fun onEchangerCategoriesAdjacentes(nomCategorie1: String, nomCategorie2: String) {
        viewModelScope.launch {
            try {
                val categorie1 = categoriesMap.values.find { it.nom == nomCategorie1 }
                    ?: throw Exception("Catégorie '$nomCategorie1' introuvable")
                val categorie2 = categoriesMap.values.find { it.nom == nomCategorie2 }
                    ?: throw Exception("Catégorie '$nomCategorie2' introuvable")

                // Échanger les ordres
                val categorieModifiee1 = categorie1.copy(ordre = categorie2.ordre)
                val categorieModifiee2 = categorie2.copy(ordre = categorie1.ordre)

                // Mise à jour optimiste de l'interface
                val nouvellesCategories = categoriesMap.values.map { categorie ->
                    when (categorie.id) {
                        categorie1.id -> categorieModifiee1
                        categorie2.id -> categorieModifiee2
                        else -> categorie
                    }
                }

                val nouveauxGroupes = reorganiserGroupesParOrdre(nouvellesCategories)
                _uiState.update { currentState ->
                    currentState.copy(enveloppesGroupees = nouveauxGroupes)
                }

                // Mettre à jour le cache
                categoriesMap = nouvellesCategories.associateBy { it.id }

                // Synchroniser avec PocketBase
                categorieRepository.mettreAJourCategorie(categorieModifiee1)
                categorieRepository.mettreAJourCategorie(categorieModifiee2)

                // 🔥 SYNCHRONISATION TEMPS RÉEL
                realtimeSyncService.declencherMiseAJourBudget()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur échange: ${e.message}") }
                chargerDonnees()
            }
        }
    }

    /**
     * Calcule les nouveaux ordres après déplacement d'une catégorie.
     */
    private fun calculerNouveauxOrdres(
        categoriesOrdonnees: List<Categorie>,
        categorieADeplacer: Categorie,
        nouvellePosition: Int
    ): List<Categorie> {
        val listeModifiable = categoriesOrdonnees.toMutableList()

        // Retirer la catégorie de sa position actuelle
        val positionActuelle = listeModifiable.indexOfFirst { it.id == categorieADeplacer.id }
        if (positionActuelle == -1) return categoriesOrdonnees

        listeModifiable.removeAt(positionActuelle)

        // Insérer à la nouvelle position (en s'assurant qu'elle est valide)
        val positionCible = nouvellePosition.coerceIn(0, listeModifiable.size)
        listeModifiable.add(positionCible, categorieADeplacer)

        // Recalculer tous les ordres pour garantir la cohérence
        return listeModifiable.mapIndexed { index, categorie ->
            categorie.copy(ordre = index)
        }
    }

    /**
     * Réorganise les groupes d'enveloppes selon le nouvel ordre des catégories.
     */
    private fun reorganiserGroupesParOrdre(nouvellesCategories: List<Categorie>): Map<String, List<Enveloppe>> {
        val enveloppesGroupees = _uiState.value.enveloppesGroupees

        return nouvellesCategories
            .sortedBy { it.ordre }
            .associate { categorie ->
                categorie.nom to (enveloppesGroupees[categorie.nom] ?: emptyList())
            }
    }

    // ===== GESTION DU DÉPLACEMENT DES ENVELOPPES =====

    /**
     * Déplace une enveloppe vers une nouvelle position dans sa catégorie.
     * Met à jour l'ordre des enveloppes et synchronise avec PocketBase.
     */
    fun onDeplacerEnveloppe(enveloppeId: String, nouvellePosition: Int) {
        viewModelScope.launch {
            try {
                // Trouver l'enveloppe et sa catégorie
                val enveloppeADeplacer = enveloppesList.find { it.id == enveloppeId }
                    ?: throw Exception("Enveloppe '$enveloppeId' introuvable")

                val categorieNom =
                    categoriesMap.values.find { it.id == enveloppeADeplacer.categorieId }?.nom
                        ?: throw Exception("Catégorie de l'enveloppe introuvable")

                // Obtenir les enveloppes de cette catégorie triées par ordre
                val enveloppesDeCategorie = enveloppesList
                    .filter { it.categorieId == enveloppeADeplacer.categorieId && !it.estArchive }
                    .sortedBy { it.ordre }

                // Calculer les nouveaux ordres
                val nouvellesEnveloppes = calculerNouveauxOrdresEnveloppes(
                    enveloppesDeCategorie,
                    enveloppeADeplacer,
                    nouvellePosition
                )

                // Mise à jour optimiste de l'interface
                val nouveauxGroupes = _uiState.value.enveloppesGroupees.toMutableMap()
                nouveauxGroupes[categorieNom] = nouvellesEnveloppes

                _uiState.update { currentState ->
                    currentState.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        enveloppeEnDeplacement = null
                    )
                }

                // Mettre à jour le cache local
                enveloppesList = enveloppesList.map { enveloppe ->
                    nouvellesEnveloppes.find { it.id == enveloppe.id } ?: enveloppe
                }

                // Synchroniser avec PocketBase en batch
                val enveloppesModifiees = nouvellesEnveloppes.filter { nouvelle ->
                    val ancienne = enveloppesDeCategorie.find { it.id == nouvelle.id }
                    ancienne?.ordre != nouvelle.ordre
                }

                enveloppesModifiees.forEach { enveloppe ->
                    enveloppeRepository.mettreAJourEnveloppe(enveloppe).onFailure { erreur ->
                        _uiState.update { it.copy(erreur = "Erreur déplacement enveloppe: ${erreur.message}") }
                        chargerDonnees()
                        return@launch
                    }
                }

                // 🔥 SYNCHRONISATION TEMPS RÉEL
                realtimeSyncService.declencherMiseAJourBudget()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
                chargerDonnees()
            }
        }
    }

    /**
     * Calcule les nouveaux ordres après déplacement d'une enveloppe.
     */
    private fun calculerNouveauxOrdresEnveloppes(
        enveloppesOrdonnees: List<Enveloppe>,
        enveloppeADeplacer: Enveloppe,
        nouvellePosition: Int
    ): List<Enveloppe> {
        val listeModifiable = enveloppesOrdonnees.toMutableList()

        // Retirer l'enveloppe de sa position actuelle
        val positionActuelle = listeModifiable.indexOfFirst { it.id == enveloppeADeplacer.id }
        if (positionActuelle == -1) return enveloppesOrdonnees

        listeModifiable.removeAt(positionActuelle)

        // Insérer à la nouvelle position (en s'assurant qu'elle est valide)
        val positionCible = nouvellePosition.coerceIn(0, listeModifiable.size)
        listeModifiable.add(positionCible, enveloppeADeplacer)

        // Recalculer tous les ordres pour garantir la cohérence
        return listeModifiable.mapIndexed { index, enveloppe ->
            enveloppe.copy(ordre = index)
        }
    }

    /**
     * Démarre le déplacement d'une enveloppe.
     */
    fun onDebuterDeplacementEnveloppe(enveloppeId: String) {
        _uiState.update {
            it.copy(enveloppeEnDeplacement = enveloppeId)
        }
    }

    /**
     * Termine le déplacement d'une enveloppe.
     */
    fun onTerminerDeplacementEnveloppe() {
        _uiState.update {
            it.copy(enveloppeEnDeplacement = null)
        }
    }

    // ===== UTILITAIRES =====

    fun onEffacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    private fun organiserGroupes(groupes: Map<String, List<Enveloppe>>): Map<String, List<Enveloppe>> {
        val categoriesOrdonnees = categoriesMap.values.sortedBy { it.ordre }.map { it.nom }
        return groupes.toSortedMap(compareBy { nomCategorie ->
            val indexExistant = categoriesOrdonnees.indexOf(nomCategorie)
            // Si la catégorie existe déjà dans l'ordre, utiliser son index
            // Sinon, la placer à la fin (index très grand)
            if (indexExistant != -1) indexExistant else Int.MAX_VALUE
        })
    }
}
