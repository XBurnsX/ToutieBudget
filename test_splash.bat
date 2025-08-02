@echo off
echo ===================================
echo   Test du nouveau splash screen
echo ===================================
echo.

echo 1. Construction de l'APK de debug...
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de la construction !
    pause
    exit /b 1
)

echo.
echo 2. Installation sur l'appareil connecte...
call gradlew.bat installDebug

if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de l'installation !
    echo Assurez-vous qu'un appareil ou emulateur est connecte.
    pause
    exit /b 1
)

echo.
echo ===================================
echo   BUILD ET INSTALLATION REUSSIS !
echo ===================================
echo.
echo L'application a ete installee avec les nouveaux splash screens :
echo - Splash screen natif Android (au demarrage) - FOND #191a1b
echo - Splash screen personalise (dans l'app) - FOND #191a1b
echo - BRANDING.PNG EN GRAND (180dp) avec ANIMATIONS
echo - SPLASH.PNG AUSSI EN GRAND (160dp) - LES DEUX EN GRAND !
echo - Texte blanc sur fond #191a1b
echo.
echo Relancez l'application pour voir LES DEUX LOGOS EN GRAND !
echo.
pause