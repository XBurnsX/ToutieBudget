# Instructions pour obtenir le SHA-1 de Google Play Store

## ⚠️ IMPORTANT : Il y a DEUX SHA-1 différents !

### 1. **Upload Key SHA-1** (votre keystore)
- C'est celui que vous utilisez pour signer le bundle
- SHA-1: `14:D6:F9:65:C3:5C:1F:33:6C:89:DD:74:89:B9:2E:93:8A:06:3C:C5`
- **NE PAS utiliser pour OAuth** (sauf si c'est aussi l'App Signing Key)

### 2. **App Signing Key SHA-1** (Google Play)
- C'est celui que Google Play utilise pour signer l'app distribuée
- **C'EST CELUI-LÀ qu'il faut dans Google Cloud Console pour OAuth !**

## 📋 Comment obtenir le SHA-1 de l'App Signing Key :

1. Allez sur **Google Play Console** : https://play.google.com/console/
2. Sélectionnez votre app **"Toutie Budget"**
3. Allez dans : **Setup** > **App signing**
4. Dans la section **"App signing key certificate"**, vous verrez :
   - **SHA-1 certificate fingerprint** ← **COPIEZ CELUI-LÀ !**
   - SHA-256 certificate fingerprint

## ✅ Ce qu'il faut faire :

1. **Copiez le SHA-1 de l'App Signing Key** depuis Google Play Console
2. **Ajoutez-le dans Google Cloud Console** :
   - https://console.cloud.google.com/
   - Projet : `toutiebudget-kotlin`
   - **APIs & Services** > **Credentials**
   - Cliquez sur votre **OAuth 2.0 Client ID Android**
   - Dans **"SHA-1 certificate fingerprints"**, ajoutez le SHA-1 de l'App Signing Key
   - Sauvegardez

## 🔍 Vérification :

- Si vous avez déjà uploadé une version sur Google Play, Google Play a probablement généré un App Signing Key
- Le SHA-1 de l'App Signing Key est différent de celui de votre upload key
- Pour que Google Sign-In fonctionne dans l'app distribuée, vous DEVEZ utiliser le SHA-1 de l'App Signing Key

## 📝 Note :

Si c'est la **première version** de l'app et que vous n'avez pas encore uploadé :
- Google Play n'a pas encore généré d'App Signing Key
- Dans ce cas, utilisez le SHA-1 de votre upload key pour l'instant
- Après le premier upload, Google Play générera l'App Signing Key et vous devrez ajouter son SHA-1
