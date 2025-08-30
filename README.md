# Gestion de la Base de Données Locale - ToutieBudget

## 🎯 Vue d'ensemble

Cette fonctionnalité permet aux utilisateurs de gérer directement leur base de données locale Room depuis l'application, offrant une interface similaire à PocketBase pour effectuer des opérations CRUD (Create, Read, Update, Delete) sur toutes les entités de données.

## 🚀 Comment y accéder

1. Ouvrir l'application ToutieBudget
2. Aller dans **Paramètres** (Settings)
3. Cliquer sur **"Gestion de la base de données"** dans la section "Base de données"
4. L'interface de gestion s'ouvre avec 7 onglets organisés par type d'entité

## ✨ Fonctionnalités disponibles

### 1. **Onglet Comptes** 
- **Comptes Chèques** : Gestion complète (ajout, modification, suppression)
- **Comptes Crédit** : Gestion des limites de crédit et taux d'intérêt
- **Comptes Dette** : Suivi des montants initiaux et paiements
- **Comptes Investissement** : Gestion des placements et soldes

### 2. **Onglet Transactions**
- Ajout, modification et suppression des transactions
- Gestion des notes, types, montants et dates
- Association avec les comptes utilisateurs

### 3. **Onglet Allocations Mensuelles** ⭐ NOUVEAU
- Gestion complète des allocations mensuelles
- Suivi des montants alloués, dépensés et soldes
- Association avec les enveloppes et comptes sources

### 4. **Onglet Catégories**
- Interface préparée pour la gestion des catégories de dépenses
- *Fonctionnalités à implémenter*

### 5. **Onglet Enveloppes**
- Interface préparée pour la gestion des enveloppes budgétaires
- *Fonctionnalités à implémenter*

### 6. **Onglet Tiers**
- Interface préparée pour la gestion des tiers (personnes/organisations)
- *Fonctionnalités à implémenter*

### 7. **Onglet Prêts**
- Interface préparée pour la gestion des prêts personnels
- *Fonctionnalités à implémenter*

## 🔧 Corrections récentes apportées

### ✅ Problèmes résolus
1. **ID utilisateur incorrect** : Remplacement de l'utilisateurId vide `""` par le vrai ID de l'utilisateur connecté via `PocketBaseClient.obtenirUtilisateurConnecte()?.id`
2. **Champs d'entités incorrects** : Correction des noms de champs pour correspondre aux entités Room :
   - `CompteCredit` : `limite` → `limiteCredit`, `solde` → `soldeUtilise`
   - `CompteDette` : `montant` → `montantInitial`
   - `CompteInvestissement` : `valeur` → `solde`, suppression de `typeInvestissement`
   - `Transaction` : `description` → `note`, ajout du champ `type`
3. **Méthodes DAO inexistantes** : Remplacement de `getAllComptes()` et `getAllTransactions()` par les vraies méthodes disponibles
4. **Types d'ID incorrects** : Correction des types d'ID de `Int` vers `String` pour correspondre aux entités Room
5. **Champs manquants** : Ajout de tous les champs requis lors de la création d'entités

### 🆕 Nouvelles fonctionnalités
- **Onglet Allocations Mensuelles** entièrement fonctionnel avec CRUD complet
- Gestion des utilisateurs connectés pour toutes les opérations
- Validation des données utilisateur avant chaque opération

## 🎨 Design et UX

- **Interface Material 3** cohérente avec le thème de l'application
- **Navigation par onglets** intuitive et organisée
- **Sections expansibles** pour une meilleure organisation visuelle
- **Dialogs d'édition** avec validation des champs
- **Feedback utilisateur** via Snackbars pour toutes les opérations
- **Couleurs cohérentes** avec la palette ToutieBudget

## 📱 Utilisation

### Ajouter un élément
1. Cliquer sur le bouton **"+"** dans la section correspondante
2. Remplir les champs requis
3. Cliquer sur **"Enregistrer"**

### Modifier un élément
1. Cliquer sur l'icône **"Modifier"** (crayon) sur l'élément
2. Modifier les champs souhaités
3. Cliquer sur **"Enregistrer"**

### Supprimer un élément
1. Cliquer sur l'icône **"Supprimer"** (poubelle) sur l'élément
2. Confirmer la suppression dans le dialog

## 🔒 Sécurité et validation

- **Authentification utilisateur** requise pour toutes les opérations
- **Validation des données** avant insertion/modification
- **Gestion des erreurs** avec messages informatifs
- **Isolation des données** par utilisateur connecté

## 🏗️ Architecture technique

### Composants principaux
- `DatabaseManagerScreen` : Écran principal avec navigation par onglets
- `ComptesTab` : Gestion des différents types de comptes
- `TransactionsTab` : Gestion des transactions
- `AllocationsTab` : Gestion des allocations mensuelles
- `CategoriesTab`, `EnveloppesTab`, `TiersTab`, `PretsTab` : Interfaces préparées

### Technologies utilisées
- **Jetpack Compose** pour l'interface utilisateur
- **Room Database** pour la persistance locale
- **Coroutines** pour les opérations asynchrones
- **Material 3** pour le design
- **Navigation Component** pour la navigation

### Intégration
- **PocketBase Client** pour l'authentification utilisateur
- **Base de données Room** existante de l'application
- **Système de navigation** existant

## 🚧 Fonctionnalités à implémenter

### Priorité haute
- [ ] Implémentation complète de **Catégories** (CRUD)
- [ ] Implémentation complète d'**Enveloppes** (CRUD)
- [ ] Implémentation complète de **Tiers** (CRUD)
- [ ] Implémentation complète de **Prêts** (CRUD)

### Priorité moyenne
- [ ] Recherche et filtrage des données
- [ ] Export/Import des données
- [ ] Sauvegarde automatique
- [ ] Historique des modifications

### Priorité basse
- [ ] Validation avancée des données
- [ ] Gestion des relations entre entités
- [ ] Drag & Drop pour réorganiser
- [ ] Opérations par lot
- [ ] Statistiques des données
- [ ] Synchronisation avec PocketBase

## 📋 Prérequis

- Utilisateur connecté à l'application
- Base de données Room initialisée
- Permissions d'accès aux données locales

## 🐛 Dépannage

### Problèmes courants
1. **"Utilisateur non connecté"** : Se reconnecter à l'application
2. **"Erreur lors du chargement"** : Vérifier la connexion à la base de données
3. **Champs manquants** : Tous les champs requis doivent être remplis

### Logs et débogage
- Les erreurs sont affichées dans des Snackbars
- Vérifier les logs Android pour plus de détails
- Utiliser le débogueur pour inspecter les données

## 🔄 Versions

- **Version actuelle** : 1.0.0
- **Dernière mise à jour** : Janvier 2025
- **Compatibilité** : Android API 24+ (Android 7.0+)

## 📞 Support

Pour toute question ou problème :
1. Vérifier la documentation de l'API Room
2. Consulter les logs d'erreur
3. Tester avec des données simples
4. Contacter l'équipe de développement

---

*Cette fonctionnalité transforme ToutieBudget en un véritable outil de gestion de base de données locale, offrant aux utilisateurs un contrôle total sur leurs données financières.*
