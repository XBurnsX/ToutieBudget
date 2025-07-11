# 🔑 Guide SHA-1 et Configuration Google Sign-In

## 🚨 **Problème Identifié**

Google Sign-In retourne `RESULT_CANCELED (0)` au lieu de `RESULT_OK (-1)`. Cela indique un problème de configuration, probablement le SHA-1 fingerprint.

## 🔧 **Étape 1 : Obtenir le SHA-1 de Debug**

### Sur Windows (PowerShell)
```powershell
# Dans le dossier du projet
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

### Sur macOS/Linux
```bash
# Dans le dossier du projet
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### Résultat Attendu
```
Certificate fingerprints:
SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
SHA256: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

## 🔧 **Étape 2 : Configurer Google Cloud Console**

1. **Allez sur** [Google Cloud Console](https://console.cloud.google.com/)
2. **Sélectionnez** votre projet `toutiebudget-kotlin`
3. **OAuth 2.0** → **Identifiants**
4. **Trouvez** le Client ID Android existant ou créez-en un nouveau
5. **Ajoutez** le SHA-1 obtenu à l'étape 1
6. **Sauvegardez**

## 🔧 **Étape 3 : Vérifier la Configuration**

### Dans google-services.json
```json
{
  "client": [
    {
      "client_info": {
        "android_client_info": {
          "package_name": "com.xburnsx.toutiebudget"
        }
      },
      "oauth_client": [
        {
          "client_id": "857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com"
        }
      ]
    }
  ]
}
```

### Dans build.gradle.kts
```kotlin
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com\"")
```

## 🔧 **Étape 4 : Test Rapide**

1. **Attendez** 5-10 minutes après avoir ajouté le SHA-1
2. **Redémarrez** l'émulateur/appareil
3. **Lancez** l'application
4. **Testez** la connexion Google

## 🚀 **Solutions Alternatives**

### Si le problème persiste :

#### 1. **Test sur Appareil Physique**
```bash
# Obtenir le SHA-1 de release
keytool -list -v -keystore toutiebudget.jks -alias your_key_alias
```

#### 2. **Vérifier Google Play Services**
- Allez dans **Paramètres** → **Applications** → **Google Play Services**
- Vérifiez que la version est à jour
- Si nécessaire, mettez à jour depuis le Play Store

#### 3. **Vérifier le Compte Google**
- Assurez-vous qu'un compte Google est configuré sur l'appareil
- Testez la connexion dans d'autres applications Google

#### 4. **Test avec Configuration Simple**
```kotlin
// Test temporaire sans serverAuthCode
GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestEmail()
    .build()
```

## 📊 **Logs de Diagnostic**

Après correction, vous devriez voir :

```
📊 Result Code: -1
📊 RESULT_OK = -1
✅ Résultat OK - Traitement...
✅ Compte obtenu: user@gmail.com
✅ Server Auth Code: 4/0AfJohXn...
```

## 🎯 **Résultat Final**

Une fois le SHA-1 correctement configuré :
- ✅ Google Sign-In fonctionne
- ✅ Server Auth Code obtenu
- ✅ Connexion PocketBase réussie
- ✅ Utilisateur authentifié

## 📞 **Support**

Si le problème persiste après ces étapes :

1. **Vérifiez** les logs détaillés de l'application
2. **Testez** sur un autre appareil/émulateur
3. **Vérifiez** la connectivité réseau
4. **Contactez** le support avec les logs complets 