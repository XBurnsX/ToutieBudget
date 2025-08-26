# Plan pour Room-First avec Pocketbase Backup

## 🎯 OBJECTIF FINAL CLAIR

**Transformer l'application de Pocketbase-first vers Room-first avec synchronisation automatique**

### Architecture cible (ce qu'on veut obtenir)
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  ViewModel      │    │  Repository     │
│   (Compose)     │◄──►│                 │◄──►│                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
                       ┌─────────────────────────────────────────┐
                       │           Data Layer                    │
                       │  ┌─────────────┐  ┌─────────────────┐  │
                       │  │   Room      │  │   Pocketbase    │  │
                       │  │  (Local)    │◄─►│   (Remote)      │  │
                       │  │  PRIMARY    │  │   BACKUP        │  │
                       │  │  ┌─────────┐│  └─────────────────┘  │
                       │  │  │SyncJob  ││                       │
                       │  │  │Table    ││                       │
                       │  │  └─────────┘│                       │
                       │  └─────────────┘                       │
                       └─────────────────────────────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   Worker        │
                       │  (Sync Auto)    │
                       └─────────────────┘
```

---

## 🏪 LES 4 PERSONNAGES CLÉS DU SYSTÈME

Il y a quatre pièces maîtresses qui font tout fonctionner en arrière-plan, sans que le client (le ViewModel) s'en rende compte.

### 1. La Réserve (La base de données Room)
**Son rôle** : C'est le frigo et le garde-manger de votre restaurant. Il est situé directement dans la cuisine (sur le téléphone de l'utilisateur).

**Son avantage** : Il est ultra-rapide. Quand le cuisinier (ViewModel) a besoin d'un légume (une transaction) ou veut en ranger un nouveau, il l'attrape ou le dépose dans le frigo. L'action est instantanée. C'est pour ça que l'application ne gèle jamais, même sans internet.

**La règle d'or** : L'application, pour afficher quelque chose, regarde TOUJOURS et UNIQUEMENT dans la Réserve.

### 2. La Liste de Tâches (La table SyncJob)
**Son rôle** : C'est un calepin à côté du frigo. Chaque fois que le Gérant d'Entrepôt fait une action (ajoute une transaction, supprime un compte), il l'écrit sur ce calepin. Par exemple : "À ENVOYER : Nouvelle transaction #123", "À SUPPRIMER : Compte #456".

**Son avantage** : On n'oublie jamais rien. Même si l'utilisateur ferme l'application ou perd sa connexion, la liste de tâches est sauvegardée en sécurité dans la Réserve.

### 3. Le Gérant d'Entrepôt (Les Repositories)
**Son rôle** : C'est le seul qui a le droit de toucher à la Réserve et à la Liste de Tâches. C'est le cerveau des opérations de données.

**Comment il travaille** : Quand le cuisinier (ViewModel) lui dit "ajoute-moi cette nouvelle transaction", le Gérant fait deux choses immédiatement :

- Il met la nouvelle transaction dans la Réserve (le frigo).
- Il écrit sur la Liste de Tâches : "Tâche #1 : Créer cette transaction sur le serveur".

**Le secret** : Pour le cuisinier (ViewModel), le travail est déjà fini, car la transaction est dans la Réserve et visible dans l'app. Il ne sait même pas qu'une note a été prise pour le serveur.

### 4. L'Ouvrier de Nuit (Le WorkManager)
**Son rôle** : C'est un employé qui travaille en arrière-plan, même quand le restaurant est fermé (l'application est fermée). Son seul travail est de s'occuper de la Liste de Tâches.

**Ses conditions de travail** : Il ne peut travailler que s'il y a une route ouverte vers le Supermarché (quand il y a du réseau internet).

**Comment il travaille** :

- Il se réveille périodiquement ou quand on l'appelle.
- Il regarde la Liste de Tâches. S'il n'y a rien, il se rendort.
- S'il voit "Tâche #1 : Créer transaction #123", il prend les détails et les envoie au Supermarché (PocketBase).
- Si le Supermarché répond "OK, bien reçu !", l'Ouvrier rature la tâche de sa liste.
- Si le Supermarché ne répond pas (pas de réseau), l'Ouvrier laisse la tâche sur la liste et se dit "je réessaierai plus tard".

---

## 🔍 ANALYSE APPROFONDIE DU PROJET ACTUEL

### Architecture actuelle découverte

**IMPORTANT : Ce projet utilise déjà Pocketbase !** Il n'y a PAS de Room actuellement. L'architecture est :

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  ViewModel      │    │  Repository     │
│   (Compose)     │◄──►│                 │◄──►│                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
                       ┌─────────────────────────────────────────┐
                       │           Data Layer                    │
                       │  ┌─────────────────────────────────┐  │
                       │  │   PocketBase (Remote)           │  │
                       │  │   + OkHttp3 Client              │  │
                       │  │   + Gson Serialization          │  │
                       │  └─────────────────────────────────┘  │
                       └─────────────────────────────────────────┘
```

