param(
    [ValidateSet('all','smoke','years','destroy')][string]$Mode='all',
    [int]$Months=48,
    [int]$Fuzz=2000,
    [int]$Seed=20261026,
    [string]$Serial=''
)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {throw 'ADB introuvable. Installer Android SDK Platform-Tools et ajouter adb au PATH.'}
$devices=(& adb devices | Select-String '\sdevice$')
if (-not $devices) {throw 'Aucun Android ADB connecté. Ouvrir emulateur ou brancher appareil.'}
Write-Host 'IMPORTANT: connecter TOUTIEBUDGET avec un compte Google de TEST. Les actions de l app sont vraiment synchronisees par l app.' -ForegroundColor Yellow
Write-Host 'Aucun acces direct a PocketBase. La session actuelle ne sera pas reinitialisee.'
$answer=Read-Host 'Confirme que l application est connectee au compte Google de TEST (écrire TEST)'
if ($answer -ne 'TEST') {throw 'Execution annulee.'}
$out='resultats/'+(Get-Date -Format yyyyMMdd-HHmmss)
$argsList=@('engine.py','--mode',$Mode,'--months',"$Months",'--fuzz',"$Fuzz",'--seed',"$Seed",'--catalog','catalog.json','--output',$out)
if($Serial){$argsList+=@('--serial',$Serial)}
if(Get-Command py -ErrorAction SilentlyContinue){& py -3 @argsList}else{& python @argsList}
Write-Host "Rapport: $out/rapport.html"
