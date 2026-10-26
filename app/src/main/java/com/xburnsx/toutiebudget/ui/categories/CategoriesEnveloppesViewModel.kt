// chemin/simule: /ui/categories/CategoriesEnveloppesViewModel.kt
// Dépendances: ViewModel, Repositories, PocketBaseClient, Modèles

package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date


/**
 * ViewModel pour la gestion des catégories et enveloppes.
 * Gère les créations, suppressions et mises à jour avec synchronisation instantanée.
 */
class CategoriesEnveloppesViewModel(
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouEcriture = com.xburnsx.toutiebudget.utils.VerrouOperation()

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

                // 🔥 CORRIGER LES ORDRES SI TOUS SONT À 0
                val categoriesCorrigees = if (categories.all { it.ordre == 0 } && categories.size > 1) {
                    categories.mapIndexed { index, categorie ->
                        val categorieCorrigee = categorie.copy(ordre = index)

                        // Mettre à jour dans PocketBase immédiatement
                        launch {
                            categorieRepository.mettreAJourCategorie(categorieCorrigee)
                        }

                        categorieCorrigee
                    }
                } else {
                    categories
                }

                // Mettre à jour le cache
                categoriesMap = categoriesCorrigees.associateBy { it.id }
                enveloppesList = enveloppes.filter { !it.estArchive }

                // 🔥 ORGANISER LES DONNÉES EN RESPECTANT L'ORDRE DES CATÉGORIES
                val enveloppesGroupees = organiserDonneesPourAffichage(categoriesCorrigees, enveloppesList)

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

    /**
     * Organise les données pour l'affichage en respectant strictement l'ordre des catégories.
     */
    private fun organiserDonneesPourAffichage(
        categories: List<Categorie>,
        enveloppes: List<Enveloppe>
    ): Map<String, List<Enveloppe>> {
        val categoriesMap = categories.associateBy { it.id }

        // 🔥 TRIER LES CATÉGORIES PAR ORDRE (c'est la clé !)
        val categoriesTriees = categories.sortedBy { it.ordre }

        // Créer un LinkedHashMap pour préserver l'ordre d'insertion
        val groupes = linkedMapOf<String, List<Enveloppe>>()

        // Ajouter les catégories dans l'ordre trié
        categoriesTriees.forEach { categorie ->
            val enveloppesDeCategorie = enveloppes
                .filter { it.categorieId == categorie.id && !it.estArchive }
                .sortedBy { it.ordre }
            groupes[categorie.nom] = enveloppesDeCategorie
        }

        // Ajouter les enveloppes sans catégorie à la fin (si il y en a)
        val enveloppesSansCategorie = enveloppes
            .filter { enveloppe ->
                !categoriesMap.containsKey(enveloppe.categorieId) && !enveloppe.estArchive
            }
            .sortedBy { it.ordre }

        if (enveloppesSansCategorie.isNotEmpty()) {
            groupes["Sans catégorie"] = enveloppesSansCategorie
        }



        // 🔥 RETOURNER DIRECTEMENT LE LINKEDHASHMAP POUR PRÉSERVER L'ORDRE
        return groupes
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

        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")

                // Créer l'objet catégorie temporaire avec le bon ordre
                val nouvelOrdre = categoriesMap.values.maxByOrNull { it.ordre }?.ordre?.plus(1) ?: 0
                val categorieTemporaire = Categorie(
                    id = IdGenerator.generateId(),
                    utilisateurId = utilisateurId,
                    nom = nom,
                    ordre = nouvelOrdre
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

        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")

                // Trouver la catégorie correspondante
                val categorie = categoriesMap.values.find { it.nom == categorieNom }
                    ?: throw Exception("Catégorie '$categorieNom' introuvable")

                // ✅ CRÉATION D'UNE ENVELOPPE COMPLÈTEMENT VIDE
                val enveloppeVide = Enveloppe(
                    id = IdGenerator.generateId(),
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

        viewModelScope.lancerUneFois(verrouEcriture) {
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
        viewModelScope.lancerUneFois(verrouEcriture) {
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

        viewModelScope.lancerUneFois(verrouEcriture) {
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

        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val categorieObj = categoriesMap.values.find { it.nom == nomCategorie }
                    ?: throw Exception("Catégorie '$nomCategorie' introuvable")

                // Vérifier qu'elle est vide
                val enveloppes = _uiState.value.enveloppesGroupees[nomCategorie] ?: emptyList()
                if (enveloppes.isNotEmpty()) {
                    _uiState.update { it.copy(erreur = "Impossible de supprimer une catégorie contenant des enveloppes") }
                    return@lancerUneFois
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

                // 🔥 METTRE À JOUR LE CACHE LOCAL AVANT L'INTERFACE
                categoriesMap = nouvellesCategories.associateBy { it.id }

                // 🔥 RECONSTRUIRE LES GROUPES DANS LE BON ORDRE
                val nouveauxGroupes = organiserDonneesPourAffichage(nouvellesCategories, enveloppesList)

                // 🔥 METTRE À JOUR L'INTERFACE IMMÉDIATEMENT
                _uiState.update { currentState ->
                    currentState.copy(
                        enveloppesGroupees = nouveauxGroupes,
                        categorieEnDeplacement = null,
                        versionUI = currentState.versionUI + 1 // 🔥 FORCER LA RECOMPOSITION
                    )
                }

                // Synchroniser avec PocketBase en batch
                val categoriesModifiees = nouvellesCategories.filter { nouvelle ->
                    val ancienne = categoriesOrdonnees.find { it.id == nouvelle.id }
                    ancienne?.ordre != nouvelle.ordre
                }

                // 🔥 SYNCHRONISER AVEC POCKETBASE
                val misesAJour = categoriesModifiees.map { categorie ->
                    categorieRepository.mettreAJourCategorie(categorie)
                }

                // 🔥 ATTENDRE QUE TOUTES LES MISES À JOUR SOIENT TERMINÉES
                misesAJour.forEach { resultat ->
                    resultat.onFailure { erreur ->
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

    // ===== UTILITAIRES =====

    fun onEffacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }
}
