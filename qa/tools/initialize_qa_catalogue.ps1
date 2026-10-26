param(
    [string]$Specification = 'C:\Users\XBurnsX\Downloads\ToutieBudget_Codex_Tester_Repartage.txt',
    [string]$RepositoryRoot = 'D:\Toutie Budget'
)

$ErrorActionPreference = 'Stop'
$qaRoot = Join-Path $RepositoryRoot 'qa'
New-Item -ItemType Directory -Force -Path $qaRoot | Out-Null

$lines = Get-Content -LiteralPath $Specification
$tests = [System.Collections.Generic.List[object]]::new()

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^(?<id>[A-Z]{2,5}-\d{3})\s*\|\s*FAIRE\s*:\s*(?<scenario>.+?)\s*$') {
        $id = $Matches.id
        $scenario = $Matches.scenario.Trim()
        $expected = ''
        $proof = ''
        for ($j = $i + 1; $j -lt [Math]::Min($i + 8, $lines.Count); $j++) {
            if ($lines[$j] -match '^[A-Z]{2,5}-\d{3}\s*\|') { break }
            if ($lines[$j] -match '^\s*ATTENDU\s*:\s*(.+?)\s*$') { $expected = $Matches[1].Trim() }
            if ($lines[$j] -match '^\s*PREUVE\s*:\s*(.+?)\s*$') { $proof = $Matches[1].Trim() }
        }

        $status = 'NOT_RUN'
        $justification = 'Pas encore exécuté dans un environnement E2E PocketBase isolé.'
        if ($id -eq 'AUTH-015') {
            $status = 'PASS'
            $justification = 'Installation neuve; réseau coupé avant lancement; écran Connexion échouée explicite; Réessayer actionné; aucune donnée locale; aucun crash. Preuves: qa/screenshots/startup_clean_build.png, qa/screenshots/AUTH-015_retry_offline.png, qa/ui-dumps/AUTH-015_retry_offline.xml, qa/logs/AUTH-015_retry_offline.log.'
        }
        if ($id -eq 'SEC-009') {
            $status = 'FAIL'
            $justification = "Le build debug utilisé pour l’E2E contacte https://pocketbase.toutieserver.com/api/health au démarrage au lieu de bloquer une URL de production. Preuve: qa/logs/startup_clean_build.log."
        }

        $tests.Add([pscustomobject]@{
            ID = $id
            Module = ($id -split '-')[0]
            Type = 'ANDROID_E2E'
            Scenario = $scenario
            Preconditions = 'Fixture déterministe restaurée; application et backend TEST isolés; interaction UI réelle.'
            Etapes_UI = $scenario
            Attendu_UI_Room_PocketBase = $expected
            Oracle = $proof
            Statut = $status
            Justification = $justification
        })
    }
}

$tests.Add([pscustomobject]@{
    ID = 'SEC-016'
    Module = 'SEC'
    Type = 'API_SECURITY_DISCOVERY'
    Scenario = 'Interroger sans authentification les listes PocketBase users, comptes_cheques, categories, enveloppes et transactions avec fields=id et perPage=1.'
    Preconditions = 'Aucun en-tête Authorization; requêtes GET seulement; aucun contenu métier conservé.'
    Etapes_UI = 'Sans objet: découverte API complémentaire issue du préflight de sécurité.'
    Attendu_UI_Room_PocketBase = 'PocketBase refuse par 401/403 et ne révèle ni enregistrements ni totaux.'
    Oracle = 'Codes HTTP et métadonnées de pagination uniquement; ne jamais afficher ou conserver les IDs retournés.'
    Statut = 'FAIL'
    Justification = "PocketBase a répondu HTTP 200 sans authentification aux cinq collections et a révélé leurs totaux. Aucun contenu métier n’a été lu ni conservé."
})

$cataloguePath = Join-Path $qaRoot 'catalogue_tests.csv'
$tests | Sort-Object ID | Export-Csv -LiteralPath $cataloguePath -NoTypeInformation -Encoding utf8BOM

