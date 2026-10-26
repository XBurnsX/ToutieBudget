# Ajoute dans PocketBase les champs dont la nouvelle version de l'app a besoin.
# Sans eux, ces informations restent seulement sur le téléphone (rien n'est perdu sur l'appareil).
#
#   comptes_credits.details_carte_json   : profil émetteur, taux, cycle et relevés des cartes (section 10)
#   comptes_dettes.details_dette_json      : mode, contrat, lien d'enveloppe, taux et ajustements des dettes (section 11)
#   user_preferences.enveloppes_favorites : enveloppes favorites de la page Budget (A4)
#
# Le script ne fait qu'AJOUTER des champs JSON manquants ; il ne modifie ni ne supprime rien d'autre.
# On peut le relancer sans risque.
# Utilisation : powershell -ExecutionPolicy Bypass -File .\migrer-pocketbase.ps1

$ErrorActionPreference = 'Stop'
$urlPocketBase = 'https://pocketbase.toutieserver.com'

$champsAAjouter = @(
    @{ collection = 'comptes_credits'; champ = 'details_carte_json' },
    @{ collection = 'comptes_dettes'; champ = 'details_dette_json' },
    @{ collection = 'user_preferences'; champ = 'enveloppes_favorites' }
)

# Connexion admin (le mot de passe reste dans cette fenêtre)
$courriel = Read-Host "Courriel admin PocketBase"
$motDePasseSecurise = Read-Host "Mot de passe admin PocketBase" -AsSecureString
$motDePasse = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($motDePasseSecurise))
$auth = Invoke-RestMethod -Method Post -Uri "$urlPocketBase/api/collections/_superusers/auth-with-password" `
    -ContentType 'application/json' -Body (@{ identity = $courriel; password = $motDePasse } | ConvertTo-Json)
$motDePasse = $null
$entetes = @{ Authorization = $auth.token }

foreach ($cible in $champsAAjouter) {
    $collection = Invoke-RestMethod -Method Get -Uri "$urlPocketBase/api/collections/$($cible.collection)" -Headers $entetes
    $champs = @($collection.fields)
    if ($champs | Where-Object { $_.name -eq $cible.champ }) {
        Write-Host "$($cible.collection).$($cible.champ) existe déjà." -ForegroundColor Green
        continue
    }
    # PocketBase remplace la liste complète : on renvoie les champs existants (avec leurs id) + le nouveau
    $champs += [pscustomobject]@{ name = $cible.champ; type = 'json'; required = $false; maxSize = 2000000 }
    Invoke-RestMethod -Method Patch -Uri "$urlPocketBase/api/collections/$($cible.collection)" -Headers $entetes `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes((@{ fields = $champs } | ConvertTo-Json -Depth 10))) | Out-Null
    Write-Host "$($cible.collection).$($cible.champ) ajouté." -ForegroundColor Cyan
}

Write-Host "Terminé." -ForegroundColor Green
