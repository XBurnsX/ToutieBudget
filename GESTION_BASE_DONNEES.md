# Gestion de la Base de Données Locale

## Vue d'ensemble

Une nouvelle page de gestion de base de données a été ajoutée dans les paramètres de l'application ToutieBudget. Cette page permet de gérer directement la base de données locale Room, similaire à un panel d'administration PocketBase.

## Accès

1. Ouvrir l'application ToutieBudget
2. Aller dans les **Paramètres** (icône engrenage)
3. Scroller jusqu'à la section **Base de données**
4. Cliquer sur **"Gestion de la base de données"**

## Fonctionnalités

### Onglets disponibles

La page est organisée en 6 onglets principaux :

1. **Comptes** - Gestion des différents types de comptes
2. **Transactions** - Gestion des transactions
3. **Catégories** - Gestion des catégories de dépenses
4. **Enveloppes** - Gestion des enveloppes budgétaires
5. **Tiers** - Gestion des tiers (personnes/entités)
6. **Prêts** - Gestion des prêts personnels

### Gestion des Comptes

#### Types de comptes supportés

- **Comptes Chèques** : Comptes bancaires classiques
- **Comptes Crédit** : Cartes de crédit avec limites
- **Comptes Dette** : Prêts et dettes
- **Comptes Investissement** : Placements et investissements

#### Opérations disponibles

- ✅ **Ajouter** un nouveau compte
- ✏️ **Modifier** un compte existant
- 🗑️ **Supprimer** un compte
- 📊 **Voir** la liste de tous les comptes

#### Champs gérés

**Comptes Chèques :**
- Nom du compte
- Solde
- Devise (par défaut EUR)
- Couleur
- Ordre d'affichage

**Comptes Crédit :**
- Nom du compte
- Limite de crédit
- Solde utilisé
- Taux d'intérêt
- Couleur
- Ordre d'affichage

**Comptes Dette :**
- Nom du compte
- Montant initial
- Solde de la dette
- Taux d'intérêt
- Paiement minimum
- Durée du prêt

**Comptes Investissement :**
- Nom du compte
- Solde
- Couleur
- Ordre d'affichage

### Gestion des Transactions

#### Opérations disponibles

- ✅ **Ajouter** une nouvelle transaction
- ✏️ **Modifier** une transaction existante
- 🗑️ **Supprimer** une transaction
- 📊 **Voir** la liste de toutes les transactions

#### Champs gérés

- Note/Description
- Type de transaction
- Montant
- Date
- Compte associé
- Collection du compte

### Interface utilisateur

#### Design Material 3

- **Thème sombre** cohérent avec l'application
- **Animations** fluides pour l'ouverture/fermeture des sections
- **Cartes** organisées par type d'entité
- **Boutons d'action** clairement identifiés

#### Navigation intuitive

- **Onglets** pour naviguer entre les différents types d'entités
- **Sections pliables** pour organiser l'information
- **Boutons d'action** contextuels (Ajouter, Modifier, Supprimer)
- **Dialogs** pour l'édition et la confirmation

## Utilisation

### Ajouter un élément

1. Cliquer sur le bouton **"+"** dans la section correspondante
2. Remplir les champs requis
3. Cliquer sur **"Enregistrer"**

### Modifier un élément

1. Cliquer sur l'icône **✏️** (crayon) de l'élément
2. Modifier les champs souhaités
3. Cliquer sur **"Enregistrer"**

### Supprimer un élément

1. Cliquer sur l'icône **🗑️** (poubelle) de l'élément
2. Confirmer la suppression dans le dialog
3. Cliquer sur **"Supprimer"**

## Sécurité

- **Confirmation requise** pour toutes les suppressions
- **Validation des données** avant sauvegarde
- **Gestion des erreurs** avec messages informatifs
- **Transactions sécurisées** avec la base de données

## Architecture technique

### Composants

- `DatabaseManagerScreen` : Écran principal de gestion
- `ComptesTab` : Gestion des comptes
- `TransactionsTab` : Gestion des transactions
- `CategoriesTab` : Gestion des catégories (à implémenter)
- `EnveloppesTab` : Gestion des enveloppes (à implémenter)
- `TiersTab` : Gestion des tiers (à implémenter)
- `PretsTab` : Gestion des prêts (à implémenter)

### Intégration

- **Navigation** : Intégrée dans le système de navigation existant
- **Base de données** : Utilise les DAOs Room existants
- **Thème** : Cohérent avec le système de thème de l'application
- **État** : Géré avec Compose State et LaunchedEffect

### Méthodes utilisées

- `getComptesByUtilisateur()` : Récupération des comptes
- `getTransactionsByUtilisateur()` : Récupération des transactions
- `insertCompte()` / `insertTransaction()` : Ajout d'éléments
- `updateCompte()` / `updateTransaction()` : Modification d'éléments
- `deleteCompteById()` / `deleteTransactionById()` : Suppression d'éléments

## Développement futur

### Fonctionnalités à implémenter

- [ ] **Gestion complète des catégories**
- [ ] **Gestion complète des enveloppes**
- [ ] **Gestion complète des tiers**
- [ ] **Gestion complète des prêts**
- [ ] **Recherche et filtrage**
- [ ] **Export/Import de données**
- [ ] **Sauvegarde automatique**
- [ ] **Historique des modifications**

### Améliorations possibles

- **Validation avancée** des données
- **Gestion des relations** entre entités
- **Interface drag & drop** pour réorganiser
- **Mode batch** pour les opérations multiples
- **Statistiques** sur les données
- **Synchronisation** avec PocketBase

## Support

Pour toute question ou problème avec cette fonctionnalité :

1. Vérifier que l'application est à jour
2. Consulter les logs de l'application
3. Redémarrer l'application si nécessaire
4. Contacter l'équipe de développement

---

**Version** : 1.0  
**Date** : 2024  
**Compatibilité** : Android 6.0+ (API 23+)
