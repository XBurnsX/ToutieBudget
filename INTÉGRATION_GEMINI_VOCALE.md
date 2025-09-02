# 🎤 Intégration Vocale Gemini - ToutieBudget

## 📋 Vue d'ensemble
Intégration de Google Gemini pour permettre la déclaration vocale des dépenses dans l'application ToutieBudget.

## 🎯 Objectifs
- Permettre de déclarer des dépenses par la voix
- Utiliser Gemini pour analyser le texte vocal
- Système de clarification pour éviter les ambiguïtés
- Intégration transparente avec l'app existante

## 🏗️ Architecture

### Composants principaux
1. **GeminiService** - Interface avec l'API Gemini
2. **VoiceExpenseHandler** - Gestion des commandes vocales
3. **ClarificationEngine** - Moteur de questions/réponses
4. **VoiceDataProcessor** - Traitement et validation des données

### Flux de données
```
Voix → Gemini API → Analyse → Clarification → Validation → Base de données
```

## 📱 Étapes d'implémentation

### Phase 1: Setup Gemini API
- [ ] Créer le compte développeur Gemini
- [ ] Obtenir les clés API
- [ ] Configurer les permissions
- [ ] Créer GeminiService.kt
- [ ] Tester la connexion API

### Phase 2: Analyse vocale
- [ ] Créer VoiceExpenseHandler.kt
- [ ] Implémenter l'analyse de texte avec Gemini
- [ ] Extraction des montants, lieux, catégories
- [ ] Tests d'extraction de données

### Phase 3: Moteur de clarification
- [ ] Créer ClarificationEngine.kt
- [ ] Logique de questions/réponses
- [ ] Gestion des comptes multiples
- [ ] Gestion des lieux multiples
- [ ] Tests de clarification

### Phase 4: Intégration base de données
- [ ] Créer VoiceDataProcessor.kt
- [ ] Connexion avec la base existante
- [ ] Sauvegarde des transactions
- [ ] Gestion des erreurs
- [ ] Tests d'intégration

### Phase 5: Interface utilisateur
- [ ] Ajouter les permissions microphone
- [ ] Bouton d'activation vocale
- [ ] Indicateur d'écoute
- [ ] Affichage des clarifications
- [ ] Tests UI

### Phase 6: Tests et optimisations
- [ ] Tests sur différents appareils
- [ ] Tests de reconnaissance vocale
- [ ] Optimisation des réponses
- [ ] Gestion des cas d'erreur
- [ ] Tests de performance

## 🔧 Configuration requise

### Dépendances
```kotlin
// Gemini API
implementation("com.google.ai.client.generativeai:generativeai:0.1.1")

// Permissions
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Variables d'environnement
```kotlin
// Dans local.properties
GEMINI_API_KEY=votre_clé_api_ici
```

## 📝 Exemples d'utilisation

### Commandes vocales typiques
```
"J'ai dépensé 25$ au Shell"
"25$ en essence au Shell Port-Cartier"
"Ajoute une dépense de 15$ pour le lunch"
```

### Flux de clarification
```
Utilisateur: "J'ai dépensé 25$ au Shell"
Gemini: "Quel compte voulez-vous utiliser ? Wealthsimple, Tangerine, ou autre ?"
Utilisateur: "Wealthsimple"
Gemini: "Quel Shell ? Port-Cartier, Sept-Îles, ou autre ?"
Utilisateur: "Port-Cartier"
Gemini: "Catégorie ? Essence, Dépanneur, ou autre ?"
Utilisateur: "Essence"
Gemini: "25$ au Shell Port-Cartier, compte Wealthsimple, catégorie Essence. Confirmer ?"
Utilisateur: "Oui"
Gemini: "Dépense enregistrée dans ToutieBudget !"
```

## 🧪 Tests

### Tests unitaires
- [ ] GeminiService
- [ ] VoiceExpenseHandler
- [ ] ClarificationEngine
- [ ] VoiceDataProcessor

### Tests d'intégration
- [ ] Flux complet de déclaration vocale
- [ ] Gestion des erreurs API
- [ ] Performance de reconnaissance
- [ ] Précision des extractions

### Tests utilisateur
- [ ] Reconnaissance sur différents accents
- [ ] Tests en environnement bruyant
- [ ] Tests sur différents appareils
- [ ] Validation des clarifications

## 🚀 Déploiement

### Version alpha
- [ ] Tests internes complets
- [ ] Validation des fonctionnalités
- [ ] Documentation utilisateur

### Version beta
- [ ] Tests avec utilisateurs réels
- [ ] Collecte de feedback
- [ ] Optimisations basées sur l'usage

### Version finale
- [ ] Intégration dans l'app principale
- [ ] Documentation complète
- [ ] Support utilisateur

## 📊 Métriques de succès

### Performance
- Précision de reconnaissance vocale > 95%
- Temps de réponse < 2 secondes
- Taux de succès des clarifications > 90%

### Utilisateur
- Réduction du temps de déclaration de 50%
- Satisfaction utilisateur > 4.5/5
- Adoption de la fonction vocale > 30%

## 🔍 Dépannage

### Problèmes courants
1. **API Gemini ne répond pas**
   - Vérifier la clé API
   - Vérifier les permissions internet
   - Vérifier la limite de requêtes

2. **Reconnaissance vocale défaillante**
   - Vérifier les permissions microphone
   - Tester en environnement calme
   - Vérifier la qualité audio

3. **Clarifications incorrectes**
   - Vérifier la logique de clarification
   - Tester avec différents accents
   - Optimiser les questions

## 📚 Ressources

### Documentation officielle
- [Gemini API Documentation](https://ai.google.dev/docs)
- [Android Speech Recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google AI Studio](https://aistudio.google.com/)

### Exemples de code
- [Gemini Android Samples](https://github.com/google/generative-ai-android)
- [Voice Recognition Examples](https://developer.android.com/guide/topics/speech-recognition)

## 🎯 Prochaines étapes

### Session actuelle
- [ ] Création de ce fichier de documentation
- [ ] Analyse du code existant
- [ ] Planification de l'architecture

### Session suivante
- [ ] Setup Gemini API
- [ ] Création des services de base
- [ ] Tests de connexion

---

**Dernière mise à jour:** [Date]
**Statut:** En cours de planification
**Développeur:** Assistant IA
**Prochaine session:** Setup Gemini API
