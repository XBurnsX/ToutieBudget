@echo off
echo ========================================
echo Obtention du SHA-1 de la clé de debug
echo ========================================
echo.

REM Chemin vers le keystore de debug par défaut
set KEYSTORE_PATH=%USERPROFILE%\.android\debug.keystore
set KEYSTORE_PASSWORD=android
set KEY_ALIAS=androiddebugkey

echo Vérification de l'existence du keystore...
if not exist "%KEYSTORE_PATH%" (
    echo ERREUR: Le keystore de debug n'existe pas à l'emplacement suivant:
    echo %KEYSTORE_PATH%
    echo.
    echo Le keystore sera créé automatiquement lors de la première compilation.
    echo Compilez d'abord votre application, puis relancez ce script.
    pause
    exit /b 1
)

echo Keystore trouvé: %KEYSTORE_PATH%
echo.

REM Obtenir le SHA-1
echo Extraction du SHA-1...
echo.

REM Utiliser keytool pour obtenir le SHA-1
"%JAVA_HOME%\bin\keytool.exe" -list -v -keystore "%KEYSTORE_PATH%" -alias "%KEY_ALIAS%" -storepass "%KEYSTORE_PASSWORD%" -keypass "%KEYSTORE_PASSWORD%" 2>nul | findstr /C:"SHA1:"

if errorlevel 1 (
    echo.
    echo Tentative avec keytool du JDK système...
    keytool -list -v -keystore "%KEYSTORE_PATH%" -alias "%KEY_ALIAS%" -storepass "%KEYSTORE_PASSWORD%" -keypass "%KEYSTORE_PASSWORD%" 2>nul | findstr /C:"SHA1:"
)

echo.
echo ========================================
echo Instructions:
echo ========================================
echo 1. Copiez le SHA-1 ci-dessus
echo 2. Allez sur https://console.cloud.google.com/
echo 3. Sélectionnez votre projet: toutiebudget-kotlin
echo 4. Allez dans APIs ^& Services ^> Credentials
echo 5. Cliquez sur votre OAuth 2.0 Client ID Android
echo 6. Ajoutez le SHA-1 dans la section "SHA-1 certificate fingerprints"
echo 7. Sauvegardez les modifications
echo.
echo Note: Il peut falloir quelques minutes pour que les changements prennent effet.
echo.
pause