### Modèles de données existants

#### 1. **Compte** (Interface sealed + 4 implémentations)
- `CompteCheque` : Comptes bancaires avec `pret_a_placer`
- `CompteCredit` : Cartes de crédit avec frais mensuels JSON
- `CompteDette` : Dettes avec taux d'intérêt
- `CompteInvestissement` : Comptes d'investissement

**Champs clés** : `id`, `utilisateur_id`, `nom`, `solde`, `couleur`, `archive`, `ordre`, `collection`

#### 2. **Transaction**
- Support des types : Dépense, Revenu, Prêt, Emprunt, Transfert, etc.
- Champs : `montant`, `date`, `compteId`, `allocationMensuelleId`, `estFractionnee`
- **IMPORTANT** : Pas de cache local, tout va directement vers Pocketbase

#### 3. **Autres modèles**
- `Categorie`, `Enveloppe`, `Tiers`, `PretPersonnel`, `AllocationMensuelle`

### Repositories existants (TOUS utilisent Pocketbase)

#### **CompteRepositoryImpl**
- ✅ `recupererTousLesComptes()` : Appels parallèles vers 4 collections
- ✅ `creerCompte()` : POST vers Pocketbase
- ✅ `mettreAJourCompte()` : PATCH vers Pocketbase
- ✅ `supprimerCompte()` : DELETE vers Pocketbase
- ✅ Gestion des collections : `comptes_cheques`, `comptes_credits`, `comptes_dettes`, `comptes_investissement`

#### **TransactionRepositoryImpl**
- ✅ `creerTransaction()` : POST vers Pocketbase
- ✅ `recupererToutesLesTransactions()` : GET depuis Pocketbase
- ✅ Gestion des filtres par période, compte, allocation

### Configuration Pocketbase actuelle

#### **PocketBaseClient**
- ✅ Authentification Google OAuth2
- ✅ Gestion des tokens JWT
- ✅ Résolution d'URL (local vs externe)
- ✅ Timeouts optimisés (3s connect, 8s read, 5s write)
- ✅ Support HTTP/2 + compression gzip

#### **Collections Pocketbase identifiées**
- `comptes_cheques`
- `comptes_credits` 
- `comptes_dettes`
- `comptes_investissement`
- `transactions`
- `categories`
- `enveloppes`
- `tiers`
- `pret_personnel`
- `allocation_mensuelle`

### Dépendances actuelles
- ✅ **OkHttp3** : Client HTTP pour Pocketbase
- ✅ **Gson** : Sérialisation JSON
- ✅ **Coroutines** : Gestion asynchrone
- ✅ **Compose** : UI moderne
- ❌ **Room** : PAS présent
- ❌ **SQLite** : PAS présent

### Gestion des données actuelles

#### **Création d'un compte**
1. UI → ViewModel → Repository
2. Repository → Sérialisation Gson
3. POST vers Pocketbase avec token JWT
4. Mise à jour de l'UI via événements

#### **Création d'une transaction**
1. UI → ViewModel → Repository
2. Repository → POST vers Pocketbase
3. Mise à jour du solde du compte
4. Déclenchement des événements de rafraîchissement

#### **Synchronisation**
- ✅ Temps réel via `RealtimeSyncService`
- ✅ Événements déclenchés sur modifications
- ✅ Gestion des erreurs réseau

---

## 🚨 CONTRAINTES ABSOLUES - À RESPECTER

### 1. **AUCUNE MODIFICATION DES VIEWMODELS ET SCREENS**
- ❌ **INTERDIT** de toucher aux ViewModels existants
- ❌ **INTERDIT** de modifier les écrans/UI
- ✅ **OBLIGATOIRE** : Tout doit fonctionner exactement comme avant
- ✅ **OBLIGATOIRE** : Les repositories doivent garder la même interface

### 2. **Noms Room et Pocketbase IDENTIQUES à 100%**
- ✅ **OBLIGATOIRE** : Mêmes noms de champs dans Room et Pocketbase
- ✅ **OBLIGATOIRE** : Mêmes noms de tables/collections
- ✅ **OBLIGATOIRE** : Même structure de données
- ❌ **INTERDIT** : Aucune différence de nommage

### 3. **Architecture Room-First avec Worker de Sync**
- ✅ **OBLIGATOIRE** : Room = source de vérité principale
- ✅ **OBLIGATOIRE** : Worker en arrière-plan pour la synchronisation
- ✅ **OBLIGATOIRE** : Sync même quand l'app est fermée
- ✅ **OBLIGATOIRE** : Mode offline complet

