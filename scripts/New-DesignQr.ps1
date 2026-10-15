param(
    [switch]$Initialize,
    [string]$DesignId = '',
    [string]$StartsAt = '',
    [string]$EndsAt = '',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$projectDirectory = Split-Path $PSScriptRoot -Parent
$issuerDirectory = Join-Path $projectDirectory 'Essential-デザイン発行者'
$keyDirectory = Join-Path $issuerDirectory '秘密鍵'
$keyPath = Join-Path $keyDirectory 'design-issuer.dpapi'
$publicPath = Join-Path $projectDirectory 'app\src\main\assets\design-issuer-public.txt'
$designs = @('default_light','default_dark','mint','sunset','violet','forest','ocean','ruby','sky','rose','reference')
$labels = @('Essential Light','Essential Dark','Anemo Essential','Geo Essential','Electro Essential','Dendro Essential','Hydro Essential','Pyro Essential','Lunar Essential','Cryo Essential','Ruler of Death')
$rsa = New-Object System.Security.Cryptography.RSACryptoServiceProvider 3072
$rsa.PersistKeyInCsp = $false
try {
    if ($Initialize) {
        if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $publicPath)) { throw '既存の発行鍵または公開鍵は上書きできません。' }
        New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
        $privateBytes = [Text.Encoding]::UTF8.GetBytes($rsa.ToXmlString($true))
        try {
            $protectedBytes = [Security.Cryptography.ProtectedData]::Protect($privateBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
            [IO.File]::WriteAllBytes($keyPath, $protectedBytes)
        } finally { [Array]::Clear($privateBytes, 0, $privateBytes.Length) }
        [IO.File]::WriteAllText($publicPath, [Convert]::ToBase64String($rsa.ExportParameters($false).Modulus), [Text.Encoding]::ASCII)
        Write-Output 'デザイン専用の発行鍵を初期化しました。秘密鍵はこのWindowsユーザーのDPAPIで保護されています。'
        return
    }
    if (!(Test-Path -LiteralPath $keyPath)) { throw '最初に-Initializeで発行鍵を初期化してください。' }
    if ($DesignId -eq '') {
        for ($index = 0; $index -lt $designs.Count; $index++) { Write-Output (('{0}: {1} ({2})' -f ($index+1), $labels[$index], $designs[$index])) }
        $enteredDesign = Read-Host 'デザインの番号またはID'
        $designNumber = 0
        if ([int]::TryParse($enteredDesign, [ref]$designNumber) -and $designNumber -ge 1 -and $designNumber -le $designs.Count) { $DesignId = $designs[$designNumber-1] }
        else { $DesignId = $enteredDesign }
    }
    $DesignId = $DesignId.ToLowerInvariant()
    if ($designs -notcontains $DesignId) { throw '対応しているデザインIDを指定してください。' }
    if ($StartsAt -eq '') { $StartsAt = Read-Host '利用開始日時（日本時間 yyyy-MM-dd HH:mm、空欄で今すぐ）' }
    if ($EndsAt -eq '') { $EndsAt = Read-Host '利用終了日時（日本時間 yyyy-MM-dd HH:mm）' }
    function ConvertTo-DesignTime([string]$value, [bool]$allowNow) {
        if ($allowNow -and [string]::IsNullOrWhiteSpace($value)) { return [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
        if ($value -match '(Z|[+-][0-9]{2}:[0-9]{2})$') {
            return [DateTimeOffset]::Parse($value, [Globalization.CultureInfo]::InvariantCulture).ToUnixTimeSeconds()
        }
        $parsed = [DateTime]::ParseExact($value, [string[]]@('yyyy-MM-dd HH:mm','yyyy-MM-dd HH:mm:ss'), [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None)
        return ([DateTimeOffset]::new($parsed, [TimeSpan]::FromHours(9))).ToUnixTimeSeconds()
    }
    $start = ConvertTo-DesignTime $StartsAt $true
    $end = ConvertTo-DesignTime $EndsAt $false
    $issued = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    if ($start -lt 1 -or $start -ge $end -or $end -le $issued -or $end-$start -gt 366L*86400) { throw '終了は開始より後、かつ未来を指定してください。利用期間の上限は366日です。' }
    $privateBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try { $rsa.FromXmlString([Text.Encoding]::UTF8.GetString($privateBytes)) }
    finally { [Array]::Clear($privateBytes, 0, $privateBytes.Length) }
    $public = [Convert]::ToBase64String($rsa.ExportParameters($false).Modulus)
    if ($public -ne [IO.File]::ReadAllText($publicPath).Trim()) { throw '発行鍵とアプリの公開鍵が一致しません。' }
    $id = [Guid]::NewGuid().ToString()
    $payload = [Text.Encoding]::ASCII.GetBytes("1|jp.essential.app|app-design|$id|$DesignId|$issued|$start|$end")
    $signature = $rsa.SignData($payload, 'SHA256')
    function ConvertTo-Base64Url([byte[]]$bytes) { [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
    $token = 'essential-design:' + (ConvertTo-Base64Url $payload) + '.' + (ConvertTo-Base64Url $signature)
    if ($OutputDirectory -eq '') { $OutputDirectory = Join-Path $issuerDirectory '配布QR' }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $basePath = Join-Path $OutputDirectory "Design-$DesignId-$id"
    [IO.File]::WriteAllText("$basePath.txt", $token, [Text.Encoding]::UTF8)
    $java = 'C:\Program Files\Java\jdk-23\bin\java.exe'
    $jar = Join-Path $projectDirectory 'Essential-XP発行者\zxing-core.jar'
    & $java '-cp' $jar (Join-Path $PSScriptRoot 'XpQrImage.java') $token "$basePath.png"
    if ($LASTEXITCODE -ne 0) { throw 'QR画像を生成できませんでした。' }
    [IO.File]::WriteAllText("$basePath.json", (@{id=$id;design=$DesignId;issued=$issued;start=$start;end=$end} | ConvertTo-Json), [Text.Encoding]::UTF8)
    Write-Output "生成完了: $basePath.png（$DesignId、開始・終了日時を固定）"
} finally { $rsa.Dispose() }