$matrix = foreach ($test in ($tests | Sort-Object ID)) {
    [pscustomobject]@{
        Exigence_Ou_Controle = $test.Scenario
        Tests = $test.ID
        Module = $test.Module
        Couverture = if ($test.Statut -eq 'NOT_RUN') { 'PLANIFIEE_NON_EXECUTEE' } else { 'EXECUTEE' }
        Statut = $test.Statut
    }
}
$matrix | Export-Csv -LiteralPath (Join-Path $qaRoot 'matrice_couverture.csv') -NoTypeInformation -Encoding utf8BOM

$inventory = [System.Collections.Generic.List[object]]::new()
$navigationFiles = Get-ChildItem -LiteralPath (Join-Path $RepositoryRoot 'app\src\main') -Recurse -Filter '*.kt'
foreach ($file in $navigationFiles) {
    $raw = Get-Content -LiteralPath $file.FullName -Raw
    if ($null -eq $raw) { continue }
    $routeMatches = [regex]::Matches($raw, 'composable\s*\(\s*(?:route\s*=\s*)?"([^"]+)"')
    foreach ($match in $routeMatches) {
        $before = $raw.Substring(0, $match.Index)
        $lineNumber = ($before -split "`n").Count
        $inventory.Add([pscustomobject]@{
            Page = [IO.Path]::GetFileNameWithoutExtension($file.Name)
            Sous_Page = ''
            Controle = 'Route Navigation Compose'
            Action = 'ouvrir'
            Route = $match.Groups[1].Value
            Composant_Source = "$($file.FullName):$lineNumber"
            Etat_Visible = 'SOURCE_DISCOVERED_NOT_EXECUTED'
            Test_ID = ''
        })
    }
}

$controlPattern = '\b(Button|IconButton|FloatingActionButton|TextField|OutlinedTextField|DropdownMenu|DropdownMenuItem|Checkbox|RadioButton|Switch|combinedClickable|clickable|detectTapGestures)\s*\('
foreach ($file in $navigationFiles) {
    $fileLines = Get-Content -LiteralPath $file.FullName
    for ($lineIndex = 0; $lineIndex -lt $fileLines.Count; $lineIndex++) {
        if ($fileLines[$lineIndex] -match $controlPattern) {
            $inventory.Add([pscustomobject]@{
                Page = [IO.Path]::GetFileNameWithoutExtension($file.Name)
                Sous_Page = ''
                Controle = $Matches[1]
                Action = 'interaction à confirmer dans APK'
                Route = ''
                Composant_Source = "$($file.FullName):$($lineIndex + 1)"
                Etat_Visible = 'SOURCE_DISCOVERED_NOT_EXECUTED'
                Test_ID = ''
            })
        }
    }
}
$inventory | Sort-Object Page,Composant_Source | Export-Csv -LiteralPath (Join-Path $qaRoot 'inventaire_ui.csv') -NoTypeInformation -Encoding utf8BOM

$specHash = (Get-FileHash -LiteralPath $Specification -Algorithm SHA256).Hash.ToLowerInvariant()
$gitHash = (& git -C $RepositoryRoot rev-parse HEAD).Trim()
$versionLines = Select-String -LiteralPath (Join-Path $RepositoryRoot 'app\build.gradle.kts') -Pattern 'versionCode\s*=|versionName\s*='
$checksumLines = @(
    "spec_sha256  $specHash  $Specification",
    "git_commit  $gitHash",
    ($versionLines | ForEach-Object { "app_version  $($_.Line.Trim())" })
)
Set-Content -LiteralPath (Join-Path $qaRoot 'checksums.txt') -Value $checksumLines -Encoding utf8

Write-Output "Catalogue: $($tests.Count) tests (491 racines + 1 découverte)"
Write-Output "Inventaire source: $($inventory.Count) routes/contrôles"
Write-Output "PASS: $(@($tests | Where-Object Statut -eq 'PASS').Count)"
Write-Output "FAIL: $(@($tests | Where-Object Statut -eq 'FAIL').Count)"
Write-Output "NOT_RUN: $(@($tests | Where-Object Statut -eq 'NOT_RUN').Count)"