---

## 📋 PLAN D'ACTION DÉTAILLÉ ET NUMÉROTÉ

### ÉTAPE 1 : Ajout de Room (Nouveau) - [🔄 EN COURS]

#### 1.1 Ajouter les dépendances Room dans build.gradle
- [ ] Ajouter `room-runtime` dans dependencies
- [ ] Ajouter `room-compiler` dans kapt
- [ ] Ajouter `room-ktx` pour support Coroutines
- [ ] **STATUT** : ⏳ À FAIRE

#### 1.2 Créer la base de données Room
- [ ] Créer la classe `ToutieBudgetDatabase`
- [ ] Configurer les entités et DAOs
- [ ] Ajouter la configuration dans le module DI
- [ ] **STATUT** : ⏳ À FAIRE

#### 1.3 Créer l'entité SyncJob (Liste de Tâches)
- [ ] Créer la table `sync_jobs` avec champs : id, type, action, data_json, created_at, status
- [ ] Créer le DAO `SyncJobDao` avec méthodes CRUD
- [ ] **STATUT** : ⏳ À FAIRE

#### 1.4 Créer les entités Room avec noms IDENTIQUES à Pocketbase
- [ ] Créer l'entité `CompteCheque` (même structure que le modèle existant)
- [ ] Créer l'entité `CompteCredit` (même structure que le modèle existant)
- [ ] Créer l'entité `CompteDette` (même structure que le modèle existant)
- [ ] Créer l'entité `CompteInvestissement` (même structure que le modèle existant)
- [ ] Créer l'entité `Transaction` (même structure que le modèle existant)
- [ ] Créer l'entité `Categorie` (même structure que le modèle existant)
- [ ] Créer l'entité `Enveloppe` (même structure que le modèle existant)
- [ ] Créer l'entité `Tiers` (même structure que le modèle existant)
- [ ] Créer l'entité `PretPersonnel` (même structure que le modèle existant)
- [ ] Créer l'entité `AllocationMensuelle` (même structure que le modèle existant)
- [ ] **STATUT** : ⏳ À FAIRE

#### 1.5 Créer les DAOs pour chaque entité
- [ ] Créer `CompteDao` avec méthodes CRUD
- [ ] Créer `TransactionDao` avec méthodes CRUD
- [ ] Créer `CategorieDao` avec méthodes CRUD
- [ ] Créer `EnveloppeDao` avec méthodes CRUD
- [ ] Créer `TiersDao` avec méthodes CRUD
- [ ] Créer `PretPersonnelDao` avec méthodes CRUD
- [ ] Créer `AllocationMensuelleDao` avec méthodes CRUD
- [ ] **STATUT** : ⏳ À FAIRE

#### 1.6 Tests de validation des noms
- [ ] Vérifier que tous les noms de champs correspondent exactement
- [ ] Vérifier que toutes les tables ont les bons noms
- [ ] Tester la compilation et la création de la base
- [ ] **STATUT** : ⏳ À FAIRE

---

### ÉTAPE 2 : Refactorisation des repositories (INTERFACE IDENTIQUE) - [⏳ EN ATTENTE]

