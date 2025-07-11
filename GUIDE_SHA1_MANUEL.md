# 🔐 Guide Manuel SHA-1 - Diagnostic Google Sign-In

## 🚨 **Problème Principal**
Google Sign-In retourne `RESULT_CANCELED (0)` au lieu de `RESULT_OK (-1)`, empêchant l'obtention du `serverAuthCode` nécessaire pour PocketBase.

## 🔍 **Diagnostic SHA-1 Manuel**

### 1. **Obtenir le SHA-1 de Debug**

#### Méthode 1 : Via Android Studio
1. Ouvrez Android Studio
2. Allez dans **View** → **Tool Windows** → **Gradle**
3. Dépliez votre projet → **app** → **Tasks** → **android**
4. Double-clique sur **signingReport**
5. Copiez le SHA-1 de debug

#### Méthode 2 : Via Terminal (si adb est disponible)
```bash
# Dans le dossier du projet
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

#### Méthode 3 : Via PowerShell
```powershell
# Dans le dossier du projet
keytool -list -v -keystore $env:USERPROFILE\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### 2. **Vérifier Google Cloud Console**

1. Allez sur https://console.cloud.google.com
2. Sélectionnez votre projet
3. Allez dans **APIs & Services** → **Credentials**
4. Trouvez votre **Client ID Android**
5. Vérifiez que le SHA-1 est présent

### 3. **SHA-1 Attendu pour Debug**

Le SHA-1 de debug standard est généralement :
```
SHA1: 5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25
```

## 🔧 **Configuration Google Cloud Console**

### 1. **Client ID Android**
- **Package name** : `com.xburnsx.toutiebudget`
- **SHA-1 fingerprint** : [Votre SHA-1 de debug]

### 2. **Client ID Web**
- **Authorized JavaScript origins** :
  ```
  http://localhost:8090
  http://192.168.1.77:8090
  http://toutiebudget.duckdns.org:8090
  ```
- **Authorized redirect URIs** :
  ```
  http://localhost:8090
  http://192.168.1.77:8090
  http://toutiebudget.duckdns.org:8090
  ```

## 📱 **Test de l'Application**

### 1. **Lancer l'Application**
- Ouvrez l'application sur l'émulateur
- Observez les logs de diagnostic SHA-1

### 2. **Logs Attendus**
```
🔍 === DIAGNOSTIC SHA-1 ===
✅ SHA-1 de debug obtenu: [Votre SHA-1]
📋 Instructions pour Google Cloud Console:
1. Allez sur https://console.cloud.google.com
2. Sélectionnez votre projet
3. Allez dans 'APIs & Services' > 'Credentials'
4. Trouvez votre Client ID Android
5. Ajoutez ce SHA-1: [Votre SHA-1]
6. Attendez 5-10 minutes pour la propagation
```

### 3. **Test de Connexion**
- Cliquez sur "Se connecter avec Google"
- Observez les logs pour voir :
  - ✅ `RESULT_OK (-1)` au lieu de `RESULT_CANCELED (0)`
  - ✅ `Server Auth Code` reçu
  - ✅ Connexion PocketBase réussie

## 🚨 **Problèmes Courants**

### 1. **SHA-1 Incorrect**
**Symptômes** : `RESULT_CANCELED (0)`
**Solution** : Vérifiez le SHA-1 dans Google Cloud Console

### 2. **Google Play Services**
**Symptômes** : Erreur de connexion
**Solution** : Vérifiez que Google Play Services est installé et à jour

### 3. **Configuration OAuth2**
**Symptômes** : Erreur 400/401 sur PocketBase
**Solution** : Vérifiez la configuration OAuth2 dans PocketBase

## 🔧 **Commandes de Diagnostic**

### Vérifier Google Play Services
```bash
# Sur l'appareil
Settings > Apps > Google Play Services > Version
```

### Vérifier la Connectivité
```powershell
# Test PocketBase
Invoke-WebRequest -Uri "http://192.168.1.77:8090/api/health" -Method Get
```

### Vérifier les Logs
```bash
# Si adb est disponible
adb logcat | grep "com.xburnsx.toutiebudget"
```

## 📞 **Support**

Si les problèmes persistent :

1. **Vérifiez** le SHA-1 dans Google Cloud Console
2. **Attendez** 5-10 minutes après modification
3. **Testez** sur un appareil physique
4. **Vérifiez** Google Play Services
5. **Redémarrez** l'émulateur

## 🎯 **Résultat Attendu**

Après correction du SHA-1 :
```
📊 Result Code: -1
📊 RESULT_OK = -1
✅ Résultat OK - Traitement...
✅ Compte obtenu: user@gmail.com
✅ Server Auth Code: 4/0AfJohXn...
✅ Code serveur disponible - Connexion avec PocketBase
✅ AUTHENTIFICATION GOOGLE RÉUSSIE !
``` 