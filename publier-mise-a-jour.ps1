# Publie une nouvelle version de Toutie Budget dans PocketBase (collection app_versions).
# L'app la détecte à l'ouverture et propose de l'installer.
#
# Avant de lancer : monter versionCode et versionName dans app/build.gradle.kts.
# Utilisation : powershell -ExecutionPolicy Bypass -File .\publier-mise-a-jour.ps1

$ErrorActionPreference = 'Stop'
$racine = $PSScriptRoot
$urlPocketBase = 'https://pocketbase.toutieserver.com'
$collection = 'app_versions'

# 1. Version à publier (lue dans app/build.gradle.kts)
$gradle = Get-Content (Join-Path $racine 'app\build.gradle.kts') -Raw
$versionCode = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$versionName = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
Write-Host "Version à publier : $versionName (code $versionCode)" -ForegroundColor Cyan

# 2. Compiler l'APK release (signé avec la clé de keystore.properties)
if (-not (Test-Path (Join-Path $racine 'keystore.properties'))) {
    throw "keystore.properties introuvable : l'APK ne serait pas signé et ne pourrait pas s'installer par-dessus l'app."
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
Write-Host "Compilation de l'APK release..." -ForegroundColor Cyan
Push-Location $racine
try { & .\gradlew.bat :app:assembleRelease --console=plain; if ($LASTEXITCODE -ne 0) { throw "La compilation a échoué." } }
finally { Pop-Location }

$apk = Join-Path $racine 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK introuvable : $apk" }
$sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$tailleMo = [math]::Round((Get-Item $apk).Length / 1MB, 1)
Write-Host "APK : $tailleMo Mo, SHA-256 $sha256" -ForegroundColor Green

# 3. Notes de version (affichées dans l'app)
$notes = Read-Host "Notes de version (ce qui change, une ligne)"

# 4. Connexion admin PocketBase (le mot de passe reste dans cette fenêtre)
$courriel = Read-Host "Courriel admin PocketBase"
$motDePasseSecurise = Read-Host "Mot de passe admin PocketBase" -AsSecureString
$motDePasse = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($motDePasseSecurise))
$auth = Invoke-RestMethod -Method Post -Uri "$urlPocketBase/api/collections/_superusers/auth-with-password" `
    -ContentType 'application/json' -Body (@{ identity = $courriel; password = $motDePasse } | ConvertTo-Json)
$motDePasse = $null
$entetes = @{ Authorization = $auth.token }

# 5. Créer la collection au premier usage (lecture publique, écriture admin seulement)
try {
    Invoke-RestMethod -Method Get -Uri "$urlPocketBase/api/collections/$collection" -Headers $entetes | Out-Null
} catch {
    Write-Host "Création de la collection $collection..." -ForegroundColor Cyan
    $definition = @{
        name = $collection
        type = 'base'
        listRule = ''
        viewRule = ''
        createRule = $null
        updateRule = $null
        deleteRule = $null
        fields = @(
            @{ name = 'version_code'; type = 'number'; required = $true; onlyInt = $true },
            @{ name = 'version_name'; type = 'text'; required = $true },
            @{ name = 'notes'; type = 'text' },
            @{ name = 'apk'; type = 'file'; required = $true; maxSelect = 1; maxSize = 209715200 },
            @{ name = 'sha256'; type = 'text' }
        )
        indexes = @("CREATE UNIQUE INDEX idx_app_versions_code ON $collection (version_code)")
    }
    Invoke-RestMethod -Method Post -Uri "$urlPocketBase/api/collections" -Headers $entetes `
        -ContentType 'application/json' -Body ($definition | ConvertTo-Json -Depth 5) | Out-Null
}

# 6. Envoyer la version (curl.exe pour l'envoi de fichier, absent de PowerShell 5.1)
$fichierNotes = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($fichierNotes, $notes, (New-Object Text.UTF8Encoding $false))
try {
    $reponse = & curl.exe -sS -X POST "$urlPocketBase/api/collections/$collection/records" `
        -H "Authorization: $($auth.token)" `
        -F "version_code=$versionCode" `
        -F "version_name=$versionName" `
        -F "notes=<$fichierNotes" `
        -F "sha256=$sha256" `
        -F "apk=@$apk;filename=toutiebudget-$versionCode.apk"
} finally {
    Remove-Item $fichierNotes -ErrorAction SilentlyContinue
}
$resultat = $reponse | ConvertFrom-Json
if (-not $resultat.id) { throw "Envoi refusé par PocketBase : $reponse" }

Write-Host ""
Write-Host "Version $versionName publiée. Elle sera proposée à la prochaine ouverture de l'app." -ForegroundColor Green
