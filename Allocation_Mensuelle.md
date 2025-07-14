# 📚 BIBLE - Système d'Allocations Mensuelles ToutieBudget

## 🎯 RÉSUMÉ EXÉCUTIF
Le système d'allocations mensuelles de ToutieBudget fonctionne par **ADDITION AUTOMATIQUE** d'entrées séparées. Chaque transaction crée une **NOUVELLE entrée** dans la table `allocations_mensuelles` qui s'additionne automatiquement avec les entrées existantes.

## 🔍 PRINCIPE FONDAMENTAL

### ❌ CE QU'IL NE FAUT PAS FAIRE
- **JAMAIS modifier une allocation existante** pour ajouter de l'argent
- **JAMAIS utiliser `mettreAJourAllocation()` pour créer**
- **JAMAIS utiliser `getOrCreateAllocationMensuelle()` pour les virements** (bug de parsing)

### ✅ CE QU'IL FAUT FAIRE
- **TOUJOURS créer une NOUVELLE allocation** avec `creerNouvelleAllocation()`
- **Laisser le système additionner automatiquement** les entrées
- **Utiliser le bon format de données** pour PocketBase

## 🏗️ ARCHITECTURE DU SYSTÈME

### Structure de la table `allocations_mensuelles`
```
- id (string) : ID unique généré par PocketBase
- utilisateur_id (string) : ID de l'utilisateur
- enveloppe_id (string) : ID de l'enveloppe concernée
- mois (date) : Premier jour du mois (ex: 2025-07-01 00:00:00)
- solde (double) : Montant de cette entrée (+5, -10, +30, etc.)
- alloue (double) : Montant alloué dans cette entrée
- depense (double) : Montant dépensé dans cette entrée
- compte_source_id (string) : D'où vient l'argent
- collection_compte_source (string) : Type de compte source
```

### Exemple concret
Si une enveloppe a un solde de -30$, c'est la somme de plusieurs entrées :
```
Entrée 1: solde = -5$   (dépense)
Entrée 2: solde = -10$  (dépense)
Entrée 3: solde = -15$  (dépense)
TOTAL = -30$
```

Pour ajouter 5$, on crée une **NOUVELLE entrée** :
```
Entrée 4: solde = +5$   (virement prêt à placer)
NOUVEAU TOTAL = -30$ + 5$ = -25$
```

## 🛠️ SOLUTION TECHNIQUE COMPLÈTE

### 1. Interface Repository
```kotlin
// Dans AllocationMensuelleRepository.kt
suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle): AllocationMensuelle
```

### 2. Implémentation Repository
```kotlin
// Dans AllocationMensuelleRepositoryImpl.kt
override suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle): AllocationMensuelle {
    return creerAllocationMensuelleInterne(allocation)
}
```

### 3. Méthode de création corrigée
La méthode `creerAllocationMensuelleInterne()` a été corrigée pour :

#### A. Envoi des données (format correct)
```kotlin
// ✅ CORRECT : Utiliser un Map avec les noms PocketBase
val donneesAllocation = mapOf(
    "utilisateur_id" to utilisateurId,
    "enveloppe_id" to allocation.enveloppeId,
    "mois" to DATE_FORMAT.format(allocation.mois), // Format correct !
    "solde" to allocation.solde,
    "alloue" to allocation.alloue,
    "depense" to allocation.depense,
    "compte_source_id" to allocation.compteSourceId,
    "collection_compte_source" to allocation.collectionCompteSource
)

// ❌ INCORRECT : Utiliser l'objet directement
// gson.toJson(AllocationMensuelle(...)) // Mauvais format de date !
```

#### B. Réception des données (parsing correct)
```kotlin
// ✅ CORRECT : Parser manuellement pour gérer les dates
val jsonObject = gson.fromJson(corpsReponse, JsonObject::class.java)

// Nettoyer la date (enlever .000Z qui cause l'erreur)
val moisString = jsonObject.get("mois").asString
val dateClean = moisString.replace(".000Z", "")
val dateParsee = DATE_FORMAT.parse(dateClean)

// Créer l'objet manuellement
val allocationCreee = AllocationMensuelle(
    id = jsonObject.get("id").asString,
    // ... autres champs
    mois = dateParsee, // Date correctement parsée !
    // ...
)

// ❌ INCORRECT : Gson direct
// gson.fromJson(corpsReponse, AllocationMensuelle::class.java) // Erreur parsing date !
```

### 4. Use Case de virement
```kotlin
// Dans VirementUseCase.kt
suspend fun effectuerVirementPretAPlacerVersEnveloppe(...) {
    // 1. Diminuer pret_a_placer du compte
    compteRepository.mettreAJourPretAPlacerSeulement(compteId, -montant)
    
    // 2. Créer NOUVELLE allocation (pas modifier existante !)
    val nouvelleAllocation = AllocationMensuelle(
        id = "", // PocketBase génère l'ID
        solde = montant, // +5$ qui s'additionne
        // ... autres champs
    )
    
    // 3. CRÉER (pas mettre à jour !)
    val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
    
    // 4. Transaction de traçabilité
    // ...
}
```

## 🐛 PROBLÈMES RÉSOLUS

### Problème 1 : Format de date incorrect
**Erreur** : `Failed to create record` (400)
**Cause** : Gson sérialisait la date au format `"Jul 1, 2025 12:00:00 AM"`
**Solution** : Utiliser `DATE_FORMAT.format()` → `"2025-07-01 00:00:00"`

### Problème 2 : Parsing de date incorrect
**Erreur** : `Failed parsing '2025-07-01 00:00:00.000Z' as Date`
**Cause** : PocketBase retourne `.000Z` que Gson ne peut pas parser
**Solution** : Nettoyer avec `.replace(".000Z", "")` avant parsing

### Problème 3 : Mauvaise approche
**Erreur** : Aucune nouvelle entrée créée
**Cause** : Tentative de modifier allocation existante au lieu de créer
**Solution** : Toujours créer une NOUVELLE allocation

## 🎯 RÈGLES D'OR

1. **UNE TRANSACTION = UNE NOUVELLE ALLOCATION**
2. **JAMAIS modifier, TOUJOURS créer**
3. **Utiliser le format de date PocketBase** (`DATE_FORMAT`)
4. **Parser manuellement les réponses** pour gérer `.000Z`
5. **Laisser le système additionner automatiquement**

## 🔧 OUTILS DE DEBUG

### Logs essentiels
```kotlin
println("[DEBUG] 🚀 CRÉATION d'une NOUVELLE allocation de +$montant")
println("[DEBUG] 📝 Appel creerNouvelleAllocation()...")
println("[DEBUG] ✅ SUCCÈS! Nouvelle allocation créée avec ID: ${allocationCreee.id}")
```

### Vérification PocketBase
- Aller dans la collection `allocations_mensuelles`
- Vérifier qu'une nouvelle entrée est créée (pas modifiée)
- Vérifier que le solde total s'additionne correctement

## 🚀 RÉSULTAT FINAL

Quand tout fonctionne :
1. ✅ **Prêt à placer diminue** (ex: 465$ → 460$)
2. ✅ **Nouvelle entrée créée** dans `allocations_mensuelles`
3. ✅ **Solde enveloppe mis à jour** (ex: -30$ → -25$)
4. ✅ **Transaction de traçabilité créée**

---

**📝 Note importante** : Cette bible documente la solution qui FONCTIONNE. Ne pas dévier de cette approche sans comprendre parfaitement pourquoi chaque étape est nécessaire !
