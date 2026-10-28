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
    private val realtimeSyncService: RealtimeSyncService,
    private val transactionRepository: com.xburnsx.toutiebudget.data.repositories.TransactionRepository? = null
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouEcriture = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(CategoriesEnveloppesUiState())
    val uiState: StateFlow<CategoriesEnveloppesUiState> = _uiState.asStateFlow()

    // Cache des données pour optimiser les performances
    private var categoriesMap = mapOf<String, Categorie>()
    private var enveloppesList = listOf<Enveloppe>()
    private var generationChargement = 0L

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
        val generation = ++generationChargement
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // Charger les données en parallèle
                val categoriesResult = categorieRepository.recupererToutesLesCategories()
                val enveloppesResult = enveloppeRepository.recupererToutesLesEnveloppes()

                // CE6 : un échec réseau/Room n'est JAMAIS une liste vide.
                val categoriesCorrigees = categoriesResult.getOrThrow()
                val enveloppes = enveloppesResult.getOrThrow()
                if (generation != generationChargement) return@launch

                // CE12 : aucune réécriture des ordres au simple chargement.
                // Une migration contrôlée doit être séparée de l'affichage.
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
                        categoriesParId = categoriesMap,
                        enveloppesArchivees = enveloppes.filter { it.estArchive },
                        erreur = null
                    )
                }

            } catch (e: Exception) {
                if (generation != generationChargement) return@launch
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
            groupes[categorie.id] = enveloppesDeCategorie
        }

        // Ajouter les enveloppes sans catégorie à la fin (si il y en a)
        val enveloppesSansCategorie = enveloppes
            .filter { enveloppe ->
                !categoriesMap.containsKey(enveloppe.categorieId) && !enveloppe.estArchive
            }
            .sortedBy { it.ordre }

        if (enveloppesSansCategorie.isNotEmpty()) {
            groupes["__sans_categorie__"] = enveloppesSansCategorie
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

                    // CE7 : la vraie erreur est affichée et la saisie est rendue
                    _uiState.update { currentState ->
                        currentState.copy(
                            enveloppesGroupees = groupesCorriges,
                            isAjoutCategorieDialogVisible = true,
                            nomNouvelleCategorie = nom,
                            erreur = "Erreur lors de la création de la catégorie : ${erreur.message}"
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
                val categorie = categoriesMap[categorieNom]
                    ?: categoriesMap.values.singleOrNull { it.nom == categorieNom }
                    ?: throw Exception("Catégorie '$categorieNom' introuvable ou ambiguë")

                // CE8 : pas deux enveloppes au même nom dans une catégorie ; CE10 : ordre stable dès la création
                // CE8/CA4 : même une homonyme archivée doit être contrôlée,
                // sinon la restauration créerait deux enveloppes identiques.
                val voisines = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                    .filter { it.categorieId == categorie.id }
                if (voisines.any { it.nom.trim().equals(nom, ignoreCase = true) }) {
                    _uiState.update { it.copy(erreur = "L'enveloppe « $nom » existe déjà dans $categorieNom") }
                    return@lancerUneFois
                }
                val ordre = (voisines.maxOfOrNull { it.ordre } ?: -1) + 1

                // ✅ CRÉATION D'UNE ENVELOPPE COMPLÈTEMENT VIDE
                val enveloppeVide = Enveloppe(
                    id = IdGenerator.generateId(),
                    utilisateurId = utilisateurId,
                    nom = nom,
                    categorieId = categorie.id, // IMPORTANT: Lien avec la catégorie
                    estArchive = false,
                    ordre = ordre, // CE10
                    // ✅ OBJECTIFS VIDES PAR DÉFAUT avec nouveaux noms
                    typeObjectif = TypeObjectif.Aucun,  // Pas d'objectif
                    objectifMontant = 0.0,             // Pas de montant objectif
                    dateObjectif = null,               // Pas de date d'objectif (String)
                    dateDebutObjectif = null,          // Pas de date de début
                    objectifJour = null                // Pas de jour spécifique
                )


                // CE7 : ne pas fermer la fenêtre ni effacer le formulaire avant
                // que Room ait confirmé la création. Le résultat d'un repository
                // en échec doit rester visible et saisissable pour une nouvelle tentative.
                enveloppeRepository.creerEnveloppe(enveloppeVide).getOrThrow()
                _uiState.update { current ->
                    current.copy(
                        isAjoutEnveloppeDialogVisible = false,
                        nomNouvelleEnveloppe = "",
                        categoriePourAjout = null,
                        erreur = null
                    )
                }
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        erreur = "Erreur lors de la création : ${e.message}",
                        isAjoutEnveloppeDialogVisible = true
                    )
                }
            }
        }
    }

    /** CA1 : renommer sans changer l'ID ni les références des enveloppes. */
    fun renommerCategorie(categorieId: String, nouveauNom: String) {
        val nom = nouveauNom.trim()
        if (nom.isBlank()) {
            _uiState.update { it.copy(erreur = "Le nom de catégorie est requis.") }
            return
        }
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val categorie = categoriesMap[categorieId]
                    ?: error("Catégorie introuvable.")
                require(categoriesMap.values.none {
                    it.id != categorieId && it.nom.trim().equals(nom, ignoreCase = true)
                }) { "Une catégorie porte déjà ce nom." }
                if (nom != categorie.nom) {
                    categorieRepository.mettreAJourCategorie(categorie.copy(nom = nom)).getOrThrow()
                }
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Renommage refusé : ${e.message}") }
            }
        }
    }

    /** CA2 : conserve l'identité et tous les montants de l'enveloppe. */
    fun renommerEnveloppe(enveloppeId: String, nouveauNom: String) {
        val nom = nouveauNom.trim()
        if (nom.isBlank()) {
            _uiState.update { it.copy(erreur = "Le nom d'enveloppe est requis.") }
            return
        }
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val toutes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val env = toutes.firstOrNull { it.id == enveloppeId }
                    ?: error("Enveloppe introuvable.")
                require(toutes.none {
                    it.id != env.id && it.categorieId == env.categorieId &&
                        it.nom.trim().equals(nom, ignoreCase = true)
                }) { "Une enveloppe porte déjà ce nom dans cette catégorie." }
                if (env.nom != nom) enveloppeRepository.mettreAJourEnveloppe(
                    env.copy(nom = nom)
                ).getOrThrow()
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Renommage refusé : ${e.message}") }
            }
        }
    }

    /** CA3 : déplacer le classement sans toucher aux transactions ni allocations. */
    fun deplacerEnveloppeCategorie(enveloppeId: String, destinationCategorieId: String) {
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                require(categoriesMap.containsKey(destinationCategorieId)) {
                    "Catégorie de destination introuvable."
                }
                val toutes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val env = toutes.firstOrNull { it.id == enveloppeId }
                    ?: error("Enveloppe introuvable.")
                if (env.categorieId == destinationCategorieId) return@lancerUneFois
                require(toutes.none {
                    it.id != env.id && it.categorieId == destinationCategorieId &&
                        it.nom.trim().equals(env.nom.trim(), ignoreCase = true)
                }) { "Une enveloppe porte déjà ce nom dans la catégorie de destination." }
                val ordre = (toutes.filter { it.categorieId == destinationCategorieId }
                    .maxOfOrNull { it.ordre } ?: -1) + 1
                enveloppeRepository.mettreAJourEnveloppe(
                    env.copy(categorieId = destinationCategorieId, ordre = ordre)
                ).getOrThrow()
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Déplacement refusé : ${e.message}") }
            }
        }
    }

    /** CA4 : restauration par UPDATE, jamais DELETE/CREATE. */
    fun restaurerEnveloppe(enveloppeId: String) {
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val toutes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val env = toutes.firstOrNull { it.id == enveloppeId && it.estArchive }
                    ?: error("Enveloppe archivée introuvable.")
                require(categoriesMap.containsKey(env.categorieId)) {
                    "La catégorie d'origine est introuvable; restauration refusée."
                }
                require(toutes.none {
                    it.id != env.id && !it.estArchive && it.categorieId == env.categorieId &&
                        it.nom.trim().equals(env.nom.trim(), ignoreCase = true)
                }) { "Le nom existe déjà parmi les enveloppes actives." }
                enveloppeRepository.mettreAJourEnveloppe(env.copy(estArchive = false))
                    .getOrThrow()
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Restauration refusée : ${e.message}") }
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
        val state = _uiState.value
        val enveloppe = state.enveloppePourObjectif ?: return
        val candidate = try {
            ReglesObjectifEnveloppe.appliquer(enveloppe, state.objectifFormState)
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(erreur = e.message ?: "Objectif invalide.") }
            return
        }
        // CE17 : même configuration = aucune écriture et aucune réinitialisation.
        if (candidate == enveloppe) {
            onFermerObjectifDialog()
            return
        }
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                // CE19 : ne pas fermer ni vider les champs sur erreur Room.
                // L'affichage est remplacé seulement après confirmation de l'écriture.
                enveloppeRepository.mettreAJourEnveloppe(candidate).getOrThrow()
                _uiState.update { actuel ->
                    actuel.copy(
                        enveloppesGroupees = actuel.enveloppesGroupees.mapValues { (_, liste) ->
                            liste.map { if (it.id == candidate.id) candidate else it }
                        },
                        isObjectifDialogVisible = false,
                        enveloppePourObjectif = null,
                        objectifFormState = ObjectifFormState(),
                        erreur = null
                    )
                }
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(erreur = "Échec de la sauvegarde de l'objectif : ${e.message}")
                }
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
                isConfirmationSuppressionEnveloppeVisible = true,
                apercuRetrait = null,
                previsualisationRetraitEnCours = true
            )
        }
        viewModelScope.launch {
            try {
                val allocations = enveloppeRepository.recupererAllocationsEnveloppe(enveloppe.id)
                    .getOrThrow()
                val aujourdHui = java.time.YearMonth.now()
                val futures = allocations.count { allocation ->
                    java.time.YearMonth.from(
                        allocation.mois.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                    ).isAfter(aujourdHui)
                }
                val mouvements = transactionRepository?.recupererToutesLesTransactions()
                    ?.getOrThrow()
                val idsAllocations = allocations.map { it.id }.toSet()
                val transactionsLiees = mouvements?.filter { tx ->
                    tx.allocationMensuelleId in idsAllocations ||
                        (tx.estFractionnee && runCatching {
                            com.xburnsx.toutiebudget.ui.historique.PortionsHistorique
                                .lire(tx.sousItems).any { it.enveloppeId == enveloppe.id }
                        }.getOrDefault(false))
                }
                val techniques = transactionsLiees?.count { tx ->
                    tx.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement ||
                        tx.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.PaiementEffectue
                }
                if (_uiState.value.enveloppePourSuppression?.id == enveloppe.id) {
                    _uiState.update {
                        it.copy(
                            previsualisationRetraitEnCours = false,
                            apercuRetrait = ApercuRetraitEnveloppe(
                                nombreAllocations = allocations.size,
                                periodesAvecSolde = allocations.count {
                                    !com.xburnsx.toutiebudget.utils.MoneyFormatter.isAmountZero(it.solde)
                                },
                                allocationsFutures = futures,
                                soldeRestant = allocations.sumOf { it.solde },
                                transactionsLiees = transactionsLiees?.size,
                                paiementsTechniquesLies = techniques
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (_uiState.value.enveloppePourSuppression?.id == enveloppe.id) {
                    _uiState.update {
                        it.copy(
                            previsualisationRetraitEnCours = false,
                            erreur = "Prévisualisation indisponible : ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun onFermerConfirmationSuppressionEnveloppe() {
        _uiState.update {
            it.copy(
                enveloppePourSuppression = null,
                isConfirmationSuppressionEnveloppeVisible = false,
                apercuRetrait = null,
                previsualisationRetraitEnCours = false
            )
        }
    }

    /**
     * Supprime une enveloppe avec mise à jour instantanée.
     */
    fun onConfirmerSuppressionEnveloppe() {
        val enveloppe = _uiState.value.enveloppePourSuppression ?: return

        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                // CE2 : aucun argent ne disparaît. Une enveloppe qui garde un solde (n'importe quel mois) est refusée.
                val allocations = enveloppeRepository.recupererAllocationsEnveloppe(enveloppe.id).getOrThrow()
                val soldeRestant = allocations.sumOf { it.solde }
                if (allocations.any { kotlin.math.round(it.solde * 100.0).toLong() != 0L }) {
                    _uiState.update {
                        it.copy(
                            erreur = "« ${enveloppe.nom} » contient encore ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(soldeRestant)}. " +
                                "Virez cet argent ailleurs avant de la retirer."
                        )
                    }
                    return@lancerUneFois
                }
                // CE5 : une enveloppe de paiement technique doit rester
                // active tant que les opérations liées la référencent.
                val sources = transactionRepository?.recupererToutesLesTransactions()?.getOrThrow()
                if (sources != null) {
                    val allocationsIds = allocations.map { it.id }.toSet()
                    val paiementLie = sources.any { tx ->
                        val technique = tx.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement ||
                            tx.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.PaiementEffectue
                        technique && (
                            tx.allocationMensuelleId in allocationsIds ||
                                (tx.estFractionnee && runCatching {
                                    com.xburnsx.toutiebudget.ui.historique.PortionsHistorique
                                        .lire(tx.sousItems).any { it.enveloppeId == enveloppe.id }
                                }.getOrDefault(false))
                            )
                    }
                    require(!paiementLie) {
                        "Cette enveloppe est liée à un paiement de carte ou de dette. Archivage bloqué."
                    }
                }
                // CE1/CE4 : une absence d'allocations ne prouve pas qu'aucun
                // historique n'existe. L'archivage garde IDs et références.
                // Ne masquer l'enveloppe qu'après confirmation de l'écriture.
                enveloppeRepository.mettreAJourEnveloppe(
                    enveloppe.copy(estArchive = true)
                ).getOrThrow()
                onFermerConfirmationSuppressionEnveloppe()
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()

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

        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val categorieObj = categoriesMap[nomCategorie]
                    ?: categoriesMap.values.singleOrNull { it.nom == nomCategorie }
                    ?: throw Exception("Catégorie '$nomCategorie' introuvable ou ambiguë")

                // CE3 : vide pour de vrai, y compris les enveloppes archivées (elles gardent leur historique)
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                    .filter { it.categorieId == categorieObj.id }
                if (enveloppes.isNotEmpty()) {
                    val archivees = enveloppes.count { it.estArchive }
                    _uiState.update {
                        it.copy(
                            erreur = if (archivees == enveloppes.size) {
                                "Impossible de supprimer « $nomCategorie » : elle contient encore $archivees enveloppe(s) archivée(s)."
                            } else {
                                "Impossible de supprimer une catégorie contenant des enveloppes"
                            }
                        )
                    }
                    return@lancerUneFois
                }

                // CE3/CE4 : ne pas masquer la catégorie avant le succès Room.
                categorieRepository.supprimerCategorie(categorieObj.id).getOrThrow()
                onFermerConfirmationSuppressionCategorie()
                chargerDonnees()
                realtimeSyncService.declencherMiseAJourBudget()

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
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                // CE9/CE11 : les noms ne servent qu'à retrouver l'action UI.
                // Le classement et le contrôle concurrent utilisent les ID.
                val avant = categoriesMap.values.sortedWith(
                    compareBy<Categorie> { it.ordre }.thenBy { it.id }
                )
                val selection = avant.firstOrNull { it.id == nomCategorie }
                    ?: avant.singleOrNull { it.nom == nomCategorie }
                    ?: error("Catégorie ambiguë ou introuvable : $nomCategorie")
                val apres = calculerNouveauxOrdres(avant, selection, nouvellePosition)
                if (avant == apres) return@lancerUneFois
                // Les changements ne deviennent visibles qu'après commit Room.
                categorieRepository.reorganiserCategories(avant, apres).getOrThrow()
                categoriesMap = apres.associateBy { it.id }
                _uiState.update { etat ->
                    etat.copy(
                        enveloppesGroupees = organiserDonneesPourAffichage(apres, enveloppesList),
                        categoriesParId = categoriesMap,
                        categorieEnDeplacement = null,
                        versionUI = etat.versionUI + 1,
                        erreur = null
                    )
                }
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Classement non enregistré : ${e.message}") }
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
        viewModelScope.lancerUneFois(verrouEcriture) {
            try {
                val selection = enveloppesList.firstOrNull { it.id == enveloppeId }
                    ?: error("Enveloppe introuvable.")
                val avant = enveloppesList.filter {
                    it.categorieId == selection.categorieId && !it.estArchive
                }.sortedWith(compareBy<Enveloppe> { it.ordre }.thenBy { it.id })
                val apres = calculerNouveauxOrdresEnveloppes(avant, selection, nouvellePosition)
                if (avant == apres) return@lancerUneFois
                enveloppeRepository.reorganiserEnveloppes(avant, apres).getOrThrow()
                enveloppesList = enveloppesList.map { env ->
                    apres.firstOrNull { it.id == env.id } ?: env
                }
                _uiState.update { etat ->
                    etat.copy(
                        enveloppesGroupees = organiserDonneesPourAffichage(
                            categoriesMap.values.toList(), enveloppesList
                        ),
                        enveloppeEnDeplacement = null,
                        erreur = null
                    )
                }
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Classement non enregistré : ${e.message}") }
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
