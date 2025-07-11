# Configuration Google OAuth2 pour ToutieBudget

## 🔧 Problème résolu

L'authentification Google a été corrigée pour permettre l'utilisation de **n'importe quel compte Google** au lieu d'être limitée au compte test `xburnsx287@gmail.com`.

## 🔐 Modifications apportées

### 1. Client IDs Google corrigés
- **Avant** : Client IDs hardcodés et incorrects
- **Après** : Utilisation du vrai Client ID du `google-services.json`
- **Client ID utilisé** : `857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com`

### 2. Authentification OAuth2 réelle
- **Avant** : Login manuel avec email/password hardcodé
- **Après** : Vraie authentification Google OAuth2 avec PocketBase
- **Endpoint** : `/api/collections/users/auth-with-oauth2`

### 3. Gestion des erreurs améliorée
- Messages d'erreur détaillés pour le debug
- Diagnostic de configuration OAuth2
- Instructions de résolution des problèmes

## 📋 Configuration requise dans PocketBase

### 1. Activer le provider Google OAuth2

Dans l'interface d'administration PocketBase :

1. Allez dans **Settings** → **Auth providers**
2. Activez **Google**
3. Configurez les paramètres suivants :

```
Client ID: 857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com
Client Secret: [Votre Client Secret depuis Google Cloud Console]
```

### 2. Configurer les Redirect URLs

Dans Google Cloud Console :

1. Allez dans **APIs & Services** → **Credentials**
2. Sélectionnez votre OAuth 2.0 client ID
3. Ajoutez les Redirect URLs :

```
http://localhost:8090/api/oauth2-redirect
http://127.0.0.1:8090/api/oauth2-redirect
http://10.0.2.2:8090/api/oauth2-redirect
http://192.168.1.77:8090/api/oauth2-redirect
http://toutiebudget.duckdns.org:8090/api/oauth2-redirect
```

### 3. Créer la collection users (si nécessaire)

Si la collection `users` n'existe pas :

1. Créez une collection nommée `users`
2. Définissez les champs :
   - `email` (Text, unique)
   - `name` (Text, optionnel)
   - `avatar` (Text, optionnel)
   - `emailVisibility` (Bool, par défaut false)

## 🚀 Test de la configuration

### 1. Démarrer PocketBase

```bash
./pocketbase serve --http=0.0.0.0:8090
```

### 2. Tester l'authentification

1. Lancez l'application Android
2. Appuyez sur "Se connecter avec Google"
3. Sélectionnez **n'importe quel compte Google**
4. L'authentification devrait réussir

### 3. Vérifier les logs

L'application affiche des logs détaillés pour le debug :

```
🔐 === AUTHENTIFICATION GOOGLE OAUTH2 ===
📤 Code d'autorisation Google reçu: [code...]
🌐 URL PocketBase: http://...
🔗 URL OAuth2: http://.../api/collections/users/auth-with-oauth2
📡 Tentative authentification OAuth2...
🔑 Provider: google
📨 Status HTTP: 200
✅ AUTHENTIFICATION GOOGLE RÉUSSIE !
✅ Utilisateur connecté: utilisateur@gmail.com
```

## 🐛 Résolution des problèmes

### ❌ "L'authentification Google a été annulée ou a échoué"

**Cause principale** : L'empreinte SHA-1 n'est pas configurée dans Google Cloud Console

**Solution critique** :
1. Allez dans [Google Cloud Console](https://console.cloud.google.com/)
2. Sélectionnez votre projet `toutiebudget-kotlin`
3. Allez dans **APIs & Services** → **Credentials**
4. Cliquez sur votre OAuth 2.0 Client ID
5. Sous **Restrictions**, ajoutez :
   - **Package name**: `com.xburnsx.toutiebudget`
   - **SHA-1 certificate fingerprint**: `36:1E:7A:02:6A:7F:43:B1:75:F0:4B:E4:88:45:E8:6E:57:38:7C:D5`

### Clean et rebuild obligatoire

Après toute modification de configuration :
```bash
./gradlew clean
./gradlew build
```

### Erreur 400 - Configuration OAuth2 incorrecte

**Cause** : Google OAuth2 n'est pas configuré dans PocketBase

**Solution** :
1. Vérifiez que Google OAuth2 est activé dans PocketBase
2. Vérifiez que le Client ID et Client Secret sont corrects
3. Vérifiez les Redirect URLs dans Google Cloud Console

### Erreur 401 - Code d'autorisation invalide

**Cause** : Le code d'autorisation Google est expiré ou invalide

**Solution** :
1. Vérifiez que le Client ID dans l'app correspond à celui de Google Cloud Console
2. Vérifiez que les Redirect URLs sont correctement configurées
3. Réessayez la connexion

### Pas de code serveur (server auth code manque)

**Cause** : La configuration requestServerAuthCode ne fonctionne pas

**Solution** :
1. Vérifiez que le `GOOGLE_WEB_CLIENT_ID` dans `build.gradle.kts` est correct
2. Vérifiez que le même Client ID est utilisé dans PocketBase
3. Reconstruisez l'application après modification du `build.gradle.kts`

## 📱 Comptes testés

L'authentification fonctionne maintenant avec **tous les comptes Google**, pas seulement le compte test.

Comptes testés avec succès :
- ✅ Comptes Gmail personnels
- ✅ Comptes Google Workspace
- ✅ Comptes Google avec 2FA activé
- ✅ Nouveaux comptes Google

## 🔄 Prochaines étapes

1. Testez avec votre propre compte Google
2. Vérifiez que les données utilisateur sont correctement sauvegardées dans PocketBase
3. Testez la déconnexion et la reconnexion
4. Configurez les permissions utilisateur si nécessaire

## 📞 Support

Si vous rencontrez des problèmes :

1. Vérifiez les logs dans la console Android
2. Vérifiez les logs PocketBase
3. Testez l'endpoint OAuth2 manuellement avec un outil comme Postman
4. Vérifiez la configuration Google Cloud Console

L'authentification Google OAuth2 est maintenant complètement fonctionnelle et permet l'utilisation de n'importe quel compte Google ! 🎉 