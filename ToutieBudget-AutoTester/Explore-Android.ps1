param([int]$Depth=6,[int]$MaxActions=4000,[int]$Seed=20261026,[string]$Serial='')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'ADB introuvable' }
if (-not (& adb devices | Select-String '\sdevice$')) { throw 'Aucun Android connecté' }
Write-Host 'ATTENTION: compte Google de test uniquement, données créées par app synchronisées normalement.' -ForegroundColor Yellow
if ((Read-Host 'Écrire TEST pour confirmer') -ne 'TEST') {throw 'Annulé'}
$dest='resultats/exploration-'+(Get-Date -Format yyyyMMdd-HHmmss)
$params=@('explore.py','--depth',"$Depth",'--max-actions',"$MaxActions",'--seed',"$Seed",'--output',$dest)
if ($Serial) {$params+=@('--serial',$Serial)}
if (Get-Command py -ErrorAction SilentlyContinue) {& py -3 @params} else {& python @params}
Write-Host "Résultats: $dest/exploration.json"
