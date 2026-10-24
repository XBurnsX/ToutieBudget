# Script PowerShell pour obtenir le SHA-1 de la clé de debug
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Obtention du SHA-1 de la clé de debug" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$keystorePath = "$env:USERPROFILE\.android\debug.keystore"

if (-not (Test-Path $keystorePath)) {
    Write-Host "ERREUR: Le keystore de debug n'existe pas à:" -ForegroundColor Red
    Write-Host $keystorePath -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Le keystore sera créé lors de la première compilation." -ForegroundColor Yellow
    Write-Host "Compilez d'abord votre application dans Android Studio." -ForegroundColor Yellow
    pause
    exit 1
}

Write-Host "Keystore trouvé: $keystorePath" -ForegroundColor Green
Write-Host ""

# Chercher keytool dans plusieurs emplacements possibles
$keytoolPaths = @(
    "$env:JAVA_HOME\bin\keytool.exe",
    "$env:LOCALAPPDATA\Android\Sdk\jbr\bin\keytool.exe",
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    "C:\Program Files\Java\*\bin\keytool.exe"
)

$keytool = $null
foreach ($path in $keytoolPaths) {
    if (Test-Path $path) {
        $keytool = $path
        break
    }
}

# Si pas trouvé, essayer de trouver dans le PATH
if (-not $keytool) {
    $keytool = Get-Command keytool -ErrorAction SilentlyContinue
    if ($keytool) {
        $keytool = $keytool.Source
    }
}

if (-not $keytool -or -not (Test-Path $keytool)) {
    Write-Host "ERREUR: keytool non trouvé!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Solutions:" -ForegroundColor Yellow
    Write-Host "1. Utilisez Android Studio > Terminal intégré > gradlew signingReport" -ForegroundColor Yellow
    Write-Host "2. Ou installez Java JDK et ajoutez-le au PATH" -ForegroundColor Yellow
    Write-Host "3. Ou utilisez Android Studio > Build > Generate Signed Bundle/APK" -ForegroundColor Yellow
    pause
    exit 1
}

Write-Host "Utilisation de: $keytool" -ForegroundColor Green
Write-Host ""
Write-Host "Extraction du SHA-1..." -ForegroundColor Cyan
Write-Host ""

# Exécuter keytool
$output = & $keytool -list -v -keystore $keystorePath -alias androiddebugkey -storepass android -keypass android 2>&1

# Chercher la ligne SHA1 (support français et anglais)
$sha1Line = $output | Select-String -Pattern "SHA\s*1:" | Select-Object -First 1

if ($sha1Line) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "SHA-1 TROUVÉ:" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host $sha1Line.Line -ForegroundColor Yellow
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Instructions:" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "1. Copiez le SHA-1 ci-dessus (sans 'SHA1:')" -ForegroundColor White
    Write-Host "2. Allez sur https://console.cloud.google.com/" -ForegroundColor White
    Write-Host "3. Projet: toutiebudget-kotlin" -ForegroundColor White
    Write-Host "4. APIs & Services > Credentials" -ForegroundColor White
    Write-Host "5. Cliquez sur votre OAuth 2.0 Client ID Android" -ForegroundColor White
    Write-Host "6. Ajoutez le SHA-1 dans 'SHA-1 certificate fingerprints'" -ForegroundColor White
    Write-Host "7. Sauvegardez et attendez 2-3 minutes" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "ERREUR: SHA-1 non trouvé dans la sortie!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Sortie complète:" -ForegroundColor Yellow
    Write-Host $output
}

pause
