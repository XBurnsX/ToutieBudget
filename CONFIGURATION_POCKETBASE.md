# 🔧 Configuration PocketBase OAuth2 - Guide Complet

## 📋 Problèmes Détectés

D'après les logs de diagnostic, voici les problèmes identifiés :

### ✅ **Ce qui fonctionne**
- PocketBase est accessible sur `http://192.168.1.77:8090`
- L'endpoint OAuth2 existe et répond
- La connectivité réseau est bonne

### ⚠️ **Problèmes à résoudre**

#### 1. **Erreur 401 sur `/api/collections`**
```
Status: 401
❌ Impossible d'accéder aux collections
```
**Solution** : C'est normal, les collections sont protégées. Pas de problème.

#### 2. **Erreur 400 avec `redirectURL`**
```
"redirectURL":{"code":"validation_required","message":"Cannot be blank."}
```
**Solution** : Configuration OAuth2 incomplète dans PocketBase.

## 🔧 Configuration PocketBase OAuth2

### 1. **Accéder à l'Admin PocketBase**

1. Ouvrez votre navigateur
2. Allez sur `http://192.168.1.77:8090/_/`
3. Connectez-vous avec vos identifiants admin

### 2. **Configurer la Collection Users**

1. Allez dans **Collections** → **users**
2. Vérifiez que la collection existe
3. Si elle n'existe pas, créez-la avec ces champs :
   - `email` (type: email, required: true)
   - `name` (type: text)
   - `avatar` (type: url)

### 3. **Configurer Google OAuth2**

1. Allez dans **Settings** → **Auth providers**
2. Cliquez sur **Google**
3. Activez le provider
4. Configurez les champs :

```
Provider: Google
Client ID: 857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com
Client Secret: [Votre Client Secret Google]
Redirect URL: http://localhost:8090
```

### 4. **Vérifier les Paramètres OAuth2**

Dans la configuration Google OAuth2, assurez-vous que :

- ✅ **Provider** : `google`
- ✅ **Client ID** : Votre Client ID Google
- ✅ **Client Secret** : Votre Client Secret Google
- ✅ **Redirect URL** : `http://localhost:8090`
- ✅ **Auth options** : Activé
- ✅ **Auto confirm** : Activé (optionnel)

### 5. **Tester la Configuration**

Après la configuration, testez avec l'application :

1. Lancez l'application
2. Activez le mode debug
3. Cliquez sur le bouton "🔍" pour le diagnostic
4. Vérifiez que vous obtenez :
   ```
   ✅ Configuration OAuth2 correcte - Code invalide détecté
   ```

## 🔍 **Vérifications Supplémentaires**

### 1. **Google Cloud Console**

Vérifiez que dans Google Cloud Console :

- ✅ **OAuth 2.0** est activé
- ✅ **Client ID Web** est configuré
- ✅ **SHA-1 fingerprint** est ajouté
- ✅ **Origines JavaScript autorisées** incluent `http://localhost:8090`

### 2. **URLs de Redirection Google**

Dans Google Cloud Console → OAuth 2.0 → URIs de redirection autorisés :

```
http://localhost:8090
http://192.168.1.77:8090
http://toutiebudget.duckdns.org:8090
```

### 3. **Test de Connexion**

Après configuration, testez la connexion complète :

1. **Mode debug** : Activez le mode debug dans l'app
2. **Connexion Google** : Cliquez sur "Se connecter avec Google"
3. **Vérification** : Observez les logs pour voir :
   - ✅ Code d'autorisation reçu
   - ✅ Connexion PocketBase réussie
   - ✅ Token d'authentification obtenu

## 🚨 **Messages d'Erreur Courants**

### Erreur 400 - "redirectURL validation_required"
**Cause** : Redirect URL manquant dans la requête
**Solution** : ✅ **Corrigé** - Ajouté `redirectUrl: "http://localhost:8090"`

### Erreur 401 - "Authentication required"
**Cause** : Endpoint protégé
**Solution** : ✅ **Normal** - Les collections sont protégées

### Erreur 404 - "Endpoint not found"
**Cause** : Configuration OAuth2 manquante
**Solution** : Configurer le provider Google dans PocketBase

## 📱 **Test Final**

Une fois tout configuré :

1. **Redémarrez** PocketBase si nécessaire
2. **Lancez** l'application
3. **Testez** la connexion Google
4. **Vérifiez** les logs de debug

**Résultat attendu** :
```
✅ Code serveur disponible - Connexion avec PocketBase
✅ AUTHENTIFICATION GOOGLE RÉUSSIE !
👤 Utilisateur connecté: user@gmail.com
```

## 🔧 **Commandes de Diagnostic**

Si vous avez accès au serveur PocketBase :

```bash
# Vérifier les logs PocketBase
tail -f /path/to/pocketbase/logs

# Vérifier la configuration
curl -X GET http://192.168.1.77:8090/api/health

# Tester l'endpoint OAuth2 (doit retourner 404 en GET)
curl -X GET http://192.168.1.77:8090/api/collections/users/auth-with-oauth2
```

## 📞 **Support**

Si les problèmes persistent après cette configuration :

1. **Vérifiez** les logs PocketBase
2. **Testez** avec l'outil de diagnostic intégré
3. **Vérifiez** la configuration Google Cloud Console
4. **Testez** sur un autre appareil/émulateur 