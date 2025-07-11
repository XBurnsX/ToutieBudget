# 🔐 Flux de Connexion Google OAuth2 avec PocketBase - Debug Complet

## 📋 Vue d'ensemble

L'application ToutieBudget utilise Google Sign-In pour l'authentification, avec PocketBase comme backend. Ce document décrit le flux complet et les outils de debug mis en place.

## 🔄 Flux de Connexion

### 1. Initialisation
- **LoginViewModel** s'initialise avec le mode debug activé
- **PocketBaseClient** se connecte et teste la connectivité
- **UrlResolver** détermine automatiquement l'URL PocketBase à utiliser

### 2. Configuration Google Sign-In
```kotlin
GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestEmail()
    .requestProfile()
    .requestIdToken(webClientId)
    .requestServerAuthCode(webClientId, forceRefresh = true)
    .build()
```

### 3. Lancement de la Connexion
1. L'utilisateur clique sur "Se connecter avec Google"
2. `GoogleSignIn.getClient()` lance l'intent de connexion
3. Google retourne un `ActivityResult`

### 4. Traitement du Résultat
- **RESULT_OK** : Récupération du compte Google avec `serverAuthCode` et `idToken`
- **RESULT_CANCELED** : Analyse détaillée des causes d'échec

### 5. Authentification PocketBase
- Envoi du `serverAuthCode` à l'endpoint `/api/collections/users/auth-with-oauth2`
- Analyse de la réponse avec gestion d'erreurs détaillée

## 🔧 Mode Debug

### Activation
Le mode debug est activé par défaut. Il peut être basculé via :
- Interface normale : Bouton "🔧 Mode Debug"
- Interface debug : Bouton "❌" pour désactiver

### Fonctionnalités Debug

#### 1. Logs Détaillés
- Chaque étape du flux est loggée avec des emojis pour faciliter la lecture
- Les logs sont affichés en temps réel dans l'interface debug
- Limite de 50 dernières entrées pour éviter la surcharge

#### 2. Diagnostic PocketBase
Bouton "🔍" pour lancer un diagnostic complet :
- Test de connectivité vers toutes les URLs configurées
- Test de l'endpoint OAuth2 (GET et POST)
- Vérification de la configuration Google dans PocketBase
- Analyse des erreurs avec messages explicites

#### 3. Gestion des Erreurs Améliorée
Messages d'erreur plus explicites selon le type :
- **Timeout** : "Le serveur ne répond pas dans les délais"
- **Réseau** : "Erreur de connexion réseau"
- **404** : "Serveur PocketBase introuvable"
- **401** : "Authentification échouée"

## 📊 Codes d'Erreur Google Sign-In

| Code | Signification | Cause Possible |
|------|---------------|----------------|
| 10 | DEVELOPER_ERROR | Configuration incorrecte |
| 12500 | SIGN_IN_REQUIRED | Utilisateur non connecté |
| 12501 | SIGN_IN_CANCELLED | Connexion annulée |
| 12502 | SIGN_IN_CURRENTLY_IN_PROGRESS | Connexion en cours |

## 🔍 Outils de Diagnostic

### TestPocketBase.kt
Utilitaire complet pour tester la connectivité :

```kotlin
// Test de connectivité
TestPocketBase.testerConnectiviteComplète()

// Test de l'endpoint OAuth2
TestPocketBase.testerEndpointOAuth2(url)

// Diagnostic complet
TestPocketBase.diagnosticComplet()
```

### Analyse des Erreurs Serveur
```kotlin
400 -> "Code d'autorisation Google invalide"
401 -> "Authentification échouée"
404 -> "Endpoint OAuth2 introuvable"
500 -> "Erreur interne du serveur"
```

## 🛠️ Configuration Requise

### Google Cloud Console
- Client ID Web configuré
- SHA-1 fingerprint ajouté
- OAuth2 activé pour l'application

### PocketBase
- Collection `users` créée
- Fournisseur Google OAuth2 configuré
- Endpoint `/api/collections/users/auth-with-oauth2` accessible

### Android
- Google Play Services installé et à jour
- Permissions internet configurées
- `google-services.json` dans le projet

## 🚀 Utilisation

1. **Lancement de l'app** : Mode debug activé automatiquement
2. **Test de connexion** : Cliquer sur "Se connecter avec Google"
3. **Analyse des logs** : Observer les logs en temps réel
4. **Diagnostic** : Utiliser le bouton "🔍" pour un test complet
5. **Résolution** : Suivre les messages d'erreur explicites

## 📝 Logs Types

### Initialisation
```
🚀 Initialisation du LoginViewModel...
📡 Initialisation du client PocketBase...
✅ Client PocketBase initialisé avec succès
```

### Connexion Google
```
🔐 === DÉBUT CONNEXION GOOGLE AVEC COMPTE ===
📧 Email: user@gmail.com
👤 Nom: User Name
🔑 Code autorisation: 4/0AfJohXn...
🎫 ID Token: eyJhbGciOiJSUzI1NiIs...
```

### Authentification PocketBase
```
🔐 === AUTHENTIFICATION GOOGLE OAUTH2 ===
🌐 URL PocketBase obtenue: http://192.168.1.77:8090
🔗 URL OAuth2 complète: http://192.168.1.77:8090/api/collections/users/auth-with-oauth2
📦 Corps de la requête JSON préparé: {"provider":"google","code":"4/0AfJohXn..."}
📡 Envoi de la requête à PocketBase...
📨 Réponse reçue de PocketBase ! Status: 200
✅ AUTHENTIFICATION GOOGLE RÉUSSIE !
```

## 🔧 Dépannage

### Problème : RESULT_CANCELED
1. Vérifier Google Play Services
2. Contrôler la configuration SHA-1
3. Vérifier le Client ID
4. Tester sur un autre appareil

### Problème : Erreur 404 sur PocketBase
1. Vérifier que PocketBase est démarré
2. Contrôler l'URL dans UrlResolver
3. Tester la connectivité réseau
4. Vérifier la configuration OAuth2 dans PocketBase

### Problème : Erreur 400/401
1. Vérifier la configuration Google OAuth2 dans PocketBase
2. Contrôler le Client ID et Secret
3. Vérifier les URLs de redirection
4. Tester avec un nouveau code d'autorisation

## 📱 Interface Utilisateur

### Mode Normal
- Interface élégante avec image de fond
- Bouton "🔧 Mode Debug" en bas
- Messages d'erreur explicites

### Mode Debug
- Logs en temps réel
- Boutons d'action (🧹, 🔍, ❌)
- Diagnostic complet de PocketBase
- Interface technique pour développeurs 