#### 2.1 Créer les nouveaux repositories Room-first
- [ ] Créer `CompteRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `TransactionRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `CategorieRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `EnveloppeRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `TiersRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `PretPersonnelRepositoryRoomImpl` qui utilise Room d'abord
- [ ] Créer `AllocationMensuelleRepositoryRoomImpl` qui utilise Room d'abord
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 2.2 Implémenter la logique Room-first dans chaque repository
- [ ] Modifier `CompteRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `TransactionRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `CategorieRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `EnveloppeRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `TiersRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `PretPersonnelRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] Modifier `AllocationMensuelleRepositoryRoomImpl` pour utiliser Room + ajouter à SyncJob
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 2.3 Mettre à jour l'injection de dépendances
- [ ] Modifier `AppModule` pour utiliser les nouveaux repositories Room
- [ ] Tester que l'interface reste identique
- [ ] **STATUT** : ⏳ EN ATTENTE

---

### ÉTAPE 3 : Worker de synchronisation - [⏳ EN ATTENTE]

#### 3.1 Créer le WorkManager de synchronisation
- [ ] Créer la classe `SyncWorker` qui hérite de `CoroutineWorker`
- [ ] Implémenter la logique de lecture de la table `SyncJob`
- [ ] Implémenter la logique d'envoi vers Pocketbase
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 3.2 Configurer la planification du Worker
- [ ] Configurer le WorkManager pour s'exécuter en arrière-plan
- [ ] Configurer les contraintes réseau
- [ ] Configurer la réexécution en cas d'échec
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 3.3 Gérer les différents types de tâches
- [ ] Implémenter la création (CREATE) vers Pocketbase
- [ ] Implémenter la mise à jour (UPDATE) vers Pocketbase
- [ ] Implémenter la suppression (DELETE) vers Pocketbase
- [ ] **STATUT** : ⏳ EN ATTENTE

---

### ÉTAPE 4 : Import initial des données - [⏳ EN ATTENTE]

#### 4.1 Créer le service d'import initial
- [ ] Créer `InitialImportService` pour récupérer toutes les données depuis Pocketbase
- [ ] Implémenter l'import des comptes (4 collections)
- [ ] Implémenter l'import des transactions
- [ ] Implémenter l'import des catégories
- [ ] Implémenter l'import des enveloppes
- [ ] Implémenter l'import des tiers
- [ ] Implémenter l'import des prêts personnels
- [ ] Implémenter l'import des allocations mensuelles
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 4.2 Intégrer l'import dans la page startup
- [ ] Modifier la page startup pour appeler `InitialImportService`
- [ ] Synchroniser la barre de progression avec les vraies données
- [ ] Gérer les erreurs d'import
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 4.3 Gestion de la première connexion
- [ ] Déclencher l'import APRÈS la connexion Google
- [ ] Récupérer l'ID utilisateur Pocketbase
- [ ] Basculer en mode Room-first une fois l'import terminé
- [ ] **STATUT** : ⏳ EN ATTENTE

---

### ÉTAPE 5 : Tests et optimisation - [⏳ EN ATTENTE]

#### 5.1 Tests de performance
- [ ] Tester la vitesse de Room vs Pocketbase
- [ ] Tester la synchronisation en arrière-plan
- [ ] Tester le mode offline complet
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 5.2 Tests de synchronisation
- [ ] Tester la création offline puis sync
- [ ] Tester la modification offline puis sync
- [ ] Tester la suppression offline puis sync
- [ ] **STATUT** : ⏳ EN ATTENTE

#### 5.3 Tests offline complets
- [ ] Tester l'application sans internet
- [ ] Tester la reprise après reconnexion
- [ ] Vérifier que tout fonctionne comme avant
- [ ] **STATUT** : ⏳ EN ATTENTE

---

## 🔧 PROCHAINES ÉTAPES IMMÉDIATES

1. **Commencer par l'ÉTAPE 1.1** : Ajouter les dépendances Room dans build.gradle
2. **Vérifier que tout compile** avant de continuer
3. **Tester chaque sous-étape** avant de passer à la suivante

---

## ❓ QUESTIONS CRITIQUES - RÉPONSES OBTENUES

### 1. **WorkManager** : Quelle fréquence de synchronisation voulez-vous ?
**RÉPONSE** : On sync dès qu'il y a des changements MAIS si on est en offline, ça doit stocker les modifications (création, modification et suppression) dans une liste d'épicerie. Quand internet revient, le worker lit la liste et effectue les modifications.

### 2. **Import initial** : Voulez-vous un indicateur de progression pendant l'import ?
**RÉPONSE** : J'ai une page startup avec "initialisation en cours". L'import initial se fait durant ce chargement. Organise-toi juste que la barre se synchronise avec les données (pas que la barre finisse mais que les données ne soient pas encore finies d'importer).

### 3. **Gestion des conflits** : Quelle stratégie en cas de conflit ?
**RÉPONSE** : Théoriquement si tu suis mon exemple de liste de tâches, il n'y aura jamais de conflit.

### 4. **Taille des données** : Y a-t-il des limites de taille à considérer pour Room ?
**RÉPONSE** : Non, toutes les transactions s'affichent. On optimisera si nécessaire une fois tout fonctionnel.

### 5. **Première utilisation** : Quand l'import initial doit-il se faire ?
**RÉPONSE** : Tu importes APRÈS la connexion Google parce que tu importes durant mon "initialisation en cours" qui se passe après... et que tu as besoin de l'ID Pocketbase pour importer les données du compte.

---

## 📝 RÉSUMÉ EXÉCUTIF

**AVANT** : L'app fait des appels HTTP directs vers Pocketbase à chaque action
**APRÈS** : L'app utilise Room en local + sync automatique vers Pocketbase en arrière-plan

**RÉSULTAT** : 
- ✅ Mode offline complet
- ✅ Performance améliorée (pas d'attente réseau)
- ✅ Synchronisation automatique
- ✅ Aucun changement visible pour l'utilisateur
- ✅ Aucune modification des ViewModels/UI

---

*Document créé le : [Date actuelle]*
*Version : 4.0 - Plan détaillé et numéroté avec suivi de progression*
